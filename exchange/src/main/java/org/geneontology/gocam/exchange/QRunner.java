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
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
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
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.update.UpdateAction;
import org.apache.jena.update.UpdateFactory;
import org.apache.jena.update.UpdateRequest;
import org.geneontology.jena.SesameJena;
import org.geneontology.rules.engine.Explanation;
import org.geneontology.rules.engine.Triple;
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

	public QRunner (WorkingMemory wm) {
		jena = makeJenaModel(wm);
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

	Map<String, Set<String>> getPathways() {
		Map<String, Set<String>> pathway_type = new HashMap<String, Set<String>>();
		String q = null;
		try {
			q = IOUtils.toString(App.class.getResourceAsStream("get_pathways.rq"), StandardCharsets.UTF_8);
		} catch (IOException e) {
			System.out.println("Could not load SPARQL query from jar \n"+e);
		}
		QueryExecution qe = QueryExecutionFactory.create(q, jena);
		ResultSet results = qe.execSelect();
		while (results.hasNext()) {
			QuerySolution qs = results.next();
			Resource pathway = qs.getResource("pathway");
			Resource parent =  qs.getResource("type");
			Set<String> ps = pathway_type.get(pathway.getURI());
			if(ps == null) { ps = new HashSet<String>();}
			ps.add(parent.getURI());
			pathway_type.put(pathway.getURI(), ps);
		}
		qe.close();
		return pathway_type;
	}
	
	Map<String, Set<String>> getFunctions() {
		Map<String, Set<String>> function_type = new HashMap<String, Set<String>>();
		String q = null;
		try {
			q = IOUtils.toString(App.class.getResourceAsStream("get_functions.rq"), StandardCharsets.UTF_8);
		} catch (IOException e) {
			System.out.println("Could not load SPARQL query from jar \n"+e);
		}
		QueryExecution qe = QueryExecutionFactory.create(q, jena);
		ResultSet results = qe.execSelect();
		while (results.hasNext()) {
			QuerySolution qs = results.next();
			Resource pathway = qs.getResource("function");
			Resource parent =  qs.getResource("type");
			Set<String> ps = function_type.get(pathway.getURI());
			if(ps == null) { ps = new HashSet<String>();}
			ps.add(parent.getURI());
			function_type.put(pathway.getURI(), ps);
		}
		qe.close();
		return function_type;
	}
	
	Map<String, Integer> getRelations() {
		Map<String, Integer> relations = new HashMap<String, Integer>();
		String q = null; String q_e = null;
		try {
			q = IOUtils.toString(App.class.getResourceAsStream("get_function_relations.rq"), StandardCharsets.UTF_8);
			q_e = IOUtils.toString(App.class.getResourceAsStream("get_entity_function_relations.rq"), StandardCharsets.UTF_8);

		} catch (IOException e) {
			System.out.println("Could not load SPARQL query from jar \n"+e);
		}
		QueryExecution qe = QueryExecutionFactory.create(q, jena);
		ResultSet results = qe.execSelect();
		while (results.hasNext()) {
			QuerySolution qs = results.next();
			Resource relation = qs.getResource("relation");
			Integer c = relations.get(relation.getURI());
			if(c==null) { c = 0;}
			c++;
			relations.put(relation.getURI(), c);
		}
		qe.close();
		qe = QueryExecutionFactory.create(q_e, jena);
		results = qe.execSelect();
		while (results.hasNext()) {
			QuerySolution qs = results.next();
			Resource relation = qs.getResource("relation");
			Integer c = relations.get(relation.getURI());
			if(c==null) { c = 0;}
			c++;
			relations.put(relation.getURI(), c);
		}
		qe.close();
		return relations;
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
//			<http://www.w3.org/1999/02/22-rdf-syntax-ns#type> <http://www.w3.org/2002/07/owl#Nothing>	
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
		String pathway_uri;
		public InferredEnabler(String reaction2_uri, String reaction1_uri, String enabler_uri, String pathway_uri) {
			this.reaction2_uri = reaction2_uri;
			this.reaction1_uri = reaction1_uri;
			this.enabler_uri = enabler_uri;
			this.pathway_uri = pathway_uri;
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
			//Resource pathway = qs.getResource("pathway");
			ie.add(new InferredEnabler(reaction2.getURI(), reaction1.getURI(), enabler.getURI(), null));
		}
		qe.close();
		return ie;
	}	
	
	class InferredRegulator {
		String reaction1_uri;
		String reaction2_uri;
		String prop_uri;
		String pathway_uri;
		String entity_uri;
		InferredRegulator(String r1_uri, String p_uri, String r2_uri, String pathway, String entity){
			reaction1_uri = r1_uri;
			prop_uri = p_uri;
			reaction2_uri = r2_uri;
			pathway_uri = pathway;
			entity_uri = entity;
		}
	}
	
	Set<InferredRegulator> getInferredRegulatorsQ1() {
		Set<InferredRegulator> ir = new HashSet<InferredRegulator>();
		String query = null;
		try {		
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
			Resource pathway = qs.getResource("pathway");
			Resource entity = qs.getResource("entityZ");
			//reaction1  regulated somehow by reaction 2
			String pathway_uri = "";
			if(pathway!=null) {
				pathway_uri = pathway.getURI();
			}
			ir.add(new InferredRegulator(reaction1.getURI(), property.getURI(), reaction2.getURI(), pathway_uri, entity.getURI()));
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
			Resource pathway = qs.getResource("pathway");
			//reaction1  regulated somehow by reaction 2
			String pathway_uri = "";
			if(pathway!=null) {
				pathway_uri = pathway.getURI();
			}
			ir.add(new InferredRegulator(reaction1.getURI(), GoCAM.directly_negatively_regulates.getIRI().toString(), reaction2.getURI(), pathway_uri, ""));
		}
		qe.close();
		return ir;
	}
	
	Set<String> findBindingReactions() {
		Set<String> binders = new HashSet<String>();
		String query = null;
		try {		
			query = IOUtils.toString(App.class.getResourceAsStream("query2update_binding.rq"), StandardCharsets.UTF_8);
		} catch (IOException e) {
			System.out.println("Could not load SPARQL update from jar \n"+e);
		}
		QueryExecution qe = QueryExecutionFactory.create(query, jena);
		ResultSet results = qe.execSelect();
		while (results.hasNext()) {
			QuerySolution qs = results.next();
			Resource reaction = qs.getResource("reaction"); 
			binders.add(reaction.getURI());
		}
		qe.close();
		return binders;
	}
	
	public class InferredTransport{
		String reaction_uri;
		String input_loc_uri;
		String output_loc_uri;
		String input_loc_class_uri;
		String output_loc_class_uri;
		String thing_type_uri;
		String pathway_uri;
	}
	
	Set<InferredTransport> findTransportReactions() {
		Set<InferredTransport> transports = new HashSet<InferredTransport>();
		String query = null;
		try {		
			query = IOUtils.toString(App.class.getResourceAsStream("query2update_localization.rq"), StandardCharsets.UTF_8);
		} catch (IOException e) {
			System.out.println("Could not load SPARQL update from jar \n"+e);
		}
		QueryExecution qe = QueryExecutionFactory.create(query, jena);
		ResultSet results = qe.execSelect();
		while (results.hasNext()) {
			QuerySolution qs = results.next();
			Resource pathway = qs.getResource("pathway");
			
			String pathway_uri = "";
			if(pathway!=null) {pathway_uri = pathway.getURI();}
			Resource reaction = qs.getResource("reaction"); 
			Resource start = qs.getResource("start_location_type"); 
			Resource end = qs.getResource("end_location_type"); 
			Resource start_loc_instance = qs.getResource("start_location"); 
			Resource end_loc_instance = qs.getResource("end_location"); 
			Resource thing_type = qs.getResource("thing_type"); 
			InferredTransport t = new InferredTransport();
			t.reaction_uri = reaction.getURI();
			t.input_loc_class_uri = start.getURI();
			t.output_loc_class_uri = end.getURI();
			t.thing_type_uri = thing_type.getURI();
			t.input_loc_uri = start_loc_instance.getURI();
			t.output_loc_uri = end_loc_instance.getURI();
			t.pathway_uri = pathway_uri;
			transports.add(t);
		}
		qe.close();
		return transports;
	}
	
	public class InferredOccursIn {
		String pathway_uri;
		String reaction_uri;
		Set<String> location_type_uris = new HashSet<String>();
		Map<String, String> entity_location_instances = new HashMap<String, String>();
	}
	//?reaction ?prop ?location_instance ?location_type ?entity	
	Set<InferredOccursIn> findOccursInReaction(){
		Set<InferredOccursIn> occurs = new HashSet<InferredOccursIn>();
		String query = null;
		try {		
			query = IOUtils.toString(App.class.getResourceAsStream("query2update_occurs_in.rq"), StandardCharsets.UTF_8);
		} catch (IOException e) {
			System.out.println("Could not load SPARQL update from jar \n"+e);
		}
		QueryExecution qe = QueryExecutionFactory.create(query, jena);
		ResultSet results = qe.execSelect();
		
		Map<String, InferredOccursIn> reaction_locinfo = new HashMap<String, InferredOccursIn>();
		while (results.hasNext()) {
			QuerySolution qs = results.next();
			Resource pathway = qs.getResource("pathway");
			String pathway_uri = pathway.getURI();
			Resource reaction = qs.getResource("reaction");
			String reaction_uri = reaction.getURI();
			//Resource reaction_type = qs.getResource("reaction_type"); 
			//Resource prop = qs.getResource("prop"); 
			Resource location_instance = qs.getResource("location_instance"); 
			String location_instance_uri = location_instance.getURI();
			Resource location_type = qs.getResource("location_type"); 
			String location_type_uri = location_type.getURI();
			Resource entity = qs.getResource("entity"); 
			String entity_uri = entity.getURI();

			if(reaction_uri==null){
				continue;
			}
			InferredOccursIn o = reaction_locinfo.get(reaction_uri);
			if(o==null) {
				o = new InferredOccursIn();
				o.reaction_uri = reaction_uri;
				o.pathway_uri = pathway_uri;
			}
			
			o.location_type_uris.add(location_type_uri);
			o.entity_location_instances.put(entity_uri, location_instance_uri);
			reaction_locinfo.put(reaction_uri, o);
		}
		qe.close();
		
		for(String reaction : reaction_locinfo.keySet()) {
			InferredOccursIn o = reaction_locinfo.get(reaction);
			//check for the ones that are the all the same
			//if(o.location_type_uris.size()==1) {
				occurs.add(o);
			//}
		}
		
		return occurs;
	}
	
	public class ComplexInput {
		String pathway_uri;
		String reaction_uri;
		String property_uri;
		Map<String, Set<String>> complex_parts = new HashMap<String, Set<String>>();
	}
	
	Set<ComplexInput> findComplexInputs(){
		Set<ComplexInput> complexes = new HashSet<ComplexInput>();
		String query = null;
		try {		
			query = IOUtils.toString(App.class.getResourceAsStream("query2update_find_complex_inputs.rq"), StandardCharsets.UTF_8);
		} catch (IOException e) {
			System.out.println("Could not load SPARQL update from jar \n"+e);
		}
		QueryExecution qe = QueryExecutionFactory.create(query, jena);
		ResultSet results = qe.execSelect();
		
		Map<String, ComplexInput> reaction_property_complexes = new HashMap<String, ComplexInput>();
		while (results.hasNext()) {
			QuerySolution qs = results.next();
			Resource pathway = qs.getResource("pathway");
			String pathway_uri = "no_pathway via has_part";
			if(pathway!=null) {
				pathway_uri = pathway.getURI();
			}			
			Resource reaction = qs.getResource("reaction");
			String reaction_uri = reaction.getURI();
			Resource complex_instance = qs.getResource("complex"); 
			String complex_instance_uri = complex_instance.getURI();
			Resource complex_part = qs.getResource("complex_part"); 
			String complex_part_uri = complex_part.getURI();
			RDFNode property = qs.get("property");
			String property_uri = property.as(Property.class).getURI();
			if(reaction_uri==null){
				continue;
			}
			ComplexInput o = reaction_property_complexes.get(reaction_uri+property_uri+complex_instance_uri);
			if(o==null) {
				o = new ComplexInput();
				o.reaction_uri = reaction_uri;
				o.pathway_uri = pathway_uri;			
				o.property_uri = property_uri;
			}			
			Set<String> complex_parts = o.complex_parts.get(complex_instance_uri);
			if(complex_parts==null) {
				complex_parts = new HashSet<String>();
			}
			complex_parts.add(complex_part_uri);
			o.complex_parts.put(complex_instance_uri, complex_parts);
			reaction_property_complexes.put(reaction_uri+property_uri+complex_instance_uri, o);
		}
		qe.close();
		complexes.addAll(reaction_property_complexes.values());
		return complexes;
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
	
	int deleteCellularComponentTyping() {
		int n = nTriples();
		String update = null;
		try {
			update = IOUtils.toString(App.class.getResourceAsStream("delete_type_super_cc.rq"), StandardCharsets.UTF_8);
		} catch (IOException e) {
			System.out.println("Could not load SPARQL update from jar \n"+e);
		}
		UpdateAction.parseExecute(update, jena) ;
		int n_after = nTriples();
		n= n-n_after;
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
