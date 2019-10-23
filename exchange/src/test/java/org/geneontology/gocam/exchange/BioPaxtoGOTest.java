/**
 * 
 */
package org.geneontology.gocam.exchange;

import static org.junit.Assert.*;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.geneontology.rules.engine.WorkingMemory;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.openrdf.query.BindingSet;
import org.openrdf.query.QueryEvaluationException;
import org.openrdf.query.TupleQueryResult;
import org.openrdf.repository.RepositoryException;
import org.openrdf.rio.RDFHandlerException;
import org.openrdf.rio.RDFParseException;
import org.semanticweb.owlapi.model.OWLOntologyCreationException;
import org.semanticweb.owlapi.model.OWLOntologyStorageException;

/**
 * @author bgood
 *
 */
public class BioPaxtoGOTest {
	//use default values for testing
	static BioPaxtoGO bp2g = new BioPaxtoGO();
	//parameters to set
	static String empty_catalogue_file = "./src/test/resources/catalog-no-import.xml";
	static String input_biopax = "./src/test/resources/biopax/"; 
	static String output_file_folder = "./src/test/resources/gocam/"; 
	static String output_file_stub = "./src/test/resources/gocam/test-"; 
	static String output_blazegraph_journal = "./src/test/resources/gocam/blazegraph.jnl";  
	static String tag = ""; //unexpanded
	static String base_title = "title here";//"Will be replaced if a title can be found for the pathway in its annotations
	static String default_contributor = "";//"https://orcid.org/0000-0002-7334-7852"; //
	static String default_provider = "";//"https://reactome.org";//"https://www.wikipathways.org/";//"https://www.pathwaycommons.org/";	
	static String test_pathway_name = null;
	static String go_lego_file = "./src/test/resources/go-lego-test.owl";
	static String go_plus_file = "./src/test/resources/go-plus.owl";
	static Blazer blaze;
	static QRunner tbox_qrunner;
	/**
	 * @throws java.lang.Exception 
	 */
	@BeforeClass
	public static void setUpBeforeClass() throws Exception {
		System.out.println("set up before class");
		bp2g.go_lego_file = go_lego_file;
		bp2g.goplus = new GOPlus(go_plus_file);
		bp2g.blazegraph_output_journal = output_blazegraph_journal;
		//clean out any prior data in triple store
		FileWriter clean = new FileWriter(bp2g.blazegraph_output_journal, false);
		clean.write("");
		clean.close();
		//open connection to triple store
		blaze = new Blazer(bp2g.blazegraph_output_journal);
		//initialize the rules for inference
		tbox_qrunner = GoCAM.getQRunnerForTboxInference(Collections.singleton(bp2g.go_lego_file));
		//run the conversion on all the test biopax files
		System.out.println("test biopaxtogo");
		File dir = new File(input_biopax);
		File[] directoryListing = dir.listFiles();
		//run through all files
		if (directoryListing != null) {
			for (File biopax : directoryListing) {
				String name = biopax.getName();
				if(name.contains(".owl")||name.contains(".xml")) { 
					name = name.replaceAll(".owl", "-");
					name = name.replaceAll(".xml", "-");
					String this_output_file_stub = output_file_stub+name;
					try {
						bp2g.convert(biopax.getAbsolutePath(), this_output_file_stub, base_title, default_contributor, default_provider, tag, null, blaze, tbox_qrunner);
					} catch (OWLOntologyCreationException | OWLOntologyStorageException | RepositoryException
							| RDFParseException | RDFHandlerException | IOException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				}
			} 
		}else {
			try {
				bp2g.convert(input_biopax, output_file_stub, base_title, default_contributor, default_provider, tag, null, blaze, tbox_qrunner);
			} catch (OWLOntologyCreationException | OWLOntologyStorageException | RepositoryException
					| RDFParseException | RDFHandlerException | IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		System.out.println("done set up before class");
	}

	
	/**
	 * @throws java.lang.Exception
	 */
	@AfterClass
	public static void tearDownAfterClass() throws Exception {
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
	//@Test
	public final void testOWLConsistency() {
		File dir = new File(output_file_folder);
		File[] directoryListing = dir.listFiles();
		for (File abox_file : directoryListing) {
			if(abox_file.getAbsolutePath().endsWith(".ttl")) {
				try {
					GoCAM go_cam = new GoCAM(abox_file.getAbsoluteFile(), empty_catalogue_file);
					go_cam.qrunner = new QRunner(go_cam.go_cam_ont); 		
					WorkingMemory wm_with_tbox = tbox_qrunner.arachne.createInferredModel(go_cam.go_cam_ont,false, false);			
					go_cam.qrunner.jena = go_cam.qrunner.makeJenaModel(wm_with_tbox);
					boolean is_logical = go_cam.validateGoCAM();	
					System.out.println(abox_file.getName()+" owl consistent:"+is_logical);
					assertTrue(is_logical);
				} catch (OWLOntologyCreationException | IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
			
		}
	}
	
	/**
	 * Test method for {@link org.geneontology.gocam.exchange.GoCAM#inferTransportProcess()}.
	 * Test that transport processes are:
	 *  correctly typed as molecular function
	 * 	have the proper starting and ending locations
	 *  have the right number of inputs and outputs
	 *  have an input that is also an output 
	 * Use reaction in Signaling By BMP R-HSA-201451
	 * 	The phospho-R-Smad1/5/8:Co-Smad transfers to the nucleus
	 * 	https://reactome.org/content/detail/R-HSA-201472
	 * Compare to http://noctua-dev.berkeleybop.org/editor/graph/gomodel:R-HSA-201451
	 */
	@Test
	public final void testInferTransportProcess() {
		TupleQueryResult result = null;
		try {
			String query =
					"prefix obo: <http://purl.obolibrary.org/obo/> "
					+ "select ?type (count(distinct ?output) AS ?outputs) (count(distinct ?input) AS ?inputs) " + 
					"where { " + 
					" VALUES ?reaction { <http://model.geneontology.org/R-HSA-201451/R-HSA-201472> } "
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
			assertTrue(type.equals("http://purl.obolibrary.org/obo/GO_0003674"));
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
	}

	/**
	 * Test method for {@link org.geneontology.gocam.exchange.GoCAM#inferTransportProcess()}.
	 * Test that dissociation processes are:
	 * 	correctly typed: as molecular function 
	 *  have proper start and end locations defined 	
	 *  and have the correct number of inputs and outputs
	 * Use reaction in Signaling By BMP R-HSA-201451
	 * 	Phospho-R-Smad1/5/8 dissociates from the receptor complex
	 * 	https://reactome.org/content/detail/R-HSA-201453
	 * Compare to http://noctua-dev.berkeleybop.org/editor/graph/gomodel:R-HSA-201451
	 */
	@Test
	public final void testInferDissociationProcess() {
		TupleQueryResult result = null;
		try {
			result = blaze.runSparqlQuery(
				"prefix obo: <http://purl.obolibrary.org/obo/> "
				+ "select ?type (count(distinct ?output) AS ?outputs) (count(distinct ?input) AS ?inputs) " + 
				"where { " + 
				"VALUES ?reaction { <http://model.geneontology.org/R-HSA-201451/R-HSA-201453> }" + 
				"  ?reaction rdf:type ?type .	" + 
				"  ?reaction obo:RO_0002339 ?endlocation . " + 
				"  ?endlocation rdf:type <http://purl.obolibrary.org/obo/GO_0005829> . " + 
				"  ?reaction obo:RO_0002338 ?startlocation . " + 
				"  ?startlocation rdf:type <http://purl.obolibrary.org/obo/GO_0031901> ." + 
				"  ?reaction obo:RO_0002234 ?output . " + 
				"  ?reaction obo:RO_0002233 ?input ." + 
				"  filter(?type != owl:NamedIndividual)" + 
				"} "+
				"group by ?type ");
			int n = 0; String type = null; int outputs = 0; int inputs = 0;
			while (result.hasNext()) {
				BindingSet bindingSet = result.next();
				type = bindingSet.getValue("type").stringValue();
				outputs = Integer.parseInt(bindingSet.getValue("outputs").stringValue());
				inputs = Integer.parseInt(bindingSet.getValue("inputs").stringValue());
				n++;
			}
			assertTrue(n==1);
			assertTrue(type.equals("http://purl.obolibrary.org/obo/GO_0003674"));
			assertTrue(outputs == 2);
			assertTrue(inputs == 1);
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
		TupleQueryResult result = null;
		try {
			result = blaze.runSparqlQuery(
				"prefix obo: <http://purl.obolibrary.org/obo/> "
				+ "select ?locationclass " + 
				"where { " + 
				"VALUES ?reaction { <http://model.geneontology.org/R-HSA-201451/R-HSA-201422> }" + 
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
		TupleQueryResult result = null;
		try {
			result = blaze.runSparqlQuery(
				"prefix obo: <http://purl.obolibrary.org/obo/> "
				+ "select ?locationclass " + 
				"where { " + 
				"VALUES ?reaction { <http://model.geneontology.org/R-HSA-201451/R-HSA-201425> }" + 
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
	}
}
