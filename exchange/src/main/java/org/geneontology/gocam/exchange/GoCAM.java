/**
 * 
 */
package org.geneontology.gocam.exchange;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.jena.rdf.model.Statement;
import org.biopax.paxtools.model.level3.PublicationXref;
import org.biopax.paxtools.model.level3.Xref;
import org.geneontology.gocam.exchange.QRunner.InferredEnabler;
import org.geneontology.gocam.exchange.QRunner.InferredRegulator;
import org.geneontology.jena.SesameJena;
import org.geneontology.rules.engine.Explanation;
import org.geneontology.rules.engine.RuleEngine;
import org.geneontology.rules.engine.Triple;
import org.geneontology.rules.engine.WorkingMemory;
import org.geneontology.rules.util.Bridge;
import org.openrdf.repository.RepositoryException;
import org.openrdf.rio.RDFHandlerException;
import org.openrdf.rio.RDFParseException;
//import org.coode.owlapi.turtle.TurtleOntologyFormat;
import org.semanticweb.owlapi.formats.TurtleDocumentFormat;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.io.FileDocumentTarget;
import org.semanticweb.owlapi.model.AddAxiom;
import org.semanticweb.owlapi.model.AddImport;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLAnnotation;
import org.semanticweb.owlapi.model.OWLAnnotationAssertionAxiom;
import org.semanticweb.owlapi.model.OWLAnnotationProperty;
import org.semanticweb.owlapi.model.OWLAnnotationSubject;
import org.semanticweb.owlapi.model.OWLAnnotationValue;
import org.semanticweb.owlapi.model.OWLAxiom;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLClassAssertionAxiom;
import org.semanticweb.owlapi.model.OWLClassExpression;
import org.semanticweb.owlapi.model.OWLDataFactory;
import org.semanticweb.owlapi.model.OWLDocumentFormat;
import org.semanticweb.owlapi.model.OWLEntity;
import org.semanticweb.owlapi.model.OWLEquivalentClassesAxiom;
import org.semanticweb.owlapi.model.OWLImportsDeclaration;
import org.semanticweb.owlapi.model.OWLIndividual;
import org.semanticweb.owlapi.model.OWLLiteral;
import org.semanticweb.owlapi.model.OWLNamedIndividual;
import org.semanticweb.owlapi.model.OWLObjectProperty;
import org.semanticweb.owlapi.model.OWLObjectPropertyAssertionAxiom;
import org.semanticweb.owlapi.model.OWLObjectPropertyAxiom;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyCreationException;
import org.semanticweb.owlapi.model.OWLOntologyManager;
import org.semanticweb.owlapi.model.OWLOntologyStorageException;
import org.semanticweb.owlapi.model.OWLSubClassOfAxiom;
import org.semanticweb.owlapi.model.RemoveAxiom;
import org.semanticweb.owlapi.search.EntitySearcher;
import org.semanticweb.owlapi.util.OWLEntityRemover;
import org.semanticweb.owlapi.util.OWLOntologyWalker;
import org.semanticweb.owlapi.vocab.OWLRDFVocabulary;

import scala.collection.JavaConverters;

/**
 * @author bgood
 *
 */
public class GoCAM {
	public static final String base_iri = "http://model.geneontology.org/";
	public static final IRI go_lego_iri = IRI.create("http://purl.obolibrary.org/obo/go/extensions/go-lego.owl");
	public static final IRI obo_iri = IRI.create("http://purl.obolibrary.org/obo/");
	public static final IRI uniprot_iri = IRI.create("http://identifiers.org/uniprot/");
	public static IRI base_ont_iri;
	public static OWLAnnotationProperty title_prop, contributor_prop, date_prop, skos_exact_match,  
	state_prop, evidence_prop, provided_by_prop, x_prop, y_prop, rdfs_label, rdfs_comment, source_prop;
	public static OWLObjectProperty part_of, has_part, has_input, has_output, 
	provides_direct_input_for, directly_inhibits, directly_activates, occurs_in, enabled_by, enables, regulated_by, located_in,
	directly_positively_regulated_by, directly_negatively_regulated_by, involved_in_regulation_of, involved_in_negative_regulation_of, involved_in_positive_regulation_of,
	directly_negatively_regulates, directly_positively_regulates, has_role;
	public static OWLClass 
	bp_class, continuant_class, process_class, go_complex, molecular_function, 
	eco_imported, eco_imported_auto, eco_inferred_auto, 
	chebi_protein, chebi_gene, chemical_entity, chemical_role;
	OWLOntology go_cam_ont;
	OWLDataFactory df;
	OWLOntologyManager ontman;
	String base_contributor, base_date, base_provider;
	//for inference 
	QRunner qrunner;
	//for storage
	String path2bgjournal;
	Blazer blazegraphdb;

	/**
	 * @throws OWLOntologyCreationException 
	 * 
	 */

