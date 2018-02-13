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
import org.semanticweb.owlapi.model.OWLNamedIndividual;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyCreationException;
import org.semanticweb.owlapi.model.OWLOntologyManager;
import org.semanticweb.owlapi.model.OWLOntologyStorageException;
import org.semanticweb.owlapi.model.RemoveAxiom;

/**
 * I live to test
 *
 */
public class App {
    
	public static void main( String[] args ) throws OWLOntologyCreationException, OWLOntologyStorageException {
    		GoCAM cam = new GoCAM("test ontology title", "contibutor", null, "provider", false);
    		OWLNamedIndividual i = cam.makeAnnotatedIndividual("http://example.com/i");
    		OWLNamedIndividual i2 = cam.makeAnnotatedIndividual("http://example.com/i2");
    		Set<OWLAnnotation> annos = new HashSet<OWLAnnotation>();
    		OWLAnnotation anno = cam.df.getOWLAnnotation(GoCAM.contributor_prop, cam.df.getOWLLiteral("luke skywalker"));
    		annos.add(anno);
    		cam.addObjectPropertyAssertion(i, GoCAM.directly_activates, i2, annos);
    		cam.writeGoCAM("/Users/bgood/Desktop/test/apptest.ttl");
	}
}
