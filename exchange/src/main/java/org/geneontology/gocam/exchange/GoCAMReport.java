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
import org.semanticweb.owlapi.model.OWLOntology;

/**
 * @author bgood
 *
 */
public class GoCAMReport {
	Model model = ModelFactory.createDefaultModel();
	String name = "";
	//n nodes of each type
	int mf_count = 0;
	int bp_count = 0;
	int distinct_relation_count = 0;
	int distinct_cc_count = 0;
	int complex_count = 0;
	int distinct_protein_count = 0;
	int distinct_chemical_count = 0;
	//node to class mappings
	Map<String, Set<String>> pathway_types = new HashMap<String, Set<String>>();
	Map<String, Set<String>> pathway_inferred_types = new HashMap<String, Set<String>>();
	Map<String, Set<String>> pathway_asserted_types = new HashMap<String, Set<String>>();
	Map<String, Set<String>> function_types = new HashMap<String, Set<String>>();
	Map<String, Set<String>> function_inferred_types = new HashMap<String, Set<String>>();
	Map<String, Set<String>> function_asserted_types = new HashMap<String, Set<String>>();
	Map<String, Set<String>> complex_types = new HashMap<String, Set<String>>();
	Map<String, Set<String>> complex_inferred_types = new HashMap<String, Set<String>>();
	Map<String, Set<String>> complex_asserted_types = new HashMap<String, Set<String>>();
	Map<String, Set<String>> all_types = new HashMap<String, Set<String>>();
	//counts inferences and classifications
	int mf_unclassified = 0;
	int mf_inferred_type = 0;
	int mf_deepened = 0;
	int bp_unclassified = 0;
	int bp_inferred_type = 0;
	int bp_deepened = 0;
	int cc_unclassified = 0;
	int complex_unclassified = 0;
	int complex_inferred_type = 0;
	int complex_deepened = 0;
	int n_reactions = 0;
	int n_pathways = 0;
	//for convenience (and not passing around ontologies for later)
	Map<String, String> uri_term = new HashMap<String, String>();

	public GoCAMReport() {};


	public String makeSimpleContentReport() {
		String row = bp_count+"\t"+mf_count+"\t"+complex_count+"\t"+distinct_protein_count+"\t"+distinct_chemical_count+"\t"+distinct_cc_count+"\t"+distinct_relation_count+"\n";
		return row;
	}
	
	public String makeMappingReport() {
		String report = "";
		for(String pathway_uri : pathway_types.keySet()) {
			report+="Pathway\t"+uri_term.get(pathway_uri)+"\t";
			if(pathway_asserted_types!=null&&pathway_asserted_types.size()>0) {
				Set<String> types = pathway_asserted_types.get(pathway_uri);
				if(types!=null) {
					for(String type : types) {
						String label = uri_term.get(type);
						report+=label+",";
					}
				}
			}
			report+="\t";
			if(pathway_inferred_types!=null&&pathway_inferred_types.size()>0) {
				Set<String> types = pathway_inferred_types.get(pathway_uri);
				if(types!=null) {
					for(String type : types) {
						String label = uri_term.get(type);
						report+=label+",";
					}
				}
			}
			report+="\n";			
		}

		for(String function_uri : function_types.keySet()) {
			report+="function\t"+uri_term.get(function_uri)+"\t";
			if(function_asserted_types!=null&&function_asserted_types.size()>0) {
				Set<String> types = function_asserted_types.get(function_uri);
				if(types!=null) {
					for(String type : types) {
						String label = uri_term.get(type);
						report+=label+",";
					}
				}
			}
			report+="\t";
			if(function_inferred_types!=null&&function_inferred_types.size()>0) {
				Set<String> types = function_inferred_types.get(function_uri);
				if(types!=null) {
					for(String type : types) {
						String label = uri_term.get(type);
						report+=label+",";
					}
				}
			}

			report+="\n";			
		}

		for(String complex_uri : complex_types.keySet()) {
			report+="complex\t"+uri_term.get(complex_uri)+"\t";
			if(complex_asserted_types!=null&&complex_asserted_types.size()>0) {
				Set<String> types = complex_asserted_types.get(complex_uri);
				if(types!=null) {
					for(String type : types) {
						String label = uri_term.get(type);
						report+=label+",";
					}
				}
			}
			report+="\t";
			if(complex_inferred_types!=null&&complex_inferred_types.size()>0) {
				Set<String> types = complex_inferred_types.get(complex_uri);
				if(types!=null) {
					for(String type : types) {
						String label = uri_term.get(type);
						report+=label+",";
					}
				}
			}
			report+="\n";			
		}

		return report;
	}