	public GoCAM(IRI ont_iri, String gocam_title, String contributor, String date, String provider, boolean add_lego_import) throws OWLOntologyCreationException {
		base_contributor = contributor;
		base_date = getDate(date);
		base_provider = provider;
		base_ont_iri = ont_iri;
		ontman = OWLManager.createOWLOntologyManager();				
		go_cam_ont = ontman.createOntology(ont_iri);
		df = OWLManager.getOWLDataFactory();
		
//TODO basically never going to do this, maybe take it out..
		if(add_lego_import) {
			String lego_iri = "http://purl.obolibrary.org/obo/go/extensions/go-lego.owl";
			OWLImportsDeclaration legoImportDeclaration = df.getOWLImportsDeclaration(IRI.create(lego_iri));
			ontman.applyChange(new AddImport(go_cam_ont, legoImportDeclaration));
		}
		/*
 <http://model.geneontology.org/5a5fd3de00000008> rdf:type owl:Ontology ;
                                                  owl:versionIRI <http://model.geneontology.org/5a5fd3de00000008> ;
                                                  owl:imports <http://purl.obolibrary.org/obo/go/extensions/go-lego.owl> ;
                                                  <http://geneontology.org/lego/modelstate> "development"^^xsd:string ;
                                                  <http://purl.org/dc/elements/1.1/contributor> "http://orcid.org/0000-0002-2874-6934"^^xsd:string ;
                                                  <http://purl.org/dc/elements/1.1/title> "Tre test"^^xsd:string ;
                                                  <http://purl.org/dc/elements/1.1/date> "2018-01-18"^^xsd:string .		
		 */

		//Annotation properties for metadata and evidence
		title_prop = df.getOWLAnnotationProperty(IRI.create("http://purl.org/dc/elements/1.1/title"));
		contributor_prop = df.getOWLAnnotationProperty(IRI.create("http://purl.org/dc/elements/1.1/contributor"));
		date_prop = df.getOWLAnnotationProperty(IRI.create("http://purl.org/dc/elements/1.1/date"));
		source_prop = df.getOWLAnnotationProperty(IRI.create("http://purl.org/dc/elements/1.1/source"));
		state_prop = df.getOWLAnnotationProperty(IRI.create("http://geneontology.org/lego/modelstate"));
		evidence_prop = df.getOWLAnnotationProperty(IRI.create("http://geneontology.org/lego/evidence"));
		provided_by_prop = df.getOWLAnnotationProperty(IRI.create("http://purl.org/pav/providedBy"));
		x_prop = df.getOWLAnnotationProperty(IRI.create("http://geneontology.org/lego/hint/layout/x"));
		y_prop = df.getOWLAnnotationProperty(IRI.create("http://geneontology.org/lego/hint/layout/y"));
		rdfs_label = df.getOWLAnnotationProperty(OWLRDFVocabulary.RDFS_LABEL.getIRI());
		rdfs_comment = df.getOWLAnnotationProperty(OWLRDFVocabulary.RDFS_COMMENT.getIRI());
		skos_exact_match = df.getOWLAnnotationProperty(IRI.create("http://www.w3.org/2004/02/skos/core#exactMatch"));

		//Will add classes and relations as we need them now. 
		//TODO Work on using imports later to ensure we don't produce incorrect ids..
		//classes	
		
		chemical_role =df.getOWLClass(IRI.create(obo_iri+"CHEBI_50906"));
		addLabel(chemical_role, "chemical role");
		//biological process
		bp_class = df.getOWLClass(IRI.create(obo_iri + "GO_0008150")); 
		addLabel(bp_class, "Biological Process");
		//molecular function GO:0003674
		molecular_function = df.getOWLClass(IRI.create(obo_iri + "GO_0003674")); 
		addLabel(molecular_function, "Molecular Function");
		//continuant 
		continuant_class = df.getOWLClass(IRI.create(obo_iri + "BFO_0000002")); 
		addLabel(continuant_class, "Continuant");
		//occurent
		process_class =  df.getOWLClass(IRI.create(obo_iri + "BFO_0000015")); 
		addLabel(process_class, "Process");		
		//complex GO_0032991
		go_complex = df.getOWLClass(IRI.create(obo_iri + "GO_0032991")); 
		addLabel(go_complex, "Macromolecular Complex");		
		//http://purl.obolibrary.org/obo/ECO_0000313
		//"A type of imported information that is used in an automatic assertion."
		eco_imported_auto = df.getOWLClass(IRI.create(obo_iri + "ECO_0000313")); 
		//"A type of evidence that is based on work performed by a person or group prior to a use by a different person or group."
		eco_imported = df.getOWLClass(IRI.create(obo_iri + "ECO_0000311")); 
		//ECO_0000363 "A type of evidence based on computational logical inference that is used in automatic assertion."
		eco_inferred_auto = df.getOWLClass(IRI.create(obo_iri + "ECO_0000363")); 		
		//complex
		OWLSubClassOfAxiom comp = df.getOWLSubClassOfAxiom(go_complex, continuant_class);
		ontman.addAxiom(go_cam_ont, comp);
		//proteins and genes as they are in neo
		chebi_protein = df.getOWLClass(IRI.create(obo_iri + "CHEBI_36080"));
		addLabel(chebi_protein, "chebi protein");
		chebi_gene = df.getOWLClass(IRI.create(obo_iri + "CHEBI_33695"));
		addLabel(chebi_gene, "chebi gene"); 

		chemical_entity =df.getOWLClass(IRI.create(obo_iri+"CHEBI_24431"));
		addLabel(chemical_entity, "chemical entity");
		
		//part of
		part_of = df.getOWLObjectProperty(IRI.create(obo_iri + "BFO_0000050"));
		addLabel(part_of, "part of"); 
		//has part
		has_part = df.getOWLObjectProperty(IRI.create(obo_iri + "BFO_0000051"));
		addLabel(has_part, "has part");
		//has input 
		has_input = df.getOWLObjectProperty(IRI.create(obo_iri + "RO_0002233"));
		addLabel(has_input, "has input");
		//has output 
		has_output = df.getOWLObjectProperty(IRI.create(obo_iri + "RO_0002234"));
		addLabel(has_output, "has output");
		//directly provides input for (process to process)
		provides_direct_input_for = df.getOWLObjectProperty(IRI.create(obo_iri + "RO_0002413"));
		addLabel(provides_direct_input_for, "directly provides input for (process to process)");
		//RO_0002408 directly inhibits (process to process)
		directly_inhibits = df.getOWLObjectProperty(IRI.create(obo_iri + "RO_0002408"));
		addLabel(directly_inhibits, "directly inhibits (process to process)");
		//RO_0002406 directly activates (process to process)
		directly_activates = df.getOWLObjectProperty(IRI.create(obo_iri + "RO_0002406"));
		addLabel(directly_activates, "directly activates (process to process)");
		//BFO_0000066 occurs in (note that it can only be used for occurents in occurents)
		occurs_in = df.getOWLObjectProperty(IRI.create(obo_iri + "BFO_0000066"));
		addLabel(occurs_in, "occurs in");
		//RO_0001025
		located_in = df.getOWLObjectProperty(IRI.create(obo_iri + "RO_0001025"));
		addLabel(located_in, "located in");		
		//RO_0002333 enabled by
		enabled_by = df.getOWLObjectProperty(IRI.create(obo_iri + "RO_0002333"));
		addLabel(enabled_by, "enabled by");
		//RO_0002327
		enables = df.getOWLObjectProperty(IRI.create(obo_iri + "RO_0002327"));
		addLabel(enables, "enables");
		//RO_0002334 regulated by (processual) 
		regulated_by = df.getOWLObjectProperty(IRI.create(obo_iri + "RO_0002334"));
		addLabel(regulated_by, "regulated by");
		//RO_0002335 negatively regulated by
		//RO_0002336 positively regulated by
		//directly negatively regulated by RO_0002023
		//directly positively regulated by RO_0002024
		directly_negatively_regulated_by = df.getOWLObjectProperty(IRI.create(obo_iri + "RO_0002023"));
		addLabel(directly_negatively_regulated_by, "directly negatively regulated by");
		//RO_0002630
		directly_negatively_regulates = df.getOWLObjectProperty(IRI.create(obo_iri + "RO_0002630"));
		addLabel(directly_negatively_regulates, "directly negatively regulates");
		
		directly_positively_regulated_by = df.getOWLObjectProperty(IRI.create(obo_iri + "RO_0002024"));
		addLabel(directly_positively_regulated_by, "directly positively regulated by");
		
		directly_positively_regulates = df.getOWLObjectProperty(IRI.create(obo_iri + "RO_0002629"));
		addLabel(directly_positively_regulates, "directly positively regulates");
		
		//RO_0002430 involved_in_negative_regulation_of
		//RO_0002429 involved_in_positive_regulation_of
		involved_in_negative_regulation_of = df.getOWLObjectProperty(IRI.create(obo_iri + "RO_0002430"));
		addLabel(involved_in_negative_regulation_of, "involved in negative regulation_of");
		involved_in_positive_regulation_of = df.getOWLObjectProperty(IRI.create(obo_iri + "RO_0002429"));
		addLabel(involved_in_positive_regulation_of, "involved in positive regulation_of");

		involved_in_regulation_of = df.getOWLObjectProperty(IRI.create(obo_iri + "RO_0002428"));
		addLabel(involved_in_regulation_of, "involved in regulation of");
		
		//RO:0000087
		has_role = df.getOWLObjectProperty(IRI.create(obo_iri + "RO_0000087"));
		addLabel(has_role, "has role");
		
		//Annotate the ontology
		OWLAnnotation title_anno = df.getOWLAnnotation(title_prop, df.getOWLLiteral(gocam_title));
		OWLAxiom titleaxiom = df.getOWLAnnotationAssertionAxiom(ont_iri, title_anno);
		ontman.addAxiom(go_cam_ont, titleaxiom);

		OWLAnnotation contributor_anno = df.getOWLAnnotation(contributor_prop, df.getOWLLiteral(base_contributor));
		OWLAxiom contributoraxiom = df.getOWLAnnotationAssertionAxiom(ont_iri, contributor_anno);
		ontman.addAxiom(go_cam_ont, contributoraxiom);

		OWLAnnotation date_anno = df.getOWLAnnotation(date_prop, df.getOWLLiteral(base_date));
		OWLAxiom dateaxiom = df.getOWLAnnotationAssertionAxiom(ont_iri, date_anno);
		ontman.addAxiom(go_cam_ont, dateaxiom);

		OWLAnnotation state_anno = df.getOWLAnnotation(state_prop, df.getOWLLiteral("development"));
		OWLAxiom stateaxiom = df.getOWLAnnotationAssertionAxiom(ont_iri, state_anno);
		ontman.addAxiom(go_cam_ont, stateaxiom);

		//ontman.applyChanges();
	}

