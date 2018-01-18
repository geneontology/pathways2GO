/**
 * 
 */
package noctua.exchange;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.Set;

import org.biopax.paxtools.controller.PathAccessor;
import org.biopax.paxtools.impl.MockFactory;
import org.biopax.paxtools.io.BioPAXIOHandler;
import org.biopax.paxtools.io.SimpleIOHandler;
import org.biopax.paxtools.model.BioPAXElement;
import org.biopax.paxtools.model.BioPAXFactory;
import org.biopax.paxtools.model.BioPAXLevel;
import org.biopax.paxtools.model.Model;
import org.biopax.paxtools.model.level3.*;
import org.biopax.paxtools.model.level3.Process;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.io.FileDocumentTarget;
import org.semanticweb.owlapi.model.AddAxiom;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLAnnotation;
import org.semanticweb.owlapi.model.OWLAxiom;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLClassAssertionAxiom;
import org.semanticweb.owlapi.model.OWLDataFactory;
import org.semanticweb.owlapi.model.OWLEntity;
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
import org.semanticweb.owlapi.vocab.OWLRDFVocabulary;

/**
 * @author bgood
 *
 */
public class BioPaxtoGO {
	public static final IRI noctua_test_iri = IRI.create("http://noctua.berkeleybop.org/download/gomodel:59dc728000000287/owl");
	public static final IRI go_lego_iri = IRI.create("http://purl.obolibrary.org/obo/go/extensions/go-lego.owl");
	public static final IRI obo_iri = IRI.create("http://purl.obolibrary.org/obo/");
	public static final IRI uniprot_iri = IRI.create("http://identifiers.org/uniprot/");
	public static final IRI biopax_iri = IRI.create("http://www.biopax.org/release/biopax-level3.owl#");
	OWLObjectProperty part_of, has_part, has_input, has_output, 
	provides_direct_input_for, directly_inhibits, directly_activates, occurs_in, enabled_by, regulated_by;

	OWLClass bp_class, continuant_class, protein_class, reaction_class, go_complex, molecular_function;
	/**
	 * @param args
	 * @throws FileNotFoundException 
	 * @throws OWLOntologyCreationException 
	 * @throws OWLOntologyStorageException 
	 */
	public static void main(String[] args) throws FileNotFoundException, OWLOntologyCreationException, OWLOntologyStorageException {
		BioPaxtoGO bp2g = new BioPaxtoGO();
		String input_biopax = "src/main/resources/reactome/reactome-input-109581.owl";
		String converted = "src/main/resources/reactome/reactome-output-109581.owl";
		bp2g.convert(input_biopax, converted);
	}

