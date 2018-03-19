/**
 * 
 */
package org.geneontology.gocam.exchange;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.io.IOUtils;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.query.QuerySolution;
import org.apache.jena.query.ResultSet;
import org.apache.jena.rdf.model.Literal;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.update.UpdateAction;
import org.apache.jena.update.UpdateFactory;
import org.apache.jena.update.UpdateRequest;
import org.geneontology.jena.SesameJena;
import org.geneontology.rules.engine.WorkingMemory;
import org.geneontology.rules.util.Bridge;
import org.semanticweb.owlapi.model.OWLAxiom;
import org.semanticweb.owlapi.model.OWLOntology;

import scala.collection.JavaConverters;

/**
 * Provide access to the Jena SPARQL engine over OWLOntology graphs.  
 * Optionally employ the Arachne reasoner to expand the graphs prior to query.
 * @author bgood
 *
 */
public class QRunner {
	Model jena;
	ArachneAccessor arachne;
	WorkingMemory wm;
	/**
	 * 
	 */
	public QRunner(OWLOntology tbox, OWLOntology abox, boolean add_inferences, boolean add_property_definitions, boolean add_class_definitions) {
		if(add_inferences) {
			System.out.println("Setting up Arachne reasoner, extracting rules from tbox");
			if(abox!=null) {
				//pull out any rules from abox.. and add to tbox
				Set<OWLAxiom> littlet = abox.getTBoxAxioms(null);
				if(littlet!=null) {
					for(OWLAxiom a : littlet) {
						tbox.getOWLOntologyManager().addAxiom(tbox, a);
					}
				}
			}
			arachne = new ArachneAccessor(tbox);
			if(abox!=null) {
				System.out.println("Applying rules to expand the abox graph");
				wm = arachne.createInferredModel(abox, add_property_definitions, add_class_definitions);			
				System.out.println("Making Jena model from inferred graph");
				jena = makeJenaModel(wm);
			}
		}else {
			System.out.println("Making Jena model (no inferred relations, no tbox)");
			jena = makeJenaModel(abox, null);
		}
	}

	public QRunner(OWLOntology abox) {
		System.out.println("Setting up Jena model for query.  Only including Abox ontology, no reasoning");
		jena = makeJenaModel(abox, null);
	}

	Model makeJenaModel(WorkingMemory wm) {
		Model model = ModelFactory.createDefaultModel();
		model.add(JavaConverters.setAsJavaSetConverter(wm.facts()).asJava().stream()
				.map(t -> model.asStatement(Bridge.jenaFromTriple(t))).collect(Collectors.toList()));
		return model;
	}

	Model makeJenaModel(OWLOntology abox, OWLOntology tbox) {
		Model model = ModelFactory.createDefaultModel();
		Set<Statement> a_statements = JavaConverters.setAsJavaSetConverter(SesameJena.ontologyAsTriples(abox)).asJava();		
		for(Statement s : a_statements) {
			model.add(s);
		}
		if(tbox!=null) {
			Set<Statement> t_statements = JavaConverters.setAsJavaSetConverter(SesameJena.ontologyAsTriples(tbox)).asJava();
			for(Statement s : t_statements) {
				model.add(s);
			}
		}	
		return model;
	}

	int nTriples() {
		int n = 0;
		String q = null;
		try {
			q = IOUtils.toString(App.class.getResourceAsStream("triple_count.rq"), StandardCharsets.UTF_8);
		} catch (IOException e) {
			System.out.println("Could not load SPARQL query from jar \n"+e);
		}
		QueryExecution qe = QueryExecutionFactory.create(q, jena);
		ResultSet results = qe.execSelect();
		if (results.hasNext()) {
			QuerySolution qs = results.next();
			Literal s = qs.getLiteral("triples");
			n = s.getInt();
		}
		qe.close();
		return n;
	}

	boolean isConsistent() {
		boolean consistent = true;
		String q = null;
		try {
			q = IOUtils.toString(App.class.getResourceAsStream("consistency_check.rq"), StandardCharsets.UTF_8);
		} catch (IOException e) {
			System.out.println("Could not load SPARQL query from jar \n"+e);
		}
		QueryExecution qe = QueryExecutionFactory.create(q, jena);
		ResultSet results = qe.execSelect();
		if (results.hasNext()) {
			QuerySolution qs = results.next();
			Literal s = qs.getLiteral("triples");
			if(s.getInt()==0) {
				consistent = true;
			}else{
				consistent = false;
			}
		}
		qe.close();
		return consistent;
	}

