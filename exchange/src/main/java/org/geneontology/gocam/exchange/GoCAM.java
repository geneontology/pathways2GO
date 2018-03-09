/**
 * 
 */
package org.geneontology.gocam.exchange;

import java.io.File;
import java.io.UnsupportedEncodingException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;

import org.biopax.paxtools.model.level3.PublicationXref;
import org.biopax.paxtools.model.level3.Xref;
//import org.coode.owlapi.turtle.TurtleOntologyFormat;
import org.semanticweb.owlapi.formats.TurtleDocumentFormat;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.io.FileDocumentTarget;
import org.semanticweb.owlapi.model.AddAxiom;
import org.semanticweb.owlapi.model.AddImport;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLAnnotation;
import org.semanticweb.owlapi.model.OWLAnnotationProperty;
import org.semanticweb.owlapi.model.OWLAnnotationSubject;
import org.semanticweb.owlapi.model.OWLAnnotationValue;
import org.semanticweb.owlapi.model.OWLAxiom;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLClassAssertionAxiom;
import org.semanticweb.owlapi.model.OWLDataFactory;
import org.semanticweb.owlapi.model.OWLDocumentFormat;
import org.semanticweb.owlapi.model.OWLEntity;
import org.semanticweb.owlapi.model.OWLImportsDeclaration;
import org.semanticweb.owlapi.model.OWLIndividual;
import org.semanticweb.owlapi.model.OWLLiteral;
import org.semanticweb.owlapi.model.OWLNamedIndividual;
import org.semanticweb.owlapi.model.OWLObjectProperty;
import org.semanticweb.owlapi.model.OWLObjectPropertyAssertionAxiom;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyCreationException;
import org.semanticweb.owlapi.model.OWLOntologyManager;
import org.semanticweb.owlapi.model.OWLOntologyStorageException;
import org.semanticweb.owlapi.model.OWLSubClassOfAxiom;
import org.semanticweb.owlapi.search.EntitySearcher;
import org.semanticweb.owlapi.vocab.OWLRDFVocabulary;

/**
 * @author bgood
 *
 */
public class GoCAM {
	public static final IRI go_lego_iri = IRI.create("http://purl.obolibrary.org/obo/go/extensions/go-lego.owl");
	public static final IRI obo_iri = IRI.create("http://purl.obolibrary.org/obo/");
	public static final IRI uniprot_iri = IRI.create("http://identifiers.org/uniprot/");
	public static IRI base_ont_iri;
	public static OWLAnnotationProperty title_prop, contributor_prop, date_prop, 
		state_prop, evidence_prop, provided_by_prop, x_prop, y_prop, rdfs_label, rdfs_comment, source_prop;
	public static OWLObjectProperty part_of, has_part, has_input, has_output, 
		provides_direct_input_for, directly_inhibits, directly_activates, occurs_in, enabled_by, enables, regulated_by, located_in;
	public static OWLClass bp_class, continuant_class, go_complex, molecular_function, eco_imported, eco_imported_auto;
	OWLOntology go_cam_ont;
	OWLDataFactory df;
	OWLOntologyManager ontman;
	String base_contributor, base_date, base_provider;
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
	
	
	IRI makeIri(String entity) {
		String uri = "http://model.geneontology.org/"+entity.hashCode();
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
				IRI anno_iri = makeIri(source.hashCode()+"_"+prop.hashCode()+"_"+target.hashCode()+"_"+pmid);
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
	
	void writeGoCAM(String outfilename) throws OWLOntologyStorageException {
		FileDocumentTarget outfile = new FileDocumentTarget(new File(outfilename));
		//ontman.setOntologyFormat(go_cam_ont, new TurtleOntologyFormat());	
		ontman.setOntologyFormat(go_cam_ont, new TurtleDocumentFormat());	
		ontman.saveOntology(go_cam_ont,outfile);	
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
		//TODO either grab this from a PURL so its always up to date, or keep the referenced imported file in sync.  
		String tbox_file = "src/main/resources/org/geneontology/gocam/exchange/ro-merged.owl";
		//<http://purl.obolibrary.org/obo/go/extensions/go-lego.owl>
		//String tbox_file = "src/main/resources/org/geneontology/gocam/exchange/go-lego-noneo.owl";
		OWLOntologyManager tman = OWLManager.createOWLOntologyManager();
		OWLOntology tbox = tman.loadOntologyFromOntologyDocument(new File(tbox_file));	
		boolean add_inferences = true;
		boolean add_property_definitions = false; boolean add_class_definitions = false;
		QRunner q = new QRunner(tbox, this.go_cam_ont, add_inferences, add_property_definitions, add_class_definitions);
		is_valid = q.isConsistent();
		if(is_valid) {
			System.out.println("GO-CAM model is valid, nice one!");
		}else {
			System.out.println("GO-CAM model is not logically consistent, please inspect model and try again!\n Entities = OWL:Nothing include:\n");
			Set<String> u = q.getUnreasonableEntities();
			for(String s : u) {
				System.out.println(s);
			}
		}
		return is_valid;
	}
	
}