	Blazer initializeBlazeGraph(String journal) {
		path2bgjournal = journal;
		blazegraphdb = new Blazer(journal);
		return blazegraphdb;
	}



	public String getDate(String input_date) {
		String date = "";
		if(input_date==null) {
			Date now = new Date();
			SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
			date = sdf.format(now);			
		}else {
			date = input_date;
		}
		return date;
	}

	OWLNamedIndividual makeAnnotatedIndividual(String iri_string) {
		IRI iri = IRI.create(iri_string);
		return makeAnnotatedIndividual(iri);
	}
	OWLNamedIndividual makeAnnotatedIndividual(IRI iri, String contributor_uri, String date, String provider_uri) {
		OWLNamedIndividual i = df.getOWLNamedIndividual(iri);		
		addBasicAnnotations2Individual(iri, contributor_uri, date, provider_uri);
		return i;
	}

	OWLNamedIndividual makeAnnotatedIndividual(IRI iri) {
		OWLNamedIndividual i = df.getOWLNamedIndividual(iri);		
		addBasicAnnotations2Individual(iri, this.base_contributor, this.base_date, this.base_provider);
		return i;
	}
	
	void addBasicAnnotations2Individual(IRI individual_iri, String contributor_uri, String date, String provider_uri) {
		addLiteralAnnotations2Individual(individual_iri, contributor_prop, contributor_uri);
		addLiteralAnnotations2Individual(individual_iri, date_prop, getDate(date));
		addLiteralAnnotations2Individual(individual_iri, provided_by_prop, provider_uri);
		return;
	}

