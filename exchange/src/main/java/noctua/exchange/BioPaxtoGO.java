/**
 * 
 */
package noctua.exchange;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.stream.Stream;

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
import org.coode.owlapi.turtle.TurtleOntologyFormat;
import org.eclipse.rdf4j.model.vocabulary.DC;
import org.eclipse.rdf4j.model.vocabulary.RDF;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.io.FileDocumentTarget;
import org.semanticweb.owlapi.io.StreamDocumentTarget;
import org.semanticweb.owlapi.model.AddAxiom;
import org.semanticweb.owlapi.model.AddImport;
import org.semanticweb.owlapi.model.AxiomType;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLAnnotation;
import org.semanticweb.owlapi.model.OWLAnnotationProperty;
import org.semanticweb.owlapi.model.OWLAnnotationSubject;
import org.semanticweb.owlapi.model.OWLAxiom;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLClassAssertionAxiom;
import org.semanticweb.owlapi.model.OWLClassExpression;
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
import org.semanticweb.owlapi.model.OntologyConfigurator;
import org.semanticweb.owlapi.search.EntitySearcher;
import org.semanticweb.owlapi.search.Searcher;
import org.semanticweb.owlapi.util.OWLEntityRemover;
import org.semanticweb.owlapi.vocab.OWLRDFVocabulary;

/**
 * @author bgood
 *
 */
public class BioPaxtoGO {
	public static final IRI noctua_test_iri = IRI.create("http://noctua.berkeleybop.org/download/gomodel:59dc728000000287/owl");


	/**
	 * @param args
	 * @throws FileNotFoundException 
	 * @throws OWLOntologyCreationException 
	 * @throws OWLOntologyStorageException 
	 * @throws UnsupportedEncodingException 
	 */
	public static void main(String[] args) throws FileNotFoundException, OWLOntologyCreationException, OWLOntologyStorageException, UnsupportedEncodingException {
		BioPaxtoGO bp2g = new BioPaxtoGO();
		String input_biopax = 
				"src/main/resources/reactome/wnt/wnt_tcf_full.owl";
				//"src/main/resources/reactome/Homo_sapiens.owl";
				//"src/main/resources/reactome/glycolysis/glyco_biopax.owl";
				//"src/main/resources/reactome/reactome-input-109581.owl";
		String converted = 
				//"/Users/bgood/Desktop/test/converted-wnt-full-";
				"/Users/bgood/Desktop/test/converted-";
				//"/Users/bgood/Documents/GitHub/my-noctua-models/models/reactome-homosapiens-";
				//"src/main/resources/reactome/output/test/reactome-output-glyco-"; 
				//"src/main/resources/reactome/output/reactome-output-109581-";
		//String converted_full = "/Users/bgood/Documents/GitHub/my-noctua-models/models/reactome-homosapiens-wnt-tcf-full";
		boolean split_by_pathway = true;
		boolean add_lego_import = false;
		String base_title = "default pathway ontology"; 
		String base_contributor = "reactome contributor"; 
		String base_provider = "https://reactome.org";
		bp2g.convert(input_biopax, converted, split_by_pathway, add_lego_import, base_title, base_contributor, base_provider);
	}

	
	
