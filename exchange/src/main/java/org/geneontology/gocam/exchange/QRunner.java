/**
 * 
 */
package org.geneontology.gocam.exchange;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
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
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.OWLAxiom;
import org.semanticweb.owlapi.model.OWLDataFactory;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyCreationException;
import org.semanticweb.owlapi.model.OWLOntologyManager;

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
	 * @throws OWLOntologyCreationException 
	 * 
	 */
	public QRunner(Collection<OWLOntology> tboxes, OWLOntology abox, boolean add_inferences, boolean add_property_definitions, boolean add_class_definitions) throws OWLOntologyCreationException {
		if(add_inferences) {
			System.out.println("Setting up Arachne reasoner for Qrunner, extracting rules from tbox");
			if(abox!=null) {
				//pull out any rules from abox.. and add to tbox
				Set<OWLAxiom> littlet = abox.getTBoxAxioms(null);
				if(littlet!=null) {
					OWLOntologyManager man = OWLManager.createOWLOntologyManager();
					OWLOntology t = man.createOntology();
					for(OWLAxiom a : littlet) {
						man.addAxiom(t, a);
					}
					tboxes.add(t);
				}
			}
			arachne = new ArachneAccessor(tboxes);
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
		System.out.println("Setting up Jena model for query.  Only including input Abox ontology, no reasoning");
		jena = makeJenaModel(abox, null);
	}

//	public void updateAboxAndApplyRules(OWLOntology abox, boolean add_property_definitions, boolean add_class_definitions) {
//		//this is needed to get the little type and subclass assignments made in go_cams to stitch things together
//		arachne.makeExpandedRuleSet(abox);
//		//now apply the rules to generate inferences
//		wm = arachne.createInferredModel(abox, add_property_definitions, add_class_definitions);	
//		//move triples to jena for query
//		jena = makeJenaModel(wm);
//	}
	
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
	
	Set<String> getTypes(String i_uri) {
		Set<String> types = new HashSet<String>();
		String q = 
				"prefix rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#> " + 
				"SELECT ?type WHERE { " 
						+"<"+ i_uri + ">	rdf:type ?type  " + 
				"   } ";
		QueryExecution qe = QueryExecutionFactory.create(q, jena);
		ResultSet results = qe.execSelect();
		while (results.hasNext()) {
			QuerySolution qs = results.next();
			Resource r = qs.getResource("type");
			types.add(r.getURI());
		}
		qe.close();
		return types;
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
		String reaction2_uri;
		String reaction1_uri;
		String enabler_uri;
		public InferredEnabler(String reaction2_uri, String reaction1_uri, String enabler_uri) {
			this.reaction2_uri = reaction2_uri;
			this.reaction1_uri = reaction1_uri;
			this.enabler_uri = enabler_uri;
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
			Resource reaction1 = qs.getResource("reaction1"); 
			Resource reaction2 = qs.getResource("reaction2"); 
			Resource enabler = qs.getResource("input");
			ie.add(new InferredEnabler(reaction2.getURI(), reaction1.getURI(), enabler.getURI()));
		}
		qe.close();
		return ie;
	}	
	
	class InferredRegulator {
		String reaction1_uri;
		String reaction2_uri;
		String prop_uri;
		InferredRegulator(String r1_uri, String p_uri, String r2_uri){
			reaction1_uri = r1_uri;
			prop_uri = p_uri;
			reaction2_uri = r2_uri;
		}
	}
	
	Set<InferredRegulator> getInferredRegulatorsQ1() {
		Set<InferredRegulator> ir = new HashSet<InferredRegulator>();
		String query = null;
		try {
		
			//updateNR2 = IOUtils.toString(App.class.getResourceAsStream("update_negative_regulation_by_binding.rq"), StandardCharsets.UTF_8);
			
			query = IOUtils.toString(App.class.getResourceAsStream("query2update_regulation_1.rq"), StandardCharsets.UTF_8);
		} catch (IOException e) {
			System.out.println("Could not load SPARQL update from jar \n"+e);
		}
		QueryExecution qe = QueryExecutionFactory.create(query, jena);
		ResultSet results = qe.execSelect();
		while (results.hasNext()) {
			QuerySolution qs = results.next();
			Resource reaction1 = qs.getResource("reaction1"); 
			Resource reaction2 = qs.getResource("reaction2"); 
			Resource property = qs.getResource("prop");
			//reaction1  regulated somehow by reaction 2
			ir.add(new InferredRegulator(reaction1.getURI(), property.getURI(), reaction2.getURI()));
		}
		qe.close();
		return ir;
	}
	
	Set<InferredRegulator> getInferredRegulatorsQ2() {
		Set<InferredRegulator> ir = new HashSet<InferredRegulator>();
		String query = null;
		try {		
			//TODO revisit this query post May 2018 meeting...  weakened it as Noctua1.0 version of this took out complex has_part relations
			query = IOUtils.toString(App.class.getResourceAsStream("query2update_regulation_2_noctua1.rq"), StandardCharsets.UTF_8);
		} catch (IOException e) {
			System.out.println("Could not load SPARQL update from jar \n"+e);
		}
		QueryExecution qe = QueryExecutionFactory.create(query, jena);
		ResultSet results = qe.execSelect();
		int t = 0;
		while (results.hasNext()) {
			t++;
			QuerySolution qs = results.next();
			Resource reaction1 = qs.getResource("reaction1"); 
			Resource reaction2 = qs.getResource("reaction2"); 
			//reaction1  regulated somehow by reaction 2
			ir.add(new InferredRegulator(reaction1.getURI(), GoCAM.directly_negatively_regulates.getIRI().toString(), reaction2.getURI()));
		}
		qe.close();
		return ir;
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
	
	void deletePathwayHasPart() {
		String update1 = null;
		String update2 = null;
		try {
			update1 = IOUtils.toString(App.class.getResourceAsStream("delete_process_has_part_evidence.rq"), StandardCharsets.UTF_8);
			update2 = IOUtils.toString(App.class.getResourceAsStream("delete_process_has_part_relations.rq"), StandardCharsets.UTF_8);
		} catch (IOException e) {
			System.out.println("Could not load SPARQL update from jar \n"+e);
		}
		UpdateAction.parseExecute(update1, jena) ;
		UpdateAction.parseExecute(update2, jena) ;
		return;
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
