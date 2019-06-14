/**
 * 
 */
package org.geneontology.gocam.exchange;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

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
import org.semanticweb.owlapi.model.OWLAnnotation;
import org.semanticweb.owlapi.model.OWLAnnotationAssertionAxiom;
import org.semanticweb.owlapi.model.OWLAnnotationProperty;
import org.semanticweb.owlapi.model.OWLAxiom;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLClassAssertionAxiom;
import org.semanticweb.owlapi.model.OWLDataFactory;
import org.semanticweb.owlapi.model.OWLNamedIndividual;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyCreationException;
import org.semanticweb.owlapi.model.OWLOntologyManager;
import org.semanticweb.owlapi.model.OWLOntologyStorageException;
import org.semanticweb.owlapi.model.parameters.Imports;

import scala.collection.JavaConverters;

/**
 * Wrapper around Arachne from @author balhoff 
 * @author bgood
 *
 */
public class ArachneAccessor {

	Collection<OWLOntology> tbox_ontologies;
	RuleEngine ruleEngine;
	Set<org.apache.jena.reasoner.rulesys.Rule> jena_rules;
	public final Set<String> upper;
	public final OWLAnnotationProperty biolink_category;
	/**
	 * Arachne needs a tbox (defined classes from ontology) to get started.
	 */
	public ArachneAccessor(Collection<OWLOntology> tbox) {
		tbox_ontologies = tbox;
		ruleEngine = initializeRuleEngine(tbox_ontologies);
		/**
		 * BioLink
		 *standardize on https://w3id.org/biolink/vocab/category 
			https://w3id.org/biolink/vocab/MolecularActivity
			https://w3id.org/biolink/vocab/BiologicalProcess
			https://w3id.org/biolink/vocab/CellularComponent
		 */
		OWLDataFactory df = OWLManager.getOWLDataFactory();
		biolink_category = df.getOWLAnnotationProperty(IRI.create("https://w3id.org/biolink/vocab/category"));
		upper = new HashSet<String>();
		String obo = "http://purl.obolibrary.org/obo/";
		upper.add(obo+"GO_0003674"); //MF
		upper.add(obo+"GO_0008150"); //BP
		upper.add(obo+"GO_0005575"); //CC
		upper.add(obo+"CL_0000000"); //Cell
		upper.add(obo+"UBERON_0001062"); //anatomical entity
		upper.add(obo+"GO_0032991"); //protein-containing complex
		upper.add(obo+"CHEBI_23367"); //molecular entity
		//CHEBI_23367 subsumes all 'gene products' in neo as all as all chemical entities
		upper.add(obo+"CHEBI_33695"); //gene 
		upper.add(obo+"CHEBI_36080"); //protein
		upper.add(obo+"CHEBI_24431"); //chemical
		upper.add(obo+"ECO_0000000"); //evidence
	}


	/**
	 * Takes the tbox ontology an converts it into a set of Jena Rules.
	 * These rules are how inference is accomplished
	 * @param tbox
	 * @return
	 */
	RuleEngine initializeRuleEngine(Collection<OWLOntology> tbox) {
		Set<Rule> rules = new HashSet<Rule>();
		for(OWLOntology o : tbox) {
			rules.addAll(JavaConverters.setAsJavaSetConverter(OWLtoRules.translate(o, Imports.INCLUDED, true, true, true, true)).asJava());
			//indirect rules add statements like this ?pr <http://arachne.geneontology.org/indirect_type> ?pr_type
			//when an inferred type is added to link an instance to a superclass of one of its direct types
			rules.addAll(JavaConverters.setAsJavaSetConverter(OWLtoRules.indirectRules(o)).asJava());
		}
		jena_rules = rules;
		return new RuleEngine(Bridge.rulesFromJena(JavaConverters.asScalaSetConverter(rules).asScala()), true);
	}

