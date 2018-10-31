/**
 * 
 */
package org.geneontology.gocam.exchange;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.apache.jena.rdf.model.Model;
import org.geneontology.rules.engine.Triple;
import org.geneontology.rules.engine.WorkingMemory;
import org.semanticweb.owlapi.model.IRI;

/**
 * @author bgood
 *
 */
public class GoCAMReport {
	String name = "";
	int mf_unclassified = 0;
	int bp_unclassified = 0;
	int cc_unclassified = 0;
	int complex_unclassified = 0;
	int mf_count = 0;
	int bp_count = 0;
	int cc_count = 0;
	int complex_count = 0;

	Set<String> mf_labels = new HashSet<String>();
	Set<String> mf_to_root_labels = new HashSet<String>();
	Set<String> bp_labels = new HashSet<String>();
	Set<String> bp_to_root_labels = new HashSet<String>();
	Set<String> cc_labels = new HashSet<String>();
	Set<String> cc_to_root_labels = new HashSet<String>();
	Set<String> complex_labels = new HashSet<String>();
	Set<String> complex_to_root_labels = new HashSet<String>();
	public GoCAMReport() {};
	

	public GoCAMReport(WorkingMemory wm) {
		QRunner qr = new QRunner(wm);
		Map<String, Set<String>> pathway_types = qr.getPathways();
		Map<String, Set<String>> function_types = qr.getFunctions();
		Map<String, Integer> relation_count = qr.getRelations();
		
		System.out.println(pathway_types+"\n"+function_types+"\n"+relation_count);
		System.out.println("pathways: "+pathway_types.keySet().size());
		System.out.println("functions: "+function_types.keySet().size());
		System.out.println("relations: "+relation_count.keySet().size());
		for(String r : relation_count.keySet()) {
			System.out.println(r+" "+relation_count.get(r));
		}
	}
	
	/**
	 * Build a report for this go_cam using an Arachne-inferred set of triples (WorkingMemory) for that model
	 * @param wm
	 * @param go_cam
	 */
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

}
