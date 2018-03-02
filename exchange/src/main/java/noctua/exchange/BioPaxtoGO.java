/**
 * 
 */
package noctua.exchange;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.UnsupportedEncodingException;
import java.text.SimpleDateFormat;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.stream.Stream;

import org.biopax.paxtools.io.BioPAXIOHandler;
import org.biopax.paxtools.io.SimpleIOHandler;
import org.biopax.paxtools.model.Model;
import org.biopax.paxtools.model.level3.BiochemicalReaction;
import org.biopax.paxtools.model.level3.CellularLocationVocabulary;
import org.biopax.paxtools.model.level3.Complex;
import org.biopax.paxtools.model.level3.Control;
import org.biopax.paxtools.model.level3.Controller;
import org.biopax.paxtools.model.level3.Dna;
import org.biopax.paxtools.model.level3.Entity;
import org.biopax.paxtools.model.level3.EntityReference;
import org.biopax.paxtools.model.level3.Pathway;
import org.biopax.paxtools.model.level3.PathwayStep;
import org.biopax.paxtools.model.level3.PhysicalEntity;
import org.biopax.paxtools.model.level3.Process;
import org.biopax.paxtools.model.level3.Protein;
import org.biopax.paxtools.model.level3.PublicationXref;
import org.biopax.paxtools.model.level3.RelationshipXref;
import org.biopax.paxtools.model.level3.SmallMolecule;
import org.biopax.paxtools.model.level3.UnificationXref;
import org.biopax.paxtools.model.level3.XReferrable;
import org.biopax.paxtools.model.level3.Xref;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLAnnotation;
import org.semanticweb.owlapi.model.OWLAxiom;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLClassExpression;
import org.semanticweb.owlapi.model.OWLDataFactory;
import org.semanticweb.owlapi.model.OWLEntity;
import org.semanticweb.owlapi.model.OWLIndividual;
import org.semanticweb.owlapi.model.OWLNamedIndividual;
import org.semanticweb.owlapi.model.OWLObjectProperty;
import org.semanticweb.owlapi.model.OWLObjectPropertyAssertionAxiom;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyCreationException;
import org.semanticweb.owlapi.model.OWLOntologyStorageException;
import org.semanticweb.owlapi.model.OWLSubClassOfAxiom;
import org.semanticweb.owlapi.search.EntitySearcher;
import org.semanticweb.owlapi.util.OWLEntityRemover;

/**
 * @author bgood
 *
 */
public class BioPaxtoGO {
	public static OWLClass reaction_class, pathway_class, protein_class;
	public static final IRI biopax_iri = IRI.create("http://www.biopax.org/release/biopax-level3.owl#");

	/**
	 * @param args
	 * @throws FileNotFoundException 
	 * @throws OWLOntologyCreationException 
	 * @throws OWLOntologyStorageException 
	 * @throws UnsupportedEncodingException 
	 */
	public static void main(String[] args) throws FileNotFoundException, OWLOntologyCreationException, OWLOntologyStorageException, UnsupportedEncodingException {
		BioPaxtoGO bp2g = new BioPaxtoGO();
//		String input_folder = "/Users/bgood/Downloads/biopax/";
//		String output_folder = "/Users/bgood/Downloads/biopax_converted/";
//		bp2g.convertReactomeFolder(input_folder, output_folder);
		
		String input_biopax = "/Users/bgood/Desktop/test/Wnt_example.owl";
				//		"src/main/resources/reactome/wnt/wnt_tcf_full.owl";
		//		"src/main/resources/reactome/Homo_sapiens.owl";
		//"src/main/resources/reactome/glycolysis/glyco_biopax.owl";
		//"src/main/resources/reactome/reactome-input-109581.owl";
		String converted = "/Users/bgood/Desktop/test/Wnt_example_cam-";
				//"/Users/bgood/Desktop/test/converted-wnt-full-";
				//"/Users/bgood/Desktop/test_input/converted-";
				//"/Users/bgood/Documents/GitHub/my-noctua-models/models/reactome-homosapiens-";
		//"src/main/resources/reactome/output/test/reactome-output-glyco-"; 
		//"src/main/resources/reactome/output/reactome-output-109581-";
		//String converted_full = "/Users/bgood/Documents/GitHub/my-noctua-models/models/reactome-homosapiens-wnt-tcf-full";
		boolean split_by_pathway = true;
		bp2g.convertReactomeFile(input_biopax, converted, split_by_pathway);
	} 

	private void convertReactomeFile(String input_file, String output, boolean split_by_pathway) throws FileNotFoundException, OWLOntologyCreationException, OWLOntologyStorageException, UnsupportedEncodingException {
		boolean add_lego_import = false;
		String base_title = "default pathway ontology"; 
		String base_contributor = "reactome contributor"; 
		String base_provider = "https://reactome.org";
		String tag = "";
		convert(input_file, output, split_by_pathway, add_lego_import, base_title, base_contributor, base_provider, tag);
	}
	
