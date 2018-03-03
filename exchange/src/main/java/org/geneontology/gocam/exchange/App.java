package org.geneontology.gocam.exchange;

import java.io.File;

import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.IRI;
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
	
	public static void main( String[] args ) throws OWLOntologyCreationException, OWLOntologyStorageException {
		//Test reading, reasoning, query

		//prepare an abox (taken from Arachne test case)
		// https://github.com/balhoff/arachne/tree/master/src/test/resources/org/geneontology/rules
		String abox_file = "src/main/resources/org/geneontology/gocam/exchange/57c82fad00000639.ttl";
		OWLOntologyManager aman = OWLManager.createOWLOntologyManager();
		OWLOntology abox = aman.loadOntologyFromOntologyDocument(new File(abox_file));	
		
		//prepare tbox
		String tbox_file = "src/main/resources/org/geneontology/gocam/exchange/ro-merged.owl";
		OWLOntologyManager tman = OWLManager.createOWLOntologyManager();
		OWLOntology tbox = tman.loadOntologyFromOntologyDocument(new File(tbox_file));		
		//build the graph
		boolean add_inferences = true;
		IRI ontology_id = IRI.create("http://theontology_iri_here");
		QRunner q = new QRunner(tbox, abox, ontology_id, add_inferences);
		//ask it questions
		boolean c = q.isConsistent();
		System.out.println("Is it consistent? "+c);
		//how big is it?
		int n = q.nTriples();
		System.out.println("N triples "+n); 
		//how many inferred triples? (assuming inference on)
		if(add_inferences) {
			System.out.println("inferred "+(q.wm.facts().size()-q.wm.asserted().size()));
			System.out.println("All "+q.wm.facts().size());
			q.printFactsExplanations();
		}
//134 with ro merged, minimal_lego
	}


}
