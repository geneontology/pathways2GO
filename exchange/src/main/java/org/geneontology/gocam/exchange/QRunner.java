/**
 * 
 */
package org.geneontology.gocam.exchange;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.io.IOUtils;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.query.QuerySolution;
import org.apache.jena.query.ResultSet;
import org.apache.jena.rdf.model.Literal;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.reasoner.rulesys.Rule;
import org.geneontology.jena.OWLtoRules;
import org.geneontology.jena.SesameJena;
import org.geneontology.rules.engine.Explanation;
import org.geneontology.rules.engine.RuleEngine;
import org.geneontology.rules.engine.Triple;
import org.geneontology.rules.engine.WorkingMemory;
import org.geneontology.rules.util.Bridge;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyCreationException;
import org.semanticweb.owlapi.model.parameters.Imports;

import scala.collection.JavaConverters;

/**
 * @author bgood
 *
 */
public class QRunner {
	private RuleEngine ruleEngine;
	private final Model jena;
	WorkingMemory wm;
	boolean inference_on;
	/**
	 * 
	 */
	public QRunner(OWLOntology tbox, OWLOntology abox, IRI ontology_iri, boolean add_inferences) {
		inference_on = add_inferences;
		if(inference_on) {
			ruleEngine = initializeRuleEngine(tbox);
			wm = createInferredModel(tbox, abox, ontology_iri);
			jena = makeJenaModel(wm);
		}else {
			jena = makeJenaModel(abox, tbox);
		}
	}

	Model makeJenaModel(OWLOntology abox, OWLOntology tbox) {
		Model model = ModelFactory.createDefaultModel();
		Set<Statement> a_statements = JavaConverters.setAsJavaSetConverter(SesameJena.ontologyAsTriples(abox)).asJava();		
		for(Statement s : a_statements) {
			model.add(s);
		}
		if(tbox!=null) {
			Set<Statement> t_statements = JavaConverters.setAsJavaSetConverter(SesameJena.ontologyAsTriples(tbox)).asJava();
			for(Statement s : t_statements) {
				model.add(s);
			}
		}	
		return model;
	}

	int nTriples() {
		int n = 0;
		String q = null;
		try {
			q = IOUtils.toString(App.class.getResourceAsStream("triple_count.rq"), StandardCharsets.UTF_8);
		} catch (IOException e) {
			System.out.println("Could not load SPARQL query from jar \n"+e);
		}
		QueryExecution qe = QueryExecutionFactory.create(q, jena);
		ResultSet results = qe.execSelect();
		if (results.hasNext()) {
			QuerySolution qs = results.next();
			Literal s = qs.getLiteral("triples");
			System.out.println(s);
			n = s.getInt();
		}
		qe.close();
		return n;
	}
	
	boolean isConsistent() {
		boolean consistent = true;
		String q = null;
		try {
			q = IOUtils.toString(App.class.getResourceAsStream("consistency_check.rq"), StandardCharsets.UTF_8);
		} catch (IOException e) {
			System.out.println("Could not load SPARQL query from jar \n"+e);
		}
		QueryExecution qe = QueryExecutionFactory.create(q, jena);
		ResultSet results = qe.execSelect();
		if (results.hasNext()) {
			QuerySolution qs = results.next();
			Literal s = qs.getLiteral("triples");
			System.out.println(s);
			if(s.getInt()==0) {
				consistent = true;
			}else{
				consistent = false;
			}
		}
		qe.close();
		return consistent;
	}

	Model makeJenaModel(WorkingMemory wm) {
		Model model = ModelFactory.createDefaultModel();
		model.add(JavaConverters.setAsJavaSetConverter(wm.facts()).asJava().stream()
				.map(t -> model.asStatement(Bridge.jenaFromTriple(t))).collect(Collectors.toList()));
		return model;
	}

	RuleEngine initializeRuleEngine(OWLOntology ontology) {
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
	WorkingMemory createInferredModel(OWLOntology tbox_ontology, OWLOntology abox_ontology, IRI ontology_id) {
		//Set<Statement> statements = JavaConverters.setAsJavaSetConverter(SesameJena.ontologyAsTriples(getModelAbox(modelId))).asJava();
		Set<Statement> statements = JavaConverters.setAsJavaSetConverter(SesameJena.ontologyAsTriples(abox_ontology)).asJava();		
		Set<Triple> triples = statements.stream().map(s -> Bridge.tripleFromJena(s.asTriple())).collect(Collectors.toSet());
		try {
			// Using model's ontology IRI so that a spurious different ontology declaration triple isn't added
			//OWLOntology schemaOntology = OWLManager.createOWLOntologyManager().createOntology(ontology.getRBoxAxioms(Imports.INCLUDED), ontology_id);
			OWLOntology schemaOntology = OWLManager.createOWLOntologyManager().createOntology(tbox_ontology.getRBoxAxioms(Imports.INCLUDED), ontology_id);
			Set<Statement> schemaStatements = JavaConverters.setAsJavaSetConverter(SesameJena.ontologyAsTriples(schemaOntology)).asJava();
			triples.addAll(schemaStatements.stream().map(s -> Bridge.tripleFromJena(s.asTriple())).collect(Collectors.toSet()));
		} catch (OWLOntologyCreationException e) {
			System.out.println("Couldn't add rbox statements to data model.");
			System.out.println(e);
		}
		wm = ruleEngine.processTriples(JavaConverters.asScalaSetConverter(triples).asScala());
		return wm; 
	}

	void printFactsExplanations() {
		int n = 0;
		scala.collection.Iterator<Triple> i = wm.facts().iterator();		
		while(i.hasNext()) {	
			Triple fact = i.next();
			if(!wm.asserted().contains(fact)) {
				n++;
				System.out.println(n+"\t"+fact.o().toString());
				//				if(!fact.o().toString().equals("<http://www.biopax.org/release/biopax-level3.owl#Protein>")) {
				scala.collection.immutable.Set<Explanation> e = wm.explain(fact);
				System.out.println("\t\t"+e);
				//				}
				//<http://purl.obolibrary.org/obo/BFO_0000002>"\n" + 
			}
		}
		return;
	}
}