	Set<OWLAnnotation> getDefaultAnnotations(){
		Set<OWLAnnotation> annos = new HashSet<OWLAnnotation>();
		annos.add(df.getOWLAnnotation(contributor_prop, df.getOWLLiteral(this.base_contributor)));
		annos.add(df.getOWLAnnotation(date_prop, df.getOWLLiteral(this.base_date)));
		annos.add(df.getOWLAnnotation(provided_by_prop, df.getOWLLiteral(this.base_provider)));
		return annos;
	}

	OWLAnnotation addEvidenceAnnotation(IRI individual_iri, IRI evidence_iri) {
		OWLAnnotation anno = df.getOWLAnnotation(GoCAM.evidence_prop, evidence_iri);
		OWLAxiom axiom = df.getOWLAnnotationAssertionAxiom(individual_iri, anno);
		ontman.addAxiom(go_cam_ont, axiom);
		//ontman.applyChanges();		
		return anno;
	}

	OWLAnnotation addLiteralAnnotations2Individual(IRI individual_iri, OWLAnnotationProperty prop, String value) {
		OWLAnnotation anno = df.getOWLAnnotation(prop, df.getOWLLiteral(value));
		OWLAxiom axiom = df.getOWLAnnotationAssertionAxiom(individual_iri, anno);
		ontman.addAxiom(go_cam_ont, axiom);
		//ontman.applyChanges();		
		return anno;
	}

	OWLAnnotation addLiteralAnnotations2Individual(IRI individual_iri, OWLAnnotationProperty prop, OWLLiteral value) {
		OWLAnnotation anno = df.getOWLAnnotation(prop, value);
		OWLAxiom axiom = df.getOWLAnnotationAssertionAxiom(individual_iri, anno);
		ontman.addAxiom(go_cam_ont, axiom);
		//ontman.applyChanges();		
		return anno;
	}

	OWLAnnotation addUriAnnotations2Individual(IRI individual_iri, OWLAnnotationProperty prop, IRI value) {
		OWLAnnotation anno = df.getOWLAnnotation(prop, value);
		OWLAxiom axiom = df.getOWLAnnotationAssertionAxiom(individual_iri, anno);
		ontman.addAxiom(go_cam_ont, axiom);
		//ontman.applyChanges();		
		return anno;
	}

