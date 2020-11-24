/**
 * 
 */
package org.geneontology.gocam.exchange;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

import org.biopax.paxtools.io.BioPAXIOHandler;
import org.biopax.paxtools.io.SimpleIOHandler;
import org.biopax.paxtools.model.BioPAXElement;
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
import org.biopax.paxtools.model.level3.DnaRegion;
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
import org.biopax.paxtools.model.level3.Provenance;
import org.biopax.paxtools.model.level3.PublicationXref;
import org.biopax.paxtools.model.level3.RelationshipXref;
import org.biopax.paxtools.model.level3.Rna;
import org.biopax.paxtools.model.level3.RnaRegion;
import org.biopax.paxtools.model.level3.SimplePhysicalEntity;
import org.biopax.paxtools.model.level3.SmallMolecule;
import org.biopax.paxtools.model.level3.TemplateDirectionType;
import org.biopax.paxtools.model.level3.TemplateReaction;
import org.biopax.paxtools.model.level3.UnificationXref;
import org.biopax.paxtools.model.level3.XReferrable;
import org.biopax.paxtools.model.level3.Xref;
import org.geneontology.rules.engine.Explanation;
import org.geneontology.rules.engine.Node;
import org.geneontology.rules.engine.Rule;
import org.geneontology.rules.engine.Triple;
import org.geneontology.rules.engine.TriplePattern;
import org.geneontology.rules.engine.WorkingMemory;
import org.obolibrary.robot.CatalogXmlIRIMapper;
import org.openrdf.repository.RepositoryException;
import org.openrdf.rio.RDFHandlerException;
import org.openrdf.rio.RDFParseException;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.AxiomType;
import org.semanticweb.owlapi.model.ClassExpressionType;
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
import org.semanticweb.owlapi.model.OWLObjectIntersectionOf;
import org.semanticweb.owlapi.model.OWLObjectProperty;
import org.semanticweb.owlapi.model.OWLObjectPropertyAssertionAxiom;
import org.semanticweb.owlapi.model.OWLObjectUnionOf;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyCreationException;
import org.semanticweb.owlapi.model.OWLOntologyManager;
import org.semanticweb.owlapi.model.OWLOntologyStorageException;
import org.semanticweb.owlapi.model.OWLSubClassOfAxiom;
import org.semanticweb.owlapi.search.EntitySearcher;
import org.semanticweb.owlapi.util.OWLEntityRemover;
import org.semarglproject.vocab.OWL;

/**
 * @author bgood
 *
 */
public class BioPaxtoGO {
	String go_lego_file;
	String base_provider;
	GOLego golego; //The fully axiomitized Gene Ontology. Used in multiple places for different purposes. 
	String blazegraph_output_journal;//Generated models will be stored both as files and as entries in this blazegraph journal.  Note this is ready for use in a Noctua/Minerva instance without any further processing.
	QRunner tbox_qrunner;
	ImportStrategy strategy;
	enum ImportStrategy {
		NoctuaCuration, 
	}
	EntityStrategy entityStrategy;
	enum EntityStrategy {
		REACTO,
		YeastCyc
	}
	boolean add_bp_from_pathway_parents = false;  //sometimes subpathways do not have BP annotation while parents do. Turning this on assumes that subpathway means subclass and adds parent BP as type for subpathway nodes missing BP annotation.
	boolean apply_layout = false; //If true, attempts a rational layout for Noctua based on the semantic structure of the model.  (Breaks down or larger models)
	boolean generate_report = true; //If true, generates GoCAMReport and ReasonerReport objects after each model is created.  These are added to the GoMappingReport object that contains one report per pathway processed. 
	boolean explain_inconsistant_models = true; //If true, will output text explanations for OWL inconsistent models before halting.  
	GoMappingReport report; //Captures details of mappings from input biopax pathways to output go-cams as well as information about the results of OWL reasoning on these models.   
	Model biopax_model; //The BioPAX model that is being converted.  
	boolean check_consistency = true; //set to true to execute an OWL consistency check each time a pathway is processed.  If inconsistent, it generates a report and halts the program
	boolean ignore_diseases = true; //If true, skips any pathway that has the word 'disease' in its name or any of its parent pathway's name 
	boolean drop_drug_reactions = true; //If true, removes reactions that involve drugs (as determined by the presence of an IUPHAR id on the physical entity). 
	boolean add_lego_import = false; //If true, an OWL import statement bring in go-lego.owl is added to each generated model.  
	boolean save_inferences = false;  //If true, adds inferences to blazegraph journal
	boolean split_by_pathway = true; //keep to true unless you want one giant model for whatever you input
	boolean expand_subpathways = false;  //If true, add content from all lower level pathways.  This is generally a bad idea for high level nodes like 'Signaling Pathways'
	//these define the extent to which information from other pathways is brought into the pathway in question
	//leaving all false, limits the reactions captured in each pathway to those shown in a e.g. Reactome view of the pathway
	boolean causal_recurse = false;  //if true this will follow BioPAX nextStep links to gather content from other pathways
	boolean add_pathway_parents = false; //if true will add all pathways that contain each reaction.  (Reactions may be present in multiple pathways.)
	boolean add_neighboring_events_from_other_pathways = true; //if true will pull in nextStep connections from other pathways.  Note that this is not recursive, will only go one level out.
	boolean add_upstream_controller_events_from_other_pathways = false; //if true will add reactions from other pathways if one of their participants is a controller (catalyst or regulator) of a reaction in the current pathway.  
	boolean add_subpathway_bridges = true; //this is groundwork for an approach that generates go-cams that reference members of other go-cams, here referencing other pathways.  
	String default_namespace_prefix = "Reactome"; //this is used to generate curi structured references - e.g. Reactome:HSA-007
	Set<String> drug_process_ids = new HashSet<String>(); 
	Map<String, String> accession_neo = new HashMap<String, String>(); //in case we need to store mappings to neo IRIs, use this
	public BioPaxtoGO(){
		strategy = ImportStrategy.NoctuaCuration; 
		report = new GoMappingReport();
	}

