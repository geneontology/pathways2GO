package noctua.exchange;

import java.io.File;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.jena.rdf.model.Statement;
import org.apache.jena.reasoner.rulesys.Rule;
import org.geneontology.jena.OWLtoRules;
import org.geneontology.jena.SesameJena;
import org.geneontology.rules.engine.RuleEngine;
import org.geneontology.rules.engine.Triple;
import org.geneontology.rules.engine.WorkingMemory;
import org.geneontology.rules.util.Bridge;
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
import org.semanticweb.owlapi.model.parameters.Imports;
import org.semanticweb.owlapi.search.EntitySearcher;

import scala.collection.JavaConverters;

import org.phenoscape.scowl.*;

/**
 * I live to test
 *
 */
public class App {
	private final RuleEngine ruleEngine;

	App(OWLOntology ontology){
		ruleEngine = initializeRuleEngine(ontology);
	}
	
	public static void main( String[] args ) throws OWLOntologyCreationException, OWLOntologyStorageException {
		int x = 100; int y = 0;
		
		
		GoCAM go_cam = new GoCAM("test ontology title", "contibutor", null, "provider", false);
		String in = "src/main/resources/reactome/glycolysis/glyco_biopax.owl";
		String out = "/Users/bgood/Desktop/test/test44444.ttl";
		go_cam.readGoCAM(in);
		go_cam.writeGoCAM(out);
		
		App app = new App(go_cam.go_cam_ont);
		IRI ontology_id = IRI.create("http://nothing");
		WorkingMemory mem = app.createInferredModel(go_cam.go_cam_ont, ontology_id);
		
		
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
	
	private RuleEngine initializeRuleEngine(OWLOntology ontology) {
		Set<Rule> rules = new HashSet<Rule>();
		rules.addAll(JavaConverters.setAsJavaSetConverter(OWLtoRules.translate(ontology, Imports.INCLUDED, true, true, true, true)).asJava());
		rules.addAll(JavaConverters.setAsJavaSetConverter(OWLtoRules.indirectRules(ontology)).asJava());
		return new RuleEngine(Bridge.rulesFromJena(JavaConverters.asScalaSetConverter(rules).asScala()), true);
	}
	
	/**
	 * Return Arachne working memory representing LEGO model combined with inference rules.
	 * This model will not remain synchronized with changes to data.
	 * @param LEGO modelId
	 * @return Jena model
	 */
	public WorkingMemory createInferredModel(OWLOntology ontology, IRI ontology_id) {
		//Set<Statement> statements = JavaConverters.setAsJavaSetConverter(SesameJena.ontologyAsTriples(getModelAbox(modelId))).asJava();
		Set<Statement> statements = JavaConverters.setAsJavaSetConverter(SesameJena.ontologyAsTriples(ontology)).asJava();
		
		Set<Triple> triples = statements.stream().map(s -> Bridge.tripleFromJena(s.asTriple())).collect(Collectors.toSet());
		try {
			// Using model's ontology IRI so that a spurious different ontology declaration triple isn't added
			OWLOntology schemaOntology = OWLManager.createOWLOntologyManager().createOntology(ontology.getRBoxAxioms(Imports.INCLUDED), ontology_id);
			Set<Statement> schemaStatements = JavaConverters.setAsJavaSetConverter(SesameJena.ontologyAsTriples(schemaOntology)).asJava();
			triples.addAll(schemaStatements.stream().map(s -> Bridge.tripleFromJena(s.asTriple())).collect(Collectors.toSet()));
		} catch (OWLOntologyCreationException e) {
			System.out.println("Couldn't add rbox statements to data model.");
			System.out.println(e);
		}
		return ruleEngine.processTriples(JavaConverters.asScalaSetConverter(triples).asScala());
	}
	
}
