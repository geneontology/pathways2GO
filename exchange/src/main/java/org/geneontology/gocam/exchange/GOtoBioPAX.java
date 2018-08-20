package org.geneontology.gocam.exchange;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.util.Collection;
import java.util.Iterator;
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
import org.biopax.paxtools.model.level3.RelationshipTypeVocabulary;
import org.biopax.paxtools.model.level3.RelationshipXref;
import org.biopax.paxtools.model.level3.SmallMolecule;
import org.biopax.paxtools.model.level3.UnificationXref;
import org.geneontology.rules.util.ArachneOWLReasoner;
import org.geneontology.rules.util.ArachneOWLReasonerFactory;
import org.semanticweb.elk.owlapi.ElkReasonerFactory;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLAnnotation;
import org.semanticweb.owlapi.model.OWLAnnotationProperty;
import org.semanticweb.owlapi.model.OWLAxiom;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLClassExpression;
import org.semanticweb.owlapi.model.OWLDataFactory;
import org.semanticweb.owlapi.model.OWLIndividual;
import org.semanticweb.owlapi.model.OWLLiteral;
import org.semanticweb.owlapi.model.OWLNamedIndividual;
import org.semanticweb.owlapi.model.OWLObjectProperty;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyCreationException;
import org.semanticweb.owlapi.model.OWLOntologyManager;
import org.semanticweb.owlapi.model.OWLOntologyStorageException;
import org.semanticweb.owlapi.reasoner.OWLReasoner;
import org.semanticweb.owlapi.reasoner.OWLReasonerFactory;
import org.semanticweb.owlapi.reasoner.structural.StructuralReasonerFactory;
import org.semanticweb.owlapi.search.EntitySearcher;

public class GOtoBioPAX {
//	public static OWLObjectProperty xref_object_prop;
	public static OWLAnnotationProperty obo_id_prop;
	public static String biopax_base = "http://www.biopax.org/release/biopax-level3.owl#";
	public static String obo_base_1 = "http://purl.obolibrary.org/obo/";
	public static String obo_base_2 = "http://www.geneontology.org/formats/oboInOwl#";
	public OWLDataFactory df;
	
	public GOtoBioPAX() {
		df = OWLManager.getOWLDataFactory();
	//	xref_object_prop = df.getOWLObjectProperty(IRI.create(biopax_base + "xref"));
		obo_id_prop = df.getOWLAnnotationProperty(IRI.create(obo_base_2 + "id"));
	}

