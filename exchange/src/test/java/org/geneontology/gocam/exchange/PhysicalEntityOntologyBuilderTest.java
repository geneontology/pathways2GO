/**
 * 
 */
package org.geneontology.gocam.exchange;

import static org.junit.Assert.*;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.geneontology.gocam.exchange.PhysicalEntityOntologyBuilder.ReasonerImplementation;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.obolibrary.robot.CatalogXmlIRIMapper;
import org.openrdf.repository.RepositoryException;
import org.openrdf.rio.RDFHandlerException;
import org.openrdf.rio.RDFParseException;
import org.semanticweb.HermiT.ReasonerFactory;
import org.semanticweb.elk.owlapi.ElkReasonerFactory;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.AddImport;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLDataFactory;
import org.semanticweb.owlapi.model.OWLImportsDeclaration;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyCreationException;
import org.semanticweb.owlapi.model.OWLOntologyManager;
import org.semanticweb.owlapi.model.OWLOntologyStorageException;
import org.semanticweb.owlapi.reasoner.Node;
import org.semanticweb.owlapi.reasoner.OWLReasoner;
import org.semanticweb.owlapi.reasoner.OWLReasonerFactory;

import com.google.common.collect.Sets;

/**
 * @author benjamingood
 *
 */
public class PhysicalEntityOntologyBuilderTest {

	static String input_biopax_file = "/Users/benjamingood/test/biopax/March2020_Homo_sapiens.owl";//"./src/test/resources/biopax/bmp.owl";
	static String reacto_out = "./src/test/resources/ontology/reacto_test";
	static String go_lego_other_file = "/Users/benjamingood/GitHub/go-ontology/src/ontology/extensions/go-lego-no-neo-no-reacto.owl";
	static String catalog = "/Users/benjamingood/gocam_ontology/catalog-no-neo-no-reacto.xml";

	/**
	 * @throws java.lang.Exception
	 */
	@BeforeClass
	public static void setUpBeforeClass() throws Exception {
	}

	/**
	 * @throws java.lang.Exception
	 */
	@AfterClass
	public static void tearDownAfterClass() throws Exception {
	}

	@Test
	public void reasonerTest() {
		try {
			//OWLOntology go_lego_other = 
			OWLOntologyManager	ontman = OWLManager.createOWLOntologyManager();				
			OWLOntology go_lego_tbox = ontman.loadOntologyFromOntologyDocument(new File(go_lego_other_file));			
			System.out.println(" making reacto ");
//			OWLOntology reacto = PhysicalEntityOntologyBuilder.buildReacto(input_biopax_file, reacto_out, go_lego_tbox);
			OWLOntology reacto = ontman.loadOntologyFromOntologyDocument(new File("/Users/benjamingood/Downloads/reacto-3.owl"));
			System.out.println(" loading go-lego for satisfiability test ");
			//OWLOntology go_lego_other = reacto.getOWLOntologyManager().loadOntologyFromOntologyDocument(new File(go_lego_other_file));
			System.out.println(" Adding "+go_lego_tbox.getAxiomCount()+" axioms from go-lego to reacto ");
			
			IRI lego_iri = IRI.create("http://purl.obolibrary.org/obo/go/extensions/go-lego.owl");
			ontman.setIRIMappers(Sets.newHashSet(new CatalogXmlIRIMapper(catalog)));
			OWLImportsDeclaration importDeclaration=ontman.getOWLDataFactory().getOWLImportsDeclaration(lego_iri);
			ontman.applyChange(new AddImport(reacto, importDeclaration));
			ontman.loadOntology(lego_iri);
			
//			reacto.getOWLOntologyManager().addAxioms(reacto, go_lego_tbox.getAxioms());
			OWLReasonerFactory reasonerFactory = new ElkReasonerFactory();
			System.out.println(" creating reacto reasoner ");
			OWLReasoner reasoner = reasonerFactory.createReasoner(reacto);
			reasoner.flush();
			assertTrue("reacto plus go-lego is not consistent ", reasoner.isConsistent());
			if(!reasoner.isConsistent()) {
				Node<OWLClass> u = reasoner.getUnsatisfiableClasses();
				System.out.println(u.getSize()+"inconsistent classes:");
				for(OWLClass broken : u.getEntitiesMinusTop()) {
					System.out.println(broken);
				}
			}else {
				System.out.println("reacto is consistent with go-lego");
			}
		} catch (OWLOntologyCreationException e
				//|OWLOntologyStorageException | RepositoryException | RDFParseException | RDFHandlerException | IOException e
				) 
		{
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

}