	/**
	 * This and the SPARQL queries that underlie it expect a WorkingMemory object containing
	 * a reasoned set of triples that includes the ontology graphs.  
	 * @param wm
	 */
	public GoCAMReport(WorkingMemory wm, String n, GoCAM go_cam, OWLOntology go) {
		name = n;
		QRunner qr = new QRunner(wm);
		pathway_types = qr.getPathways();
		bp_count = pathway_types.keySet().size();
		function_types = qr.getFunctions();
		mf_count = function_types.keySet().size();
		complex_types = qr.getComplexClasses();
		complex_count = complex_types.keySet().size();
		//function inferences
		for(String function_node : function_types.keySet()) {
			boolean no_manual_type = false;
			boolean counted = false;
			for(String type : function_types.get(function_node)) {
				if(type.equals("http://purl.obolibrary.org/obo/GO_0003674")) {
					if(!counted) {
						mf_unclassified++;
					}
					no_manual_type = true;
					counted = true;
				}
			}
			counted = false;
			for(String type : function_types.get(function_node)) {
				if(!type.equals("http://purl.obolibrary.org/obo/GO_0003674")) {
					boolean inferred = wasInferred(wm, function_node, "http://www.w3.org/1999/02/22-rdf-syntax-ns#type", type);
					if(inferred) {		
						Set<String> itypes = function_inferred_types.get(function_node);
						if(itypes==null) {itypes = new HashSet<String>();}
						itypes.add(type);
						function_inferred_types.put(function_node, itypes);
						if(!counted) {
							if(!no_manual_type) {
								mf_deepened++;
							}else {
								mf_inferred_type++;
							}
							counted = true;
						}
					}else {
						Set<String> atypes = function_asserted_types.get(function_node);
						if(atypes==null) {atypes = new HashSet<String>();}
						atypes.add(type);
						function_asserted_types.put(function_node, atypes);
					}
				}
			}
		}
		//bp inferences GO_0008150
		for(String pathway_node : pathway_types.keySet()) {
			boolean no_manual_type = false;
			boolean counted = false;
			for(String type : pathway_types.get(pathway_node)) {
				if(type.equals("http://purl.obolibrary.org/obo/GO_0008150")) {
					if(!counted) {
						bp_unclassified++;
					}
					no_manual_type = true;
					counted = true;
				}
			}
			counted = false;
			for(String type : pathway_types.get(pathway_node)) {
				if(!type.equals("http://purl.obolibrary.org/obo/GO_0008150")) {
					boolean inferred = wasInferred(wm, pathway_node, "http://www.w3.org/1999/02/22-rdf-syntax-ns#type", type);
					if(inferred) {		
						Set<String> itypes = pathway_inferred_types.get(pathway_node);
						if(itypes==null) {itypes = new HashSet<String>();}
						itypes.add(type);
						pathway_inferred_types.put(pathway_node, itypes);
						if(!counted) {
							if(!no_manual_type) {
								bp_deepened++;
							}else {
								bp_inferred_type++;
							}
							counted = true;
						}
					}else {
						Set<String> atypes = pathway_asserted_types.get(pathway_node);
						if(atypes==null) {atypes = new HashSet<String>();}
						atypes.add(type);
						pathway_asserted_types.put(pathway_node, atypes);
					}
				}
			}
		}
		//complex GO_0032991
		for(String complex_node : complex_types.keySet()) {
			boolean no_manual_type = false;
			boolean counted = false;
			for(String type : complex_types.get(complex_node)) {
				if(type.equals("http://purl.obolibrary.org/obo/GO_0032991")) {
					if(!counted) {
						complex_unclassified++;
					}
					no_manual_type = true;
					counted = true;
				}
			}
			counted = false;
			for(String type : complex_types.get(complex_node)) {
				if(!type.equals("http://purl.obolibrary.org/obo/GO_0032991")) {
					boolean inferred = wasInferred(wm, complex_node, "http://www.w3.org/1999/02/22-rdf-syntax-ns#type", type);
					if(inferred) {		
						Set<String> itypes = complex_inferred_types.get(complex_node);
						if(itypes==null) {itypes = new HashSet<String>();}
						itypes.add(type);
						complex_inferred_types.put(complex_node, itypes);
						if(!counted) {
							if(!no_manual_type) {
								complex_deepened++;
							}else {
								complex_inferred_type++;
							}
							counted = true;
						}
					}else {
						Set<String> atypes = complex_asserted_types.get(complex_node);
						if(atypes==null) {atypes = new HashSet<String>();}
						atypes.add(type);
						complex_asserted_types.put(complex_node, atypes);
					}
				}
			}
		}

		Map<String, Integer> relation_n = qr.getRelations();
		distinct_relation_count = relation_n.keySet().size();
		Map<String, Integer> protein_n = qr.getProteins();
		distinct_protein_count = protein_n.keySet().size();
		Map<String, Integer> chemical_n = qr.getChemicals();
		distinct_chemical_count = chemical_n.keySet().size();
		Map<String, Integer> complex_n = qr.getComplexes();
		complex_count = complex_n.keySet().size();	
		Map<String, Integer> component_n = qr.getComponents();
		distinct_cc_count = component_n.keySet().size();

		all_types.putAll(complex_types);
		all_types.putAll(pathway_types);
		all_types.putAll(function_types);
		for(String node_uri : all_types.keySet()){
			String label = Helper.getaLabel(node_uri, go_cam.go_cam_ont);
			uri_term.put(node_uri, label);
			for(String type_uri : all_types.get(node_uri)) {
				String type_label = Helper.getaLabel(type_uri, go);
				uri_term.put(type_uri, type_label);
			}
		}
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
