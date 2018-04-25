/**
 * 
 */
package org.geneontology.gocam.exchange;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import org.biopax.paxtools.io.BioPAXIOHandler;
import org.biopax.paxtools.io.SimpleIOHandler;
import org.biopax.paxtools.model.Model;
import org.biopax.paxtools.model.level2.catalysis;
import org.biopax.paxtools.model.level3.BiochemicalReaction;
import org.biopax.paxtools.model.level3.Catalysis;
import org.biopax.paxtools.model.level3.CellularLocationVocabulary;
import org.biopax.paxtools.model.level3.Complex;
import org.biopax.paxtools.model.level3.Control;
import org.biopax.paxtools.model.level3.ControlType;
import org.biopax.paxtools.model.level3.Controller;
import org.biopax.paxtools.model.level3.Conversion;
import org.biopax.paxtools.model.level3.ConversionDirectionType;
import org.biopax.paxtools.model.level3.Degradation;
import org.biopax.paxtools.model.level3.Dna;
import org.biopax.paxtools.model.level3.Entity;
import org.biopax.paxtools.model.level3.EntityReference;
import org.biopax.paxtools.model.level3.GeneticInteraction;
import org.biopax.paxtools.model.level3.Interaction;
import org.biopax.paxtools.model.level3.MolecularInteraction;
import org.biopax.paxtools.model.level3.NucleicAcid;
import org.biopax.paxtools.model.level3.Pathway;
import org.biopax.paxtools.model.level3.PathwayStep;
import org.biopax.paxtools.model.level3.PhysicalEntity;
import org.biopax.paxtools.model.level3.Process;
import org.biopax.paxtools.model.level3.Protein;
import org.biopax.paxtools.model.level3.PublicationXref;
import org.biopax.paxtools.model.level3.RelationshipXref;
import org.biopax.paxtools.model.level3.SmallMolecule;
import org.biopax.paxtools.model.level3.TemplateDirectionType;
import org.biopax.paxtools.model.level3.TemplateReaction;
import org.biopax.paxtools.model.level3.UnificationXref;
import org.biopax.paxtools.model.level3.XReferrable;
import org.biopax.paxtools.model.level3.Xref;
import org.openrdf.repository.RepositoryException;
import org.openrdf.rio.RDFHandlerException;
import org.openrdf.rio.RDFParseException;
import org.semanticweb.owlapi.model.AxiomType;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLAnnotation;
import org.semanticweb.owlapi.model.OWLAnnotationValue;
import org.semanticweb.owlapi.model.OWLAxiom;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLClassExpression;
import org.semanticweb.owlapi.model.OWLDataFactory;
import org.semanticweb.owlapi.model.OWLEntity;
import org.semanticweb.owlapi.model.OWLEquivalentClassesAxiom;
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
	//public static OWLClass reaction_class, pathway_class, protein_class;
	public static final IRI biopax_iri = IRI.create("http://www.biopax.org/release/biopax-level3.owl#");
	final String mapping_report_file = "report/mapping.txt";
	int noctua_version = 1;
	String blazegraph_output_journal = "/Users/bgood/noctua-config/blazegraph.jnl";
	/**
	 * @param args
	 * @throws OWLOntologyCreationException 
	 * @throws OWLOntologyStorageException 
	 * @throws IOException 
	 * @throws RDFHandlerException 
	 * @throws RDFParseException 
	 * @throws RepositoryException 
	 */
	public static void main(String[] args) throws OWLOntologyCreationException, OWLOntologyStorageException, RepositoryException, RDFParseException, RDFHandlerException, IOException {
		BioPaxtoGO bp2g = new BioPaxtoGO();
		//		String input_folder = "/Users/bgood/Downloads/biopax/";
		//		String output_folder = "/Users/bgood/Downloads/biopax_converted/";
		//		bp2g.convertReactomeFolder(input_folder, output_folder);

		String input_biopax = 
				"/Users/bgood/Desktop/test/transport_small_mlc.owl";
			//			"/Users/bgood/Desktop/test/abacavir_metabolism.owl";
				//"/Users/bgood/Desktop/test/gap_junction.owl"; 
		//		"/Users/bgood/Desktop/test/BMP_signaling.owl"; 
		//		"/Users/bgood/Desktop/test/Wnt_example.owl";
		//"/Users/bgood/Desktop/test/Wnt_full_tcf_signaling.owl";
		//"src/main/resources/reactome/Homo_sapiens.owl";
		//"/Users/bgood/Downloads/biopax/homosapiens.owl";
		//"src/main/resources/reactome/glycolysis/glyco_biopax.owl";
		//"src/main/resources/reactome/reactome-input-109581.owl";
		String converted = 
		//"/Users/bgood/Desktop/test/tmp/converted-";
		//				"/Users/bgood/Desktop/test/abacavir_metabolism_output/converted-";
				//"/Users/bgood/Desktop/test/Clathrin-mediated-endocytosis-output/converted-";
				//"/Users/bgood/Desktop/test/Wnt_output/converted-";
				//"/Users/bgood/Desktop/test/gap_junction_output/converted-";
		//		"/Users/bgood/Desktop/test/bmp_output/converted-";
		//"/Users/bgood/Documents/GitHub/my-noctua-models/models/reactome-homosapiens-";
		"/Users/bgood/reactome-go-cam-models/human/reactome-homosapiens-";
		//"src/main/resources/reactome/output/test/reactome-output-glyco-"; 
		//"src/main/resources/reactome/output/reactome-output-109581-";
		//String converted_full = "/Users/bgood/Documents/GitHub/my-noctua-models/models/TCF-dependent_signaling_in_response_to_Wnt";
		boolean split_by_pathway = true;
		boolean save_inferences = false;
		boolean expand_subpathways = false;  //this is a bad idea for high level nodes like 'Signaling Pathways'
		bp2g.convertReactomeFile(input_biopax, converted, split_by_pathway, save_inferences, expand_subpathways);
	} 

	private void convertReactomeFile(String input_file, String output, boolean split_by_pathway, boolean save_inferences, boolean expand_subpathways) throws OWLOntologyCreationException, OWLOntologyStorageException, RepositoryException, RDFParseException, RDFHandlerException, IOException {
		boolean add_lego_import = false; //unless you never want to open the output in Protege always leave false..
		String base_title = "FULL BMP Signaling";//"FULL TCF-dependent_signaling_in_response_to_Wnt"; 
		String base_contributor = "https://orcid.org/0000-0002-7334-7852"; //Ben Good
		String base_provider = "https://reactome.org";
		String tag = "";
		convert(input_file, output, split_by_pathway, add_lego_import, base_title, base_contributor, base_provider, tag, save_inferences, expand_subpathways);
	}

	private void convertReactomeFolder(String input_folder, String output_folder, boolean save_inferences, boolean expand_subpathways) throws OWLOntologyCreationException, OWLOntologyStorageException, RepositoryException, RDFParseException, RDFHandlerException, IOException {
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
				if(species.contains(".owl")) { //ignore other kinds of files.. like DS_STORE!
					String output_file_stub = output_folder+"/reactome-"+species.replaceAll(".owl", "-");
					convert(input_biopax.getAbsolutePath(), output_file_stub, split_by_pathway, add_lego_import, base_title, base_contributor, base_provider, species, save_inferences, expand_subpathways);
				}
			}
		} 
	}

	/**
	 * The main point of access for converting BioPAX level 3 OWL models into GO-CAM OWL models
	 * @param input_biopax
	 * @param converted
	 * @param split_by_pathway
	 * @param add_lego_import
	 * @param base_title
	 * @param base_contributor
	 * @param base_provider
	 * @param tag
	 * @throws OWLOntologyCreationException
	 * @throws OWLOntologyStorageException
	 * @throws IOException 
	 * @throws RDFHandlerException 
	 * @throws RDFParseException 
	 * @throws RepositoryException 
	 */
	private void convert(
			String input_biopax, String converted, 
			boolean split_out_by_pathway, boolean add_lego_import,
			String base_title, String base_contributor, String base_provider, String tag, 
			boolean save_inferences, boolean expand_subpathways) throws OWLOntologyCreationException, OWLOntologyStorageException, RepositoryException, RDFParseException, RDFHandlerException, IOException  {
		//read biopax pathway(s)
		BioPAXIOHandler handler = new SimpleIOHandler();
		FileInputStream f = new FileInputStream(input_biopax);
		Model model = handler.convertFromOWL(f);
		int n_pathways = 0;
		//set up ontology (used if not split)
		String base_ont_title = base_title;
		String iri = "http://model.geneontology.org/"+base_ont_title.hashCode(); 
		IRI ont_iri = IRI.create(iri);
		GoCAM go_cam = new GoCAM(ont_iri, base_ont_title, base_contributor, null, base_provider, add_lego_import);
		//for blazegraph output
		boolean save2blazegraph = true;
		String journal = blazegraph_output_journal;
		if(journal.equals("")) {
			journal = converted+".jnl";
		}
		go_cam.path2bgjournal = journal;
		//clean out any prior data in store
		FileWriter clean = new FileWriter(journal, false);
		clean.write("");
		clean.close();
		Blazer blaze = go_cam.initializeBlazeGraph(journal);
		QRunner tbox_qrunner = go_cam.initializeQRunnerForTboxInference();
		//for report
		//initialize clean file
		FileWriter report = new FileWriter(mapping_report_file, false);
		report.write("Reactome label\tReactome id\tGO id\tgo_node_type\treactome_node_type\n");
		report.close();
		//list pathways
		int total_pathways = model.getObjects(Pathway.class).size();
		for (Pathway currentPathway : model.getObjects(Pathway.class)){
			String reactome_id = null;
			n_pathways++;
			System.out.println(n_pathways+" of "+total_pathways+" Pathway:"+currentPathway.getName()); 
			if(split_out_by_pathway) {
				//then reinitialize for each pathway
				reactome_id = null;
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
				base_ont_title = "Reactome:"+tag+":"+currentPathway.getDisplayName();
				iri = "http://model.geneontology.org/"+base_ont_title.hashCode(); //using a URL encoded string here confused the UI code...
				ont_iri = IRI.create(iri);	
				go_cam = new GoCAM(ont_iri, base_ont_title, contributor_link, null, base_provider, add_lego_import);
				//re-using sparql runner, no need to re-load the tbox each time 
				go_cam.qrunner = tbox_qrunner; 
				//journal is by default in 'append' mode - keeping the same journal reference add each pathway to same journal
				go_cam.path2bgjournal = journal;
				go_cam.blazegraphdb = blaze;
			}

			String uri = currentPathway.getUri();
			//make the OWL individual representing the pathway so it can be used below
			OWLNamedIndividual p = go_cam.makeAnnotatedIndividual(GoCAM.makeGoCamifiedIRI(uri));
			//define it (add types etc)
			definePathwayEntity(go_cam, currentPathway, reactome_id, expand_subpathways, true);
			//			Set<String> pubids = getPubmedIds(currentPathway);
			//get and set parent pathways
			//currently viewing one model as a complete thing - leaving out outgoing connections.  
			//			for(Pathway parent_pathway : currentPathway.getPathwayComponentOf()) {				
			//				OWLNamedIndividual parent = go_cam.makeAnnotatedIndividual(GoCAM.makeGoCamifiedIRI(parent_pathway.getUri()));
			//				go_cam.addRefBackedObjectPropertyAssertion(p, GoCAM.part_of, parent, pubids, GoCAM.eco_imported_auto,  "PMID", null);
			//				definePathwayEntity(go_cam, parent_pathway, reactome_id, false);
			//			}
			//write results
			if(split_out_by_pathway) {
				String n = currentPathway.getDisplayName();
				n = n.replaceAll("/", "-");	
				n = n.replaceAll(" ", "_");
				String outfilename = converted+n+".ttl";	
				wrapAndWrite(outfilename, go_cam, save_inferences, save2blazegraph);
				//reset for next pathway.
				go_cam.ontman.removeOntology(go_cam.go_cam_ont);
			} 
		}	
		//export all
		if(!split_out_by_pathway) {
			wrapAndWrite(converted+".ttl", go_cam, save_inferences, save2blazegraph);		
		}

	}

	/**
	 * Once all the Paxtools parsing and initial go_cam OWL ontology creation is done, apply more inference rules and export the files
	 * @param outfilename
	 * @param go_cam
	 * @param save_inferences
	 * @param save2blazegraph
	 * @throws OWLOntologyCreationException
	 * @throws OWLOntologyStorageException
	 * @throws RepositoryException
	 * @throws RDFParseException
	 * @throws RDFHandlerException
	 * @throws IOException
	 */
	private void wrapAndWrite(String outfilename, GoCAM go_cam, boolean save_inferences, boolean save2blazegraph) throws OWLOntologyCreationException, OWLOntologyStorageException, RepositoryException, RDFParseException, RDFHandlerException, IOException {		
		//make sure sparql kb in sync with ontology
		go_cam.qrunner = new QRunner(go_cam.go_cam_ont); 
		//infer new edges based on sparql matching
		System.out.println("Before sparql inference -  triples: "+go_cam.qrunner.nTriples());
		go_cam.applySparqlRules();
		//sparql rules make additions to go_cam_ont
		go_cam.qrunner = new QRunner(go_cam.go_cam_ont); 
		System.out.println("After sparql inference -  triples: "+go_cam.qrunner.nTriples());
		boolean is_logical = go_cam.validateGoCAM();
		if(save_inferences) {
			System.out.println("preparing model starting with (unreasoned) triples: "+go_cam.qrunner.nTriples());
			//apply Arachne to tbox rules and add inferences to qrunner.jena rdf model
			go_cam.addInferredEdges();
			System.out.println("total triples after OWL inference: "+go_cam.qrunner.nTriples());
		}		
		//synchronize jena model <- with owl-api model	 
		//go_cam_ont should have everything we want at this point, including any imports
		go_cam.qrunner = new QRunner(go_cam.go_cam_ont); 
		if(noctua_version == 1) {
			//adds coordinates to go_con_ont model 
			layoutForNoctuaVersion1(go_cam);	
			//add them into the rdf 
			go_cam.qrunner = new QRunner(go_cam.go_cam_ont); 
			//do rdf-only pruning - don't reinitialize runner after this as these changes don't get put into ontology
			//remove has_part relations linking process to reactions.  redundant as all reactions are part of the main process right now and clouds view
			go_cam.qrunner.deletePathwayHasPart();
			//remove any locations on physical entities. screws display as entities can't be folded into function nodes
			go_cam.qrunner.deleteEntityLocations();
		}
		go_cam.writeGoCAM(outfilename, save2blazegraph);
		if(!is_logical) {
			System.out.println("Illogical go_cam..  stopping");
			System.exit(0);
		}
	}

	private void definePathwayEntity(GoCAM go_cam, Pathway pathway, String reactome_id, boolean expand_subpathways, boolean add_components) throws IOException {
		FileWriter report = new FileWriter(mapping_report_file, true);
		IRI pathway_iri = GoCAM.makeGoCamifiedIRI(pathway.getUri());
		OWLNamedIndividual pathway_e = go_cam.makeAnnotatedIndividual(pathway_iri);
		go_cam.addLabel(pathway_e, pathway.getDisplayName());
		if(add_components) {
			//in obo world each pathway is a biological process
			go_cam.addTypeAssertion(pathway_e, GoCAM.bp_class);
		}//if not adding component this is only being used to show context for some other pathway
		else if(noctua_version == 1){
			OWLClass justaname = go_cam.df.getOWLClass(GoCAM.makeGoCamifiedIRI(pathway.getUri()+"class"));
			go_cam.addLabel(justaname, pathway.getDisplayName());
			go_cam.addSubclassAssertion(justaname, GoCAM.bp_class, null);
			go_cam.addTypeAssertion(pathway_e, justaname);
		}
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
		//references
		Set<String> pubids = getPubmedIds(pathway);
		//annotations and go
		Set<Xref> xrefs = pathway.getXref();	
		boolean go_bp_set = false;
		for(Xref xref : xrefs) {
			//dig out any xreferenced GO processes and assign them as types
			if(xref.getModelInterface().equals(RelationshipXref.class)) {
				RelationshipXref r = (RelationshipXref)xref;	    			
				//System.out.println(xref.getDb()+" "+xref.getId()+" "+xref.getUri()+"----"+r.getRelationshipType());
				//note that relationship types are not defined beyond text strings like RelationshipTypeVocabulary_gene ontology term for cellular process
				//you just have to know what to do.
				//here we add the referenced GO class as a type.  
				if(r.getDb().equals("GENE ONTOLOGY")) {
					String goid = r.getId().replaceAll(":", "_");
					OWLClass xref_go_parent = go_cam.df.getOWLClass(IRI.create(GoCAM.obo_iri + goid));
					//add it into local hierarchy (temp pre import)	
					//addRefBackedObjectPropertyAssertion
					go_cam.addSubclassAssertion(xref_go_parent, GoCAM.bp_class, null);
					go_cam.addTypeAssertion(pathway_e, xref_go_parent);
					go_bp_set = true;
					//record mappings
					report.write(pathway.getDisplayName()+"\t"+reactome_id+"\t"+goid+"\tBP\tPathway\n");
				}
			}
		}

		//record lack of mappings
		if(!go_bp_set) {
			report.write(pathway.getDisplayName()+"\t"+reactome_id+"\tnone\tBP\tPathway\n");
		}
		report.close();

		if(add_components) {
			//define the pieces of the pathway
			//Process subsumes Pathway and Interaction (which is usually a reaction).  
			//A pathway may have either or both reaction or pathway components.  
			for(Process process : pathway.getPathwayComponent()) {
				//Conversion subsumes BiochemicalReaction, TransportWithBiochemicalReaction, ComplexAssembly, Degradation, GeneticInteraction, MolecularInteraction, TemplateReaction
				//though the great majority are BiochemicalReaction
				//whatever it is, its a part of the pathway
				OWLNamedIndividual child = go_cam.df.getOWLNamedIndividual(GoCAM.makeGoCamifiedIRI(process.getUri()));
				if(noctua_version == 1) {
					//this will add something to see what happened as the has_part lines are going to get blown away.
					go_cam.addLiteralAnnotations2Individual(child.getIRI(), GoCAM.rdfs_comment, pathway.getDisplayName()+" has_part "+process.getDisplayName());
					go_cam.addLiteralAnnotations2Individual(child.getIRI(), GoCAM.rdfs_comment, process.getDisplayName()+" references PMIDS: "+pubids);
				}
				go_cam.addRefBackedObjectPropertyAssertion(pathway_e, GoCAM.has_part, child, pubids, GoCAM.eco_imported_auto, "PMID", null);
				//attach reactions that make up the pathway
				if(process instanceof Conversion 
						|| process instanceof TemplateReaction
						|| process instanceof GeneticInteraction 
						|| process instanceof MolecularInteraction ){
					defineReactionEntity(go_cam, process, GoCAM.makeGoCamifiedIRI(process.getUri()));				
					//attach child pathways
				}else if(process.getModelInterface().equals(Pathway.class)){				
					definePathwayEntity(go_cam, (Pathway)process, reactome_id, expand_subpathways, false);	
				}
				else {
					System.out.println("Unknown Process !"+process.getDisplayName());
					System.out.println("Process URI.. "+process.getUri());			
					System.out.println("Process model interface.. "+process.getModelInterface());	
					System.exit(0);
				}
			}
			//reaction -> reaction connections
			//TODO this would fit the 2nd step sparql rules.. would be more consistent to move it there and use the same pattern
			//below mapped from Chris Mungall's
			//prolog rules https://github.com/cmungall/pl-sysbio/blob/master/prolog/sysbio/bp2lego.pl
			Set<PathwayStep> steps = pathway.getPathwayOrder();
			for(PathwayStep step1 : steps) {
				Set<Process> events = step1.getStepProcess();
				Set<PathwayStep> step2s = step1.getNextStep();
				for(PathwayStep step2 : step2s) {
					Set<Process> nextEvents = step2.getStepProcess();
					for(Process event : events) {
						for(Process nextEvent : nextEvents) {
							//	Event directly_provides_input_for NextEvent
							if((event.getModelInterface().equals(BiochemicalReaction.class))&&
									(nextEvent.getModelInterface().equals(BiochemicalReaction.class))) {
								IRI e1_iri = GoCAM.makeGoCamifiedIRI(event.getUri());
								IRI e2_iri = GoCAM.makeGoCamifiedIRI(nextEvent.getUri());
								OWLNamedIndividual e1 = go_cam.df.getOWLNamedIndividual(e1_iri);
								OWLNamedIndividual e2 = go_cam.df.getOWLNamedIndividual(e2_iri);
								go_cam.addRefBackedObjectPropertyAssertion(e1, GoCAM.provides_direct_input_for, e2, Collections.singleton(reactome_id), GoCAM.eco_imported_auto, "Reactome", null);
								//in some cases, the reaction may connect off to a different pathway and hence not be caught in above loop to define reaction entities
								//e.g. Recruitment of SET1 methyltransferase complex  -> APC promotes disassembly of beta-catenin transactivation complex
								//are connected yet in different pathways
								//if its been defined, ought to at least have a label
								if(go_cam.getaLabel(e2).equals("")){
									defineReactionEntity(go_cam, nextEvent, e2_iri);		
								}
							}
						}
					}
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
	 * @throws IOException 
	 */
	private void defineReactionEntity(GoCAM go_cam, Entity entity, IRI this_iri) throws IOException {
		FileWriter report = new FileWriter(mapping_report_file, true);
		//add entity to ontology, whatever it is
		OWLNamedIndividual e = null;
		if(this_iri!=null) {
			e = go_cam.makeAnnotatedIndividual(this_iri);
		}else {
			e = go_cam.makeAnnotatedIndividual(GoCAM.makeGoCamifiedIRI(entity.getUri()));
		}
		String reactome_id = "";
		for(Xref xref : entity.getXref()) {
			if(xref.getModelInterface().equals(UnificationXref.class)) {
				UnificationXref r = (UnificationXref)xref;	    			
				if(r.getDb().equals("Reactome")) {
					reactome_id = r.getId();
					if(reactome_id.startsWith("R-HSA")) {
						break;
					}
				}
			}
		}		
		//this allows linkage between different OWL individuals in the GO-CAM sense that correspond to the same thing in the BioPax sense
		go_cam.addUriAnnotations2Individual(e.getIRI(),GoCAM.skos_exact_match, IRI.create(entity.getUri()));	
		//check for annotations
		Set<String> pubids = getPubmedIds(entity);
		String entity_name = entity.getDisplayName();
		go_cam.addLabel(e, entity_name);
		//attempt to localize the entity (only if Physical Entity because that is how Reactome views existence in space)
		if(entity instanceof PhysicalEntity) {
			if(noctua_version>1) { //noctua version only really for display and this mucks it up
				go_cam.addTypeAssertion(e, GoCAM.continuant_class); //will be specified further later.  This is here because Reactome sometimes does not make any more specific assertion than 'physical entity' for things like f-actin.  https://reactome.org/content/detail/R-HSA-202986
			}
			CellularLocationVocabulary loc = ((PhysicalEntity) entity).getCellularLocation();

			if(loc!=null) {			
				//dig out the GO cellular location and create an individual for it
				String location_term = null;
				Set<Xref> xrefs = loc.getXref();
				for(Xref xref : xrefs) {
					if(xref.getModelInterface().equals(UnificationXref.class)) {
						UnificationXref uref = (UnificationXref)xref;	    			
						//here we add the referenced GO class as a type.  
						if(uref.getDb().equals("GENE ONTOLOGY")) {
							OWLClass xref_go_loc = go_cam.df.getOWLClass(IRI.create(GoCAM.obo_iri + uref.getId().replaceAll(":", "_")));
							Set<XReferrable> refs = uref.getXrefOf();							
							for(XReferrable ref : refs) {
								location_term = ref.toString().replaceAll("CellularLocationVocabulary_", "");
								break;
							}
							if(location_term!=null) {
								OWLNamedIndividual loc_e = go_cam.makeAnnotatedIndividual(GoCAM.makeGoCamifiedIRI(loc.getUri()+entity.getUri()));
								go_cam.addLabel(xref_go_loc, location_term);
								go_cam.addTypeAssertion(loc_e, xref_go_loc);
								go_cam.addRefBackedObjectPropertyAssertion(e, GoCAM.located_in, loc_e, pubids, GoCAM.eco_imported_auto, "PMID", null);		
								if(noctua_version == 1) {
									go_cam.addLiteralAnnotations2Individual(e.getIRI(), GoCAM.rdfs_comment, "located_in "+location_term);
								}
								break; //there can be only one 
							}
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
				go_cam.addSubclassAssertion(uniprotein_class, GoCAM.chebi_protein, null);										
				//name the class with the uniprot id for now..
				//NOTE different protein versions are grouped together into the same root class by the conversion
				//e.g. Q9UKV3 gets the uniproteins ACIN1, ACIN1(1-1093), ACIN1(1094-1341)
				go_cam.addLabel(uniprotein_class, id);
				//until something is imported that understands the uniprot entities, assert that they are proteins
				go_cam.addTypeAssertion(e,  uniprotein_class);
			}else { //no entity reference so look for parts 
				Set<PhysicalEntity> prot_parts = protein.getMemberPhysicalEntity();
				if(prot_parts!=null) {					
					//if its made of parts and it doesn't have its own unique protein name, call it a complex..	
					Set<String> cnames = new HashSet<String>();
					for(PhysicalEntity prot_part : prot_parts) {
						cnames.add(prot_part.getDisplayName());
						//hook up parts	
						if(noctua_version == 1) { //Noctua view can't handle long parts lists so leave them out
							go_cam.addLiteralAnnotations2Individual(e.getIRI(), GoCAM.rdfs_comment, "has_part "+prot_part.getDisplayName());
						}else {
							OWLNamedIndividual prot_part_entity = go_cam.df.getOWLNamedIndividual(GoCAM.makeGoCamifiedIRI(prot_part.getUri()+entity.getUri())); //define it independently within this context
							go_cam.addObjectPropertyAssertion(e, GoCAM.has_part, prot_part_entity, null);		
							//define them = hopefully get out a name and a class for the sub protein.	
							defineReactionEntity(go_cam, prot_part, prot_part_entity.getIRI());
						}						
					}
					//adds a unique class to describe this complex 
					//TODO generally tbox modifications don't play well with Noctua/Minerva which expects only Abox in the model
					//but need to show something other than "molecular complex' for the nodes in the folded reaction view..
					//so stuffing the names into the class..  yay.  
					if(noctua_version == 1) { 
						addComplexAsSimpleClass(go_cam, cnames, e, null);
					}else {
						go_cam.addTypeAssertion(e, GoCAM.go_complex);
					}
				}else { 
					go_cam.addTypeAssertion(e,  GoCAM.chebi_protein);
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
				go_cam.addSubclassAssertion(uniprotein_class, GoCAM.chebi_protein, null);										
				go_cam.addLabel(uniprotein_class, id);
				//until something is imported that understands the uniprot entities, assert that they are proteins
				go_cam.addTypeAssertion(e, uniprotein_class);
			}else {
				//note that complex.getComponent() apparently violates the rules in its documentation which stipulate that it should return
				//a flat representation of the parts of the complex (e.g. proteins) and not nested complexes (which the reactome biopax does here)
				Set<String> cnames = new HashSet<String>();
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
						cnames.add(component.getDisplayName());
						//	IRI comp_uri = IRI.create(component.getUri()+e.hashCode());
						IRI comp_uri = GoCAM.makeGoCamifiedIRI(component.getUri()+entity.getUri());
						OWLNamedIndividual component_entity = go_cam.df.getOWLNamedIndividual(comp_uri);
						if(noctua_version == 1) { //Noctua view can't handle long parts lists so leave them out
							go_cam.addLiteralAnnotations2Individual(e.getIRI(), GoCAM.rdfs_comment, "has_part "+component.getDisplayName());
						}else {
							go_cam.addObjectPropertyAssertion(e, GoCAM.has_part, component_entity, null);
							defineReactionEntity(go_cam, component, comp_uri);
						}
					}
				}
				//adds a unique class to describe this complex (no no to modify tbox..)
				if(noctua_version == 1) { 
					addComplexAsSimpleClass(go_cam, cnames, e, null);
				}else {
					//assert it as a complex
					go_cam.addTypeAssertion(e, GoCAM.go_complex);
				}

			}
		}
		//Interaction subsumes Conversion, GeneticInteraction, MolecularInteraction, TemplateReaction
		//Conversion subsumes BiochemicalReaction, TransportWithBiochemicalReaction, ComplexAssembly, Degradation, GeneticInteraction, MolecularInteraction, TemplateReaction
		//though the great majority are BiochemicalReaction
		else if (entity instanceof Interaction){  			
			if (entity instanceof TemplateReaction) {
				Set<PhysicalEntity> products = ((TemplateReaction) entity).getProduct();
				for(PhysicalEntity output : products) {
					//IRI o_iri = IRI.create(output.getUri()+e.hashCode());
					IRI o_iri = GoCAM.makeGoCamifiedIRI(output.getUri()+entity.getUri());
					OWLNamedIndividual output_entity = go_cam.df.getOWLNamedIndividual(o_iri);
					defineReactionEntity(go_cam, output, o_iri);
					go_cam.addObjectPropertyAssertion(e, GoCAM.has_output, output_entity, go_cam.getDefaultAnnotations());
				}
				//not used ?
				//NucleicAcid nuc = ((TemplateReaction) entity).getTemplate();
				//TemplateDirectionType tempdirtype = ((TemplateReaction) entity).getTemplateDirection();
			}

			//Conversion reaction = (Conversion)(entity);
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

			//taking out biopax namespace references in things we will want to query
			//go_cam.addTypeAssertion(e, reaction_class);	
			boolean mf_set = false;
			//Create entities for reaction components
			Set<Entity> participants = ((Interaction) entity).getParticipant();
			for(Entity participant : participants) {
				//figure out its nature and capture that
				//			if(noctua_version == 1) {
				//				IRI participant_iri = GoCAM.makeGoCamifiedIRI(participant.getUri()); //merge them with members of other reactions to improve display..						
				//				defineReactionEntity(go_cam, participant, participant_iri);	
				//			}else {
				IRI participant_iri = GoCAM.makeGoCamifiedIRI(participant.getUri()+entity.getUri()); //keep the entities in this reaction uniquely identified.. (don't merge them with members of other reactions even if same class of thing)								
				defineReactionEntity(go_cam, participant, participant_iri);		
				//			}
			}
			//link to participants in reaction
			//biopax#left -> obo:input , biopax#right -> obo:output
			if(entity instanceof Conversion) {

				ConversionDirectionType direction = ((Conversion) entity).getConversionDirection();
				if(direction==null&&(entity instanceof Degradation)) {
					direction = ConversionDirectionType.LEFT_TO_RIGHT;
				}

				Set<PhysicalEntity> inputs = null;
				Set<PhysicalEntity> outputs = null;

				if(direction.equals(ConversionDirectionType.LEFT_TO_RIGHT)) {
					inputs = ((Conversion) entity).getLeft();
					outputs = ((Conversion) entity).getRight();
				}else if(direction.equals(ConversionDirectionType.RIGHT_TO_LEFT)) {
					outputs = ((Conversion) entity).getLeft();
					inputs = ((Conversion) entity).getRight();
					System.out.println("Right to left reaction found!  "+entity.getDisplayName()+" "+entity.getUri());
					System.exit(0);
				}else if(direction.equals(ConversionDirectionType.REVERSIBLE)) {
					System.out.println("REVERSIBLE reaction found!  "+entity.getDisplayName()+" "+entity.getUri());
					System.exit(0);
				}

				if(inputs!=null) {
					for(PhysicalEntity input : inputs) {
						IRI i_iri = null;
						i_iri = GoCAM.makeGoCamifiedIRI(input.getUri()+entity.getUri());
						OWLNamedIndividual input_entity = go_cam.df.getOWLNamedIndividual(i_iri);
						defineReactionEntity(go_cam, input, i_iri);
						go_cam.addObjectPropertyAssertion(e, GoCAM.has_input, input_entity,go_cam.getDefaultAnnotations());
					}}
				if(outputs!=null) {
					for(PhysicalEntity output : outputs) {
						IRI o_iri = null;
						o_iri = GoCAM.makeGoCamifiedIRI(output.getUri()+entity.getUri());
						OWLNamedIndividual output_entity = go_cam.df.getOWLNamedIndividual(o_iri);
						defineReactionEntity(go_cam, output, o_iri);
						go_cam.addObjectPropertyAssertion(e, GoCAM.has_output, output_entity, go_cam.getDefaultAnnotations());
					}}
			}

			if(entity instanceof Process) {
				//find controllers 
				//			Event directly_inhibits NextEvent
				//			   <==
				//			   control(Event),
				//			   controlled(Event,NextEvent),
				//			   controlType(Event,literal(type(_,'INHIBITION'))).
				Set<Control> controllers = ((Process) entity).getControlledOf();
				for(Control controller : controllers) {
					//make an individual of the class molecular function
					//controller 'entities' from biopax may map onto functions from go_cam
					//check for reactome mappings
					//dig out the GO molecular function and create an individual for it
					Set<Xref> xrefs = controller.getXref(); //controller is either a 'control', 'catalysis', 'Modulation', or 'TemplateReactionRegulation'
					for(Xref xref : xrefs) {
						if(xref.getModelInterface().equals(RelationshipXref.class)) {
							RelationshipXref ref = (RelationshipXref)xref;	    			
							//here we add the referenced GO class as a type.  
							if(ref.getDb().equals("GENE ONTOLOGY")) {
								String goid = ref.getId().replaceAll(":", "_");
								OWLClass xref_go_func = go_cam.df.getOWLClass(IRI.create(GoCAM.obo_iri + goid));
								//add the go function class as a type for the reaction instance being controlled here
								go_cam.addTypeAssertion(e, xref_go_func);
								mf_set = true;
								//record mappings
								report.write(entity.getDisplayName()+"\t"+reactome_id+"\t"+goid+"\tMF\tcontroller\n");
							}
						}
					}	
					if(!mf_set) {
						report.write(entity.getDisplayName()+"\t"+reactome_id+"\tnone\tMF\tcontroller\n");
					}
					ControlType ctype = controller.getControlType();				
					boolean is_catalysis = false;
					if(controller.getModelInterface().equals(Catalysis.class)) {
						is_catalysis = true;
					}
					Set<Controller> controller_entities = controller.getController();
					for(Controller controller_entity : controller_entities) {
						IRI iri = null;
						iri = GoCAM.makeGoCamifiedIRI(controller_entity.getUri()+entity.getUri()+"controller");
						defineReactionEntity(go_cam, controller_entity, iri);
						//the protein or complex
						OWLNamedIndividual controller_e = go_cam.df.getOWLNamedIndividual(iri);
						//the controlling physical entity enables that function/reaction
						//on occasion there are refs associated with the controller.
						Set<String> controllerpubrefs = getPubmedIds(controller_entity);		

						//define relationship between controller entity and reaction
						//if catalysis then always enabled by
						if(is_catalysis) {
							//make sure there is something there to define the node so it shows up in Noctua view
							if(noctua_version == 1) {
								Collection<OWLClassExpression> types = EntitySearcher.getTypes(controller_e, go_cam.go_cam_ont);
								if(types==null||types.size()==0) {
									String name = controller_entity.getDisplayName();
									OWLClass named_class = go_cam.df.getOWLClass(IRI.create(GoCAM.base_iri+"unknown_"+(name.replaceAll(" ", "_"))));
									go_cam.addSubclassAssertion(named_class, GoCAM.continuant_class, null);
									go_cam.addTypeAssertion(controller_e, named_class);
									go_cam.addLiteralAnnotations2Individual(controller_e.getIRI(), GoCAM.rdfs_comment, "Only defined as a Physical Entity.  No protein etc. identifier given by source.  Could be unknown, could be a family.");
									go_cam.addLabel(controller_e, name);
								}
							}
							go_cam.addRefBackedObjectPropertyAssertion(e, GoCAM.enabled_by, controller_e, controllerpubrefs, GoCAM.eco_imported_auto, "PMID", null);	

						}else {
							//otherwise look at text 
							//					//define how the molecular function (process) relates to the reaction (process)
							if(ctype.toString().startsWith("INHIBITION")){
								go_cam.addRefBackedObjectPropertyAssertion(controller_e, GoCAM.involved_in_negative_regulation_of, e, controllerpubrefs, GoCAM.eco_imported_auto, "PMID", null);	
							}else if(ctype.toString().startsWith("ACTIVATION")){
								go_cam.addRefBackedObjectPropertyAssertion(controller_e, GoCAM.involved_in_positive_regulation_of, e, controllerpubrefs, GoCAM.eco_imported_auto, "PMID", null);
							}else {
								//default to regulates
								go_cam.addRefBackedObjectPropertyAssertion(controller_e, GoCAM.involved_in_regulation_of,  e, controllerpubrefs, GoCAM.eco_imported_auto, "PMID", null);
							}
						}
					}
				}
				boolean reaction_mapped = false;
				for(Xref xref : entity.getXref()) {
					if(xref.getModelInterface().equals(RelationshipXref.class)) {
						RelationshipXref ref = (RelationshipXref)xref;	    			
						//here we add the referenced GO class as a type.  
						if(ref.getDb().equals("GENE ONTOLOGY")) {
							String goid = ref.getId().replaceAll(":", "_");
							OWLClass xref_go_func = go_cam.df.getOWLClass(IRI.create(GoCAM.obo_iri + goid));
							//add the go class as a type for the reaction instance 
							go_cam.addTypeAssertion(e, xref_go_func);
							reaction_mapped = true;
							//record mappings
							report.write(entity.getDisplayName()+"\t"+reactome_id+"\t"+goid+"\tBP?\treaction\n");
						}
					}
				}	
				if(!reaction_mapped) {
					report.write(entity.getDisplayName()+"\t"+reactome_id+"\tnone\tBP?\treaction\n");
				}

				//if(!mf_set) {
				//want to stay in go tbox as much as possible - even if defaulting to root nodes.  
				//always add it for now.. can take out when local dev pipeline includes the go hence making this redundant
				go_cam.addTypeAssertion(e, GoCAM.molecular_function);	
				//}
				//The OWL for the reaction and all of its parts should now be assembled.  Now can apply secondary rules to improve mapping to go-cam model
				//If all of the entities involved in a reaction are located in the same GO cellular component, 
				//add that the reaction/function occurs_in that location
				//take location information off of the components.  

				Set<OWLClass> reaction_places = new HashSet<OWLClass>();
				Set<OWLClass> input_places = getLocations(EntitySearcher.getObjectPropertyValues(e, GoCAM.has_input, go_cam.go_cam_ont), go_cam.go_cam_ont);
				Set<OWLClass> output_places = getLocations(EntitySearcher.getObjectPropertyValues(e, GoCAM.has_output, go_cam.go_cam_ont), go_cam.go_cam_ont);
				Set<OWLClass> enabler_places = getLocations(EntitySearcher.getObjectPropertyValues(e, GoCAM.enabled_by, go_cam.go_cam_ont), go_cam.go_cam_ont);
				Set<OWLClass> negreg_places = getLocations(EntitySearcher.getObjectPropertyValues(e, GoCAM.involved_in_negative_regulation_of, go_cam.go_cam_ont), go_cam.go_cam_ont);
				Set<OWLClass> posreg_places = getLocations(EntitySearcher.getObjectPropertyValues(e, GoCAM.involved_in_positive_regulation_of, go_cam.go_cam_ont), go_cam.go_cam_ont);

				reaction_places.addAll(input_places); reaction_places.addAll(output_places);  reaction_places.addAll(enabler_places); 
				reaction_places.addAll(negreg_places); reaction_places.addAll(posreg_places);
				if(reaction_places.size()==1) {
					//System.out.println("1 "+reaction +" "+reaction_places);
					for(OWLClass place : reaction_places) {
						//create the unique individual for this reaction's location individual
						IRI iri = GoCAM.makeGoCamifiedIRI(entity.getUri()+place.getIRI().toString());
						OWLNamedIndividual placeInstance = go_cam.df.getOWLNamedIndividual(iri);
						go_cam.addTypeAssertion(placeInstance, place);
						go_cam.addRefBackedObjectPropertyAssertion(e, GoCAM.occurs_in, placeInstance, pubids, GoCAM.eco_imported_auto, "PMID", null);
					}
					//					if(noctua_version==1) {
					//					//remove all location assertions for physical entities because they destroy noctua1.0 view
					//					go_cam.go_cam_ont = stripLocations(EntitySearcher.getObjectPropertyValues(e, GoCAM.has_input, go_cam.go_cam_ont), go_cam.go_cam_ont, go_cam.df);
					//					go_cam.go_cam_ont = stripLocations(EntitySearcher.getObjectPropertyValues(e, GoCAM.has_output, go_cam.go_cam_ont), go_cam.go_cam_ont, go_cam.df);
					//					go_cam.go_cam_ont = stripLocations(EntitySearcher.getObjectPropertyValues(e, GoCAM.enabled_by, go_cam.go_cam_ont), go_cam.go_cam_ont, go_cam.df);
					//					go_cam.go_cam_ont = stripLocations(EntitySearcher.getObjectPropertyValues(e, GoCAM.involved_in_negative_regulation_of, go_cam.go_cam_ont), go_cam.go_cam_ont, go_cam.df);
					//					go_cam.go_cam_ont = stripLocations(EntitySearcher.getObjectPropertyValues(e, GoCAM.involved_in_positive_regulation_of, go_cam.go_cam_ont), go_cam.go_cam_ont, go_cam.df);
					//				}
				}else {
					//System.out.println("1+++  "+reaction +" "+reaction_places);
					for(OWLClass place : reaction_places) {
						//TODO do something more clever to decide on where the function occurs if things are happening in multiple places.		
						String plabel = go_cam.getaLabel(place);
						go_cam.addLiteralAnnotations2Individual(e.getIRI(), GoCAM.rdfs_comment, "occurs_in "+plabel);
					}
				}
			}
		}
		report.close();
		return;
	}

	/**
	 * Since Noctua expects specific classes for individuals and go doesn't have them for complexes, make them.
	 * Note that these could be defined logically based on their parts if we ever wanted to do any inference.  
	 * @param go_cam
	 * @param component_names
	 * @param complex_i
	 * @param annotations
	 * @return
	 */
	private OWLNamedIndividual addComplexAsSimpleClass(GoCAM go_cam, Set<String> component_names, OWLNamedIndividual complex_i, Set<OWLAnnotation> annotations) {
		String combo_name = "";
		for(String n : component_names) {
			combo_name=combo_name+"_"+n;
		}
		OWLClass complex_class = go_cam.df.getOWLClass(GoCAM.makeGoCamifiedIRI(combo_name));
		Set<String> labels =  go_cam.getLabels(complex_i);
		for(String label : labels) {
			go_cam.addLabel(complex_class, label+" ");
		}
		go_cam.addSubclassAssertion(complex_class, GoCAM.go_complex, annotations);
		go_cam.addTypeAssertion(complex_i, complex_class);
		return complex_i;
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

	/**
	 * Sometimes complexes are annotated with locations as are all of their (potentially many) components.  These annotations
	 * really clog up the Noctua graph view as it stands now.  This should remove complex component location annotations and leave
	 * just one location annotation on the complex when they are all the same.  
	 * @param go_cam
	 */
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
	 * Tries to line up pathways/processes hotizontally on the top with reactions/functions associated with each one expanding vertically down
	 * When a reaction provides_input_for another reaction, try to line these up so the order flows from left to right.
	 * Try to group inputs above, outputs below and enablers - see @layoutReactionComponents
	 * @param go_cam
	 */
	//	private void layoutForNoctuaVersion1(GoCAM go_cam) {
	//		//removeRedundantLocations(go_cam);
	//		Iterator<OWLIndividual> pathways = EntitySearcher.getIndividuals(GoCAM.bp_class, go_cam.go_cam_ont).iterator();
	//		int y_spacer = 350; int x_spacer = 450;
	//		int y = 50; int x = 200;
	//		//generally only one pathway represented with reactions - others just links off via part of
	//		//draw them in a line across the top 
	//		while(pathways.hasNext()) {
	//			OWLNamedIndividual pathway = (OWLNamedIndividual)pathways.next();
	//			go_cam.addLiteralAnnotations2Individual(pathway.getIRI(), GoCAM.x_prop, go_cam.df.getOWLLiteral(x));
	//			go_cam.addLiteralAnnotations2Individual(pathway.getIRI(), GoCAM.y_prop, go_cam.df.getOWLLiteral(y));   			
	//			//find reactions that are part of this pathway
	//			//CLUNKY.. fighting query.. need to find a better way - maybe sparql... 
	//			int reaction_x = x; 
	//			int reaction_y = y + y_spacer;
	//			Iterator<OWLIndividual> reactions = EntitySearcher.getObjectPropertyValues(pathway, GoCAM.has_part, go_cam.go_cam_ont).iterator();
	//		
	//			//find out if there is a logical root - a reaction with no provides_direct_input_for relations coming into it
	//			Set<OWLIndividual> roots = new HashSet<OWLIndividual>();
	//			while(reactions.hasNext()) {
	//				OWLIndividual react = reactions.next();
	//				roots.add(react);
	//			}
	//			reactions = EntitySearcher.getObjectPropertyValues(pathway, GoCAM.has_part, go_cam.go_cam_ont).iterator();
	//			while(reactions.hasNext()) {	
	//				OWLIndividual react = reactions.next();
	//				Iterator<OWLIndividual> targets = EntitySearcher.getObjectPropertyValues(react, GoCAM.provides_direct_input_for, go_cam.go_cam_ont).iterator();
	//				while(targets.hasNext()) {
	//					OWLIndividual target = targets.next();			
	//					roots.remove(target);				
	//				}
	//			}
	//			//if there is a root or roots.. do a sideways bread first layout
	//			if(roots.size()>0) {
	//				for(OWLIndividual root : roots) {
	//					layoutHorizontalTree(reaction_x, reaction_y, x_spacer, y_spacer, (OWLNamedIndividual)root, GoCAM.provides_direct_input_for, go_cam);
	//					reaction_y = reaction_y + y_spacer;
	//				}				
	//			}else { // do loop layout.  
	//				reactions = EntitySearcher.getObjectPropertyValues(pathway, GoCAM.has_part, go_cam.go_cam_ont).iterator();
	//				if(reactions.hasNext()) {
	//					System.out.println(pathway + "Loop layout!");
	//					OWLNamedIndividual loopstart = (OWLNamedIndividual) reactions.next();
	//					layoutLoopish(reaction_x, reaction_y, x_spacer, y_spacer, loopstart, GoCAM.provides_direct_input_for, go_cam);		
	//				}
	//			}
	//			x = x+x_spacer;
	//		}
	//	}

	/**
	 * Given knowledge of semantic structure of a GO-CAM, try to make a basic layout that is useful within the Noctua editor as it stands in Version1 (May 2018).
	 * In this implementation, all function node attributes should be fully 'folded' in the the UI. 
	 * Attempt to line the function nodes up in some reasonable order..
	 * @param go_cam
	 */
	private void layoutForNoctuaVersion1(GoCAM go_cam) {
		Iterator<OWLIndividual> pathways = EntitySearcher.getIndividuals(GoCAM.bp_class, go_cam.go_cam_ont).iterator();
		int y_spacer = 300; int x_spacer = 450;
		int y = 50; int x = 200;
		//generally only one pathway represented with reactions - others just links off via part of
		//draw them in a line across the top 
		while(pathways.hasNext()) {
			OWLNamedIndividual pathway = (OWLNamedIndividual)pathways.next();

			//making Pathway basically just a label for what people are looking at
			//all has_part connections taken out to clear up view
			//put it at top left
			int h = 20; 
			int k = 20; 

			go_cam.addLiteralAnnotations2Individual(pathway.getIRI(), GoCAM.x_prop, go_cam.df.getOWLLiteral(h));
			go_cam.addLiteralAnnotations2Individual(pathway.getIRI(), GoCAM.y_prop, go_cam.df.getOWLLiteral(k));   			

			//find reactions that are part of this pathway
			Collection<OWLIndividual> reactions_and_subpathways = EntitySearcher.getObjectPropertyValues(pathway, GoCAM.has_part, go_cam.go_cam_ont);
			Set<OWLIndividual> reactions = new HashSet<OWLIndividual>();

			for(OWLIndividual r : reactions_and_subpathways) {
				for(OWLClassExpression type :EntitySearcher.getTypes(r, go_cam.go_cam_ont)) {
					OWLClass c = type.asOWLClass();
					if(c.equals(GoCAM.molecular_function)) {
						reactions.add(r);
						break;
					}
				}
			}
			//classify reactions: root of causal chain, member of chain, island
			Set<OWLIndividual> islands = new HashSet<OWLIndividual>();
			Set<OWLIndividual> chain_roots = new HashSet<OWLIndividual>();
			Set<OWLIndividual> chain_members = new HashSet<OWLIndividual>();
			for(OWLIndividual r : reactions) {
				int incoming = 0;
				int outgoing = 0;
				Collection<OWLObjectPropertyAssertionAxiom> axioms = getCausalReferencingOPAxioms((OWLEntity) r, go_cam);
				for(OWLObjectPropertyAssertionAxiom op : axioms) {
					if(op.getSubject().equals(r)) {
						outgoing++;
					}else if(op.getObject().equals(r)) {
						incoming++;
					}		
				}
				if(incoming==0&&outgoing==0) {
					islands.add(r);
				}
				else if(incoming==0&&outgoing>0) {
					chain_roots.add(r);
				}else if(incoming>0) {
					chain_members.add(r);
				}

			}

			//if there is a root or roots.. do a sideways horizontal line graph
			if(chain_roots.size()>0) {
				System.out.println(pathway + "Chain layout!");
				layoutChain(250, 20, 350, 500, chain_roots, chain_members, islands, go_cam);	
			}else if(chain_members.size()==0) {
				layoutChain(250, 20, 200, 500, chain_roots, chain_members, islands, go_cam);	
			}else{	
				// do circle layout 
				if(reactions!=null) {					
					System.out.println(pathway + "Circle layout!");
					layoutCircle(chain_members, islands, go_cam);		
				}
			}
			x = x+x_spacer;
		}
	}

	private Set<OWLObjectPropertyAssertionAxiom> getCausalReferencingOPAxioms(OWLEntity e, GoCAM go_cam){
		Collection<OWLAxiom> axioms = EntitySearcher.getReferencingAxioms((OWLEntity) e, go_cam.go_cam_ont);
		Set<OWLObjectPropertyAssertionAxiom> causal_axioms = new HashSet<OWLObjectPropertyAssertionAxiom>();
		for(OWLAxiom axiom : axioms) {
			if(axiom.isOfType(AxiomType.OBJECT_PROPERTY_ASSERTION)){
				OWLObjectPropertyAssertionAxiom op = (OWLObjectPropertyAssertionAxiom) axiom;
				//causal only
				//TODO would be fun to actually use the RO and a little inference to make this list..  
				if(op.getProperty().equals(GoCAM.directly_negatively_regulates)||
						op.getProperty().equals(GoCAM.directly_positively_regulates)||
						op.getProperty().equals(GoCAM.directly_negatively_regulated_by)||
						op.getProperty().equals(GoCAM.directly_positively_regulated_by)||
						op.getProperty().equals(GoCAM.provides_direct_input_for)) {
					causal_axioms.add(op);
				}
			}				
		}
		return causal_axioms;
	}

	private void layoutChain(int x, int y, int x_spacer, int y_spacer, Set<OWLIndividual> chain_roots, Set<OWLIndividual> chain_members, Set<OWLIndividual> islands, GoCAM go_cam) {
		int max_y = 0;
		int r = 0;
		int r2_start = 75;
		for(OWLIndividual root : chain_roots) {
			if(r%2==0) {
				max_y = layoutHorizontalLine(x, y, x_spacer, y_spacer, root, go_cam);
			}else {
				max_y = layoutHorizontalLine(r2_start, y, x_spacer, y_spacer, root, go_cam);
			}
			y = max_y+y_spacer;
			r++;
		}	
		if(r%2!=0) {
			x = r2_start;
		}
		for(OWLIndividual island : islands) {
			go_cam.addLiteralAnnotations2Individual(((OWLNamedIndividual) island).getIRI(), GoCAM.x_prop, go_cam.df.getOWLLiteral(x));
			go_cam.addLiteralAnnotations2Individual(((OWLNamedIndividual) island).getIRI(), GoCAM.y_prop, go_cam.df.getOWLLiteral(y));
			x+=x_spacer;
		}
	}

	private int layoutHorizontalLine(int x, int y, int x_spacer, int y_spacer, OWLIndividual node, GoCAM go_cam) {
		//don't fly into infinity and beyond!
		if(mapHintPresent((OWLNamedIndividual) node, go_cam)) {		
			return y;
		}
		System.out.println("laying out "+go_cam.getaLabel((OWLEntity) node)+" "+node.toString()+x+" "+y+" "+x_spacer+" "+y_spacer);
		//layout the node
		go_cam.addLiteralAnnotations2Individual(((OWLNamedIndividual) node).getIRI(), GoCAM.x_prop, go_cam.df.getOWLLiteral(x));
		go_cam.addLiteralAnnotations2Individual(((OWLNamedIndividual) node).getIRI(), GoCAM.y_prop, go_cam.df.getOWLLiteral(y));
		//recursively lay out children
		Collection<OWLIndividual> children = EntitySearcher.getObjectPropertyValues(node, GoCAM.provides_direct_input_for, go_cam.go_cam_ont);
		if(children.size()==0) {
			Collection<OWLObjectPropertyAssertionAxiom> axioms = getCausalReferencingOPAxioms((OWLEntity) node, go_cam);
			for(OWLObjectPropertyAssertionAxiom axiom : axioms) {
				if(node.equals(((OWLObjectPropertyAssertionAxiom) axiom).getSubject())){
					children.add(((OWLObjectPropertyAssertionAxiom) axiom).getObject());
				}				
			}
		}
		int nrows = 0;
		//typically there will only be one, but... 
		for(OWLIndividual child : children) {
			if(!mapHintPresent((OWLNamedIndividual) child, go_cam)) {
				layoutHorizontalLine(x+x_spacer, y, x_spacer, y_spacer, child, go_cam);
				nrows++;
				y = y+y_spacer;
			}
		}
		if(nrows==1) {
			return y-y_spacer;
		}
		return y;
	}

	private void layoutCircle(Collection<OWLIndividual> chain_members, Collection<OWLIndividual> islands, GoCAM go_cam) {
		//layout any unconnected nodes on the top for this one
		int x = 250; int y = 20; int x_spacer = 75;
		for(OWLIndividual island : islands) {
			go_cam.addLiteralAnnotations2Individual(((OWLNamedIndividual) island).getIRI(), GoCAM.x_prop, go_cam.df.getOWLLiteral(x));
			go_cam.addLiteralAnnotations2Individual(((OWLNamedIndividual) island).getIRI(), GoCAM.y_prop, go_cam.df.getOWLLiteral(y));
			x+=x_spacer;
		}		
		
		LinkedHashSet<OWLIndividual> ordered_chain = causalSort(chain_members, null, null, go_cam);
		//add any stragglers.. give up on multiple loops for now. 
		if(chain_members.size()>ordered_chain.size()) {
			for(OWLIndividual member : chain_members) {
				ordered_chain.add(member);//hashset should ensure non-redundancy
			}
		}
		//TODO calculate based on n nodes
		//currently reasonable approximation for small number of nodes
		int h = 800; // x coordinate of circle center
		int k = 700; // y coordinate of circle center (y going down for web layout)
		int r = 600; //radius of circle 
		int n = chain_members.size(); //number nodes to draw 
		double step = 2*Math.PI/n; //radians to move about circle per step 
		double theta = 0;
		for(OWLIndividual reaction_node : ordered_chain) {
			x = Math.round((long)(h + r*Math.cos(theta)));
			y = Math.round((long)(k - r*Math.sin(theta))); 
			theta = theta + step;
			IRI node_iri = reaction_node.asOWLNamedIndividual().getIRI();
			go_cam.addLiteralAnnotations2Individual(node_iri, GoCAM.x_prop, go_cam.df.getOWLLiteral(x));
			go_cam.addLiteralAnnotations2Individual(node_iri, GoCAM.y_prop, go_cam.df.getOWLLiteral(y));
		}
	}
	
	private LinkedHashSet<OWLIndividual> causalSort(Collection<OWLIndividual> chain_members, LinkedHashSet<OWLIndividual> ordered_chain, OWLIndividual node, GoCAM go_cam){
		if(ordered_chain==null&&node==null) {
			//initialize
			ordered_chain = new LinkedHashSet<OWLIndividual>();
			node = chain_members.iterator().next();
			ordered_chain.add(node);
		}
		//get causal child 
		Collection<OWLIndividual> children = EntitySearcher.getObjectPropertyValues(node, GoCAM.provides_direct_input_for, go_cam.go_cam_ont);
		if(children.size()==0) {
			Collection<OWLObjectPropertyAssertionAxiom> axioms = getCausalReferencingOPAxioms((OWLEntity) node, go_cam);
			for(OWLObjectPropertyAssertionAxiom axiom : axioms) {
				if(node.equals(((OWLObjectPropertyAssertionAxiom) axiom).getSubject())){
					children.add(((OWLObjectPropertyAssertionAxiom) axiom).getObject());
				}				
			}
		}
		//add one
		if(children.size()>0) {
			OWLIndividual next = children.iterator().next();
			if(ordered_chain.add(next)) {
				ordered_chain = causalSort(chain_members, ordered_chain, next, go_cam);
			}
		}
		return ordered_chain;
	}


	/**
	 * Try to make a consistent layout for the standard components of a biopax reaction as represented in a go-cam.
	 * Inputs above, Outputs below, Enablers 
	 * @param reaction
	 * @param go_cam
	 * @param reaction_x
	 * @param reaction_y
	 */
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
		// down for output
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
		//TODO
		//involved in negative / positive regulation of 
		//left and down
		int negative_regulator_x = reaction_x - 400;
		int negative_regulator_y = reaction_y + 200;
		//this doesn't make sense... 
		Iterator<OWLIndividual> neg_regulators = EntitySearcher.getObjectPropertyValues(reaction, GoCAM.involved_in_negative_regulation_of, go_cam.go_cam_ont).iterator();
		if(neg_regulators!=null) {
			while(neg_regulators.hasNext()) {
				OWLNamedIndividual regulator = (OWLNamedIndividual)neg_regulators.next();
				Collection<OWLAnnotation> c = EntitySearcher.getAnnotationObjects(regulator.getIRI(), go_cam.go_cam_ont, GoCAM.x_prop);				
				if(c.size()==0) {
					go_cam.addLiteralAnnotations2Individual(regulator.getIRI(), GoCAM.x_prop, go_cam.df.getOWLLiteral(negative_regulator_x));
					go_cam.addLiteralAnnotations2Individual(regulator.getIRI(), GoCAM.y_prop, go_cam.df.getOWLLiteral(negative_regulator_y));									
				}
			}
		}
		int positive_regulator_x = reaction_x - 400;
		int positive_regulator_y = reaction_y + 200;
		Iterator<OWLIndividual> pos_regulators = EntitySearcher.getObjectPropertyValues(reaction, GoCAM.involved_in_positive_regulation_of, go_cam.go_cam_ont).iterator();
		if(pos_regulators!=null) {
			while(pos_regulators.hasNext()) {
				OWLNamedIndividual regulator = (OWLNamedIndividual)pos_regulators.next();
				Collection<OWLAnnotation> c = EntitySearcher.getAnnotationObjects(regulator.getIRI(), go_cam.go_cam_ont, GoCAM.x_prop);				
				if(c.size()==0) {
					go_cam.addLiteralAnnotations2Individual(regulator.getIRI(), GoCAM.x_prop, go_cam.df.getOWLLiteral(positive_regulator_x));
					go_cam.addLiteralAnnotations2Individual(regulator.getIRI(), GoCAM.y_prop, go_cam.df.getOWLLiteral(positive_regulator_y));									
				}
			}
		}

	}

	/**
	 * Layout the 'parts_of' the given entity (e.g. a Complex) such that they group around it.
	 * @param entity
	 * @param entity_x
	 * @param entity_y
	 * @param go_cam
	 */
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

	/**
	 * Idea here is to start building a semi-circle to show a closed loop.  Its not working great.
	 * @param start_x
	 * @param start_y
	 * @param x_spacer
	 * @param y_spacer
	 * @param reaction_node
	 * @param edge_type
	 * @param go_cam
	 */
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




	/**
	 * Have we already given the node a location?
	 * @param node
	 * @param go_cam
	 * @return
	 */
	private boolean mapHintPresent(OWLNamedIndividual node, GoCAM go_cam){
		boolean x_present = false;
		//long nx = EntitySearcher.getAnnotationObjects(node, go_cam.go_cam_ont, GoCAM.x_prop).count();
		Collection<OWLAnnotation> xs = EntitySearcher.getAnnotationObjects(node, go_cam.go_cam_ont, GoCAM.x_prop);
		if(xs.size()>0) {
			x_present = true;
		}
		return x_present;
	}

	/**
	 * Given the edge_type (e.g. provides_direct_input_for) recursively follow the links along that edge and position the nodes in a horizontal row.
	 * @param start_x
	 * @param start_y
	 * @param x_spacer
	 * @param y_spacer
	 * @param reaction_root
	 * @param edge_type
	 * @param go_cam
	 */
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

	private void layoutHorizontalTreeNoctuaVersion1(int start_x, int start_y, int x_spacer, int y_spacer, OWLNamedIndividual reaction_root, OWLObjectProperty edge_type, GoCAM go_cam) {
		//don't fly into infinity and beyond!
		if(mapHintPresent(reaction_root, go_cam)) {
			return;
		}
		go_cam.addLiteralAnnotations2Individual(reaction_root.getIRI(), GoCAM.x_prop, go_cam.df.getOWLLiteral(start_x));
		go_cam.addLiteralAnnotations2Individual(reaction_root.getIRI(), GoCAM.y_prop, go_cam.df.getOWLLiteral(start_y));
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