	private void convertReactomeFolder(String input_folder, String output_folder) throws FileNotFoundException, OWLOntologyCreationException, OWLOntologyStorageException, UnsupportedEncodingException {
		boolean split_by_pathway = true;
		boolean add_lego_import = false;
		String base_title = "Reactome pathway ontology"; 
		String base_contributor = "Reactome contributor"; 
		String base_provider = "https://reactome.org";

		File dir = new File(input_folder);
		File[] directoryListing = dir.listFiles();
		if (directoryListing != null) {
			for (File input_biopax : directoryListing) {
				String species = input_biopax.getName();
				if(species.contains(".owl")) { //ignore other kinds of files.. liek DS_STORE!
					String output_file_stub = output_folder+"/reactome-"+species.replaceAll(".owl", "-");
					convert(input_biopax.getAbsolutePath(), output_file_stub, split_by_pathway, add_lego_import, base_title, base_contributor, base_provider, species);
				}
			}
		} 
	}

	private void setupBioPaxOntParts(GoCAM go_cam) {
		//protein
		protein_class = go_cam.df.getOWLClass(IRI.create(biopax_iri + "Protein")); 
		go_cam.addLabel(protein_class, "Protein");
		go_cam.addSubclassAssertion(protein_class, GoCAM.continuant_class, null);
		//reaction
		reaction_class = go_cam.df.getOWLClass(IRI.create(biopax_iri + "Reaction")); 
		go_cam.addLabel(reaction_class, "Reaction");
		//pathway
		pathway_class = go_cam.df.getOWLClass(IRI.create(biopax_iri + "Pathway")); 
		go_cam.addLabel(pathway_class, "Pathway");
	}

	private void convert(
			String input_biopax, String converted, 
			boolean split_by_pathway, boolean add_lego_import,
			String base_title, String base_contributor, String base_provider, String tag) throws FileNotFoundException, OWLOntologyCreationException, OWLOntologyStorageException, UnsupportedEncodingException  {
		//read biopax pathway(s)
		BioPAXIOHandler handler = new SimpleIOHandler();
		FileInputStream f = new FileInputStream(input_biopax);
		Model model = handler.convertFromOWL(f);

		//set up ontology (used if not split)
		GoCAM go_cam = new GoCAM("Meta Pathway Ontology", base_contributor, null, base_provider, add_lego_import);
		setupBioPaxOntParts(go_cam);
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
				go_cam = new GoCAM("Reactome:"+tag+":"+currentPathway.getDisplayName(), contributor_link, null, base_provider, add_lego_import);
				setupBioPaxOntParts(go_cam);
			}

			String uri = currentPathway.getUri();
			//make the OWL individual representing the pathway so it can be used below
			OWLNamedIndividual p = go_cam.makeAnnotatedIndividual(uri);
			//define it (add types etc)
			definePathwayEntity(go_cam, currentPathway, split_by_pathway);
			Set<String> pubids = getPubmedIds(currentPathway);
			//		Set<OWLAnnotation> annos = makeAnnotationSet(go_cam, pubrefs, p);

