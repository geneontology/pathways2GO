package noctua.exchange;

import java.io.File;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.stream.Stream;

import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.io.FileDocumentTarget;
import org.semanticweb.owlapi.model.AddAxiom;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLAnnotation;
import org.semanticweb.owlapi.model.OWLAxiom;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLDataFactory;
import org.semanticweb.owlapi.model.OWLEntity;
import org.semanticweb.owlapi.model.OWLIndividual;
import org.semanticweb.owlapi.model.OWLNamedIndividual;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyCreationException;
import org.semanticweb.owlapi.model.OWLOntologyManager;
import org.semanticweb.owlapi.model.OWLOntologyStorageException;
import org.semanticweb.owlapi.model.RemoveAxiom;
import org.semanticweb.owlapi.search.EntitySearcher;

/**
 * I live to test
 *
 */
public class App {

	public static void main( String[] args ) throws OWLOntologyCreationException, OWLOntologyStorageException {
		int x = 100; int y = 0;
		
		
		GoCAM go_cam = new GoCAM("test ontology title", "contibutor", null, "provider", false);
		String in = "src/main/resources/reactome/glycolysis/glyco_biopax.owl";
		String out = "/Users/bgood/Desktop/test/test.ttl";
		go_cam.readGoCAM(in);
		//m.loadOntology(IRI.create("http://domain.for.import.ontology/importedontology"));
//		String minimal_lego = "/Users/bgood/minerva/minerva-server/src/main/resources/go-lego-trimmed.owl";
//		String noneo_lego = "/Users/bgood/minerva/minerva-server/src/main/resources/go-lego-noneo.owl";
//		String maximal_lego = "/Users/bgood/minerva/minerva-server/src/main/resources/go-lego-full.owl";
	//	go_cam.ontman.loadOntologyFromOntologyDocument(new File(minimal_lego));
		
//		go_cam.writeGoCAM(out);

		//use stream to print out labels for members of a class
		//		OWLClass pathway_class = go_cam.df.getOWLClass(IRI.create(BioPaxtoGO.biopax_iri + "Pathway")); 
		//    		EntitySearcher.
		//    			getIndividuals(pathway_class, go_cam.go_cam_ont).
		//    				forEach(pathway -> EntitySearcher.getAnnotationObjects((OWLEntity) pathway, go_cam.go_cam_ont, GoCAM.rdfs_label).
		//    						forEach(System.out::println)
		//    						);
	}
}