	Set<String> getUnreasonableEntities() {
		Set<String> unreasonable = new HashSet<String>();
		String q = null;
		try {
			q = IOUtils.toString(App.class.getResourceAsStream("unreasonable_query.rq"), StandardCharsets.UTF_8);
		} catch (IOException e) {
			System.out.println("Could not load SPARQL query from jar \n"+e);
		}
		QueryExecution qe = QueryExecutionFactory.create(q, jena);
		ResultSet results = qe.execSelect();
		while (results.hasNext()) {
			QuerySolution qs = results.next();
			Resource r = qs.getResource("s");
			unreasonable.add(r.getURI());
		}
		qe.close();
		return unreasonable;
	}
	
	/**
#infer that an entity (either protein or protein complex) E enables a reaction R2
#if R1 provides direct input for R2 
#and R1 has output E1
#and R2 has input E2
#and E1 = E2
#delete { ?reaction2 obo:RO_0002233 ?input . 
 ## must also delete annotations added for these assertions 
#  ?anno_node owl:annotatedProperty obo:RO_0002233 .
 # ?anno_node ?prop ?c 
# }  
select ?reaction2 obo:RO_0002333 ?input   # for update
	 * @return
	 */
	class InferredEnabler {
		String reaction_uri;
		String enabler_uri;
		InferredEnabler(String r_uri, String e_uri){
			reaction_uri = r_uri;
			enabler_uri = e_uri;
		}
	}
	
	Set<InferredEnabler> getInferredEnablers() {
		Set<InferredEnabler> ie = new HashSet<InferredEnabler>();
		String query = null;
		try {
			query = IOUtils.toString(App.class.getResourceAsStream("query2update_enabled_by.rq"), StandardCharsets.UTF_8);
		} catch (IOException e) {
			System.out.println("Could not load SPARQL update from jar \n"+e);
		}
		QueryExecution qe = QueryExecutionFactory.create(query, jena);
		ResultSet results = qe.execSelect();
		while (results.hasNext()) {
			QuerySolution qs = results.next();
			Resource reaction = qs.getResource("reaction2"); //?reaction2 ?input
			Resource enabler = qs.getResource("input");
			ie.add(new InferredEnabler(reaction.getURI(), enabler.getURI()));
		}
		qe.close();
		return ie;
	}	
	
	int addInferredRegulators() {
		int n = nTriples();
		String updatePR = null;
		String updateNR = null;
		String updateNR2 = null;
		String updateR = null;
		try {
			updatePR = IOUtils.toString(App.class.getResourceAsStream("update_positive_regulation.rq"), StandardCharsets.UTF_8);
			updateNR = IOUtils.toString(App.class.getResourceAsStream("update_negative_regulation.rq"), StandardCharsets.UTF_8);
			updateNR2 = IOUtils.toString(App.class.getResourceAsStream("update_negative_regulation_by_binding.rq"), StandardCharsets.UTF_8);
			updateR = IOUtils.toString(App.class.getResourceAsStream("update_regulation.rq"), StandardCharsets.UTF_8);
			//	count = IOUtils.toString(App.class.getResourceAsStream("count_enabled_by.rq"), StandardCharsets.UTF_8);
		} catch (IOException e) {
			System.out.println("Could not load SPARQL update from jar \n"+e);
		}
		UpdateAction.parseExecute(updatePR, jena) ;
		UpdateAction.parseExecute(updateNR, jena) ;
		UpdateAction.parseExecute(updateNR2, jena) ;
		UpdateAction.parseExecute(updateR, jena) ;
		return nTriples()-n;
	}
	
	int deleteEntityLocations() {
		int n = 0;
		String update = null;
		String count = null;
		try {
			update = IOUtils.toString(App.class.getResourceAsStream("delete_entity_locations.rq"), StandardCharsets.UTF_8);
			count = IOUtils.toString(App.class.getResourceAsStream("count_located_in.rq"), StandardCharsets.UTF_8);
		} catch (IOException e) {
			System.out.println("Could not load SPARQL update from jar \n"+e);
		}
		//before
		int n_before = count(count);
		UpdateAction.parseExecute(update, jena) ;
		int n_after = count(count);
		n= n_after-n_before;
		return n;
	}
	
	int count(String sparql_count_query) {
		int n = 0;
		QueryExecution qe = QueryExecutionFactory.create(sparql_count_query, jena);
		ResultSet results = qe.execSelect();
		if (results.hasNext()) {
			QuerySolution qs = results.next();
			Literal s = qs.getLiteral("c"); 
			n = s.getInt();
		}
		qe.close();
		return n;
	}
	
	/**
	 * Writes whatever is currently in the jena model to a file
	 * @param filename
	 * @param format
	 * @throws IOException 
	 */
	void dumpModel(String filename, String format) throws IOException {
		FileOutputStream o = new FileOutputStream(filename);
		jena.write(o, format);
		o.close();
	}
	void dumpModel(File file, String format) throws IOException {
		FileOutputStream o = new FileOutputStream(file);
		jena.write(o, format);
		o.close();
	}

}