			//get and set parent pathways
			for(Pathway parent_pathway : currentPathway.getPathwayComponentOf()) {				
				//System.out.println(currentPathway.getName()+" is a Component of Pathway:"+parent_pathway.getName()); 
				OWLNamedIndividual parent = go_cam.makeAnnotatedIndividual(parent_pathway.getUri());
				go_cam.addRefBackedObjectPropertyAssertion(p, GoCAM.part_of, parent, pubids, GoCAM.eco_imported_auto);
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
								go_cam.addRefBackedObjectPropertyAssertion(e1, GoCAM.provides_direct_input_for, e2, pubids, GoCAM.eco_imported_auto);
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
					go_cam.addRefBackedObjectPropertyAssertion(p, GoCAM.has_part, child, pubids, GoCAM.eco_imported_auto);
					definePathwayEntity(go_cam, (Pathway)process, split_by_pathway);	
				}
			}
			if(split_by_pathway) {
				String n = currentPathway.getDisplayName();
				n = n.replaceAll("/", "-");	
				n = n.replaceAll(" ", "_");
				String outfilename = converted+n+".ttl";	
				layoutForNoctua(go_cam);
				go_cam.writeGoCAM(outfilename);
				//reset for next pathway.
				//go_cam.ontman.clearOntologies();
				go_cam.ontman.removeOntology(go_cam.go_cam_ont);
			} 
		}	
		//export all
		if(!split_by_pathway) {
			layoutForNoctua(go_cam);
			go_cam.writeGoCAM(converted+".ttl");
		}
	}


	private void definePathwayEntity(GoCAM go_cam, Pathway pathway, boolean split_by_pathway) {
		IRI pathway_iri = IRI.create(pathway.getUri());
		OWLNamedIndividual pathway_e = go_cam.makeAnnotatedIndividual(pathway_iri);
		//tag it as from Reactome..
		go_cam.addTypeAssertion(pathway_e, pathway_class);
		String name = pathway.getDisplayName();
		if(!split_by_pathway) {
			name="Full_"+name;
		}
		go_cam.addLabel(pathway_e, pathway.getDisplayName());
		//comments
		for(String comment: pathway.getComment()) {
			if(comment.startsWith("Authored:")||
					comment.startsWith("Reviewed:")||
					comment.startsWith("Edited:")) {
				go_cam.addLiteralAnnotations2Individual(pathway_iri, GoCAM.contributor_prop, comment);
			}else {
				go_cam.addLiteralAnnotations2Individual(pathway_iri, GoCAM.rdfs_comment, comment);
			}
		}
		//annotations and go
		Set<Xref> xrefs = pathway.getXref();
		//publications 
		Set<String> pubids = getPubmedIds(pathway);
		for(Xref xref : xrefs) {
			//dig out any xreferenced GO processes and assign them as types
			if(xref.getModelInterface().equals(RelationshipXref.class)) {
				RelationshipXref r = (RelationshipXref)xref;	    			
				//System.out.println(xref.getDb()+" "+xref.getId()+" "+xref.getUri()+"----"+r.getRelationshipType());
				//note that relationship types are not defined beyond text strings like RelationshipTypeVocabulary_gene ontology term for cellular process
				//you just have to know what to do.
				//here we add the referenced GO class as a type.  
				if(r.getDb().equals("GENE ONTOLOGY")) {
					OWLClass xref_go_parent = go_cam.df.getOWLClass(IRI.create(GoCAM.obo_iri + r.getId().replaceAll(":", "_")));
					//add it into local hierarchy (temp pre import)	
					//addRefBackedObjectPropertyAssertion
					go_cam.addSubclassAssertion(xref_go_parent, GoCAM.bp_class, null);
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
			e = go_cam.makeAnnotatedIndividual(this_iri);
		}else {
			e = go_cam.makeAnnotatedIndividual(IRI.create(entity.getUri()));
		}
		//check for annotations
		Set<String> pubids = getPubmedIds(entity);
		String entity_name = entity.getDisplayName();
		go_cam.addLabel(e, entity_name);
		//attempt to localize the entity (only if Physical Entity because that is how Reactome views existence in space)
		if(entity instanceof PhysicalEntity) {
			CellularLocationVocabulary loc = ((PhysicalEntity) entity).getCellularLocation();
			if(loc!=null) {
				//OWLNamedIndividual loc_e = go_cam.df.getOWLNamedIndividual(loc.getUri()+e.hashCode());
				OWLNamedIndividual loc_e = go_cam.makeAnnotatedIndividual(loc.getUri()+e.hashCode());				
				//hook up the location
				go_cam.addRefBackedObjectPropertyAssertion(e, GoCAM.located_in, loc_e, pubids, GoCAM.eco_imported_auto);
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
				go_cam.addSubclassAssertion(uniprotein_class, protein_class, null);										
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
						go_cam.addObjectPropertyAssertion(e, GoCAM.has_part, prot_part_entity, null);
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
							go_cam.addSubclassAssertion(dna_class, GoCAM.continuant_class, null);										
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
							go_cam.addSubclassAssertion(mlc_class, GoCAM.continuant_class, null);										
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
				go_cam.addSubclassAssertion(uniprotein_class, protein_class, null);										
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
						go_cam.addObjectPropertyAssertion(e, GoCAM.has_part, component_entity, null);
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
			go_cam.addTypeAssertion(e, reaction_class);			
			//connect reaction to its pathway(s) via has_part
			Set<Pathway> pathways = reaction.getPathwayComponentOf();
			for(Pathway pathway : pathways) {
				OWLNamedIndividual p = go_cam.df.getOWLNamedIndividual(IRI.create(pathway.getUri()));
				go_cam.addRefBackedObjectPropertyAssertion(p, GoCAM.has_part, e, pubids, GoCAM.eco_imported_auto);
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
					go_cam.addObjectPropertyAssertion(e, GoCAM.has_input, input_entity,go_cam.getDefaultAnnotations());
				}
				Set<PhysicalEntity> outputs = reaction.getRight();
				for(PhysicalEntity output : outputs) {
					IRI o_iri = IRI.create(output.getUri()+e.hashCode());
					OWLNamedIndividual output_entity = go_cam.df.getOWLNamedIndividual(o_iri);
					defineReactionEntity(go_cam, output, o_iri);
					go_cam.addObjectPropertyAssertion(e, GoCAM.has_output, output_entity, go_cam.getDefaultAnnotations());
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

				//ControlType ctype = controller.getControlType();
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
					//on occasion there are refs associated with the controller.
					Set<String> controllerpubrefs = getPubmedIds(controller_entity);					
					go_cam.addRefBackedObjectPropertyAssertion(e, GoCAM.enabled_by, controller_e, controllerpubrefs, GoCAM.eco_imported_auto);	
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
					go_cam.addRefBackedObjectPropertyAssertion(e, GoCAM.occurs_in, placeInstance, pubids, GoCAM.eco_imported_auto);
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

	private Set<String> getPubmedIds(Entity entity) {
		Set<String> pmids = new HashSet<String>();
		for(Xref xref : entity.getXref()) {
			if(xref.getModelInterface().equals(PublicationXref.class)) {
				PublicationXref pub = (PublicationXref)xref;
				if(pub!=null&&pub.getDb()!=null) {
					if(pub.getDb().equals("Pubmed")) {
						pmids.add(pub.getId());
					}}
			}
		}
		return pmids;
	}



	private Set<OWLClass> getLocations(Collection<OWLIndividual> thing_stream, OWLOntology go_cam_ont){
		Iterator<OWLIndividual> things = thing_stream.iterator();		
		Set<OWLClass> places = new HashSet<OWLClass>();
		while(things.hasNext()) {
			OWLIndividual thing = things.next();
			places.addAll(getLocations(thing, go_cam_ont));
			//should not need to recurse- already flattened
			Iterator<OWLIndividual> parts = EntitySearcher.getObjectPropertyValues(thing, GoCAM.has_part, go_cam_ont).iterator();
			while(parts.hasNext()) {
				OWLIndividual part = parts.next();
				places.addAll(getLocations(part, go_cam_ont));
			}
		}
		return places;
	}

	private Set<OWLClass> getLocations(OWLIndividual thing, OWLOntology go_cam_ont){
		Iterator<OWLIndividual> locations = EntitySearcher.getObjectPropertyValues(thing, GoCAM.located_in, go_cam_ont).iterator();
		Set<OWLClass> places = new HashSet<OWLClass>();
		while(locations.hasNext()) {
			OWLIndividual location = locations.next();
			Iterator<OWLClassExpression> location_types = EntitySearcher.getTypes(location, go_cam_ont).iterator();
			while(location_types.hasNext()) {
				OWLClassExpression location_expression = location_types.next();
				OWLClass location_class = location_expression.asOWLClass();
				places.add(location_class);
			}
		}
		return places;
	}

	private OWLOntology stripLocations(Collection<OWLIndividual> thing_stream, OWLOntology go_cam_ont, OWLDataFactory df){
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

	private void removeRedundantLocations(GoCAM go_cam) {
		Iterator<OWLIndividual> complexes = EntitySearcher.getIndividuals(GoCAM.go_complex, go_cam.go_cam_ont).iterator();
		while(complexes.hasNext()) {
			OWLNamedIndividual complex = (OWLNamedIndividual)complexes.next();
			Set<OWLClass> complex_locations = getLocations(complex, go_cam.go_cam_ont);
			Iterator<OWLIndividual> parts = EntitySearcher.getObjectPropertyValues(complex, GoCAM.has_part, go_cam.go_cam_ont).iterator();
			Set<OWLClass> part_locations = new HashSet<OWLClass>();
			while(parts.hasNext()) {
				OWLNamedIndividual part = (OWLNamedIndividual)parts.next();
				part_locations.addAll(getLocations(part, go_cam.go_cam_ont));
			}
			if(complex_locations.equals(part_locations)) {
				parts = EntitySearcher.getObjectPropertyValues(complex, GoCAM.has_part, go_cam.go_cam_ont).iterator();
				while(parts.hasNext()) {
					OWLIndividual part = parts.next();
					Iterator<OWLIndividual>  locations = EntitySearcher.getObjectPropertyValues(part, GoCAM.located_in, go_cam.go_cam_ont).iterator();
					while(locations.hasNext()) {
						OWLIndividual part_location = locations.next();
						OWLEntityRemover remover = new OWLEntityRemover(Collections.singleton(go_cam.go_cam_ont));
						remover.visit(part_location.asOWLNamedIndividual());
						go_cam.go_cam_ont.getOWLOntologyManager().applyChanges(remover.getChanges());
					}
				}
			}else {
				//System.out.println(complex_locations+" not same as "+part_locations);
			}
		}
		return;
	}


	/**
	 * Given knowledge of semantic structure of a GO-CAM, try to make a basic layout that is useful within the Noctua editor.
	 * Tries to line up pathways/processes vertically on the left with reactions/functions associated with each one expanding horizontally
	 * @param go_cam
	 */
	private void layoutForNoctuaV1(GoCAM go_cam) {
		removeRedundantLocations(go_cam);
		Iterator<OWLIndividual> pathways = EntitySearcher.getIndividuals(pathway_class, go_cam.go_cam_ont).iterator();
		int y_spacer = 450; int x_spacer = 650;
		int y = 320; int x = 60;
		while(pathways.hasNext()) {
			OWLNamedIndividual pathway = (OWLNamedIndividual)pathways.next();
			go_cam.addLiteralAnnotations2Individual(pathway.getIRI(), GoCAM.x_prop, go_cam.df.getOWLLiteral(x));
			go_cam.addLiteralAnnotations2Individual(pathway.getIRI(), GoCAM.y_prop, go_cam.df.getOWLLiteral(y));   			
			//horizontal line of related reactions
			int reaction_x = x + x_spacer; 
			int reaction_y = y;
			Iterator<OWLIndividual> reactions = EntitySearcher.getObjectPropertyValues(pathway, GoCAM.has_part, go_cam.go_cam_ont).iterator();
			while(reactions.hasNext()) {
				OWLNamedIndividual reaction = (OWLNamedIndividual)reactions.next();
				go_cam.addLiteralAnnotations2Individual(reaction.getIRI(), GoCAM.x_prop, go_cam.df.getOWLLiteral(reaction_x));
				go_cam.addLiteralAnnotations2Individual(reaction.getIRI(), GoCAM.y_prop, go_cam.df.getOWLLiteral(reaction_y));
				//up for input down for output
				int input_x = reaction_x - 200;
				int input_y = reaction_y - 200;
				Iterator<OWLIndividual> inputs = EntitySearcher.getObjectPropertyValues(reaction, GoCAM.has_input, go_cam.go_cam_ont).iterator();
				while(inputs.hasNext()) {
					OWLNamedIndividual input = (OWLNamedIndividual)inputs.next();
					go_cam.addLiteralAnnotations2Individual(input.getIRI(), GoCAM.x_prop, go_cam.df.getOWLLiteral(input_x));
					go_cam.addLiteralAnnotations2Individual(input.getIRI(), GoCAM.y_prop, go_cam.df.getOWLLiteral(input_y));

					int location_x = input_x;
					int location_y = input_y - 100;
					Iterator<OWLIndividual> locations = EntitySearcher.getObjectPropertyValues(input, GoCAM.located_in, go_cam.go_cam_ont).iterator();
					while(locations.hasNext()) {
						OWLNamedIndividual location = (OWLNamedIndividual)locations.next();
						go_cam.addLiteralAnnotations2Individual(location.getIRI(), GoCAM.x_prop, go_cam.df.getOWLLiteral(location_x));
						go_cam.addLiteralAnnotations2Individual(location.getIRI(), GoCAM.y_prop, go_cam.df.getOWLLiteral(location_y));
						location_x = location_x + 50;
					}	
					input_x = input_x+200;
				}
				int output_x = reaction_x - 200;
				int output_y = reaction_y + 300;
				Iterator<OWLIndividual> outputs = EntitySearcher.getObjectPropertyValues(reaction, GoCAM.has_output, go_cam.go_cam_ont).iterator();
				while(outputs.hasNext()) {
					OWLNamedIndividual output = (OWLNamedIndividual)outputs.next();
					go_cam.addLiteralAnnotations2Individual(output.getIRI(), GoCAM.x_prop, go_cam.df.getOWLLiteral(output_x));
					go_cam.addLiteralAnnotations2Individual(output.getIRI(), GoCAM.y_prop, go_cam.df.getOWLLiteral(output_y));

					int location_x = output_x;
					int location_y = output_y + 100;
					Iterator<OWLIndividual> locations = EntitySearcher.getObjectPropertyValues(output, GoCAM.located_in, go_cam.go_cam_ont).iterator();
					while(locations.hasNext()) {
						OWLNamedIndividual location = (OWLNamedIndividual)locations.next();
						go_cam.addLiteralAnnotations2Individual(location.getIRI(), GoCAM.x_prop, go_cam.df.getOWLLiteral(location_x));
						go_cam.addLiteralAnnotations2Individual(location.getIRI(), GoCAM.y_prop, go_cam.df.getOWLLiteral(location_y));
						location_y = location_y + 50;
					}					
					output_x = output_x+200;
				}
				//up left for enabled 
				int enabler_x = reaction_x - 400;
				int enabler_y = reaction_y - 200;

				Iterator<OWLIndividual> enablers = EntitySearcher.getObjectPropertyValues(reaction, GoCAM.enabled_by, go_cam.go_cam_ont).iterator();
				if(enablers!=null) {
					while(enablers.hasNext()) {
						OWLNamedIndividual enabler = (OWLNamedIndividual)enablers.next();
						//check if already has a location (e.g. as an input)
						long n = ((Stream<OWLIndividual>) EntitySearcher.getAnnotationObjects(enabler.getIRI(), go_cam.go_cam_ont, GoCAM.x_prop)).count();
						if(n==0) {
							go_cam.addLiteralAnnotations2Individual(enabler.getIRI(), GoCAM.x_prop, go_cam.df.getOWLLiteral(enabler_x));
							go_cam.addLiteralAnnotations2Individual(enabler.getIRI(), GoCAM.y_prop, go_cam.df.getOWLLiteral(enabler_y));					
							int location_x = enabler_x;
							int location_y = enabler_y - 100;
							Iterator<OWLIndividual> locations = EntitySearcher.getObjectPropertyValues(enabler, GoCAM.located_in, go_cam.go_cam_ont).iterator();
							while(locations.hasNext()) {
								OWLNamedIndividual location = (OWLNamedIndividual)locations.next();
								go_cam.addLiteralAnnotations2Individual(location.getIRI(), GoCAM.x_prop, go_cam.df.getOWLLiteral(location_x));
								go_cam.addLiteralAnnotations2Individual(location.getIRI(), GoCAM.y_prop, go_cam.df.getOWLLiteral(location_y));
								location_y = location_y - 50;
							}					
							enabler_x = enabler_x+50;
							enabler_y = enabler_y+100;
						}
					}
				}

				reaction_x = reaction_x+x_spacer;
			}

			y = y+y_spacer;
		}
	}

	private void layoutForNoctua(GoCAM go_cam) {
		removeRedundantLocations(go_cam);
		Iterator<OWLIndividual> pathways = EntitySearcher.getIndividuals(pathway_class, go_cam.go_cam_ont).iterator();
		int y_spacer = 450; int x_spacer = 650;
		int y = 50; int x = 500;
		//generally only one pathway represented with reactions - others just links off via part of
		//draw them in a line across the top 
		while(pathways.hasNext()) {
			OWLNamedIndividual pathway = (OWLNamedIndividual)pathways.next();

			go_cam.addLiteralAnnotations2Individual(pathway.getIRI(), GoCAM.x_prop, go_cam.df.getOWLLiteral(x));
			go_cam.addLiteralAnnotations2Individual(pathway.getIRI(), GoCAM.y_prop, go_cam.df.getOWLLiteral(y));   			

			//find reactions that are part of this pathway
			//CLUNKY.. fighting query.. need to find a better way - maybe sparql... 
			int reaction_x = x; 
			int reaction_y = y + y_spacer;
			Iterator<OWLIndividual> reactions = EntitySearcher.getObjectPropertyValues(pathway, GoCAM.has_part, go_cam.go_cam_ont).iterator();
			//find out if there is a logical root - a reaction with no provides_direct_iput_for relations coming into it
			Set<OWLIndividual> roots = new HashSet<OWLIndividual>();
			while(reactions.hasNext()) {
				OWLIndividual react = reactions.next();
				roots.add(react);
			}
			reactions = EntitySearcher.getObjectPropertyValues(pathway, GoCAM.has_part, go_cam.go_cam_ont).iterator();
			while(reactions.hasNext()) {	
				OWLIndividual react = reactions.next();
				Iterator<OWLIndividual> targets = EntitySearcher.getObjectPropertyValues(react, GoCAM.provides_direct_input_for, go_cam.go_cam_ont).iterator();
				while(targets.hasNext()) {
					OWLIndividual target = targets.next();			
					roots.remove(target);				
				}
			}
			//if there is a root or roots.. do a sideways bread first layout
			if(roots.size()>0) {
				for(OWLIndividual root : roots) {
					layoutHorizontalTree(reaction_x, reaction_y, x_spacer, y_spacer, (OWLNamedIndividual)root, GoCAM.provides_direct_input_for, go_cam);
					reaction_y = reaction_y + y_spacer;
				}				
			}else { // do loop layout.  
				reactions = EntitySearcher.getObjectPropertyValues(pathway, GoCAM.has_part, go_cam.go_cam_ont).iterator();
				if(reactions.hasNext()) {
					System.out.println(pathway + "Loop layout!");
					OWLNamedIndividual loopstart = (OWLNamedIndividual) reactions.next();
					layoutLoopish(reaction_x, reaction_y, x_spacer, y_spacer, loopstart, GoCAM.provides_direct_input_for, go_cam);		
				}
			}
			x = x+x_spacer;
		}
	}

	private void layoutReactionComponents(OWLIndividual reaction, GoCAM go_cam, int reaction_x, int reaction_y) {
		//up for input down for output
		int input_x = reaction_x - 200;
		int input_y = reaction_y - 200;
		Iterator<OWLIndividual> inputs = EntitySearcher.getObjectPropertyValues(reaction, GoCAM.has_input, go_cam.go_cam_ont).iterator();
		while(inputs.hasNext()) {
			OWLNamedIndividual input = (OWLNamedIndividual)inputs.next();
			go_cam.addLiteralAnnotations2Individual(input.getIRI(), GoCAM.x_prop, go_cam.df.getOWLLiteral(input_x));
			go_cam.addLiteralAnnotations2Individual(input.getIRI(), GoCAM.y_prop, go_cam.df.getOWLLiteral(input_y));

			int location_x = input_x;
			int location_y = input_y - 100;
			Iterator<OWLIndividual> locations = EntitySearcher.getObjectPropertyValues(input, GoCAM.located_in, go_cam.go_cam_ont).iterator();
			while(locations.hasNext()) {
				OWLNamedIndividual location = (OWLNamedIndividual)locations.next();
				go_cam.addLiteralAnnotations2Individual(location.getIRI(), GoCAM.x_prop, go_cam.df.getOWLLiteral(location_x));
				go_cam.addLiteralAnnotations2Individual(location.getIRI(), GoCAM.y_prop, go_cam.df.getOWLLiteral(location_y));
				location_x = location_x + 50;
			}	
			layoutPartsAndlocations(input, input_x, input_y, go_cam);	
			input_x = input_x+200;
		}
		int output_x = reaction_x - 200;
		int output_y = reaction_y + 300;
		Iterator<OWLIndividual> outputs = EntitySearcher.getObjectPropertyValues(reaction, GoCAM.has_output, go_cam.go_cam_ont).iterator();
		while(outputs.hasNext()) {
			OWLNamedIndividual output = (OWLNamedIndividual)outputs.next();
			go_cam.addLiteralAnnotations2Individual(output.getIRI(), GoCAM.x_prop, go_cam.df.getOWLLiteral(output_x));
			go_cam.addLiteralAnnotations2Individual(output.getIRI(), GoCAM.y_prop, go_cam.df.getOWLLiteral(output_y));

			int location_x = output_x;
			int location_y = output_y + 100;
			Iterator<OWLIndividual> locations = EntitySearcher.getObjectPropertyValues(output, GoCAM.located_in, go_cam.go_cam_ont).iterator();
			while(locations.hasNext()) {
				OWLNamedIndividual location = (OWLNamedIndividual)locations.next();
				go_cam.addLiteralAnnotations2Individual(location.getIRI(), GoCAM.x_prop, go_cam.df.getOWLLiteral(location_x));
				go_cam.addLiteralAnnotations2Individual(location.getIRI(), GoCAM.y_prop, go_cam.df.getOWLLiteral(location_y));
				location_y = location_y + 50;
			}					
			layoutPartsAndlocations(output, output_x, output_y, go_cam);	
			output_x = output_x+200;
		}
		//up left for enabled 
		int enabler_x = reaction_x - 400;
		int enabler_y = reaction_y - 200;

		Iterator<OWLIndividual> enablers = EntitySearcher.getObjectPropertyValues(reaction, GoCAM.enabled_by, go_cam.go_cam_ont).iterator();
		if(enablers!=null) {
			while(enablers.hasNext()) {
				OWLNamedIndividual enabler = (OWLNamedIndividual)enablers.next();
				//check if already has a location (e.g. as an input)
				//long n = EntitySearcher.getAnnotationObjects(enabler.getIRI(), go_cam.go_cam_ont, GoCAM.x_prop).count();
				Collection<OWLAnnotation> c = EntitySearcher.getAnnotationObjects(enabler.getIRI(), go_cam.go_cam_ont, GoCAM.x_prop);				
				if(c.size()==0) {
					go_cam.addLiteralAnnotations2Individual(enabler.getIRI(), GoCAM.x_prop, go_cam.df.getOWLLiteral(enabler_x));
					go_cam.addLiteralAnnotations2Individual(enabler.getIRI(), GoCAM.y_prop, go_cam.df.getOWLLiteral(enabler_y));					
					int location_x = enabler_x;
					int location_y = enabler_y - 100;
					Iterator<OWLIndividual> locations = EntitySearcher.getObjectPropertyValues(enabler, GoCAM.located_in, go_cam.go_cam_ont).iterator();
					while(locations.hasNext()) {
						OWLNamedIndividual location = (OWLNamedIndividual)locations.next();
						go_cam.addLiteralAnnotations2Individual(location.getIRI(), GoCAM.x_prop, go_cam.df.getOWLLiteral(location_x));
						go_cam.addLiteralAnnotations2Individual(location.getIRI(), GoCAM.y_prop, go_cam.df.getOWLLiteral(location_y));
						location_y = location_y - 50;
					}	
					//some of these have parts lurking about
					layoutPartsAndlocations(enabler, enabler_x, enabler_y, go_cam);					
					enabler_x = enabler_x+50;
					enabler_y = enabler_y+100;
				}
			}
		}
	}

	private void layoutPartsAndlocations(OWLIndividual entity, int entity_x, int entity_y, GoCAM go_cam) {
		int part_x = entity_x + 50 ;
		int part_y = entity_y - 150;
		Iterator<OWLIndividual> parts = EntitySearcher.getObjectPropertyValues(entity, GoCAM.has_part, go_cam.go_cam_ont).iterator();
		while(parts.hasNext()) {
			OWLNamedIndividual part = (OWLNamedIndividual)parts.next();
			go_cam.addLiteralAnnotations2Individual(part.getIRI(), GoCAM.x_prop, go_cam.df.getOWLLiteral(part_x));
			go_cam.addLiteralAnnotations2Individual(part.getIRI(), GoCAM.y_prop, go_cam.df.getOWLLiteral(part_y));
			//and some of these have locations.. 
			int part_location_x = part_x;
			int part_location_y = part_y - 100;
			Iterator<OWLIndividual> part_locations = EntitySearcher.getObjectPropertyValues(part, GoCAM.located_in, go_cam.go_cam_ont).iterator();
			while(part_locations.hasNext()) {
				OWLNamedIndividual part_location = (OWLNamedIndividual)part_locations.next();
				go_cam.addLiteralAnnotations2Individual(part_location.getIRI(), GoCAM.x_prop, go_cam.df.getOWLLiteral(part_location_x));
				go_cam.addLiteralAnnotations2Individual(part_location.getIRI(), GoCAM.y_prop, go_cam.df.getOWLLiteral(part_location_y));
				part_location_y = part_location_y - 50;
			}	
		}
	}

	private void layoutLoopish(int start_x, int start_y, int x_spacer, int y_spacer, OWLNamedIndividual reaction_node, OWLObjectProperty edge_type, GoCAM go_cam) {
		//don't fly into infinity and beyond!
		if(mapHintPresent(reaction_node, go_cam)) {		
			return;
		}
		go_cam.addLiteralAnnotations2Individual(reaction_node.getIRI(), GoCAM.x_prop, go_cam.df.getOWLLiteral(start_x));
		go_cam.addLiteralAnnotations2Individual(reaction_node.getIRI(), GoCAM.y_prop, go_cam.df.getOWLLiteral(start_y));
		//add all its pieces
		layoutReactionComponents(reaction_node, go_cam, start_x, start_y);
		//recurse
		Iterator<OWLIndividual> children = EntitySearcher.getObjectPropertyValues(reaction_node, edge_type, go_cam.go_cam_ont).iterator();
		int child_x = start_x + x_spacer;
		int child_y = start_y + y_spacer;
		while(children.hasNext()) {
			OWLNamedIndividual child = (OWLNamedIndividual) children.next();
			layoutLoopish(child_x, child_y, x_spacer, y_spacer + 500, child, edge_type, go_cam);
			child_x = child_x + x_spacer + 200;
			child_y = child_y - y_spacer - 200;
		}	
	}

	private boolean mapHintPresent(OWLNamedIndividual node, GoCAM go_cam){
		boolean x_present = false;
		//long nx = EntitySearcher.getAnnotationObjects(node, go_cam.go_cam_ont, GoCAM.x_prop).count();
		Collection<OWLAnnotation> xs = EntitySearcher.getAnnotationObjects(node, go_cam.go_cam_ont, GoCAM.x_prop);
		if(xs.size()>0) {
			x_present = true;
		}
		return x_present;
	}

	private void layoutHorizontalTree(int start_x, int start_y, int x_spacer, int y_spacer, OWLNamedIndividual reaction_root, OWLObjectProperty edge_type, GoCAM go_cam) {
		//don't fly into infinity and beyond!
		if(mapHintPresent(reaction_root, go_cam)) {
			return;
		}
		go_cam.addLiteralAnnotations2Individual(reaction_root.getIRI(), GoCAM.x_prop, go_cam.df.getOWLLiteral(start_x));
		go_cam.addLiteralAnnotations2Individual(reaction_root.getIRI(), GoCAM.y_prop, go_cam.df.getOWLLiteral(start_y));
		//add all its pieces
		layoutReactionComponents(reaction_root, go_cam, start_x, start_y);
		//recurse through children
		Iterator<OWLIndividual> children = EntitySearcher.getObjectPropertyValues(reaction_root, edge_type, go_cam.go_cam_ont).iterator();
		int child_x = start_x + x_spacer;
		int child_y = start_y;
		while(children.hasNext()) {
			OWLNamedIndividual child = (OWLNamedIndividual) children.next();
			layoutHorizontalTree(child_x, child_y, x_spacer, y_spacer, child, edge_type, go_cam);
			child_y = child_y + y_spacer;		
		}
	}
}
