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
		tbox_files.add(BioPaxtoGO.reactome_physical_entities_file);
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
		String go_cam_folder = "/Users/bgood/Desktop/test/go_cams/reactome/";
		//"/Users/bgood/Documents/GitHub/noctua-models/models/";
		String out = "/Users/bgood/Desktop/test/go_cams/arachne_validator_reactome_june11.txt";
		String filename_must_contain = "reactome";
		String catalog_file = "/Users/bgood/gocam_ontology/catalog-no-import.xml";
		//validator.testConsistencyForFolder(go_cam_folder, out, filename_must_contain, catalog_file);
		//		GoCAM go_cam = new GoCAM(new File("/Users/bgood/Documents/GitHub/noctua-models/models/MGI_MGI_1923628.ttl"), catalog_file);
		//		boolean is_logical = validator.arachneTest(go_cam, true);	
		Set<String> files = new HashSet<String>();
		files.add(go_cam_folder+"reactome-homosapiens-Synthesis_of_Lipoxins_(LX).ttl"); 
		files.add(go_cam_folder+"reactome-homosapiens-Synthesis_of_5-eicosatetraenoic_acids.ttl"); 
		files.add(go_cam_folder+"reactome-homosapiens-Apoptotic_cleavage_of_cellular_proteins.ttl"); 
		files.add(go_cam_folder+"reactome-homosapiens-Breakdown_of_the_nuclear_lamina.ttl"); 
		files.add(go_cam_folder+"reactome-homosapiens-Apoptosis_induced_DNA_fragmentation.ttl"); 
		files.add(go_cam_folder+"reactome-homosapiens-Synthesis_of_Leukotrienes_(LT)_and_Eoxins_(EX).ttl"); 
		files.add(go_cam_folder+"reactome-homosapiens-tRNA_modification_in_the_nucleus_and_cytosol.ttl");
		boolean print_explanations = true;
		validator.testConsistencyForFiles(files, out, filename_must_contain, catalog_file, print_explanations);
	}

	void testConsistencyForFolder(String input_folder, String output_file, String filename_must_contain, String catalog_file, boolean print_explanations) throws OWLOntologyCreationException, IOException {
		File dir = new File(input_folder);
		File[] directoryListing = dir.listFiles();
		Set<String>files = new HashSet<String>();
		for (File abox_file : directoryListing) {
			files.add(abox_file.getAbsolutePath());
		}
		testConsistencyForFiles(files,output_file, filename_must_contain, catalog_file, print_explanations);
		return;
	}

	void testConsistencyForFiles(Set<String>files, String output_file, String filename_must_contain, String catalog_file, boolean print_explanations) throws OWLOntologyCreationException, IOException {
		FileWriter output = new FileWriter(output_file);
		output.write("file\tconsistent\n");
		int total = 0;
		for (String abox_file : files) {
			if(abox_file.endsWith(".ttl")&&abox_file.contains(filename_must_contain)) {
				total++;
			}else {
				files.remove(abox_file);
			}
		}
		if(print_explanations) {
			//clean out file for appending
			FileWriter writer = new FileWriter(output_file, false);
			writer.close();
		}
		if (files != null) {
			int n = 0;
			for (String abox_file : files) {
				if(abox_file.endsWith(".ttl")&&abox_file.contains(filename_must_contain)) {
					n++;
					System.out.println("starting on "+n+" of "+total+"\t"+abox_file);
					GoCAM go_cam = new GoCAM(new File(abox_file), catalog_file);
					String explain_file = output_file.replace(".txt", "")+"_explanations.txt";
					boolean is_logical = arachneTest(go_cam, print_explanations, explain_file, abox_file);		
					output.write(abox_file+"\t"+is_logical+"\n");
					System.out.println(n+" of "+total+"\t"+abox_file+"\t"+is_logical);
				}
			}
		}
		output.close();
	}

	boolean arachneTest(GoCAM go_cam, boolean print_explanations, String explain_file, String ont_name) throws OWLOntologyCreationException, IOException {	
		go_cam.qrunner = new QRunner(go_cam.go_cam_ont); 		
		WorkingMemory wm_with_tbox = tbox_qrunner.arachne.createInferredModel(go_cam.go_cam_ont,false, false);			
		go_cam.qrunner.jena = go_cam.qrunner.makeJenaModel(wm_with_tbox);
		boolean is_logical = go_cam.validateGoCAM();	
		if(print_explanations) {
			writeExplanation(wm_with_tbox, explain_file, ont_name);
		}
		return is_logical;
	}

	void writeExplanation(WorkingMemory wm_with_tbox, String output_file, String ont_name) throws IOException {
		FileWriter writer = new FileWriter(output_file, true);
		writer.write("explanations for "+ont_name+"\n");
		scala.collection.Iterator<Triple> triples = wm_with_tbox.facts().toList().iterator();
		while(triples.hasNext()) {				
			Triple triple = triples.next();
			if(wm_with_tbox.asserted().contains(triple)) {
				continue;
			}else { //<http://arachne.geneontology.org/indirect_type>
				if(triple.p().toString().equals("<http://www.w3.org/1999/02/22-rdf-syntax-ns#type>")&&
						triple.o().toString().equals("<http://www.w3.org/2002/07/owl#Nothing>")) {
					OWLEntity bad = go_cam.df.getOWLNamedIndividual(IRI.create(triple.s().toString()));
					writer.write("inferred inconsistent:"+triple.s()+" "+go_cam.getaLabel(bad)+"\n");
					scala.collection.immutable.Set<Explanation> explanations = wm_with_tbox.explain(triple);
					scala.collection.Iterator<Explanation> e = explanations.iterator();
					while(e.hasNext()) {
						Explanation exp = e.next();
						writer.write(exp.toString()+"\n\n");
					}
				}
			}
		}
		writer.close();
	}

}
