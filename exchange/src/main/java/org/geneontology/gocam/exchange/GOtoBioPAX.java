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
import org.biopax.paxtools.model.level2.pathwayStep;
import org.biopax.paxtools.model.level3.BiochemicalReaction;
import org.biopax.paxtools.model.level3.Catalysis;
import org.biopax.paxtools.model.level3.CellularLocationVocabulary;
import org.biopax.paxtools.model.level3.Complex;
import org.biopax.paxtools.model.level3.Control;
import org.biopax.paxtools.model.level3.ControlType;
import org.biopax.paxtools.model.level3.Controller;
import org.biopax.paxtools.model.level3.Entity;
import org.biopax.paxtools.model.level3.EntityReference;
import org.biopax.paxtools.model.level3.Pathway;
import org.biopax.paxtools.model.level3.PathwayStep;
import org.biopax.paxtools.model.level3.PhysicalEntity;
import org.biopax.paxtools.model.level3.Process;
import org.biopax.paxtools.model.level3.Protein;
import org.biopax.paxtools.model.level3.ProteinReference;
import org.biopax.paxtools.model.level3.Provenance;
import org.biopax.paxtools.model.level3.RelationshipTypeVocabulary;
import org.biopax.paxtools.model.level3.RelationshipXref;
import org.biopax.paxtools.model.level3.SmallMolecule;
import org.biopax.paxtools.model.level3.SmallMoleculeReference;
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
import org.semanticweb.owlapi.model.OWLObjectPropertyExpression;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyCreationException;
import org.semanticweb.owlapi.model.OWLOntologyID;
import org.semanticweb.owlapi.model.OWLOntologyIRIMapper;
import org.semanticweb.owlapi.model.OWLOntologyManager;
import org.semanticweb.owlapi.model.OWLOntologyStorageException;
import org.semanticweb.owlapi.reasoner.OWLReasoner;
import org.semanticweb.owlapi.reasoner.OWLReasonerFactory;
import org.semanticweb.owlapi.reasoner.structural.StructuralReasoner;
import org.semanticweb.owlapi.reasoner.structural.StructuralReasonerFactory;
import org.semanticweb.owlapi.search.EntitySearcher;
import org.semanticweb.owlapi.util.SimpleIRIMapper;

public class GOtoBioPAX {

