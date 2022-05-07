/**
 * 
 */
package org.geneontology.gocam.exchange;

import static org.junit.Assert.*;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URL;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.apache.jena.rdf.model.Model;
import org.geneontology.rules.engine.WorkingMemory;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.obolibrary.robot.CatalogXmlIRIMapper;
import org.openrdf.query.BindingSet;
import org.openrdf.query.QueryEvaluationException;
import org.openrdf.query.TupleQueryResult;
import org.openrdf.repository.RepositoryException;
import org.openrdf.rio.RDFHandlerException;
import org.openrdf.rio.RDFParseException;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyCreationException;
import org.semanticweb.owlapi.model.OWLOntologyManager;
import org.semanticweb.owlapi.model.OWLOntologyStorageException;
import org.semanticweb.owlapi.reasoner.OWLReasoner;
import org.semanticweb.owlapi.reasoner.OWLReasonerFactory;
import org.semanticweb.owlapi.reasoner.structural.StructuralReasonerFactory;

import com.google.common.collect.Sets;

/**
 * @author bgood
 *
 */
public class BioPaxtoGOTest {
	//use default values for testing
	static BioPaxtoGO bp2g = new BioPaxtoGO(); 
	//parameters to set
	static String input_biopax = "./src/test/resources/biopax/"; 
	static String output_file_folder = "./src/test/resources/gocam/"; 
	static String output_file_stub = "./src/test/resources/gocam/test-"; 
	static String output_blazegraph_journal = "./src/test/resources/gocam/blazegraph.jnl"; //"/Users/bgood/noctua-config/blazegraph.jnl"; //
	static String tag = ""; //unexpanded
	static String base_title = "title here";//"Will be replaced if a title can be found for the pathway in its annotations
	static String default_contributor = "https://orcid.org/0000-0002-7334-7852"; //
	static String default_provider = "https://reactome.org";//"https://www.wikipathways.org/";//"https://www.pathwaycommons.org/";	
	static String test_pathway_name = null;
	static String empty_catalogue_file = "./src/test/resources/catalog-no-import.xml";
	static String local_catalogue_file = "./src/test/resources/ontology/catalog-for-validation.xml";//  //"/Users/bgood/gocam_ontology/catalog-v001-for-noctua.xml";
	static String go_lego_file = "./src/test/resources/ontology/go-lego-no-neo.owl";
	static String go_plus_url = "http://purl.obolibrary.org/obo/go/extensions/go-plus.owl";
	static String go_plus_file = "./target/go-plus.owl";
	static Blazer blaze;
	static QRunner tbox_qrunner;

	/**
	 * @throws java.lang.Exception 
	 */
	@BeforeClass
	public static void setUpBeforeClass() throws Exception {
		System.out.println("set up before class");
		bp2g.go_lego_file = go_lego_file;
		File goplus_file = new File(go_plus_file);
		if(!goplus_file.exists()) {
			URL goplus_location = new URL(go_plus_url);
			System.out.println("downloading goplus ontology from "+go_plus_url);
			org.apache.commons.io.FileUtils.copyURLToFile(goplus_location, goplus_file);
		}
		bp2g.blazegraph_output_journal = output_blazegraph_journal;
		//clean out any prior data in triple store
		FileWriter clean = new FileWriter(bp2g.blazegraph_output_journal, false);
		clean.write("");
		clean.close();
		//open connection to triple store
		blaze = new Blazer(bp2g.blazegraph_output_journal);
		System.out.println("done connecting to blaze, loading axioms");
		//set up for validation
		OWLOntologyManager ontman = OWLManager.createOWLOntologyManager();
		if(local_catalogue_file!=null) {
			ontman.setIRIMappers(Collections.singleton(new CatalogXmlIRIMapper(local_catalogue_file)));
		}
		OWLOntology tbox = ontman.loadOntologyFromOntologyDocument(new File(go_lego_file));
		bp2g.golego = new GOLego(tbox);
		//initialize the rules for inference
		System.out.println("starting tbox build");
		bp2g.tbox_qrunner = new QRunner(Collections.singleton(tbox), null, bp2g.golego.golego_reasoner, true, false, false);
		System.out.println("done building arachne");		
		fullBuild();
		fullBuildYeastCyc();
		//loadBlazegraph();
		System.out.println("done set up before class");
	}
	
	public static void loadBlazegraph() {
		bp2g.blazegraph_output_journal = output_blazegraph_journal;
		blaze = new Blazer(bp2g.blazegraph_output_journal);
	}
	
