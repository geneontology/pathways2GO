package org.geneontology.gocam.exchange;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import org.biopax.paxtools.io.BioPAXIOHandler;
import org.biopax.paxtools.io.SimpleIOHandler;
import org.biopax.paxtools.model.BioPAXElement;
import org.biopax.paxtools.model.BioPAXFactory;
import org.biopax.paxtools.model.BioPAXLevel;
import org.biopax.paxtools.model.Model;
import org.biopax.paxtools.model.level3.BiochemicalReaction;
import org.biopax.paxtools.model.level3.Complex;
import org.biopax.paxtools.model.level3.Entity;
import org.biopax.paxtools.model.level3.Pathway;
import org.biopax.paxtools.model.level3.PhysicalEntity;
import org.biopax.paxtools.model.level3.Protein;
import org.biopax.paxtools.model.level3.Provenance;
import org.biopax.paxtools.model.level3.RelationshipTypeVocabulary;
import org.biopax.paxtools.model.level3.RelationshipXref;
import org.biopax.paxtools.model.level3.SmallMolecule;
import org.biopax.paxtools.model.level3.UnificationXref;
import org.geneontology.rules.util.ArachneOWLReasoner;
import org.geneontology.rules.util.ArachneOWLReasonerFactory;
import org.geneontology.whelk.owlapi.WhelkOWLReasoner;
import org.geneontology.whelk.owlapi.WhelkOWLReasonerFactory;
import org.semanticweb.elk.owlapi.ElkReasonerFactory;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.AddImport;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLAnnotation;
import org.semanticweb.owlapi.model.OWLAnnotationProperty;
import org.semanticweb.owlapi.model.OWLAxiom;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLClassExpression;
import org.semanticweb.owlapi.model.OWLDataFactory;
import org.semanticweb.owlapi.model.OWLImportsDeclaration;
import org.semanticweb.owlapi.model.OWLIndividual;
import org.semanticweb.owlapi.model.OWLLiteral;
import org.semanticweb.owlapi.model.OWLNamedIndividual;
import org.semanticweb.owlapi.model.OWLObjectProperty;
import org.semanticweb.owlapi.model.OWLObjectPropertyAssertionAxiom;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyCreationException;
import org.semanticweb.owlapi.model.OWLOntologyID;
import org.semanticweb.owlapi.model.OWLOntologyIRIMapper;
import org.semanticweb.owlapi.model.OWLOntologyManager;
import org.semanticweb.owlapi.model.OWLOntologyStorageException;
import org.semanticweb.owlapi.reasoner.OWLReasoner;
import org.semanticweb.owlapi.reasoner.OWLReasonerFactory;
import org.semanticweb.owlapi.reasoner.structural.StructuralReasonerFactory;
import org.semanticweb.owlapi.search.EntitySearcher;
import org.semanticweb.owlapi.util.SimpleIRIMapper;

public class GOtoBioPAX {

	public static OWLAnnotationProperty obo_id_prop;
	public static String biopax_base = "http://www.biopax.org/release/biopax-level3.owl#";
	public static String obo_base_1 = "http://purl.obolibrary.org/obo/";
	public static String obo_base_2 = "http://www.geneontology.org/formats/oboInOwl#";
	public static String go_cam_model_base = "http://model.geneontology.org/";
	public OWLDataFactory df;
	public OWLOntologyManager ontman;
	public OWLOntology goplus;
	public GoCAM go_cam = new GoCAM(); //initializes all the useful static classes and properties..

	public GOtoBioPAX() throws OWLOntologyCreationException {		
		df = OWLManager.getOWLDataFactory();
		obo_id_prop = df.getOWLAnnotationProperty(IRI.create(obo_base_2 + "id"));		
		ontman = OWLManager.createOWLOntologyManager();	
		IRI go_plus_iri = IRI.create("http://purl.obolibrary.org/obo/go/extensions/go-lego.owl");
		IRI go_plus_local_iri = IRI.create("file:///Users/bgood/git/noctua_exchange/exchange/src/main/resources/org/geneontology/gocam/exchange/go-plus-merged.owl");
		ontman.getIRIMappers().add(new SimpleIRIMapper(go_plus_iri, go_plus_local_iri));
		goplus = ontman.loadOntology(go_plus_iri);	
	}

	public static void main(String[] args) throws OWLOntologyCreationException, FileNotFoundException, OWLOntologyStorageException {
		//		//"/Users/bgood/Desktop/test/tmp/converted-Degradation_of_AXIN.ttl";

		//initializes GOPlus for reasoning
		GOtoBioPAX go2bp = new GOtoBioPAX();
		//load a single go-cam
		String input_go_cam = "/Users/bgood/Desktop/test/go_cams/canonical_wnt.owl";
		String output_biopax = "/Users/bgood/Desktop/test/biopax/canonical_wnt_as_biopax.owl";
		//do the conversion
		go2bp.makeBioPAXFromGoCAM(input_go_cam, output_biopax);
	}