	/**
	 * Return a RuleEngine encapsulating both whatever rules exist now and whatever rules exist in the provided ontology
	 * @param tbox
	 * @return
	 */
	RuleEngine makeExpandedRuleSet(OWLOntology newtbox) {
		Set<Rule> rules = new HashSet<Rule>();
		rules.addAll(JavaConverters.setAsJavaSetConverter(OWLtoRules.translate(newtbox, Imports.INCLUDED, true, true, true, true)).asJava());
		//indirect rules add statements like this ?pr <http://arachne.geneontology.org/indirect_type> ?pr_type
		//when an inferred type is added to link an instance to a superclass of one of its direct types
		rules.addAll(JavaConverters.setAsJavaSetConverter(OWLtoRules.indirectRules(newtbox)).asJava());
		//add to existing rule set
		rules.addAll(jena_rules);
		//make a new rule engine 
		//TODO this is on the slow side..
		RuleEngine rulen= new RuleEngine(Bridge.rulesFromJena(JavaConverters.asScalaSetConverter(rules).asScala()), true);
		return rulen;
	}


	/**
	 * Return an Arachne working memory model including the provided abox, additional inferred edges, and optionally the
	 * triples representing the property definitions (rbox) and class definitions of the tbox 
	 * @param abox_ontology
	 * @param add_property_definitions
	 * @param add_class_definitions
	 * @return
	 */
	public WorkingMemory createInferredModel(OWLOntology abox_ontology, boolean add_property_definitions, boolean add_class_definitions) {
		long mark = System.currentTimeMillis();
		Set<Statement> statements = JavaConverters.setAsJavaSetConverter(SesameJena.ontologyAsTriples(abox_ontology)).asJava();		
		System.out.println("step 1 "+(System.currentTimeMillis()-mark));
		mark = System.currentTimeMillis();
		Set<Triple> triples = statements.stream().map(s -> Bridge.tripleFromJena(s.asTriple())).collect(Collectors.toSet());
		System.out.println("step 2 "+(System.currentTimeMillis()-mark));
		mark = System.currentTimeMillis();
		try {
			if(add_property_definitions) {
				for(OWLOntology tbox_ontology : this.tbox_ontologies) {
					OWLOntology propOntology = OWLManager.createOWLOntologyManager().createOntology(tbox_ontology.getRBoxAxioms(Imports.EXCLUDED));
					Set<Statement> propStatements = JavaConverters.setAsJavaSetConverter(SesameJena.ontologyAsTriples(propOntology)).asJava();
					triples.addAll(propStatements.stream().map(s -> Bridge.tripleFromJena(s.asTriple())).collect(Collectors.toSet()));
				}
				System.out.println("step 3 "+(System.currentTimeMillis()-mark));
				mark = System.currentTimeMillis();
			}
			if(add_class_definitions) {
				for(OWLOntology tbox_ontology : this.tbox_ontologies) {
					OWLOntology tboxOntology = OWLManager.createOWLOntologyManager().createOntology(tbox_ontology.getTBoxAxioms(Imports.EXCLUDED));
					Set<Statement> tboxStatements = JavaConverters.setAsJavaSetConverter(SesameJena.ontologyAsTriples(tboxOntology)).asJava();
					triples.addAll(tboxStatements.stream().map(s -> Bridge.tripleFromJena(s.asTriple())).collect(Collectors.toSet()));
				}
				System.out.println("step 4 "+(System.currentTimeMillis()-mark));
				mark = System.currentTimeMillis();
			}
		} catch (OWLOntologyCreationException e) {
			System.out.println("Couldn't add rbox or tbox statements to triples.");
			System.out.println(e);
		}
		//	System.out.println("triples before reasoning: "+triples.size());
		WorkingMemory wm = ruleEngine.processTriples(JavaConverters.asScalaSetConverter(triples).asScala());
		System.out.println("step 5 "+(System.currentTimeMillis()-mark));
		mark = System.currentTimeMillis();
		//	System.out.println("triples after reasoning: "+wm.facts().size());
		return wm; 
	}

