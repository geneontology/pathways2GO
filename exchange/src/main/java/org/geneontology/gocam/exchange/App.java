package org.geneontology.gocam.exchange;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.HashSet;
import java.util.Set;

import org.apache.jena.rdf.model.Statement;
import org.apache.jena.rdf.model.StmtIterator;
import org.openrdf.repository.RepositoryException;
import org.openrdf.rio.RDFHandlerException;
import org.openrdf.rio.RDFParseException;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLAxiom;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLClassExpression;
import org.semanticweb.owlapi.model.OWLEquivalentClassesAxiom;
import org.semanticweb.owlapi.model.OWLNamedIndividual;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyCreationException;
import org.semanticweb.owlapi.model.OWLOntologyManager;
import org.semanticweb.owlapi.model.OWLOntologyStorageException;

/**
 * I live to test
 *
 */
public class App {
	//	String minimal_lego = "src/main/resources/org/geneontology/gocam/exchange/go-lego-trimmed.owl";
	//	String noneo_lego = "src/main/resources/org/geneontology/gocam/exchange/go-lego-noneo.owl";
	//	String maximal_lego = "src/main/resources/org/geneontology/gocam/exchange/go-lego-full.owl";	

	public static void main( String[] args ) throws OWLOntologyCreationException, OWLOntologyStorageException, RepositoryException, RDFParseException, RDFHandlerException, IOException {
		String base_ont_title = "Meta Pathway Ontology";
		String iri = "http://model.geneontology.org/"+base_ont_title.hashCode(); //using a URL encoded string here confused the UI code...
		IRI ont_iri = IRI.create(iri);
		GoCAM go_cam = new GoCAM(ont_iri, base_ont_title, "base_contributor", null, "base_provider", false);
		String pid1 = "pid1"; String pid2 = "pid2";
		String root_iri = "http://model.geneontology.org/";

		OWLClass pclass1 = go_cam.df.getOWLClass(IRI.create(root_iri+"pclass1"));
		OWLClass pclass2 = go_cam.df.getOWLClass(IRI.create(root_iri+"pclass2"));
		
		OWLClass complex = go_cam.df.getOWLClass(IRI.create(root_iri+"complexclass1"));
		OWLClassExpression hasPartPclass1 = go_cam.df.getOWLObjectSomeValuesFrom(GoCAM.has_part, pclass1);
		OWLClassExpression hasPartPclass2 = go_cam.df.getOWLObjectSomeValuesFrom(GoCAM.has_part, pclass2);
		Set<OWLClassExpression> parts = new HashSet<OWLClassExpression>();
		parts.add(hasPartPclass1); parts.add(hasPartPclass2);
		OWLClassExpression complex_def = go_cam.df.getOWLObjectIntersectionOf(parts);
		OWLEquivalentClassesAxiom eq = go_cam.df.getOWLEquivalentClassesAxiom(complex, complex_def);
		go_cam.ontman.addAxiom(go_cam.go_cam_ont, eq);
		
		IRI prot_part_iri1 = IRI.create(root_iri+"p1"); IRI prot_part_iri2 = IRI.create(root_iri+"p2");
		OWLNamedIndividual prot_part_entity2 = go_cam.df.getOWLNamedIndividual(prot_part_iri2); 
		OWLNamedIndividual prot_part_entity1 = go_cam.df.getOWLNamedIndividual(prot_part_iri1); //define it independently within this context
		go_cam.addTypeAssertion(prot_part_entity1, pclass1);
		go_cam.addTypeAssertion(prot_part_entity2, pclass2);
		
		OWLNamedIndividual complex_i = go_cam.df.getOWLNamedIndividual(IRI.create(root_iri+Math.random()));
		go_cam.addObjectPropertyAssertion(complex_i, GoCAM.has_part, prot_part_entity1, null);
		go_cam.addObjectPropertyAssertion(complex_i, GoCAM.has_part, prot_part_entity2, null);
	//	go_cam.addTypeAssertion(complex_i, complex);
		go_cam.initializeQRunner(go_cam.go_cam_ont);
		go_cam.writeGoCAM("/Users/bgood/Desktop/test.ttl", true, false);
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
}