	public void makeBioPAXFromGoCAM(String go_cam_file, String biopax_file) throws OWLOntologyCreationException, FileNotFoundException, OWLOntologyStorageException {
		//set up the BioPax model		
		BioPAXFactory factory = BioPAXLevel.L3.getDefaultFactory();
		Model biopax = factory.createModel();
		biopax.setXmlBase(go_cam_model_base+"biopax/");
		
		//load the go_cam and grab its iri and metadata
		OWLOntology go_cam_ont = ontman.loadOntologyFromOntologyDocument(new File(go_cam_file));	
		OWLOntologyID ont_id = go_cam_ont.getOntologyID();				
		String ont_uri_string = ont_id.getOntologyIRI().get().toString();
		Set<OWLAnnotation> ont_annos = go_cam_ont.getAnnotations();
		String go_cam_title = "";
		String model_id = "";
		String date = "";
		String modelstate = "";
		Set<String> contributors = new HashSet<String>();
		String go_cam_comment = "Source "+ont_uri_string+" contributors: ";
		for(OWLAnnotation ont_anno : ont_annos) {
			String v = ont_anno.getValue().asLiteral().get().getLiteral();
			String p = ont_anno.getProperty().getIRI().toString();
			if(p.equals("http://purl.org/dc/elements/1.1/title")){
				go_cam_title = v;
			}else if(p.equals("http://purl.org/dc/elements/1.1/contributor")){
				contributors.add(v);
				go_cam_comment = go_cam_comment+" "+v;
			}else if (p.equals("http://geneontology.org/lego/id")){
				model_id = v;
			}else if (p.equals("http://geneontology.org/lego/modelstate")){
				modelstate = v;
			}else if (p.equals("http://purl.org/dc/elements/1.1/date")){
				date = v;
			}
		}
		go_cam_comment = go_cam_comment + " model state = "+modelstate+" date "+date;
		//make a reasoner that incorporates the imported goplus
		//noting that the go_cam imports lego and lego is mapped to a real goplus file in the constructor
		OWLReasonerFactory reasonerFactory = new WhelkOWLReasonerFactory(); 
		WhelkOWLReasoner go_cam_reasoner = (WhelkOWLReasoner)reasonerFactory.createReasoner(go_cam_ont);	
		//get all of the biological process nodes in the cam, generate pathway biopax nodes for each
		Set<OWLNamedIndividual> bps = go_cam_reasoner.getInstances(GoCAM.bp_class, false).getFlattened();
		for(OWLNamedIndividual bp : bps) {
			Pathway pathway = factory.create(Pathway.class, bp.getIRI().toString()); 
			pathway = (Pathway)addDataSource(biopax, factory, pathway, ont_uri_string, go_cam_title , go_cam_comment);
			//note each pathway could, in principle, be linked to multiple biological processes
			Set<OWLClass> go_bp_classes = go_cam_reasoner.getTypes(bp, true).getFlattened();
			for(OWLClass go_bp_class : go_bp_classes) {
				//getting the names and ids
				Collection<OWLAnnotation> ids = EntitySearcher.getAnnotationObjects(go_bp_class, goplus, obo_id_prop);
				for(OWLAnnotation id_anno : ids) {
					OWLLiteral go_acc = id_anno.getValue().asLiteral().get();
					pathway = (Pathway) addGoXref(biopax, factory, pathway, go_acc.getLiteral(), "bp");
					pathway.addName(Helper.getaLabel(go_bp_class, goplus));
				}
			}
			biopax.add(pathway);
			System.out.println("Pathway "+pathway.getDisplayName());
			//now add the parts (reactions/functions)
			//noting that has_part now captures part_ofs thanks the whelk reasoner
			Set<OWLNamedIndividual> has_parts = go_cam_reasoner.getObjectPropertyValues(bp, GoCAM.has_part).getFlattened();
			for(OWLNamedIndividual mf : has_parts) {
				//TODO look for interesting relationships
				Set<OWLObjectPropertyAssertionAxiom> props = go_cam_reasoner.getAllObjectPropertyValues(mf);
				System.out.println("Pathway part: "+mf+" props "+props);
				BiochemicalReaction reaction = factory.create(BiochemicalReaction.class, mf.getIRI().toString());
				Set<OWLClass> go_mf_classes = go_cam_reasoner.getTypes(mf, true).getFlattened();
				for(OWLClass go_mf_class : go_mf_classes) {	
					Collection<OWLAnnotation> ids = EntitySearcher.getAnnotationObjects(go_mf_class, goplus, obo_id_prop);
					for(OWLAnnotation id_anno : ids) {
						OWLLiteral go_acc = id_anno.getValue().asLiteral().get();
						reaction = (BiochemicalReaction) addGoXref(biopax, factory, reaction, go_acc.getLiteral(), "mf");
						reaction.addName(Helper.getaLabel(go_mf_class, goplus));
					}
				}
				biopax.add(reaction);
				pathway.addPathwayComponent(reaction);

				System.out.println("Reaction "+reaction);
				//get the pieces of the reaction
				Set<OWLNamedIndividual> inputs = go_cam_reasoner.getObjectPropertyValues(mf, GoCAM.has_input).getFlattened();
				for(OWLNamedIndividual input : inputs) {
					reaction.addLeft(goCamEntityToBioPAXentity(input, factory, biopax, go_cam_reasoner));
					System.out.println("input "+input);
				}

				Set<OWLNamedIndividual> outputs = go_cam_reasoner.getObjectPropertyValues(mf, GoCAM.has_output).getFlattened();
				for(OWLNamedIndividual output : outputs) {
					reaction.addLeft(goCamEntityToBioPAXentity(output, factory, biopax, go_cam_reasoner));
					System.out.println("output "+output);
				}
			}
		}

		BioPAXIOHandler handler = new SimpleIOHandler();
		FileOutputStream outstream = new FileOutputStream(biopax_file);
		handler.convertToOWL(biopax, outstream);
	}

