/**
 * 
 */
package org.geneontology.gocam.exchange;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.apache.jena.graph.Triple;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.Statement;
import org.geneontology.rules.engine.WorkingMemory;
import org.geneontology.rules.util.Bridge;
import org.semanticweb.owlapi.model.IRI;

/**
 * @author bgood
 *
 */
public class GoCAMReport {
	Model model = ModelFactory.createDefaultModel();
	String name = "";
	int mf_unclassified = 0;
	int bp_unclassified = 0;
	int cc_unclassified = 0;
	int complex_unclassified = 0;
	
	int mf_count = 0;
	int bp_count = 0;
	int relation_count = 0;
	int cc_count = 0;
	int complex_count = 0;
	int protein_count = 0;
	int chemical_count = 0;

	Set<String> mf_labels = new HashSet<String>();
	Set<String> mf_to_root_labels = new HashSet<String>();
	Set<String> bp_labels = new HashSet<String>();
	Set<String> bp_to_root_labels = new HashSet<String>();
	Set<String> cc_labels = new HashSet<String>();
	Set<String> cc_to_root_labels = new HashSet<String>();
	Set<String> complex_labels = new HashSet<String>();
	Set<String> complex_to_root_labels = new HashSet<String>();
	public GoCAMReport() {};
	

	/**
	 * This and the SPARQL queries that underlie it expect a WorkingMemory object containing
	 * a reasoned set of triples that includes the ontology graphs.  
	 * @param wm
	 */
	public GoCAMReport(WorkingMemory wm, String n) {
		name = n;
		QRunner qr = new QRunner(wm);
		Map<String, Set<String>> pathway_types = qr.getPathways();
		bp_count = pathway_types.keySet().size();
		Map<String, Set<String>> function_types = qr.getFunctions();
		mf_count = function_types.keySet().size();
		Map<String, Set<String>> complex_types = qr.getComplexClasses();
		complex_count = complex_types.keySet().size();
		
		for(String function_node : function_types.keySet()) {
			if(function_types.get(function_node).size()==1) {
			for(String type : function_types.get(function_node)) {
				if(type.equals("http://purl.obolibrary.org/obo/GO_0003674")) {
					mf_unclassified++;
				}
			}
			}
		}
		for(String bp_node : pathway_types.keySet()) {
			if(pathway_types.get(bp_node).size()==1) {
			for(String type : pathway_types.get(bp_node)) {
				if(type.equals("http://purl.obolibrary.org/obo/GO_0008150")) {
					bp_unclassified++;
				}
			}
			}
		}
		for(String complex_node : complex_types.keySet()) {
			if(complex_types.get(complex_node).size()==1) {
			for(String type : complex_types.get(complex_node)) {
				if(type.equals("http://purl.obolibrary.org/obo/GO_0032991")) {
					complex_unclassified++;
				}else {
					boolean inferred = wasInferred(wm, complex_node, "http://www.w3.org/1999/02/22-rdf-syntax-ns#type", type);
					System.out.println(inferred +" "+ complex_node+"\t"+type);
				}
			}
			}
		}
		
		Map<String, Integer> relation_n = qr.getRelations();
		relation_count = relation_n.keySet().size();
		Map<String, Integer> protein_n = qr.getProteins();
		protein_count = protein_n.keySet().size();
		Map<String, Integer> chemical_n = qr.getChemicals();
		chemical_count = chemical_n.keySet().size();
		Map<String, Integer> complex_n = qr.getComplexes();
		complex_count = complex_n.keySet().size();
		
		
		Map<String, Integer> component_n = qr.getComponents();
		cc_count = component_n.keySet().size();
		
		System.out.println("Unique classes/properties asserted in model:");
		System.out.println("pathways: "+bp_count);
		System.out.println("unclassified pathways: "+bp_unclassified);
		System.out.println("functions: "+mf_count);
		System.out.println("unclassified functions: "+mf_unclassified);
		System.out.println("relations: "+relation_count);
		System.out.println("proteins: "+protein_count);
		System.out.println("chemicals: "+chemical_count);
		System.out.println("complexes: "+complex_count);
		System.out.println("unclassified complexes: "+complex_unclassified);
		System.out.println("Cellular components: "+cc_count);
		System.out.println(component_n.keySet());
	}
	
	
	public boolean wasInferred(WorkingMemory wm, String subject, String predicate, String object) {		
		Resource s = model.createResource(subject);
		Property p = model.createProperty(predicate);
		Resource o = model.createResource(object);
		Statement statement = model.createStatement(s, p, o);
		Triple jena_triple = statement.asTriple();		 
		org.geneontology.rules.engine.Triple arachne_triple = Bridge.tripleFromJena(jena_triple);
		boolean inferred = !wm.asserted().contains(arachne_triple);
		return inferred;
	}
	
	/**
	 * Build a report for this go_cam using an Arachne-inferred set of triples (WorkingMemory) for that model
	 * @param wm
	 * @param go_cam
	 
	public GoCAMReport(WorkingMemory wm, GoCAM go_cam){
		String mf_uri = "<"+GoCAM.molecular_function.getIRI().toURI().toString()+">";
		String bp_uri = "<"+GoCAM.bp_class.getIRI().toURI().toString()+">";
		String cc_uri = "<"+GoCAM.cc_class.getIRI().toURI().toString()+">";
		String complex_uri = "<"+GoCAM.go_complex.getIRI().toURI().toString()+">";
		scala.collection.Iterator<Triple> triples = wm.facts().toList().iterator();
		//(wm.asserted().contains(triple)) 
		while(triples.hasNext()) {				
			Triple triple = triples.next();
			if(triple.p().toString().equals("<http://www.w3.org/1999/02/22-rdf-syntax-ns#type>")||
					triple.p().toString().equals("<http://arachne.geneontology.org/indirect_type>")) {
				//mf
				if(triple.o().toString().equals(mf_uri)) {
					String mf_node = triple.s().toString().replace("<", "").replace(">", "");
					String name = Helper.getaLabel(mf_node, go_cam.go_cam_ont);
					mf_labels.add(name);
					if(wm.asserted().contains(triple)) { //should not have direct assertions to root classes unless there isn't anything else
						mf_unclassified++;
						mf_to_root_labels.add(name);
					}
				}
				//bp
				if(triple.o().toString().equals(bp_uri)) {
					String bp_node = triple.s().toString().replace("<", "").replace(">", "");
					String name = Helper.getaLabel(bp_node, go_cam.go_cam_ont);
					bp_labels.add(name);
					if(wm.asserted().contains(triple)) { //should not have direct assertions to root classes unless there isn't anything else
						bp_unclassified++;
						bp_to_root_labels.add(name);
					}
				}
				//complex
				if(triple.o().toString().equals(complex_uri)) {
					String complex_node = triple.s().toString().replace("<", "").replace(">", "");
					String name = Helper.getaLabel(complex_node, go_cam.go_cam_ont);
					complex_labels.add(name);
					if(wm.asserted().contains(triple)) { //should not have direct assertions to root classes unless there isn't anything else
						complex_unclassified++;
						complex_to_root_labels.add(name);
					}
				}
			}
			//TODO look at classes used in other relations
			else {
				if(triple.p().toString().equals("<"+GoCAM.occurs_in.getIRI().toURI().toString()+">")) {
					//note need to hop over one more triple from here a occurs_in b , b type_of c.  
				}
			}
		}
	}*/

}