	void printFactsExplanations(WorkingMemory wm) {
		int n = 0;
		scala.collection.Iterator<Triple> i = wm.facts().iterator();		
		while(i.hasNext()) {	
			Triple fact = i.next();
			if(!wm.asserted().contains(fact)) {
				n++;
				System.out.println(n+"\t"+fact.o().toString());
				scala.collection.immutable.Set<Explanation> e = wm.explain(fact);
				System.out.println("\t\t"+e);
			}
		}
		return;
	}
	//return a list of inferred instance classifications
	public static Map<String, Set<String>> getInferredTypes(WorkingMemory wm, boolean remove_indirect_types){
		Map<String, Set<String>> inferred = new HashMap<String, Set<String>>();		
		Map<String, Set<String>> indirect = new HashMap<String, Set<String>>();
		scala.collection.Iterator<Triple> triples = wm.facts().toList().iterator();
		while(triples.hasNext()) {				
			Triple triple = triples.next();
			if(wm.asserted().contains(triple)) {
				continue;
			}else { //<http://arachne.geneontology.org/indirect_type>
				if(triple.p().toString().equals("<http://www.w3.org/1999/02/22-rdf-syntax-ns#type>")) {
					Set<String> types = inferred.get(triple.s().toString());
					if(types==null) {
						types = new HashSet<String>();
					}
					types.add(triple.o().toString());
					inferred.put(triple.s().toString(), types);
				}else if(remove_indirect_types&&triple.p().toString().equals("<http://arachne.geneontology.org/indirect_type>")) {
					Set<String> types = indirect.get(triple.s().toString());
					if(types==null) {
						types = new HashSet<String>();
					}
					types.add(triple.o().toString());
					indirect.put(triple.s().toString(), types);					
				}
			}
		}
		if(remove_indirect_types) {
			for(String thing : indirect.keySet()) {
				Set<String> indirect_types = indirect.get(thing);
				Set<String> types = inferred.get(thing);
				if(types!=null) {
					types.removeAll(indirect_types);
					inferred.put(thing, types);
				}
			}
		}
		
		return inferred;
	}
	
	/**
	 * Given a folder of .ttl files representing aboxes (OWL Individual declarations), 
	 * use Arachne to apply the rules it extracted during its construction to add inferred edges to the Abox
	 * Save these expanded graphs into new files in output folder.
	 * @param input_folder
	 * @param output_folder
	 * @param add_property_definitions
	 * @param add_class_definitions
	 * @throws OWLOntologyCreationException
	 * @throws FileNotFoundException
	 */
	public void reasonAllInFolder(String input_folder, String output_folder, boolean add_property_definitions, boolean add_class_definitions) throws OWLOntologyCreationException, FileNotFoundException {
		OWLOntologyManager aman = OWLManager.createOWLOntologyManager();
		File dir = new File(input_folder);
		File[] directoryListing = dir.listFiles();
		if (directoryListing != null) {
			int n = 0;
			for (File abox_file : directoryListing) {
				if(abox_file.getName().endsWith(".ttl")) {
					n++;
					System.out.println("Adding inferences for "+abox_file.getName()+" "+n+" of "+directoryListing.length);
					OWLOntology abox = aman.loadOntologyFromOntologyDocument(abox_file);	
					WorkingMemory wm = createInferredModel(abox, add_property_definitions, add_class_definitions);
					//for convenient export of the wm model..
					Model jena = makeJenaModel(wm);
					String filename = output_folder+"/expanded_"+abox_file.getName();
					FileOutputStream o = new FileOutputStream(filename);
					jena.write(o, "TURTLE");
					jena.close();
				}else {
					System.out.println("Skipping: "+abox_file.getName());
				}
			}
		}
		return;
	}