	public void convert(String input_biopax, String converted) throws FileNotFoundException, OWLOntologyCreationException, OWLOntologyStorageException  {
		//set up ontology 
		OWLOntologyManager ontman = OWLManager.createOWLOntologyManager();
		OWLOntology go_cam_ont = ontman.createOntology();
		//Will add classes and relations as we need them now. 
		//TODO Work on using imports later to ensure we don't produce incorrect ids..
		OWLDataFactory df = OWLManager.getOWLDataFactory();
		//biological process
		bp_class = df.getOWLClass(IRI.create(obo_iri + "GO_0008150")); 
		addLabel(ontman, go_cam_ont, df, bp_class, "Biological Process");
		//molecular function GO:0003674
		molecular_function = df.getOWLClass(IRI.create(obo_iri + "GO_0003674")); 
		addLabel(ontman, go_cam_ont, df, molecular_function, "Molecular Function");
		//continuant 
		continuant_class = df.getOWLClass(IRI.create(obo_iri + "BFO_0000002")); 
		addLabel(ontman, go_cam_ont, df, continuant_class, "Continuant");
		//protein
		protein_class = df.getOWLClass(IRI.create(biopax_iri + "Protein")); 
		addLabel(ontman, go_cam_ont, df, protein_class, "Protein");
		//reaction
		reaction_class = df.getOWLClass(IRI.create(biopax_iri + "Reaction")); 
		addLabel(ontman, go_cam_ont, df, reaction_class, "Reaction");
		//complex GO_0032991
		go_complex = df.getOWLClass(IRI.create(obo_iri + "GO_0032991")); 
		addLabel(ontman, go_cam_ont, df, go_complex, "Macromolecular Complex");		

		//tmp for viewing while debugging, will be taken care of by import and reasoning
		OWLSubClassOfAxiom prot = df.getOWLSubClassOfAxiom(protein_class, continuant_class);
		ontman.addAxiom(go_cam_ont, prot);
		ontman.applyChanges();
		OWLSubClassOfAxiom comp = df.getOWLSubClassOfAxiom(go_complex, continuant_class);
		ontman.addAxiom(go_cam_ont, comp);
		ontman.applyChanges();

		//part of
		part_of = df.getOWLObjectProperty(IRI.create(obo_iri + "BFO_0000050"));
		addLabel(ontman, go_cam_ont, df, part_of, "part of"); 
		//has part
		has_part = df.getOWLObjectProperty(IRI.create(obo_iri + "BFO_0000051"));
		addLabel(ontman, go_cam_ont, df, has_part, "has part");
		//has input 
		has_input = df.getOWLObjectProperty(IRI.create(obo_iri + "RO_0002233"));
		addLabel(ontman, go_cam_ont, df, has_input, "has input");
		//has output 
		has_output = df.getOWLObjectProperty(IRI.create(obo_iri + "RO_0002234"));
		addLabel(ontman, go_cam_ont, df, has_output, "has output");
		//directly provides input for (process to process)
		provides_direct_input_for = df.getOWLObjectProperty(IRI.create(obo_iri + "RO_0002413"));
		addLabel(ontman, go_cam_ont, df, provides_direct_input_for, "directly provides input for (process to process)");
		//RO_0002408 directly inhibits (process to process)
		directly_inhibits = df.getOWLObjectProperty(IRI.create(obo_iri + "RO_0002408"));
		addLabel(ontman, go_cam_ont, df, directly_inhibits, "directly inhibits (process to process)");
		//RO_0002406 directly activates (process to process)
		directly_activates = df.getOWLObjectProperty(IRI.create(obo_iri + "RO_0002406"));
		addLabel(ontman, go_cam_ont, df, directly_activates, "directly activates (process to process)");
		//BFO_0000066 occurs in
		occurs_in = df.getOWLObjectProperty(IRI.create(obo_iri + "BFO_0000066"));
		addLabel(ontman, go_cam_ont, df, occurs_in, "occurs in");
		//RO_0002333 enabled by
		enabled_by = df.getOWLObjectProperty(IRI.create(obo_iri + "RO_0002333"));
		addLabel(ontman, go_cam_ont, df, enabled_by, "enabled by");
		//RO_0002334 regulated by (processual) 
		regulated_by = df.getOWLObjectProperty(IRI.create(obo_iri + "RO_0002334"));
		addLabel(ontman, go_cam_ont, df, regulated_by, "regulated by");

		//read biopax pathway(s)
		BioPAXIOHandler handler = new SimpleIOHandler();
		FileInputStream f = new FileInputStream(input_biopax);
		Model model = handler.convertFromOWL(f);
		//list pathways
		for (Pathway currentPathway : model.getObjects(Pathway.class)){
			System.out.println("Pathway:"+currentPathway.getName()); 
			String uri = currentPathway.getUri();
			//make the OWL individual
			OWLNamedIndividual p = df.getOWLNamedIndividual(IRI.create(uri));
			//label it
			for(String pathway_name : currentPathway.getName()) {
				addLabel(ontman, go_cam_ont, df, p, pathway_name);
			}		
			//set a default type of biological process
			OWLClassAssertionAxiom isa_bp = df.getOWLClassAssertionAxiom(bp_class, p);
			ontman.addAxiom(go_cam_ont, isa_bp);
			ontman.applyChanges();
			//dig out any xreferenced GO processes 
			Set<Xref> xrefs = currentPathway.getXref();
			for(Xref xref : xrefs) {
				if(xref.getModelInterface().equals(RelationshipXref.class)) {
					RelationshipXref r = (RelationshipXref)xref;	    			
					//System.out.println(xref.getDb()+" "+xref.getId()+" "+xref.getUri()+"----"+r.getRelationshipType());
					//note that relationship types are not defined beyond text strings like RelationshipTypeVocabulary_gene ontology term for cellular process
					//you just have to know what to do.
					//here we add the referenced GO class as a type.  
					if(r.getDb().equals("GENE ONTOLOGY")) {
						OWLClass xref_go_parent = df.getOWLClass(IRI.create(obo_iri + r.getId().replaceAll(":", "_")));
						//add it into local hierarchy (temp pre inport)
						OWLSubClassOfAxiom tmp = df.getOWLSubClassOfAxiom(xref_go_parent, bp_class);
						ontman.addAxiom(go_cam_ont, tmp);
						OWLClassAssertionAxiom isa_xrefedbp = df.getOWLClassAssertionAxiom(xref_go_parent, p);
						ontman.addAxiom(go_cam_ont, isa_xrefedbp);
						ontman.applyChanges();
					}
				}
			}
			//get any pathway-part of relationships
			for(Pathway parent_pathway : currentPathway.getPathwayComponentOf()) {
				//System.out.println(currentPathway.getName()+" is a Component of Pathway:"+parent_pathway.getName()); 
				OWLNamedIndividual parent = df.getOWLNamedIndividual(IRI.create(parent_pathway.getUri()));
				OWLObjectPropertyAssertionAxiom add_partof_axiom = df.getOWLObjectPropertyAssertionAxiom(part_of, p, parent);
				AddAxiom addAxiom = new AddAxiom(go_cam_ont, add_partof_axiom);
				ontman.applyChanges(addAxiom);
			}

			//below mapped from Chris Mungall's
			//prolog rules https://github.com/cmungall/pl-sysbio/blob/master/prolog/sysbio/bp2lego.pl
			//looking at this, prolog/graph solution seems much more elegant... 

			//get the steps of the pathway 			
			Set<PathwayStep> steps = currentPathway.getPathwayOrder();
			for(PathwayStep step1 : steps) {
				Set<Process> events = step1.getStepProcess();
				Set<PathwayStep> step2s = step1.getNextStep();
				for(PathwayStep step2 : step2s) {
					Set<Process> nextEvents = step2.getStepProcess();
					for(Process event : events) {
						for(Process nextEvent : nextEvents) {
							OWLNamedIndividual e1 = df.getOWLNamedIndividual(IRI.create(event.getUri()));
							OWLNamedIndividual e2 = df.getOWLNamedIndividual(IRI.create(nextEvent.getUri()));
							//	Event directly_provides_input_for NextEvent
							//	 <==
							//		Step stepProcess Event,
							//		Step nextStep NextStep,
							//		NextStep stepProcess NextEvent,
							//		biochemicalReaction(Event),
							//		biochemicalReaction(NextEvent).
							if((event.getModelInterface().equals(BiochemicalReaction.class))&&
									(nextEvent.getModelInterface().equals(BiochemicalReaction.class))) {
								OWLObjectPropertyAssertionAxiom add_step_axiom = df.getOWLObjectPropertyAssertionAxiom(provides_direct_input_for, e1, e2);
								AddAxiom addStepAxiom = new AddAxiom(go_cam_ont, add_step_axiom);
								ontman.applyChanges(addStepAxiom);
							}
						}
					}
				}
			}

			//get the pieces of the pathway
			//Process subsumes Pathway and Reaction.  A pathway may have either or both reaction or pathway components.  
			for(Process process : currentPathway.getPathwayComponent()) {
				//System.out.println("Process "+ process.getName()+" of "+currentPathway.getName()); 
				//If this subprocess is a Pathway, ignore it here as it will be processed in the all pathways loop 
				//above and the part of relationship will be captured there via the .getPathwayComponentOf method
				//Otherwise it will be a Reaction - which holds most of the information.  
				if(process.getModelInterface().equals(BiochemicalReaction.class)) {
					BiochemicalReaction reaction = (BiochemicalReaction)process;
					defineReactionEntity(ontman, go_cam_ont, df, reaction);
				}
			}
		}	
		//export
		FileDocumentTarget outfile = new FileDocumentTarget(new File(converted));
		ontman.saveOntology(go_cam_ont,outfile);
	}