	public static void main(String[] args) throws OWLOntologyCreationException, FileNotFoundException, OWLOntologyStorageException {
		String input_go_cam = "/Users/bgood/Desktop/test/canonical_wnt.owl";
		//"/Users/bgood/Desktop/test/tmp/converted-Degradation_of_AXIN.ttl";
		GoCAM go_cam = new GoCAM(input_go_cam);
		GOtoBioPAX go2bp = new GOtoBioPAX();
		go2bp.makeBioPAXFromGoCAM(go_cam);
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
	
	public void makeBioPAXFromGoCAM(GoCAM go_cam) throws OWLOntologyCreationException, FileNotFoundException, OWLOntologyStorageException {
		//add go to the model 
		OWLOntology go = go_cam.ontman.loadOntologyFromOntologyDocument(new File("/Users/bgood/git/noctua_exchange/exchange/src/main/resources/org/geneontology/gocam/exchange/go.owl"));	
		go_cam.ontman.addAxioms(go_cam.go_cam_ont, go.getAxioms());
		//turn on the reasoner
		OWLReasonerFactory reasonerFactory = new StructuralReasonerFactory(); //Elk
		OWLReasoner go_cam_reasoner = reasonerFactory.createReasoner(go_cam.go_cam_ont);	
//		OWLReasonerFactory reasonerFactory = new ArachneOWLReasonerFactory(go);
//		OWLReasoner go_cam_reasoner = reasonerFactory.createReasoner(go);
	//	Helper.writeOntology("/Users/bgood/Desktop/test/tmp.ttl", go_cam.go_cam_ont);
		
		String output_biopax = "/Users/bgood/Desktop/test/tmp/canonical_wnt_as_biopax.owl";
		BioPAXFactory factory = BioPAXLevel.L3.getDefaultFactory();
		Model biopax = factory.createModel();
		biopax.setXmlBase(GoCAM.base_iri);
		
		
		
		//get the pathways (nodes of super type biological process)
		Set<OWLNamedIndividual> bps = go_cam_reasoner.getInstances(GoCAM.bp_class, false).getFlattened();
		for(OWLNamedIndividual bp : bps) {
			Pathway pathway = factory.create(Pathway.class, bp.getIRI().toString()+"_bp"); 
			//note each pathway could, in principle, be linked to multiple biological processes
			Set<OWLClass> go_bp_classes = go_cam_reasoner.getTypes(bp, true).getFlattened();
			for(OWLClass go_bp_class : go_bp_classes) {
				Collection<OWLAnnotation> ids = EntitySearcher.getAnnotationObjects(go_bp_class, go, obo_id_prop);
				for(OWLAnnotation id_anno : ids) {
					OWLLiteral go_acc = id_anno.getValue().asLiteral().get();
					pathway = (Pathway) addGoXref(biopax, factory, pathway, go_acc.getLiteral(), "bp");
					pathway.addName(Helper.getaLabel(go_bp_class, go));
				}
			}
			biopax.add(pathway);
			System.out.println("Pathway "+pathway);
			//get their mf/reaction parts 
			//TODO use a reasoner to do this (get inferred edges..)
			//Set<OWLNamedIndividual> mfs = go_cam_reasoner.getObjectPropertyValues(bp, GoCAM.has_part).getFlattened();
			//for(OWLNamedIndividual mf : mfs) {
			Collection<OWLIndividual> has_parts = EntitySearcher.getObjectPropertyValues(bp, GoCAM.has_part, go_cam.go_cam_ont);
		
			Iterator<OWLIndividual> mfs = has_parts.iterator();
			while(mfs.hasNext()) {
				OWLNamedIndividual mf = (OWLNamedIndividual)mfs.next();
				BiochemicalReaction reaction = factory.create(BiochemicalReaction.class, mf.getIRI().toString());
				biopax.add(reaction);
				//connect them
				pathway.addPathwayComponent(reaction);
				System.out.println("Reaction "+reaction);
				//get the pieces of the reaction
				Iterator<OWLIndividual> inputs = EntitySearcher.getObjectPropertyValues(mf, GoCAM.has_input, go_cam.go_cam_ont).iterator();
				while(inputs.hasNext()) {
					OWLNamedIndividual e = inputs.next().asOWLNamedIndividual();
					reaction.addLeft(goCamEntityToBioPAXentity(e, factory, biopax, go_cam, go_cam_reasoner));
				}
				Iterator<OWLIndividual> outputs = EntitySearcher.getObjectPropertyValues(mf, GoCAM.has_output, go_cam.go_cam_ont).iterator();
				while(outputs.hasNext()) {
					OWLNamedIndividual e = outputs.next().asOWLNamedIndividual();
					reaction.addRight(goCamEntityToBioPAXentity(e, factory, biopax, go_cam, go_cam_reasoner));
				}
			}
		}
		
		BioPAXIOHandler handler = new SimpleIOHandler();
		FileOutputStream outstream = new FileOutputStream(output_biopax);
		handler.convertToOWL(biopax, outstream);
	}
	
	public PhysicalEntity goCamEntityToBioPAXentity(OWLNamedIndividual e, BioPAXFactory factory, Model biopax, GoCAM go_cam, OWLReasoner go_cam_reasoner) {
		PhysicalEntity entity = null;
		String e_iri = e.getIRI().toString();

		System.out.println(e_iri);		
		Set<OWLClass> types = go_cam_reasoner.getTypes(e, false).getFlattened();
		if(types==null) {
			System.out.println("error, no type found for reaction I/O entity "+e_iri);
			return null;
		}
		//GoCAM.chebi_protein

		if(types.contains(GoCAM.chemical_entity)) {
			SmallMolecule mlc = factory.create(SmallMolecule.class, e_iri);
			biopax.add(mlc);
			System.out.println("small "+mlc);
			return mlc;
		}else if(types.contains(GoCAM.chebi_protein)) {
			Protein protein = factory.create(Protein.class, e_iri);
			biopax.add(protein);
			System.out.println("protein "+protein);
			return protein;
		}else if(types.contains(GoCAM.go_complex)) {
			Complex complex = factory.create(Complex.class, e_iri);
			biopax.add(complex);
			System.out.println("complex "+complex);
			return complex;
		}else {
			entity = factory.create(PhysicalEntity.class, e_iri);
			System.out.println("other "+entity+" "+types);
		}
		return entity;
	}
	
}