	public static OWLAnnotationProperty obo_id_prop;
	public static String biopax_base = "http://www.biopax.org/release/biopax-level3.owl#";
	public static String obo_base_1 = "http://purl.obolibrary.org/obo/";
	public static String obo_base_2 = "http://www.geneontology.org/formats/oboInOwl#";
	public static String go_cam_model_base = "http://model.geneontology.org/";
	public static String go_cam_biopax_base = "http://model.geneontology.org/biopax/";
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
		//for the moment need structual reasoner to do object properties
		OWLReasonerFactory reasonerFactory2 = new StructuralReasonerFactory(); 
		StructuralReasoner go_struct_reasoner = (StructuralReasoner)reasonerFactory2.createReasoner(go_cam_ont);	
		
		
		//get all of the biological process nodes in the cam, generate pathway biopax nodes for each
		Set<OWLNamedIndividual> bps = go_cam_reasoner.getInstances(GoCAM.bp_class, false).getFlattened();
		for(OWLNamedIndividual bp : bps) {
			Pathway pathway = factory.create(Pathway.class, bp.getIRI().toString()); 
			pathway = (Pathway)addDataSource(biopax, factory, pathway, ont_uri_string, go_cam_title , go_cam_comment);
			//note each pathway could, in principle, be linked to multiple biological processes
			Set<OWLClass> go_bp_classes = go_cam_reasoner.getTypes(bp, true).getFlattened();
			for(OWLClass go_bp_class : go_bp_classes) {
				//getting the names and ids
				pathway = (Pathway) addGoXref(biopax, factory, pathway, go_bp_class, "bp");
			}

			biopax.add(pathway);
			System.out.println("Pathway "+pathway);
			//now add the parts (reactions/functions)
			//noting that has_part now captures part_ofs thanks the whelk reasoner
			Set<OWLNamedIndividual> has_parts = go_cam_reasoner.getObjectPropertyValues(bp, GoCAM.has_part).getFlattened();
			for(OWLNamedIndividual mf : has_parts) {
				boolean is_binding = false;
				boolean is_catalysis = false;
				BiochemicalReaction reaction = factory.create(BiochemicalReaction.class, mf.getIRI().toString());
				Set<OWLClass> go_mf_classes = go_cam_reasoner.getTypes(mf, true).getFlattened();
				for(OWLClass go_mf_class : go_mf_classes) {	
					reaction = (BiochemicalReaction) addGoXref(biopax, factory, reaction, go_mf_class, "mf");
				}
				biopax.add(reaction);
				pathway.addPathwayComponent(reaction);
				System.out.println("Reaction "+reaction);
				//check for higher level types for inferences later on
				Set<OWLClass> go_mf_all_classes = go_cam_reasoner.getTypes(mf, false).getFlattened();
				if(go_mf_all_classes.contains(GoCAM.binding)) {
					is_binding = true;
				}else if(go_mf_all_classes.contains(GoCAM.catalytic_activity)) {
					is_catalysis = true;
				}
				//where does it happen? 				// occurs_in BFO:0000066  
				//note fairly large model difference here.  GO-CAM tags the event with the space or spaces, BioPAX tags the participant molecules
				//TODO should we also check for locations on pathways?
				//TODO how to handle multiple locations (transport)
				Set<OWLNamedIndividual> location_is = go_cam_reasoner.getObjectPropertyValues(mf, GoCAM.occurs_in).getFlattened();
				Set<OWLClass> ccs = new HashSet<OWLClass>();
				for(OWLNamedIndividual location : location_is) {
					Set<OWLClass> go_cc_classes = go_cam_reasoner.getTypes(location, true).getFlattened();
					ccs.addAll(go_cc_classes);
				}
				//now get all the molecules involved
				Set<PhysicalEntity> left_hand_side = new HashSet<PhysicalEntity>();
				//enablers
				Set<OWLNamedIndividual> enablers = go_cam_reasoner.getObjectPropertyValues(mf, GoCAM.enabled_by).getFlattened();
				for(OWLNamedIndividual enabler : enablers) {
					PhysicalEntity entity = goCamEntityToBioPAXPhysicalentity(enabler, factory, biopax, go_cam_reasoner);
					entity  = (PhysicalEntity) addLocations(factory, biopax,entity , ccs);
					System.out.println("enabler / controller "+enabler);
					//enabler is controller in biopax				
					Control control = factory.create(Control.class, enabler.getIRI().toString()+"as_control");
					//use ontology annotations to specify if possible - e.g. catalytic activity turns into catalysis
					if(is_catalysis) {
						control = factory.create(Catalysis.class, enabler.getIRI().toString()+"as_catalytic_control");
					}
					control.addControlled(reaction);					
					control.addController(entity);
					//TODO is it ever possible that some one would attach enabled_by for a repression??  doubt it
					control.setControlType(ControlType.ACTIVATION);
					biopax.add(control);
				}
				//get the inputs and outputs of the reaction
				Set<OWLNamedIndividual> inputs = go_cam_reasoner.getObjectPropertyValues(mf, GoCAM.has_input).getFlattened();
				//Note that GO-CAMs don't usually do this..  
				Set<OWLNamedIndividual> outputs = go_cam_reasoner.getObjectPropertyValues(mf, GoCAM.has_output).getFlattened();
				for(OWLNamedIndividual output : outputs) {
					PhysicalEntity entity = goCamEntityToBioPAXPhysicalentity(output, factory, biopax, go_cam_reasoner);
					entity = (PhysicalEntity) addLocations(factory, biopax,entity, ccs);
					reaction.addRight(entity);
					System.out.println("output "+output);
				}
				for(OWLNamedIndividual input : inputs) {
					PhysicalEntity entity = goCamEntityToBioPAXPhysicalentity(input, factory, biopax, go_cam_reasoner);
					entity = (PhysicalEntity) addLocations(factory, biopax,entity, ccs);
					reaction.addLeft(entity);				
					left_hand_side.add(entity);
					System.out.println("input "+input);
				}
				//Creates a complex by inferring that binding reactions between X, Y result in complex XY
				Complex intervening_controller = null;
				if(is_binding&&outputs.size()==0&&left_hand_side.size()>1) {
					intervening_controller = factory.create(Complex.class, go_cam_biopax_base+Math.random());
					for(PhysicalEntity entity : left_hand_side) {
						intervening_controller.addComponent(entity);
					}
					reaction.addRight(intervening_controller);
					biopax.add(intervening_controller);
				}
				//relationships between molecular functions
				go_cam_reasoner.getObjectPropertyValues(mf, GoCAM.causally_upstream_of);
				Set<OWLObjectPropertyAssertionAxiom> mf_props = go_cam_reasoner.getAllObjectPropertyValues(mf);
				for(OWLObjectPropertyAssertionAxiom prop_axiom : mf_props) {
					OWLObjectPropertyExpression prop = prop_axiom.getProperty();
					Set<OWLObjectPropertyExpression> super_props = go_struct_reasoner.getSuperObjectProperties(prop, false).getFlattened();

					if(super_props.contains(GoCAM.causally_upstream_of)) {
						PathwayStep sourceStep = factory.create(PathwayStep.class, mf.getIRI().toString()+"_step");
						sourceStep.addStepProcess(reaction);
						OWLNamedIndividual downstream = (OWLNamedIndividual) prop_axiom.getObject();
						PathwayStep targetStep = factory.create(PathwayStep.class, downstream.getIRI().toString()+"_step");
						Process downstream_process = (Process)biopax.getByID(downstream.getIRI().toString());
						
						if(downstream_process==null) {
							downstream_process = factory.create(BiochemicalReaction.class, downstream.getIRI().toString());
						}
						targetStep.addStepProcess(downstream_process);
						sourceStep.addNextStep(targetStep);
						Control control = factory.create(Control.class, mf.getIRI().toString()+"as_function_control");
						control.addControlled(downstream_process);	
						if(intervening_controller != null) {										
							control.addController(intervening_controller);
						}						
						if(super_props.contains(GoCAM.causally_upstream_of_positive_effect)) {
							control.setControlType(ControlType.ACTIVATION);
						}else if(super_props.contains(GoCAM.causally_upstream_of_negative_effect)) {
							control.setControlType(ControlType.INHIBITION);
						}
						addToModelWithIdCheck(biopax, sourceStep);
						addToModelWithIdCheck(biopax, targetStep);
						addToModelWithIdCheck(biopax, control);
					}
				}
			}
		}