	public static void fullBuild() throws Exception{
		bp2g.entityStrategy = BioPaxtoGO.EntityStrategy.REACTO;
		bp2g.generate_report = false;
		
		//run the conversion on all the test biopax files
		System.out.println("running biopaxtogo on all test files");
		File dir = new File(input_biopax);
		File[] directoryListing = dir.listFiles();
		//TODO generalize this!  
		Set<String> taxa = new HashSet<String>();
		taxa.add("http://purl.obolibrary.org/obo/NCBITaxon_9606");
		//run through all files
		if (directoryListing != null) {
			for (File biopax : directoryListing) {
				String name = biopax.getName();
				if(name.contains(".owl")) { 
					name = name.replaceAll(".owl", "-");
					String this_output_file_stub = output_file_folder+name;
					try {
						bp2g.convert(biopax.getAbsolutePath(), this_output_file_stub, base_title, default_contributor, default_provider, tag, null, blaze, taxa);
					} catch (OWLOntologyCreationException | OWLOntologyStorageException | RepositoryException
							| RDFParseException | RDFHandlerException | IOException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				}
			} 
		}else {
			try {
				bp2g.convert(input_biopax, output_file_stub, base_title, default_contributor, default_provider, tag, null, blaze, taxa);
			} catch (OWLOntologyCreationException | OWLOntologyStorageException | RepositoryException
					| RDFParseException | RDFHandlerException | IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		
	}
	
	public static void fullBuildYeastCyc() throws Exception{
		String sssom_file = "./target/classes/YeastCyc/obomatch-go-yeastpathway.sssom.tsv.txt";
		String input_yeastcyc_biopax = "./src/test/resources/cyc/";
		String default_provider = "https://yeastgenome.org";
		
		bp2g.entityStrategy = BioPaxtoGO.EntityStrategy.YeastCyc;
		bp2g.sssom = new SSSOM(sssom_file);
		bp2g.generate_report = false;
			
		//run the conversion on all the test biopax files
		System.out.println("running biopaxtogo on all test files");
		File dir = new File(input_yeastcyc_biopax);
		File[] directoryListing = dir.listFiles();
		Set<String> taxa = new HashSet<String>();
		taxa.add("http://purl.obolibrary.org/obo/NCBITaxon_559292");
		//run through all files
		if (directoryListing != null) {
			for (File biopax : directoryListing) {
				String name = biopax.getName();
				if(name.contains(".owl")) { 
					name = name.replaceAll(".owl", "-");
					String this_output_file_stub = output_file_folder+name;
					try {
						bp2g.convert(biopax.getAbsolutePath(), this_output_file_stub, base_title, default_contributor, default_provider, tag, null, blaze, taxa);
					} catch (OWLOntologyCreationException | OWLOntologyStorageException | RepositoryException
							| RDFParseException | RDFHandlerException | IOException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				}
			} 
		}else {
			try {
				bp2g.convert(input_yeastcyc_biopax, output_file_stub, base_title, default_contributor, default_provider, tag, null, blaze, taxa);
			} catch (OWLOntologyCreationException | OWLOntologyStorageException | RepositoryException
					| RDFParseException | RDFHandlerException | IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		
	}
	
	/**
	 * @throws java.lang.Exception
	 */
	@AfterClass
	public static void tearDownAfterClass() throws Exception {
		blaze.getRepo().shutDown();
		blaze = null;
		bp2g.golego = null;
		bp2g.tbox_qrunner = null;
		System.out.println("tear down after class");
	}

	/**
	 * @throws java.lang.Exception
	 */
	@Before
	public void setUp() throws Exception {
		System.out.println("setup");
	}

	/**
	 * @throws java.lang.Exception
	 */
	@After
	public void tearDown() throws Exception {
		System.out.println("tear down ");
	}

	/**
	 * 
	 * 
	 * 		//"Glycolysis"; //"Signaling by BMP"; //"TCF dependent signaling in response to WNT"; //"RAF-independent MAPK1/3 activation";//"Oxidative Stress Induced Senescence"; //"Activation of PUMA and translocation to mitochondria";//"HDR through Single Strand Annealing (SSA)";  //"IRE1alpha activates chaperones"; //"Generation of second messenger molecules";//null;//"Clathrin-mediated endocytosis";
		//next tests: 
		//for continuant problem: Import of palmitoyl-CoA into the mitochondrial matrix 
		//error in rule rule:reg3 NTRK2 activates RAC1
		//
		//(rule:reg3) The relation 'DOCK3 binds FYN associated with NTRK2' 'directly positively regulates' 'DOCK3 activates RAC1' was inferred because: reaction1 has an output that is the enabler of reaction 2.
		//test for active site recognition
		//	test_pathways.add("SCF(Skp2)-mediated degradation of p27/p21");
		//unions
		//			test_pathways.add("GRB2 events in ERBB2 signaling");
		//			test_pathways.add("Elongator complex acetylates replicative histone H3, H4");
		//looks good
		//	test_pathways.add("Attenuation phase");
		//		test_pathways.add("NTRK2 activates RAC1");
		//		test_pathways.add("Unwinding of DNA");
		//		test_pathways.add("Regulation of TNFR1 signaling");
		//		test_pathways.add("SCF(Skp2)-mediated degradation of p27/p21");
		//inconsistent, but not sure how to fix		
		//test_pathways.add("tRNA modification in the nucleus and cytosol");
		//inconsistent
		//test_pathways.add("Apoptosis induced DNA fragmentation");

		//		test_pathways.add("SHC1 events in ERBB4 signaling");
		//looks good.  example of converting binding function to regulatory process template
		//	 test_pathways.add("FRS-mediated FGFR3 signaling");
		//	 test_pathways.add("FRS-mediated FGFR4 signaling");
		//looks good, nice inference for demo	 
		//		 test_pathways.add("Activation of G protein gated Potassium channels");
		//		 test_pathways.add("Regulation of actin dynamics for phagocytic cup formation");
		//		 test_pathways.add("SHC-mediated cascade:FGFR2");
		//		 test_pathways.add("SHC-mediated cascade:FGFR3");
		//check this one for annotations on regulates edges
		//		test_pathways.add("RAF-independent MAPK1/3 activation");
		//great example of why we are not getting a complete data set without inter model linking.  
		//		test_pathways.add("TCF dependent signaling in response to WNT");
		//looks great..
		//looks good 
		//	test_pathways.add("activated TAK1 mediates p38 MAPK activation");
		//check for relations between events that might not be biopax typed chemical reactions - e.g. degradation
		//			test_pathways.add("HDL clearance");
	 * 
	 */



	

	/**
	 * Test that all generated models are consistent.
	 */
//	@Test
	public final void testOWLConsistency() {
		File dir = new File(output_file_folder);
		File[] directoryListing = dir.listFiles();
		for (File abox_file : directoryListing) {
			if(abox_file.getAbsolutePath().endsWith(".ttl")) {
				try {
					GoCAM go_cam = new GoCAM(abox_file.getAbsoluteFile(), empty_catalogue_file);
					go_cam.qrunner = new QRunner(go_cam.go_cam_ont); 		
					WorkingMemory wm_with_tbox = bp2g.tbox_qrunner.arachne.createInferredModel(go_cam.go_cam_ont,false, false);			
					go_cam.qrunner.jena = go_cam.qrunner.makeJenaModel(wm_with_tbox);
					boolean is_logical = go_cam.validateGoCAM();	
					System.out.println(abox_file.getName()+" owl consistent:"+is_logical);
					assertTrue(abox_file.getName()+" owl consistent:"+is_logical, is_logical);
				} catch (OWLOntologyCreationException | IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
			
		}
	}

//	R-HSA-163359	
	@Test
	public final void testDiseaseReactionDeletion() {
		System.out.println("test removal of stray disease reactions coming in from causal relation");
		String pathway = "<http://model.geneontology.org/R-HSA-163359>";
		String reaction_delete = "<http://model.geneontology.org/R-HSA-9660819>";
		String reaction_present = "<http://model.geneontology.org/R-HSA-163617>";
		String all_reaction_q =  
				"SELECT  distinct ?reaction ?reaction_prop ?reaction_value  \n" + 
				"WHERE {\n" + 
				"  GRAPH pathway_id {  \n" + 
				"    	reaction_id ?reaction_prop ?reaction_value . \n" + 
				"    }\n" + 
				"  } \n";
		TupleQueryResult result = null;
		int n = 0;
		try {			
			String q1 = all_reaction_q.replace("pathway_id", pathway);
			q1 = q1.replace("reaction_id", reaction_delete);
			result = blaze.runSparqlQuery(q1);			
			while (result.hasNext()) {
				BindingSet bindingSet = result.next();
				n++;
			}
		} catch (QueryEvaluationException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} finally {
			try {
				result.close();
			} catch (QueryEvaluationException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		assertTrue("disease reaction "+reaction_delete+" not deleted", n==0);
		n = 0;
		result = null;
		try {
			
			String q2 = all_reaction_q.replace("pathway_id", pathway);
			q2 = q2.replace("reaction_id", reaction_present);
			result = blaze.runSparqlQuery(q2);
			while (result.hasNext()) {
				BindingSet bindingSet = result.next();
				n++;
			}
		} catch (QueryEvaluationException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} finally {
			try {
				result.close();
			} catch (QueryEvaluationException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		assertTrue("reaction "+reaction_present+" not present", n>0);

	}	

	// R-HSA-9674015 	
	@Test
	public final void testDrugReactionDeletion() {
		System.out.println("removal of drug reactions");
		String pathway = "<http://model.geneontology.org/R-HSA-186797>";
		String reaction_delete = "<http://model.geneontology.org/R-HSA-9674015>";
		String reaction_present = "<http://model.geneontology.org/R-HSA-8864036>";
		String all_reaction_q =  
				"SELECT  distinct ?reaction ?reaction_prop ?reaction_value  \n" + 
				"WHERE {\n" + 
				"  GRAPH pathway_id {  \n" + 
				"    	reaction_id ?reaction_prop ?reaction_value . \n" + 
				"    }\n" + 
				"  } \n";
		TupleQueryResult result = null;
		int n = 0;
		try {			
			String q1 = all_reaction_q.replace("pathway_id", pathway);
			q1 = q1.replace("reaction_id", reaction_delete);
			result = blaze.runSparqlQuery(q1);			
			while (result.hasNext()) {
				BindingSet bindingSet = result.next();
				n++;
			}
		} catch (QueryEvaluationException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} finally {
			try {
				result.close();
			} catch (QueryEvaluationException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		assertTrue("drug reaction "+reaction_delete+" not deleted", n==0);
		n = 0;
		result = null;
		try {
			
			String q2 = all_reaction_q.replace("pathway_id", pathway);
			q2 = q2.replace("reaction_id", reaction_present);
			result = blaze.runSparqlQuery(q2);
			while (result.hasNext()) {
				BindingSet bindingSet = result.next();
				n++;
			}
		} catch (QueryEvaluationException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} finally {
			try {
				result.close();
			} catch (QueryEvaluationException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		assertTrue("reaction "+reaction_present+" not present", n>0);

	}
	
	/**
	 * Make sure that the same reaction in different pathways is identical at the RDF level
	 * Support integration when viewing the whole collection in SPARQL
	 * Examples: 
	 * reaction R-HSA-190326 in pathway R-HSA-190322 Signaling by FGFR4 
	 * and reaction R-HSA-190326 in pathway R-HSA-5654228 Phospholipase C-mediated cascade; FGFR4 
	 * 
	 * reaction R-HSA-169680 in pathway R-HSA-2029485 Role of phospholipids in phagocytosis
	 * reaction R-HSA-169680 in pathway R-HSA-422356 Regulation of insulin secretion
	 */
	@Test
	public final void testIdentifierAssignment() {
		System.out.println("Testing identifier assignment consistency");
		String pathway_1 = "<http://model.geneontology.org/R-HSA-1606322>";
		String reaction_1 = "<http://model.geneontology.org/R-HSA-1591234>";
		String pathway_2 = "<http://model.geneontology.org/R-HSA-1606341>";
		Set<String> prop_value_1 = new HashSet<String>();
		Set<String> prop_value_2 = new HashSet<String>();
		String all_reaction_q = "PREFIX owl: <http://www.w3.org/2002/07/owl#> \n" + 
				"PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#> \n" + 
				"#model metadata\n" + 
				"PREFIX metago: <http://model.geneontology.org/>\n" + 
				"PREFIX lego: <http://geneontology.org/lego/> \n" + 
				"#model data\n" + 
				"PREFIX part_of: <http://purl.obolibrary.org/obo/BFO_0000050>\n" + 
				"PREFIX occurs_in: <http://purl.obolibrary.org/obo/BFO_0000066>\n" + 
				"PREFIX enabled_by: <http://purl.obolibrary.org/obo/RO_0002333>\n" + 
				"PREFIX has_input: <http://purl.obolibrary.org/obo/RO_0002233>\n" + 
				"PREFIX has_output: <http://purl.obolibrary.org/obo/RO_0002234>\n" + 
				"PREFIX causally_upstream_of: <http://purl.obolibrary.org/obo/RO_0002411>\n" + 
				"PREFIX provides_direct_input_for: <http://purl.obolibrary.org/obo/RO_0002413>\n" + 
				"PREFIX directly_positively_regulates: <http://purl.obolibrary.org/obo/RO_0002629>\n" + 
				"\n" + 
				"SELECT  distinct ?reaction ?reaction_prop ?reaction_value  \n" + 
				"WHERE {\n" + 
				"  #other graph <http://model.geneontology.org/R-HSA-1606341>  1606341 1606322\n" + 
				"  GRAPH pathway_id {  \n" + 
				"        ?id <http://purl.org/dc/elements/1.1/title> ?pathway_title . \n" + 
				"    	reaction_id ?reaction_prop ?reaction_value . \n" + 
				"    }\n" + 
				"  } \n";
		TupleQueryResult result = null;
		try {
			
			String q1 = all_reaction_q.replace("pathway_id", pathway_1);
			q1 = q1.replace("reaction_id", reaction_1);
			result = blaze.runSparqlQuery(q1);
			
			while (result.hasNext()) {
				BindingSet bindingSet = result.next();
				String prop = bindingSet.getValue("reaction_prop").stringValue();
				String value = bindingSet.getValue("reaction_value").stringValue();
				if(!prop.equals("http://purl.obolibrary.org/obo/BFO_0000050")
						&&!prop.equals("http://purl.org/dc/elements/1.1/contributor")) {
					prop_value_1.add(prop+":"+value);
				}
			}
		} catch (QueryEvaluationException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} finally {
			try {
				result.close();
			} catch (QueryEvaluationException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		result = null;
		try {
			
			String q2 = all_reaction_q.replace("pathway_id", pathway_2);
			q2 = q2.replace("reaction_id", reaction_1);
			result = blaze.runSparqlQuery(q2);
			while (result.hasNext()) {
				BindingSet bindingSet = result.next();
				String prop = bindingSet.getValue("reaction_prop").stringValue();
				String value = bindingSet.getValue("reaction_value").stringValue();
				if(!prop.equals("http://purl.obolibrary.org/obo/BFO_0000050")
						&&!prop.equals("http://purl.org/dc/elements/1.1/contributor")
						&&!prop.equals("http://purl.obolibrary.org/obo/RO_0002411")) {
					prop_value_2.add(prop+":"+value);
				}
			}
		} catch (QueryEvaluationException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} finally {
			try {
				result.close();
			} catch (QueryEvaluationException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		Set<String> tmp1 = new HashSet<String>(prop_value_1);
		prop_value_1.removeAll(prop_value_2);
		prop_value_2.removeAll(tmp1);
		assertTrue("diff values:\n\t"+prop_value_1+"\n"+prop_value_2, prop_value_1.size()==0);
		
	}
	
	
	/**
	 * Test method for {@link org.geneontology.gocam.exchange.GoCAM#inferTransportProcess()}.
	 * Test that transport processes are:
	 *  correctly typed as transporter activity
	 * 	have the proper starting and ending locations
	 *  have the right number of inputs and outputs
	 *  have an input that is also an output 
	 * Use reaction in Signaling By BMP R-HSA-201451
	 * 	The phospho-R-Smad1/5/8:Co-Smad transfers to the nucleus
	 * 	https://reactome.org/content/detail/R-HSA-201472
	 * Compare to http://noctua-dev.berkeleybop.org/editor/graph/gomodel:R-HSA-201451
	 */
	
	/**
	 * nice example: R-HSA-997272 Reactome:unexpanded:Inhibition of voltage gated Ca2+ channels via Gbeta/gamma subunits
	 */
	@Test
	public final void testInferLocalizationProcess() {
		System.out.println("Testing localization inference");
		TupleQueryResult result = null;
		try {
			String query =
					"prefix obo: <http://purl.obolibrary.org/obo/> "
					+ "select distinct ?type "
					+ "where { " + 
					" VALUES ?reaction { <http://model.geneontology.org/R-HSA-201472> } "
					+ " ?reaction rdf:type ?type . " + 
					"  filter(?type != owl:NamedIndividual) "+
					"  ?reaction obo:RO_0002339 ?endlocation . " + 
					"  ?endlocation rdf:type <http://purl.obolibrary.org/obo/GO_0005654> . " + 
					"  ?reaction obo:RO_0002338 ?startlocation . " + 
					"  ?startlocation rdf:type <http://purl.obolibrary.org/obo/GO_0005829> . "
					+ "?input rdf:type ?entityclass . "
					+ "?output rdf:type ?entityclass ." + 
					"}";
			result = blaze.runSparqlQuery(query);
			int n = 0; String type = null; 
			while (result.hasNext()) {
				BindingSet bindingSet = result.next();
				type = bindingSet.getValue("type").stringValue();
				n++;
			}
			assertTrue(n==1);
			assertTrue(type.equals("http://purl.obolibrary.org/obo/GO_0005215"));
			//assertTrue(inputs==1);
			//assertTrue(outputs==1);
		} catch (QueryEvaluationException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} finally {
			try {
				result.close();
			} catch (QueryEvaluationException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		System.out.println("Done testing transport inference");
	}
	
	/**
	 * Test method for {@link org.geneontology.gocam.exchange.GoCAM#inferTransportProcess()}.
	 * Test that protein transport processes are:
	 *  correctly typed as protein localization 
	 * 	have the proper starting and ending locations
	 *  have the right number of inputs and outputs
	 *  have an input that is also an output 
	 * Use reaction in TCF dependent signaling in response to WNT R-HSA-201681
	 * Beta-catenin translocates to the nucleus
	 * 	reaction uri http://model.geneontology.org/R-HSA-201681/R-HSA-201669 
	 */
	@Test
	public final void testInferProteinLocalizationProcess() {
		System.out.println("Testing localization inference");
		TupleQueryResult result = null;
		try {
			String query =
					"prefix obo: <http://purl.obolibrary.org/obo/> "
					+ "select ?type (count(distinct ?output) AS ?outputs) (count(distinct ?input) AS ?inputs) " + 
					"where { " + 
					" VALUES ?reaction { <http://model.geneontology.org/R-HSA-201669> } "
					+ " ?reaction rdf:type ?type . " + 
					"  filter(?type != owl:NamedIndividual) "
					+ " ?reaction obo:RO_0002234 ?output . " + 
					" ?reaction obo:RO_0002233 ?input . " + 
					"  ?reaction obo:RO_0002339 ?endlocation . " + 
					"  ?endlocation rdf:type <http://purl.obolibrary.org/obo/GO_0005654> . " + 
					"  ?reaction obo:RO_0002338 ?startlocation . " + 
					"  ?startlocation rdf:type <http://purl.obolibrary.org/obo/GO_0005829> . "
					+ "?input rdf:type ?entityclass . "
					+ "?output rdf:type ?entityclass ." + 
					"}"
				+" group by ?type ";
			result = blaze.runSparqlQuery(query);
			int n = 0; String type = null; int outputs = 0; int inputs = 0;
			while (result.hasNext()) {
				BindingSet bindingSet = result.next();
				type = bindingSet.getValue("type").stringValue();
				outputs = Integer.parseInt(bindingSet.getValue("outputs").stringValue());
				inputs = Integer.parseInt(bindingSet.getValue("inputs").stringValue());
				n++;
			}
			assertTrue(n==1);
			assertTrue("type is "+type, type.equals("http://purl.obolibrary.org/obo/GO_0140318"));
			assertTrue(inputs==1);
			assertTrue(outputs==1);
		} catch (QueryEvaluationException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} finally {
			try {
				result.close();
			} catch (QueryEvaluationException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		System.out.println("Done testing protein transport inference");
	}


	/**
	 * Test method for {@link org.geneontology.gocam.exchange.GoCAM#inferOccursInFromEntityLocations()}.
	 * Test that occurs_in statements on reactions are inferred from entity locations
	 * Use reaction in Signaling By BMP R-HSA-201451
	 * 	Phospho-R-Smad1/5/8 forms a complex with Co-Smad
	 * 	https://reactome.org/content/detail/R-HSA-201422
	 * Compare to http://noctua-dev.berkeleybop.org/editor/graph/gomodel:R-HSA-201451
	 */
	@Test
	public final void testOccursInFromEntityLocations() {
		System.out.println("Testing occurs in from entities inference");
		TupleQueryResult result = null;
		try {
			result = blaze.runSparqlQuery(
				"prefix obo: <http://purl.obolibrary.org/obo/> "
				+ "select ?locationclass " + 
				"where { " + 
				"VALUES ?reaction { <http://model.geneontology.org/R-HSA-201422> }" + 
				"  ?reaction obo:BFO_0000066 ?location . "
				+ "?location rdf:type ?locationclass " + 
				"  filter(?locationclass != owl:NamedIndividual)" + 
				"}");
			int n = 0; String location = null;
			while (result.hasNext()) {
				BindingSet bindingSet = result.next();
				location = bindingSet.getValue("locationclass").stringValue();
				n++;
			}
			assertTrue(n==1);
			assertTrue(location.equals("http://purl.obolibrary.org/obo/GO_0005829"));
		} catch (QueryEvaluationException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} finally {
			try {
				result.close();
			} catch (QueryEvaluationException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		System.out.println("Done occurs in from entities inference");
	}
	
	
	/**
	 * Test method for {@link org.geneontology.gocam.exchange.GoCAM#inferOccursInFromEntityLocations()}.
	 * Test that occurs_in statements on reactions are inferred from enabling molecules
	 * Use reaction R-HSA-201425 is Signaling by BMP 
	 * 	Ubiquitin-dependent degradation of the Smad complex terminates BMP2 signalling
	 * 	https://reactome.org/content/detail/R-HSA-201425 
	 * Compare to http://noctua-dev.berkeleybop.org/editor/graph/gomodel:R-HSA-201451
	 */
	@Test
	public final void testOccursInFromEnablerLocation() {
		System.out.println("Testing occurs in from enabler inference");
		TupleQueryResult result = null;
		try {
			result = blaze.runSparqlQuery(
				"prefix obo: <http://purl.obolibrary.org/obo/> "
				+ "select ?locationclass " + 
				"where { " + 
				"VALUES ?reaction { <http://model.geneontology.org/R-HSA-201425> }" + 
				"  ?reaction obo:BFO_0000066 ?location . "
				+ "?location rdf:type ?locationclass " + 
				"  filter(?locationclass != owl:NamedIndividual)" + 
				"}");
			int n = 0; String location = null;
			while (result.hasNext()) {
				BindingSet bindingSet = result.next();
				location = bindingSet.getValue("locationclass").stringValue();
				n++;
			}
			assertTrue(n==1);
			assertTrue(location, location.equals("http://purl.obolibrary.org/obo/GO_0005654"));
		} catch (QueryEvaluationException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} finally {
			try {
				result.close();
			} catch (QueryEvaluationException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		System.out.println("Done testing occurs in from enabler inference");
	}
	
	/**
	 * Test method for {@link org.geneontology.gocam.exchange.GoCAM#inferRegulatesViaOutputRegulates()}.
	 * Test that if reaction1 has_output M and reaction2 is regulated by M 
	 * then reaction1 provides direct input for a binding reaction that regulates reaction2
	 * Use pathway R-HSA-1810476 RIP-mediated NFkB activation via ZBP1
	 */
	@Test 
	public final void testInferRegulatesViaOutputRegulates() {
		System.out.println("Testing infer regulates via output regulates");
		TupleQueryResult result = null;
		try {
			result = blaze.runSparqlQuery(
				"prefix obo: <http://purl.obolibrary.org/obo/> "
				+ "select ?prop " + 
				"where { " + 
				"VALUES ?reaction2 { <http://model.geneontology.org/R-HSA-168910> } ." + 
				"VALUES ?reaction1 { <http://model.geneontology.org/R-HSA-1810457> } . " + 
				"  ?reaction1 <http://purl.obolibrary.org/obo/RO_0002413> ?binding_reaction . "
				+ "?binding_reaction ?prop ?reaction2 . "+
				"}"); 
			int n = 0; String prop = null;
			while (result.hasNext()) {
				BindingSet bindingSet = result.next();
				prop = bindingSet.getValue("prop").stringValue();
				n++;
			}
			assertTrue("should have been 1, but got n results: "+n, n==1);
			assertTrue("got "+prop, prop.equals("http://purl.obolibrary.org/obo/RO_0002629"));
		} catch (QueryEvaluationException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} finally {
			try {
				result.close();
			} catch (QueryEvaluationException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		System.out.println("Done testing infer regulates via output regulates");
	}
	
	/**
	 * Test method for {@link org.geneontology.gocam.exchange.GoCAM#inferEnablersForBinding()}.
	 * Test that if: 
	 * reaction1 is a binding reaction1 
	 * 				with M as an input, 
	 * 				M is a protein or complex, 
	 * 				and M is the output of reaction2 
	 * 					which is causally upstream of reaction1
	 * then reaction1 is enabled by M
	 * 	Pathway: Regulation of Glucokinase by Glucokinase Regulatory Protein
	 * 	Reaction: glucokinase (GCK1) + glucokinase regulatory protein (GKRP) <=> GCK1:GKRP complex
	 * Compare to http://noctua-dev.berkeleybop.org/editor/graph/gomodel:R-HSA-170822
	 */
//This test was taken out along with the corresponding rule - see https://github.com/geneontology/pathways2GO/issues/103	
//	@Test 
	public final void testInferEnablersFromUpstream() {
		System.out.println("Testing infer enabler from upstream output as an input");
		TupleQueryResult result = null;
		try {
			result = blaze.runSparqlQuery(
				"prefix obo: <http://purl.obolibrary.org/obo/> "
				+ "select ?M " + 
				"where { " +          
				"VALUES ?reaction1 { <http://model.geneontology.org/R-HSA-170824> } ." + 
				"VALUES ?reaction2 { <http://model.geneontology.org/R-HSA-170825> } . " + 
				"  ?reaction2 <http://purl.obolibrary.org/obo/RO_0002629> ?reaction1 ."
				+ "?reaction1 <http://purl.obolibrary.org/obo/RO_0002333> ?entityM . "
				+ "?entityM rdf:type ?M . "+
				" ?reaction2 <http://purl.obolibrary.org/obo/RO_0002234> ?upstreamM . "
				+ "?upstreamM rdf:type ?M . "
				+ "filter(?M != owl:NamedIndividual) "+
				"}"); 
			int n = 0; String M = "";
			while (result.hasNext()) {
				BindingSet bindingSet = result.next();
				M = bindingSet.getValue("M").stringValue();
				System.out.println("Enabler type: "+M);
				n++;
			}
			assertTrue("should have been 1, but got n results: "+n, n==1);
		} catch (QueryEvaluationException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} finally {
			try {
				result.close();
			} catch (QueryEvaluationException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		System.out.println("Done testing infer regulates via output regulates");
	}
	
	/**
	 * Test method for {@link org.geneontology.gocam.exchange.GoCAM#convertEntityRegulatorsToBindingFunctions()}.
	 * Test that if reaction1 has_output M and reaction2 is regulated by M then reaction1 regulates reaction2
	 * Use pathway Glycolysis R-HSA-70171 , reaction R-HSA-71670  
	 * 	phosphoenolpyruvate + ADP => pyruvate + ATP
entity involved in regulation of function 
Binding has_input E1
Binding has_input E2
Binding +-_regulates R
Binding part_of +-_regulation_of BP
⇐ 	
E1 +- involved_in_regulation_of R
R enabled_by E2
BP has_part R
	 */
	@Test 
	public final void testConvertEntityRegulatorsToBindingFunctions() {
		System.out.println("Testing convert entity regulators to binding functions");
		TupleQueryResult result = null;
		try {
			result = blaze.runSparqlQuery(
				"select distinct ?binding_reaction " + 
				"where { " + 
				"VALUES ?reaction1 { <http://model.geneontology.org/R-HSA-71670> } ."  
				+ " ?binding_reaction <http://purl.obolibrary.org/obo/RO_0002212> ?reaction1 . " //
				+ "?binding_reaction rdf:type <http://purl.obolibrary.org/obo/GO_0005488> . "
				+ "?binding_reaction <http://purl.obolibrary.org/obo/RO_0002233> ?input1 . "
				+ "?binding_reaction <http://purl.obolibrary.org/obo/RO_0002333> ?input2 . "
				+ "filter(?input1 != ?input2) "
				+"}"); 
			int n = 0; 
			while (result.hasNext()) {
				BindingSet bindingSet = result.next();
				String br = bindingSet.getValue("binding_reaction").stringValue();
				n++;
			}
			assertTrue("should have been 3, but got n results: "+n, n==3);
		} catch (QueryEvaluationException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} finally {
			try {
				result.close();
			} catch (QueryEvaluationException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		System.out.println("Done testing testConvertEntityRegulatorsToBindingFunctions");
	}
	
	/**
	 * Test method for active site handling in {@link org.geneontology.gocam.exchange.BioPaxtoGO#defineReactionEntity}.
	 * When reactome (this is a reactome specific hack) indicates in a Control object that a specific element of a complex
	 * is the active controller (regulator, catalyst), then that part should be used as the agent in the reaction.
	 * This is also a test for {@link org.geneontology.gocam.exchange.GoCAM#convertEntityRegulatorsToBindingFunctions}
	 * Use pathway R-HSA-4641262 , reaction = R-HSA-201685 
	 * 	Beta-catenin is released from the destruction complex
	 * 	https://reactome.org/content/detail/R-HSA-4641262 
	 * Compare to http://noctua-dev.berkeleybop.org/editor/graph/gomodel:R-HSA-4641262
	 */
	@Test
	public final void testActiveSiteInController() {
		System.out.println("Testing active sites in controller");
		TupleQueryResult result = null;
		try {
			result = blaze.runSparqlQuery(
				"prefix obo: <http://purl.obolibrary.org/obo/> "
				+ "select ?pathway " + 
				"where { " + 
				"VALUES ?reaction { <http://model.geneontology.org/R-HSA-201677> } . " 
				+ "?reaction obo:BFO_0000050 ?pathway . "
				+ "?reaction obo:RO_0002333 ?active_part . "+
				"}");
			int n = 0; String pathway = null;
			while (result.hasNext()) {
				BindingSet bindingSet = result.next();
				pathway = bindingSet.getValue("pathway").stringValue();
				n++;
			}
			assertTrue("expected 1, got "+n, n==1);
			assertTrue("got "+pathway, pathway.equals("http://model.geneontology.org/R-HSA-4641262/R-HSA-4641262"));
		} catch (QueryEvaluationException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} finally {
			try {
				result.close();
			} catch (QueryEvaluationException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		System.out.println("Done testing active sites in controller");
	}
	

	/**
	 * Test method for {@link org.geneontology.gocam.exchange.GoCAM#inferRegulatesViaOutputEnables}.
	 * Use pathway R-HSA-4641262 , reaction1 = R-HSA-1504186 reaction2 = R-HSA-201677
	 * Relation should be RO:0002413 directly positive regulates
	 * 	DVL recruits GSK3beta:AXIN1 to the receptor complex
	 * Phosphorylation of LRP5/6 cytoplasmic domain by membrane-associated GSK3beta
	 * 	https://reactome.org/content/detail/R-HSA-4641262 
	 * Compare to http://noctua-dev.berkeleybop.org/editor/graph/gomodel:R-HSA-4641262
	 * 
	 * Also an active site detection test
	 */
	@Test
	public final void testInferRegulatesViaOutputEnables() {
		System.out.println("Testing regulates via output enables");
		TupleQueryResult result = null;
		try {
			result = blaze.runSparqlQuery(
				"prefix obo: <http://purl.obolibrary.org/obo/> "
				+ "select ?pathway " + 
				"where { " + 
				"VALUES ?reaction1 { <http://model.geneontology.org/R-HSA-1504186> } . "+ 
				"VALUES ?reaction2 { <http://model.geneontology.org/R-HSA-201677> } . "+
				" ?reaction1 obo:RO_0002629 ?reaction2 . "
				+ "?reaction2 obo:RO_0002333 ?active_part . "
				+ "?reaction1 obo:BFO_0000050 ?pathway "+
				
				"}");
			int n = 0; String pathway = null;
			while (result.hasNext()) {
				BindingSet bindingSet = result.next();
				pathway = bindingSet.getValue("pathway").stringValue();
				n++;
			}
			assertTrue(n==1);
			assertTrue("got "+pathway, pathway.equals("http://model.geneontology.org/R-HSA-4641262/R-HSA-4641262"));
		} catch (QueryEvaluationException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} finally {
			try {
				result.close();
			} catch (QueryEvaluationException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		System.out.println("Done testing regulates via output enables");
	}
	
	//gomodel:R-HSA-4641262/R-HSA-201677 / RO:0002413 / gomodel:R-HSA-4641262/R-HSA-201691
	// #inferProvidesInput	
	/**
	 * Test method for {@link org.geneontology.gocam.exchange.GoCAM#inferProvidesInput}.
	 * Use pathway R-HSA-4641262 , reaction1 = R-HSA-201677 reaction2 = R-HSA-201691
	 * Relation should be RO:0002413 directly positive regulates
	 * Phosphorylation of LRP5/6 cytoplasmic domain by membrane-associated GSK3beta
	 * Phosphorylation of LRP5/6 cytoplasmic domain by CSNKI
	 * 	https://reactome.org/content/detail/R-HSA-4641262 
	 * Compare to http://noctua-dev.berkeleybop.org/editor/graph/gomodel:R-HSA-4641262
	 * 
	 * Also an active site detection test
	 */
	@Test
	public final void testInferProvidesInput() {
		System.out.println("Testing provides input");
		TupleQueryResult result = null;
		try {
			result = blaze.runSparqlQuery(
				"prefix obo: <http://purl.obolibrary.org/obo/> "
				+ "select ?pathway " + 
				"where { " + 
				"VALUES ?reaction1 { <http://model.geneontology.org/R-HSA-201677> } . "+ 
				"VALUES ?reaction2 { <http://model.geneontology.org/R-HSA-201691> } . "+
				" ?reaction1 obo:RO_0002413 ?reaction2 . "
				+ "?reaction1 obo:BFO_0000050 ?pathway "+				
				"}");
			int n = 0; String pathway = null;
			while (result.hasNext()) {
				BindingSet bindingSet = result.next();
				pathway = bindingSet.getValue("pathway").stringValue();
				n++;
			}
			assertTrue(n==1);
			assertTrue("got "+pathway, pathway.equals("http://model.geneontology.org/R-HSA-4641262/R-HSA-4641262"));
		} catch (QueryEvaluationException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} finally {
			try {
				result.close();
			} catch (QueryEvaluationException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		System.out.println("Done testing regulates via output enables");
	}
	
	@Test
	public final void testAny() {
		System.out.println("testing ");
		String pathway = "<http://model.geneontology.org/GLYCLEAV-PWY>";
		String q =  
				" prefix rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#> \n"
				+ "prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#> \n"
				+ "SELECT ?pathway ?pathway_node ?type ?comment \n" + 
				"WHERE {\n" + 
				"  GRAPH "+pathway+"  {  \n" + 
				"    	?pathway_node rdf:type ?type . \n" + 
				"    	?pathway_node rdfs:comment ?comment "+
				"    }\n" + 
				"  } \n";
		TupleQueryResult result = null;
		int n = 0;
		String comment = "";
		try {			
			result = blaze.runSparqlQuery(q);			
			while (result.hasNext()) {
				BindingSet bindingSet = result.next();
				n++;
				comment = bindingSet.getValue("comment").stringValue();
				//String path = bindingSet.getValue("pathway").stringValue();
				String node = bindingSet.getValue("pathway_node").stringValue();
				String type = bindingSet.getValue("type").stringValue();
				System.out.println(pathway+"\t"+node+"\t"+type+"\t"+comment);
			}
		} catch (QueryEvaluationException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} finally {
			try {
				result.close();
			} catch (QueryEvaluationException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		assertTrue("no typed nodes? "+pathway, n>1);
	}
	
	@Test
	public final void testSSSOMbp() {
		System.out.println("testing sssom BP mapping additions");
		String pathway = "<http://model.geneontology.org/GLYCLEAV-PWY>";
		String pathway_node = "<http://model.geneontology.org/GLYCLEAV-PWY/GLYCLEAV-PWY>";
		String pathway_go_type = "<http://purl.obolibrary.org/obo/GO_0019464>";
		String q =  
				" prefix rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#> "
				+ "prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#> "
				+ "SELECT  ?comment  \n" + 
				"WHERE {\n" + 
				"  GRAPH "+pathway+"  {  \n" + 
				"    	"+pathway_node+" rdf:type "+pathway_go_type +" . \n" + 
				"    	"+pathway_node+" rdfs:comment ?comment "+
				"    }\n" + 
				"  } \n";
		TupleQueryResult result = null;
		int n = 0;
		String comment = "";
		try {			
			result = blaze.runSparqlQuery(q);			
			while (result.hasNext()) {
				BindingSet bindingSet = result.next();
				n++;
				comment = bindingSet.getValue("comment").stringValue();
			}
		} catch (QueryEvaluationException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} finally {
			try {
				result.close();
			} catch (QueryEvaluationException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		assertTrue("Didn't get sssom mapped type assertion for "+pathway_node, n==1);
		assertTrue("Didn't get comment on sssom type assertion", comment.startsWith("This type assertion was computed"));
	}
	
	@Test
	public final void testSSSOMmf() {
		System.out.println("testing sssom BP mapping additions");
		String pathway = "<http://model.geneontology.org/PWY3O-981>";
		String reaction_node = "<http://model.geneontology.org/ACETOINDEHYDROG-RXN>";
		String reaction_go_type = "<http://purl.obolibrary.org/obo/GO_0019152>";
		String q =  
				" prefix rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#> "
				+ "prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#> "
				+ "SELECT  ?comment  \n" + 
				"WHERE {\n" + 
				"  GRAPH "+pathway+"  {  \n" + 
				"    	"+reaction_node+" rdf:type "+reaction_go_type +" . \n" + 
				"    	"+reaction_node+" rdfs:comment ?comment "+
				"    }\n" + 
				"  } \n";
		TupleQueryResult result = null;
		int n = 0;
		String comment = "";
		try {			
			result = blaze.runSparqlQuery(q);			
			while (result.hasNext()) {
				BindingSet bindingSet = result.next();
				n++;
				comment = bindingSet.getValue("comment").stringValue();
			}
		} catch (QueryEvaluationException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} finally {
			try {
				result.close();
			} catch (QueryEvaluationException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		assertTrue("Didn't get sssom mapped type assertion for "+reaction_node, n==1);
		assertTrue("Didn't get comment on sssom type assertion", comment.startsWith("This type assertion was computed"));
	}
	
	@Test
	public final void testSGDIdLookup() {
		System.out.println("testing EC->MF lookup via SGD ID");
		String pathway = "<http://model.geneontology.org/YEAST-SALV-PYRMID-DNTP>";
		String reaction_node = "<http://model.geneontology.org/RXN3O-314>";
		String reaction_go_type = "<http://purl.obolibrary.org/obo/GO_0045437>";
		String reaction_gp_type = "<http://identifiers.org/sgd/S000002808>";
		String q =  
				" prefix rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#> "
				+ "prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#> "
				+ "prefix RO: <http://purl.obolibrary.org/obo/RO_> "
				+ "SELECT *  \n" + 
				"WHERE {\n" + 
				"  GRAPH "+pathway+"  {  \n" + 
				"    	"+reaction_node+" rdf:type "+reaction_go_type + " . \n" +
				"    	?gp_node rdf:type "+reaction_gp_type + " . \n" +
				"    	"+reaction_node+" RO:0002333 ?gp_node " +
				"    }\n" + 
				"  } \n";
		int n = runQueryAndGetCount(q);
		assertTrue("Didn't get correct EC-sourced type assertion for "+reaction_node, n==1);
	}
	
	public static int runQueryAndGetCount(String query) {
		TupleQueryResult result = null;
		int n = 0;
		try {			
			result = blaze.runSparqlQuery(query);			
			while (result.hasNext()) {
				result.next();
				n++;
			}
		} catch (QueryEvaluationException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} finally {
			try {
				result.close();
			} catch (QueryEvaluationException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		return n;
	}
	
	@Test
	public final void testChemicalRoleReplacement() {
		System.out.println("testing replacement of CHEBI chemical role with chemical entity");
		String pathway = "<http://model.geneontology.org/ERGOSTEROL-SYN-PWY-1>";
		String reaction_node = "<http://model.geneontology.org/RXN3O-9816>";
		String chemical_entity_type = "<http://purl.obolibrary.org/obo/CHEBI_24431>";
		String chemical_acceptor_type = "<http://purl.obolibrary.org/obo/CHEBI_15339>";
		String q =  
				" prefix rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#> "
				+ "prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#> "
				+ "prefix RO: <http://purl.obolibrary.org/obo/RO_> "
				+ "SELECT *  \n" + 
				"WHERE {\n" + 
				"  GRAPH "+pathway+"  {  \n" + 
				"    	"+reaction_node+" RO:0002233 ?chebi_node . \n" +
				"    	?chebi_node rdf:type "+chemical_entity_type +
				"    }\n" + 
				"  } \n";
		int n = runQueryAndGetCount(q);
		assertTrue("No has_input chemical_entity assertion for "+reaction_node, n==1);
		
		q = 
				" prefix rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#> "
				+ "prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#> "
				+ "prefix owl: <http://www.w3.org/2002/07/owl#> "
				+ "SELECT *  \n" + 
				"WHERE {\n" + 
				"  GRAPH "+pathway+"  {  \n" + 
				"    	?chebi_node rdf:type owl:NamedIndividual . \n" +
				"    	?chebi_node rdf:type "+chemical_acceptor_type +
				"    }\n" + 
				"  } \n";
		n = runQueryAndGetCount(q);
		// acceptor is subclassOf chemical_role
		assertTrue("There are 'acceptor' type assertions in "+pathway, n==0);
	}
}
