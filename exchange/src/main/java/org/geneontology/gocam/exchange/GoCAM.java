/**
 * 
 */
package org.geneontology.gocam.exchange;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
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
import java.util.TreeMap;
import java.util.stream.Collectors;

import org.apache.jena.rdf.model.Statement;
import org.apache.jena.vocabulary.RDF;
import org.biopax.paxtools.model.level3.PublicationXref;
import org.biopax.paxtools.model.level3.Xref;
import org.geneontology.gocam.exchange.QRunner.ComplexInput;
import org.geneontology.gocam.exchange.QRunner.InferredEnabler;
import org.geneontology.gocam.exchange.QRunner.InferredOccursIn;
import org.geneontology.gocam.exchange.QRunner.InferredRegulator;
import org.geneontology.gocam.exchange.QRunner.InferredTransport;
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
import org.semanticweb.owlapi.model.AxiomType;
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
import org.semanticweb.owlapi.model.OWLObjectPropertyExpression;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyCreationException;
import org.semanticweb.owlapi.model.OWLOntologyManager;
import org.semanticweb.owlapi.model.OWLOntologyStorageException;
import org.semanticweb.owlapi.model.OWLSubClassOfAxiom;
import org.semanticweb.owlapi.model.RemoveAxiom;
import org.semanticweb.owlapi.search.EntitySearcher;
import org.semanticweb.owlapi.search.Searcher;
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
	state_prop, evidence_prop, provided_by_prop, x_prop, y_prop, rdfs_label, rdfs_comment, source_prop, 
	definition, database_cross_reference;
	public static OWLObjectProperty part_of, has_part, has_input, has_output, 
	provides_direct_input_for, directly_inhibits, directly_activates, occurs_in, enabled_by, enables, regulated_by, located_in,
	directly_positively_regulated_by, directly_negatively_regulated_by, involved_in_regulation_of, involved_in_negative_regulation_of, involved_in_positive_regulation_of,
	directly_negatively_regulates, directly_positively_regulates, has_role, causally_upstream_of, causally_upstream_of_negative_effect, causally_upstream_of_positive_effect,
	has_target_end_location, has_target_start_location, interacts_with, has_participant, functionally_related_to;

	public static OWLClass 
	bp_class, continuant_class, process_class, go_complex, cc_class, molecular_function, 
	eco_imported, eco_imported_auto, eco_inferred_auto, 
	chebi_protein, chebi_gene, chemical_entity, chemical_role, 
	catalytic_activity, binding, signal_transducer_activity, transporter_activity,
	protein_binding, establishment_of_protein_localization;
	public OWLOntology go_cam_ont;
	public OWLDataFactory df;
	public OWLOntologyManager ontman;
	String base_contributor, base_date, base_provider;
	//for inference 
	public QRunner qrunner;
	//for storage
	String path2bgjournal;
	Blazer blazegraphdb;

	public GoCAM() throws OWLOntologyCreationException {
		ontman = OWLManager.createOWLOntologyManager();				
		go_cam_ont = ontman.createOntology();
		df = OWLManager.getOWLDataFactory();
		initializeClassesAndRelations();
	}

	public GoCAM(String filename) throws OWLOntologyCreationException {
		ontman = OWLManager.createOWLOntologyManager();				
		go_cam_ont = ontman.loadOntologyFromOntologyDocument(new File(filename));
		df = OWLManager.getOWLDataFactory();
		initializeClassesAndRelations();
	}

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
		initializeClassesAndRelations();

		//TODO basically never going to do this, maybe take it out..
		if(add_lego_import) {
			String lego_iri = "http://purl.obolibrary.org/obo/go/extensions/go-lego.owl";
			OWLImportsDeclaration legoImportDeclaration = df.getOWLImportsDeclaration(IRI.create(lego_iri));
			ontman.applyChange(new AddImport(go_cam_ont, legoImportDeclaration));
		}
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

	}


	public void initializeClassesAndRelations() {
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
		definition = df.getOWLAnnotationProperty(IRI.create("http://purl.obolibrary.org/obo/IAO_0000115"));	
		database_cross_reference = df.getOWLAnnotationProperty(IRI.create("http://www.geneontology.org/formats/oboInOwl#hasDbXref"));	


		//Will add classes and relations as we need them now. 
		//TODO Work on using imports later to ensure we don't produce incorrect ids..
		//classes	

		catalytic_activity = df.getOWLClass(IRI.create(obo_iri+"GO_0003824"));
		binding = df.getOWLClass(IRI.create(obo_iri+"GO_0005488"));
		protein_binding = df.getOWLClass(IRI.create(obo_iri+"GO_0005515"));
		establishment_of_protein_localization = df.getOWLClass(IRI.create(obo_iri+"GO_0045184"));

		signal_transducer_activity = df.getOWLClass(IRI.create(obo_iri+"GO_0004871"));
		transporter_activity = df.getOWLClass(IRI.create(obo_iri+"GO_0005215"));

		chemical_role =df.getOWLClass(IRI.create(obo_iri+"CHEBI_50906"));
		addLabel(chemical_role, "chemical role");
		//biological process
		bp_class = df.getOWLClass(IRI.create(obo_iri + "GO_0008150")); 
		addLabel(bp_class, "Biological Process");
		//molecular function GO:0003674
		molecular_function = df.getOWLClass(IRI.create(obo_iri + "GO_0003674")); 
		addLabel(molecular_function, "Molecular Function");
		//cellular component
		cc_class =  df.getOWLClass(IRI.create(obo_iri + "GO_0005575"));
		addLabel(cc_class, "Cellular Component");
		//continuant 
		continuant_class = df.getOWLClass(IRI.create(obo_iri + "BFO_0000002")); 
		addLabel(continuant_class, "Continuant");
		//occurent
		process_class =  df.getOWLClass(IRI.create(obo_iri + "BFO_0000015")); 
		addLabel(process_class, "Process");		
		//complex GO_0032991
		go_complex = df.getOWLClass(IRI.create(obo_iri + "GO_0032991")); 
		addLabel(go_complex, "protein-containing complex");		
		//http://purl.obolibrary.org/obo/ECO_0000313
		//"A type of imported information that is used in an automatic assertion."
		eco_imported_auto = df.getOWLClass(IRI.create(obo_iri + "ECO_0000313")); 
		//"A type of evidence that is based on work performed by a person or group prior to a use by a different person or group."
		eco_imported = df.getOWLClass(IRI.create(obo_iri + "ECO_0000311")); 
		//ECO_0000363 "A type of evidence based on computational logical inference that is used in automatic assertion."
		eco_inferred_auto = df.getOWLClass(IRI.create(obo_iri + "ECO_0000363")); 		
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
		//RO_0002434 
		interacts_with = df.getOWLObjectProperty(IRI.create(obo_iri + "RO_0002434")); 
		addLabel(interacts_with, "interacts with");
		has_participant  = df.getOWLObjectProperty(IRI.create(obo_iri + "RO_0000057")); 
		addLabel(has_participant, "has participant");
		functionally_related_to  = df.getOWLObjectProperty(IRI.create(obo_iri + "RO_0002328")); 
		addLabel(functionally_related_to, "functionally related to");

		//http://purl.obolibrary.org/obo/RO_0002305
		causally_upstream_of  = df.getOWLObjectProperty(IRI.create(obo_iri + "RO_0002411"));
		addLabel(causally_upstream_of, "causally upstream of");
		causally_upstream_of_negative_effect  = df.getOWLObjectProperty(IRI.create(obo_iri + "RO_0002305"));
		addLabel(causally_upstream_of_negative_effect, "causally upstream of with a negative effect");
		causally_upstream_of_positive_effect  = df.getOWLObjectProperty(IRI.create(obo_iri + "RO_0002304"));
		addLabel(causally_upstream_of_positive_effect, "causally upstream of with a positive effect");

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

		has_target_end_location = df.getOWLObjectProperty(IRI.create(obo_iri + "RO_0002339"));
		has_target_start_location = df.getOWLObjectProperty(IRI.create(obo_iri + "RO_0002338"));
	}

	public ClassificationReport getClassificationReport(){
		ClassificationReport class_report = new ClassificationReport();
		class_report.mf_count = EntitySearcher.getIndividuals(molecular_function, go_cam_ont).size();
		class_report.bp_count = EntitySearcher.getIndividuals(bp_class, go_cam_ont).size();
		class_report.cc_count = EntitySearcher.getIndividuals(cc_class, go_cam_ont).size();
		class_report.complex_count = EntitySearcher.getIndividuals(go_complex, go_cam_ont).size();
		class_report.mf_unclassified = countUnclassifiedRDF(molecular_function, go_cam_ont);
		class_report.bp_unclassified = countUnclassifiedRDF(bp_class, go_cam_ont);
		class_report.cc_unclassified = countUnclassifiedRDF(cc_class, go_cam_ont);
		class_report.complex_unclassified = countUnclassifiedRDF(go_complex, go_cam_ont);
		return class_report;
	}

	public int countUnclassifiedRDF(OWLClass root_class, OWLOntology ont) {
		int un = 0; 
		//only count unique concepts once - even when there are many individuals that are expressions of that concept
		//e.g. one complex may be used many many times in a model.  If it is unclassified, just count that as one.  
		Set<String> unique = new HashSet<String>();
		for(OWLIndividual i : EntitySearcher.getIndividuals(root_class, ont)) {
			String label = this.getaLabel((OWLEntity)i);
			if(unique.add(label)) {
				Set<String> types = qrunner.getTypes(i.asOWLNamedIndividual().getIRI().toString());
				int ntypes = 0;
				String root_class_iri = root_class.getIRI().toString();
				for(String type : types) {
					if((!type.equals(root_class_iri))&&type.contains("GO")) {
						ntypes++;
					}
				}
				if(ntypes==0) {
					un++;
				}
			}
		}
		return un;
	}

	public int countUnclassifiedOWL(OWLClass root_class) {
		int un = 0; 
		//only count unique concepts once - even when there are many individuals that are expressions of that concept
		//e.g. one complex may be used many many times in a model.  If it is unclassified, just count that as one.  
		Set<String> unique = new HashSet<String>();
		for(OWLIndividual i : EntitySearcher.getIndividuals(root_class, go_cam_ont)) {
			String label = this.getaLabel((OWLEntity)i);
			if(unique.add(label)) {
				Collection<OWLClassExpression> types = EntitySearcher.getTypes(i, go_cam_ont);
				int ntypes = 0;
				for(OWLClassExpression type : types) {
					if(type!=root_class&&type.toString().contains("GO")) {
						ntypes++;
					}
				}
				if(ntypes==0) {
					un++;
				}
			}
		}
		return un;
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

	public OWLAnnotation addLiteralAnnotations2Individual(IRI individual_iri, OWLAnnotationProperty prop, String value) {
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

	public void addLabel(OWLEntity entity, String label) {
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

	public void addComment(OWLEntity entity, String comment) {
		if(comment==null) {
			return;
		}		
		OWLLiteral c = df.getOWLLiteral(comment);
		OWLAnnotation comment_anno = df.getOWLAnnotation(rdfs_comment, c);
		OWLAxiom commentaxiom = df.getOWLAnnotationAssertionAxiom(entity.getIRI(), comment_anno);
		ontman.addAxiom(go_cam_ont, commentaxiom);
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
		return Helper.getLabels(e, go_cam_ont);
	}

	public String getaLabel(OWLEntity e){
		return Helper.getaLabel(e, go_cam_ont);
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
	OWLAxiom addRefBackedObjectPropertyAssertion(OWLNamedIndividual source, OWLObjectProperty prop, OWLNamedIndividual target, 
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
				IRI anno_iri = makeRandomIri();
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
			return check; //already have it
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
			return add_prop_axiom;
		}		
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
	public QRunner initializeQRunnerForTboxInference(Set<String> tbox_files) throws OWLOntologyCreationException {
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

	public QRunner initializeQRunner(Collection<OWLOntology> tbox) throws OWLOntologyCreationException {
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

	void applyAnnotatedTripleRemover(IRI subject, IRI predicate, IRI object) {
		OWLOntologyWalker walker = new OWLOntologyWalker(Collections.singleton(go_cam_ont));
		UpdateAnnotationsVisitor updater = new UpdateAnnotationsVisitor(walker, subject, predicate, object);
		walker.walkStructure(updater); 
		if(updater.getAxioms()!=null&&updater.getAxioms().size()>0) {
			for(OWLAxiom a : updater.getAxioms()) {
				ontman.removeAxiom(go_cam_ont, a);
			}
		}
	}

	void deleteOwlEntityAndAllReferencesToIt(OWLEntity e) {
		for (OWLAnnotationAssertionAxiom annAx : EntitySearcher.getAnnotationAssertionAxioms(e.getIRI(), this.go_cam_ont)) {
			ontman.removeAxiom(go_cam_ont, annAx);
		}
		for (OWLAxiom annAx :EntitySearcher.getReferencingAxioms(e, this.go_cam_ont)) {
			ontman.removeAxiom(go_cam_ont, annAx);
		}
	}

	public class RuleResults{
		Map<String, Integer> rule_hitcount = new TreeMap<String, Integer>();
		Map<String, Set<String>> rule_pathways = new TreeMap<String, Set<String>>();
		Map<String, Set<String>> rule_reactions = new TreeMap<String, Set<String>>();

		public Integer checkInitCount(String rulename, RuleResults r) {
			Integer i_o_count = r.rule_hitcount.get(rulename);
			if(i_o_count==null) {
				i_o_count = 0;
			}
			return i_o_count;
		}

		public Set<String> checkInitPathways(String rulename, RuleResults r){
			Set<String> i_o_pathways = r.rule_pathways.get(rulename);
			if(i_o_pathways==null) {
				i_o_pathways = new HashSet<String>();
			}
			return i_o_pathways;
		}

		public Set<String> checkInitReactions(String rulename, RuleResults r){
			Set<String> i_o_reactions = r.rule_reactions.get(rulename);
			if(i_o_reactions==null) {
				i_o_reactions = new HashSet<String>();
			}
			return i_o_reactions;
		}

		public String toString() {
			String result = "";
			for(String rule : rule_hitcount.keySet()) {
				result+=rule+"\t"+rule_hitcount.get(rule)+"\t"+rule_pathways.get(rule)+"\n";
			}
			return result;
		}
	}

	/**
	 * Use sparql queries to inform modifications to the go-cam owl ontology 
	 * assumes it is loaded with everything to start with a la qrunner = new QRunner(go_cam_ont); 
	 * @throws IOException 
	 */
	RuleResults applySparqlRules(BioPaxtoGO.ImportStrategy strategy) {
		RuleResults r = new RuleResults();

		/**
		 * Rule 2: Infer rdf:type Protein Binding
		 * If a reaction has no GO annotation beyond 'molecular function'
		 * and the reaction has an output that is a protein complex
		 * and the output protein complex contains one of the inputs to the reaction
		 * Then assign the reaction as rdf:type Protein Binding
		 * 
		 * See 'Signaling by BMP' https://reactome.org/content/detail/R-HSA-201451 
		 * (12 inferences)
		 */
		String binding_rule = "binding";
		Integer binding_count = r.checkInitCount(binding_rule, r);
		Set<String> binding_reactions = r.checkInitReactions(binding_rule, r);
		Set<String> binders = qrunner.findBindingReactions();
		if(!binders.isEmpty()) {
			binding_count+= binders.size();
			binding_reactions.addAll(binders);
		}
		for(String binder_uri : binders) {
			OWLNamedIndividual reaction = this.makeAnnotatedIndividual(binder_uri);
			//delete the generic type
			//not as fancy as below because types don't currently have annotations on them
			OWLClassAssertionAxiom classAssertion = df.getOWLClassAssertionAxiom(molecular_function, reaction);
			ontman.removeAxiom(go_cam_ont, classAssertion);
			//add the binding type 
			addTypeAssertion(reaction, protein_binding);
			//add a comment on the node
			addLiteralAnnotations2Individual(reaction.getIRI(), rdfs_comment, "Inferred to be of type protein binding because an output complex contains at least one of its the inputs");
		}
		r.rule_hitcount.put(binding_rule, binding_count);
		r.rule_reactions.put(binding_rule, binding_reactions);

		/**
		 * Rule 3: Infer Protein Transport reactions
		 * If a reaction has not been provided with an rdf:type 
		 * and the input entities are the same as the output entities
		 * and the input entities have different locations from the output entities
		 * then tag it as rdf:type 'establishment of protein localization' (notably a BP not an MF)
		 * and add has_target_end_location and has_target_start_location attributes to the reaction node
		 * See: 'Deactivation of the beta-catenin transactivating complex' https://reactome.org/PathwayBrowser/#/R-HSA-3769402
		 * (1 inference for reaction 'Beta-catenin translocates to the nucleus', 'https://reactome.org/PathwayBrowser/#/R-HSA-201681&SEL=R-HSA-201669&PATH=R-HSA-162582,R-HSA-195721'
		 *  Downstream dependency alert: do this before enabler inference step below since we don't want that rule to fire on transport reactions
		 */
		String transport_rule = "transport";
		Integer transport_count = r.checkInitCount(transport_rule, r);
		Set<String> transport_pathways = r.checkInitPathways(transport_rule, r);		
		Set<InferredTransport> transports = qrunner.findTransportReactions();		
		if(transports.size()>0) {
			transport_count+=transports.size();
			Set<String> transport_reactions = new HashSet<String>();
			for(InferredTransport transport : transports) {
				if(!transport_reactions.add(transport.reaction_uri)){
					//should only end up with one per reaction.. make sure
					continue;
				}
				transport_pathways.add(transport.pathway_uri);
				OWLNamedIndividual reaction = this.makeAnnotatedIndividual(transport.reaction_uri);
				OWLClassAssertionAxiom classAssertion = df.getOWLClassAssertionAxiom(molecular_function, reaction);
				ontman.removeAxiom(go_cam_ont, classAssertion);
				//add transport type
				addTypeAssertion(reaction, establishment_of_protein_localization);
				addLiteralAnnotations2Individual(reaction.getIRI(), rdfs_comment, "Inferred to be of type 'establishment of protein localization'"
						+ " because at least one protein is the same as an input and an output aside from its location.");
				//record what moved where so the classifier can see it properly
				OWLNamedIndividual start_loc = makeAnnotatedIndividual(makeRandomIri());
				OWLClass start_loc_type = df.getOWLClass(IRI.create(transport.input_loc_class_uri));
				addTypeAssertion(start_loc, start_loc_type);				
				OWLNamedIndividual end_loc = makeAnnotatedIndividual(makeRandomIri());
				OWLClass end_loc_type = df.getOWLClass(IRI.create(transport.output_loc_class_uri));
				addTypeAssertion(end_loc, end_loc_type);				
				//add relations to enable deeper classification based on OWL axioms in BP branch
				Set<OWLAnnotation> annos = getDefaultAnnotations();
				String explain1 = "This relation was inferred because a protein that was an input to the reaction started out in the target location "+getaLabel(end_loc_type)
				+ " and then, as a consequence of the reaction/process was transported to another location.";
				annos.add(df.getOWLAnnotation(rdfs_comment, df.getOWLLiteral(explain1)));				
				addRefBackedObjectPropertyAssertion(reaction, has_target_start_location, start_loc, null, GoCAM.eco_inferred_auto, null, annos);
				String explain2 = "This relation was inferred because a protein that was an input to the reaction started one location "
						+ " and then, as a consequence of the reaction/process was transported to the target end location "+getaLabel(end_loc_type);
				Set<OWLAnnotation> annos2 = getDefaultAnnotations();
				annos2.add(df.getOWLAnnotation(rdfs_comment, df.getOWLLiteral(explain2)));
				addRefBackedObjectPropertyAssertion(reaction, has_target_end_location, end_loc, null, GoCAM.eco_inferred_auto, null, annos2);
			}
			//enabled by needs to know if there are any transport reactions as these should not be included
			//hence reload graph from ontology
			qrunner = new QRunner(go_cam_ont);
		}
		r.rule_hitcount.put(transport_rule, transport_count);
		r.rule_pathways.put(transport_rule, transport_pathways);

		/**
		 * Rule 1: infer occurs_in relations
		 * If all participants in a reaction are located_in the same place,
		 * Then assert that the reaction occurs_in that place and remove the located_in assertions
		 * OR, if the strategy is targeted towards curator review, take all of the locations used for INPUTs and add them as occurs_in
		 * Run after transport inference as this will remove locations
		 * See 'Signaling by BMP' https://reactome.org/content/detail/R-HSA-201451 
		 * (7 inferences)
		 */
		String i_o_rule = "occurs_in";
		Integer i_o_count = r.checkInitCount(i_o_rule, r);
		Set<String> i_o_pathways = r.checkInitPathways(i_o_rule, r);
		Set<InferredOccursIn> inferred_occurs = qrunner.findOccursInReaction();
		if(inferred_occurs.isEmpty()) {
			System.out.println("No occurs in");
		}else {
			i_o_count+=inferred_occurs.size();			
			for(InferredOccursIn o : inferred_occurs) {
				boolean add_occurs = false;
				if(o.location_type_uris.size()==1||strategy == BioPaxtoGO.ImportStrategy.NoctuaCuration) {
					add_occurs = true;
				}	
				if(add_occurs) {
					i_o_pathways.add(o.pathway_uri);
					OWLNamedIndividual reaction = this.makeAnnotatedIndividual(o.reaction_uri);
					//make the occurs in assertion
					for(String location_type_uri : o.location_type_uris) {
						OWLClass location_class = df.getOWLClass(IRI.create(location_type_uri));
						Set<OWLAnnotation> annos = getDefaultAnnotations();
						String explain1 = "This relation was inferred because an input, enabler, or regulator of this reaction was said to be located in "+getaLabel(location_class);
						annos.add(df.getOWLAnnotation(rdfs_comment, df.getOWLLiteral(explain1)));		
						OWLNamedIndividual placeInstance = df.getOWLNamedIndividual(GoCAM.makeRandomIri());
						addTypeAssertion(placeInstance, location_class);
						addRefBackedObjectPropertyAssertion(reaction, GoCAM.occurs_in, placeInstance, null, GoCAM.eco_imported_auto, "PMID", annos);
					}
				}
			}
		}
		r.rule_hitcount.put(i_o_rule, i_o_count);
		r.rule_pathways.put(i_o_rule, i_o_pathways);			

		/**
		 * Rule 4: Infer enabled_by 
		 * If R1 provides_direct_input_for R2 
		 * and R1 has_output E1
		 * and R2 has_input E2
		 * and E1 is the same type of thing as E2 (see sparql for details)
		 * and R2 is not enabled by anything else 
		 * Then infer that an entity (either protein or protein complex) E enables a reaction R2
		 * Remove the has_input relation replaced by the enabled_by relation 
		 * 
		 * See 'Signaling by BMP' https://reactome.org/content/detail/R-HSA-201451 
		 * (6 inferences)
		 */
		//infer and change some inputs to enablers
		String enabler_rule = "enabler";
		Integer enabler_count = r.checkInitCount(enabler_rule, r);
		Set<String> enabler_pathways = r.checkInitPathways(enabler_rule, r);		
		Set<InferredEnabler> ies = qrunner.getInferredEnablers();
		if(!ies.isEmpty()) {
			enabler_count+=ies.size();
		}
		for(InferredEnabler ie : ies) {			
			//enabler_pathways.add(ie.pathway_uri);
			//create ?reaction2 obo:RO_0002333 ?input
			OWLNamedIndividual e = this.makeAnnotatedIndividual(ie.enabler_uri);
			OWLNamedIndividual r2 = this.makeAnnotatedIndividual(ie.reaction2_uri);
			OWLNamedIndividual r1 = this.makeAnnotatedIndividual(ie.reaction1_uri);
			//delete the has_input relation
			applyAnnotatedTripleRemover(r2.getIRI(), has_input.getIRI(), e.getIRI());
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
		}
		r.rule_hitcount.put(enabler_rule, enabler_count);
		r.rule_pathways.put(enabler_rule, enabler_pathways);
		qrunner = new QRunner(go_cam_ont); 

		/**
		 * Rule 5: Regulator 1: direct assertion 
		 * If an entity is involved_in_regulation_of reaction1 
		 * And that entity is the output of reaction 2
		 * Then infer that reaction 2 regulates reaction 1
		 * (capture if its positive or negative regulation)
		 * 
		 * See 'Disassembly of the destruction complex and recruitment of AXIN to the membrane' 
		 * e.g. reaction Beta-catenin is released from the destruction complex 
		 * https://reactome.org/PathwayBrowser/#/R-HSA-201681&SEL=R-HSA-201685&PATH=R-HSA-162582,R-HSA-195721
		 * which should be generated by 'WNT:FZD:p10S/T-LRP5/6:DVL:AXIN:GSK3B' involved_in_positive_regulation_of 'Beta-catenin is released from the destruction complex'
		 */

		String regulator_rule = "regulator_1";
		Integer regulator_count = r.checkInitCount(regulator_rule, r);
		Set<String> regulator_pathways = r.checkInitPathways(regulator_rule, r);
		Set<InferredRegulator> ir1 = qrunner.getInferredRegulatorsQ1();
		regulator_count+=ir1.size();
		for(InferredRegulator ir : ir1) {
			regulator_pathways.add(ir.pathway_uri);
			OWLNamedIndividual r2 = this.makeAnnotatedIndividual(ir.reaction1_uri);
			OWLNamedIndividual r1 = this.makeAnnotatedIndividual(ir.reaction2_uri);
			OWLObjectProperty o = GoCAM.directly_negatively_regulates;
			OWLNamedIndividual entity = this.makeAnnotatedIndividual(ir.entity_uri);
			String reg = "negatively regulates";
			if(ir.prop_uri.equals("http://purl.obolibrary.org/obo/RO_0002429")) {
				o = GoCAM.directly_positively_regulates;
				reg = "positively regulates";
			}
			Set<OWLAnnotation> annos = getDefaultAnnotations();
			//add the process process regulates relation
			String entity_label = getaLabel(entity);
			String explain = "The is relation was inferred because reaction1 has output "+entity_label+" and "
					+ entity_label+" "+reg+" reaction2.  Note that this regulation is non-catalytic. See and comment on mapping rules at https://tinyurl.com/y8jctxxv ";
			annos.add(df.getOWLAnnotation(rdfs_comment, df.getOWLLiteral(explain)));
			this.addRefBackedObjectPropertyAssertion(r1, o, r2, null, GoCAM.eco_inferred_auto, null, annos);
			//delete the entity regulates process relation 
			applyAnnotatedTripleRemover(r2.getIRI(), o.getIRI(), entity.getIRI());
			
		}
		r.rule_hitcount.put(regulator_rule, regulator_count);
		r.rule_pathways.put(regulator_rule, regulator_pathways);
		qrunner = new QRunner(go_cam_ont); 

		/**
		 * Rule 6: Regulator 2: negative regulation by binding
		 * If reaction2 is enabled_by entity1
		 * And reaction1 has entity1 as an input
		 * And reaction1 has entity2 as an input
		 * And reaction1 has a complex containign entity1 and entity2 as output
		 * Then infer that reaction1 directly_negatively_regulates reaction2
		 * (by binding up the entity that enables reaction2 to happen).
		 * 
		 *  See 'Signaling by BMP' https://reactome.org/content/detail/R-HSA-201451 
		 * 
		 */
		String regulator_rule_2 = "regulator_2";
		Integer regulator_count_2 = r.checkInitCount(regulator_rule_2, r);
		Set<String> regulator_pathways_2 = r.checkInitPathways(regulator_rule_2, r);

		Set<InferredRegulator> ir2_neg = qrunner.getInferredRegulatorsQ2();
		regulator_count_2+=ir2_neg.size();
		for(InferredRegulator ir : ir2_neg) {
			regulator_pathways_2.add(ir.pathway_uri);
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
		r.rule_hitcount.put(regulator_rule_2, regulator_count_2);
		r.rule_pathways.put(regulator_rule_2, regulator_pathways_2);
		qrunner = new QRunner(go_cam_ont); 

		/*
		 * Noctua Curation strategy rules
		 */
		if(strategy == BioPaxtoGO.ImportStrategy.NoctuaCuration) {
			/**
			 * Rule Noctua 1 : Delete all location assertions if for noctua curation
			 * Note that in some cases, location assertions interact with occurs_in property chains resulting in inconsistencies..
			 * Doing this in ontology instead of just RDF (go_cam.qrunner.deleteEntityLocations();) 
			 * so we can check it with reasoner
			 */

			for(OWLObjectPropertyAssertionAxiom a : go_cam_ont.getAxioms(AxiomType.OBJECT_PROPERTY_ASSERTION)) {
				OWLObjectPropertyExpression p = a.getProperty();
				if(p.equals(located_in)) {
					OWLNamedIndividual s = a.getSubject().asOWLNamedIndividual();
					OWLNamedIndividual o = a.getObject().asOWLNamedIndividual();
					applyAnnotatedTripleRemover(s.getIRI(), located_in.getIRI(), o.getIRI());
					ontman.removeAxiom(go_cam_ont, a);
					deleteOwlEntityAndAllReferencesToIt(o);
				}
			}			

			/**
			 * Noctua Rule 2.  No complexes allowed as inputs or enablers of a reaction, only proteins..
			 * Find such complexes and replace all statements involving them with statements about their components
			 */
			Set<ComplexInput> complex_inputs = qrunner.findComplexInputs();
			int input_complex_count = 0;
			String input_complex_rule = "No complexes as input or output ";
			Set<String> ic_p = new HashSet<String>();
			for(ComplexInput complex_input : complex_inputs) {
				ic_p.add(complex_input.pathway_uri);
				OWLNamedIndividual reaction = this.makeAnnotatedIndividual(complex_input.reaction_uri);
				OWLObjectProperty property =df.getOWLObjectProperty(IRI.create(complex_input.property_uri));
				Map<String, Set<String>> complex_parts = complex_input.complex_parts;
				for(String complex_uri : complex_parts.keySet()) {
					OWLNamedIndividual complex = this.makeAnnotatedIndividual(complex_uri);
					input_complex_count++;
					//add claims on parts (unless output)
					for(String part : complex_parts.get(complex_uri)) {
						OWLNamedIndividual complex_part = this.makeAnnotatedIndividual(part);
						Set<OWLAnnotation> annos = getDefaultAnnotations();
						if(property.equals(has_output)) { //just remove it for now
							applyAnnotatedTripleRemover(complex.getIRI(), has_part.getIRI(), complex_part.getIRI());
							deleteOwlEntityAndAllReferencesToIt(complex_part);
						}else if(property.equals(involved_in_positive_regulation_of)||property.equals(involved_in_negative_regulation_of)) {
							String explain = "When a complex is involved in the non-catalytic regulation of a reaction, "
									+ "it is broken into its parts and each is asserted to be a regulator  "
									+ "of the molecular function corresponding to the reaction. Here the complex was: "+getaLabel(complex);
							annos.add(df.getOWLAnnotation(rdfs_comment, df.getOWLLiteral(explain)));
							addRefBackedObjectPropertyAssertion(complex_part, property, reaction, null, GoCAM.eco_inferred_auto, null, annos);	
						}else {
							String explain = "When a complex is an input of or an enabler of a reaction, "
									+ "it is broken into its parts and each is asserted to be an input or enabler  "
									+ "of the molecular function corresponding to the reaction. Here the complex was: "+getaLabel(complex);
							annos.add(df.getOWLAnnotation(rdfs_comment, df.getOWLLiteral(explain)));
							addRefBackedObjectPropertyAssertion(reaction, property, complex_part, null, GoCAM.eco_inferred_auto, null, annos);					
						}
					}
					//remove the complex
					//first remove evidence statements
					for(OWLObjectPropertyAssertionAxiom oass : go_cam_ont.getObjectPropertyAssertionAxioms(reaction)) {
						if(oass.getProperty().equals(property)&&oass.getObject().equals(complex)) {
							for(OWLAnnotation anno : oass.getAnnotations()) {
								if(anno.getProperty().equals(evidence_prop)) {
									OWLNamedIndividual evidence = df.getOWLNamedIndividual(anno.getValue().asIRI().get());
									deleteOwlEntityAndAllReferencesToIt(evidence);
								}
							}
						}
					}	
					if(property.equals(involved_in_positive_regulation_of)||property.equals(involved_in_negative_regulation_of)) {
						for(OWLObjectPropertyAssertionAxiom oass : go_cam_ont.getObjectPropertyAssertionAxioms(complex)) {
							if(oass.getProperty().equals(property)&&oass.getObject().equals(reaction)) {
								for(OWLAnnotation anno : oass.getAnnotations()) {
									if(anno.getProperty().equals(evidence_prop)) {
										OWLNamedIndividual evidence = df.getOWLNamedIndividual(anno.getValue().asIRI().get());
										deleteOwlEntityAndAllReferencesToIt(evidence);
									}
								}
							}
						}
						applyAnnotatedTripleRemover(complex.getIRI(), property.getIRI(), reaction.getIRI());
					}else {
						applyAnnotatedTripleRemover(reaction.getIRI(), property.getIRI(), complex.getIRI());
					}				
					deleteOwlEntityAndAllReferencesToIt(complex);
				}
			}
			r.rule_hitcount.put(input_complex_rule, input_complex_count);
			r.rule_pathways.put(input_complex_rule, ic_p);
			qrunner = new QRunner(go_cam_ont); 
		}


		return r;
	}

	void writeGoCAM_jena(String outfilename, boolean save2blazegraph) throws OWLOntologyStorageException, OWLOntologyCreationException, RepositoryException, RDFParseException, RDFHandlerException, IOException {
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
	public boolean validateGoCAM() throws OWLOntologyCreationException {
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
