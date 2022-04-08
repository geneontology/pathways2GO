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
public class BioPaxtoGOYeastCycTest {
	//use default values for testing
	static BioPaxtoGO bp2g = new BioPaxtoGO(); 
	//parameters to set
	static String sssom_file = "./target/classes/YeastCyc/obomatch-go-yeastpathway.sssom.tsv.txt";
	static String input_biopax = "./src/test/resources/cyc/"; 
	static String output_file_folder = "./src/test/resources/gocam/yeastcyc/"; 
	static String output_file_stub = "./src/test/resources/gocam/yeastcyc/test-"; 
	static String output_blazegraph_journal = "./src/test/resources/gocam/yeastcyc/blazegraph.jnl"; //"/Users/bgood/noctua-config/blazegraph.jnl"; //
	static String tag = ""; //unexpanded
	static String base_title = "title here";//"Will be replaced if a title can be found for the pathway in its annotations
	static String default_contributor = "https://orcid.org/0000-0002-7334-7852"; //
	static String default_provider = "https://yeastgenome.org";//"https://www.wikipathways.org/";//"https://www.pathwaycommons.org/";	
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
		fullBuild();
		//loadBlazegraph();
	}
	
	public static void loadBlazegraph() {
		bp2g.blazegraph_output_journal = output_blazegraph_journal;
		blaze = new Blazer(bp2g.blazegraph_output_journal);
	}
	
	public static void fullBuild() throws Exception{
		bp2g.entityStrategy = BioPaxtoGO.EntityStrategy.YeastCyc;
		bp2g.sssom = new SSSOM(sssom_file);
		bp2g.generate_report = false;
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
		//run the conversion on all the test biopax files
		System.out.println("running biopaxtogo on all test files");
		File dir = new File(input_biopax);
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
				bp2g.convert(input_biopax, output_file_stub, base_title, default_contributor, default_provider, tag, null, blaze, taxa);
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
	 * Test that all generated models are consistent.
	 */
	@Test
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