	void addLabel(OWLEntity entity, String label) {
		if(label==null) {
			return;
		}		
		OWLLiteral lbl = df.getOWLLiteral(label);
		OWLAnnotation label_anno = df.getOWLAnnotation(rdfs_label, lbl);
		OWLAxiom labelaxiom = df.getOWLAnnotationAssertionAxiom(entity.getIRI(), label_anno);
		ontman.addAxiom(go_cam_ont, labelaxiom);
		//ontman.applyChanges();
		return;
	}

	/**
	 * Thanks https://stackoverflow.com/questions/20780425/using-owl-api-given-an-owlclass-how-can-i-get-rdfslabel-of-it/20784993#20784993
	 * ...
	 * @param cls
	 * @return
	 */
	Set<String> getLabels(OWLEntity e){
		Set<String> labels = new HashSet<String>();
		for(OWLAnnotationAssertionAxiom a : go_cam_ont.getAnnotationAssertionAxioms(e.getIRI())) {
			if(a.getProperty().isLabel()) {
				if(a.getValue() instanceof OWLLiteral) {
					OWLLiteral val = (OWLLiteral) a.getValue();
					labels.add(val.getLiteral());
				}
			}
		}
		return labels;
	}

	String getaLabel(OWLEntity e){
		Set<String> labels = getLabels(e);
		String label = "";
		if(labels!=null&&labels.size()>0) {
			for(String l : labels) {
				label = l;
				break;
			}
		}
		return label;
	}

	/**
	 //TODO maybe... COuld use the minerva pattern that follows or implement something that makes purls etc.
static String uniqueTop = Long.toHexString(Math.abs((System.currentTimeMillis()/1000)));
static final AtomicLong instanceCounter = new AtomicLong(0L);
final long counterValue = instanceCounter.getAndIncrement();
	 * @return
	 */
	public static IRI makeRandomIri() {
		String iri = base_iri+Math.random();
		return IRI.create(iri);
	}

	public static IRI makeGoCamifiedIRI(String uri) {
		String iri = base_iri+uri.hashCode();
		return IRI.create(iri);
	}
	/**
	 * Given a set of PubMed reference identifiers, the pieces of a triple, and an evidence class, create an evidence individual for each pmid, 
	 * create a corresponding OWLAnnotation entity, make the triple along with all the annotations as evidence.  
	 * @param source
	 * @param prop
	 * @param target
	 * @param ids
	 * @param evidence_class
	 * @param namespace_prefix (e.g. PMID)
	 */
	void addRefBackedObjectPropertyAssertion(OWLNamedIndividual source, OWLObjectProperty prop, OWLNamedIndividual target, 
			Set<String> ids, OWLClass evidence_class, String namespace_prefix, Set<OWLAnnotation> other_annotations) {
		OWLObjectPropertyAssertionAxiom add_prop_axiom = null;
		Set<OWLAnnotation> annos = new HashSet<OWLAnnotation>();
		if(other_annotations!=null) {
			annos.addAll(other_annotations);
		}
		annos.addAll(getDefaultAnnotations());//prepare the database annotations like pubmed ids 
		if(ids!=null) {			
			for(String id : ids) {
			//	IRI anno_iri = makeEntityHashIri(source.hashCode()+"_"+prop.hashCode()+"_"+target.hashCode()+"_"+namespace_prefix+"_"+id);
				IRI anno_iri = this.makeRandomIri();
				OWLNamedIndividual evidence = makeAnnotatedIndividual(anno_iri);					
				addTypeAssertion(evidence, evidence_class);
				addLiteralAnnotations2Individual(anno_iri, GoCAM.source_prop, namespace_prefix+":"+id);
				OWLAnnotation anno = df.getOWLAnnotation(GoCAM.evidence_prop, anno_iri);
				annos.add(anno);
			}
		}else {
			IRI anno_iri = IRI.create(GoCAM.base_iri+"_evidence_"+source.toStringID()+"_"+prop.toStringID()+"_"+target.toStringID()+"_"+evidence_class.toStringID());//makeEntityHashIri("evidence"+source.hashCode()+"_"+prop.hashCode()+"_"+target.hashCode()+"_"+evidence_class.hashCode());
			OWLNamedIndividual evidence = makeAnnotatedIndividual(anno_iri);					
			addTypeAssertion(evidence, evidence_class);
			OWLAnnotation anno = df.getOWLAnnotation(GoCAM.evidence_prop, anno_iri);
			annos.add(anno);
		}
		//check if this is an update or a create
		OWLObjectPropertyAssertionAxiom check = df.getOWLObjectPropertyAssertionAxiom(prop, source, target, annos);
		boolean annotated_axiom_exists = EntitySearcher.containsAxiom(check, go_cam_ont, false);
		if(annotated_axiom_exists) {
			return; //already have it
		}else{
			boolean axiom_exists = EntitySearcher.containsAxiomIgnoreAnnotations(check, go_cam_ont, false);
			//axiom exists with different annotations so merge them
			if(axiom_exists) {
				//it is here to handle situations where we might add a new axiom that is the same as an existing axiom but with new annotations..
				//rather than creating two, want to merge the annotations onto one.  
				OWLOntologyWalker walker = new OWLOntologyWalker(Collections.singleton(go_cam_ont));
				UpdateAnnotationsVisitor updater = new UpdateAnnotationsVisitor(walker, source.getIRI(), prop.getIRI(), target.getIRI());
				walker.walkStructure(updater); 
				if(updater.getAxioms()!=null&&updater.getAxioms().size()>0) {
					//Its an update.
					//need to remove the old one(s) but collect existing annotations.  
					ontman.removeAxioms(go_cam_ont, updater.getAxioms());
					if(updater.getAnnotations()!=null) {
						annos.addAll(updater.getAnnotations());
					}
				}
			}
//			else {
//				System.out.println("creating "+check);
//			}
			//now finally make the new one.
			add_prop_axiom = df.getOWLObjectPropertyAssertionAxiom(prop, source, target, annos);
			AddAxiom addAxiom = new AddAxiom(go_cam_ont, add_prop_axiom);
			ontman.applyChange(addAxiom);
		}
		
		return ;
	}




