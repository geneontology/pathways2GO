package org.geneontology.gocam.exchange;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.jena.rdf.model.Statement;
import org.apache.jena.rdf.model.StmtIterator;
import org.apache.jena.vocabulary.RDFS;
import org.openrdf.repository.RepositoryException;
import org.openrdf.rio.RDFHandlerException;
import org.openrdf.rio.RDFParseException;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLAnnotation;
import org.semanticweb.owlapi.model.OWLAnnotationAxiom;
import org.semanticweb.owlapi.model.OWLAnnotationProperty;
import org.semanticweb.owlapi.model.OWLAxiom;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLClassExpression;
import org.semanticweb.owlapi.model.OWLDataFactory;
import org.semanticweb.owlapi.model.OWLEquivalentClassesAxiom;
import org.semanticweb.owlapi.model.OWLIndividual;
import org.semanticweb.owlapi.model.OWLNamedIndividual;
import org.semanticweb.owlapi.model.OWLObjectProperty;
import org.semanticweb.owlapi.model.OWLObjectPropertyAssertionAxiom;
import org.semanticweb.owlapi.model.OWLObjectPropertyAxiom;
import org.semanticweb.owlapi.model.OWLObjectSomeValuesFrom;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyCreationException;
import org.semanticweb.owlapi.model.OWLOntologyManager;
import org.semanticweb.owlapi.model.OWLOntologyStorageException;
import org.semanticweb.owlapi.model.RemoveAxiom;
import org.semanticweb.owlapi.search.EntitySearcher;
import org.semanticweb.owlapi.util.OWLOntologyWalker;
import org.semanticweb.owlapi.util.OWLOntologyWalkerVisitor;
import org.semanticweb.owlapi.util.OWLOntologyWalkerVisitorEx;
import org.semanticweb.owlapi.vocab.OWLRDFVocabulary;

/**
 * I live to test
 *
 */
public class App {
	//	String minimal_lego = "src/main/resources/org/geneontology/gocam/exchange/go-lego-trimmed.owl";
	//	String noneo_lego = "src/main/resources/org/geneontology/gocam/exchange/go-lego-noneo.owl";
	//	String maximal_lego = "src/main/resources/org/geneontology/gocam/exchange/go-lego-full.owl";	

	public static void main( String[] args ) throws OWLOntologyCreationException, OWLOntologyStorageException, RepositoryException, RDFParseException, RDFHandlerException, IOException {
		String ontf = "/Users/bgood/Desktop/test/bmp_output/converted-bmp-Signaling_by_BMP.ttl";
		OWLOntologyManager man = OWLManager.createOWLOntologyManager();
		OWLDataFactory df = man.getOWLDataFactory();
		OWLOntology ont = man.loadOntologyFromOntologyDocument(new File(ontf));
		IRI source_iri = IRI.create("http://www.reactome.org/biopax/63/201451#BiochemicalReaction9");
		IRI prop_iri = IRI.create("http://purl.obolibrary.org/obo/RO_0002333");
		IRI target_iri = IRI.create("http://www.reactome.org/biopax/63/201451#Protein29-842491573");
		//get any existing axioms and any annotations for said triple		
		System.out.println(ont.getAxiomCount());
		OWLOntologyWalker walker = new OWLOntologyWalker(Collections.singleton(ont));
		UpdateAnnotationsVisitor updater = new UpdateAnnotationsVisitor(walker, source_iri, prop_iri, target_iri);
		walker.walkStructure(updater); 
		//now ready to update by deleting and then creating again...
		man.removeAxioms(ont, updater.getToremove());
		System.out.println(ont.getAxiomCount());
		Set<OWLAnnotation> annos = updater.getOldannos();
		//add new ones now..
//		OWLAnnotation title_anno = df.getOWLAnnotation(title_prop, df.getOWLLiteral(gocam_title));
		OWLAnnotationProperty comment = df.getOWLAnnotationProperty(OWLRDFVocabulary.RDFS_COMMENT.getIRI());
		OWLAnnotation comment1 = df.getOWLAnnotation(comment, df.getOWLLiteral("Yay I did it"));	
		annos.add(comment1);
		//and add the axiom back in
		OWLObjectPropertyAssertionAxiom back = df.getOWLObjectPropertyAssertionAxiom(df.getOWLObjectProperty(prop_iri), df.getOWLNamedIndividual(source_iri), df.getOWLNamedIndividual(target_iri), annos);
		man.addAxiom(ont, back);
		System.out.println(ont.getAxiomCount());
	}