	public void categorizeInstanceNodesInFolder(String input_folder, String output_folder) throws OWLOntologyCreationException, FileNotFoundException, OWLOntologyStorageException {
		OWLOntologyManager aman = OWLManager.createOWLOntologyManager();
		
		File dir = new File(input_folder);
		File[] directoryListing = dir.listFiles();
		if (directoryListing != null) {
			int n = 0;
			for (File abox_file : directoryListing) {
				if(abox_file.getName().endsWith(".ttl")) {
					n++;
					System.out.println("Adding Types for "+abox_file.getName()+" "+n+" of "+directoryListing.length);
					OWLOntology abox = aman.loadOntologyFromOntologyDocument(abox_file);	
					abox = addCategoryTags(abox, aman);
					String filename = output_folder+"/typed_"+abox_file.getName();
					Helper.writeOntology(filename, abox);
				}else {
					System.out.println("Skipping: "+abox_file.getName());
				}
			}
		}
		return;
	}
	
	public OWLOntology addCategoryTags(OWLOntology abox, OWLOntologyManager ontman) throws OWLOntologyCreationException {			
		OWLDataFactory df = OWLManager.getOWLDataFactory();
		boolean add_property_definitions = false; boolean add_class_definitions = false;
		WorkingMemory wm = createInferredModel(abox, add_property_definitions, add_class_definitions);
		scala.collection.Iterator<Triple> triples = wm.facts().toList().iterator();
		while(triples.hasNext()) {				
			Triple triple = triples.next();
			 //<http://arachne.geneontology.org/indirect_type>
			if(triple.p().toString().equals("<http://www.w3.org/1999/02/22-rdf-syntax-ns#type>")) {
				String type = deArrow(triple.o().toString());
				if(upper.contains(type)) {
					String subject_uri = deArrow(triple.s().toString());
					IRI subject_iri = IRI.create(subject_uri);
					IRI type_iri = IRI.create(type);
					//if we went normal standards route
					//OWLNamedIndividual subject = df.getOWLNamedIndividual(subject_iri);
					//OWLClass type_class = df.getOWLClass(type_iri);
					//OWLClassAssertionAxiom isa = df.getOWLClassAssertionAxiom(type_class, subject);
					//ontman.addAxiom(abox, isa);
					//biolink 
					OWLAnnotation cat_anno = df.getOWLAnnotation(biolink_category, type_iri);
					OWLAnnotationAssertionAxiom cat = df.getOWLAnnotationAssertionAxiom(subject_iri, cat_anno);
					ontman.addAxiom(abox, cat);
				}
			}
		}
		return abox;
	}
	
	public String deArrow(String s) {
		return s.replaceAll("<", "").replaceAll(">", "");
	}
	public String addArrow(String s) {
		return "<"+s+">";
	}

	
	Model makeJenaModel(WorkingMemory wm) {
		Model model = ModelFactory.createDefaultModel();
		model.add(JavaConverters.setAsJavaSetConverter(wm.facts()).asJava().stream()
				.map(t -> model.asStatement(Bridge.jenaFromTriple(t))).collect(Collectors.toList()));
		return model;
	}

	/**
	 * Given a directory of ttl files, merge them into one OWLOntology
	 * @param input_folder
	 * @return
	 * @throws OWLOntologyCreationException
	 */
	public static OWLOntology makeOneOntologyFromDirectory(String input_folder) throws OWLOntologyCreationException {
		OWLOntologyManager aman = OWLManager.createOWLOntologyManager();
		OWLOntology metaont = aman.createOntology();
		File dir = new File(input_folder);
		File[] directoryListing = dir.listFiles();
		if (directoryListing != null) {
			for (File abox_file : directoryListing) {
				if(abox_file.getName().endsWith(".ttl")) {
					OWLOntology box = aman.loadOntologyFromOntologyDocument(abox_file);	
					Set<OWLAxiom> axioms = box.getAxioms();
					for(OWLAxiom axiom : axioms) {
						aman.addAxiom(metaont, axiom);
					}
					System.out.println("Added "+abox_file.getName()+" Abox axioms now count: "+metaont.getAxioms().size());
				}else {
					System.out.println("Skipping: "+abox_file.getName());
				}

			}
		}
		return metaont;
	}

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		// TODO Auto-generated method stub

	}

}
