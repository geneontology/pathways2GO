/**
 * 
 */
package org.geneontology.gocam.exchange;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.biopax.paxtools.model.level3.PublicationXref;
import org.biopax.paxtools.model.level3.Xref;
import org.geneontology.gocam.exchange.QRunner.InferredEnabler;
import org.geneontology.gocam.exchange.QRunner.InferredRegulator;
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
import org.semanticweb.owlapi.vocab.OWLRDFVocabulary;

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
	public static OWLAnnotationProperty title_prop, contributor_prop, date_prop, 
	state_prop, evidence_prop, provided_by_prop, x_prop, y_prop, rdfs_label, rdfs_comment, source_prop;
	public static OWLObjectProperty part_of, has_part, has_input, has_output, 
	provides_direct_input_for, directly_inhibits, directly_activates, occurs_in, enabled_by, enables, regulated_by, located_in,
	directly_positively_regulated_by, directly_negatively_regulated_by, involved_in_regulation_of, involved_in_negative_regulation_of, involved_in_positive_regulation_of;
	public static OWLClass bp_class, continuant_class, process_class, go_complex, molecular_function, eco_imported, eco_imported_auto;
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

		//Will add classes and relations as we need them now. 
		//TODO Work on using imports later to ensure we don't produce incorrect ids..
		//classes	
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
		//complex
		OWLSubClassOfAxiom comp = df.getOWLSubClassOfAxiom(go_complex, continuant_class);
		ontman.addAxiom(go_cam_ont, comp);
		//ontman.applyChanges();

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
		directly_positively_regulated_by = df.getOWLObjectProperty(IRI.create(obo_iri + "RO_0002024"));
		addLabel(directly_positively_regulated_by, "directly positively regulated by");
		//RO_0002430 involved_in_negative_regulation_of
		//RO_0002429 involved_in_positive_regulation_of
		involved_in_negative_regulation_of = df.getOWLObjectProperty(IRI.create(obo_iri + "RO_0002430"));
		addLabel(involved_in_negative_regulation_of, "involved in negative regulation_of");
		involved_in_positive_regulation_of = df.getOWLObjectProperty(IRI.create(obo_iri + "RO_0002429"));
		addLabel(involved_in_positive_regulation_of, "involved in positive regulation_of");
		
		involved_in_regulation_of = df.getOWLObjectProperty(IRI.create(obo_iri + "RO_0002428"));
		addLabel(involved_in_regulation_of, "involved in regulation of");
		
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
	

	IRI makeEntityHashIri(Object entity) {
		String uri = base_iri+entity.hashCode();
		return IRI.create(uri);
	}
	
	IRI makeRandomIri() {
		String uri = base_iri+Math.random();
		return IRI.create(uri);
	}

	/**
	 * Given a set of PubMed reference identifiers, the pieces of a triple, and an evidence class, create an evidence individual for each pmid, 
	 * create a corresponding OWLAnnotation entity, make the triple along with all the annotations as evidence.  
	 * @param source
	 * @param prop
	 * @param target
	 * @param pmids
	 * @param evidence_class
	 */
	void addRefBackedObjectPropertyAssertion(OWLIndividual source, OWLObjectProperty prop, OWLIndividual target, Set<String> pmids, OWLClass evidence_class) {
		OWLObjectPropertyAssertionAxiom add_prop_axiom = null;
		if(pmids!=null&&pmids.size()>0) {
			Set<OWLAnnotation> annos = new HashSet<OWLAnnotation>();
			for(String pmid : pmids) {
				IRI anno_iri = makeEntityHashIri(source.hashCode()+"_"+prop.hashCode()+"_"+target.hashCode()+"_"+pmid);
				OWLNamedIndividual evidence = makeAnnotatedIndividual(anno_iri);					
				addTypeAssertion(evidence, evidence_class);
				addLiteralAnnotations2Individual(anno_iri, GoCAM.source_prop, "PMID:"+pmid);
				OWLAnnotation anno = df.getOWLAnnotation(GoCAM.evidence_prop, anno_iri);
				annos.add(anno);
			}
			annos.addAll(getDefaultAnnotations());
			add_prop_axiom = df.getOWLObjectPropertyAssertionAxiom(prop, source, target, annos);
		}else {
			add_prop_axiom = df.getOWLObjectPropertyAssertionAxiom(prop, source, target, getDefaultAnnotations());
		}
		AddAxiom addAxiom = new AddAxiom(go_cam_ont, add_prop_axiom);
		ontman.applyChange(addAxiom);
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
	QRunner initializeQRunnerForTboxInference() throws OWLOntologyCreationException {
		//TODO either grab this from a PURL so its always up to date, or keep the referenced imported file in sync.  
		String tbox_file = "src/main/resources/org/geneontology/gocam/exchange/ro-merged.owl";
		//<http://purl.obolibrary.org/obo/go/extensions/go-lego.owl>
		//String tbox_file = "src/main/resources/org/geneontology/gocam/exchange/go-lego-noneo.owl";
		OWLOntologyManager tman = OWLManager.createOWLOntologyManager();
		OWLOntology tbox = tman.loadOntologyFromOntologyDocument(new File(tbox_file));	
		boolean add_inferences = true;
		boolean add_property_definitions = false; boolean add_class_definitions = false;
		qrunner = new QRunner(tbox, go_cam_ont, add_inferences, add_property_definitions, add_class_definitions);
		return qrunner;
	}

	QRunner initializeQRunner(OWLOntology tbox) throws OWLOntologyCreationException {
		boolean add_inferences = true;
		boolean add_property_definitions = false; boolean add_class_definitions = false;
		qrunner = new QRunner(tbox, null, add_inferences, add_property_definitions, add_class_definitions);
		return qrunner;
	}
	
	void addInferredEdges() throws OWLOntologyCreationException {
		if(qrunner==null||qrunner.arachne==null) {
			initializeQRunnerForTboxInference();
		}
		//System.out.println("Applying tbox rules to expand the gocam graph");
		qrunner.wm = qrunner.arachne.createInferredModel(this.go_cam_ont, false, false);			
		//System.out.println("Making Jena model from inferred graph for query");
		qrunner.jena = qrunner.makeJenaModel(qrunner.wm);
	}
	
/**
 * Use sparql queries to inform modifications to the go-cam owl ontology 
 */
	void applySparqlRules() {
		Set<InferredEnabler> ies = qrunner.getInferredEnablers();
		for(InferredEnabler ie : ies) {
			//create ?reaction2 obo:RO_0002333 ?input
			OWLNamedIndividual e = this.makeAnnotatedIndividual(ie.enabler_uri);
			OWLNamedIndividual r = this.makeAnnotatedIndividual(ie.reaction_uri);
			Set<OWLAnnotation> annos = getDefaultAnnotations();
			String explain = "This 'enabled by' relation was inferred as follows. "
				+ "An entity (either protein or protein complex) E enables a reaction R2\n" + 
					" IF R1 provides direct input for R2 \n" + 
					" and R1 has output E1 \n" + 
					" and R2 has input E2 \n" + 
					" and E1 = E2 ";
			annos.add(df.getOWLAnnotation(rdfs_comment, df.getOWLLiteral(explain)));
			this.addObjectPropertyAssertion(r, GoCAM.enabled_by, e, annos);
			System.out.println(r+" added enabled by "+e);
			//delete the input relation (replaced above by the enabled by relation)
			//TODO the following doesn't work.  Making new individuals all over the place makes them harder to find..
			//above makes a new edge
			//OWLObjectPropertyAssertionAxiom has_input = df.getOWLObjectPropertyAssertionAxiom(GoCAM.has_input, r, e);			
			//this.ontman.removeAxiom(go_cam_ont, has_input);
		}
		System.out.println("Added "+ies.size()+" enabled_by triples");
		Set<InferredRegulator> ir1 = qrunner.getInferredRegulatorsQ1();
		for(InferredRegulator ir : ir1) {
			//create ?reaction2 obo:RO_0002333 ?input
			OWLNamedIndividual r1 = this.makeAnnotatedIndividual(ir.reaction1_uri);
			OWLNamedIndividual r2 = this.makeAnnotatedIndividual(ir.reaction2_uri);
			OWLObjectProperty o = GoCAM.directly_negatively_regulated_by;
			if(ir.prop_uri.equals("http://purl.obolibrary.org/obo/RO_0002429")) {
				o = GoCAM.directly_positively_regulated_by;
			}
			Set<OWLAnnotation> annos = getDefaultAnnotations();
			String explain = "This regulation relation was inferred. "
				+ "Reaction1 is (+-)regulated by Reaction2 if\n" + 
					" IF R2 has output A\n" + 
					" and A is involved in (+-)regulation of R1 ";
			annos.add(df.getOWLAnnotation(rdfs_comment, df.getOWLLiteral(explain)));
			this.addObjectPropertyAssertion(r1, o, r2, annos);
			System.out.println("reg1 "+r1+" "+o+" "+r2);
		}
		System.out.println("Added "+ir1.size()+" pos/neg reg triples");
		Set<InferredRegulator> ir2_neg = qrunner.getInferredRegulatorsQ2();
		for(InferredRegulator ir : ir2_neg) {
			//create ?reaction2 obo:RO_0002333 ?input
			OWLNamedIndividual r1 = this.makeAnnotatedIndividual(ir.reaction1_uri);
			OWLNamedIndividual r2 = this.makeAnnotatedIndividual(ir.reaction2_uri);
			OWLObjectProperty o = df.getOWLObjectProperty(IRI.create(ir.prop_uri));
			Set<OWLAnnotation> annos = getDefaultAnnotations();
			String explain = "This regulation relation was inferred based on idea of inhibitory binding. "
				+ "Reaction1 is negatively regulated by Reaction2 if\n" + 
				 " IF R2 has input A, R2 has input B, has output A/B complex\n" + 
				 " and R1 is enabled by B";
			annos.add(df.getOWLAnnotation(rdfs_comment, df.getOWLLiteral(explain)));
			this.addObjectPropertyAssertion(r1, o, r2, annos);
			System.out.println("reg2 "+r1+" "+o+" "+r2);
		}		
		System.out.println("Added "+ir2_neg.size()+" neg inhibitory binding reg triples");
	}

	//for some reason the following produces floating evidence nodes in Noctua right now - same thing that happens when you 
	//add evidence node to an rdf:type edge
	//not using for the moment..
//	OWLAnnotation addInferenceEvidenceAsComment(OWLNamedIndividual source, OWLObjectProperty prop, OWLNamedIndividual target, String comment) {
//		IRI anno_iri = makeEntityHashIri(source.hashCode()+"_"+prop.hashCode()+"_"+target.hashCode()+"_"+comment.hashCode());
//		OWLNamedIndividual evidence = makeAnnotatedIndividual(anno_iri);					
//		addTypeAssertion(evidence, GoCAM.eco_imported_auto);
//		addLiteralAnnotations2Individual(anno_iri, rdfs_comment, comment);
//		OWLAnnotation anno = df.getOWLAnnotation(GoCAM.evidence_prop, anno_iri);
//		return anno;
//	}

	void writeGoCAM(String outfilename, boolean add_inferred, boolean save2blazegraph, boolean applySparqlRules) throws OWLOntologyStorageException, OWLOntologyCreationException, RepositoryException, RDFParseException, RDFHandlerException, IOException {
		File outfilefile = new File(outfilename);	
		//synchronize jena model <- with owl-api model	 
		//go_cam_ont should have everything we want at this point, including any imports
		qrunner = new QRunner(go_cam_ont); 
		if(applySparqlRules) {
			System.out.println("Before sparql inference -  triples: "+qrunner.nTriples());
			applySparqlRules();
			//sparql rules make additions to go_cam_ont
			qrunner = new QRunner(go_cam_ont); 
			System.out.println("After sparql inference -  triples: "+qrunner.nTriples());
			int n_removed = qrunner.deleteEntityLocations();
			System.out.println("Removed "+n_removed+" entity location triples");
		}				
		if(add_inferred) {
			System.out.println("preparing model starting with (unreasoned) triples: "+qrunner.nTriples());
			//apply Arachne to tbox rules and add inferences to qrunner.jena rdf model
			addInferredEdges();
			System.out.println("total triples after inference: "+qrunner.nTriples());
		}
		//use jena export
		System.out.println("writing n triples: "+qrunner.nTriples());
		qrunner.dumpModel(outfilefile, "TURTLE");
//		else {
//			//write with OWL API..		
//			FileDocumentTarget outfile = new FileDocumentTarget(outfilefile);
//			//ontman.setOntologyFormat(go_cam_ont, new TurtleOntologyFormat());	
//			ontman.setOntologyFormat(go_cam_ont, new TurtleDocumentFormat());	
//			ontman.saveOntology(go_cam_ont,outfile);
//		}

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
			this.initializeQRunnerForTboxInference();
		}
		this.addInferredEdges();
		is_valid = qrunner.isConsistent();
		System.out.println("Total triples in validated model including tbox: "+qrunner.nTriples());
		if(!is_valid) {
			System.out.println("GO-CAM model is not logically consistent, please inspect model and try again!\n Entities = OWL:Nothing include:\n");
			Set<String> u = qrunner.getUnreasonableEntities();
			for(String s : u) {
				System.out.println(s);
			}
		}
		return is_valid;
	}

}