	public static void buildSparqlable() throws OWLOntologyCreationException, OWLOntologyStorageException, FileNotFoundException{
		String input_folder = "/Users/bgood/reactome-go-cam-models/humantest/";
		String output_folder = "/Users/bgood/reactome-go-cam-models/humantest_reasoned/";
		String tbox_file = "src/main/resources/org/geneontology/gocam/exchange/ro-merged.owl";
		OWLOntologyManager tman = OWLManager.createOWLOntologyManager();
		OWLOntology tbox = tman.loadOntologyFromOntologyDocument(new File(tbox_file));	
		ArachneAccessor a = new ArachneAccessor(tbox);
		boolean add_property_definitions = false;
		boolean add_class_definitions = false;
		a.reasonAllInFolder(input_folder, output_folder, add_property_definitions, add_class_definitions);
	}

	public static void queryCollection() throws OWLOntologyCreationException, OWLOntologyStorageException, IOException {
		String input_folder = "/Users/bgood/reactome-go-cam-models/humantest/";
		OWLOntology abox = ArachneAccessor.makeOneOntologyFromDirectory(input_folder);
		//prepare tbox
		String tbox_file = "src/main//resources/org/geneontology/gocam/exchange/ro-merged.owl";
		OWLOntologyManager tman = OWLManager.createOWLOntologyManager();
		OWLOntology tbox = tman.loadOntologyFromOntologyDocument(new File(tbox_file));	
		//test inference
		boolean add_inferences = false;
		boolean add_property_definitions = true;
		boolean add_class_definitions = false;
		QRunner q = testInference(abox, tbox, add_inferences, add_property_definitions, add_class_definitions);
		q.dumpModel("/Users/bgood/reactome-go-cam-models/all_human_no_inference.ttl", "TURTLE");
	}

	public static void test1() throws OWLOntologyCreationException, OWLOntologyStorageException, FileNotFoundException {
		//prepare an abox (taken from Arachne test case)
		// https://github.com/balhoff/arachne/tree/master/src/test/resources/org/geneontology/rules
		String abox_file = "src/main/resources/org/geneontology/gocam/exchange/57c82fad00000639.ttl";
		OWLOntologyManager aman = OWLManager.createOWLOntologyManager();
		OWLOntology abox = aman.loadOntologyFromOntologyDocument(new File(abox_file));	

		//prepare tbox
		String tbox_file = "src/main/resources/org/geneontology/gocam/exchange/ro-merged.owl";
		OWLOntologyManager tman = OWLManager.createOWLOntologyManager();
		OWLOntology tbox = tman.loadOntologyFromOntologyDocument(new File(tbox_file));	
		boolean add_inferences = false;
		boolean add_property_definitions = true;
		boolean add_class_definitions = true;
		QRunner no_inf = testInference(abox, tbox, add_inferences, add_property_definitions, add_class_definitions);
		add_inferences = true;
		QRunner inf = testInference(abox, tbox, add_inferences, add_property_definitions, add_class_definitions);
		StmtIterator base = no_inf.jena.listStatements();
		int missing = 0;
		while(base.hasNext()) {
			Statement s = base.next();
			if(!inf.jena.contains(s)) {
				missing++;
				if(missing < 10) {
					System.out.println("Missing from reasoned model:\n\t"+s);
				}
			}
		}
		System.out.println("Missing from reasoned model:"+missing);
	}

