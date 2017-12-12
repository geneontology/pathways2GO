package noctua.exchange;

import java.io.File;
import java.util.Iterator;
import java.util.stream.Stream;

import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.io.FileDocumentTarget;
import org.semanticweb.owlapi.model.AddAxiom;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLAxiom;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLDataFactory;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyCreationException;
import org.semanticweb.owlapi.model.OWLOntologyManager;
import org.semanticweb.owlapi.model.OWLOntologyStorageException;
import org.semanticweb.owlapi.model.RemoveAxiom;

/**
 * Hello OWL world!
 *
 */
public class App 
{
    public static final IRI pizza_iri = IRI.create("https://protege.stanford.edu/ontologies/pizza/pizza.owl");
    public static final IRI noctua_test_iri = IRI.create("http://noctua.berkeleybop.org/download/gomodel:59dc728000000287/owl");
    public static final IRI go_lego_iri = IRI.create("http://purl.obolibrary.org/obo/go/extensions/go-lego.owl");
    public static void main( String[] args ) throws OWLOntologyCreationException, OWLOntologyStorageException
    {
        System.out.println( "Hello OWL World!" );
        //make an ontology
        OWLOntologyManager m = OWLManager.createOWLOntologyManager();
        OWLOntology o = m.createOntology();
        System.out.println("Created empty ontology");
        //add a class
        OWLDataFactory df = OWLManager.getOWLDataFactory();
     // class A and class B
        OWLClass clsA = df.getOWLClass(IRI.create(pizza_iri + "#A")); 
        OWLClass clsB = df.getOWLClass(IRI.create(pizza_iri + "#B"));
     // Now create the axiom
        OWLAxiom axiom = df.getOWLSubClassOfAxiom(clsA, clsB);
        // add the axiom to the ontology.
        AddAxiom addAxiom = new AddAxiom(o, axiom);
        m.applyChange(addAxiom);
        System.out.println(o.axioms().count());
     // remove the axiom from the ontology
     //   RemoveAxiom removeAxiom = new RemoveAxiom(o,axiom);
     //   m.applyChange(removeAxiom);
        System.out.println(o.axioms().count());

        
        //import from an IRI
        OWLOntology pizza = m.loadOntology(pizza_iri);
        System.out.println("Loaded remote ontology");
        //show axioms
        Stream<OWLAxiom> ax_stream = pizza.axioms();
       // System.out.println("Remote ontology axiom count: "+ax_stream.count());
//        Iterator<OWLAxiom> axes = ax_stream.iterator();
//       while(axes.hasNext()) {
//    	   	OWLAxiom ax = (OWLAxiom) axes.next();
//    	   		System.out.println(ax.isAnonymous()+" "+ax.toString());
//       }
        
         //export
        FileDocumentTarget outfile = new FileDocumentTarget(new File("/Users/bgood/Documents/test.owl"));
        m.saveOntology(o,outfile);
    }
}