	private void convert(
			String input_biopax, String converted, 
			boolean split_by_pathway, boolean add_lego_import,
			String base_title, String base_contributor, String base_provider) throws FileNotFoundException, OWLOntologyCreationException, OWLOntologyStorageException, UnsupportedEncodingException  {
		//read biopax pathway(s)
		BioPAXIOHandler handler = new SimpleIOHandler();
		FileInputStream f = new FileInputStream(input_biopax);
		Model model = handler.convertFromOWL(f);

		//set up ontology (used if not split)
		GoCAM go_cam = new GoCAM("Meta Pathway Ontology", base_contributor, null, base_provider, add_lego_import);

		//list pathways
		for (Pathway currentPathway : model.getObjects(Pathway.class)){
			System.out.println("Pathway:"+currentPathway.getName()); 
			if(split_by_pathway) {
				//re initialize for each pathway
				String reactome_id = null;
				String contributor_link = "https://reactome.org";
				//See if there is a specific pathway reference to allow a direct link
				Set<Xref> xrefs = currentPathway.getXref();
				for(Xref xref : xrefs) {
					if(xref.getModelInterface().equals(UnificationXref.class)) {
						UnificationXref r = (UnificationXref)xref;	    			
						if(r.getDb().equals("Reactome")) {
							reactome_id = r.getId();
							if(reactome_id.startsWith("R-HSA")) {
								contributor_link = "https://reactome.org/content/detail/"+reactome_id;
								//or https://reactome.org/PathwayBrowser/#/ to go right to pathway browser
								break;
							}
						}
					}
				}			
				go_cam = new GoCAM("Reactome:"+currentPathway.getDisplayName(), contributor_link, null, base_provider, add_lego_import);
			}

			String uri = currentPathway.getUri();
			//make the OWL individual representing the pathway so it can be used below
			OWLNamedIndividual p = go_cam.df.getOWLNamedIndividual(IRI.create(uri));
			//define it (add types etc)
			definePathwayEntity(go_cam, currentPathway, split_by_pathway);

			//get and set parent pathways
			for(Pathway parent_pathway : currentPathway.getPathwayComponentOf()) {				
				//System.out.println(currentPathway.getName()+" is a Component of Pathway:"+parent_pathway.getName()); 
				OWLNamedIndividual parent = go_cam.df.getOWLNamedIndividual(IRI.create(parent_pathway.getUri()));
				go_cam.addObjectPropertyAssertion(p, GoCAM.part_of, parent);
				definePathwayEntity(go_cam, parent_pathway, split_by_pathway);
			}

			//below mapped from Chris Mungall's
			//prolog rules https://github.com/cmungall/pl-sysbio/blob/master/prolog/sysbio/bp2lego.pl
			//looking at this, prolog/graph solution seems much more elegant... 

			//get the steps of the pathway (process (aka event) can be either a pathway or a reaction) 

			//Event directly_provides_input_for NextEvent
			//<==	
			//pathway pathway_order pathwayStep1
			//pathwayStep1 step_process process
			//pathwayStep1 next_step pathwayStep2
			//PathwayStep2 step_process process2

			Set<PathwayStep> steps = currentPathway.getPathwayOrder();
			for(PathwayStep step1 : steps) {
				Set<Process> events = step1.getStepProcess();
				Set<PathwayStep> step2s = step1.getNextStep();
				for(PathwayStep step2 : step2s) {
					Set<Process> nextEvents = step2.getStepProcess();
					for(Process event : events) {
						for(Process nextEvent : nextEvents) {
							//	Event directly_provides_input_for NextEvent
							//	 <==
							//		Step stepProcess Event,
							//		Step nextStep NextStep,
							//		NextStep stepProcess NextEvent,
							//		biochemicalReaction(Event),
							//		biochemicalReaction(NextEvent).
							if((event.getModelInterface().equals(BiochemicalReaction.class))&&
									(nextEvent.getModelInterface().equals(BiochemicalReaction.class))) {
								OWLNamedIndividual e1 = go_cam.df.getOWLNamedIndividual(IRI.create(event.getUri()));
								go_cam.addLabel(e1, event.getDisplayName());
								OWLNamedIndividual e2 = go_cam.df.getOWLNamedIndividual(IRI.create(nextEvent.getUri()));
								go_cam.addLabel(e2, nextEvent.getDisplayName());
								go_cam.addObjectPropertyAssertion(e1, GoCAM.provides_direct_input_for, e2);
							}
							//							else {
							//
							//							}
							//							System.out.println(event+" could provide input for "+nextEvent);
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
					defineReactionEntity(go_cam, reaction, null);
					//add the child pathway (one level) when splitting up into individual pathways (unnesting)
				}else if(split_by_pathway&&process.getModelInterface().equals(Pathway.class)){
					OWLNamedIndividual child = go_cam.df.getOWLNamedIndividual(IRI.create(process.getUri()));
					go_cam.addObjectPropertyAssertion(p, GoCAM.has_part, child);
					definePathwayEntity(go_cam, (Pathway)process, split_by_pathway);	
				}
			}
			if(split_by_pathway) {
				String n = currentPathway.getDisplayName();
				n = n.replaceAll("/", "-");	
				n = n.replaceAll(" ", "_");
				String outfilename = converted+n+".ttl";	
				go_cam.writeGoCAM(outfilename);
				//reset for next pathway.
				go_cam.ontman.clearOntologies();
			} 
		}	
		//export all
		if(!split_by_pathway) {
			go_cam.writeGoCAM(converted+".ttl");
		}
	}


	private void definePathwayEntity(GoCAM go_cam, Pathway pathway, boolean split_by_pathway) {
		IRI pathway_iri = IRI.create(pathway.getUri());
		OWLNamedIndividual pathway_e = go_cam.df.getOWLNamedIndividual(pathway_iri);		
		String contributer_uri = "placeholder for contributor uri";
		Date now = new Date();
		SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
		String provider_uri = "placeholder for provider uri";
		go_cam.addAnnotations2Individual(pathway_iri, contributer_uri, sdf.format(now), provider_uri);
		
		String name = pathway.getDisplayName();
		if(!split_by_pathway) {
			name="Full_"+name;
		}
		
		go_cam.addLabel(pathway_e, pathway.getDisplayName());
		//set a default type of biological process
		//		OWLClassAssertionAxiom p_isa_bp = df.getOWLClassAssertionAxiom(bp_class, pathway_e);
		//		ontman.addAxiom(go_cam_ont, p_isa_bp);
		//		ontman.applyChanges();
		//dig out any xreferenced GO processes and assign them as types
		Set<Xref> xrefs = pathway.getXref();
		for(Xref xref : xrefs) {
			if(xref.getModelInterface().equals(RelationshipXref.class)) {
				RelationshipXref r = (RelationshipXref)xref;	    			
				//System.out.println(xref.getDb()+" "+xref.getId()+" "+xref.getUri()+"----"+r.getRelationshipType());
				//note that relationship types are not defined beyond text strings like RelationshipTypeVocabulary_gene ontology term for cellular process
				//you just have to know what to do.
				//here we add the referenced GO class as a type.  
				if(r.getDb().equals("GENE ONTOLOGY")) {
					OWLClass xref_go_parent = go_cam.df.getOWLClass(IRI.create(GoCAM.obo_iri + r.getId().replaceAll(":", "_")));
					//add it into local hierarchy (temp pre import)
					go_cam.addSubclassAssertion(xref_go_parent, GoCAM.bp_class);
					go_cam.addTypeAssertion(pathway_e, xref_go_parent);
				}
			}
		}
		return;
	}




	private String getUniprotProteinId(Protein protein) {
		String id = null;
		EntityReference entity_ref = protein.getEntityReference();	
		if(entity_ref!=null) {
			Set<Xref> p_xrefs = entity_ref.getXref();				
			for(Xref xref : p_xrefs) {
				if(xref.getModelInterface().equals(UnificationXref.class)) {
					UnificationXref uref = (UnificationXref)xref;	
					if(uref.getDb().startsWith("UniProt")) {
						id = uref.getId();
						break;//TODO consider case where there is more than one id..
					}
				}
			}
		}
		return id;
	}

	/**
	 * Given a BioPax entity and an ontology, add a GO_CAM structured OWLIndividual representing the entity into the ontology
	 * 	//Done: Complex, Protein, SmallMolecule, Dna 
		//TODO DnaRegion, RnaRegion
	 * @param ontman
	 * @param go_cam_ont
	 * @param df
	 * @param entity
	 * @return
	 */
	private void defineReactionEntity(GoCAM go_cam, Entity entity, IRI this_iri) {
		//add entity to ontology, whatever it is
		OWLNamedIndividual e = null;
		if(this_iri!=null) {
			e = go_cam.df.getOWLNamedIndividual(this_iri);
		}else {
			e = go_cam.df.getOWLNamedIndividual(IRI.create(entity.getUri()));
		}

		String entity_name = entity.getDisplayName();
		go_cam.addLabel(e, entity_name);
		//attempt to localize the entity (only if Physical Entity because that is how Reactome views existence in space)
		if(entity instanceof PhysicalEntity) {
			CellularLocationVocabulary loc = ((PhysicalEntity) entity).getCellularLocation();
			if(loc!=null) {
				OWLNamedIndividual loc_e = go_cam.df.getOWLNamedIndividual(loc.getUri()+e.hashCode());
				//hook up the location
				go_cam.addObjectPropertyAssertion(e, GoCAM.located_in, loc_e);
				//dig out the GO cellular location and create an individual for it
				Set<Xref> xrefs = loc.getXref();
				for(Xref xref : xrefs) {
					if(xref.getModelInterface().equals(UnificationXref.class)) {
						UnificationXref uref = (UnificationXref)xref;	    			
						//here we add the referenced GO class as a type.  
						if(uref.getDb().equals("GENE ONTOLOGY")) {
							OWLClass xref_go_loc = go_cam.df.getOWLClass(IRI.create(GoCAM.obo_iri + uref.getId().replaceAll(":", "_")));
							Set<XReferrable> refs = uref.getXrefOf();
							String term = "";
							for(XReferrable ref : refs) {
								term = ref.toString().replaceAll("CellularLocationVocabulary_", "");
								break;
							}
							go_cam.addLabel(xref_go_loc, term);
							go_cam.addTypeAssertion(loc_e, xref_go_loc);
						}
					}
				}
			}
		}	
		//Protein	
		if(entity.getModelInterface().equals(Protein.class)) {
			Protein protein = (Protein)entity;
			String id = getUniprotProteinId(protein);
			if(id!=null) {
				//create the specific protein class
				OWLClass uniprotein_class = go_cam.df.getOWLClass(IRI.create(GoCAM.uniprot_iri + id)); 
				go_cam.addSubclassAssertion(uniprotein_class, GoCAM.protein_class);										
				//name the class with the uniprot id for now..
				//NOTE different protein versions are grouped together into the same root class by the conversion
				//e.g. Q9UKV3 gets the uniproteins ACIN1, ACIN1(1-1093), ACIN1(1094-1341)
				go_cam.addLabel(uniprotein_class, id);
				//until something is imported that understands the uniprot entities, assert that they are proteins
				go_cam.addTypeAssertion(e,  uniprotein_class);
			}else { //no entity reference so look for parts 
				Set<PhysicalEntity> prot_parts = protein.getMemberPhysicalEntity();
				if(prot_parts!=null) {
					for(PhysicalEntity prot_part : prot_parts) {
						OWLNamedIndividual prot_part_entity = go_cam.df.getOWLNamedIndividual(IRI.create(prot_part.getUri()+e.hashCode())); //define it independently within this context
						//hook up parts	
						go_cam.addObjectPropertyAssertion(e, GoCAM.has_part, prot_part_entity);
						//define them = hopefully get out a name and a class for the sub protein.	
						defineReactionEntity(go_cam, prot_part, prot_part_entity.getIRI());
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
							OWLClass dna_class = go_cam.df.getOWLClass(IRI.create(GoCAM.obo_iri + id)); 
							go_cam.addSubclassAssertion(dna_class, GoCAM.continuant_class);										
							//name the class with the gene id
							go_cam.addLabel(dna_class, id);
							//assert a continuant
							go_cam.addTypeAssertion(e, dna_class);
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
							OWLClass mlc_class = go_cam.df.getOWLClass(IRI.create(GoCAM.obo_iri + id)); 
							go_cam.addSubclassAssertion(mlc_class, GoCAM.continuant_class);										
							//name the class with the chebi id
							go_cam.addLabel(mlc_class, id);
							//assert its a chemical instance
							go_cam.addTypeAssertion(e, mlc_class);
						}
					}
				}
			}
		}
		//Complex 
		else if(entity.getModelInterface().equals(Complex.class)) {
			Complex complex = (Complex)entity;
			//recursively get all parts
			Set<PhysicalEntity> level1 = complex.getComponent();
			level1.addAll(complex.getMemberPhysicalEntity());
			Set<PhysicalEntity> complex_parts = flattenNest(level1, null);
			//Now decide if, in GO-CAM, it should be a complex or not
			//If the complex has only 1 protein or only forms of the same protein, then just call it a protein
			//Otherwise go ahead and make the complex
			Set<String> prots = new HashSet<String>();
			String id = null;
			for(PhysicalEntity component : complex_parts) {
				if(component.getModelInterface().equals(Protein.class)) {
					id = getUniprotProteinId((Protein)component);
					if(id!=null) {
						prots.add(id);
					}
				}
			}
			if(prots.size()==1) {
				//assert it as one protein 
				OWLClass uniprotein_class = go_cam.df.getOWLClass(IRI.create(GoCAM.uniprot_iri + id)); 
				go_cam.addSubclassAssertion(uniprotein_class, GoCAM.protein_class);										
				go_cam.addLabel(uniprotein_class, id);
				//until something is imported that understands the uniprot entities, assert that they are proteins
				go_cam.addTypeAssertion(e, uniprotein_class);
			}else {
				//assert it as a complex
				go_cam.addTypeAssertion(e, GoCAM.go_complex);
				//note that complex.getComponent() apparently violates the rules in its documentation which stipulate that it should return
				//a flat representation of the parts of the complex (e.g. proteins) and not nested complexes (which the reactome biopax does here)
				for(PhysicalEntity component : complex_parts) {
					//hook up parts	
					if(component.getModelInterface().equals(Complex.class)){
						System.out.println("No nested complexes please");
						System.exit(0);
					}else {
						if(component.getMemberPhysicalEntity().size()>0) {
							System.out.println("No nested complexes please.. failing on "+e);
							System.exit(0);
						}
						IRI comp_uri = IRI.create(component.getUri()+e.hashCode());
						OWLNamedIndividual component_entity = go_cam.df.getOWLNamedIndividual(comp_uri);
						go_cam.addObjectPropertyAssertion(e, GoCAM.has_part, component_entity);
						//						//now define complex components
						defineReactionEntity(go_cam, component, comp_uri);
					}
				}
			}
		}
		else if(entity.getModelInterface().equals(BiochemicalReaction.class)){
			BiochemicalReaction reaction = (BiochemicalReaction)(entity);
			//TODO get the preceding event
			//This is not necessary to get the connectivity when querying the integrated pathway collection
			//the outgoing connections are present in the preceding pathway - e.g. 
			//tbid binds to inactive BAK protein has preceding event translocation of tBID to mitichondria
			//connection not shown in pathway containing tbid binds to inactive BAK protein but is shown 
			//in pathway containing translocation of tBID to mitichondria 
			// e.g. translocation of tBID to mitichondria -- provides direct input for -- tbid binds to inactive BAK protein

			//e.g. pathway 'Mitochondrial recruitment of Drp1' (reaction116) has preceeding event 'Caspase mediated cleavage of BAP31' [Homo sapiens] Reaction 94
			//94 - stepProcessOf - next Step - stepProcess 
			//			if(entity.getDisplayName().equals("Caspase mediated cleavage of BAP31")) {
			//				System.out.println(entity);
			//				BiochemicalReaction e_r = (BiochemicalReaction)entity;
			//				Set<PathwayStep> steps_of = e_r.getStepProcessOf();
			//				for(PathwayStep step : steps_of) {
			//					for(PathwayStep s : step.getNextStep()) {
			//						System.out.println("BAP31.."+s.getStepProcess());
			//						System.out.println(s.getStepProcess()+" has preceding event "+e);
			//					}
			//				}
			//			}

			//type it
			go_cam.addTypeAssertion(e, GoCAM.reaction_class);			
			//connect reaction to its pathway(s) via part of
			Set<Pathway> pathways = reaction.getPathwayComponentOf();
			for(Pathway pathway : pathways) {
				OWLNamedIndividual p = go_cam.df.getOWLNamedIndividual(IRI.create(pathway.getUri()));
				go_cam.addObjectPropertyAssertion(e, GoCAM.part_of, p);
			}

			//Create entities for reaction components
			Set<Entity> participants = reaction.getParticipant();
			for(Entity participant : participants) {
				//figure out its nature and capture that
				IRI participant_iri = IRI.create(participant.getUri()+e.hashCode()); //keep the entities in this reaction uniquely identified.. (don't merge them with members of other reactions even if same class of thing)				
				defineReactionEntity(go_cam, participant, participant_iri);		
				//link to participants in reaction
				//biopax#left -> obo:input , biopax#right -> obo:output
				Set<PhysicalEntity> inputs = reaction.getLeft();
				for(PhysicalEntity input : inputs) {
					IRI i_iri = IRI.create(input.getUri()+e.hashCode());
					OWLNamedIndividual input_entity = go_cam.df.getOWLNamedIndividual(i_iri);
					defineReactionEntity(go_cam, input, i_iri);
					go_cam.addObjectPropertyAssertion(e, GoCAM.has_input, input_entity);
				}
				Set<PhysicalEntity> outputs = reaction.getRight();
				for(PhysicalEntity output : outputs) {
					IRI o_iri = IRI.create(output.getUri()+e.hashCode());
					OWLNamedIndividual output_entity = go_cam.df.getOWLNamedIndividual(o_iri);
					defineReactionEntity(go_cam, output, o_iri);
					go_cam.addObjectPropertyAssertion(e, GoCAM.has_output, output_entity);
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
				//				if(controller.getUri().equals("http://www.reactome.org/biopax/63/70326#Control3")) {
				//					System.out.println("Hello 3 controller ");
				//				}

				ControlType ctype = controller.getControlType();
				//make an individual of the class molecular function
				//catalysis 'entities' from biopax may map onto functions from go_cam
				//check for reactome mappings
				//dig out the GO molecular function and create an individual for it
				//OWLNamedIndividual mf = df.getOWLNamedIndividual(controller.getUri()); 
				Set<Xref> xrefs = controller.getXref(); //controller is either a 'control' or a 'catalysis' so far
				boolean mf_set = false;
				for(Xref xref : xrefs) {
					if(xref.getModelInterface().equals(RelationshipXref.class)) {
						RelationshipXref ref = (RelationshipXref)xref;	    			
						//here we add the referenced GO class as a type.  
						if(ref.getDb().equals("GENE ONTOLOGY")) {
							OWLClass xref_go_func = go_cam.df.getOWLClass(IRI.create(GoCAM.obo_iri + ref.getId().replaceAll(":", "_")));
							//add the go function class as a type for the reaction instance being controlled here
							go_cam.addTypeAssertion(e, xref_go_func);
							mf_set = true;
						}
					}
				}		

				Set<Controller> controller_entities = controller.getController();
				for(Controller controller_entity : controller_entities) {
					String local_id = controller_entity.getUri()+e.hashCode();
					IRI iri = IRI.create((local_id));
					defineReactionEntity(go_cam, controller_entity, iri);
					//the protein or complex
					OWLNamedIndividual controller_e = go_cam.df.getOWLNamedIndividual(iri);
					//the controlling physical entity enables that function/reaction
					go_cam.addObjectPropertyAssertion(e, GoCAM.enabled_by, controller_e);
				}
			}
			//					OWLObjectPropertyAssertionAxiom add_func_axiom2 = df.getOWLObjectPropertyAssertionAxiom(enables, controller_e, e);
			//					AddAxiom addFuncAxiom2 = new AddAxiom(go_cam_ont, add_func_axiom2);
			//					ontman.applyChanges(addFuncAxiom2);

			//TODO maybe try harder to find a MF if not explicitly defined
			//if no GO is set for the controller, add an intermediate empty function?
			//					if(!mf_set) {
			//						OWLNamedIndividual mf = df.getOWLNamedIndividual(controller_entity.getUri()+"_function_"+Math.random()); 
			//						OWLClassAssertionAxiom isa_function = df.getOWLClassAssertionAxiom(molecular_function, mf);
			//						ontman.addAxiom(go_cam_ont, isa_function);
			//						ontman.applyChanges();
			//					}

			//					//define how the molecular function (process) relates to the reaction (process)
			//					if(ctype.toString().startsWith("INHIBITION")){
			//						// Event directly_inhibits NextEvent 
			//						OWLObjectPropertyAssertionAxiom add_step_axiom = df.getOWLObjectPropertyAssertionAxiom(directly_inhibits, controller_e, e);
			//						AddAxiom addStepAxiom = new AddAxiom(go_cam_ont, add_step_axiom);
			//						ontman.applyChanges(addStepAxiom);
			//						//System.out.println(a_mf +" inhibits "+e);
			//					}else if(ctype.toString().startsWith("ACTIVATION")){
			//						// Event directly_ACTIVATES NextEvent 
			//						OWLObjectPropertyAssertionAxiom add_step_axiom = df.getOWLObjectPropertyAssertionAxiom(directly_activates, controller_e, e);
			//						AddAxiom addStepAxiom = new AddAxiom(go_cam_ont, add_step_axiom);
			//						ontman.applyChanges(addStepAxiom);
			//						//System.out.println(a_mf +" activates "+e);
			//					}else {
			//						//default to regulates
			//						OWLObjectPropertyAssertionAxiom add_step_axiom = df.getOWLObjectPropertyAssertionAxiom(regulated_by, e, controller_e);
			//						AddAxiom addStepAxiom = new AddAxiom(go_cam_ont, add_step_axiom);
			//						ontman.applyChanges(addStepAxiom);
			//						//System.out.println(e +" regulated_by "+a_mf);
			//					}

			//The OWL for the reaction and all of its parts should now be assembled.  Now can apply secondary rules to improve mapping to go-cam model
			//If all of the entities involved in a reaction are located in the same GO cellular component, 
			//add that the reaction/function occurs_in that location
			//take location information off of the components.  

			Set<OWLClass> reaction_places = new HashSet<OWLClass>();
			Set<OWLClass> input_places = getLocations(EntitySearcher.getObjectPropertyValues(e, GoCAM.has_input, go_cam.go_cam_ont), go_cam.go_cam_ont);
			Set<OWLClass> output_places = getLocations(EntitySearcher.getObjectPropertyValues(e, GoCAM.has_output, go_cam.go_cam_ont), go_cam.go_cam_ont);
			Set<OWLClass> enabler_places = getLocations(EntitySearcher.getObjectPropertyValues(e, GoCAM.enabled_by, go_cam.go_cam_ont), go_cam.go_cam_ont);
			reaction_places.addAll(input_places); reaction_places.addAll(output_places);  reaction_places.addAll(enabler_places); 
			if(reaction_places.size()==1) {
				//System.out.println("1 "+reaction +" "+reaction_places);
				for(OWLClass place : reaction_places) {
					//create the unique individual for this reaction's location individual
					IRI iri = IRI.create(reaction.getUri()+place.hashCode());
					OWLNamedIndividual placeInstance = go_cam.df.getOWLNamedIndividual(iri);
					go_cam.addTypeAssertion(placeInstance, place);
					go_cam.addObjectPropertyAssertion(e, GoCAM.occurs_in, placeInstance);
				}
				//remove all location assertions for the things in this reaction
				go_cam.go_cam_ont = stripLocations(EntitySearcher.getObjectPropertyValues(e, GoCAM.has_input, go_cam.go_cam_ont), go_cam.go_cam_ont, go_cam.df);
				go_cam.go_cam_ont = stripLocations(EntitySearcher.getObjectPropertyValues(e, GoCAM.has_output, go_cam.go_cam_ont), go_cam.go_cam_ont, go_cam.df);
				go_cam.go_cam_ont = stripLocations(EntitySearcher.getObjectPropertyValues(e, GoCAM.enabled_by, go_cam.go_cam_ont), go_cam.go_cam_ont, go_cam.df);
			}else {
				//System.out.println("1+++  "+reaction +" "+reaction_places);
				for(OWLClass place : reaction_places) {
					//TODO do something clever to decide on where the function occurs if things are happening in multiple places.
				}
			}
		}
		return;
	}

	//Could be done with a PathAccessor
	//PathAccessor accessor = new PathAccessor("Complex/component*");
	//The * should do the recursion according to http://journals.plos.org/ploscompbiol/article/file?type=supplementary&id=info:doi/10.1371/journal.pcbi.1003194.s001

	//	private Set<PhysicalEntity> getAllPartsOfComplex(Complex complex, Set<PhysicalEntity> parts){
	//		Set<PhysicalEntity> all_parts = new HashSet<PhysicalEntity>();
	//		if(parts!=null) {
	//			all_parts.addAll(parts);
	//		}
	//		//note that biopx doc suggests not to use this.. but its there in reactome in some places
	//		Set<PhysicalEntity> members = complex.getMemberPhysicalEntity();
	//		members.addAll(complex.getComponent());
	//		for(PhysicalEntity e : members) {
	//			if(e.getModelInterface().equals(Complex.class)) { 
	//				all_parts = getAllPartsOfComplex((Complex)e, all_parts);
	//			} else {
	//				all_parts.add(e);
	//			}
	//		}
	//		return all_parts;
	//	}

	private Set<OWLClass> getLocations(Stream<OWLIndividual> thing_stream, OWLOntology go_cam_ont){
		Iterator<OWLIndividual> things = thing_stream.iterator();		
		Set<OWLClass> places = new HashSet<OWLClass>();
		while(things.hasNext()) {
			OWLIndividual thing = things.next();
			Iterator<OWLIndividual> locations = EntitySearcher.getObjectPropertyValues(thing, GoCAM.located_in, go_cam_ont).iterator();
			while(locations.hasNext()) {
				OWLIndividual location = locations.next();
				Iterator<OWLClassExpression> location_types = EntitySearcher.getTypes(location, go_cam_ont).iterator();
				while(location_types.hasNext()) {
					OWLClassExpression location_expression = location_types.next();
					OWLClass location_class = location_expression.asOWLClass();
					places.add(location_class);
				}
			}
			//should not need to recurse- already flattened
			Iterator<OWLIndividual> parts = EntitySearcher.getObjectPropertyValues(thing, GoCAM.has_part, go_cam_ont).iterator();
			while(parts.hasNext()) {
				OWLIndividual part = parts.next();
				Iterator<OWLIndividual> part_locations = EntitySearcher.getObjectPropertyValues(part, GoCAM.located_in, go_cam_ont).iterator();
				while(part_locations.hasNext()) {
					OWLIndividual part_location = part_locations.next();
					Iterator<OWLClassExpression> location_types = EntitySearcher.getTypes(part_location, go_cam_ont).iterator();
					while(location_types.hasNext()) {
						OWLClassExpression location_expression = location_types.next();
						OWLClass location_class = location_expression.asOWLClass();
						places.add(location_class);
					}
				}
			}
		}
		return places;
	}

	private OWLOntology stripLocations(Stream<OWLIndividual> thing_stream, OWLOntology go_cam_ont, OWLDataFactory df){
		//things are the physical entities
		Iterator<OWLIndividual> things = thing_stream.iterator();
		while(things.hasNext()){
			OWLIndividual thing = things.next();
			//removes the reaction has_input/etc. thing relations
			//Stream<OWLAxiom> location_axioms = EntitySearcher.getReferencingAxioms((OWLEntity) thing, go_cam_ont);
			//go_cam_ont.removeAxioms(location_axioms);
			Iterator<OWLIndividual> places = EntitySearcher.getObjectPropertyValues(thing, GoCAM.located_in, go_cam_ont).iterator(); 
			while(places.hasNext()) {
				OWLIndividual place = places.next();
				//				OWLObjectPropertyAssertionAxiom location_axiom = df.getOWLObjectPropertyAssertionAxiom(located_in, thing, place);
				//				go_cam_ont.remove(location_axiom);
				OWLEntityRemover remover = new OWLEntityRemover(Collections.singleton(go_cam_ont));
				remover.visit(place.asOWLNamedIndividual());
				// or ind.accept(remover);
				go_cam_ont.getOWLOntologyManager().applyChanges(remover.getChanges());
			}
			//strip part locations.. 
			Iterator<OWLIndividual> parts = EntitySearcher.getObjectPropertyValues(thing, GoCAM.has_part, go_cam_ont).iterator();
			while(parts.hasNext()) {
				OWLIndividual part = parts.next();
				Iterator<OWLIndividual> part_locations = EntitySearcher.getObjectPropertyValues(part, GoCAM.located_in, go_cam_ont).iterator();
				while(part_locations.hasNext()) {
					OWLIndividual part_location = part_locations.next();
					OWLEntityRemover remover = new OWLEntityRemover(Collections.singleton(go_cam_ont));
					remover.visit(part_location.asOWLNamedIndividual());
					go_cam_ont.getOWLOntologyManager().applyChanges(remover.getChanges());
				}
			}

		}
		return go_cam_ont;
	}


	/**
	 * Recursively run through a set that may be of mixed type and turn it into a flat list of the bottom level pieces.  
	 * @param input_parts
	 * @param output_parts
	 * @return
	 */
	private Set<PhysicalEntity> flattenNest(Set<PhysicalEntity> input_parts, Set<PhysicalEntity> output_parts){
		Set<PhysicalEntity> all_parts = new HashSet<PhysicalEntity>();
		if(output_parts!=null) {
			all_parts.addAll(output_parts);
		}
		for(PhysicalEntity e : input_parts) {
			if(e.getModelInterface().equals(Complex.class)) { 
				Complex complex = (Complex)e;
				Set<PhysicalEntity> members = complex.getMemberPhysicalEntity();
				members.addAll(complex.getComponent());				
				all_parts = flattenNest(members, all_parts);			
			}else if(e.getMemberPhysicalEntity().size()>0) { //for weird case where a protein has other proteins as pieces.. but isn't called a complex..
				all_parts = flattenNest(e.getMemberPhysicalEntity(), all_parts);	
			} else {
				all_parts.add(e);
			}
		}
		return all_parts;
	}
}