	//TODO Maybe someday unit tests..  
	public static QRunner testInference(OWLOntology abox, OWLOntology tbox, 
			boolean add_inferences, boolean add_property_definitions, boolean add_class_definitions)  throws OWLOntologyCreationException, OWLOntologyStorageException, FileNotFoundException {
		//Test reading, reasoning, query

		//build the graph
		QRunner q = new QRunner(tbox, abox, add_inferences, add_property_definitions, add_class_definitions);
		//ask it questions
		boolean c = q.isConsistent();
		System.out.println("Is it consistent? "+c);
		if(!c) {
			Set<String> uns = q.getUnreasonableEntities();
			System.out.println("Entities that equal owl:Nothing");
			for(String u : uns) {
				System.out.println(u);
			}
		}
		//how big is it?
		int n = q.nTriples();
		System.out.println("N triples "+n); 
		//how many inferred triples? (assuming inference on)
		if(add_inferences) {
			System.out.println("inferred "+(q.wm.facts().size()-q.wm.asserted().size()));
			System.out.println("All "+q.wm.facts().size());
			//q.printFactsExplanations();
		}

		//57c82fad00000639.ttl + ro-merged.owl no inference = 6630 triples
		//57c82fad00000639.ttl + ro-merged.owl with inference = 2852 triples, including 282 inferred
		//57c82fad00000639.ttl + ro-merged.owl with inference, without indirectRules = 2834 triples, including 264 inferred		
		//57c82fad00000639.ttl + ro-merged.owl with inference, without triples from tbox = 629 triples, including 282 inferred
		//57c82fad00000639.ttl + ro-merged.owl with inference, without indirectRules, without triples from tbox = 611 triples, including 264 inferred
		//test says arachneInferredTriples.size shouldEqual 611  
		//arachneInferredTriples = wm.facts
		return q;
	}

	OWLNamedIndividual addComplexAsSimpleClass(GoCAM go_cam, Set<String> component_names, IRI complex_instance_iri, Set<OWLAnnotation> annotations) {
		String combo_name = GoCAM.base_iri;
		for(String n : component_names) {
			combo_name=combo_name+"_"+n;
		}
		OWLClass complex_class = go_cam.df.getOWLClass(IRI.create(combo_name));
		go_cam.addSubclassAssertion(complex_class, GoCAM.go_complex, annotations);
		//complex instance
		OWLNamedIndividual complex_i = go_cam.makeAnnotatedIndividual(complex_instance_iri);
		go_cam.addTypeAssertion(complex_i, complex_class);
		return complex_i;
	}


	OWLNamedIndividual addComplexAsLogicalClass(GoCAM go_cam, Set<IRI> component_iris, IRI complex_instance_iri, Set<OWLAnnotation> annotations) {
		String combo_name = GoCAM.base_iri;
		for(IRI component_iri : component_iris) {
			combo_name=combo_name+"_"+component_iri.getShortForm();
		}
		OWLClass complex_class = go_cam.df.getOWLClass(IRI.create(combo_name));
		//could be inferred if we added an if has_protein_parts axiom to parent, but not our ontology..
		go_cam.addSubclassAssertion(complex_class, GoCAM.go_complex, annotations);
		//parts list as class expressions and individuals 
		Set<OWLClassExpression> part_classes = new HashSet<OWLClassExpression>();
		Set<OWLNamedIndividual> part_is = new HashSet<OWLNamedIndividual>();
		for(IRI component_iri : component_iris) {
			//add or get the class
			OWLClass protein_class = go_cam.df.getOWLClass(component_iri);		
			OWLClassExpression hasPartPclass = go_cam.df.getOWLObjectSomeValuesFrom(GoCAM.has_part, protein_class);
			part_classes.add(hasPartPclass);
			//make the instance
			OWLNamedIndividual prot_part_entity = go_cam.makeAnnotatedIndividual(component_iri);
			go_cam.addTypeAssertion(prot_part_entity, protein_class);
			part_is.add(prot_part_entity);
		}
		//build intersection class 
		OWLClassExpression complex_def = go_cam.df.getOWLObjectIntersectionOf(part_classes);
		OWLEquivalentClassesAxiom eq = go_cam.df.getOWLEquivalentClassesAxiom(complex_class, complex_def);
		go_cam.ontman.addAxiom(go_cam.go_cam_ont, eq);
		//complex instance
		OWLNamedIndividual complex_i = go_cam.makeAnnotatedIndividual(complex_instance_iri);
		for(OWLNamedIndividual i : part_is) {
			go_cam.addObjectPropertyAssertion(complex_i, GoCAM.has_part, i, annotations);
		}
		//this could be inferred based on definition above, but since we know right now no need to run reasoner
		go_cam.addTypeAssertion(complex_i, complex_class);
		return complex_i;
	}
}
