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
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
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
import org.obolibrary.robot.CatalogXmlIRIMapper;
import org.obolibrary.robot.IOHelper;
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
import org.semanticweb.owlapi.reasoner.OWLReasoner;
import org.semanticweb.owlapi.reasoner.OWLReasonerFactory;
import org.semanticweb.owlapi.reasoner.structural.StructuralReasonerFactory;
import org.semanticweb.owlapi.search.EntitySearcher;
import org.semanticweb.owlapi.search.Searcher;
import org.semanticweb.owlapi.util.OWLEntityRemover;
import org.semanticweb.owlapi.util.OWLOntologyWalker;
import org.semanticweb.owlapi.vocab.OWLRDFVocabulary;

import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;

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
	public static OWLAnnotationProperty title_prop, contributor_prop, date_prop, skos_exact_match, skos_altlabel,  
	state_prop, evidence_prop, provided_by_prop, x_prop, y_prop, rdfs_label, rdfs_comment, rdfs_seealso, source_prop, 
	definition, database_cross_reference, canonical_record;
	public static OWLObjectProperty part_of, has_part, has_input, has_output, has_component, 
	provides_direct_input_for, directly_inhibits, directly_activates, occurs_in, enabled_by, enables, regulated_by, located_in,
	directly_positively_regulated_by, directly_negatively_regulated_by, involved_in_regulation_of, involved_in_negative_regulation_of, involved_in_positive_regulation_of,
	directly_negatively_regulates, directly_positively_regulates, has_role, causally_upstream_of, causally_upstream_of_negative_effect, causally_upstream_of_positive_effect,
	negatively_regulates, positively_regulates, 
	has_target_end_location, has_target_start_location, interacts_with, has_participant, functionally_related_to,
	contributes_to, only_in_taxon;

	public static OWLClass 
	bp_class, continuant_class, process_class, go_complex, cc_class, molecular_function, 
	eco_imported, eco_imported_auto, eco_inferred_auto, 
	chebi_molecular_entity, 
	chebi_protein, chebi_information_biomacromolecule, chemical_entity, chemical_role, 
	catalytic_activity, signal_transducer_activity, transporter_activity,
	binding, protein_binding, protein_complex_binding, 
	establishment_of_protein_localization, negative_regulation_of_molecular_function, positive_regulation_of_molecular_function,
	chebi_mrna, chebi_rna, chebi_dna, unfolded_protein, 
	transport, protein_transport, human, 
	union_set;
	public static OWLClassExpression taxon_human;

	public OWLOntology go_cam_ont;
	public OWLDataFactory df;
	public OWLOntologyManager ontman;
	String base_contributor, base_date, base_provider;
	//for inference 
	public QRunner qrunner;
	//for storage
	String path2bgjournal;
	Blazer blazegraphdb;
	//for convenience
	String name;

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
	 * This is the one to use if trying to read in a GO-CAM model that imports lego (which imports the universe).  
	 * Use the catalogue file to control what gets imported - your probably don't need anything.
	 * @param ontology_file
	 * @param catalog
	 * @throws OWLOntologyCreationException
	 * @throws IOException
	 */
	public GoCAM(File ontology_file, String catalog) throws OWLOntologyCreationException, IOException {
		ontman = OWLManager.createOWLOntologyManager();				
		//IOHelper helper = new IOHelper();
		//go_cam_ont = helper.loadOntology(filename, catalog);
		ontman.setIRIMappers(Sets.newHashSet(new CatalogXmlIRIMapper(catalog)));
		go_cam_ont = ontman.loadOntologyFromOntologyDocument(ontology_file);
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

		//TODO find a better way
		if(add_lego_import) {
			String lego_iri = "http://purl.obolibrary.org/obo/go/extensions/go-lego.owl";
			OWLImportsDeclaration legoImportDeclaration = df.getOWLImportsDeclaration(IRI.create(lego_iri));
			ontman.applyChange(new AddImport(go_cam_ont, legoImportDeclaration));
			//until it is in go_lego all the models are going to need it
			String reo_iri = "https://github.com/geneontology/pathways2GO/raw/master/exchange/generated/REO.owl";
			//String reo_iri = "https://github.com/geneontology/pathways2GO/raw/master/exchange/generated/plant-REO.owl";
			OWLImportsDeclaration reoImportDeclaration = df.getOWLImportsDeclaration(IRI.create(reo_iri));
			ontman.applyChange(new AddImport(go_cam_ont, reoImportDeclaration));
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

		OWLAnnotation provider_anno = df.getOWLAnnotation(provided_by_prop, df.getOWLLiteral(base_provider));
		OWLAxiom provideraxiom = df.getOWLAnnotationAssertionAxiom(ont_iri, provider_anno);
		ontman.addAxiom(go_cam_ont, provideraxiom);

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
		rdfs_seealso = df.getOWLAnnotationProperty(OWLRDFVocabulary.RDFS_SEE_ALSO.getIRI());
		skos_exact_match = df.getOWLAnnotationProperty(IRI.create("http://www.w3.org/2004/02/skos/core#exactMatch"));
		skos_altlabel = df.getOWLAnnotationProperty(IRI.create("http://www.w3.org/2004/02/skos/core#altLabel"));
		definition = df.getOWLAnnotationProperty(IRI.create("http://purl.obolibrary.org/obo/IAO_0000115"));	
		database_cross_reference = df.getOWLAnnotationProperty(IRI.create("http://www.geneontology.org/formats/oboInOwl#hasDbXref"));	
		canonical_record = df.getOWLAnnotationProperty(IRI.create("http://geneontology.org/lego/canonical_record"));

		//Will add classes and relations as we need them now. 
		//TODO add something to validate that ids are correct..  
		//classes	
		human = df.getOWLClass(IRI.create(obo_iri+"NCBITaxon_9606"));
		catalytic_activity = df.getOWLClass(IRI.create(obo_iri+"GO_0003824"));
		binding = df.getOWLClass(IRI.create(obo_iri+"GO_0005488"));
		protein_binding = df.getOWLClass(IRI.create(obo_iri+"GO_0005515"));
		protein_complex_binding = df.getOWLClass(IRI.create(obo_iri+"GO_0044877"));	
		establishment_of_protein_localization = df.getOWLClass(IRI.create(obo_iri+"GO_0045184"));
		negative_regulation_of_molecular_function = df.getOWLClass(IRI.create(obo_iri+"GO_0044092"));
		positive_regulation_of_molecular_function = df.getOWLClass(IRI.create(obo_iri+"GO_0044093"));		
		signal_transducer_activity = df.getOWLClass(IRI.create(obo_iri+"GO_0004871"));
		transporter_activity = df.getOWLClass(IRI.create(obo_iri+"GO_0005215"));
		protein_transport = df.getOWLClass(IRI.create(obo_iri+"GO_0015031"));
		transport = df.getOWLClass(IRI.create(obo_iri+"GO_0006810"));
		union_set = df.getOWLClass(IRI.create("http://placeholder/union_set"));
		addLabel(union_set, "Unordered collection");
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
		chebi_molecular_entity = df.getOWLClass(IRI.create(obo_iri + "CHEBI_23367"));
		addLabel(chebi_molecular_entity, "molecular entity");
		//proteins and genes as they are in neo
		chebi_protein = df.getOWLClass(IRI.create(obo_iri + "CHEBI_36080"));
		addLabel(chebi_protein, "chebi protein");
		chebi_information_biomacromolecule = df.getOWLClass(IRI.create(obo_iri + "CHEBI_33695"));
		addLabel(chebi_information_biomacromolecule, "chebi information biomacromolecule"); 
		chemical_entity =df.getOWLClass(IRI.create(obo_iri+"CHEBI_24431"));
		addLabel(chemical_entity, "chemical entity");
		chebi_mrna = df.getOWLClass(IRI.create(obo_iri+"CHEBI_33699"));
		chebi_rna = df.getOWLClass(IRI.create(obo_iri+"CHEBI_33697"));
		chebi_dna = df.getOWLClass(IRI.create(obo_iri+"CHEBI_16991"));
		unfolded_protein = df.getOWLClass(IRI.create(obo_iri+"HINO_0008749"));
		addLabel(unfolded_protein, "unfolded protein");
		//part of
		part_of = df.getOWLObjectProperty(IRI.create(obo_iri + "BFO_0000050"));
		addLabel(part_of, "part of"); 
		//has part
		has_part = df.getOWLObjectProperty(IRI.create(obo_iri + "BFO_0000051"));
		addLabel(has_part, "has part");
		//has_component - use when you want to specify an exact cardinality - e.g. 5 and only 5 fingers.
		has_component = df.getOWLObjectProperty(IRI.create(obo_iri + "RO_0002180"));
		addLabel(has_component, "has component");
		//http://purl.obolibrary.org/obo/RO_0002160
		only_in_taxon = df.getOWLObjectProperty(IRI.create(obo_iri + "RO_0002160"));
		addLabel(only_in_taxon, "only in taxon");
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

		negatively_regulates = df.getOWLObjectProperty(IRI.create(obo_iri + "RO_0002212"));
		addLabel(negatively_regulates, "negatively regulates");
		positively_regulates = df.getOWLObjectProperty(IRI.create(obo_iri + "RO_0002213"));
		addLabel(positively_regulates, "positively regulateds");

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
		contributes_to = df.getOWLObjectProperty(IRI.create(obo_iri + "RO_0002326"));

		//re-usable restrictions
		taxon_human = df.getOWLObjectSomeValuesFrom(only_in_taxon, human);
	}

	//	public GoCAMReport getGoCAMReport(){
	//		GoCAMReport class_report = new GoCAMReport();
	//		for(OWLIndividual mf : EntitySearcher.getIndividuals(molecular_function, go_cam_ont)) {
	//			class_report.mf_count++;
	//			class_report.mf_labels.add(this.getaLabel((OWLEntity) mf));
	//		}
	//		for(OWLIndividual bp : EntitySearcher.getIndividuals(bp_class, go_cam_ont)) {
	//			class_report.bp_count++;
	//			class_report.bp_labels.add(this.getaLabel((OWLEntity) bp));
	//		}
	//		for(OWLIndividual cc : EntitySearcher.getIndividuals(cc_class, go_cam_ont)) {
	//			class_report.cc_count++;
	//			class_report.cc_labels.add(this.getaLabel((OWLEntity) cc));
	//		}		
	//		for(OWLIndividual complex : EntitySearcher.getIndividuals(go_complex, go_cam_ont)) {
	//			class_report.complex_count++;
	//			class_report.complex_labels.add(this.getaLabel((OWLEntity) complex));
	//		}
	//		
	//		class_report.mf_unclassified = countUnclassifiedRDF(molecular_function, go_cam_ont);
	//		class_report.bp_unclassified = countUnclassifiedRDF(bp_class, go_cam_ont);
	//		class_report.cc_unclassified = countUnclassifiedRDF(cc_class, go_cam_ont);
	//		class_report.complex_unclassified = countUnclassifiedRDF(go_complex, go_cam_ont);
	//		return class_report;
	//	}

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

	public OWLNamedIndividual makeAnnotatedIndividual(IRI iri) {
		OWLNamedIndividual i = df.getOWLNamedIndividual(iri);		
		addBasicAnnotations2Individual(iri, this.base_contributor, this.base_date, this.base_provider);
		return i;
	}

	public OWLNamedIndividual makeBridgingIndividual(IRI iri) {
		OWLNamedIndividual i = df.getOWLNamedIndividual(iri);	
		addLiteralAnnotations2Individual(iri, rdfs_comment, "I live in another model");
		return i;
	}

	OWLNamedIndividual makeUnannotatedIndividual(IRI iri) {
		OWLNamedIndividual i = df.getOWLNamedIndividual(iri);		
		return i;
	}
	private OWLNamedIndividual makeUnannotatedIndividual(String iri) {
		OWLNamedIndividual i = df.getOWLNamedIndividual(IRI.create(iri));		
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
		return anno;
	}

	public OWLAnnotation addLiteralAnnotations2Individual(IRI individual_iri, OWLAnnotationProperty prop, String value) {
		OWLAnnotation anno = df.getOWLAnnotation(prop, df.getOWLLiteral(value));
		OWLAxiom axiom = df.getOWLAnnotationAssertionAxiom(individual_iri, anno);
		ontman.addAxiom(go_cam_ont, axiom);
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

	public void addDatabaseXref(OWLEntity e, String xref_id) {
		if(xref_id==null) {
			return;
		}		
		OWLLiteral id = df.getOWLLiteral(xref_id);
		OWLAnnotation id_anno = df.getOWLAnnotation(database_cross_reference, id);
		OWLAxiom idaxiom = df.getOWLAnnotationAssertionAxiom(e.getIRI(), id_anno);
		ontman.addAxiom(go_cam_ont, idaxiom);
		return;

	}

	public void addAltLabel(OWLEntity entity, String label) {
		if(label==null) {
			return;
		}
		OWLLiteral lbl = df.getOWLLiteral(label);
		OWLAnnotation label_anno = df.getOWLAnnotation(skos_altlabel, lbl);
		OWLAxiom labelaxiom = df.getOWLAnnotationAssertionAxiom(entity.getIRI(), label_anno);
		ontman.addAxiom(go_cam_ont, labelaxiom);
		return;
	}

	public void addSeeAlso(OWLEntity entity, String related) {
		if(related==null) {
			return;
		}
		OWLLiteral lbl = df.getOWLLiteral(related);
		OWLAnnotation label_anno = df.getOWLAnnotation(rdfs_seealso, lbl);
		OWLAxiom labelaxiom = df.getOWLAnnotationAssertionAxiom(entity.getIRI(), label_anno);
		ontman.addAxiom(go_cam_ont, labelaxiom);
		return;
	}

	public void addLabel(OWLEntity entity, String label) {
		if(label==null) {
			return;
		}
		OWLLiteral lbl = df.getOWLLiteral(label);
		OWLAnnotation label_anno = df.getOWLAnnotation(rdfs_label, lbl);
		OWLAxiom labelaxiom = df.getOWLAnnotationAssertionAxiom(entity.getIRI(), label_anno);
		ontman.addAxiom(go_cam_ont, labelaxiom);
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
	public static IRI makeRandomIri(String model_base_id) {
		String root = base_iri;
		if(model_base_id!=null) {
			root = base_iri+model_base_id+"/";
		}
		String iri = root+UUID.randomUUID();		
		return IRI.create(iri);
	}

	public static String makeGoCamifiedIRIstring(String model_base_id, String entity_id) {
		if(entity_id==null) {
			entity_id = UUID.randomUUID().toString();	
		}
		String iri = base_iri+model_base_id+"/"+entity_id;
		return iri;
	}

	public static IRI makeGoCamifiedIRI(String model_base_id, String entity_id) {
		String iri = makeGoCamifiedIRIstring(model_base_id, entity_id);
		return IRI.create(iri);
	}
	/**
	 * Given a set of reference identifiers, the pieces of a triple, and an evidence class, create an evidence individual for each reference, 
	 * create a corresponding OWLAnnotation entity, make the triple along with all the annotations as evidence.  
	 * @param source
	 * @param prop
	 * @param target
	 * @param ids
	 * @param evidence_class
	 * @param namespace_prefix (e.g. PMID)
	 */
	OWLAxiom addRefBackedObjectPropertyAssertion(OWLNamedIndividual source, OWLObjectProperty prop, OWLNamedIndividual target, 
			Set<String> ids, OWLClass evidence_class, String namespace_prefix, Set<OWLAnnotation> other_annotations, String model_id) {
		OWLObjectPropertyAssertionAxiom add_prop_axiom = null;
		Set<OWLAnnotation> annos = new HashSet<OWLAnnotation>();
		if(other_annotations!=null) {
			annos.addAll(other_annotations);
		}
		annos.addAll(getDefaultAnnotations());//prepare the database annotations like pubmed ids 
		if(ids!=null) {			
			for(String id : ids) {
				//	IRI anno_iri = makeEntityHashIri(source.hashCode()+"_"+prop.hashCode()+"_"+target.hashCode()+"_"+namespace_prefix+"_"+id);
				IRI anno_iri = makeRandomIri(model_id);
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




	public void addObjectPropertyAssertion(OWLIndividual source, OWLObjectProperty prop, OWLIndividual target, Set<OWLAnnotation> annotations) {
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

	void addSubClassAssertion(OWLClass child, OWLClassExpression parent) {
		addSubclassAssertion(child, parent, null);
	}

	public void addSubclassAssertion(OWLClass child, OWLClassExpression parent, Set<OWLAnnotation> annotations) {
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
	public void addTypeAssertion(OWLNamedIndividual individual, OWLClassExpression type) {
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
	public static QRunner getQRunnerForTboxInference(Set<String> tbox_files) throws OWLOntologyCreationException {
		System.out.println("building tbox rule base for arachne");
		OWLOntologyManager tman = OWLManager.createOWLOntologyManager();
		List<OWLOntology> tboxes = new ArrayList<OWLOntology>();
		for(String tbox_file : tbox_files) {
			OWLOntology tbox = tman.loadOntologyFromOntologyDocument(new File(tbox_file));	
			tboxes.add(tbox);
		}
		boolean add_inferences = true;
		boolean add_property_definitions = false; boolean add_class_definitions = false;
		QRunner qrunner = new QRunner(tboxes, null, add_inferences, add_property_definitions, add_class_definitions);
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

	//TODO this is super slow.  Find a new pattern.
	void applyAnnotatedTripleRemover(IRI subject, IRI predicate, IRI object) {
		OWLOntologyWalker walker = new OWLOntologyWalker(Collections.singleton(go_cam_ont));
		UpdateAnnotationsVisitor updater = new UpdateAnnotationsVisitor(walker, subject, predicate, object);
		walker.walkStructure(updater); 
		if(updater.getAxioms()!=null&&updater.getAxioms().size()>0) {
			for(OWLAxiom a : updater.getAxioms()) {
				ontman.removeAxiom(go_cam_ont, a);
			}
			for(OWLAnnotation a : updater.getAnnotations()) {
				OWLAnnotationProperty p = a.getProperty();
				if(p.equals(evidence_prop)) {
					//then we are looking at the Evidence instance as the target of the property
					OWLAnnotationValue v = a.getValue();
					OWLNamedIndividual anno_entity = df.getOWLNamedIndividual(v.asIRI().get());
					deleteOwlEntityAndAllReferencesToIt(anno_entity);
				}
			}
		}
	}

	//TODO explore whether something like this is faster
	//https://stackoverflow.com/questions/46860119/deleting-specific-class-and-axioms-in-owlapi
	//OWLEntityRemover remover = new OWLEntityRemover(Collections.singleton(ontology)); currentClass.accept(remover); manager.applyChanges(remover.getChanges()); 
	void deleteOwlEntityAndAllReferencesToIt(OWLEntity e) {	
		Collection<OWLAnnotationAssertionAxiom> node_annotations = EntitySearcher.getAnnotationAssertionAxioms(e.getIRI(), this.go_cam_ont);
		if(node_annotations!=null) {
			for (OWLAnnotationAssertionAxiom annAx : node_annotations) {
				ontman.removeAxiom(go_cam_ont, annAx);
			}
		}
		Collection<OWLAxiom> referencing_axioms = EntitySearcher.getReferencingAxioms(e, this.go_cam_ont);
		if(referencing_axioms !=null) {
			for (OWLAxiom aAx : referencing_axioms) {
				//if it references a location node, zap that one too.
				for(OWLObjectProperty prop : aAx.getObjectPropertiesInSignature()) {
					if(prop.equals(located_in)) {
						OWLObjectPropertyAssertionAxiom o = (OWLObjectPropertyAssertionAxiom)aAx;
						OWLNamedIndividual i = o.getObject().asOWLNamedIndividual();
						if(!i.equals(e)) { //already in process of deleting
							deleteOwlEntityAndAllReferencesToIt(i);
						}
					}
				}
				//delete evidence nodes associated with axioms discussing this entity
				for(OWLAnnotation anno : aAx.getAnnotations()) {
					if(anno.getProperty().equals(evidence_prop)) {
						OWLNamedIndividual evidence = df.getOWLNamedIndividual(anno.getValue().asIRI().get());
						deleteOwlEntityAndAllReferencesToIt(evidence);
					}
				}
				//now remove the axiom
				ontman.removeAxiom(go_cam_ont, aAx);
			}
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
	RuleResults applySparqlRules(String model_id) {

		RuleResults r = new RuleResults();
		//NOTE that the order these are run matters.
		r = inferTransportProcess(model_id, r);	//must be run before occurs_in and before deleteLocations	 
		r = inferOccursInFromEntityLocations(model_id, r);
		//This is turned off based on discussions May 8, 2018
		//we will now only be capturing location information in the form of occurs_in statements on reactions.
		//Where reaction location is ambiguous, e.g. entities are found at multiple sites and no active unit is known, no locations will be recording in the go-cam
		//r = addEntityLocationsForAmbiguousReactions(model_id, r); //must run after occurs_in and before deleteLocations
		r = inferRegulatesViaOutputRegulates(model_id, r);
		//This is turned off because it produces too many false positives for a direct conversion.  
		//it is worth keeping around as it does succeed enough times to be useful feedback for Reactome
		//see https://github.com/geneontology/pathways2GO/issues/62  
		//r = inferNegativeRegulationByBinding(model_id, r);
		r = inferRegulatesViaOutputEnables(model_id, r);
		r = inferProvidesInput(model_id, r);
		r = convertEntityRegulatorsToBindingFunctions(model_id, r);
		deleteLocations();
		cleanOutUnconnectedNodes();
		return r;
	}

	/**
	 * Infer Transport reactions
	 * If a reaction has not been provided with an rdf:type 
	 * and the input entities are the same as the output entities
	 * and the input entities have different locations from the output entities
	 * then add has_target_end_location and has_target_start_location attributes to the reaction node
	 * 
	 * if the reaction is enabled by something
	 * and the entities are proteins
	 * then tag it as rdf:type 'protein transport'
	 * else tag it as 'transport' 
	 * See: 'TCF dependent signaling in response to WNT' https://reactome.org/PathwayBrowser/#/R-HSA-201681&SEL=R-HSA-201669&PATH=R-HSA-162582,R-HSA-195721
	 * (1 inference for reaction 'Beta-catenin translocates to the nucleus'
	 *  Downstream dependency alert: do this before enabler inference step below since we don't want that rule to fire on transport reactions
	 */	
	private RuleResults inferTransportProcess(String model_id, RuleResults r) {
		String transport_rule = "transport";
		Integer transport_count = r.checkInitCount(transport_rule, r);
		Set<String> transport_pathways = r.checkInitPathways(transport_rule, r);		
		Set<InferredTransport> transports = qrunner.findTransportReactions();		
		if(transports.size()>0) {
			transport_count+=transports.size();
			Set<String> transport_reactions = new HashSet<String>();
			for(InferredTransport transport_reaction : transports) {
				if(!transport_reactions.add(transport_reaction.reaction_uri)){
					//should only end up with one per reaction.. make sure
					continue;
				}
				transport_pathways.add(transport_reaction.pathway_uri);
				OWLNamedIndividual reaction = this.makeAnnotatedIndividual(transport_reaction.reaction_uri);
				OWLClassAssertionAxiom classAssertion = df.getOWLClassAssertionAxiom(molecular_function, reaction);
				ontman.removeAxiom(go_cam_ont, classAssertion);
				//add transport type
				//but only if there is an enabler
				String explain = "No type is assigned because the reaction did not have one assigned.  This is likely some kind of transport function but without something to enable it, the GO will not make a claim about what it might be.";
				if(transport_reaction.enabler_uri!=null) {
					if(transport_reaction.thing_type_uri.contains("uniprot")) {
						addTypeAssertion(reaction, protein_transport);
						explain = "This reaction represents the process of transporting a protein from one location to another, hence the GO-CAM conversion automatically assigned the type 'protein transport'";
					}else {
						addTypeAssertion(reaction, transport);
						explain = "This reaction represents the process of transporting something aside from a protein (e.g. a complex) from one location to another, hence the GO-CAM conversion automatically assigned the type 'protein transport'";
					}
				}else {
					addTypeAssertion(reaction, GoCAM.molecular_function);
					System.out.println("No enabler for transport reaction "+getaLabel(reaction));
				}
				addLiteralAnnotations2Individual(reaction.getIRI(), rdfs_comment, explain);
				//record what moved where so the classifier can see it properly
				OWLNamedIndividual start_loc = makeAnnotatedIndividual(makeRandomIri(model_id));
				OWLClass start_loc_type = df.getOWLClass(IRI.create(transport_reaction.input_loc_class_uri));
				addTypeAssertion(start_loc, start_loc_type);				
				OWLNamedIndividual end_loc = makeAnnotatedIndividual(makeRandomIri(model_id));
				OWLClass end_loc_type = df.getOWLClass(IRI.create(transport_reaction.output_loc_class_uri));
				addTypeAssertion(end_loc, end_loc_type);				
				//add relations to enable deeper classification based on OWL axioms in BP branch
				Set<OWLAnnotation> annos = getDefaultAnnotations();
				String explain1 = "This relation was inferred because something that was an input to the reaction started out in the target location "+getaLabel(end_loc_type)
				+ " and then, as a consequence of the reaction/process was transported to another location.";
				annos.add(df.getOWLAnnotation(rdfs_comment, df.getOWLLiteral(explain1)));				
				addRefBackedObjectPropertyAssertion(reaction, has_target_start_location, start_loc, Collections.singleton(model_id), GoCAM.eco_inferred_auto, "Reactome", annos, model_id);
				String explain2 = "This relation was inferred because something that was an input to the reaction started one location "
						+ " and then, as a consequence of the reaction/process was transported to the target end location "+getaLabel(end_loc_type);
				Set<OWLAnnotation> annos2 = getDefaultAnnotations();
				annos2.add(df.getOWLAnnotation(rdfs_comment, df.getOWLLiteral(explain2)));
				addRefBackedObjectPropertyAssertion(reaction, has_target_end_location, end_loc, Collections.singleton(model_id), GoCAM.eco_inferred_auto, "Reactome", annos2, model_id);
			}
			//enabled by needs to know if there are any transport reactions as these should not be included
			//hence reload graph from ontology
			qrunner = new QRunner(go_cam_ont);
		}
		r.rule_hitcount.put(transport_rule, transport_count);
		r.rule_pathways.put(transport_rule, transport_pathways);
		return r;
	}

	/**
	 * Rule: infer occurs_in relations
	 * 
For reactions with multiple entity locations, that are enabled by something, 
  the reaction occurs_in the location of the enabler. 
Other location information is dropped.
For reactions where all entities are in one location, the reaction occurs_in that location
For reactions with multiple entity locations and no enabler, do not assign any occurs_in relation.
	 */
	private RuleResults inferOccursInFromEntityLocations(String model_id, RuleResults r) {
		String i_o_rule = "occurs_in";
		Integer i_o_count = r.checkInitCount(i_o_rule, r);
		Set<String> i_o_pathways = r.checkInitPathways(i_o_rule, r);
		Set<InferredOccursIn> inferred_occurs = qrunner.findOccursInReaction();
		if(inferred_occurs.isEmpty()) {
			System.out.println("No occurs in");
		}else {
			i_o_count+=inferred_occurs.size();			
			for(InferredOccursIn o : inferred_occurs) {
				i_o_pathways.add(o.pathway_uri);
				OWLNamedIndividual reaction = this.makeUnannotatedIndividual(IRI.create(o.reaction_uri));
				//location of enabler trumps other conditions
				//if all the same, then keep
				boolean keep_occurs = false;
				String reason = "";
				String enabled_by_uri = obo_iri + "RO_0002333";
				Set<String> occurs_location_uris = new HashSet<String>();
				for(String relation_uri : o.relation_locations.keySet()) {
					if(relation_uri.equals(enabled_by_uri)) {
						occurs_location_uris.addAll(o.relation_locations.get(relation_uri));
						//if the enabling complex occurs in multiple locations, do not assign an occurs_in relation
						if(occurs_location_uris.size()==1) {
							keep_occurs = true;
							reason = "This relation was asserted based on the location of the enabling molecule. ";
						}else {
							System.out.println(o.reaction_uri+" has an enabler with multiple location annotations.  Maybe let the data source know this is weird.  Ignoring these for go-cam.");
						}
					}
				}
				if((!keep_occurs)&&o.location_type_uris.size()==1) {	
					keep_occurs = true;
					reason = "This relation was asserted because all entities involved in the reaction are in the same location. ";
					occurs_location_uris.add(o.location_type_uris.iterator().next());
				}
				//make the occurs in assertion
				if(keep_occurs) {
					for(String occurs_location_uri : occurs_location_uris) {
						OWLClass location_class = df.getOWLClass(IRI.create(occurs_location_uri));
						Set<OWLAnnotation> annos = getDefaultAnnotations();
						annos.add(df.getOWLAnnotation(rdfs_comment, df.getOWLLiteral(reason)));		
						OWLNamedIndividual placeInstance = df.getOWLNamedIndividual(GoCAM.makeRandomIri(model_id));
						addTypeAssertion(placeInstance, location_class);
						addRefBackedObjectPropertyAssertion(reaction, GoCAM.occurs_in, placeInstance, Collections.singleton(model_id), GoCAM.eco_imported_auto, "Reactome", annos, model_id);
					}
				}
			}
		}
		r.rule_hitcount.put(i_o_rule, i_o_count);
		r.rule_pathways.put(i_o_rule, i_o_pathways);	
		qrunner = new QRunner(go_cam_ont); 
		return r;
	}



	private RuleResults addEntityLocationsForAmbiguousReactions(String model_id, RuleResults r) {	
		String part_of_rule = "add_entity_part_of_locations";
		Integer part_of_count = r.checkInitCount(part_of_rule, r);
		//TODO not using this at the moment for anything so leaving it out of this rule for now
		//Set<String> part_of_pathways = r.checkInitPathways(part_of_rule, r);
		Map<String, String> entity_location = qrunner.findEntityLocationsForAmbiguousReactions();
		if(entity_location.isEmpty()) {
			System.out.println("No ambiguously located reactions");
		}else {
			part_of_count+=entity_location.size();			
			for(String entity_uri : entity_location.keySet()) {
				String location_uri = entity_location.get(entity_uri);			
				OWLNamedIndividual entity = this.makeUnannotatedIndividual(IRI.create(entity_uri));
				OWLNamedIndividual location = this.makeUnannotatedIndividual(IRI.create(location_uri));
				//make the part_of assertion
				Set<OWLAnnotation> annos = getDefaultAnnotations();
				String reason = "Physical entities are directly assigned part of cellular location information when "
						+ "the reaction they participate in makes use of entities in multiple locations.";
				annos.add(df.getOWLAnnotation(rdfs_comment, df.getOWLLiteral(reason)));		
				OWLNamedIndividual new_location = makeAnnotatedIndividual(makeRandomIri(model_id));
				for(OWLClassExpression t : EntitySearcher.getTypes(location, go_cam_ont)) {
					addTypeAssertion(new_location, t);
				}			
				addRefBackedObjectPropertyAssertion(entity, GoCAM.part_of, new_location, Collections.singleton(model_id), GoCAM.eco_imported_auto, "Reactome", annos, model_id);
			}
		}
		r.rule_hitcount.put(part_of_rule, part_of_count);	
		qrunner = new QRunner(go_cam_ont); 
		return r;
	}


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
	private RuleResults inferRegulatesViaOutputRegulates(String model_id, RuleResults r) {
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
			this.addRefBackedObjectPropertyAssertion(r1, o, r2, Collections.singleton(model_id), GoCAM.eco_inferred_auto, "Reactome", annos, model_id);
			//delete the entity regulates process relation 
			applyAnnotatedTripleRemover(entity.getIRI(), o.getIRI(), r2.getIRI());

		}
		r.rule_hitcount.put(regulator_rule, regulator_count);
		r.rule_pathways.put(regulator_rule, regulator_pathways);
		qrunner = new QRunner(go_cam_ont); 
		return r;
	}

	/**
	 * Rule 6: Regulator 2: negative regulation by binding
	 * If reaction2 is enabled_by entity1
	 * And reaction1 has entity1 as an input
	 * And reaction1 has entity2 as an input
	 * And reaction1 has a complex containing entity1 and entity2 as output
	 * Then infer that reaction1 directly_negatively_regulates reaction2
	 * (by binding up the entity that enables reaction2 to happen).
	 * 
	 *  See 'Signaling by BMP' https://reactome.org/content/detail/R-HSA-201451 
	 * 
	 */
	private RuleResults inferNegativeRegulationByBinding(String model_id, RuleResults r) {

		String regulator_rule_2 = "regulation_by_sequestration";
		Integer regulator_count_2 = r.checkInitCount(regulator_rule_2, r);
		Set<String> regulator_pathways_2 = r.checkInitPathways(regulator_rule_2, r);

		Set<InferredRegulator> ir2_neg = qrunner.getInferredRegulationBySequestration();
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
			//directly negatively regulates RO_0002630 
			String explain = "The relation "+r2_label+" "+o_label+" "+r1_label+" was inferred because:\n "+
					r2_label+" has inputs A and B, "+r2_label+" has output A/B complex, and " + 
					r1_label+" is enabled by B. See and comment on mapping rules at https://tinyurl.com/y8jctxxv ";
			annos.add(df.getOWLAnnotation(rdfs_comment, df.getOWLLiteral(explain)));
			//this.addObjectPropertyAssertion(r1, o, r2, annos);
			this.addRefBackedObjectPropertyAssertion(r2, o, r1, Collections.singleton(model_id), GoCAM.eco_inferred_auto, "Reactome", annos, model_id);
			//			System.out.println("reg2 "+r1+" "+o+" "+r2);
		}	
		r.rule_hitcount.put(regulator_rule_2, regulator_count_2);
		r.rule_pathways.put(regulator_rule_2, regulator_pathways_2);
		qrunner = new QRunner(go_cam_ont); 
		return r;
	}

	private RuleResults inferRegulatesViaOutputEnables(String model_id, RuleResults r) {
		/**
		 * Regulator rule 3.
		 * reaction1 causally upstream of reaction2 
		 * reaction1 has an output that is the enabler of reaction 2
		 */
		String regulator_rule_3 = "regulator_3";
		Integer regulator_count_3 = r.checkInitCount(regulator_rule_3, r);
		Set<String> regulator_pathways_3 = r.checkInitPathways(regulator_rule_3, r);

		Set<InferredRegulator> ir3_pos = qrunner.getInferredRegulatorsQ3();
		regulator_count_3+=ir3_pos.size();
		for(InferredRegulator ir : ir3_pos) {
			regulator_pathways_3.add(ir.pathway_uri);
			//create ?reaction2 obo:RO_0002333 ?input
			OWLNamedIndividual r1 = this.makeAnnotatedIndividual(ir.reaction1_uri);
			OWLNamedIndividual r2 = this.makeAnnotatedIndividual(ir.reaction2_uri);
			OWLObjectProperty o = df.getOWLObjectProperty(IRI.create(ir.prop_uri));
			String r1_label = "'"+this.getaLabel(r1)+"'";
			String r2_label = "'"+this.getaLabel(r2)+"'";
			String o_label = "'"+this.getaLabel(o)+"'";
			Set<OWLAnnotation> annos = getDefaultAnnotations();
			String explain = "(rule:reg3) The relation "+r1_label+" "+o_label+" "+r2_label+" was inferred because:\n "+
					"reaction1 has an output that is the enabler of reaction 2. See and comment on mapping rules at https://tinyurl.com/y8jctxxv ";
			annos.add(df.getOWLAnnotation(rdfs_comment, df.getOWLLiteral(explain)));
			this.addRefBackedObjectPropertyAssertion(r1, o, r2, Collections.singleton(model_id), GoCAM.eco_inferred_auto, "Reactome", annos, model_id);
			applyAnnotatedTripleRemover(r1.getIRI(), causally_upstream_of.getIRI(), r2.getIRI());
		}	
		r.rule_hitcount.put(regulator_rule_3, regulator_count_3);
		r.rule_pathways.put(regulator_rule_3, regulator_pathways_3);
		//don't re-initialize the query model yet.  If we remove the causally upstream of relation, the next rule can't fire
		//and it is possible for a reaction to both catalyze and provide input for a another reaction.  
		//qrunner = new QRunner(go_cam_ont); 
		return r;
	}

	private RuleResults inferProvidesInput(String model_id, RuleResults r) {
		/*
		 * provides input for inference
		 */
		String provides_input_rule = "provides_input";
		Integer provides_input_count = r.checkInitCount(provides_input_rule, r);
		Set<String> provides_input_pathways = r.checkInitPathways(provides_input_rule, r);

		Set<InferredRegulator> provides_input = qrunner.getInferredInputProviders();
		provides_input_count+=provides_input.size();
		for(InferredRegulator ir : provides_input) {
			provides_input_pathways.add(ir.pathway_uri);
			//create ?reaction2 obo:RO_0002333 ?input
			OWLNamedIndividual r1 = this.makeAnnotatedIndividual(ir.reaction1_uri);
			OWLNamedIndividual r2 = this.makeAnnotatedIndividual(ir.reaction2_uri);
			OWLObjectProperty o = df.getOWLObjectProperty(IRI.create(ir.prop_uri));
			String r1_label = "'"+this.getaLabel(r1)+"'";
			String r2_label = "'"+this.getaLabel(r2)+"'";
			String o_label = "'"+this.getaLabel(o)+"'";
			Set<OWLAnnotation> annos = getDefaultAnnotations();
			String explain = "(rule:provides_input) The relation "+r1_label+" "+o_label+" "+r2_label+" was inferred because:\n "+
					"reaction1 has an output that is an input of reaction 2. See and comment on mapping rules at https://tinyurl.com/y8jctxxv ";
			annos.add(df.getOWLAnnotation(rdfs_comment, df.getOWLLiteral(explain)));
			this.addRefBackedObjectPropertyAssertion(r1, o, r2, Collections.singleton(model_id), GoCAM.eco_inferred_auto, "Reactome", annos, model_id);
			applyAnnotatedTripleRemover(r1.getIRI(), causally_upstream_of.getIRI(), r2.getIRI());
		}	
		r.rule_hitcount.put(provides_input_rule, provides_input_count);
		r.rule_pathways.put(provides_input_rule, provides_input_pathways);
		qrunner = new QRunner(go_cam_ont); 
		return r;
	}

	/**
	 * Rule: entity involved in regulation of function 
+-_regulation_of_BP has_participant E1
+-_regulation_of_BP +-_regulates R
+-_regulation_of_BP part_of generic_BP
generic_BP +-_regulates pathway
⇐ 	
E1 +- involved_in_regulation_of R
pathway has_part R
	 */
	private RuleResults convertEntityRegulatorsToRegulatorFunctionsDeprecated(String model_id, RuleResults r) {		 
		String entity_regulator_rule = "entity_regulator_1";
		Integer entity_regulator_count = r.checkInitCount(entity_regulator_rule, r);
		Set<String> entity_regulator_pathways = r.checkInitPathways(entity_regulator_rule, r);
		Set<InferredRegulator> ers = qrunner.getInferredAnonymousRegulators();

		Map<String, Set<InferredRegulator>> reaction_regulators = new HashMap<String, Set<InferredRegulator>>();
		for(InferredRegulator er : ers) {
			Set<InferredRegulator> reg = reaction_regulators.get(er.reaction1_uri);
			if(reg == null) {
				reg = new HashSet<InferredRegulator>();
			}
			reg.add(er);
			reaction_regulators.put(er.reaction1_uri, reg);
		}

		entity_regulator_count+=ers.size();
		boolean reaction_neg_regulator = false;
		boolean reaction_pos_regulator = false;
		for(String reaction_uri : reaction_regulators.keySet()) {
			OWLNamedIndividual reaction = makeAnnotatedIndividual(reaction_uri);
			//just do this once per reaction, most is redundant because of flat response structure
			Set<InferredRegulator> regs = reaction_regulators.get(reaction_uri);
			if(!regs.isEmpty()) {
				//check if we need 2 bp nodes or 1
				for(InferredRegulator reg : regs) {
					if(reg.prop_uri.equals("http://purl.obolibrary.org/obo/RO_0002429")) {
						reaction_pos_regulator = true;
					}else {
						reaction_neg_regulator = true;
					}
				}
				InferredRegulator base = regs.iterator().next();
				entity_regulator_pathways.add(base.pathway_uri);
				OWLNamedIndividual pathway = makeAnnotatedIndividual(base.pathway_uri);
				//make a generic bp node to link to main pathway/process
				OWLNamedIndividual neg_bp_node = null;
				OWLNamedIndividual pos_bp_node = null;
				//make a regulatory BP node
				OWLNamedIndividual neg_reg_bp_node = null;
				OWLNamedIndividual pos_reg_bp_node = null;
				Set<OWLAnnotation> annos1 = getDefaultAnnotations();
				String comment = "This relation was asserted because a physical entity (see participants) was asserted to regulate a reaction.  This relation is part of the translation of that statement into the GO-CAM model.";
				annos1.add(df.getOWLAnnotation(rdfs_comment, df.getOWLLiteral(comment)));
				if(reaction_pos_regulator) {
					pos_reg_bp_node = makeAnnotatedIndividual(makeRandomIri(model_id));
					addTypeAssertion(pos_reg_bp_node, positive_regulation_of_molecular_function);
					pos_bp_node = makeAnnotatedIndividual(makeRandomIri(model_id));
					addTypeAssertion(pos_bp_node, bp_class);
					addRefBackedObjectPropertyAssertion(pos_reg_bp_node, part_of, pos_bp_node, Collections.singleton(model_id), GoCAM.eco_inferred_auto, "Reactome", getDefaultAnnotations(), model_id);
					addRefBackedObjectPropertyAssertion(pos_reg_bp_node, GoCAM.directly_positively_regulates, reaction, Collections.singleton(model_id), GoCAM.eco_inferred_auto, "Reactome", annos1, model_id);
					addRefBackedObjectPropertyAssertion(pos_bp_node, GoCAM.directly_positively_regulates, pathway, Collections.singleton(model_id), GoCAM.eco_inferred_auto, "Reactome", annos1, model_id);

				}
				if(reaction_neg_regulator) {
					neg_reg_bp_node = makeAnnotatedIndividual(makeRandomIri(model_id));
					neg_bp_node = makeAnnotatedIndividual(makeRandomIri(model_id));
					addTypeAssertion(neg_reg_bp_node, negative_regulation_of_molecular_function);
					addTypeAssertion(neg_bp_node, bp_class);
					addRefBackedObjectPropertyAssertion(neg_reg_bp_node, part_of, neg_bp_node, Collections.singleton(model_id), GoCAM.eco_inferred_auto, "Reactome", getDefaultAnnotations(), model_id);				
					addRefBackedObjectPropertyAssertion(neg_reg_bp_node, GoCAM.directly_negatively_regulates, reaction, Collections.singleton(model_id), GoCAM.eco_inferred_auto, "Reactome", annos1, model_id);
					addRefBackedObjectPropertyAssertion(neg_bp_node, GoCAM.directly_negatively_regulates, pathway, Collections.singleton(model_id), GoCAM.eco_inferred_auto, "Reactome", annos1, model_id);
				}
				//now get the actual regulating entities
				for(InferredRegulator er : reaction_regulators.get(reaction_uri)) {
					Set<OWLAnnotation> annos = getDefaultAnnotations();
					OWLObjectProperty prop = GoCAM.directly_negatively_regulates;
					OWLNamedIndividual original_regulator = makeAnnotatedIndividual(er.entity_uri);
					//OWLNamedIndividual regulator = cloneIndividual(er.entity_uri, model_id);

					String regulator_label = getaLabel(original_regulator);
					OWLObjectProperty prop_for_deletion = GoCAM.involved_in_negative_regulation_of;
					if(er.prop_uri.equals("http://purl.obolibrary.org/obo/RO_0002429")) {
						prop = GoCAM.directly_positively_regulates;
						String reg = " is involved in positive regulation of ";
						String explain = "The relation was inferred because "+regulator_label
								+reg+" the reaction.  See and comment on mapping rules at https://tinyurl.com/y8jctxxv ";
						annos.add(df.getOWLAnnotation(rdfs_comment, df.getOWLLiteral(explain)));
						prop_for_deletion = GoCAM.involved_in_positive_regulation_of;					
						//hook the entities up to the regulating process
						addRefBackedObjectPropertyAssertion(pos_reg_bp_node, has_participant, original_regulator, Collections.singleton(model_id), GoCAM.eco_inferred_auto, "Reactome", annos, model_id);
					}else {
						String reg = " is involved in negative regulation of ";
						String explain = "The relation was inferred because "+regulator_label
								+reg+" the reaction.  See and comment on mapping rules at https://tinyurl.com/y8jctxxv ";
						annos.add(df.getOWLAnnotation(rdfs_comment, df.getOWLLiteral(explain)));
						//hook the entities up to the regulating process
						addRefBackedObjectPropertyAssertion(neg_reg_bp_node, has_participant, original_regulator, Collections.singleton(model_id), GoCAM.eco_inferred_auto, "Reactome", annos, model_id);
					}
					//delete the original entity regulates process relation 
					//original
					applyAnnotatedTripleRemover(original_regulator.getIRI(), prop_for_deletion.getIRI(), reaction.getIRI());
					//cloned
					//applyAnnotatedTripleRemover(regulator.getIRI(), prop_for_deletion.getIRI(), reaction.getIRI());

				}
			}
		}
		r.rule_hitcount.put(entity_regulator_rule, entity_regulator_count);
		r.rule_pathways.put(entity_regulator_rule, entity_regulator_pathways);
		qrunner = new QRunner(go_cam_ont); 
		return r;
	}

	/**
	 * Rule: entity involved in regulation of function 
Binding has_input E1
Binding has_input E2
Binding +-_regulates R
Binding part_of +-_regulation_of BP
⇐ 	
E1 +- involved_in_regulation_of R
R enabled_by E2
BP has_part R
	 * @return 
	 */
	private RuleResults convertEntityRegulatorsToBindingFunctions(String model_id, RuleResults r) {		 
		String entity_regulator_rule = "entity_regulator_1";
		Integer entity_regulator_count = r.checkInitCount(entity_regulator_rule, r);
		Set<String> entity_regulator_pathways = r.checkInitPathways(entity_regulator_rule, r);
		Set<InferredRegulator> ers = qrunner.getInferredAnonymousRegulators();

		Map<String, Set<InferredRegulator>> reaction_regulators = new HashMap<String, Set<InferredRegulator>>();
		for(InferredRegulator er : ers) {
			Set<InferredRegulator> reg = reaction_regulators.get(er.reaction1_uri);
			if(reg == null) {
				reg = new HashSet<InferredRegulator>();
			}
			reg.add(er);
			reaction_regulators.put(er.reaction1_uri, reg);
		}

		entity_regulator_count+=ers.size();
		for(String reaction_uri : reaction_regulators.keySet()) {
			OWLNamedIndividual reaction = makeUnannotatedIndividual(reaction_uri);
			//just do this once per reaction, most is redundant because of flat response structure
			Set<InferredRegulator> regs = reaction_regulators.get(reaction_uri);
			if(!regs.isEmpty()) {
				InferredRegulator base = regs.iterator().next();
				entity_regulator_pathways.add(base.pathway_uri);
				OWLNamedIndividual pathway = makeUnannotatedIndividual(base.pathway_uri);
				//now get the actual regulating entities
				Set<String> regulating_entities = new HashSet<String>();
				for(InferredRegulator er : reaction_regulators.get(reaction_uri)) {
					//catch cases where there may be more than one of the same entity
					//avoid making multiple redundant binding functions for the same entity
					if(!regulating_entities.add(er.entity_uri)) {
						continue;
					}
					Set<OWLAnnotation> annos = getDefaultAnnotations();
					OWLNamedIndividual regulator = makeUnannotatedIndividual(er.entity_uri);

					OWLObjectProperty prop_for_deletion = GoCAM.involved_in_negative_regulation_of;
					OWLObjectProperty regulator_prop = GoCAM.negatively_regulates;
					String explain = "The relation was added to account for an assertion about an entity regulating (not 'enabling') the target reaction .  See and comment on mapping rules at https://tinyurl.com/y8jctxxv . See entity_regulator_1";
					if(er.prop_uri.equals("http://purl.obolibrary.org/obo/RO_0002429")) {
						//String reg = " is involved in positive regulation of ";
						annos.add(df.getOWLAnnotation(rdfs_comment, df.getOWLLiteral(explain)));
						prop_for_deletion = GoCAM.involved_in_positive_regulation_of;	
						regulator_prop = GoCAM.positively_regulates;
					}else {
						//String reg = " is involved in negative regulation of ";
						annos.add(df.getOWLAnnotation(rdfs_comment, df.getOWLLiteral(explain)));
					}
					//make the MF node
					OWLNamedIndividual binding_node = makeAnnotatedIndividual(makeRandomIri(model_id));
					addTypeAssertion(binding_node, binding);
					addRefBackedObjectPropertyAssertion(binding_node, has_input, regulator, Collections.singleton(model_id), GoCAM.eco_inferred_auto, "Reactome", annos, model_id);
					addRefBackedObjectPropertyAssertion(binding_node, regulator_prop, reaction, Collections.singleton(model_id), GoCAM.eco_inferred_auto, "Reactome", annos, model_id);

					if(er.enabler_uri!=null) {
						OWLNamedIndividual enabler = cloneIndividual(er.enabler_uri, model_id, true, true, false, true);
						addRefBackedObjectPropertyAssertion(binding_node, has_input, enabler, Collections.singleton(model_id), GoCAM.eco_inferred_auto, "Reactome", annos, model_id);
						//delete the cloned enable relation
						applyAnnotatedTripleRemover(reaction.getIRI(), enabled_by.getIRI(), enabler.getIRI());
					}
					//make a BP node
					OWLNamedIndividual bp_node = makeAnnotatedIndividual(makeRandomIri(model_id));
					addTypeAssertion(bp_node, bp_class);
					addRefBackedObjectPropertyAssertion(binding_node, part_of, bp_node, Collections.singleton(model_id), GoCAM.eco_inferred_auto, "Reactome", annos, model_id);
					addRefBackedObjectPropertyAssertion(bp_node, regulator_prop, pathway, Collections.singleton(model_id), GoCAM.eco_inferred_auto, "Reactome", annos, model_id);					
					//delete the original entity regulates process relation 
					applyAnnotatedTripleRemover(regulator.getIRI(), prop_for_deletion.getIRI(), reaction.getIRI());
				}
			}
		}
		r.rule_hitcount.put(entity_regulator_rule, entity_regulator_count);
		r.rule_pathways.put(entity_regulator_rule, entity_regulator_pathways);
		qrunner = new QRunner(go_cam_ont); 
		return r;
	}

	private void deleteLocations() {
		System.out.println("Starting delete locations");
		/**
		 * Rule Noctua 1 : Delete all location assertions if for noctua curation
		 * Note that in some cases, location assertions interact with occurs_in property chains resulting in inconsistencies..
		 * Doing this in ontology instead of just RDF (go_cam.qrunner.deleteEntityLocations();) 
		 * so we can check it with reasoner
		 */
		//TODO this is slow.  To speed up handle location and occurs in (above) in first layer of code (non-sparql)
		for(OWLObjectPropertyAssertionAxiom a : go_cam_ont.getAxioms(AxiomType.OBJECT_PROPERTY_ASSERTION)) {
			OWLObjectPropertyExpression p = a.getProperty();
			if(p.equals(located_in)) {
				OWLNamedIndividual s = a.getSubject().asOWLNamedIndividual();
				OWLNamedIndividual o = a.getObject().asOWLNamedIndividual();
				applyAnnotatedTripleRemover(s.getIRI(), located_in.getIRI(), o.getIRI());
				deleteOwlEntityAndAllReferencesToIt(o);
			}
		}			
		qrunner = new QRunner(go_cam_ont); 
		System.out.println("Eliminated 'located in' assertions");
	}


	private OWLNamedIndividual cloneIndividual(String entity_uri, String model_id, boolean clone_annotations, boolean clone_outgoing, boolean clone_incoming, boolean clone_types) {
		OWLNamedIndividual source = makeUnannotatedIndividual(entity_uri);
		return cloneIndividual(source, model_id, clone_annotations, clone_outgoing, clone_incoming, clone_types);
	}


	private OWLNamedIndividual cloneIndividual(OWLNamedIndividual source, String model_id, boolean clone_annotations, boolean clone_outgoing, boolean clone_incoming, boolean clone_types) {
		OWLNamedIndividual clone = makeUnannotatedIndividual(makeRandomIri(model_id));
		//almost always want the types
		if(clone_types) {
			Collection<OWLClassExpression> types = EntitySearcher.getTypes(source, go_cam_ont);
			Iterator<OWLClassExpression> typesi = types.iterator();
			while(typesi.hasNext()) {
				addTypeAssertion(clone, typesi.next());
			}
		}
		//annotations sometimes
		if(clone_annotations) {
			Collection<OWLAnnotationAssertionAxiom> annotation_axioms = EntitySearcher.getAnnotationAssertionAxioms(source, go_cam_ont);
			Iterator<OWLAnnotationAssertionAxiom> ai = annotation_axioms.iterator();
			while(ai.hasNext()) {
				OWLAnnotationAssertionAxiom ax = ai.next();
				OWLAnnotationAssertionAxiom aprop = (OWLAnnotationAssertionAxiom)ax;
				OWLAnnotationAssertionAxiom a_ax = df.getOWLAnnotationAssertionAxiom((OWLAnnotationSubject) clone.getIRI(), aprop.getAnnotation());
				AddAxiom addAxiom = new AddAxiom(go_cam_ont, a_ax);
				ontman.applyChange(addAxiom);
			}	
		}
		//object property axioms sometimes
		if(!clone_outgoing && ! clone_incoming) {
			return clone;
		}
		Collection<OWLAxiom> axioms = EntitySearcher.getReferencingAxioms(source, go_cam_ont);
		Iterator<OWLAxiom> i = axioms.iterator();
		while(i.hasNext()) {
			OWLAxiom ax = i.next();
			if(ax.isOfType(AxiomType.OBJECT_PROPERTY_ASSERTION)){
				OWLObjectPropertyAssertionAxiom oprop_axiom = (OWLObjectPropertyAssertionAxiom)ax;
				OWLObjectPropertyAssertionAxiom add_prop_axiom = null;
				//need to do similar clone for annotations
				Set<OWLAnnotation> source_annos = oprop_axiom.getAnnotations();
				if(clone_outgoing&&source.equals(oprop_axiom.getSubject())) {
					OWLNamedIndividual object_clone = cloneIndividual(oprop_axiom.getObject().asOWLNamedIndividual(), model_id, clone_annotations, false, false, true);//don't recurse
					add_prop_axiom = df.getOWLObjectPropertyAssertionAxiom(oprop_axiom.getProperty(), clone, object_clone, cloneAnnotations(source_annos, model_id));
				}
				else if (clone_incoming&&source.equals(oprop_axiom.getObject())) {
					OWLNamedIndividual subject_clone = cloneIndividual(oprop_axiom.getSubject().asOWLNamedIndividual(), model_id, clone_annotations, false, false, true);
					add_prop_axiom = df.getOWLObjectPropertyAssertionAxiom(oprop_axiom.getProperty(), subject_clone, clone, cloneAnnotations(source_annos, model_id));
				}
				if(add_prop_axiom !=null) {
					AddAxiom addAxiom = new AddAxiom(go_cam_ont, add_prop_axiom);
					ontman.applyChange(addAxiom);
				}
			} 
		}
		return clone;
	}

	/*
	 * for a given set of annotations (evidence blocks for go-cam assertions)
	 * clone them but give them different uris
	 */
	private Set<OWLAnnotation> cloneAnnotations(Set<OWLAnnotation> source_annos, String model_id) {
		Set<OWLAnnotation> cloned = new HashSet<OWLAnnotation>();
		for(OWLAnnotation anno : source_annos) {
			OWLAnnotationProperty anno_prop = anno.getProperty();
			OWLAnnotationValue anno_value = anno.getValue();
			if(anno_value.asLiteral().isPresent()) {
				//its just literal value, duplicate
				OWLAnnotation cloned_anno = df.getOWLAnnotation(anno_prop, anno_value);
				cloned.add(cloned_anno);
				//looking at an evidence node
			}else if(anno_value.asIRI().isPresent()) {
				OWLNamedIndividual evidence = df.getOWLNamedIndividual(anno_value.asIRI().get());
				OWLNamedIndividual cloned_evidence = cloneIndividual(evidence, model_id, true, false, false, true);
				OWLAnnotation cloned_anno = df.getOWLAnnotation(anno_prop, cloned_evidence.getIRI());
				cloned.add(cloned_anno);
			}
		}
		return cloned;
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

	void writeGoCAM_jena(String outfilename, boolean save2blazegraph, String outputformat) throws OWLOntologyStorageException, OWLOntologyCreationException, RepositoryException, RDFParseException, RDFHandlerException, IOException {
		File outfilefile = new File(outfilename);	
		//use jena export
		System.out.println("writing n triples: "+qrunner.nTriples());
		qrunner.dumpModel(outfilefile, outputformat);
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


	private Set<OWLNamedIndividual> getEvidenceNodes(){
		Set<OWLNamedIndividual> evidence_nodes = new HashSet<OWLNamedIndividual>();
		Set<OWLAxiom> all_axioms = go_cam_ont.getAxioms();
		for(OWLAxiom axiom : all_axioms) {
			Set<OWLAnnotation> all_annos = axiom.getAnnotations();
			for(OWLAnnotation anno : all_annos) {
				if(anno.getProperty().equals(evidence_prop)) {
					if(anno.getValue().asIRI().isPresent()) {
						OWLNamedIndividual a = this.makeUnannotatedIndividual(anno.getValue().asIRI().get());
						evidence_nodes.add(a);
					}				
				}
			}
		}
		return evidence_nodes;
	}

	private void cleanOutUnconnectedNodes() {
		//if the node is not referenced anywhere else then delete the node
		Set<OWLNamedIndividual> nodes = go_cam_ont.getIndividualsInSignature();
		//evidence nodes are speciall as the are only the objects of annotation assertions hence the following will not see them
		System.out.println("Starting unconnected node cleanup.  Total nodes "+nodes.size());
		Set<OWLNamedIndividual> evidence_nodes = getEvidenceNodes();
		System.out.println("Total evidence nodes "+evidence_nodes.size());
		nodes.removeAll(evidence_nodes);
		int n_removed = 0;
		for(OWLNamedIndividual node : nodes) {
			Collection<OWLAxiom> ref_axioms = EntitySearcher.getReferencingAxioms(node, go_cam_ont);
			boolean drop = true;
			Iterator<OWLAxiom> ri = ref_axioms.iterator();
			while(ri.hasNext()) {
				OWLAxiom a = ri.next();
				if(a.isOfType(AxiomType.OBJECT_PROPERTY_ASSERTION)) {
					drop = false;
					break;
				}
			}
			if(drop) {
				deleteOwlEntityAndAllReferencesToIt(node);
				n_removed++;
			}
		}
		System.out.println("removed "+n_removed);
	}




}
