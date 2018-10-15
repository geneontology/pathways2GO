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
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
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
import org.biopax.paxtools.model.level3.Provenance;
import org.biopax.paxtools.model.level3.PublicationXref;
import org.biopax.paxtools.model.level3.RelationshipXref;
import org.biopax.paxtools.model.level3.SmallMolecule;
import org.biopax.paxtools.model.level3.TemplateDirectionType;
import org.biopax.paxtools.model.level3.TemplateReaction;
import org.biopax.paxtools.model.level3.UnificationXref;
import org.biopax.paxtools.model.level3.XReferrable;
import org.biopax.paxtools.model.level3.Xref;
import org.geneontology.gocam.exchange.idmapping.IdMapper;
import org.geneontology.rules.engine.Explanation;
import org.geneontology.rules.engine.Triple;
import org.geneontology.rules.engine.WorkingMemory;
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
	public static final String goplus_file = 
			//"/Users/bgood/git/noctua_exchange/exchange/src/main/resources/org/geneontology/gocam/exchange/go-plus-merged.owl";
			"/Users/bgood/gocam_input/go-plus-sept-2018.owl";
	public static final String neo_file = 
			"/Users/bgood/gocam_input/neo.owl";
	Set<String> tbox_files;
	ImportStrategy strategy;
	enum ImportStrategy {
		NoctuaCuration, //This generates models intended only for curators to improve manually in Noctua, may contain logical oddities.  Strives to get rid of complexes.  
		DirectImport;   //This generates models that are as close as we can get them to ready for use.  Logic should be sound.  Keeps complexes in and more or less as they are in reactome.  
	}
	boolean explain_inconsistant_models = false;
	String blazegraph_output_journal = "/Users/bgood/noctua-config/blazegraph.jnl";
	GoMappingReport report;
	GOPlus goplus;

	public BioPaxtoGO(){
		strategy = ImportStrategy.NoctuaCuration;
		report = new GoMappingReport();
		tbox_files = new HashSet<String>();
		tbox_files.add(goplus_file);
		tbox_files.add(neo_file);
		try {
			goplus = new GOPlus();
		} catch (OWLOntologyCreationException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
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
		String input_biopax = 
				//"/Users/bgood/Desktop/test/biopax/pathway_commons/WP_ACE_Inhibitor_Pathway.owl";
				//"/Users/bgood/Desktop/test/biopax/pathway_commons/kegg_Biotin_metabolism.owl";
				//"/Users/bgood/Desktop/test/biopax/pathway_commons/PathwayCommons10.wp.BIOPAX.owl";
				//"/Users/bgood/Desktop/test/biopax/BMP_signaling.owl";
				//"/Users/bgood/Desktop/test/biopax/Disassembly_test.owl";
				//"/Users/bgood/Desktop/test/biopax/Homo_sapiens_Oct4_2018.owl";
		//"/Users/bgood/Desktop/test/biopax/Wnt_full_tcf_signaling_may2018.owl";
				"/Users/bgood/Desktop/test/biopax/Wnt_test_oct8_2018.owl";
		String converted = "/Users/bgood/Desktop/test/go_cams/Wnt_test_oct8_2018-";
		//"/Users/bgood/Desktop/test/go_cams/reactome/reactome-homosapiens-";

		boolean add_lego_import = false; //unless you never want to open the output in Protege always leave false..(or learn how to use a catalogue file)
		boolean split_by_pathway = true; //keep to true unless you want one giant model for whatever you input
		boolean save_inferences = false;
		boolean expand_subpathways = false;  //this is a bad idea for high level nodes like 'Signaling Pathways'
		String base_title = "title here";//"Will be replaced if a title can be found for the pathway in its annotations
		String base_contributor = "https://orcid.org/0000-0002-7334-7852"; //Ben Good
		String base_provider = "https://reactome.org";//"https://www.wikipathways.org/";//"https://www.pathwaycommons.org/";
		String tag = "unexpanded";
		if(expand_subpathways) {
			tag = "expanded";
		}	
		bp2g.convertReactomeFile(input_biopax, converted, split_by_pathway, save_inferences, expand_subpathways, base_title, base_contributor, base_provider, tag, add_lego_import);
		System.out.println("Writing report");
		bp2g.report.writeReport("report/");
		System.out.println("All done");
	} 

	private void convertReactomeFile(String input_file, 
			String output, boolean split_by_pathway, boolean save_inferences, boolean expand_subpathways, String base_title, String base_contributor, String base_provider, String tag, boolean add_lego_import ) throws OWLOntologyCreationException, OWLOntologyStorageException, RepositoryException, RDFParseException, RDFHandlerException, IOException {
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
		//set for writing metadata
		String datasource = "";
		if(base_provider.equals("https://reactome.org")) {
			datasource = "Reactome";
		}else if(base_provider.equals("https://www.wikipathways.org/")) {
			datasource = "Wikipathways";
		}else if(base_provider.equals("https://www.pathwaycommons.org/")) {
			datasource = "Pathway Commons";
		}

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
		QRunner tbox_qrunner = go_cam.initializeQRunnerForTboxInference(tbox_files);
		//list pathways
		int total_pathways = model.getObjects(Pathway.class).size();

		boolean add_pathway_components = true;
		for (Pathway currentPathway : model.getObjects(Pathway.class)){
			if(!keepPathway(currentPathway, base_provider)) { //Pathway Commons contains a lot of content free stubs when viewed this way
				continue;
			}
			String datasource_id = null;
			Set<String> pathway_source_comments = new HashSet<String>();
			n_pathways++;
			System.out.println(n_pathways+" of "+total_pathways+" Pathway:"+currentPathway.getName()); 
			if(split_out_by_pathway) {
				//then reinitialize for each pathway
				datasource_id = null;
				String contributor_link = base_provider;
				//See if there is a specific pathway reference to allow a direct link
				Set<Xref> xrefs = currentPathway.getXref();
				for(Xref xref : xrefs) {
					if(xref.getModelInterface().equals(UnificationXref.class)) {
						UnificationXref r = (UnificationXref)xref;	    			
						if(r.getDb().equals("Reactome")) {
							datasource_id = r.getId();
							if(datasource_id.startsWith("R-HSA")) {
								contributor_link = "https://reactome.org/content/detail/"+datasource_id;
								//or https://reactome.org/PathwayBrowser/#/ to go right to pathway browser
								break;
							}
						}
					}
				}	
				//check for datasource (seen commonly in Pathway Commons)
				Set<Provenance> datasources = currentPathway.getDataSource();
				for(Provenance prov : datasources) {
					if(prov.getDisplayName()!=null) {
						datasource = prov.getDisplayName();
					}
					// there is more provenance buried in comment field 
					pathway_source_comments.addAll(prov.getComment());
					//e.g. for a WikiPathways model retrieved from Pathway Commons I see
					//Source http://pointer.ucsf.edu/wp/biopax/wikipathways-human-v20150929-biopax3.zip type: BIOPAX, WikiPathways - Community Curated Human Pathways; 29/09/2015 (human)
				}			
				base_ont_title = datasource+":"+tag+":"+currentPathway.getDisplayName();
				iri = "http://model.geneontology.org/"+base_ont_title.hashCode(); //using a URL encoded string here confused the UI code...
				ont_iri = IRI.create(iri);	
				go_cam = new GoCAM(ont_iri, base_ont_title, contributor_link, null, base_provider, add_lego_import);
				//journal is by default in 'append' mode - keeping the same journal reference add each pathway to same journal
				go_cam.path2bgjournal = journal;
				go_cam.blazegraphdb = blaze;
			}

			String uri = currentPathway.getUri();
			//make the OWL individual representing the pathway so it can be used below
			OWLNamedIndividual p = go_cam.makeAnnotatedIndividual(GoCAM.makeGoCamifiedIRI(uri));
			//annotate it with any provenance comments
			for(String comment : pathway_source_comments) {
				go_cam.addComment(p, comment);
			}
			//define it (add types etc)
			definePathwayEntity(go_cam, currentPathway, datasource_id, expand_subpathways, add_pathway_components);	
			//get and set parent pathways
			//currently viewing one model as a complete thing - leaving out outgoing connections.  
			if(ImportStrategy.DirectImport != null) {
				Set<String> pubids = getPubmedIds(currentPathway);
				for(Pathway parent_pathway : currentPathway.getPathwayComponentOf()) {				
					OWLNamedIndividual parent = go_cam.makeAnnotatedIndividual(GoCAM.makeGoCamifiedIRI(parent_pathway.getUri()));
					go_cam.addRefBackedObjectPropertyAssertion(p, GoCAM.part_of, parent, pubids, GoCAM.eco_imported_auto,  "PMID", null);
					definePathwayEntity(go_cam, parent_pathway, datasource_id, expand_subpathways, add_pathway_components);
				}
			}
			//write results
			if(split_out_by_pathway) {
				String n = currentPathway.getDisplayName();
				n = n.replaceAll("/", "-");	
				n = n.replaceAll(" ", "_");
				String outfilename = converted+n+".ttl";	
				wrapAndWrite(outfilename, go_cam, tbox_qrunner, save_inferences, save2blazegraph, n, expand_subpathways);
				//reset for next pathway.
				go_cam.ontman.removeOntology(go_cam.go_cam_ont);
				go_cam.qrunner = null;
				System.out.println("reseting for next pathway...");
			} 
		}	
		//export all
		if(!split_out_by_pathway) {
			wrapAndWrite(converted+".ttl", go_cam, tbox_qrunner, save_inferences, save2blazegraph, converted, expand_subpathways);		
		}
		
		System.out.println("done with file "+input_biopax);
	}

	/**
	 * Only keep it if it has some useful content
	 * @param pathway
	 * @return
	 */
	boolean keepPathway(Pathway pathway, String base_provider) {
		boolean keep = false;
		if(base_provider.equals("https://reactome.org")) {
			keep = true;
		}else {
			Set<Process> processes = pathway.getPathwayComponent();
			if(processes!=null&&processes.size()>1) {
				keep = true;
			}
		}
		return keep;
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
	private void wrapAndWrite(String outfilename, GoCAM go_cam, QRunner tbox_qrunner, boolean save_inferences, boolean save2blazegraph, String pathwayname, boolean expand_subpathways) throws OWLOntologyCreationException, OWLOntologyStorageException, RepositoryException, RDFParseException, RDFHandlerException, IOException {		
		//set up a sparqlable kb in sync with ontology
		System.out.println("setting up rdf model for sparql rules");
		go_cam.qrunner = new QRunner(go_cam.go_cam_ont); 
		//infer new edges based on sparql matching
		System.out.println("Before sparql inference -  triples: "+go_cam.qrunner.nTriples());
		GoCAM.RuleResults rule_results = go_cam.applySparqlRules(strategy);
		System.out.println("After sparql inference -  triples: "+go_cam.qrunner.nTriples());
		System.out.println("Rule results:\n"+rule_results.toString());
		//sparql rules make additions to go_cam_ont, add them to the rdf model 
		//set up to apply OWL inference to test for consistency and add classifications
		//go_cam.go_cam_ont is ready and equals the Abox..
		//TODO capture cases where classes are added where none existed before.
		ClassificationReport before = go_cam.getClassificationReport();		
		//don't want to reload tbox each time..
		boolean rebuild_tbox_with_go_cam_ont = false;
		//this will also rebuild the rdf version of the ontology, adding things it infers
		WorkingMemory wm = go_cam.applyArachneInference(tbox_qrunner, rebuild_tbox_with_go_cam_ont);
		ClassificationReport after = go_cam.getClassificationReport();
		ReasonerReport reasoner_report = new ReasonerReport(before, after);
		report.pathway_class_report.put(pathwayname, reasoner_report);
		//checks for inferred things with rdf:type OWL:Nothing with a sparql query
		boolean is_logical = go_cam.validateGoCAM();	

		//checks for inferred classifications for reporting
		boolean skip_indirect = true;
		Map<String, Set<String>> inferred_types_by_uri = ArachneAccessor.getInferredTypes(wm, skip_indirect);
		Map<String, Set<String>> inferred_types = new HashMap<String, Set<String>>();
		//add labels
		for(String uri : inferred_types_by_uri.keySet()) {
			String u = uri.replace(">", "");
			u = u.replace("<", "");
			OWLEntity e = go_cam.df.getOWLNamedIndividual(IRI.create(u));
			String label = go_cam.getaLabel(e);
			label = label+"\t"+uri;
			inferred_types.put(label,inferred_types_by_uri.get(uri));
		}
		report.pathway_inferred_types.put(pathwayname, inferred_types);
		//synchronize jena model <- with owl-api model	 
		//go_cam_ont should have everything we want at this point
		go_cam.qrunner = new QRunner(go_cam.go_cam_ont); 
		if(strategy == ImportStrategy.NoctuaCuration) {
			//adds coordinates to go_cam_ont model 
			NoctuaLayout layout = new NoctuaLayout(go_cam);
			go_cam = layout.layoutForNoctuaVersion1(go_cam);	
			//add them into the rdf 
			go_cam.qrunner = new QRunner(go_cam.go_cam_ont); 
		}

		//removes basic types like 'Cellular Component' used in here to make reports but not useful in output data
		go_cam.qrunner.deleteCellularComponentTyping();
		System.out.println("writing....");
		go_cam.writeGoCAM_jena(outfilename, save2blazegraph);
		System.out.println("done writing...");
		if(!is_logical) {
			//System.out.println("Illogical go_cam..  stopping");
			//System.exit(0);
			report.inconsistent_models.add(outfilename);
			//explain
			if(explain_inconsistant_models) {
				scala.collection.Iterator<Triple> triples = wm.facts().toList().iterator();
				while(triples.hasNext()) {				
					Triple triple = triples.next();
					if(wm.asserted().contains(triple)) {
						continue;
					}else { //<http://arachne.geneontology.org/indirect_type>
						if(triple.p().toString().equals("<http://www.w3.org/1999/02/22-rdf-syntax-ns#type>")&&
								triple.o().toString().equals("<http://www.w3.org/2002/07/owl#Nothing>")) {
							OWLEntity bad = go_cam.df.getOWLNamedIndividual(IRI.create(triple.s().toString()));
							System.out.println("inferred inconsistent:"+triple.s()+" "+go_cam.getaLabel(bad));
							scala.collection.immutable.Set<Explanation> explanations = wm.explain(triple);
							scala.collection.Iterator<Explanation> e = explanations.iterator();
							while(e.hasNext()) {
								Explanation exp = e.next();
								System.out.println(exp.toString());
								System.out.println();
							}
						}
					}
				}
			}
		}
	}

	private void definePathwayEntity(GoCAM go_cam, Pathway pathway, String reactome_id, boolean expand_subpathways, boolean add_components) throws IOException {
		IRI pathway_iri = GoCAM.makeGoCamifiedIRI(pathway.getUri());
		OWLNamedIndividual pathway_e = go_cam.makeAnnotatedIndividual(pathway_iri);
		go_cam.addLabel(pathway_e, pathway.getDisplayName());
		if(add_components) {
			//in obo world each pathway is a biological process
			go_cam.addTypeAssertion(pathway_e, GoCAM.bp_class);
		}//if not adding component this is only being used to show context for some other pathway
		else if(strategy == ImportStrategy.NoctuaCuration){
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
					OWLClass xref_go_parent = goplus.getOboClass(uri, true);
					boolean deprecated = goplus.isDeprecated(uri);
					if(deprecated) {
						report.deprecated_classes.add(pathway.getDisplayName()+"\t"+uri+"\tBP");
					}					
					//add it into local hierarchy (temp pre import)	
					//addRefBackedObjectPropertyAssertion
					go_cam.addSubclassAssertion(xref_go_parent, GoCAM.bp_class, null);
					go_cam.addTypeAssertion(pathway_e, xref_go_parent);
					//record mappings
					mappedgo.add(goid);
				}
			}
		}
		//store mappings
		report.bp2go_bp.put(pathway, mappedgo);

		if(add_components) {
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
				OWLNamedIndividual child = go_cam.df.getOWLNamedIndividual(GoCAM.makeGoCamifiedIRI(process.getUri()));
				go_cam.addRefBackedObjectPropertyAssertion(pathway_e, GoCAM.has_part, child, pubids, GoCAM.eco_imported_auto, "PMID", null);
				//attach reactions that make up the pathway
				if(process instanceof Conversion 
						|| process instanceof TemplateReaction
						|| process instanceof GeneticInteraction 
						|| process instanceof MolecularInteraction 
						|| process instanceof Interaction){
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
	private void defineReactionEntity(GoCAM go_cam, Entity entity, IRI this_iri) throws IOException {
		//add entity to ontology, whatever it is
		OWLNamedIndividual e = null;
		if(this_iri!=null) {
			e = go_cam.makeAnnotatedIndividual(this_iri);
		}else {
			e = go_cam.makeAnnotatedIndividual(GoCAM.makeGoCamifiedIRI(entity.getUri()));
		}
		//check specifically for Reactome id
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
			//go_cam.addTypeAssertion(e, GoCAM.continuant_class); //will be specified further later.  This is here because Reactome sometimes does not make any more specific assertion than 'physical entity' for things like f-actin.  https://reactome.org/content/detail/R-HSA-202986
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
							OWLClass xref_go_loc = goplus.getOboClass(uri, true);
							boolean deprecated = goplus.isDeprecated(uri);
							if(deprecated) {
								report.deprecated_classes.add(entity.getDisplayName()+"\t"+xref_go_loc.getIRI().toString()+"\tCC");
							}
							Set<XReferrable> refs = uref.getXrefOf();							
							for(XReferrable ref : refs) {
								location_term = ref.toString().replaceAll("CellularLocationVocabulary_", "");
								break;
							}
							if(location_term!=null) {
								OWLNamedIndividual loc_e = go_cam.makeAnnotatedIndividual(GoCAM.makeRandomIri());
								//go_cam.makeAnnotatedIndividual(GoCAM.makeGoCamifiedIRI(loc.getUri()+entity.getUri()));
								go_cam.addLabel(xref_go_loc, location_term);
								go_cam.addTypeAssertion(loc_e, xref_go_loc);
								//add this for reporting reasons - avoiding need for use of full reasoner 
								go_cam.addTypeAssertion(loc_e, GoCAM.cc_class);
								go_cam.addRefBackedObjectPropertyAssertion(e, GoCAM.located_in, loc_e, pubids, GoCAM.eco_imported_auto, "PMID", null);		
								if(strategy == ImportStrategy.NoctuaCuration) {
									go_cam.addLiteralAnnotations2Individual(e.getIRI(), GoCAM.rdfs_comment, "located_in "+location_term);
								}
								break; //there can be only one 
							}
						}
					}
				}
			}
			//now get more specific type information
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
					//assert that they are proteins (for use without neo import which would clarify that)
					go_cam.addTypeAssertion(e,  uniprotein_class);
				}else { //no entity reference so look for parts 
					Set<PhysicalEntity> prot_parts = protein.getMemberPhysicalEntity();
					if(prot_parts!=null) {					
						//if its made of parts and it doesn't have its own unique protein name, call it a complex..	
						Set<String> cnames = new HashSet<String>();
						for(PhysicalEntity prot_part : prot_parts) {
							cnames.add(prot_part.getDisplayName());
							//hook up parts	
							//define it independently within this context
							OWLNamedIndividual prot_part_entity = 
									//go_cam.df.getOWLNamedIndividual(GoCAM.makeGoCamifiedIRI(prot_part.getUri()+entity.getUri())); 
									go_cam.df.getOWLNamedIndividual(GoCAM.makeRandomIri()); 
							go_cam.addObjectPropertyAssertion(e, GoCAM.has_part, prot_part_entity, null);		
							//define them = hopefully get out a name and a class for the sub protein.	
							defineReactionEntity(go_cam, prot_part, prot_part_entity.getIRI());					
						}
						//though it clutters the display, this is needed to enable Arachne inference without adding tbox assertions from each model
						//
						go_cam.addTypeAssertion(e, GoCAM.go_complex);
						//}
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
						//In GO-CAM we almost always want to talk about proteins
						//if there is a uniprot identifier to use, use that before anything else.
						String db = xref.getDb().toLowerCase();
						String id = xref.getId();
						if(db.contains("uniprot")) {
							OWLClass uniprotein_class = go_cam.df.getOWLClass(IRI.create(GoCAM.uniprot_iri + id)); 
							go_cam.addSubclassAssertion(uniprotein_class, GoCAM.chebi_protein, null);
							go_cam.addTypeAssertion(e, uniprotein_class);
						}
						//this was added for something in Reactome, don't know if generally useful..
						if(xref.getModelInterface().equals(UnificationXref.class)) {
							UnificationXref uref = (UnificationXref)xref;	
							if(uref.getDb().equals("ENSEMBL")) {
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
					String chebi_id = null;
					//first scan for directly asserted chebis

					for(Xref xref : p_xrefs) {
						//# BioPAX4
						String db = xref.getDb();
						db = db.toLowerCase();
						if(db.contains("chebi")) {
							chebi_id = xref.getId().replace(":", "_");
							break; //TODO just stop at one for now
						}
					}

					//if no chebis look at any other ids and try to convert
					if(chebi_id==null) {
						for(Xref xref : p_xrefs) {
							String database = xref.getDb();
							String id = xref.getId();
							String map = IdMapper.map2chebi(database, id);
							if(map!=null) {
								chebi_id = map;
								break;
							}
						}
					}
					if(chebi_id!=null) {			
						String chebi_uri = GoCAM.obo_iri + chebi_id;
						OWLClass mlc_class = goplus.getOboClass(chebi_uri, true);
						boolean deprecated = goplus.isDeprecated(chebi_uri);
						if(deprecated) {
							report.deprecated_classes.add(entity.getDisplayName()+"\t"+chebi_uri+"\tchebi");
						}
						String chebi_report_key;
						if(goplus.isChebiRole(chebi_uri)) {
							go_cam.addSubclassAssertion(mlc_class, GoCAM.chemical_role, null);
							OWLNamedIndividual rolei = go_cam.makeAnnotatedIndividual(GoCAM.makeGoCamifiedIRI(entity.hashCode()+"chemical"));
							go_cam.addTypeAssertion(rolei, mlc_class);									
							//assert entity here is a chemical instance
							go_cam.addTypeAssertion(e, GoCAM.chemical_entity);
							//connect it to the role
							go_cam.addRefBackedObjectPropertyAssertion(e, GoCAM.has_role, rolei, null, GoCAM.eco_imported_auto, null, null);
							chebi_report_key = chebi_uri+"\t"+entity.getDisplayName()+"\trole";
						}else { //presumably its a chemical entity if not a role								
							go_cam.addSubclassAssertion(mlc_class, GoCAM.chemical_entity, null);	
							//name the class with the chebi id
							go_cam.addLabel(mlc_class, chebi_id);
							//assert its a chemical instance
							go_cam.addTypeAssertion(e, mlc_class);
							chebi_report_key =  chebi_uri+"\t"+entity.getDisplayName()+"\tchemical";
						}
						//count it for report because suspect these might be problems to fix
						Integer ncheb = report.chebi_count.get(chebi_report_key);
						if(ncheb==null) {
							ncheb = 0;
						}
						ncheb++;
						report.chebi_count.put(chebi_report_key, ncheb);
					}else {
						//no chebi so we don't know what it is (for Noctua) aside from being some kind of chemical entity
						go_cam.addTypeAssertion(e, GoCAM.chemical_entity);
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
							IRI comp_uri = //GoCAM.makeGoCamifiedIRI(component.getUri()+entity.getUri());
									GoCAM.makeRandomIri();
							OWLNamedIndividual component_entity = go_cam.df.getOWLNamedIndividual(comp_uri);
							go_cam.addObjectPropertyAssertion(e, GoCAM.has_part, component_entity, null);
							defineReactionEntity(go_cam, component, comp_uri);
						}
					}
					//assert it as a complex - needed for correct inference (without loading up the subclass assertion in the above)
					go_cam.addTypeAssertion(e, GoCAM.go_complex);

				}
			}
			//make sure all physical things are minimally typed as a continuant
			Collection<OWLClassExpression> ptypes = EntitySearcher.getTypes(e, go_cam.go_cam_ont);		
			if(ptypes.isEmpty()) {
				go_cam.addTypeAssertion(e, GoCAM.continuant_class);
			}


		}//end physical thing
		//Interaction subsumes Conversion, GeneticInteraction, MolecularInteraction, TemplateReaction
		//Conversion subsumes BiochemicalReaction, TransportWithBiochemicalReaction, ComplexAssembly, Degradation, GeneticInteraction, MolecularInteraction, TemplateReaction
		//though the great majority are BiochemicalReaction
		else if (entity instanceof Interaction){  		
			if(entity.getModelInterface().equals(Interaction.class)) {
				//this happens a lot in WikiPathways, though is not considered good practice
				//should use a more specific class if possible.  
				Set<Entity> interactors = ((Interaction) entity).getParticipant();
				Set<OWLNamedIndividual> physical_participants = new HashSet<OWLNamedIndividual>();
				Set<OWLNamedIndividual> process_participants = new HashSet<OWLNamedIndividual>();
				for(Entity interactor : interactors) {				
					if(interactor instanceof PhysicalEntity) {
						IRI i_iri = GoCAM.makeRandomIri();
						OWLNamedIndividual i_entity = go_cam.df.getOWLNamedIndividual(i_iri);
						defineReactionEntity(go_cam, interactor, i_iri);
						go_cam.addObjectPropertyAssertion(e, GoCAM.has_participant, i_entity, go_cam.getDefaultAnnotations());
						physical_participants.add(i_entity);
					}else {
						OWLNamedIndividual part_mf = go_cam.df.getOWLNamedIndividual(GoCAM.makeGoCamifiedIRI(interactor.getUri()));
						defineReactionEntity(go_cam, interactor, part_mf.getIRI());
						go_cam.addObjectPropertyAssertion(e, GoCAM.has_part, part_mf, go_cam.getDefaultAnnotations());
						process_participants.add(part_mf);
					}					
				}
				for(OWLNamedIndividual p1 : physical_participants) {
					for(OWLNamedIndividual p2 : physical_participants) {
						if(!p1.equals(p2)) {
							go_cam.addObjectPropertyAssertion(p1, GoCAM.interacts_with, p2, go_cam.getDefaultAnnotations());
						}
					}
				}
				for(OWLNamedIndividual p1 : process_participants) {
					for(OWLNamedIndividual p2 : process_participants) {
						if(!p1.equals(p2)) {
							go_cam.addObjectPropertyAssertion(p1, GoCAM.functionally_related_to, p2, go_cam.getDefaultAnnotations());
						}
					}
				}
				for(OWLNamedIndividual p1 : physical_participants) {
					for(OWLNamedIndividual p2 : process_participants) {
						if(!p1.equals(p2)) {
							go_cam.addObjectPropertyAssertion(p2, GoCAM.enabled_by, p1, go_cam.getDefaultAnnotations());
						}
					}
				}
			}
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

			//link to participants in reaction
			if(entity instanceof Conversion) {
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
						System.out.println("REVERSIBLE reaction found!  Defaulting to assumption of left to right "+entity.getDisplayName()+" "+entity.getUri());
					}
				}else if(direction.equals(ConversionDirectionType.RIGHT_TO_LEFT)) {
					outputs = ((Conversion) entity).getLeft();
					inputs = ((Conversion) entity).getRight();
					System.out.println("Right to left reaction found!  "+entity.getDisplayName()+" "+entity.getUri());
				}else  {
					System.out.println("Reaction direction "+direction+" unknown");
					System.exit(0);
				}

				if(inputs!=null) {
					for(PhysicalEntity input : inputs) {
						IRI i_iri = null;
						i_iri = GoCAM.makeGoCamifiedIRI(input.getUri()+entity.getUri()+"input");
						OWLNamedIndividual input_entity = go_cam.df.getOWLNamedIndividual(i_iri);
						defineReactionEntity(go_cam, input, i_iri);
						go_cam.addObjectPropertyAssertion(e, GoCAM.has_input, input_entity,go_cam.getDefaultAnnotations());
					}}
				if(outputs!=null) {
					for(PhysicalEntity output : outputs) {
						IRI o_iri = null;
						o_iri = GoCAM.makeGoCamifiedIRI(output.getUri()+entity.getUri()+"output");
						OWLNamedIndividual output_entity = go_cam.df.getOWLNamedIndividual(o_iri);
						defineReactionEntity(go_cam, output, o_iri);
						go_cam.addObjectPropertyAssertion(e, GoCAM.has_output, output_entity, go_cam.getDefaultAnnotations());
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
				//find controllers 
				Set<Control> controllers = ((Process) entity).getControlledOf();
				for(Control controller : controllers) {
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
								OWLClass xref_go_func = goplus.getOboClass(uri, true);
								if(goplus.isDeprecated(uri)) {
									report.deprecated_classes.add(entity.getDisplayName()+"\t"+uri+"\tMF");
								}
								//add the go function class as a type for the reaction instance being controlled here
								go_cam.addTypeAssertion(e, xref_go_func);
								go_mf.add(goid);
							}
						}
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
							go_cam.addRefBackedObjectPropertyAssertion(e, GoCAM.enabled_by, controller_e, controllerpubrefs, GoCAM.eco_imported_auto, "PMID", null);	
						}else {
							//otherwise look at text 
							//define how the molecular function (process) relates to the reaction (process)
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
				//If a reaction is xreffed directly to the GO it is mapping to a biological process
				//this indicates the reaction is a part_of that process
				//TODO this is funky as the reaction is already part of a pathway which itself may be mapped directly to a process
				//seems that mapping ought to go on pathway, instead of both places
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
							OWLClass xref_go_func = goplus.getOboClass(uri, true);
							if(goplus.isDeprecated(uri)) {
								report.deprecated_classes.add(entity.getDisplayName()+"\t"+uri+"\tBP");
							}
							//go_cam.addSubclassAssertion(xref_go_func, GoCAM.bp_class, null);
							//the go class can not be a type for the reaction instance as we want to classify reactions as functions
							//and MF disjoint from BP
							//go_cam.addTypeAssertion(e, xref_go_func);
							//so make a new individual, hook it to that class, link to it via part of 
							OWLNamedIndividual bp_i = go_cam.makeAnnotatedIndividual(GoCAM.makeGoCamifiedIRI(entity.getUri()+goid+"individual"));
							go_cam.addLiteralAnnotations2Individual(bp_i.getIRI(), GoCAM.rdfs_comment, "Asserted direct link between reaction and biological process, independent of current pathway");
							go_cam.addTypeAssertion(bp_i, xref_go_func);
							go_cam.addRefBackedObjectPropertyAssertion(e, GoCAM.part_of, bp_i, null, GoCAM.eco_imported_auto, "", null);
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
						go_cam.addTypeAssertion(e, GoCAM.molecular_function);	
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

	Set<OWLClass> getTypesFromECs(BiochemicalReaction reaction, GoCAM go_cam){
		Set<OWLClass> gos = new HashSet<OWLClass>();
		for(String ec : reaction.getECNumber()) {
			Set<String> goids = goplus.xref_gos.get("EC:"+ec);
			for(String goid : goids) {
				OWLClass go = go_cam.df.getOWLClass(IRI.create(goid));
				gos.add(go);
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
	private OWLNamedIndividual addComplexAsSimpleClass(GoCAM go_cam, Set<String> component_names, OWLNamedIndividual complex_i, Set<OWLAnnotation> annotations) {
		String combo_name = "";
		for(String n : component_names) {
			combo_name = combo_name+n+"-";
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