	void convert(
			String input_biopax, 
			String file_output_path, 
			String base_title, 
			String base_contributor, 
			String base_provider_i, 
			String tag, 
			Set<String> test_pathway_names,
			Blazer blaze,
			Set<String> taxa) throws OWLOntologyCreationException, OWLOntologyStorageException, RepositoryException, RDFParseException, RDFHandlerException, IOException  {
		//set for writing metadata
		String datasource = "";
		base_provider = base_provider_i;
		if(base_provider.equals("https://reactome.org")) {
			datasource = "Reactome";
		}else if(base_provider.equals("https://www.wikipathways.org/")) {
			datasource = "Wikipathways";
			default_namespace_prefix = "wikipathways";
		}else if(base_provider.equals("https://www.pathwaycommons.org/")) {
			datasource = "Pathway Commons";
			default_namespace_prefix = "pathwaycommons";
		}else if(base_provider.equals("https://yeastgenome.org")) {
			datasource = "Saccharomyces Genome Database";
			default_namespace_prefix = "SGD";
		}
		//will determine how classes for physical entities are handled
		if(entityStrategy.equals(EntityStrategy.YeastCyc)) {
			//neo needs e.g.
			//http://identifiers.org/sgd/S000001024
			//we get e.g.
			//YeastCyc YIL155C-MONOMER
			//we get the mapping from a YeastCyc GPI file downloaded from http://sgd-archive.yeastgenome.org/curation/literature/ 
			//e.g. http://sgd-archive.yeastgenome.org/curation/literature/gp_information.559292_sgd.gpi.gz 
			BufferedReader reader = new BufferedReader(new FileReader("./target/classes/YeastCyc/gp_information.559292_sgd"));
			String line = reader.readLine();
			while(line!=null) {
				if(line.startsWith("!")) {
					line = reader.readLine();
				}else {
					String[] cols = line.split("	");
					String yeastcyc = cols[4];
					Set<String> yeastcyc_ids = new HashSet<String>();
					if(yeastcyc.contains("|")) {
						String[] ids = yeastcyc.split("\\|");
						for(String id : ids) {
							yeastcyc_ids.add(id);
						}
					}else {
						yeastcyc_ids.add(yeastcyc);
					}
					if(cols[0].contentEquals("ComplexPortal")) {
						for(String yeastacc : yeastcyc_ids) {
							accession_neo.put(yeastacc, "http://purl.obolibrary.org/obo/ComplexPortal_"+cols[1]);
						}	
					}else if(cols.length>=8) {
						String sgd = cols[8];
						Set<String> sgd_ids = new HashSet<String>();
						if(sgd.contains("|")) {
							String[] ids = sgd.split("\\|");
							for(String id : ids) {
								sgd_ids.add(id);
							}
						}else {
							sgd_ids.add(sgd);
						}				
						for(String yeastacc : yeastcyc_ids) {
							for(String sgdid : sgd_ids) {
								accession_neo.put(yeastacc, sgdid.replace("SGD:", "http://identifiers.org/sgd/"));
							}
						}					
					}
					line = reader.readLine();
				}
			}
			reader.close();
		}
		//read biopax pathway(s)
		BioPAXIOHandler handler = new SimpleIOHandler();
		FileInputStream f = new FileInputStream(input_biopax);
		biopax_model = handler.convertFromOWL(f);
		int n_pathways = 0;

		//set up ontology (used if not split)
		String base_ont_title = base_title;
		String iri = "http://model.geneontology.org/"+URLEncoder.encode(base_ont_title, StandardCharsets.UTF_8.toString()); 
		IRI ont_iri = IRI.create(iri);
		GoCAM go_cam = new GoCAM(ont_iri, base_ont_title, base_contributor, null, base_provider, add_lego_import, taxa);
		go_cam.qrunner = tbox_qrunner;
		//set up access to blazegraph journal for output
		boolean save2blazegraph = false;
		go_cam.path2bgjournal = blazegraph_output_journal;
		if(go_cam.path2bgjournal !=null) {
			save2blazegraph = true;
		}

		//list pathways
		int total_pathways = biopax_model.getObjects(Pathway.class).size();
		boolean add_pathway_components = true;
		for (Pathway currentPathway : biopax_model.getObjects(Pathway.class)){
			go_cam.name = getBioPaxName(currentPathway);
			if(test_pathway_names!=null&&!test_pathway_names.contains(go_cam.name)) {
				continue;
			}
			if(!keepPathway(currentPathway)){ //Pathway Commons contains a lot of content free stubs when viewed this way
				System.out.println("Skipping pathway: "+getBioPaxName(currentPathway));
				continue;
			}
			String model_id = null;
			Set<String> pathway_source_comments = new HashSet<String>();
			n_pathways++;
			System.out.println(n_pathways+" of "+total_pathways+" Pathway:"+currentPathway.getName()); 
			if(split_by_pathway) {
				//then reinitialize for each pathway
				model_id = ""+URLEncoder.encode(base_ont_title, StandardCharsets.UTF_8.toString());
				String contributor_link = base_provider;
				//See if there is a specific pathway reference to allow a direct link
				model_id = getEntityReferenceId(currentPathway);
				if(base_provider.equals("https://reactome.org")) {
					contributor_link = "https://reactome.org/content/detail/"+model_id;
				}else if(base_provider.equals("https://yeastgenome.org")) {
					contributor_link = "https://pathway.yeastgenome.org/YEAST/NEW-IMAGE?object="+model_id;
				}else {
					contributor_link = base_provider+"/"+model_id;					
				}
				//check for datasource (seen commonly in Pathway Commons)
				Set<Provenance> datasources = currentPathway.getDataSource();
				for(Provenance prov : datasources) {
					if(prov.getDisplayName()!=null) {
						datasource = prov.getDisplayName();
						if(datasource==null) {
							datasource = prov.getStandardName();
						}
					}
					// there is more provenance buried in comment field 
					pathway_source_comments.addAll(prov.getComment());
					//e.g. for a WikiPathways model retrieved from Pathway Commons I see
					//Source http://pointer.ucsf.edu/wp/biopax/wikipathways-human-v20150929-biopax3.zip type: BIOPAX, WikiPathways - Community Curated Human Pathways; 29/09/2015 (human)
				}			
				base_ont_title = datasource+":"+tag+":"+getBioPaxName(currentPathway);
				iri = "http://model.geneontology.org/"+model_id; 
				ont_iri = IRI.create(iri);	
				go_cam = new GoCAM(ont_iri, base_ont_title, contributor_link, null, base_provider, add_lego_import, taxa);
				//journal is by default in 'append' mode - keeping the same journal reference add each pathway to same journal
				go_cam.path2bgjournal = blazegraph_output_journal;
				go_cam.blazegraphdb = blaze;
				go_cam.name = getBioPaxName(currentPathway);
			}
			//make the OWL individual representing the pathway so it can be used below
			OWLNamedIndividual p = go_cam.makeAnnotatedIndividual(GoCAM.makeGoCamifiedIRI(null, model_id));
			//annotate it with any provenance comments
			for(String comment : pathway_source_comments) {
				go_cam.addComment(p, comment);
			}
			//define it (add types etc)
			definePathwayEntity(go_cam, currentPathway, model_id, expand_subpathways, add_pathway_components);	
			//get and set parent pathways
			if(add_pathway_parents) {
				//Set<String> pubids = getPubmedIds(currentPathway);
				for(Pathway parent_pathway : currentPathway.getPathwayComponentOf()) {		
					String parent_pathway_id = getEntityReferenceId(parent_pathway);
					OWLNamedIndividual parent = go_cam.makeAnnotatedIndividual(GoCAM.makeGoCamifiedIRI(null, parent_pathway_id));
					go_cam.addRefBackedObjectPropertyAssertion(p, GoCAM.part_of, parent, Collections.singleton(model_id), GoCAM.eco_imported_auto,  default_namespace_prefix, null, model_id);
					//don't add all the information on the parent pathway to this pathway
					definePathwayEntity(go_cam, parent_pathway, model_id, false, false);
				}
			}
			//write results
			if(split_by_pathway) {
				//				String n = currentPathway.getDisplayName();
				//				n = n.replaceAll("/", "-");	
				//				n = n.replaceAll(" ", "_");
				//				String outfilename = converted+n+".ttl";	
				String outfilename = file_output_path+model_id+".ttl";
				wrapAndWrite(outfilename, go_cam, save_inferences, save2blazegraph, getBioPaxName(currentPathway), expand_subpathways, model_id);
				//reset for next pathway.
				go_cam.ontman.removeOntology(go_cam.go_cam_ont);
				go_cam.qrunner = null;
				System.out.println("reseting for next pathway...");
			} 
		}	
		//export all
		if(!split_by_pathway) {
			wrapAndWrite(file_output_path+".ttl", go_cam, save_inferences, save2blazegraph, file_output_path, expand_subpathways, null);		
		}

		System.out.println("done with file "+input_biopax+" ");
		if(generate_report) {
			report.writeReport(file_output_path+"_report_");
		}
	}

	public String getEntityReferenceId(Entity bp_entity) {
		String id = null;
		Set<Xref> references = null;
		//first check for entity reference
		if(bp_entity instanceof PhysicalEntity) {
			PhysicalEntity entity = (PhysicalEntity) bp_entity;
			references = entity.getXref();
		}
		//otherwise get direct xrefs
		if(references==null) {
			references = bp_entity.getXref();
		}
		if(entityStrategy.equals(EntityStrategy.REACTO)) {
			for(Xref ref : references) {
				if(ref.getModelInterface().equals(UnificationXref.class)) {
					UnificationXref r = (UnificationXref)ref;	    			
					if(r.getDb().equals("Reactome")) {
						id = r.getId();
						if(id.startsWith("R-")) {
							break;
						}
					}
				}
			}
			//if id not found in unification references, check on other xrefs - use only for reactome
			if(id==null) {
				//Reactome Database ID
				for(Xref r : references) {   			
					if(r.getDb().startsWith("Reactome Database ID")) {
						id = r.getId();
						break;
					}
					else {
						id = r.getDb()+"_"+r.getId();
					}
				}
			}
		}else if(entityStrategy.equals(EntityStrategy.YeastCyc)) {
			for(Xref ref : references) {
				if(ref.getModelInterface().equals(UnificationXref.class)) {
					UnificationXref r = (UnificationXref)ref;	    			
					if(bp_entity instanceof SmallMolecule) {
						if(r.getDb().equalsIgnoreCase("ChEBI")) {
							id = r.getId().replace(":", "_");
							break;
						}
					}else {
						if(r.getDb().equalsIgnoreCase("YeastCyc")) {
							id = r.getId();
							break;
						}
					}
				}
			}
		}
		return id;
	}

	/**
	 * Only keep it if it has some useful content
	 * @param pathway
	 * @return
	 */
	boolean keepPathway(Pathway pathway) {
		boolean keep = false;
		if(base_provider.equals("https://reactome.org")) {
			//default to keeping all reactome content
			keep = true;
			//but ignore disease pathways
			if(ignore_diseases) {
				Set<Pathway> parents = getPathwayParents(pathway, null);
				for(Pathway parent : parents) {
					if(parent.getDisplayName().equals("Disease")) {
						keep = false;
						break;
					}
				}
			}
		}else {
			Set<Process> processes = pathway.getPathwayComponent();
			if(processes!=null&&processes.size()>1) {
				keep = true;
			}
		}
		return keep;
	}

	boolean keepReaction(Interaction reaction) {
		boolean keep = true;
		if(ignore_diseases) {
			//is all the pathways the reaction is in are disease pathways, 
			//don't keep it			
			boolean all_disease = false;
			for(Pathway pathway : reaction.getPathwayComponentOf()) {
				if(keepPathway(pathway)) {
					return true; //found a non-disease parent pathway, stop
				}else {
					all_disease = true;
				}
			}
			if(all_disease) {
				keep = false;
			}
		}
		return keep;
	}