		BioPAXIOHandler handler = new SimpleIOHandler();
		FileOutputStream outstream = new FileOutputStream(biopax_file);
		handler.convertToOWL(biopax, outstream);

		//SIFConverter converter;
		//=
		//		new SimpleInteractionConverter(new ControlRule());
		//		converter.writeInteractionsInSIF(level2, out);
	}

	public Entity addDataSource(Model biopax, BioPAXFactory factory, Entity entity, String data_source_uri, String data_source_name, String comment) {
		Provenance prov = factory.create(Provenance.class, data_source_uri);
		prov.addName(data_source_name);
		prov.addComment(comment);
		entity.addDataSource(prov);
		addToModelWithIdCheck(biopax, prov);
		return entity;
	}

	public Entity addGoXref(Model biopax, BioPAXFactory factory, Entity entity, OWLClass go_class, String go_root) {
		entity.addName(Helper.getaLabel(go_class, goplus));
		Collection<OWLAnnotation> ids = EntitySearcher.getAnnotationObjects(go_class, goplus, obo_id_prop);
		for(OWLAnnotation id_anno : ids) {
			String go_acc = id_anno.getValue().asLiteral().get().getLiteral();
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
		}

		return entity;
	}

	public BioPAXElement addToModelWithIdCheck(Model biopax, BioPAXElement element) {
		if(!biopax.containsID(element.getUri())) {
			biopax.add(element);
		}	
		return element;
	}

	//TODO find a way to get legible names to attach as labels
	public PhysicalEntity goCamEntityToBioPAXPhysicalentity(OWLNamedIndividual e, BioPAXFactory factory, Model biopax, OWLReasoner go_cam_reasoner) {
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
			addToModelWithIdCheck(biopax, mlc);
			for(OWLClass type : types) {
				SmallMoleculeReference reference = factory.create(SmallMoleculeReference.class, type.getIRI().toString());
				mlc.setEntityReference(reference);
				addToModelWithIdCheck(biopax, reference);
			}
			System.out.println("small "+mlc);
			return mlc;
		}else if(types.contains(GoCAM.go_complex)) {
			Complex complex = factory.create(Complex.class, e_iri);			
			addToModelWithIdCheck(biopax, complex);
			//			if(types.contains(GoCAM.go_complex)) {
			//				for(OWLClass type : types) {
			//
			//				}
			//			}
			System.out.println("complex "+complex);
			return complex;
		}else if(types.contains(GoCAM.chebi_protein)||guessProtein(types)) {
			Protein protein = factory.create(Protein.class, e_iri);
			addToModelWithIdCheck(biopax, protein);
			for(OWLClass type : types) {
				String p_type = type.getIRI().toString();
				if(p_type.startsWith("http://identifiers.org/uniprot/")) {
					String uniprot_id = p_type.replaceAll("http://identifiers.org/uniprot/", "");
					ProteinReference reference = factory.create(ProteinReference.class, p_type);
					protein.setEntityReference(reference);
					addToModelWithIdCheck(biopax, reference);					
					UnificationXref uxref = factory.create(UnificationXref.class, "http://pathwaycommons.org/pc2/#UnificationXref_uniprot_knowledgebase_"+uniprot_id);
					uxref.setDb("uniprot knowledgebase");
					uxref.setId(uniprot_id);
					reference.addXref(uxref);
					addToModelWithIdCheck(biopax, uxref);					
				}
			}		
			System.out.println("protein "+protein);
			return protein;
		}else{
			entity = factory.create(PhysicalEntity.class, e_iri);
			addToModelWithIdCheck(biopax, entity);
			System.out.println("other "+entity+" "+types);
		}
		return entity;
	}

	PhysicalEntity addLocations(BioPAXFactory factory, Model biopax, PhysicalEntity entity, Set<OWLClass> locations) {
		//add locations if there are any
		for(OWLClass location : locations) { 
			CellularLocationVocabulary loc = factory.create(CellularLocationVocabulary.class, go_cam_biopax_base+Math.random());
			String termname = Helper.getaLabel(location, goplus);
			loc.addTerm(termname);
			UnificationXref uni_xref = factory.create(UnificationXref.class, go_cam_biopax_base+Math.random());
			Collection<OWLAnnotation> ids = EntitySearcher.getAnnotationObjects(location, goplus, obo_id_prop);
			for(OWLAnnotation id_anno : ids) {
				OWLLiteral go_acc = id_anno.getValue().asLiteral().get();
				uni_xref.setDb("gene ontology");
				uni_xref.setId(go_acc.getLiteral());
			}
			loc.addXref(uni_xref);
			biopax.add(loc);
			biopax.add(uni_xref);
			//TODO revisit if/when we deal with multiple locations..
			entity.setCellularLocation(loc);
			System.out.println(entity+" loc "+termname);
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