	public OWLOntology addLabel(OWLOntologyManager ontman, OWLOntology go_cam_ont, OWLDataFactory df, OWLEntity entity, String label) {
		OWLLiteral lbl = df.getOWLLiteral(label);
		OWLAnnotation label_anno = df.getOWLAnnotation(df.getOWLAnnotationProperty(OWLRDFVocabulary.RDFS_LABEL.getIRI()), lbl);
		OWLAxiom labelaxiom = df.getOWLAnnotationAssertionAxiom(entity.getIRI(), label_anno);
		ontman.addAxiom(go_cam_ont, labelaxiom);
		ontman.applyChanges();
		return go_cam_ont;
	}


	/**
	 * Given a BioPax entity and an ontology, add a GO_CAM structured OWLIndividual representing the entity into the ontology
	 * 	//Done: Complex, Protein, SmallMolecule, Dna 
		//TODO Dna, DnaRegion, RnaRegion
	 * @param ontman
	 * @param go_cam_ont
	 * @param df
	 * @param entity
	 * @return
	 */
	public OWLOntology defineReactionEntity(OWLOntologyManager ontman, OWLOntology go_cam_ont, OWLDataFactory df, Entity entity) {
		//add entity to ontology, whatever it is
		OWLNamedIndividual e = df.getOWLNamedIndividual(IRI.create(entity.getUri()));
		String entity_name = entity.getDisplayName();
		addLabel(ontman, go_cam_ont, df, e, entity_name);
		//		//set a default type of continuant to start
		//		OWLClassAssertionAxiom isa_continuant = df.getOWLClassAssertionAxiom(continuant_class, e);
		//		ontman.addAxiom(go_cam_ont, isa_continuant);
		//		ontman.applyChanges();	
		//Protein	
		if(entity.getModelInterface().equals(Protein.class)) {
			Protein protein = (Protein)entity;
			EntityReference entity_ref = protein.getEntityReference();	
			if(entity_ref!=null) {
				Set<Xref> p_xrefs = entity_ref.getXref();				
				for(Xref xref : p_xrefs) {
					if(xref.getModelInterface().equals(UnificationXref.class)) {
						UnificationXref uref = (UnificationXref)xref;	
						if(uref.getDb().startsWith("UniProt")) {
							String id = uref.getId();
							//going from original conversion results here, the uri for uniprot proteins is like http://identifiers.org/uniprot/P09848
							//recent output from prolog converter produces &UniProtIsoform;Q14790 but not sure what the namespace is that is referred to
							//create the specific protein class
							OWLClass uniprotein_class = df.getOWLClass(IRI.create(uniprot_iri + id)); 
							OWLSubClassOfAxiom prot_instance = df.getOWLSubClassOfAxiom(uniprotein_class, protein_class);
							ontman.addAxiom(go_cam_ont, prot_instance);
							ontman.applyChanges();											
							//name the class with the uniprot id for now..
							//NOTE different protein versions are grouped together into the same root class by the conversion
							//e.g. Q9UKV3 gets the uniproteins ACIN1, ACIN1(1-1093), ACIN1(1094-1341)
							addLabel(ontman, go_cam_ont, df, uniprotein_class, id);
							//until something is imported that understands the uniprot entities, assert that they are proteins
							OWLClassAssertionAxiom isa_uniprotein = df.getOWLClassAssertionAxiom(uniprotein_class, e);
							ontman.addAxiom(go_cam_ont, isa_uniprotein);
							ontman.applyChanges();
						}
					}
				}
			}else { //no entity reference so look for parts 
				Set<PhysicalEntity> prot_parts = protein.getMemberPhysicalEntity();
				if(prot_parts!=null) {
					for(PhysicalEntity prot_part : prot_parts) {
						OWLNamedIndividual prot_part_entity = df.getOWLNamedIndividual(IRI.create(prot_part.getUri()));
						//hook up parts	
						OWLObjectPropertyAssertionAxiom add_cpart_axiom = df.getOWLObjectPropertyAssertionAxiom(has_part, e, prot_part_entity);
						AddAxiom addCpartAxiom = new AddAxiom(go_cam_ont, add_cpart_axiom);
						ontman.applyChanges(addCpartAxiom);
						//define them = hopefully get out a name and a class for the sub protein.	
						go_cam_ont = defineReactionEntity(ontman, go_cam_ont, df, prot_part);
					}
				}
			}
		}
		//Dna (gene)
		else if(entity.getModelInterface().equals(Dna.class)) {
			Dna dna = (Dna)entity;
			EntityReference entity_ref = dna.getEntityReference();	
			if(entity_ref!=null) {
				Set<Xref> p_xrefs = entity_ref.getXref();
				for(Xref xref : p_xrefs) {
					if(xref.getModelInterface().equals(UnificationXref.class)) {
						UnificationXref uref = (UnificationXref)xref;	
						if(uref.getDb().equals("ENSEMBL")) {
							String id = uref.getId();
							OWLClass dna_class = df.getOWLClass(IRI.create(obo_iri + id)); 
							OWLSubClassOfAxiom dna_instance = df.getOWLSubClassOfAxiom(dna_class, continuant_class);
							ontman.addAxiom(go_cam_ont, dna_instance);
							ontman.applyChanges();											
							//name the class with the gene id
							addLabel(ontman, go_cam_ont, df, dna_class, id);
							//assert a continuant
							OWLClassAssertionAxiom isa_dna = df.getOWLClassAssertionAxiom(dna_class, e);
							ontman.addAxiom(go_cam_ont, isa_dna);
							ontman.applyChanges();
						}
					}
				}
			}
		}
		//SmallMolecule
		else if(entity.getModelInterface().equals(SmallMolecule.class)) {
			SmallMolecule mlc = (SmallMolecule)entity;
			EntityReference entity_ref = mlc.getEntityReference();	
			if(entity_ref!=null) {
				Set<Xref> p_xrefs = entity_ref.getXref();
				for(Xref xref : p_xrefs) {
					if(xref.getModelInterface().equals(UnificationXref.class)) {
						UnificationXref uref = (UnificationXref)xref;	
						if(uref.getDb().equals("ChEBI")) {
							String id = uref.getId().replace(":", "_");
							OWLClass mlc_class = df.getOWLClass(IRI.create(obo_iri + id)); 
							OWLSubClassOfAxiom mlc_instance = df.getOWLSubClassOfAxiom(mlc_class, continuant_class);
							ontman.addAxiom(go_cam_ont, mlc_instance);
							ontman.applyChanges();											
							//name the class with the chebi id
							addLabel(ontman, go_cam_ont, df, mlc_class, id);
							//assert its a chemical instance
							OWLClassAssertionAxiom isa_mlc = df.getOWLClassAssertionAxiom(mlc_class, e);
							ontman.addAxiom(go_cam_ont, isa_mlc);
							ontman.applyChanges();
						}
					}
				}
			}
		}
		//Complex 
		else if(entity.getModelInterface().equals(Complex.class)) {
			Complex complex = (Complex)entity;
			OWLClassAssertionAxiom isa_complex = df.getOWLClassAssertionAxiom(go_complex, e);
			ontman.addAxiom(go_cam_ont, isa_complex);
			ontman.applyChanges();

			//get parts of the complex - allows nesting of complexes
			Set<PhysicalEntity> complex_parts = complex.getComponent();
			//note that biopx doc suggests not to use this.. but its there in reactome in sime places
			Set<PhysicalEntity> members = complex.getMemberPhysicalEntity();
			if(members!=null&&members.size()>0) {
				complex_parts.addAll(members);
			}
			//note that complex.getComponent() apparently violates the rules in its documentation which stipulate that it should return
			//a flat representation of the parts of the complex (e.g. proteins) and not nested complexes (which the reactome biopax does here)
			for(PhysicalEntity component : complex_parts) {
				//go_cam_ont = addComplexComponent(ontman, go_cam_ont, df, component, entity);
				OWLNamedIndividual component_entity = df.getOWLNamedIndividual(IRI.create(component.getUri()));
				//OWLNamedIndividual complex_o = df.getOWLNamedIndividual(IRI.create(complex.getUri()));
				//OWLObjectPropertyAssertionAxiom add_cpart_axiom = df.getOWLObjectPropertyAssertionAxiom(has_part, complex_o, component_entity);
				//hook up parts	
				OWLObjectPropertyAssertionAxiom add_cpart_axiom = df.getOWLObjectPropertyAssertionAxiom(has_part, e, component_entity);
				AddAxiom addCpartAxiom = new AddAxiom(go_cam_ont, add_cpart_axiom);
				ontman.applyChanges(addCpartAxiom);
				//now define complex components - and recurse for sub complexes	
				go_cam_ont = defineReactionEntity(ontman, go_cam_ont, df, component);
			}
		}
		else if(entity.getModelInterface().equals(BiochemicalReaction.class)){
			//type it
			BiochemicalReaction reaction = (BiochemicalReaction)(entity);
			OWLClassAssertionAxiom isa_reaction = df.getOWLClassAssertionAxiom(reaction_class, e);
			ontman.addAxiom(go_cam_ont, isa_reaction);
			ontman.applyChanges();				
			//connect reaction to its pathway(s) via part of
			Set<Pathway> pathways = reaction.getPathwayComponentOf();
			for(Pathway pathway : pathways) {
				OWLNamedIndividual p = df.getOWLNamedIndividual(IRI.create(pathway.getUri()));
				OWLObjectPropertyAssertionAxiom add_partof_axiom = df.getOWLObjectPropertyAssertionAxiom(part_of, e, p);
				AddAxiom addAxiom = new AddAxiom(go_cam_ont, add_partof_axiom);
				ontman.applyChanges(addAxiom);
			}
			//Create entities for reaction components
			Set<Entity> participants = reaction.getParticipant();
			for(Entity participant : participants) {
				//figure out its nature and capture that
				defineReactionEntity(ontman, go_cam_ont, df, participant);		
				//link to participants in reaction
				//biopax#left -> obo:input , biopax#right -> obo:output
				Set<PhysicalEntity> inputs = reaction.getLeft();
				for(PhysicalEntity input : inputs) {
					OWLNamedIndividual input_entity = df.getOWLNamedIndividual(IRI.create(input.getUri()));
					OWLObjectPropertyAssertionAxiom add_input_axiom = df.getOWLObjectPropertyAssertionAxiom(has_input, e, input_entity);
					AddAxiom addInputAxiom = new AddAxiom(go_cam_ont, add_input_axiom);
					ontman.applyChanges(addInputAxiom);
				}
				Set<PhysicalEntity> outputs = reaction.getRight();
				for(PhysicalEntity output : outputs) {
					OWLNamedIndividual output_entity = df.getOWLNamedIndividual(IRI.create(output.getUri()));
					OWLObjectPropertyAssertionAxiom add_output_axiom = df.getOWLObjectPropertyAssertionAxiom(has_output, e, output_entity);
					AddAxiom addOutputAxiom = new AddAxiom(go_cam_ont, add_output_axiom);
					ontman.applyChanges(addOutputAxiom);
				}
			}
			//find controllers 
			//			Event directly_inhibits NextEvent
			//			   <==
			//			   control(Event),
			//			   controlled(Event,NextEvent),
			//			   controlType(Event,literal(type(_,'INHIBITION'))).
			

			
			Set<Control> controllers = reaction.getControlledOf();
			for(Control controller : controllers) {
				ControlType ctype = controller.getControlType();
				Set<Controller> controller_entities = controller.getController();
				for(Controller controller_entity : controller_entities) {
					CellularLocationVocabulary loc = ((PhysicalEntity) controller_entity).getCellularLocation();
					OWLNamedIndividual loc_e = df.getOWLNamedIndividual(loc.getUri());
					//hook up the location
					OWLObjectPropertyAssertionAxiom add_loc_axiom = df.getOWLObjectPropertyAssertionAxiom(occurs_in, e, loc_e);
					AddAxiom addLocAxiom = new AddAxiom(go_cam_ont, add_loc_axiom);
					ontman.applyChanges(addLocAxiom);
					//dig out the GO cellular location for the controller and create an individual for it
					Set<Xref> xrefs = loc.getXref();
					for(Xref xref : xrefs) {
						if(xref.getModelInterface().equals(UnificationXref.class)) {
							UnificationXref uref = (UnificationXref)xref;	    			
							//here we add the referenced GO class as a type.  
							if(uref.getDb().equals("GENE ONTOLOGY")) {
								OWLClass xref_go_loc = df.getOWLClass(IRI.create(obo_iri + uref.getId().replaceAll(":", "_")));
								Set<XReferrable> refs = uref.getXrefOf();
								String term = "";
								for(XReferrable ref : refs) {
									term = ref.toString().replaceAll("CellularLocationVocabulary_", "");
									break;
								}
								addLabel(ontman, go_cam_ont, df, xref_go_loc, term);
								OWLClassAssertionAxiom isa_loc = df.getOWLClassAssertionAxiom(xref_go_loc, loc_e);
								ontman.addAxiom(go_cam_ont, isa_loc);
								ontman.applyChanges();
							}
						}
					}
					//the protein or complex
					OWLNamedIndividual c_e = df.getOWLNamedIndividual(IRI.create(controller_entity.getUri()));
					//make an individual of the class molecular function
					//TODO likely need to come up with a pseudo-random URI here instead???
					OWLIndividual a_mf = df.getOWLNamedIndividual(controller_entity.getUri()+"_function_"+Math.random()); 
					OWLClassAssertionAxiom isa_function = df.getOWLClassAssertionAxiom(molecular_function, a_mf);
					ontman.addAxiom(go_cam_ont, isa_function);
					ontman.applyChanges();
					//the controller enables that function
					OWLObjectPropertyAssertionAxiom add_func_axiom = df.getOWLObjectPropertyAssertionAxiom(enabled_by, a_mf, c_e);
					AddAxiom addFuncAxiom = new AddAxiom(go_cam_ont, add_func_axiom);
					ontman.applyChanges(addFuncAxiom);
					//define how the molecular function (process) relates to the reaction (process)
					if(ctype.toString().startsWith("INHIBITION")){
						// Event directly_inhibits NextEvent 
						OWLObjectPropertyAssertionAxiom add_step_axiom = df.getOWLObjectPropertyAssertionAxiom(directly_inhibits, a_mf, e);
						AddAxiom addStepAxiom = new AddAxiom(go_cam_ont, add_step_axiom);
						ontman.applyChanges(addStepAxiom);
						//System.out.println(a_mf +" inhibits "+e);
					}else if(ctype.toString().startsWith("ACTIVATION")){
						// Event directly_ACTIVATES NextEvent 
						OWLObjectPropertyAssertionAxiom add_step_axiom = df.getOWLObjectPropertyAssertionAxiom(directly_activates, a_mf, e);
						AddAxiom addStepAxiom = new AddAxiom(go_cam_ont, add_step_axiom);
						ontman.applyChanges(addStepAxiom);
						//System.out.println(a_mf +" activates "+e);
					}else {
						//default to regulates
						OWLObjectPropertyAssertionAxiom add_step_axiom = df.getOWLObjectPropertyAssertionAxiom(regulated_by, e, a_mf);
						AddAxiom addStepAxiom = new AddAxiom(go_cam_ont, add_step_axiom);
						ontman.applyChanges(addStepAxiom);
						//System.out.println(e +" regulated_by "+a_mf);
					}
				}
			}
		}
		return go_cam_ont;
	}

}
