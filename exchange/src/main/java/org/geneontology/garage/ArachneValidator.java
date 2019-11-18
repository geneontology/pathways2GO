/**
 * 
 */
package org.geneontology.garage;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.geneontology.gocam.exchange.BioPaxtoGO;
import org.geneontology.gocam.exchange.GoCAM;
import org.geneontology.gocam.exchange.Helper;
import org.geneontology.gocam.exchange.QRunner;
import org.geneontology.rules.engine.Explanation;
import org.geneontology.rules.engine.Triple;
import org.geneontology.rules.engine.WorkingMemory;
import org.obolibrary.robot.CatalogXmlIRIMapper;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLAnnotationAssertionAxiom;
import org.semanticweb.owlapi.model.OWLAnnotationAxiom;
import org.semanticweb.owlapi.model.OWLAxiom;
import org.semanticweb.owlapi.model.OWLClassExpression;
import org.semanticweb.owlapi.model.OWLEntity;
import org.semanticweb.owlapi.model.OWLIndividual;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyCreationException;
import org.semanticweb.owlapi.model.OWLOntologyManager;
import org.semanticweb.owlapi.search.EntitySearcher;

import com.google.common.collect.Sets;

/**
 * @author bgood
 *
 */
public class ArachneValidator {

	QRunner tbox_qrunner;
	GoCAM go_cam;
	OWLOntology merged_annotations;
	/**
	 * @throws OWLOntologyCreationException 
	 * @throws IOException 
	 * 
	 */
	public ArachneValidator() throws OWLOntologyCreationException, IOException {
		String url_for_tbox = "http://purl.obolibrary.org/obo/go/extensions/go-lego.owl";
		OWLOntologyManager tman = OWLManager.createOWLOntologyManager();
		Map<String,OWLOntology> tboxes = new HashMap<String,OWLOntology>();	
		String catalog = "/Users/bgood/gocam_ontology/catalog-v001-for-noctua.xml";
		System.out.println("using catalog: "+catalog);
		tman.setIRIMappers(Sets.newHashSet(new CatalogXmlIRIMapper(catalog)));
//		tboxes.put("goplus",tman.loadOntologyFromOntologyDocument(new File("/Users/bgood/gocam_ontology/go-plus.owl")));
		tboxes.put("golego",tman.loadOntology(IRI.create(url_for_tbox)));
//		tboxes.put("golego",tman.loadOntologyFromOntologyDocument(new File("/Users/bgood/git/noctua_exchange/exchange/src/test/go-lego-test.owl")));
//		tboxes.put("ro",tman.loadOntologyFromOntologyDocument(new File(BioPaxtoGO.ro_file)));
//		tboxes.put("legorel",tman.loadOntologyFromOntologyDocument(new File(BioPaxtoGO.legorel_file)));
//		tboxes.put("go_bfo_bridge",tman.loadOntologyFromOntologyDocument(new File(BioPaxtoGO.go_bfo_bridge_file)));
//		tboxes.put("eco_base",tman.loadOntologyFromOntologyDocument(new File(BioPaxtoGO.eco_base_file)));
//		tboxes.put("reactome",tman.loadOntologyFromOntologyDocument(new File(BioPaxtoGO.reactome_physical_entities_file)));
		
		merged_annotations = tman.createOntology();
		for(OWLAxiom a : tboxes.get("golego").getAxioms()){
		if(a.isAnnotationAxiom()) {
			tman.addAxiom(merged_annotations, a);
		}
	}
//		for(OWLAxiom a : tboxes.get("goplus").getAxioms()){
//			if(a.isAnnotationAxiom()) {
//				tman.addAxiom(merged_annotations, a);
//			}
//		}
//		for(OWLAxiom a : tboxes.get("reactome").getAxioms()){
//			if(a.isAnnotationAxiom()) {
//				tman.addAxiom(merged_annotations, a);
//			}
//		}
//		for(OWLAxiom a : tboxes.get("ro").getAxioms()){
//			if(a.isAnnotationAxiom()) {
//				tman.addAxiom(merged_annotations, a);
//			}
//		}
		
		go_cam = new GoCAM();
		boolean add_inferences = true;
		boolean add_property_definitions = false; boolean add_class_definitions = false;
		tbox_qrunner = QRunner.MakeQRunner(tboxes, go_cam.go_cam_ont, add_inferences, add_property_definitions, add_class_definitions);
		go_cam.qrunner = tbox_qrunner;
	}