	void addObjectPropertyAssertion(OWLIndividual source, OWLObjectProperty prop, OWLIndividual target, Set<OWLAnnotation> annotations) {
		OWLObjectPropertyAssertionAxiom add_prop_axiom = null;
		if(annotations!=null&&annotations.size()>0) {
			add_prop_axiom = df.getOWLObjectPropertyAssertionAxiom(prop, source, target, annotations);
		}else {
			add_prop_axiom = df.getOWLObjectPropertyAssertionAxiom(prop, source, target);
		}
		AddAxiom addAxiom = new AddAxiom(go_cam_ont, add_prop_axiom);
		ontman.applyChange(addAxiom);
		return ;
	}

	void addSubclassAssertion(OWLClass child, OWLClass parent, Set<OWLAnnotation> annotations) {
		OWLSubClassOfAxiom tmp = null;
		if(annotations!=null&&annotations.size()>0) {
			tmp = df.getOWLSubClassOfAxiom(child, parent, annotations);
		}else {
			tmp = df.getOWLSubClassOfAxiom(child, parent);
		} 		
		ontman.addAxiom(go_cam_ont, tmp);
		//ontman.applyChanges();
	}

	/**
	 * Note that, for Noctua, no annotations are allowed on rdf:type edges.  
	 * @param individual
	 * @param type
	 */
	void addTypeAssertion(OWLNamedIndividual individual, OWLClass type) {
		OWLClassAssertionAxiom isa_xrefedbp = df.getOWLClassAssertionAxiom(type, individual);
		ontman.addAxiom(go_cam_ont, isa_xrefedbp);
		//ontman.applyChanges();		
	}

	String printLabels(OWLEntity i) {
		String labels = "";
		EntitySearcher.getAnnotationObjects(i, go_cam_ont, GoCAM.rdfs_label).
		forEach(label -> System.out.println(label));

		//    			getIndividuals(pathway_class, go_cam.go_cam_ont).
		//    				forEach(pathway -> EntitySearcher.getAnnotationObjects((OWLEntity) pathway, go_cam.go_cam_ont, GoCAM.rdfs_label).
		//    						forEach(System.out::println)
		//    						);
		return labels;
	}

	/**
	 * Sets up the inference rules from provided TBox
	 * @throws OWLOntologyCreationException
	 */
	QRunner initializeQRunnerForTboxInference(Set<String> tbox_files) throws OWLOntologyCreationException {
		System.out.println("initializeQRunnerForTboxInference()");
		OWLOntologyManager tman = OWLManager.createOWLOntologyManager();
		List<OWLOntology> tboxes = new ArrayList<OWLOntology>();
		for(String tbox_file : tbox_files) {
			OWLOntology tbox = tman.loadOntologyFromOntologyDocument(new File(tbox_file));	
			tboxes.add(tbox);
		}
		boolean add_inferences = true;
		boolean add_property_definitions = false; boolean add_class_definitions = false;
		qrunner = new QRunner(tboxes, go_cam_ont, add_inferences, add_property_definitions, add_class_definitions);
		return qrunner;
	}

