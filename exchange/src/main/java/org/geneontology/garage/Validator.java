/**
 * 
 */
package org.geneontology.garage;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.geneontology.gocam.exchange.BioPaxtoGO;
import org.geneontology.gocam.exchange.GoCAM;
import org.geneontology.gocam.exchange.QRunner;
import org.geneontology.rules.engine.Explanation;
import org.geneontology.rules.engine.Triple;
import org.geneontology.rules.engine.WorkingMemory;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLEntity;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyCreationException;
import org.semanticweb.owlapi.model.OWLOntologyManager;

/**
 * @author bgood
 *
 */
public class Validator {

	QRunner tbox_qrunner;
	GoCAM go_cam;
	/**
	 * @throws OWLOntologyCreationException 
	 * 
	 */
	public Validator() throws OWLOntologyCreationException {
		Set<String> tbox_files = new HashSet<String>();
		tbox_files.add(BioPaxtoGO.goplus_file);
		tbox_files.add(BioPaxtoGO.ro_file);
		tbox_files.add(BioPaxtoGO.legorel_file);
		tbox_files.add(BioPaxtoGO.go_bfo_bridge_file);
		tbox_files.add(BioPaxtoGO.eco_base_file);
		go_cam = new GoCAM();
		tbox_qrunner = go_cam.initializeQRunnerForTboxInference(tbox_files);
	}

	/**
	 * @param args
	 * @throws OWLOntologyCreationException 
	 * @throws IOException 
	 */
	public static void main(String[] args) throws OWLOntologyCreationException, IOException {
		
		Validator validator = new Validator();
		String go_cam_folder = "/Users/bgood/Documents/GitHub/noctua-models/models/";
		String out = "/Users/bgood/Desktop/test/go_cams/arachne_validator_reactome_march25.txt";
		String filename_must_contain = "reactome";
		String catalog_file = "/Users/bgood/gocam_ontology/catalog-no-import.xml";
		validator.testConsistencyForFolder(go_cam_folder, out, filename_must_contain, catalog_file);
//		GoCAM go_cam = new GoCAM(new File("/Users/bgood/Documents/GitHub/noctua-models/models/MGI_MGI_1923628.ttl"), catalog_file);
//		boolean is_logical = validator.arachneTest(go_cam, true);	
	}

	void testConsistencyForFolder(String input_folder, String output_file, String filename_must_contain, String catalog_file) throws OWLOntologyCreationException, IOException {
		FileWriter output = new FileWriter(output_file);
		File dir = new File(input_folder);
		File[] directoryListing = dir.listFiles();
		int total = 0;
		for (File abox_file : directoryListing) {
			if(abox_file.getName().endsWith(".ttl")&&abox_file.getName().contains(filename_must_contain)) {
				total++;
			}
		}
		if (directoryListing != null) {
			int n = 0;
			for (File abox_file : directoryListing) {
				if(abox_file.getName().endsWith(".ttl")&&abox_file.getName().contains(filename_must_contain)) {
					n++;
					System.out.println("starting on "+n+" of "+total+"\t"+abox_file.getName());
					GoCAM go_cam = new GoCAM(abox_file, catalog_file);
					boolean is_logical = arachneTest(go_cam, false);		
					output.write(abox_file.getName()+"\t"+is_logical+"\n");
					System.out.println(n+" of "+total+"\t"+abox_file.getName()+"\t"+is_logical);
				}
			}
		}
		output.close();
	}
	
	boolean arachneTest(GoCAM go_cam, boolean print_explanations) throws OWLOntologyCreationException {	
		go_cam.qrunner = new QRunner(go_cam.go_cam_ont); 		
		WorkingMemory wm_with_tbox = tbox_qrunner.arachne.createInferredModel(go_cam.go_cam_ont,false, false);			
		go_cam.qrunner.jena = go_cam.qrunner.makeJenaModel(wm_with_tbox);
		boolean is_logical = go_cam.validateGoCAM();	
		if(print_explanations) {
			printExplanation(wm_with_tbox);
		}
		return is_logical;
	}

	void printExplanation(WorkingMemory wm_with_tbox) {
			scala.collection.Iterator<Triple> triples = wm_with_tbox.facts().toList().iterator();
			while(triples.hasNext()) {				
				Triple triple = triples.next();
				if(wm_with_tbox.asserted().contains(triple)) {
					continue;
				}else { //<http://arachne.geneontology.org/indirect_type>
					if(triple.p().toString().equals("<http://www.w3.org/1999/02/22-rdf-syntax-ns#type>")&&
							triple.o().toString().equals("<http://www.w3.org/2002/07/owl#Nothing>")) {
						OWLEntity bad = go_cam.df.getOWLNamedIndividual(IRI.create(triple.s().toString()));
						System.out.println("inferred inconsistent:"+triple.s()+" "+go_cam.getaLabel(bad));
						scala.collection.immutable.Set<Explanation> explanations = wm_with_tbox.explain(triple);
						scala.collection.Iterator<Explanation> e = explanations.iterator();
						while(e.hasNext()) {
							Explanation exp = e.next();
							System.out.println(exp.toString());
							System.out.println();
						}
					}
				}
			}
	}

}
