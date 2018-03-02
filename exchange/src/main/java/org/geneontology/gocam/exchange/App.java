package org.geneontology.gocam.exchange;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.io.IOUtils;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.query.QuerySolution;
import org.apache.jena.query.ResultSet;
import org.apache.jena.rdf.model.Literal;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.reasoner.rulesys.Rule;
import org.apache.jena.vocabulary.OWL;
import org.apache.jena.vocabulary.RDF;
import org.geneontology.jena.OWLtoRules;
import org.geneontology.jena.SesameJena;
import org.geneontology.rules.engine.Explanation;
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
	
	public static void main( String[] args ) throws OWLOntologyCreationException, OWLOntologyStorageException {
		//Test reading, reasoning, query
		//read a gocam
		GoCAM go_cam = new GoCAM("test ontology title", "contibutor", null, "provider", false);
		String unreasonable = "src/main/resources/org/geneontology/gocam/exchange/Unreasonable.ttl";
		String in = "/Users/bgood/Desktop/test/Wnt_example_cam-WNT_mediated_activation_of_DVL.ttl";
		go_cam.readGoCAM(in);		
		//read in a tbox for it (the go-lego import everything ontology)
		IRI ontology_id = IRI.create("http://theontology_iri_here");
		OWLOntology abox = go_cam.go_cam_ont;
		String minimal_lego = "/Users/bgood/minerva/minerva-server/src/main/resources/go-lego-trimmed.owl";
		String noneo_lego = "/Users/bgood/minerva/minerva-server/src/main/resources/go-lego-noneo.owl";
		String maximal_lego = "/Users/bgood/minerva/minerva-server/src/main/resources/go-lego-full.owl";		
		String tbox_file = minimal_lego;
		OWLOntologyManager tman = OWLManager.createOWLOntologyManager();
		OWLOntology tbox = tman.loadOntologyFromOntologyDocument(new File(tbox_file));		
		//build the graph including all inferences
		QRunner q = new QRunner(tbox, abox, ontology_id);
		System.out.println(q.wm.asserted().size()+" asserted");
		System.out.println(q.wm.facts().size()+" total facts");
		//ask it questions
		boolean c = q.isConsistent();
		System.out.println("Is consistent? "+c);
	}
	
	
}