	QRunner initializeQRunner(Collection<OWLOntology> tbox) throws OWLOntologyCreationException {
		System.out.println("initializeQRunner()");
		boolean add_inferences = true;
		boolean add_property_definitions = false; boolean add_class_definitions = false;
		qrunner = new QRunner(tbox, null, add_inferences, add_property_definitions, add_class_definitions);
		return qrunner;
	}

//	void addInferredEdges() throws OWLOntologyCreationException {
//		if(qrunner==null||qrunner.arachne==null) {
//			initializeQRunnerForTboxInference();
//		}
//		//System.out.println("Applying tbox rules to expand the gocam graph");
//		qrunner.wm = qrunner.arachne.createInferredModel(this.go_cam_ont, false, false);			
//		//System.out.println("Making Jena model from inferred graph for query");
//		qrunner.jena = qrunner.makeJenaModel(qrunner.wm);
//	}

	/**
	 * Use sparql queries to inform modifications to the go-cam owl ontology 
	 * assumes it is loaded with everything to start with a la qrunner = new QRunner(go_cam_ont); 
	 */
	void applySparqlRules() {
		Set<InferredEnabler> ies = qrunner.getInferredEnablers();
		for(InferredEnabler ie : ies) {
			//create ?reaction2 obo:RO_0002333 ?input
			OWLNamedIndividual e = this.makeAnnotatedIndividual(ie.enabler_uri);
			OWLNamedIndividual r2 = this.makeAnnotatedIndividual(ie.reaction2_uri);
			OWLNamedIndividual r1 = this.makeAnnotatedIndividual(ie.reaction1_uri);
			//delete the has_input relation
			OWLOntologyWalker walker = new OWLOntologyWalker(Collections.singleton(go_cam_ont));
			UpdateAnnotationsVisitor updater = new UpdateAnnotationsVisitor(walker, r2.getIRI(), has_input.getIRI(), e.getIRI());
			walker.walkStructure(updater); 
			if(updater.getAxioms()!=null&&updater.getAxioms().size()>0) {
				for(OWLAxiom a : updater.getAxioms()) {
					ontman.removeAxiom(go_cam_ont, a);
				}
			}
			//add the enabled_by relation 
			Set<OWLAnnotation> annos = getDefaultAnnotations();
			String r2_label = "'"+this.getaLabel(r2)+"'";
			String r1_label = "'"+this.getaLabel(r1)+"'";
			String e_label = "'"+this.getaLabel(e)+"'";
			String explain = "The relation "+r2_label+" enabled_by "+e_label+" was inferred because:\n"
					+ r1_label+" provides direct input for "+r2_label+" and \n"
					+ r1_label+" has output "+e_label+" and \n"
					+ r2_label+ " has input "+e_label+" .  The original has_input relation to "+e_label+" was replaced. See and comment on mapping rules at https://tinyurl.com/y8jctxxv ";
			annos.add(df.getOWLAnnotation(rdfs_comment, df.getOWLLiteral(explain)));
			this.addRefBackedObjectPropertyAssertion(r2, enabled_by, e, null, GoCAM.eco_inferred_auto, null, annos);
		//	System.out.println(r2+" added enabled by "+e);

		}
		//if subsequent rules need to compute over the results of previous rules, need to load the owl back into the rdf model
		System.out.println("done with enabled by, adding to rdf model");
		qrunner = new QRunner(go_cam_ont); 
	//	System.out.println("Added "+ies.size()+" enabled_by triples");
		Set<InferredRegulator> ir1 = qrunner.getInferredRegulatorsQ1();
		for(InferredRegulator ir : ir1) {
			//create ?reaction2 obo:RO_0002333 ?input
			OWLNamedIndividual r2 = this.makeAnnotatedIndividual(ir.reaction1_uri);
			OWLNamedIndividual r1 = this.makeAnnotatedIndividual(ir.reaction2_uri);
			OWLObjectProperty o = GoCAM.directly_negatively_regulates;
			String reg = "negatively regulates";
			if(ir.prop_uri.equals("http://purl.obolibrary.org/obo/RO_0002429")) {
				o = GoCAM.directly_positively_regulates;
				reg = "positively regulates";
			}
			String r1_label = "'"+this.getaLabel(r1)+"'";
			String r2_label = "'"+this.getaLabel(r2)+"'";
			String o_label = "'"+this.getaLabel(o)+"'";
			Set<OWLAnnotation> annos = getDefaultAnnotations();
			String explain = "The relation "+r2_label+" "+o_label+" "+r1_label+" was inferred because: "
					+r1_label+" has output A and A is involved in "+reg+" "+r2_label+". See and comment on mapping rules at https://tinyurl.com/y8jctxxv ";
			annos.add(df.getOWLAnnotation(rdfs_comment, df.getOWLLiteral(explain)));
			this.addRefBackedObjectPropertyAssertion(r1, o, r2, null, GoCAM.eco_inferred_auto, null, annos);
//			System.out.println("reg1 "+r2+" "+o+" "+r1);
		}
		//if subsequent rules need to compute over the results of previous rules, need to load the owl back into the rdf model
		System.out.println("done with directly asserted neg pos regulates, adding to rdf model");
		qrunner = new QRunner(go_cam_ont); 
//		System.out.println("Added "+ir1.size()+" pos/neg reg triples");
		Set<InferredRegulator> ir2_neg = qrunner.getInferredRegulatorsQ2();
		for(InferredRegulator ir : ir2_neg) {
			//create ?reaction2 obo:RO_0002333 ?input
			OWLNamedIndividual r2 = this.makeAnnotatedIndividual(ir.reaction1_uri);
			OWLNamedIndividual r1 = this.makeAnnotatedIndividual(ir.reaction2_uri);
			OWLObjectProperty o = df.getOWLObjectProperty(IRI.create(ir.prop_uri));
			String r1_label = "'"+this.getaLabel(r1)+"'";
			String r2_label = "'"+this.getaLabel(r2)+"'";
			String o_label = "'"+this.getaLabel(o)+"'";
			Set<OWLAnnotation> annos = getDefaultAnnotations();
			String explain = "The relation "+r1_label+" "+o_label+" "+r2_label+" was inferred because:\n "+
					r2_label+" has inputs A and B, "+r2_label+" has output A/B complex, and " + 
					r1_label+" is enabled by B. See and comment on mapping rules at https://tinyurl.com/y8jctxxv ";
			annos.add(df.getOWLAnnotation(rdfs_comment, df.getOWLLiteral(explain)));
			//this.addObjectPropertyAssertion(r1, o, r2, annos);
			this.addRefBackedObjectPropertyAssertion(r2, o, r1, null, GoCAM.eco_inferred_auto, null, annos);
//			System.out.println("reg2 "+r1+" "+o+" "+r2);
		}		
		qrunner = new QRunner(go_cam_ont); 
		System.out.println("done with secondary negative regulates");
//		System.out.println("Added "+ir2_neg.size()+" neg inhibitory binding reg triples");
	}

