/**
 * 
 */
package org.geneontology.gocam.exchange;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
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
	Map<String, Set<String>> e_xrefs  = new HashMap<String, Set<String>>();
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

	//String header = "Reactome node type\tReactome Label\tCurator asserted GO types\tRule-assigned types\tInferred GO types\n";

	public String makeNRMappingReport() {
		Set<String> ids = new HashSet<String>();
		String report = "";

		Map<String, Set<String>> p_inferred_pathway_types = new HashMap<String, Set<String>>();
		if(pathway_inferred_types!=null) {
			for(String pathway_uri : pathway_inferred_types.keySet()) {
				String entity = uri_term.get(pathway_uri);
				Set<String> types = pathway_inferred_types.get(pathway_uri);
				if(types!=null) {
					Set<String> etypes = p_inferred_pathway_types.get(entity);
					if(etypes==null) { etypes = new HashSet<String>();}
					etypes.addAll(types);
					p_inferred_pathway_types.put(entity, etypes);
				}
			}
		}
		Map<String, Set<String>> p_inferred_function_types = new HashMap<String, Set<String>>();
		if(function_inferred_types!=null) {
			for(String function_uri : function_inferred_types.keySet()) {
				String entity = uri_term.get(function_uri);
				Set<String> types = function_inferred_types.get(function_uri);
				if(types!=null) {
					Set<String> etypes = p_inferred_function_types.get(entity);
					if(etypes==null) { etypes = new HashSet<String>();}
					etypes.addAll(types);
					p_inferred_function_types.put(entity, etypes);
				}
			}
		}

		Map<String, Set<String>> p_inferred_complex_types = new HashMap<String, Set<String>>();
		if(complex_inferred_types!=null) {
			for(String complex_uri : complex_inferred_types.keySet()) {
				String entity = uri_term.get(complex_uri);
				Set<String> types = complex_inferred_types.get(complex_uri);
				if(types!=null) {
					Set<String> etypes = p_inferred_complex_types.get(entity);
					if(etypes==null) { etypes = new HashSet<String>();}
					etypes.addAll(types);
					p_inferred_complex_types.put(entity, etypes);
				}
			}
		}

		for(String pathway_uri : pathway_types.keySet()) {
			if(ids.contains(uri_term.get(pathway_uri))) {
				continue;
			}else {
				ids.add(uri_term.get(pathway_uri));
			}
			report+="Biological Process\t"+uri_term.get(pathway_uri)+"\t";
			if(pathway_asserted_types!=null&&pathway_asserted_types.size()>0) {
				Set<String> rule_types = new HashSet<String>();
				Set<String> types = pathway_asserted_types.get(pathway_uri);
				if(types!=null) {
					for(String type : types) {
						if(type.equals(GoCAM.establishment_of_protein_localization.getIRI().toURI().toString())) {
							rule_types.add(type);
						}else {
							String label = uri_term.get(type);
							report+=label+",";
						}
					}
				}
				report+="\t";
				if(rule_types.size()>0) {
					for(String type : types) {
						String label = uri_term.get(type);
						report+=label+",";
					}
				}
			}else {
				report+="\t";
			}
			report+="\t";
			if(p_inferred_pathway_types!=null&&p_inferred_pathway_types.size()>0) {
				Set<String> types = p_inferred_pathway_types.get(uri_term.get(pathway_uri));
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
			if(ids.contains(uri_term.get(function_uri))) {
				continue;
			}else {
				ids.add(uri_term.get(function_uri));
			}
			report+="Molecular Function\t"+uri_term.get(function_uri)+"\t";
			if(function_asserted_types!=null&&function_asserted_types.size()>0) {
				Set<String> rule_types = new HashSet<String>();
				Set<String> types = function_asserted_types.get(function_uri);
				if(types!=null) {
					for(String type : types) {
						if(type.equals(GoCAM.protein_binding.getIRI().toURI().toString())) {
							rule_types.add(type);
						}else {
							String label = uri_term.get(type);
							report+=label+",";
						}
					}
				}
				report+="\t";
				if(rule_types.size()>0) {
					for(String type : types) {
						String label = uri_term.get(type);
						report+=label+",";
					}
				}
			}else {
				report+="\t";
			}
			report+="\t";
			if(p_inferred_function_types!=null&&p_inferred_function_types.size()>0) {
				Set<String> types = p_inferred_function_types.get(uri_term.get(function_uri));
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
			if(ids.contains(uri_term.get(complex_uri))) {
				continue;
			}else {
				ids.add(uri_term.get(complex_uri));
			}
			report+="Complex\t"+uri_term.get(complex_uri)+"\t";
			if(complex_asserted_types!=null&&complex_asserted_types.size()>0) {
				Set<String> rule_types = new HashSet<String>();
				Set<String> types = complex_asserted_types.get(complex_uri);
				if(types!=null) {
					for(String type : types) {
						if(type.equals("")||type.equals("")) {
							rule_types.add(type);
						}else {
							String label = uri_term.get(type);
							report+=label+",";
						}
					}
				}
				report+="\t";
				if(rule_types.size()>0) {
					for(String type : types) {
						String label = uri_term.get(type);
						report+=label+",";
					}
				}
			}else {
				report+="\t";
			}
			report+="\t";
			if(p_inferred_complex_types!=null&&p_inferred_complex_types.size()>0) {
				Set<String> types = p_inferred_complex_types.get(uri_term.get(complex_uri));
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

	public String makeMappingReportWithOneRowForEachGoCAMEntity() {
		//		Set<String> idsmm = new HashSet<String>();
		String report = "";
		for(String pathway_uri : pathway_types.keySet()) {
			//			if(ids.contains(uri_term.get(pathway_uri))) {
			//				continue;
			//			}else {
			//				ids.add(uri_term.get(pathway_uri));
			//			}
			report+="Pathway\t"+uri_term.get(pathway_uri)+"\t";
			if(pathway_asserted_types!=null&&pathway_asserted_types.size()>0) {
				Set<String> rule_types = new HashSet<String>();
				Set<String> types = pathway_asserted_types.get(pathway_uri);
				if(types!=null) {
					for(String type : types) {
						if(type.equals("")||type.equals("")) {
							rule_types.add(type);
						}else {
							String label = uri_term.get(type);
							report+=label+",";
						}
					}
				}
				report+="\t";
				if(rule_types.size()>0) {
					for(String type : types) {
						String label = uri_term.get(type);
						report+=label+",";
					}
				}
			}else {
				report+="\t";
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
			//			if(ids.contains(uri_term.get(function_uri))) {
			//				continue;
			//			}else {
			//				ids.add(uri_term.get(function_uri));
			//			}
			report+="function\t"+uri_term.get(function_uri)+"\t";
			if(function_asserted_types!=null&&function_asserted_types.size()>0) {
				Set<String> rule_types = new HashSet<String>();
				Set<String> types = function_asserted_types.get(function_uri);
				if(types!=null) {
					for(String type : types) {
						if(type.equals(GoCAM.protein_binding.getIRI().toURI().toString())||type.equals(GoCAM.establishment_of_protein_localization.getIRI().toURI().toString())) {
							rule_types.add(type);
						}else {
							String label = uri_term.get(type);
							report+=label+",";
						}
					}
				}
				report+="\t";
				if(rule_types.size()>0) {
					for(String type : types) {
						String label = uri_term.get(type);
						report+=label+",";
					}
				}
			}else {
				report+="\t";
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
			//			if(ids.contains(uri_term.get(complex_uri))) {
			//				continue;
			//			}else {
			//				ids.add(uri_term.get(complex_uri));
			//			}
			report+="complex\t"+uri_term.get(complex_uri)+"\t";
			if(complex_asserted_types!=null&&complex_asserted_types.size()>0) {
				Set<String> rule_types = new HashSet<String>();
				Set<String> types = complex_asserted_types.get(complex_uri);
				if(types!=null) {
					for(String type : types) {
						if(type.equals("")||type.equals("")) {
							rule_types.add(type);
						}else {
							String label = uri_term.get(type);
							report+=label+",";
						}
					}
				}
				report+="\t";
				if(rule_types.size()>0) {
					for(String type : types) {
						String label = uri_term.get(type);
						report+=label+",";
					}
				}
			}else {
				report+="\t";
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
		e_xrefs = qr.getXrefs();
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
			Set<String> types = complex_types.get(complex_node);
			boolean some_inferred = false; 
			boolean some_manual = false;
			//bin the types
			for(String type : types) {
				if(type.equals("http://purl.obolibrary.org/obo/GO_0032991")) {
					continue;
				}
				boolean inferred = wasInferred(wm, complex_node, "http://www.w3.org/1999/02/22-rdf-syntax-ns#type", type);
				if(inferred) {		
					Set<String> itypes = complex_inferred_types.get(complex_node);
					if(itypes==null) {itypes = new HashSet<String>();}
					itypes.add(type);
					complex_inferred_types.put(complex_node, itypes);
					some_inferred = true;
				}else {
					Set<String> atypes = complex_asserted_types.get(complex_node);
					if(atypes==null) {atypes = new HashSet<String>();}
					atypes.add(type);
					complex_asserted_types.put(complex_node, atypes);
					some_manual = true;
				}
			}

			if(some_inferred) {
				if(some_manual){
					complex_deepened++;
				}else {
					complex_inferred_type++;
				}
			}else if(!some_manual) {
				complex_unclassified++;
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
			label = label+"\t";
			if(e_xrefs.get(node_uri)!=null) {
				for(String xref : e_xrefs.get(node_uri)) {
					label = label+xref+" ";
				}
			}
			uri_term.put(node_uri, label);			
			for(String type_uri : all_types.get(node_uri)) {
				String type_label = Helper.getaLabel(type_uri, go);
				String acc = type_uri;
				acc = acc.replaceAll("http://purl.obolibrary.org/obo/", "");
				uri_term.put(type_uri, acc+": "+type_label);
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
	}
	 * @throws IOException */

	public static void secondaryReportProcessing(String input_file) throws IOException {
		BufferedReader reader = new BufferedReader(new FileReader(input_file));
		reader.readLine();
		String line = reader.readLine();
		Set<String> nr = new HashSet<String>();
		int n_rows = 0; int n_pathways = 0; int n_reactions = 0; int n_complexes = 0;
		int n_curated_pathways = 0; int n_curated_reactions = 0; int n_curated_complexes = 0;
		int n_rule_classified_pathways = 0; int n_rule_classified_reactions = 0; int n_rule_classified_complexes = 0;
		int n_owl_classified_pathways = 0; int n_owl_classified_reactions = 0; int n_owl_classified_complexes = 0;
		Map<String, Set<String>> curated_type_reactions = new HashMap<String, Set<String>>();
		Map<String, Set<String>> curated_type_pathways = new HashMap<String, Set<String>>();
		Map<String, Set<String>> curated_type_complexes = new HashMap<String, Set<String>>();
		Map<String, Set<String>> rule_type_reactions = new HashMap<String, Set<String>>();
		Map<String, Set<String>> rule_type_pathways = new HashMap<String, Set<String>>();
		Map<String, Set<String>> rule_type_complexes = new HashMap<String, Set<String>>();
		Map<String, Set<String>> inferred_type_reactions = new HashMap<String, Set<String>>();
		Map<String, Set<String>> inferred_type_pathways = new HashMap<String, Set<String>>();
		Map<String, Set<String>> inferred_type_complexes = new HashMap<String, Set<String>>();
		while(line!=null) {
				n_rows++;
				String[] row = line.split(",",-1);	
				//Reactome node type	Reactome Label	Reactome Id	Curator asserted GO types	Rule-assigned types	Inferred GO types
				String node_type = row[0]; String label = row[1]; String reactome_id = row[2];
				String curated_types = row[3]; String rule_types = row[4]; String inferred_types = row[5];
			if(!nr.add(reactome_id+node_type)) { //note that you get different classifications for the same reactome thing.. this just report on one, so under-reporting
				System.out.println("redun "+reactome_id);
			}else {
				if(node_type.equals("Biological Process")&&(!label.contains("reaction:"))) {
					if(!curated_types.equals("")) {
						n_curated_pathways++;
						curated_type_pathways = splitNode(reactome_id, curated_type_pathways, curated_types);
					}
					if(!rule_types.equals("")) {
						n_rule_classified_pathways++;
						rule_type_pathways = splitNode(reactome_id, rule_type_pathways, rule_types);
					}else {
						n_pathways++; //rules currently only operate on mfs for localization
					}
					if(!inferred_types.equals("")) {
						n_owl_classified_pathways++;
						inferred_type_pathways = splitNode(reactome_id, inferred_type_pathways, inferred_types);
					}
					
				}
				if(node_type.equals("Molecular Function")) {
					n_reactions++;
					if(!curated_types.equals("")) {
						n_curated_reactions++;
						curated_type_reactions = splitNode(reactome_id, curated_type_reactions, curated_types);
					}
					if(!rule_types.equals("")) {
						n_rule_classified_reactions++;
						rule_type_reactions = splitNode(reactome_id, rule_type_reactions, rule_types);
					}
					if(!inferred_types.equals("")) {
						n_owl_classified_reactions++;
						inferred_type_reactions = splitNode(reactome_id, inferred_type_reactions, inferred_types);
					}
				}
				if(node_type.equals("Complex")) {
					n_complexes++;
					if(!curated_types.equals("")) {
						n_curated_complexes++;
						curated_type_complexes = splitNode(reactome_id, curated_type_complexes, curated_types);
					}
					if(!rule_types.equals("")) {
						n_rule_classified_complexes++;
						rule_type_complexes = splitNode(reactome_id, rule_type_complexes, rule_types);
					}
					if(!inferred_types.equals("")) {
						n_owl_classified_complexes++;
						inferred_type_complexes = splitNode(reactome_id, inferred_type_complexes, inferred_types);
					}
				}
				
			}
			line = reader.readLine();			
		}
		reader.close();
		
		System.out.println("n rows "+n_rows);
		System.out.println("n pathways "+n_pathways+" n_curated_pathways "+n_curated_pathways+
				" n_rule_classified_pathways "+n_rule_classified_pathways+" n_owl_classified_pathways "+n_owl_classified_pathways);
		System.out.println();
		System.out.println("n reactions "+n_reactions+" n_curated_reactions "+n_curated_reactions+
				" n_rule_classified_reactions "+n_rule_classified_reactions+" n_owl_classified_reactions "+n_owl_classified_reactions);
		System.out.println();
		System.out.println("n complexes "+n_complexes+" n_curated_complexes "+n_curated_complexes+
				" n_rule_classified_complexes "+n_rule_classified_complexes+" n_owl_classified_complexes "+n_owl_classified_complexes);
		System.out.println();
				
	}
	public static Map<String, Set<String>> splitNode(String reactome_id, Map<String, Set<String>> map, String tosplit){
		if(tosplit!=null) {
			String[] types = tosplit.split(";");
			if(types!=null&&types.length>0) {
			for(String type : types) {
				Set<String> entities = map.get(type);
				if(entities==null) { entities = new HashSet<String>();}
				entities.add(reactome_id);
				map.put(type, entities);
			}}
		}
		return map;
	}
	
	
	public static void main(String[] args) throws IOException {
		String i = "/Users/bgood/Desktop/manual_plus_inferred_mapping_tabbed.csv";
		secondaryReportProcessing(i);
	}
}