	Set<Pathway> getPathwayParents(Pathway pathway, Set<Pathway> parents){
		if(parents==null) {
			parents = new HashSet<Pathway>();
			parents.add(pathway);
		}
		for(Pathway parent_pathway : pathway.getPathwayComponentOf()) {
			parents.add(parent_pathway);
			parents.addAll(getPathwayParents(parent_pathway, parents));
		}
		return parents;
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
	private void wrapAndWrite(String outfilename, GoCAM go_cam, boolean save_inferences, boolean save2blazegraph, String pathwayname, boolean expand_subpathways, String reactome_id) throws OWLOntologyCreationException, OWLOntologyStorageException, RepositoryException, RDFParseException, RDFHandlerException, IOException {		
		//set up a sparqlable kb in sync with ontology
		go_cam.qrunner = new QRunner(go_cam.go_cam_ont); 
		//filter out reactions involving drugs
		if(drop_drug_reactions&&drug_process_ids!=null&&drug_process_ids.size()>0) {
			go_cam.removeDrugReactions(reactome_id, drug_process_ids); 
		}
		//delete any stray drug individuals
		if(drop_drug_reactions) {
			go_cam.removeDrugs(tbox_qrunner); 
		}
		//infer new edges based on sparql matching
		System.out.println("Before sparql inference -  triples: "+go_cam.qrunner.nTriples());
		GoCAM.RuleResults rule_results = go_cam.applySparqlRules(reactome_id, tbox_qrunner);
		System.out.println("After sparql inference -  triples: "+go_cam.qrunner.nTriples());
		System.out.println("Rule results:\n"+rule_results.toString());
		//sparql rules make additions to go_cam_ont, add them to the rdf model 
		//set up to apply OWL inference to test for consistency and add classifications
		//go_cam.go_cam_ont is ready and equals the Abox.. go_cam.qrunner also ready

		WorkingMemory wm_with_tbox = null;
		if(generate_report||apply_layout) {
			wm_with_tbox = tbox_qrunner.arachne.createInferredModel(go_cam.go_cam_ont,false, false);	
		}
		if(generate_report) {
			System.out.println("Report after local rules");
			GoCAMReport gocam_report_after_rules = new GoCAMReport(wm_with_tbox, outfilename, go_cam, golego.golego_ont);
			ReasonerReport reasoner_report = new ReasonerReport(gocam_report_after_rules);
			report.pathway_class_report.put(pathwayname, reasoner_report);
		}

		if(apply_layout) {
			//adds coordinates to go_cam_ont model 
			SemanticNoctuaLayout layout = new SemanticNoctuaLayout();
			go_cam = layout.layout(wm_with_tbox, go_cam);	
			//add them into the rdf 
			go_cam.qrunner = new QRunner(go_cam.go_cam_ont); 
		}

		System.out.println("writing....");
		go_cam.writeGoCAM_jena(outfilename, save2blazegraph);
		System.out.println("done writing...");
		//checks for inferred things with rdf:type OWL:Nothing with a sparql query
		if(check_consistency) {
			if(wm_with_tbox==null) {
				wm_with_tbox = tbox_qrunner.arachne.createInferredModel(go_cam.go_cam_ont,false,false);
				go_cam.qrunner.jena = go_cam.qrunner.makeJenaModel(wm_with_tbox);
			}
			boolean is_logical = go_cam.validateGoCAM();	
			if(!is_logical) {
				report.inconsistent_models.add(outfilename);
				if(explain_inconsistant_models) {
					scala.collection.Iterator<Triple> triples = wm_with_tbox.facts().toList().iterator();
					while(triples.hasNext()) {				
						Triple triple = triples.next();
						if(wm_with_tbox.asserted().contains(triple)) {
							continue;
						}else { //<http://arachne.geneontology.org/indirect_type>
							if(triple.p().toString().equals("<http://www.w3.org/1999/02/22-rdf-syntax-ns#type>")&&
									triple.o().toString().equals("<http://www.w3.org/2002/07/owl#Nothing>")) {
								String bad_uri = triple.s().toString().replaceAll(">", "").replaceAll("<", "");
								System.out.println("inferred inconsistent:"+triple.s()+" "+Helper.getaLabel(bad_uri, go_cam.go_cam_ont));
								scala.collection.immutable.Set<Explanation> explanations = wm_with_tbox.explain(triple);
								scala.collection.Iterator<Explanation> e = explanations.iterator();
								while(e.hasNext()) {
									Explanation exp = e.next();
									String exp_string = renderExplanation(exp, go_cam, golego.golego_ont);
									System.out.println(exp_string);
									System.out.println();
								}
							}
						}
					}
				}
				System.out.println("Illogical go_cam..  stopping");
				System.exit(0);
			}
		}
	}

	public static String renderExplanation(Explanation exp, GoCAM go_cam, OWLOntology tbox) {
		String exp_string = "Explanation!:\n";
		scala.collection.Iterator<Rule> rule_it = exp.rules().iterator();
		while(rule_it.hasNext()) {
			Rule r = rule_it.next();
			scala.collection.Iterator<TriplePattern> rule_body_it = r.body().iterator();
			exp_string = exp_string+"IF\n";
			while(rule_body_it.hasNext()) {
				TriplePattern tp = rule_body_it.next();
				Node subject = tp.s();
				Node predicate = tp.p();
				Node object = tp.o();
				exp_string = exp_string+labelifyTripleNode(subject, go_cam, tbox)+"\t"+labelifyTripleNode(predicate, go_cam, tbox)+"\t"+labelifyTripleNode(object, go_cam, tbox)+"\n";
			}
			exp_string = exp_string+"THEN\n";
			scala.collection.Iterator<TriplePattern> rule_head_it = r.head().iterator();
			while(rule_head_it.hasNext()) {
				TriplePattern tp = rule_head_it.next();
				Node subject = tp.s();
				Node predicate = tp.p();
				Node object = tp.o();
				exp_string = exp_string+"\t"+labelifyTripleNode(subject, go_cam, tbox)+"\t"+labelifyTripleNode(predicate, go_cam, tbox)+"\t"+labelifyTripleNode(object, go_cam, tbox)+"\n";
			}
		}	
		scala.collection.Iterator<Triple> fact_it = exp.facts().iterator();
		exp_string = exp_string+"FACTS\n";
		while(fact_it.hasNext()) {
			Triple tp = fact_it.next();
			Node subject = tp.s();
			Node predicate = tp.p();
			Node object = tp.o();
			exp_string = exp_string+"\t"+labelifyTripleNode(subject, go_cam, tbox)+"\t"+labelifyTripleNode(predicate, go_cam, tbox)+"\t"+labelifyTripleNode(object, go_cam, tbox)+"\n";
		}
		return exp_string;
	}

	public static String labelifyTripleNode(Node node, GoCAM go_cam, OWLOntology tbox) {
		String n = node.toString(); //will either be a uri or a variable like ?x
		n = n.replace("<", "");
		n = n.replace(">", "");
		if(!n.startsWith("?")) {
			//<http://www.w3.org/1999/02/22-rdf-syntax-ns#type>
			if(n.contains("#")) {
				n = n.substring(n.indexOf("#"));
				//http://model.geneontology.org/R-HSA-3214847/R-HSA-3301345	
			}else if(n.startsWith("http://purl.obolibrary.org/obo/")) {
				String label = Helper.getaLabel(n, tbox);
				if(label!=null) {
					n = label;
				}else {
					label = Helper.getaLabel(n, tbox);
					if(label!=null) {
						n = label;
					}else {
						n = n.substring(31);//trim off obo
					}
				}
			}else if(n.startsWith(GoCAM.base_iri)) {
				String label = Helper.getaLabel(n, go_cam.go_cam_ont);
				if(label!=null) {
					n = label;
				}else {
					label = Helper.getaLabel(n, tbox);
					if(label!=null) {
						n = label;
					}
				}
				//<http://purl.obolibrary.org/obo/BFO_0000066>
			}
		}
		return n;
	}

	private OWLNamedIndividual definePathwayEntity(GoCAM go_cam, Pathway pathway, String model_id, boolean expand_subpathways, boolean add_components) throws IOException {
		IRI pathway_iri = GoCAM.makeGoCamifiedIRI(model_id, model_id);
		System.out.println("defining pathway "+getBioPaxName(pathway)+" "+expand_subpathways+" "+add_components+" "+model_id);
		OWLNamedIndividual pathway_e = go_cam.makeAnnotatedIndividual(pathway_iri);
		go_cam.addLabel(pathway_e, getBioPaxName(pathway));
		go_cam.addDatabaseXref(pathway_e, model_id);
		go_cam.addSkosNote(pathway_e, pathway.getModelInterface().getCanonicalName());
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
		//Set<String> pubids = getPubmedIds(pathway);
		//annotations and go
		Set<Xref> xrefs = pathway.getXref();	
		Set<String> mappedgo = report.bp2go_bp.get(pathway);
		if(mappedgo==null) {
			mappedgo = new HashSet<String>();
		}
		for(Xref xref : xrefs) {
			//dig out any xreferenced GO processes and assign them as types
			if(xref.getModelInterface().equals(RelationshipXref.class)) {
				RelationshipXref r = (RelationshipXref)xref;	    			
				//System.out.println(xref.getDb()+" "+xref.getId()+" "+xref.getUri()+"----"+r.getRelationshipType());
				//note that relationship types are not defined beyond text strings like RelationshipTypeVocabulary_gene ontology term for cellular process
				//you just have to know what to do.
				//here we add the referenced GO class as a type.  
				String db = r.getDb().toLowerCase();
				if(db.contains("gene ontology")) {
					String goid = r.getId().replaceAll(":", "_");
					//OWLClass xref_go_parent = go_cam.df.getOWLClass(IRI.create(GoCAM.obo_iri + goid));
					String uri = GoCAM.obo_iri + goid;					
					OWLClass xref_go_parent = golego.getOboClass(uri, true);
					boolean deprecated = golego.isDeprecated(uri);
					if(deprecated) {
						report.deprecated_classes.add(getBioPaxName(pathway)+"\t"+uri+"\tBP");
					}					
					go_cam.addTypeAssertion(pathway_e, xref_go_parent);
					//record mappings
					mappedgo.add(goid);
				}
			}
		}
		//store mappings
		report.bp2go_bp.put(pathway, mappedgo);

		if(add_components) {
			//different pathway - bridging relation.
			if(add_subpathway_bridges){
				for(Process process : pathway.getPathwayComponentOf()) {
					//add pathway part of pathway relations
					//mainly for global, sparql view of data
					if(process instanceof Pathway) {
						String super_pathway_id = getEntityReferenceId(process);
						IRI super_pathway_iri = null;
						if(super_pathway_id!=null) {
							super_pathway_iri = GoCAM.makeGoCamifiedIRI(super_pathway_id, super_pathway_id);
						}else {
							super_pathway_iri = GoCAM.makeARandomIri(model_id);
						}
						//don't use part_of as reactome uses pathway hierarchy for general grouping, isa, and part of relations.
						go_cam.addUriAnnotations2Individual(pathway_e.getIRI(), GoCAM.skos_narrower, super_pathway_iri);
					}
				}
			} 
			//define the pieces of the pathway
			//Process subsumes Pathway and Interaction (which is usually a reaction).  
			//A pathway may have either or both reaction or pathway components.  
			for(Process process : pathway.getPathwayComponent()) {
				//Conversion subsumes BiochemicalReaction, TransportWithBiochemicalReaction, ComplexAssembly, Degradation, GeneticInteraction, MolecularInteraction, TemplateReaction
				//though the great majority are BiochemicalReaction
				//don't add Control entities (found that in Kegg Biotin pathway from PC)
				if(process instanceof Control) {
					continue;
				}
				String process_id = getEntityReferenceId(process);
				IRI process_iri = null;
				//IRI process_iri = GoCAM.makeGoCamifiedIRI(model_id, process_id);
				//we assume here that process_id is both available and unique
				//in non-reactome contexts this may need some revision.  
				if(process_id!=null) {
					process_iri = GoCAM.makeGoCamifiedIRI(null, process_id);
				}else {
					process_iri = GoCAM.makeARandomIri(model_id);
				}
				OWLNamedIndividual child = go_cam.df.getOWLNamedIndividual(process_iri);
				//attach reactions that make up the pathway
				if(process instanceof Conversion 
						|| process instanceof TemplateReaction
						|| process instanceof GeneticInteraction 
						|| process instanceof MolecularInteraction 
						|| process instanceof Interaction){					
					go_cam.addRefBackedObjectPropertyAssertion(child, GoCAM.part_of, pathway_e, Collections.singleton(model_id), GoCAM.eco_imported_auto, default_namespace_prefix, null, model_id);
					defineReactionEntity(go_cam, process, process_iri, false, model_id, pathway_iri.toString());	
					//attach child pathways
				}
				else if(process.getModelInterface().equals(Pathway.class)){
					String child_model_id = getEntityReferenceId(process);
					IRI child_pathway_iri = GoCAM.makeGoCamifiedIRI(null, child_model_id);
					if(expand_subpathways) {
						//if we are not doing the bridging method but we are including subpathways, add them on here. 
						OWLNamedIndividual child_pathway = go_cam.makeAnnotatedIndividual(child_pathway_iri);
						go_cam.addRefBackedObjectPropertyAssertion(child_pathway, GoCAM.part_of, pathway_e, Collections.singleton(model_id), GoCAM.eco_imported_auto, default_namespace_prefix, null, model_id);
						String sub_pathway_id = getEntityReferenceId(process);
						definePathwayEntity(go_cam, (Pathway)process, sub_pathway_id, expand_subpathways, add_components);
					}

				}
				else {
					System.out.println("Unknown Process !"+getBioPaxName(process));
					System.out.println("Process URI.. "+process.getUri());			
					System.out.println("Process model interface.. "+process.getModelInterface());	
					System.exit(0);
				}
			}
			//reaction -> reaction connections
			//looks within the current pathway and one level out - e.g. the connections in and out to other places
			//does not follow them.  
			if(!causal_recurse) { //else this is going to be handled recursively in the reaction definition function
				Set<PathwayStep> steps = pathway.getPathwayOrder();
				for(PathwayStep step1 : steps) {
					Set<Process> events = step1.getStepProcess();
					Set<PathwayStep> previousSteps = step1.getNextStepOf();
					//adding in previous step (which may be from a different pathway)
					for(PathwayStep prevStep : previousSteps) {
						Set<Process> prevEvents = prevStep.getStepProcess();
						for(Process event : events) {
							String event_id = getEntityReferenceId(event);
							for(Process prevEvent : prevEvents) {
								//limit to relations between conversions - was biochemical reactions but see no reason 
								//not to extend this to include e.g. degradation
								if((event instanceof Interaction)&&(prevEvent instanceof Interaction)&&
										!(event instanceof Control)&&!(prevEvent instanceof Control)) {
									Set<Pathway> event_pathways = event.getPathwayComponentOf();
									Set<Pathway> prev_event_pathways = prevEvent.getPathwayComponentOf();
									String add_reaction = null;
									if((event_pathways.contains(pathway)&&prev_event_pathways.contains(pathway))) {
										add_reaction = "in_pathway";
									}else if(add_neighboring_events_from_other_pathways) {	
										//test if there is any reason to avoid this reaction
										//e.g. from a banned pathway.  
										if(keepReaction((Interaction)prevEvent)) {
											add_reaction = "external_pathway";
										}else {
											add_reaction = null;
										}
									}
									if(add_reaction !=null) {
										String prev_event_id = getEntityReferenceId(prevEvent);
										IRI event_iri = GoCAM.makeGoCamifiedIRI(null, event_id);
										IRI prevEvent_iri = null;
										OWLNamedIndividual e1 = null;
										//in some cases, the reaction may connect off to a different pathway and hence not be caught in above loop to define reaction entities
										//e.g. Recruitment of SET1 methyltransferase complex  -> APC promotes disassembly of beta-catenin transactivation complex
										//are connected yet in different pathways
										if(add_reaction.equals("external_pathway")) {
											String external_pathway_id = null;
											for(Pathway external : prevEvent.getPathwayComponentOf()) {
												external_pathway_id = getEntityReferenceId(external); 
												prevEvent_iri = GoCAM.makeGoCamifiedIRI(null, prev_event_id);
												e1 = go_cam.df.getOWLNamedIndividual(prevEvent_iri);
												go_cam.addComment(e1, "reaction from external pathway "+external_pathway_id);
												break;
											}
											defineReactionEntity(go_cam, prevEvent, prevEvent_iri, false, external_pathway_id, pathway_iri.toString());													
										}else {
											prevEvent_iri = GoCAM.makeGoCamifiedIRI(null, prev_event_id);
											e1 = go_cam.df.getOWLNamedIndividual(prevEvent_iri);
										}										
										OWLNamedIndividual e2 = go_cam.df.getOWLNamedIndividual(event_iri);
										go_cam.addRefBackedObjectPropertyAssertion(e1, GoCAM.causally_upstream_of, e2, Collections.singleton(model_id), GoCAM.eco_imported_auto, default_namespace_prefix, null, model_id);										
									}
								}
							} 
						}
					} 			
				}  
			}
		}
		Collection<OWLClassExpression> types = EntitySearcher.getTypes(pathway_e, go_cam.go_cam_ont);				
		if(types.isEmpty()) { 
			//check parent pathways for types
			if(add_bp_from_pathway_parents) {
				Set<OWLClass> gos = getClosestParentWithGO(pathway);
				if(gos!=null) {
					for(OWLClass go : gos) {
						go_cam.addTypeAssertion(pathway_e, go);
						//String golabel = tbox_qrunner.getLabel(go.getIRI().toString());
						String golabel = Helper.getaLabel(go, tbox_qrunner.tbox_class_reasoner.getRootOntology());
						System.out.println("inferred bp\t"+pathway.getDisplayName()+"\t"+model_id+"\t"+go+"\t"+golabel);
					}
				}else {	//default to bp
					go_cam.addTypeAssertion(pathway_e, GoCAM.bp_class);	
				}
			}else {
				go_cam.addTypeAssertion(pathway_e, GoCAM.bp_class);	
			}
		}
		return pathway_e;
	}

	private Set<OWLClass> getClosestParentWithGO(Pathway p){
		Set<OWLClass> gos = getGOFromRelationXref(p);
		if(gos.size()>0) {
			return gos;
		}else {
			for(Pathway parent : p.getPathwayComponentOf()) {
				gos = getClosestParentWithGO(parent);
				if(gos!=null&&gos.size()>0) {
					return gos;
				}
			}
		}
		return null;
	}

	private Set<OWLClass> getGOFromRelationXref(Entity e) {
		Set<OWLClass> go = new HashSet<OWLClass>();
		Set<Xref> xrefs = e.getXref();	
		for(Xref xref : xrefs) {
			//dig out any xreferenced GO processes and assign them as types
			if(xref.getModelInterface().equals(RelationshipXref.class)) {
				RelationshipXref r = (RelationshipXref)xref;	    			  
				String db = r.getDb().toLowerCase();
				if(db.contains("gene ontology")) {
					String goid = r.getId().replaceAll(":", "_");
					String uri = GoCAM.obo_iri + goid;					
					OWLClass goc = golego.getOboClass(uri, true);
					go.add(goc);
				}
			}
		}
		return go;
	}


	private String getUniprotProteinId(Protein protein) {
		String id = null;
		EntityReference entity_ref = protein.getEntityReference();	
		if(entity_ref!=null) {
			Set<Xref> p_xrefs = entity_ref.getXref();				
			for(Xref xref : p_xrefs) {
				if(xref.getModelInterface().equals(UnificationXref.class)) {
					UnificationXref uref = (UnificationXref)xref;
					String db = uref.getDb();
					db = db.toLowerCase();
					// #BioPAX4
					//Reactome uses 'UniProt', Pathway Commons uses 'uniprot knowledgebase'
					//WikiPathways often uses UniProtKB
					//fun fun fun !
					//How about URI here, please..?
					if(db.contains("uniprot")) {
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
	 * 	//Done: Complex, Protein, SmallMolecule, Dna, Processes 
		//TODO DnaRegion, RnaRegion
	 * @param ontman
	 * @param go_cam_ont
	 * @param df
	 * @param entity
	 * @return
	 * @throws IOException 
	 */
	private void defineReactionEntity(GoCAM go_cam, Entity entity, IRI this_iri, boolean follow_controllers, String model_id, String root_pathway_iri) throws IOException {				
		String entity_id = getEntityReferenceId(entity);
		if(this_iri==null) {
			if(entity_id!=null) {
				this_iri = GoCAM.makeGoCamifiedIRI(null, entity_id);
			}else {
				this_iri = GoCAM.makeARandomIri(model_id);
			}
		}
		Set<String> dbids = new HashSet<String>();
		dbids.add(model_id);
		//add entity to ontology, whatever it is
		OWLNamedIndividual e = go_cam.makeAnnotatedIndividual(this_iri);
		go_cam.addSkosNote(e, entity.getModelInterface().getCanonicalName());
		//check specifically for Reactome id
		String reactome_entity_id = getEntityReferenceId(entity);
		//add xrefs
		for(Xref xref : entity.getXref()) {
			if(xref.getModelInterface().equals(UnificationXref.class)) {
				UnificationXref r = (UnificationXref)xref;	    			
				if(r.getDb().equals("Reactome")) {
					go_cam.addDatabaseXref(e, "Reactome:"+r.getId());
					dbids.add(reactome_entity_id);
				}
			}
		}		
		//this allows linkage between different OWL individuals in the GO-CAM sense that correspond to the same thing in the BioPax sense
		go_cam.addUriAnnotations2Individual(e.getIRI(),GoCAM.skos_exact_match, IRI.create(entity.getUri()));	
		//check for annotations
		//	Set<String> pubids = getPubmedIds(entity);		
		String entity_name = getBioPaxName(entity);
		if(entity_name!=null) {
			go_cam.addLabel(e, entity_name);
		}
		if(entity instanceof PhysicalEntity) {
			IRI entity_class_iri = getPhysicalEntityIRI(entity);
			OWLClass entity_class = go_cam.df.getOWLClass(entity_class_iri); 
			go_cam.addTypeAssertion(e,  entity_class);

			Set<String> drug_ids = Helper.getAnnotations(entity_class, tbox_qrunner.tbox_class_reasoner.getRootOntology(), GoCAM.iuphar_id);
			if(drug_ids!=null&&drug_ids.size()>0) {
				Set<Interaction> entity_processes = entity.getParticipantOf();
				for(Interaction process : entity_processes) {
					if(process instanceof Conversion) {
						String process_id = getEntityReferenceId(process);
						if(process_id!=null) {
							drug_process_ids.add(process_id);
						}
					}
				}
			}

			//attempt to localize the entity (only if Physical Entity because that is how BioPAX views existence in space)
			CellularLocationVocabulary loc = ((PhysicalEntity) entity).getCellularLocation();
			if(loc!=null) {			
				//dig out the GO cellular location and create an individual for it
				String location_term = null;
				Set<Xref> xrefs = loc.getXref();
				for(Xref xref : xrefs) {
					if(xref.getModelInterface().equals(UnificationXref.class)) {
						UnificationXref uref = (UnificationXref)xref;	    			
						//here we add the referenced GO class as a type.  
						String db = uref.getDb().toLowerCase();
						if(db.contains("gene ontology")) {
							String uri = GoCAM.obo_iri + uref.getId().replaceAll(":", "_");						
							OWLClass xref_go_loc = golego.getOboClass(uri, true);
							boolean deprecated = golego.isDeprecated(uri);
							if(deprecated) {
								report.deprecated_classes.add(getBioPaxName(entity)+"\t"+xref_go_loc.getIRI().toString()+"\tCC");
							}
							Set<XReferrable> refs = uref.getXrefOf();							
							for(XReferrable ref : refs) {
								location_term = ref.toString().replaceAll("CellularLocationVocabulary_", "");
								break;
							}
							if(location_term!=null) {
								String loc_i_id = getEntityReferenceId(entity)+"_"+uref.getId().replaceAll(":", "_");
								OWLNamedIndividual loc_e = go_cam.makeAnnotatedIndividual(GoCAM.makeGoCamifiedIRIstring(null, loc_i_id));
								go_cam.addTypeAssertion(loc_e, xref_go_loc);
								go_cam.addRefBackedObjectPropertyAssertion(e, GoCAM.located_in, loc_e, dbids, GoCAM.eco_imported_auto, default_namespace_prefix, null, model_id);		
								if(strategy == ImportStrategy.NoctuaCuration) {
									go_cam.addLiteralAnnotations2Individual(e.getIRI(), GoCAM.rdfs_comment, "located_in "+location_term);
								}
								break; //there can be only one 
							}
						}
					}
				}
			}
		}//end physical thing
		//Interaction subsumes Conversion, GeneticInteraction, MolecularInteraction, TemplateReaction
		//Conversion subsumes BiochemicalReaction, TransportWithBiochemicalReaction, ComplexAssembly, Degradation, GeneticInteraction, MolecularInteraction, TemplateReaction
		//though the great majority are BiochemicalReaction
		else if (entity instanceof Interaction){  		
			//build up causal relations between reactions from steps in the pathway
			if(causal_recurse) {
				Set<PathwayStep> steps = ((Interaction) entity).getStepProcessOf();
				for(PathwayStep thisStep :steps) {
					Set<Process> events = thisStep.getStepProcess();
					Set<PathwayStep> nextSteps = thisStep.getNextStep();
					Set<PathwayStep> previousSteps = thisStep.getNextStepOf();
					for(PathwayStep nextStep : nextSteps) {
						Set<Process> nextEvents = nextStep.getStepProcess();
						for(Process event : events) {
							String event_id = getEntityReferenceId(event);
							for(Process nextEvent : nextEvents) {
								//	Event causally_upstream_of NextEvent
								if((event.getModelInterface().equals(BiochemicalReaction.class))&&
										(nextEvent.getModelInterface().equals(BiochemicalReaction.class))) {
									String next_event_id = getEntityReferenceId(nextEvent);
									IRI e1_iri = GoCAM.makeGoCamifiedIRI(null, event_id);
									IRI e2_iri = GoCAM.makeGoCamifiedIRI(null, next_event_id);
									OWLNamedIndividual e1 = go_cam.df.getOWLNamedIndividual(e1_iri);
									OWLNamedIndividual e2 = go_cam.df.getOWLNamedIndividual(e2_iri);
									go_cam.addRefBackedObjectPropertyAssertion(e1, GoCAM.causally_upstream_of, e2, dbids, GoCAM.eco_imported_auto, default_namespace_prefix, null, model_id);
									//in some cases, the reaction may connect off to a different pathway and hence not be caught in above loop to define reaction entities
									//e.g. Recruitment of SET1 methyltransferase complex  -> APC promotes disassembly of beta-catenin transactivation complex
									//are connected yet in different pathways
									//if its been defined, ought to at least have a label
									if(go_cam.getaLabel(e2).equals("")){
										defineReactionEntity(go_cam, nextEvent, e2_iri, true, model_id, root_pathway_iri);		
									}
								}
							}
						}
					}
					//adding in previous step (which may be from a different pathway)
					for(PathwayStep prevStep : previousSteps) {
						Set<Process> prevEvents = prevStep.getStepProcess();
						for(Process event : events) {
							String event_id = getEntityReferenceId(event);
							for(Process prevEvent : prevEvents) {
								if(add_neighboring_events_from_other_pathways) {								
									if((event.getModelInterface().equals(BiochemicalReaction.class))&&
											(prevEvent.getModelInterface().equals(BiochemicalReaction.class))) {
										String prev_event_id = getEntityReferenceId(prevEvent);
										IRI event_iri = GoCAM.makeGoCamifiedIRI(null, event_id);
										IRI prevEvent_iri = GoCAM.makeGoCamifiedIRI(null, prev_event_id);
										OWLNamedIndividual e1 = go_cam.df.getOWLNamedIndividual(prevEvent_iri);
										OWLNamedIndividual e2 = go_cam.df.getOWLNamedIndividual(event_iri);
										go_cam.addRefBackedObjectPropertyAssertion(e1, GoCAM.causally_upstream_of, e2, dbids, GoCAM.eco_imported_auto, default_namespace_prefix, null, model_id);
										if(go_cam.getaLabel(e1).equals("")){
											defineReactionEntity(go_cam, prevEvent, prevEvent_iri, true, model_id, root_pathway_iri);		
										}
									}
								}
							}
						} 
					} 		
				}
			}

			if(entity.getModelInterface().equals(Interaction.class)) {
				//this happens a lot in WikiPathways, though is not considered good practice
				//should use a more specific class if possible.  
				Set<Entity> interactors = ((Interaction) entity).getParticipant();
				Set<OWLNamedIndividual> physical_participants = new HashSet<OWLNamedIndividual>();
				Set<OWLNamedIndividual> process_participants = new HashSet<OWLNamedIndividual>();
				for(Entity interactor : interactors) {				
					if(interactor instanceof PhysicalEntity) {
						IRI i_iri = GoCAM.makeGoCamifiedIRI(null, getEntityReferenceId(entity)+"_interactor_"+getEntityReferenceId(interactor));
						OWLNamedIndividual i_entity = go_cam.df.getOWLNamedIndividual(i_iri);
						defineReactionEntity(go_cam, interactor, i_iri, true, model_id, root_pathway_iri);		
						go_cam.addRefBackedObjectPropertyAssertion(e, GoCAM.has_participant, i_entity, dbids, GoCAM.eco_imported_auto,  default_namespace_prefix,go_cam.getDefaultAnnotations(), model_id);
						physical_participants.add(i_entity);
					}else {
						String interactor_id = getEntityReferenceId(interactor);
						OWLNamedIndividual part_mf = go_cam.df.getOWLNamedIndividual(GoCAM.makeGoCamifiedIRI(null, interactor_id));
						defineReactionEntity(go_cam, interactor, part_mf.getIRI(), true, model_id, root_pathway_iri);
						go_cam.addRefBackedObjectPropertyAssertion(part_mf, GoCAM.part_of, e, dbids, GoCAM.eco_imported_auto,  default_namespace_prefix, go_cam.getDefaultAnnotations(), model_id);
						process_participants.add(part_mf);
					}					
				}
				for(OWLNamedIndividual p1 : physical_participants) {
					for(OWLNamedIndividual p2 : physical_participants) {
						if(!p1.equals(p2)) {
							go_cam.addRefBackedObjectPropertyAssertion(p1, GoCAM.interacts_with, p2, dbids, GoCAM.eco_imported_auto,  default_namespace_prefix, go_cam.getDefaultAnnotations(), model_id);
						}
					}
				}
				for(OWLNamedIndividual p1 : process_participants) {
					for(OWLNamedIndividual p2 : process_participants) {
						if(!p1.equals(p2)) {
							go_cam.addRefBackedObjectPropertyAssertion(p1, GoCAM.functionally_related_to, p2, dbids, GoCAM.eco_imported_auto,  default_namespace_prefix, go_cam.getDefaultAnnotations(), model_id);
						}
					}
				}
				for(OWLNamedIndividual p1 : physical_participants) {
					for(OWLNamedIndividual p2 : process_participants) {
						if(!p1.equals(p2)) {
							go_cam.addRefBackedObjectPropertyAssertion(p2, GoCAM.enabled_by, p1, dbids, GoCAM.eco_imported_auto,  default_namespace_prefix, go_cam.getDefaultAnnotations(), model_id);
						}
					}
				}
			}
			if (entity instanceof TemplateReaction) {
				Set<PhysicalEntity> products = ((TemplateReaction) entity).getProduct();
				for(PhysicalEntity output : products) {
					String output_id = getEntityReferenceId(output);
					IRI o_iri = GoCAM.makeGoCamifiedIRI(null, output_id+"_"+entity_id);
					OWLNamedIndividual output_entity = go_cam.df.getOWLNamedIndividual(o_iri);
					defineReactionEntity(go_cam, output, o_iri, true, model_id, root_pathway_iri);
					go_cam.addRefBackedObjectPropertyAssertion(e, GoCAM.has_output, output_entity, dbids, GoCAM.eco_imported_auto,  default_namespace_prefix, go_cam.getDefaultAnnotations(), model_id);
				}
				//not used ?
				//NucleicAcid nuc = ((TemplateReaction) entity).getTemplate();
				//TemplateDirectionType tempdirtype = ((TemplateReaction) entity).getTemplateDirection();
			}

			//link to participants in reaction
			if(entity instanceof Conversion) {
				//make sure there is a connection to the parent pathway
				//TODO maybe add a query here to limit redundant loops through definePathway
				if(add_pathway_parents) {
					for(Pathway bp_pathway : ((Interaction) entity).getPathwayComponentOf()) {
						OWLNamedIndividual pathway = definePathwayEntity(go_cam, bp_pathway, null, false, false);
						go_cam.addRefBackedObjectPropertyAssertion(e,GoCAM.part_of, pathway,dbids, GoCAM.eco_imported_auto,  default_namespace_prefix, null, model_id);
					}
				}

				ConversionDirectionType direction = ((Conversion) entity).getConversionDirection();
				if(direction==null&&(entity instanceof Degradation)) {
					direction = ConversionDirectionType.LEFT_TO_RIGHT;
				}

				Set<PhysicalEntity> inputs = null;
				Set<PhysicalEntity> outputs = null;

				if(direction==null||direction.equals(ConversionDirectionType.LEFT_TO_RIGHT)||direction.equals(ConversionDirectionType.REVERSIBLE)) {
					inputs = ((Conversion) entity).getLeft();
					outputs = ((Conversion) entity).getRight();
					//http://apps.pathwaycommons.org/view?uri=http%3A%2F%2Fidentifiers.org%2Fkegg.pathway%2Fhsa00780 
					//todo..
					if(direction!=null&&direction.equals(ConversionDirectionType.REVERSIBLE)){
						System.out.println("REVERSIBLE reaction found!  Defaulting to assumption of left to right "+getBioPaxName(entity)+" "+entity.getUri());
					}
				}else if(direction.equals(ConversionDirectionType.RIGHT_TO_LEFT)) {
					outputs = ((Conversion) entity).getLeft();
					inputs = ((Conversion) entity).getRight();
					System.out.println("Right to left reaction found!  "+getBioPaxName(entity)+" "+entity.getUri());
				}else  {
					System.out.println("Reaction direction "+direction+" unknown");
					System.exit(0);
				}

				if(inputs!=null) {
					for(PhysicalEntity input : inputs) {
						IRI i_iri = null;
						String input_id = getEntityReferenceId(input);
						if(input_id==null){ //failed to find a chebi reference
							input_id = UUID.randomUUID().toString();
						}
						i_iri = GoCAM.makeGoCamifiedIRI(null, input_id+"_"+entity_id);
						OWLNamedIndividual input_entity = go_cam.df.getOWLNamedIndividual(i_iri);
						defineReactionEntity(go_cam, input, i_iri, true, model_id, root_pathway_iri);
						go_cam.addRefBackedObjectPropertyAssertion(e, GoCAM.has_input, input_entity,dbids, GoCAM.eco_imported_auto,  default_namespace_prefix, go_cam.getDefaultAnnotations(), model_id);
					}}
				if(outputs!=null) {
					for(PhysicalEntity output : outputs) {
						IRI o_iri = null;
						String output_id = getEntityReferenceId(output);
						if(output_id==null) {
							output_id = UUID.randomUUID().toString();
						}
						o_iri = GoCAM.makeGoCamifiedIRI(null, output_id+"_"+entity_id);
						OWLNamedIndividual output_entity = go_cam.df.getOWLNamedIndividual(o_iri);
						defineReactionEntity(go_cam, output, o_iri, true, model_id, root_pathway_iri);
						go_cam.addRefBackedObjectPropertyAssertion(e, GoCAM.has_output, output_entity, dbids, GoCAM.eco_imported_auto,  default_namespace_prefix, go_cam.getDefaultAnnotations(), model_id);
					}}
			}

			if(entity instanceof Process) {				
				Set<String> go_mf = report.bp2go_mf.get(entity);
				if(go_mf==null) {
					go_mf = new HashSet<String>();
				}
				Set<String> go_bp = report.bp2go_bp.get(entity);
				if(go_bp==null) {
					go_bp = new HashSet<String>();
				}
				Set<String> control_type = report.bp2go_controller.get(entity);
				if(control_type==null) {
					control_type = new HashSet<String>();
				}
				//keep track of where the reaction we are talking about controlling is coming from
				Set<Pathway> current_pathways = ((Interaction) entity).getPathwayComponentOf();

				//find controllers 
				Set<Control> controllers = ((Process) entity).getControlledOf();
				for(Control controller : controllers) {
					Set<Controller> controller_entities = controller.getController();
					boolean skip_drug_controller = false;
					for(Controller controller_entity : controller_entities) {
						String controller_entity_id = getEntityReferenceId(controller_entity);
						IRI entity_class_iri = IRI.create(GoCAM.reacto_base_iri+controller_entity_id);
						OWLClass entity_class = go_cam.df.getOWLClass(entity_class_iri); 						
						Set<String> drug_ids = Helper.getAnnotations(entity_class, tbox_qrunner.tbox_class_reasoner.getRootOntology(), GoCAM.iuphar_id);
						if(drug_ids!=null&&drug_ids.size()>0) {
							skip_drug_controller = true;
							break;
						}
					}
					if(skip_drug_controller) {
						continue;
					}
					//check if there are active sites annotated on the controller.
					Set<String> active_site_stable_ids = getActiveSites(controller);
					ControlType ctype = controller.getControlType();	
					boolean is_catalysis = false;
					if(controller.getModelInterface().equals(Catalysis.class)) {
						is_catalysis = true;
						control_type.add("Catalysis");
					}else {
						control_type.add("Non-catalytic-"+ctype.toString());
					}
					//controller 'entities' from biopax may map onto functions from go_cam
					//check for reactome mappings
					//dig out the GO molecular function and create an individual for it
					Set<Xref> xrefs = controller.getXref(); //controller is either a 'control', 'catalysis', 'Modulation', or 'TemplateReactionRegulation'
					for(Xref xref : xrefs) {
						if(xref.getModelInterface().equals(RelationshipXref.class)) {
							RelationshipXref ref = (RelationshipXref)xref;	    			
							//here we add the referenced GO class as a type. 
							//#BioPAX4
							String db = ref.getDb().toLowerCase();
							if(db.contains("gene ontology")) {
								String goid = ref.getId().replaceAll(":", "_");
								String uri = GoCAM.obo_iri + goid;
								OWLClass xref_go_func = golego.getOboClass(uri, true);
								if(golego.isDeprecated(uri)) {
									report.deprecated_classes.add(getBioPaxName(entity)+"\t"+uri+"\tMF");
								}
								//add the go function class as a type for the reaction instance being controlled here
								go_cam.addTypeAssertion(e, xref_go_func);
								go_mf.add(goid);
							}
						}
					}	
					for(Controller controller_entity : controller_entities) {
						//if the controller is produced by a reaction in another pathway, then we may want to bring that reaction into this model
						//so we can see the causal relationships between it and the reaction we have here
						//only do this if its not a small molecule...  ADP etc. make this intractable
						//limit to proteins and complexes 
						if(add_upstream_controller_events_from_other_pathways&&!(controller_entity instanceof SmallMolecule)) {
							Set<Interaction> events_controller_is_in = controller_entity.getParticipantOf();
							events_controller_is_in.remove(controller); //we know that the current control event is covered
							//criteria for adding an event to this model, this way
							//it has the controller_entity as an output						
							Set<Interaction> events_to_add = new HashSet<Interaction>();
							boolean in_this_pathway =false;
							for(Interaction event : events_controller_is_in) {
								Set<Pathway> event_pathways = event.getPathwayComponentOf();
								event_pathways.retainAll(current_pathways);
								if(event_pathways.size()>0) {
									in_this_pathway = true;
									break;
								}
							}
							if(!in_this_pathway) {
								for(Interaction event : events_controller_is_in) {
									if(event instanceof Conversion) {
										//TODO making a directionality assumption here 
										Set<PhysicalEntity> outputs = ((Conversion) event).getRight();
										if(outputs.contains(controller_entity)) {									
											events_to_add.add(event);
										}
									}
								}	
							}
							for(Interaction event : events_to_add) {
								//then we should be in some different, yet related reaction 
								//- mainly looking for the one that produced the controller molecule
								String event_id = getEntityReferenceId(event);
								IRI event_iri = GoCAM.makeGoCamifiedIRI(null, event_id);
								if(go_cam.go_cam_ont.containsIndividualInSignature(event_iri)){
									//stop recursive loops
									continue;
								}else {
									//limit to reactions as mostly we are interested in upstream processes
									//that generate the inputs that control the current reaction
									if(event instanceof BiochemicalReaction && follow_controllers) {
										defineReactionEntity(go_cam, event, event_iri, false, model_id, root_pathway_iri);
									}
								}
							}
						}
						//this is the non-recursive part.. (and we usually aren't recursing anyway)
						IRI iri = null;
						String controller_entity_id = getEntityReferenceId(controller_entity);
						iri = GoCAM.makeGoCamifiedIRI(null, controller_entity_id+"_"+entity_id+"_controller");
						defineReactionEntity(go_cam, controller_entity, iri, true, model_id, root_pathway_iri);
						//the protein or complex
						OWLNamedIndividual controller_e = go_cam.df.getOWLNamedIndividual(iri);
						//the controlling physical entity enables that function/reaction
						//check if there is an activeUnit annotation (reactome only)
						//active site 
						Set<OWLNamedIndividual> active_units = null;
						if(active_site_stable_ids.size()>0) {	
							active_units = new HashSet<OWLNamedIndividual>();
							//create the active unit nodes. 
							for(String active_site_stable_id : active_site_stable_ids) {
								//get the class for the entity
								//if it is a physical entity, then we should already have created a class to describe it based on the unique id.  
								//TODO this needs some generalizing, but focusing on getting Reactome done right now.
								IRI entity_class_iri = IRI.create(GoCAM.reacto_base_iri+active_site_stable_id);
								OWLClass entity_class = go_cam.df.getOWLClass(entity_class_iri); 
								//make a new individual - hmm.. check for conflict
								String active_id = getEntityReferenceId(controller)+"_"+active_site_stable_id;
								IRI active_iri = GoCAM.makeGoCamifiedIRI(null, active_id);
								OWLNamedIndividual active_i = go_cam.makeAnnotatedIndividual(active_iri);
								go_cam.addTypeAssertion(active_i,  entity_class);
								go_cam.addComment(active_i, "Active unit in "+controller_entity_id);
								//per discussion in pathways2GO/issues/91 removing the connection to the complex
								go_cam.addRefBackedObjectPropertyAssertion(controller_e, GoCAM.has_part, active_i, dbids,  GoCAM.eco_imported_auto, default_namespace_prefix, null, model_id);
								active_units.add(active_i);
							}
						}
						//define relationship between controller entity and reaction

						//per discussion in pathways2GO/issues/91 removing the connection to the and the complex individual
						//if(active_units!=null) {
						//	go_cam.deleteOwlEntityAndAllReferencesToIt(controller_e);
						//}
						//if catalysis then always enabled by
						if(is_catalysis) {
							//active unit known
							if(active_units!=null) {
								for(OWLNamedIndividual active_unit :active_units) {
									go_cam.addRefBackedObjectPropertyAssertion(e, GoCAM.enabled_by, active_unit, dbids, GoCAM.eco_imported_auto, default_namespace_prefix, null, model_id);	
									//make the complex itself a contributor
									//taking this out per https://github.com/geneontology/pathways2GO/issues/72
									//go_cam.addRefBackedObjectPropertyAssertion(controller_e, GoCAM.contributes_to, e, dbids, GoCAM.eco_imported_auto, default_namespace_prefix, null, model_id);	
								}
							}else {
								go_cam.addRefBackedObjectPropertyAssertion(e, GoCAM.enabled_by, controller_e, dbids, GoCAM.eco_imported_auto, default_namespace_prefix, null, model_id);	
							}
						}else {
							//otherwise look at text 
							//define how the molecular function (process) relates to the reaction (process)
							if(ctype.toString().startsWith("INHIBITION")){
								if(active_units!=null) {
									for(OWLNamedIndividual active_unit :active_units) {
										go_cam.addRefBackedObjectPropertyAssertion(active_unit, GoCAM.involved_in_negative_regulation_of, e, dbids, GoCAM.eco_imported_auto, default_namespace_prefix, null, model_id);	
									}
								}else {
									go_cam.addRefBackedObjectPropertyAssertion(controller_e, GoCAM.involved_in_negative_regulation_of, e, dbids, GoCAM.eco_imported_auto, default_namespace_prefix, null, model_id);	
								}
							}else if(ctype.toString().startsWith("ACTIVATION")){
								if(active_units!=null) {
									for(OWLNamedIndividual active_unit :active_units) {
										go_cam.addRefBackedObjectPropertyAssertion(active_unit, GoCAM.involved_in_positive_regulation_of, e, dbids, GoCAM.eco_imported_auto, default_namespace_prefix, null, model_id);
									}
								}else {
									go_cam.addRefBackedObjectPropertyAssertion(controller_e, GoCAM.involved_in_positive_regulation_of, e, dbids, GoCAM.eco_imported_auto, default_namespace_prefix, null, model_id);
								}
							}else {
								//default to regulates
								if(active_units!=null) {
									for(OWLNamedIndividual active_unit :active_units) {
										go_cam.addRefBackedObjectPropertyAssertion(active_unit, GoCAM.involved_in_regulation_of,  e, dbids, GoCAM.eco_imported_auto, default_namespace_prefix, null, model_id);
									}
								}else {
									go_cam.addRefBackedObjectPropertyAssertion(controller_e, GoCAM.involved_in_regulation_of,  e, dbids, GoCAM.eco_imported_auto, default_namespace_prefix, null, model_id);
								}
							}
						}
					}
				}
				//If a reaction is xreffed directly to the GO it is mapping to a biological process
				//this indicates the reaction is a part_of that process
				for(Xref xref : entity.getXref()) {
					if(xref.getModelInterface().equals(RelationshipXref.class)) {
						RelationshipXref ref = (RelationshipXref)xref;	    			
						//here we add the referenced GO class as a type.  
						//#BioPAX4
						String db = ref.getDb().toLowerCase();
						if(db.contains("gene ontology")) {
							String goid = ref.getId().replaceAll(":", "_");
							go_bp.add(goid);							
							String uri = GoCAM.obo_iri + goid;
							OWLClass xref_go_func = golego.getOboClass(uri, true);
							if(golego.isDeprecated(uri)) {
								report.deprecated_classes.add(getBioPaxName(entity)+"\t"+uri+"\tBP");
							}
							//the go class can not be a type for the reaction instance as we want to classify reactions as functions
							//and MF disjoint from BP
							//so make a new individual, hook it to that class, link to it via part of 
							OWLNamedIndividual bp_i = go_cam.makeAnnotatedIndividual(GoCAM.makeGoCamifiedIRI(model_id, entity_id+"_"+goid+"_individual"));
							go_cam.addLiteralAnnotations2Individual(bp_i.getIRI(), GoCAM.rdfs_comment, "Asserted direct link between reaction and biological process, independent of current pathway");
							go_cam.addTypeAssertion(bp_i, xref_go_func);
							go_cam.addRefBackedObjectPropertyAssertion(e,GoCAM.part_of, bp_i, dbids, GoCAM.eco_imported_auto, default_namespace_prefix, null, model_id);
							//use the same name and id as the entity in question as, from Reactome perspective, its about the same thing and otherwise we have no name..
							go_cam.addLabel(bp_i, "reaction:"+entity_name+": is xrefed to this process");
							if(reactome_entity_id!=null) {
								go_cam.addDatabaseXref(bp_i, reactome_entity_id);
							}
							//Per https://github.com/geneontology/pathways2GO/issues/66
							//remove the default part_of pathway relationship when one of these is added. 
							go_cam.applyAnnotatedTripleRemover(e.getIRI(), GoCAM.part_of.getIRI(), IRI.create(root_pathway_iri));
						}
					}
				}	
				//capture mappings for this reaction		
				report.bp2go_mf.put((Process)entity, go_mf);
				report.bp2go_bp.put((Process)entity, go_bp);
				report.bp2go_controller.put((Process)entity, control_type);

				//want to stay in go tbox as much as possible - even if defaulting to root nodes.  
				//if no process or function annotations, add annotation to root
				Collection<OWLClassExpression> types = EntitySearcher.getTypes(e, go_cam.go_cam_ont);				
				if(types.isEmpty()) { //go_mf.isEmpty()&&go_bp.isEmpty()
					//try mapping via xrefs
					boolean ecmapped = false;
					if(entity instanceof BiochemicalReaction) {
						for(OWLClass type :getTypesFromECs((BiochemicalReaction)entity, go_cam)) {
							go_cam.addTypeAssertion(e, type);	
							ecmapped = true;
						}
					}
					//default to mf
					if(!ecmapped) {
						//and now we aren't doing this again
						//try to infer protein binding or complex dissociation
						//						ComplexFunction b = checkForComplexFunction(e, go_cam);
						//						if(b.protein_complex_binding) {
						//							go_cam.addTypeAssertion(e, GoCAM.protein_complex_binding);	
						//						}else if(b.protein_binding) {
						//							go_cam.addTypeAssertion(e, GoCAM.protein_binding);	
						//						}else if(b.binding){
						//							go_cam.addTypeAssertion(e, GoCAM.binding);
						//						}
						//changing idea again here.  will handle these events downstream
						//						else if(b.dissociation) {
						//							go_cam.addTypeAssertion(e, GoCAM.protein_complex_dissassembly);
						//						}
						//						else {
						go_cam.addTypeAssertion(e, GoCAM.molecular_event);	
						//						}
					}
				}
				//The GO-CAM OWL for the reaction and all of its parts should now be assembled.  
				//Additional modifications to the output can come from secondary rules operating 
				//on the new OWL or its RDF representation
				//See GoCAM.applySparqlRules()

			}
		}
		return;
	}

	private IRI getPhysicalEntityIRI(Entity entity) {
		String entity_id = getEntityReferenceId(entity);
		if(entity_id!=null) {
			if(entityStrategy.equals(EntityStrategy.REACTO)) {
				//if it is a physical entity, then we should already have created a class to describe it based on the unique id.  
				//this will exist in the REACTO ontology
				IRI entity_class_iri = IRI.create(GoCAM.reacto_base_iri+entity_id);
				return entity_class_iri;
			}else if(entityStrategy.equals(EntityStrategy.YeastCyc)) {
				if(entity_id.startsWith("CHEBI")) {
					IRI entity_class_iri = IRI.create("http://purl.obolibrary.org/obo/"+entity_id);
					return entity_class_iri;
				}
				String neo_iri = accession_neo.get(entity_id);
				if(neo_iri==null) {
					String trimmed = entity_id.replace("-MONOMER", "");
					neo_iri = accession_neo.get(trimmed);
				}
				if(neo_iri==null) {
					String[] split = entity_id.split("-");
					neo_iri = accession_neo.get(split[0]);
				}
				if(neo_iri==null) {
					System.err.println("No neo access found for YeastCyc if "+entity_id);
				}else {
					return IRI.create(neo_iri);
				}
			}
		}
		System.err.println("no class found for physical entity "+entity);
		if(entity instanceof SmallMolecule) {
			//classify it as a chemical
			return IRI.create("http://purl.obolibrary.org/obo/CHEBI_24431");
		}else if(entity instanceof Complex) {
			return IRI.create("http://purl.obolibrary.org/obo/GO_0032991");
		}else {//infobiomacromolecule
			return IRI.create("http://purl.obolibrary.org/obo/CHEBI_33695");
		}
	}
	private Set<String> getExactMatches(OWLIndividual part, OWLOntology ont) {
		Set<String> matches = new HashSet<String>();
		Collection<OWLAnnotation> orig_ids = EntitySearcher.getAnnotationObjects((OWLEntity) part, ont, GoCAM.skos_exact_match);
		Iterator<OWLAnnotation> it = orig_ids.iterator();
		String orig_id = "";
		while(it.hasNext()) {
			OWLAnnotation anno = it.next();
			orig_id = anno.getValue().asIRI().get().toString();
			matches.add(orig_id);
		}
		return matches;
	}
	private Set<String> getActiveSites(Control controlled_by_complex) {
		Set<String> active_site_ids = new HashSet<String>();
		for(String comment : controlled_by_complex.getComment()) {
			if(comment.startsWith("activeUnit:")) {
				String[] c = comment.split(" ");
				String local_protein_id = c[1];
				//looks like #Protein3
				//active_site_ids.add(local_protein_id);
				//full id in biopax model
				String full_id = biopax_model.getXmlBase()+local_protein_id.substring(1);
				BioPAXElement bp_entity = biopax_model.getByID(full_id);
				String stable_id = getEntityReferenceId((Entity) bp_entity);
				active_site_ids.add(stable_id);
			}
		}
		return active_site_ids;

	}

	class ComplexFunction {
		boolean protein_complex_binding = false;
		boolean protein_binding = false;
		boolean binding = false;
		boolean dissociation = false;
	}

	/**
	 * Complex functions include protein binding, complex binding, and dissociation.
	 * These are not typically tagged in reactome.
	 * @param reaction
	 * @param go_cam
	 * @return
	 */
	private ComplexFunction checkForComplexFunction(OWLNamedIndividual reaction, GoCAM go_cam) {
		ComplexFunction binder = new ComplexFunction();
		//String r_label = go_cam.getaLabel(reaction);
		//collect inputs and outputs
		Collection<OWLIndividual> inputs = EntitySearcher.getObjectPropertyValues(reaction, GoCAM.has_input, go_cam.go_cam_ont);
		Collection<OWLIndividual> outputs = EntitySearcher.getObjectPropertyValues(reaction, GoCAM.has_output, go_cam.go_cam_ont);
		//reactome rule is simply to count to see if there are fewer outputs then inputs
		if(inputs.size()>outputs.size()) {
			binder.binding = true;
			binder.protein_binding = true;
			binder.protein_complex_binding = false;
			//for protein binding, all members must be proteins
			for(OWLIndividual input : inputs) {
				Collection<OWLClassExpression> types = EntitySearcher.getTypes(input, go_cam.go_cam_ont);
				boolean is_protein_thing = false;
				for(OWLClassExpression type : types) {
					if(type.getClassExpressionType().equals(ClassExpressionType.OWL_CLASS)) {
						for(OWLClass all_type : tbox_qrunner.tbox_class_reasoner.getSuperClasses(type, false).getFlattened()) {
							if(all_type.equals(GoCAM.chebi_protein)) {
								is_protein_thing = true;
								break;
							}
						}
					}
				}
				if(!is_protein_thing) {
					binder.protein_binding = false;
					break;
				}
			}
			if(!binder.protein_binding) {
				//if any member of the input group is a complex, then assert complex binding
				for(OWLIndividual input : inputs) {
					Collection<OWLClassExpression> types = EntitySearcher.getTypes(input, go_cam.go_cam_ont);
					boolean is_complex_thing = false;
					for(OWLClassExpression type : types) {						
						if(type.getClassExpressionType().equals(ClassExpressionType.OWL_CLASS)) {
							for(OWLClass all_type : tbox_qrunner.tbox_class_reasoner.getSuperClasses(type, false).getFlattened()) {
								if(all_type.equals(GoCAM.go_complex)) {
									is_complex_thing = true;
									break;
								}
							}
						}
					}
					if(is_complex_thing) {
						binder.protein_complex_binding = true;
						break;
					}
				}
			}
			//then it may be a protein complex disassembly reaction
		}
		//this is overly promiscuous
		//		else if(inputs.size()<outputs.size()) {
		//			System.out.println("dissociation reaction? "+go_cam.getaLabel(reaction));
		//			binder.dissociation = true;
		//		}
		return binder;
	}

	Set<OWLClass> getTypesFromECs(BiochemicalReaction reaction, GoCAM go_cam){
		Set<OWLClass> gos = new HashSet<OWLClass>();
		for(String ec : reaction.getECNumber()) {
			Set<String> goids = golego.xref_gos.get("EC:"+ec);
			if(goids!=null) {
				for(String goid : goids) {
					OWLClass go = go_cam.df.getOWLClass(IRI.create(goid));
					gos.add(go);
				}
			}
		}
		return gos;
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
	private OWLNamedIndividual addComplexAsSimpleClass(GoCAM go_cam, Set<String> component_names, OWLNamedIndividual complex_i, Set<OWLAnnotation> annotations, String model_id) {
		String combo_name = "";
		for(String n : component_names) {
			combo_name = combo_name+n+"-";
		}
		OWLClass complex_class = go_cam.df.getOWLClass(GoCAM.makeGoCamifiedIRI(model_id, combo_name));
		Set<String> labels =  go_cam.getLabels(complex_i);
		for(String label : labels) {
			go_cam.addLabel(complex_class, label+" ");
		}
		go_cam.addSubclassAssertion(complex_class, GoCAM.go_complex, annotations);
		go_cam.addTypeAssertion(complex_i, complex_class);
		return complex_i;
	}


	private Set<String> getPubmedIdsFromReactomeXrefs(Entity entity) {
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


	/**
	 * Recursively run through a set that may be of mixed type and turn it into a flat list of the bottom level pieces.  
	 * @param input_parts
	 * @param output_parts
	 * @return
	 */
	private Set<PhysicalEntity> flattenNest(Set<PhysicalEntity> input_parts, Set<PhysicalEntity> output_parts, boolean preserve_sets){
		Set<PhysicalEntity> all_parts = new HashSet<PhysicalEntity>();
		if(output_parts!=null) {
			all_parts.addAll(output_parts);
		}
		for(PhysicalEntity e : input_parts) {
			//			if(e.getDisplayName().equals("Cyclin E/A:p-T160-CDK2:CDKN1A,CDKN1B")||
			//					e.getDisplayName().equals("CDKN1A,CDKN1B")) {
			//				System.out.println("hello trouble "+e.getDisplayName()+"\n"+e.getModelInterface()+"\n"+e.getMemberPhysicalEntity());
			//			}
			//complexes
			if(e.getModelInterface().equals(Complex.class)) { 
				Complex complex = (Complex)e;
				Set<PhysicalEntity> members = complex.getMemberPhysicalEntity();				
				members.addAll(complex.getComponent());				
				all_parts = flattenNest(members, all_parts, preserve_sets);			
				//if its not a complex but has parts, than assume we are looking at an entity set
			}else if(e.getMemberPhysicalEntity().size()>0) { 
				if(preserve_sets) {
					//save the set object into the physical entity list
					all_parts.add(e); 
				}else {
					all_parts = flattenNest(e.getMemberPhysicalEntity(), all_parts, preserve_sets);	
				}
			} else {
				all_parts.add(e);
			}
		}
		return all_parts;
	}

	private String getBioPaxLocalId(OWLEntity go_cam_entity, GoCAM go_cam) {
		String local_id = null;
		Collection<OWLAnnotation> ids = EntitySearcher.getAnnotationObjects(go_cam_entity, go_cam.go_cam_ont, GoCAM.skos_exact_match);
		if(ids.size()>1) {
			System.out.println("mapping error - multiple local ids for "+go_cam_entity.toStringID()+" "+ids);
			System.exit(0);
		}else if(ids.size()==1) {
			OWLAnnotation a = ids.iterator().next();
			if(a.getValue().asIRI().isPresent()) {
				local_id = a.getValue().asIRI().get().toString();
			}else {
				System.out.println("mapping error - missing local ids for "+go_cam_entity.toStringID());
				System.exit(0);
			}
		}
		return local_id;
	}
	public static String getBioPaxName(Entity entity) {
		String entity_name = entity.getDisplayName();
		if(entity_name==null) {
			entity_name = entity.getStandardName();
		}
		if(entity_name == null) {
			for(String label : entity.getName()) {
				entity_name = label;
				return entity_name;
			}
		}
		return entity_name;
	}


}