	/**
	 * @param args
	 * @throws OWLOntologyCreationException 
	 * @throws IOException 
	 */
	public static void main(String[] args) throws OWLOntologyCreationException, IOException {

		ArachneValidator validator = new ArachneValidator();
		String go_cam_folder = "/Users/bgood/Desktop/test/go_cams/reactome/";
		//"/Users/bgood/Documents/GitHub/noctua-models/models/";
		String out = "/Users/bgood/Desktop/test/go_cams/arachne_validator_reactome_oct29.txt";
		String filename_must_contain = "reactome";
		String catalog_file = "/Users/bgood/gocam_ontology/catalog-no-import.xml";
		validator.testConsistencyForFolder(go_cam_folder, out, filename_must_contain, catalog_file, true);
		//		GoCAM go_cam = new GoCAM(new File("/Users/bgood/Documents/GitHub/noctua-models/models/MGI_MGI_1923628.ttl"), catalog_file);
		//		boolean is_logical = validator.arachneTest(go_cam, true);	
//		Set<String> files = new HashSet<String>();
//		files.add(go_cam_folder+"reactome-homosapiens-Synthesis_of_Lipoxins_(LX).ttl"); 
//		files.add(go_cam_folder+"reactome-homosapiens-Synthesis_of_5-eicosatetraenoic_acids.ttl"); 
//		files.add(go_cam_folder+"reactome-homosapiens-Apoptotic_cleavage_of_cellular_proteins.ttl"); 
//		files.add(go_cam_folder+"reactome-homosapiens-Breakdown_of_the_nuclear_lamina.ttl"); 
//		files.add(go_cam_folder+"reactome-homosapiens-Apoptosis_induced_DNA_fragmentation.ttl"); 
//		files.add(go_cam_folder+"reactome-homosapiens-Synthesis_of_Leukotrienes_(LT)_and_Eoxins_(EX).ttl"); 
//		files.add(go_cam_folder+"reactome-homosapiens-tRNA_modification_in_the_nucleus_and_cytosol.ttl");
//		boolean print_explanations = true;
//		validator.testConsistencyForFiles(files, out, filename_must_contain, catalog_file, print_explanations);
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
			writeExplanation(wm_with_tbox, explain_file, ont_name, go_cam);
		}
		return is_logical;
	}

	void writeExplanation(WorkingMemory wm_with_tbox, String output_file, String ont_name, GoCAM go_cam) throws IOException {
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
					OWLEntity bad = go_cam.df.getOWLNamedIndividual(IRI.create(triple.s().toString().replaceAll("<", "").replaceAll(">", "")));
					Collection<OWLClassExpression> ts = EntitySearcher.getTypes((OWLIndividual) bad, go_cam.go_cam_ont);
					String problem_node_label = go_cam.getaLabel(bad);
					if(problem_node_label==null) {
						for(OWLClassExpression t : ts) {
							problem_node_label = Helper.getaLabel((OWLEntity) t, merged_annotations);
							if(problem_node_label!=null) {
								break;
							}
						}					
					}
					if(problem_node_label==null) {
						problem_node_label = "no label";
					}
					writer.write("inferred inconsistent:"+triple.s()+" "+problem_node_label+"\n");
					scala.collection.immutable.Set<Explanation> explanations = wm_with_tbox.explain(triple);
					scala.collection.Iterator<Explanation> e = explanations.iterator();
					while(e.hasNext()) {
						Explanation exp = e.next();
						String exp_string = BioPaxtoGO.renderExplanation(exp, go_cam, merged_annotations);
						writer.write(exp_string+"\n\n");
					}
				}
			}
		}
		writer.close();
	}

}