	public Entity addDataSource(Model biopax, BioPAXFactory factory, Entity entity, String data_source_uri, String data_source_name, String comment) {
		Provenance prov = factory.create(Provenance.class, data_source_uri);
		prov.addName(data_source_name);
		prov.addComment(comment);
		entity.addDataSource(prov);
		addToModelWithIdCheck(biopax, prov);
		return entity;
	}
	
	public Entity addGoXref(Model biopax, BioPAXFactory factory, Entity entity, String go_acc, String go_root) {
		RelationshipXref go_xref = factory.create(RelationshipXref.class, biopax_base+Math.random());
		go_xref.setId(go_acc);
		go_xref.setDb("gene ontology");
		//using Molecular Interaction Ontology based on example from Pathway Commons		
		UnificationXref uxref = factory.create(UnificationXref.class, "http://pathwaycommons.org/pc2/#UnificationXref_molecular_interactions_ontology_MI_0359");
		uxref.setDb("molecular interactions ontology");
		String mi_acc_bp = "MI:0359";
		String mi_acc_mf = "MI:0355";
		String mi_acc_cc = "MI:0354";
		String mi_acc = "http://identifiers.org/psimi/"; String term = "";		
		if(go_root.equals("bp")) {
			mi_acc += mi_acc_bp;
			term = "biological process";
			uxref.setId(mi_acc_bp);
		}else if(go_root.equals("mf")) {
			mi_acc += mi_acc_mf;
			term = "molecular function";
			uxref.setId(mi_acc_mf);
		}else if(go_root.equals("cc")) {
			mi_acc += mi_acc_cc;
			term = "cellular component";
			uxref.setId(mi_acc_cc);
		}else {
			System.out.println("GO root not specified in go xref");
		}
		RelationshipTypeVocabulary rtv = factory.create(RelationshipTypeVocabulary.class, mi_acc);
		rtv.addTerm(term);
		rtv.addXref(uxref);
		go_xref.setRelationshipType(rtv);		
		entity.addXref(go_xref);

		addToModelWithIdCheck(biopax, go_xref);
		addToModelWithIdCheck(biopax, uxref);
		addToModelWithIdCheck(biopax, rtv);

		return entity;
	}

	public void addToModelWithIdCheck(Model biopax, BioPAXElement element) {
		if(!biopax.containsID(element.getUri())) {
			biopax.add(element);
		}	
	}

	//TODO find a way to get legible names to attach as labels
	public PhysicalEntity goCamEntityToBioPAXentity(OWLNamedIndividual e, BioPAXFactory factory, Model biopax, OWLReasoner go_cam_reasoner) {
		PhysicalEntity entity = null;
		String e_iri = e.getIRI().toString();

		System.out.println(e_iri);		
		Set<OWLClass> types = go_cam_reasoner.getTypes(e, false).getFlattened();
		if(types==null) {
			System.out.println("error, no type found for reaction I/O entity "+e_iri);
			return null;
		}
		if(types.contains(GoCAM.chemical_entity)) {
			SmallMolecule mlc = factory.create(SmallMolecule.class, e_iri);
			biopax.add(mlc);
			System.out.println("small "+mlc);
			return mlc;
		}else if(types.contains(GoCAM.go_complex)) {
			Complex complex = factory.create(Complex.class, e_iri);
			biopax.add(complex);
			System.out.println("complex "+complex);
			return complex;
		}else if(types.contains(GoCAM.chebi_protein)||guessProtein(types)) {
			Protein protein = factory.create(Protein.class, e_iri);
			biopax.add(protein);
			System.out.println("protein "+protein);
			return protein;
		}else{
			entity = factory.create(PhysicalEntity.class, e_iri);
			System.out.println("other "+entity+" "+types);
		}
		return entity;
	}

	boolean guessProtein(Set<OWLClass> types) {
		for(OWLClass t : types) {
			if(t.getIRI().toString().contains("http://identifiers.org/uniprot/")){
				return true;
			}
		}
		return false;
	}

}