	void writeGoCAM(String outfilename, boolean save2blazegraph) throws OWLOntologyStorageException, OWLOntologyCreationException, RepositoryException, RDFParseException, RDFHandlerException, IOException {
		File outfilefile = new File(outfilename);	
		//use jena export
		System.out.println("writing n triples: "+qrunner.nTriples());
		qrunner.dumpModel(outfilefile, "TURTLE");
		//reads in file created above and converts to journal
		//could optimize speed by going direct at some point if it matters
		if(save2blazegraph) {
			if(blazegraphdb==null) {
				initializeBlazeGraph(path2bgjournal);
			}
			blazegraphdb.importModelToDatabase(outfilefile);
		}
	}

	void readGoCAM(String infilename) throws OWLOntologyCreationException {
		go_cam_ont = ontman.loadOntologyFromOntologyDocument(new File(infilename));		
	}

	/**
	 * Check the generated ontology for logical inconsistencies.
	 * TODO add other checks on adherence to GO-CAM schema.  
	 * @return
	 * @throws OWLOntologyCreationException 
	 */
	boolean validateGoCAM() throws OWLOntologyCreationException {
		boolean is_valid = false;
		if(qrunner==null) {
			System.out.println("qrunner must exist to run validation");
			System.exit(0);
		}
		is_valid = qrunner.isConsistent();		
		if(is_valid) {
			System.out.println("GO-CAM model is consistent, Total triples in validated model including tbox: "+qrunner.nTriples());
		}else {
			System.out.println("GO-CAM model is not logically consistent, please inspect model and try again!\n Entities = OWL:Nothing include:\n");
			Set<String> u = qrunner.getUnreasonableEntities();
			for(String s : u) {
				System.out.println(s);
			}
		}
		return is_valid;
	}

	/**
	 * given an existing QRunner (which contains an ArachneAccessor that should already be loaded with the base tbox)
	 * add any additional rules from go_cam_ont and run the inference rules  
	 * Sets the qrunner jena model to include all the inferred edges
	 * @param tbox_qrunner
	 */
	public WorkingMemory applyArachneInference(QRunner tbox_qrunner, boolean rebuild_tbox_with_go_cam_ont) {
		WorkingMemory wm;
		//slow
		if(rebuild_tbox_with_go_cam_ont) {
			RuleEngine rulen = tbox_qrunner.arachne.makeExpandedRuleSet(go_cam_ont);
			Set<Statement> statements = JavaConverters.setAsJavaSetConverter(SesameJena.ontologyAsTriples(go_cam_ont)).asJava();
			Set<Triple> triples = statements.stream().map(s -> Bridge.tripleFromJena(s.asTriple())).collect(Collectors.toSet());
//			//apply inference
			wm = rulen.processTriples(JavaConverters.asScalaSetConverter(triples).asScala());
		}else {
			wm = tbox_qrunner.arachne.createInferredModel(go_cam_ont,false, false);
		}
//		//move to jena for query
		qrunner.jena = qrunner.makeJenaModel(wm);
		//return wm to access inferences and explanations
		return wm;
	}

}
