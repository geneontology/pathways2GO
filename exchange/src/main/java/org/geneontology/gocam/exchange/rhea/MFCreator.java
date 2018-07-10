/**
 * 
 */
package org.geneontology.gocam.exchange.rhea;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import org.apache.commons.io.IOUtils;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.query.ResultSet;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.vocabulary.OWL;
import org.biopax.paxtools.model.level3.BiochemicalReaction;
import org.geneontology.gocam.exchange.App;
import org.geneontology.gocam.exchange.ArachneAccessor;
import org.geneontology.gocam.exchange.ClassificationReport;
import org.geneontology.gocam.exchange.GoCAM;
import org.geneontology.gocam.exchange.GoMappingReport;
import org.geneontology.gocam.exchange.Helper;
import org.geneontology.gocam.exchange.QRunner;
import org.geneontology.gocam.exchange.ReasonerReport;
import org.geneontology.gocam.exchange.rhea.RheaConverter.rheaReaction;
import org.geneontology.rules.engine.WorkingMemory;
import org.semanticweb.elk.owlapi.ElkReasonerFactory;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.AxiomType;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLAnnotation;
import org.semanticweb.owlapi.model.OWLAxiom;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLClassAssertionAxiom;
import org.semanticweb.owlapi.model.OWLClassExpression;
import org.semanticweb.owlapi.model.OWLDataFactory;
import org.semanticweb.owlapi.model.OWLDataProperty;
import org.semanticweb.owlapi.model.OWLEntity;
import org.semanticweb.owlapi.model.OWLIndividual;
import org.semanticweb.owlapi.model.OWLLiteral;
import org.semanticweb.owlapi.model.OWLNamedIndividual;
import org.semanticweb.owlapi.model.OWLObjectProperty;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyCreationException;
import org.semanticweb.owlapi.model.OWLOntologyManager;
import org.semanticweb.owlapi.model.OWLOntologyStorageException;
import org.semanticweb.owlapi.model.OWLSubClassOfAxiom;
import org.semanticweb.owlapi.reasoner.NodeSet;
import org.semanticweb.owlapi.reasoner.OWLReasoner;
import org.semanticweb.owlapi.reasoner.OWLReasonerFactory;
import org.semanticweb.owlapi.search.EntitySearcher;

/**
 * @author bgood
 *
 */
public class MFCreator {

	public GoCAM go_cam;
	public static OWLClass SubstanceSet, CatalyticActivity;
	public static OWLObjectProperty has_participant, has_input, has_output, has_member, has_directed_reaction;
	public static OWLDataProperty has_stoichiometry;
	public static String base = "http://purl.obolibrary.org/obo/";
	public Model go_jena;
	public Map<String, Set<OWLClass>> rhea_go;
	public Map<String, Set<OWLClass>> rhea_ec_go;
	public Map<OWLClass, ReasoningImpactReport> class_report;
	/**
	 * @throws OWLOntologyCreationException 
	 * 
	 */
	public MFCreator(String existing_ontology_to_add_to) throws OWLOntologyCreationException {
		if(existing_ontology_to_add_to==null) {
			this.go_cam = new GoCAM();
		}else {
			this.go_cam = new GoCAM(existing_ontology_to_add_to);
		}
		SubstanceSet = go_cam.df.getOWLClass(IRI.create(base+"SubstanceSet"));
		CatalyticActivity = go_cam.df.getOWLClass(IRI.create("http://purl.obolibrary.org/obo/GO_0003824"));
		has_input = go_cam.df.getOWLObjectProperty(IRI.create("http://purl.obolibrary.org/obo/RO_0002233")); 
		has_output = go_cam.df.getOWLObjectProperty(IRI.create("http://purl.obolibrary.org/obo/RO_0002234")); 
		has_participant = go_cam.df.getOWLObjectProperty(IRI.create("http://purl.obolibrary.org/obo/RO_0000057")); 
		has_member = go_cam.df.getOWLObjectProperty(IRI.create("http://purl.obolibrary.org/obo/RO_0002351")); 
		has_stoichiometry  = go_cam.df.getOWLDataProperty(IRI.create(base+"has_stoichiometry")); 
		has_directed_reaction  = go_cam.df.getOWLObjectProperty(IRI.create(base+"has_directed_reaction")); 
		go_jena = ModelFactory.createDefaultModel();
		go_jena.read("/Users/bgood/git/noctua_exchange/exchange/src/main/resources/org/geneontology/gocam/exchange/go.owl");
		rhea_go = new HashMap<String, Set<OWLClass>>();
		rhea_ec_go =  new HashMap<String, Set<OWLClass>>();
	}

	/**
	 * @param args
	 * @throws OWLOntologyCreationException 
	 * @throws OWLOntologyStorageException 
	 * @throws IOException 
	 */
	public static void main(String[] args) throws OWLOntologyCreationException, OWLOntologyStorageException, IOException {
		//buildOntologyDefinitions();
		MFCreator m = new MFCreator(null);
		//m.testReactomeClassificationsWithArachne();
		m.testReactomeClassificationsWithELK();
	}

	public void testReactomeClassificationsWithELK() throws OWLOntologyCreationException, IOException {
		Map<OWLIndividual, Set<OWLClass>> reaction_manual_classifications = new HashMap<OWLIndividual, Set<OWLClass>>();
		Map<OWLIndividual, Set<OWLClass>> reaction_goplus_classifications = new HashMap<OWLIndividual, Set<OWLClass>>();
		Map<OWLIndividual, Set<OWLClass>> reaction_goplusplus_classifications = new HashMap<OWLIndividual, Set<OWLClass>>();

		String test_reaction_iri = "http://model.geneontology.org/-983049166";
		String old_ontology = "/Users/bgood/git/noctua_exchange/exchange/src/main/resources/org/geneontology/gocam/exchange/go-plus-merged.owl";
		String new_ontology = "/Users/bgood/Desktop/test/tmp/GO_Ultra_GCI_test_new_chebi.ttl";

		boolean check_reactome_for_inconsistent_models = false;
		String reactome_dir = "/Users/bgood/reactome-go-cam-models/human/";
		OWLOntologyManager man = OWLManager.createOWLOntologyManager();
		OWLDataFactory df = man.getOWLDataFactory();
		OWLReasonerFactory reasonerFactory = new ElkReasonerFactory();
		String defined_ontology = new_ontology;
		System.out.println("Loading ontology "+defined_ontology);
		OWLOntology goplus = man.loadOntologyFromOntologyDocument(new File(defined_ontology));	
		//iterate through the reactome pathways
		//load them into the mega ontology
		File dir = new File(reactome_dir);
		File[] directoryListing = dir.listFiles();
		if (directoryListing == null) {
			System.out.println("Bad input dir "+reactome_dir);
			System.exit(0);
		}
		int x = 0;

		Set<String> skip = new HashSet<String>(); //these are inconsistent and break reasoner
		skip.add("/Users/bgood/reactome-go-cam-models/human/reactome-homosapiens-Glutathione_synthesis_and_recycling.ttl");

		System.out.println("Building reasoner for "+defined_ontology+" reactome go-cams");
		OWLReasoner goplus_reasoner = reasonerFactory.createReasoner(goplus);		
		System.out.println("Loading reactome models and checking for inconsistencies ");

		for (File gocam_ttl : directoryListing) {
			if(!gocam_ttl.getName().endsWith(".ttl")||
					skip.contains(gocam_ttl.getAbsolutePath())) {
				continue;
			}
			x++;
			System.out.println(x+" loading "+gocam_ttl.getName());
			OWLOntology pathway_ont = man.loadOntologyFromOntologyDocument(gocam_ttl);				
			//record manual type assertions for mf terms
			Set<OWLClassAssertionAxiom> man_asserts = new HashSet<OWLClassAssertionAxiom>();
			for(OWLIndividual reaction : EntitySearcher.getIndividuals(GoCAM.molecular_function, pathway_ont)){
				Collection<OWLClassExpression> types = EntitySearcher.getTypes(reaction, pathway_ont);
				Set<OWLClass> manual = new HashSet<OWLClass>();				
				for(OWLClassExpression exp : types) {
					manual.add(exp.asOWLClass());
					if(!exp.equals(GoCAM.molecular_function)) {
						man_asserts.add(df.getOWLClassAssertionAxiom(exp, reaction));					
					}
				}
				reaction_manual_classifications.put(reaction, manual);
			}
			//remove manual classifications to see if they are found again in the new stuff
			man.removeAxioms(pathway_ont, man_asserts);
			
			//add all reactome models to main ontology graph
			man.addAxioms(goplus, pathway_ont.getAxioms());
			if(check_reactome_for_inconsistent_models) {
				goplus_reasoner.flush();
				boolean pathway_valid = goplus_reasoner.isConsistent();
				if(!pathway_valid) {
					man.removeAxioms(goplus, pathway_ont.getAxioms());
					System.out.println("Skipping "+gocam_ttl);
				}
			}
		}
		//now we should have everything, load into reasoner 
		goplus_reasoner.flush();
		//now check to see what can be inferred given the ontology and instances as they are provided in input
		Set<OWLNamedIndividual> reactions = goplus_reasoner.getInstances(GoCAM.molecular_function, false).getFlattened();
		System.out.println(reactions.size()+" reactions");
		int n_reactions = 0; int n_unclassified = 0; int n_classified = 0;

		for(OWLNamedIndividual test_reaction : reactions) {
			n_reactions++;
			Set<OWLClass> types = goplus_reasoner.getTypes(test_reaction, true).getFlattened();
			//String reaction_label = Helper.getaLabel(test_reaction, goplus);
			boolean classified = false;
			reaction_goplus_classifications.put(test_reaction, types);
			for(OWLClass type : types) {
				if(!type.equals(GoCAM.molecular_function)) {
					classified = true;
				}
			}
			if(classified) {
				n_classified++;
			}else {
				n_unclassified++;
			}
		}
		//hello go
		//base report for ontology and kb input.  
		System.out.println(defined_ontology+"\nN reactions:"+n_reactions+"\tn classified:"+n_classified+"\tn unclassified:"+n_unclassified+"\t%classified"+((float)n_classified/(float)n_reactions));
		//now add catalytic activity to everything with at least one chebi as input and one chebi as output to activate new axioms
		Set<OWLClassAssertionAxiom> cas = new HashSet<OWLClassAssertionAxiom>();
		int n_ca = 0;
		for(OWLNamedIndividual test_reaction : reactions) {
			Collection<OWLIndividual> inputs = EntitySearcher.getObjectPropertyValues(test_reaction, GoCAM.has_input, goplus);
			Collection<OWLIndividual> outputs = EntitySearcher.getObjectPropertyValues(test_reaction, GoCAM.has_output, goplus);
			boolean chebi_input = false; boolean chebi_output = false;
			Set<OWLClass> input_types = new HashSet<OWLClass>();
			for(OWLIndividual input : inputs) {
				Set<OWLClass> types = goplus_reasoner.getTypes(input.asOWLNamedIndividual(), true).getFlattened();
				input_types.addAll(types);
				for(OWLClass type : types) {
					IRI iri = type.getIRI();
					if(iri.toString().contains("CHEBI")){
						chebi_input = true;
					}
				}
			}
			Set<OWLClass> output_types = new HashSet<OWLClass>();
			for(OWLIndividual output : outputs) {
				Set<OWLClass> types = goplus_reasoner.getTypes(output.asOWLNamedIndividual(), true).getFlattened();
				output_types.addAll(types);
				for(OWLClass type : types) {
					IRI iri = type.getIRI();
					if(iri.toString().contains("CHEBI")){
						chebi_output = true;
					}
				}
			}		
			if(chebi_input&&chebi_output) {
				output_types.removeAll(input_types);
				if(output_types.size()>0) {
					n_ca++;
					OWLClassAssertionAxiom ca = df.getOWLClassAssertionAxiom(CatalyticActivity, test_reaction);
					cas.add(ca);
					//System.out.println("Catalyzing "+Helper.getaLabel(test_reaction, goplus));
				}
			}			
		}
		System.out.println("guessing catalytic activity for "+n_ca+" reactions");
		man.addAxioms(goplus, cas);
		//rerun to build new inferred set
		goplus_reasoner.flush();
		n_reactions = 0; n_unclassified = 0; n_classified = 0;
		for(OWLNamedIndividual test_reaction : reactions) {
			n_reactions++;
			Set<OWLClass> types = goplus_reasoner.getTypes(test_reaction, true).getFlattened();
			//String reaction_label = Helper.getaLabel(test_reaction, goplus);
			boolean classified = false;
			reaction_goplusplus_classifications.put(test_reaction, types);
			for(OWLClass type : types) {
				if(!(type.equals(GoCAM.molecular_function)||
						type.equals(GoCAM.catalytic_activity))) {
					classified = true;
				}
			}
			if(classified) {
				n_classified++;
			}else {
				n_unclassified++;
			}
		}
		//remove mf and catalytic from everything for analysis
		int manually_classified = 0;
		//add term level statistics
		Map<OWLClass, Integer> manual_term_count = new HashMap<OWLClass, Integer>();
		Map<OWLClass, Integer> goplus_term_count = new HashMap<OWLClass, Integer>();
		Map<OWLClass, Integer> goplusplus_term_count = new HashMap<OWLClass, Integer>();
		for(OWLNamedIndividual reaction : reactions) {
			Set<OWLClass> manual = reaction_manual_classifications.get(reaction);
			manual.remove(GoCAM.molecular_function);
			manual.remove(GoCAM.catalytic_activity);
			reaction_manual_classifications.put(reaction, manual);
			if(manual.size()>0) {
				manually_classified++;
			}
			Set<OWLClass> goplus_auto = reaction_goplus_classifications.get(reaction);
			goplus_auto.remove(GoCAM.molecular_function);
			goplus_auto.remove(GoCAM.catalytic_activity);
			reaction_goplus_classifications.put(reaction, goplus_auto);
			Set<OWLClass> goplusplus_auto = reaction_goplusplus_classifications.get(reaction);
			goplusplus_auto.remove(GoCAM.molecular_function);
			goplusplus_auto.remove(GoCAM.catalytic_activity);
			reaction_goplusplus_classifications.put(reaction, goplusplus_auto);
			//add term level statistics
			for(OWLClass term : manual) {
				Integer manterm = manual_term_count.get(term);
				if(manterm==null) {
					manterm = 0;
				}
				manterm++;
				manual_term_count.put(term, manterm);
			}
			for(OWLClass term : goplus_auto) {
				Integer t = goplus_term_count.get(term);
				if(t==null) {
					t = 0;
				}
				t++;
				goplus_term_count.put(term, t);
			}
			for(OWLClass term : goplusplus_auto) {
				Integer t = goplusplus_term_count.get(term);
				if(t==null) {
					t = 0;
				}
				t++;
				goplusplus_term_count.put(term, t);
			}
		}

		System.out.println(defined_ontology+"\nWith new Catalytic Activity definitions\nN reactions:"+n_reactions+"\tn classified:"+n_classified+"\tn unclassified:"+n_unclassified+"\t%classified"+((float)n_classified/(float)n_reactions));
		System.out.println("Manual by Reactome: "+n_reactions+"\tn classified:"+manually_classified+"\tn unclassified:"+(n_reactions-manually_classified)+"\t%classified"+((float)manually_classified/(float)n_reactions));

		//now finish with report on reasoner user in the three states
		//		Map<OWLIndividual, Set<OWLClass>> reaction_manual_classifications = new HashMap<OWLIndividual, Set<OWLClass>>();
		//		Map<OWLIndividual, Set<OWLClass>> reaction_goplus_classifications = new HashMap<OWLIndividual, Set<OWLClass>>();
		//		Map<OWLIndividual, Set<OWLClass>> reaction_goplusplus_classifications = new HashMap<OWLIndividual, Set<OWLClass>>();
		String data_file = "/Users/bgood/Desktop/test/tmp/ELK_reactome_new_mfdef_types.txt";
		FileWriter d = new FileWriter(data_file);
		d.write("reaction_label\tgoplus!=goplusNewRheaDefs\tgoplusNewRheaDefs_recapitulate\tgoplusNewRheaDefs_new\tgoplus_recapitulate\tgoplus_new\tmanual\tgoplus\tgoplusNewRheaDefs\n");
		int total_recap = 0;  int total_new = 0; 
		int total_recap_plusplus = 0;  int total_new_plusplus = 0; 
		for(OWLIndividual reaction : reaction_manual_classifications.keySet()) {
			String reaction_label = Helper.getaLabel((OWLEntity) reaction, goplus);
			Set<OWLClass> manual = reaction_manual_classifications.get(reaction);
			Set<OWLClass> goplus_auto = reaction_goplus_classifications.get(reaction);
			Set<OWLClass> goplus_recapitulate = new HashSet<OWLClass>(manual);
			goplus_recapitulate.retainAll(goplus_auto);
			Set<OWLClass> goplus_new = new HashSet<OWLClass>(goplus_auto);
			goplus_new.removeAll(manual);
			Set<OWLClass> goplusplus_auto = reaction_goplusplus_classifications.get(reaction);
			Set<OWLClass> goplusplus_recapitulate = new HashSet<OWLClass>(manual);
			goplusplus_recapitulate.retainAll(goplusplus_auto);
			Set<OWLClass> goplusplus_new = new HashSet<OWLClass>(goplusplus_auto);
			goplusplus_new.removeAll(manual);
			Set<OWLClass> goplusplus_minus_goplus = new HashSet<OWLClass>(goplusplus_auto);
			goplusplus_minus_goplus.removeAll(goplus_auto);
			if(goplusplus_recapitulate.size()>0) {
				total_recap_plusplus++;
			}
			if(goplusplus_new.size()>0) {
				total_new_plusplus++;
			}
			if(goplus_recapitulate.size()>0) {
				total_recap++;
			}
			if(goplus_new.size()>0) {
				total_new++;
			}
			d.write(reaction_label+"\t"+
					goplusplus_minus_goplus.size()+"\t"+
					goplusplus_recapitulate.size()+"\t"+
					goplusplus_new.size()+"\t"+
					goplus_recapitulate.size()+"\t"+
					goplus_new.size()+"\t"+
					Helper.owlSetToString(manual, goplus, ";")+"\t"+
					Helper.owlSetToString(goplus_auto, goplus, ";")+"\t"+
					Helper.owlSetToString(goplusplus_auto, goplus, ";")+"\n");
		}
		System.out.println("goplus: total reactions with recapitulated classes:\t"+total_recap+"\twith new classes:\t"+total_new);
		System.out.println("goplusplus: total reactions with recapitulated classes:\t"+total_recap_plusplus+"\twith new classes:\t"+total_new_plusplus);
		d.close();
		//term usage report 
		Set<OWLClass> terms = new HashSet<OWLClass>();
		//		Map<OWLClass, Integer> manual_term_count = new HashMap<OWLClass, Integer>();
		//		Map<OWLClass, Integer> goplus_term_count = new HashMap<OWLClass, Integer>();
		//		Map<OWLClass, Integer> goplusplus_term_count = new HashMap<OWLClass, Integer>();
		terms.addAll(manual_term_count.keySet());
		terms.addAll(goplus_term_count.keySet());
		terms.addAll(goplusplus_term_count.keySet());
		String term_count_file = "/Users/bgood/Desktop/test/tmp/term_counts.txt";
		FileWriter f = new FileWriter(term_count_file);
		f.write("Term\tManual\tgo_plus\tgo_plus_RHEA\n");
		for(OWLClass term : terms) {
			Integer mi = manual_term_count.get(term);
			if(mi==null) {mi=0;}
			Integer gpi = goplus_term_count.get(term);
			if(gpi==null) {gpi=0;}
			Integer gppi = goplusplus_term_count.get(term);
			if(gppi==null) {gppi=0;}
			f.write(Helper.getaLabel(term, goplus)+"\t"+mi+"\t"+gpi+"\t"+gppi+"\n");
		}
		f.close();
	}

	public void testReactomeClassificationsWithArachne() throws OWLOntologyCreationException, IOException {
		String defined_ontology = "/Users/bgood/Desktop/test/tmp/GO_Ultra_GCI_test_new_chebi.ttl"; //GO_3classes_example.ttl"; //
		String reactome_dir = "/Users/bgood/reactome-go-cam-models/human/";
		String report_dir = "/Users/bgood/Desktop/test/tmp/inf_report/Ultra_GCI_new_chebi_";
		//load ontology into Arachne
		System.out.println("Loading "+defined_ontology+" into Arachne...");
		QRunner tbox_qrunner = go_cam.initializeQRunnerForTboxInference(Collections.singleton(defined_ontology));
		System.out.println("Finished loading into Arachne, starting on Reactome pathways");
		//iterate through the reactome pathways
		File dir = new File(reactome_dir);
		File[] directoryListing = dir.listFiles();
		GoMappingReport report = new GoMappingReport();
		if (directoryListing == null) {
			System.out.println("Bad input dir "+reactome_dir);
			System.exit(0);
		}
		int x = 0;
		for (File gocam_ttl : directoryListing) {
			if(!gocam_ttl.getName().endsWith(".ttl")) {
				continue;
			}
			x++;
			//			if(x>100) {
			//				break;
			//			}
			System.out.println(gocam_ttl.getName());
			GoCAM react_gocam = new GoCAM(gocam_ttl.getAbsolutePath());
			react_gocam.qrunner = new QRunner(react_gocam.go_cam_ont); 
			ClassificationReport before = react_gocam.getClassificationReport();		
			//don't want to reload tbox each time..
			boolean rebuild_tbox_with_go_cam_ont = false;
			//this will also rebuild the rdf version of the ontology, adding things it infers
			WorkingMemory wm = react_gocam.applyArachneInference(tbox_qrunner, rebuild_tbox_with_go_cam_ont);
			ClassificationReport after = react_gocam.getClassificationReport();
			ReasonerReport reasoner_report = new ReasonerReport(before, after);
			report.pathway_class_report.put(gocam_ttl.getName(), reasoner_report);
			//			boolean is_logical = react_gocam.validateGoCAM();	
			//			if(!is_logical) {
			//				report.inconsistent_models.add(gocam_ttl.getName());
			//			}
			if(reasoner_report.mf_new_class_count!=0||x%100==0) {
				System.out.println(x+" new inferred MFs count: "+reasoner_report.mf_new_class_count);
			}
			boolean skip_indirect = true;
			Map<String, Set<String>> inferred_types_by_uri = ArachneAccessor.getInferredTypes(wm, skip_indirect);
			Map<String, Set<String>> inferred_types = new HashMap<String, Set<String>>();
			//add labels
			for(String uri : inferred_types_by_uri.keySet()) {
				String u = uri.replace(">", "");
				u = u.replace("<", "");
				OWLEntity e = react_gocam.df.getOWLNamedIndividual(IRI.create(u));
				String label = react_gocam.getaLabel(e);
				label = label+"\t"+uri;
				inferred_types.put(label,inferred_types_by_uri.get(uri));
			}
			report.pathway_inferred_types.put(gocam_ttl.getName(), inferred_types);	

			//iterate through the reactions

			//identify reactions annotated to MF terms (= true positives)
		}
		report.writeReport(report_dir);

		//remove annotations to MF terms (apart from Catalytic Activity)

		//execute reasoner

		//record how many MF terms could be recapitulated through reasoning

		//record how many new MF annotations are generated 

	}


	public void buildOntologyDefinitions() throws OWLOntologyCreationException, IOException, OWLOntologyStorageException {
		String output_ontology = "/Users/bgood/Desktop/test/tmp/GO_Ultra_GCI_test.ttl";
		//String input_go_cam = "/Users/bgood/Desktop/test/tmp/converted-Degradation_of_AXIN.ttl";
		//GoCAM go_cam = new GoCAM(input_go_cam);		
		//OWLOntology newmfs = mfc.makeMFClassesFromGoCAM(input_go_cam);
		String existing_ontology = "/Users/bgood/git/noctua_exchange/exchange/src/main/resources/org/geneontology/gocam/exchange/go-plus-merged.owl";
		//chebi-full-extract-bot.owl";
		//go-plus-merged.owl";
		//to add to goplus do this
		MFCreator mfc = new MFCreator(existing_ontology);
		//to make a new one, do this
		//	MFCreator mfc = new MFCreator(null);
		RheaConverter rc = new RheaConverter();
		Map<String, rheaReaction> reactions = rc.getReactionsFromRDF();
		OWLOntology after = mfc.makeMFClassesFromRheaReactions(reactions);

		OWLOntologyManager man = OWLManager.createOWLOntologyManager();
		OWLOntology before = man.loadOntologyFromOntologyDocument(new File(existing_ontology));	
		boolean direct_only = true;
		//		//to see impact of chebi
		////		Set<OWLSubClassOfAxiom> new_sc_axioms_no_chebi = mfc.subClassAxiomDiff(before, after, direct_only);
		//		//poor mans import
		String chebi_subset = "/Users/bgood/git/noctua_exchange/exchange/src/main/resources/org/geneontology/gocam/exchange/chebi-full-extract-bot.owl";
		OWLOntology chebi_ont = man.loadOntologyFromOntologyDocument(new File(chebi_subset));	
		man.addAxioms(before, chebi_ont.getAxioms());
		man.addAxioms(after, chebi_ont.getAxioms());
		Set<OWLSubClassOfAxiom> new_sc_axioms = mfc.subClassAxiomDiff(before, after, direct_only);
		////		
		////		//to see impact of chebi
		////		new_sc_axioms.removeAll(new_sc_axioms_no_chebi);
		//		
		System.out.println(new_sc_axioms.size()+" new subclass relations: ");
		//		//ultra intersection
		//		//no chebi
		//		//406 including all indirects 
		//		//91 direct
		//		//with chebi 446 with indirects
		//		//126 direct with chebi
		//		//simple with chebi
		//		//534 new direct subclass relations
		FileWriter f = new FileWriter("/Users/bgood/Desktop/test/tmp/GO_Ultra_GCI_extract_report.txt");
		f.write("subclass\tsuperclass\tsc\n");
		for(OWLSubClassOfAxiom sc : new_sc_axioms) {
			String subclass = Helper.getaLabel(sc.getSubClass().asOWLClass(), after);
			String superclass = Helper.getaLabel(sc.getSuperClass().asOWLClass(), after);
			f.write(subclass+"\t"+superclass+"\t"+sc+"\n");
			//System.out.println(subclass+"\t"+superclass+"\t"+sc);
		}
		f.close();
		System.out.println("Writing ontology to "+output_ontology);
		Helper.writeOntology(output_ontology, after);
	}

	public Set<OWLClass> getGObyDbXref(String xref) {
		String q = "select ?c where "
				+ "{?c <http://www.geneontology.org/formats/oboInOwl#hasDbXref> ?xref "
				+ "filter (?xref = \""+xref+"\")" 
				+ "} ";
		QueryExecution qe = QueryExecutionFactory.create(q, go_jena);
		ResultSet results = qe.execSelect();
		String iri = "";
		Set<OWLClass> gos = new HashSet<OWLClass>();
		while (results.hasNext()) {
			String cl = results.next().getResource("c").getURI();
			if(cl!=null) {
				iri = cl;
				gos.add(go_cam.df.getOWLClass(IRI.create(iri)));			
			}
		}			
		return gos;
	}


	public OWLOntology makeMFClassesFromRheaReactions(Map<String, rheaReaction> reactions) throws OWLOntologyCreationException, IOException {
		OWLOntology mfc = go_cam.go_cam_ont;
		//capture terms used for limited import
		Map<String, Set<String>> ont_terms = new HashMap<String, Set<String>>();
		Set<String> t = new HashSet<String>(); t.add(CatalyticActivity.getIRI().toString());
		ont_terms.put("GO", t);
		Set<String> r = new HashSet<String>(); 
		r.add(has_input.getIRI().toString()); r.add(has_output.getIRI().toString());
		r.add(has_member.getIRI().toString()); r.add(has_participant.getIRI().toString());
		ont_terms.put("RO", r);

		OWLDataFactory df = mfc.getOWLOntologyManager().getOWLDataFactory();
		int i = 0; int n_saved = 0;
		Map<OWLClass, OWLAxiom> rmfs = new HashMap<OWLClass, OWLAxiom>();
		for(String reaction_id : reactions.keySet()) {
			rheaReaction reaction = reactions.get(reaction_id);
			i++;
			Set<OWLClass> mfs = null;
			//first check for match by rhea
			//GO uses the bidirectional version 
			String rhea = reaction.rhea_bidirectional_id;
			mfs = getGObyDbXref(rhea);
			rhea_go.put(rhea, mfs);
			OWLClass mf = null;
			if(mfs.size()==1) {
				mf = mfs.iterator().next();
				go_cam.addComment(mf, "linked to RHEA (and thus logical definition) via existing RHEA xref: "+rhea);
				System.out.println("rhea match "+rhea+" "+mf.getIRI());
			}else if(mfs.size()>1) {
				System.out.println(mfs.size()+" GO classes for "+rhea);
				System.exit(0);
			}
			//			if(mf==null) {
			//				//else check for match by ec number
			//				String EC = reaction.ec_number.replace("http://purl.uniprot.org/enzyme/", "EC:");
			//				mfs = getGObyDbXref(EC);
			//				rhea_ec_go.put(rhea+"\t"+EC, mfs);
			//				if(mfs.size()==1) {
			//					mf = mfs.iterator().next();
			//					System.out.println("EC match "+EC+" "+mf.getIRI());
			//					go_cam.addComment(mf, "linked to RHEA "+rhea+" (and thus logical definition) via shared EC: "+EC);
			//				}else if(mfs.size()>1) {
			//					System.out.println(mfs.size()+" GO classes for "+EC);
			//					//System.exit(0);
			//					//TODO could use additional cross references to e.g., metacyc for further alignment
			//				}
			//			}
			//			
			//			if(mf==null) {
			//				mf_iri = IRI.create(GoCAM.base_iri+"GO_howto_mint_newGO_id_"+i);			
			//				mf = df.getOWLClass(mf_iri);	
			//			}else {
			//				mf_iri = mf.getIRI();
			//			}
			//For now don't add any new ones or map to anything that wasn't directly asserted in a GO annotation
			if(mf==null) {
				continue;
			}
			IRI mf_iri = mf.getIRI();
			//						if(!(mf_iri.toString().equals("http://purl.obolibrary.org/obo/GO_0003978")||
			//								mf_iri.toString().equals("http://purl.obolibrary.org/obo/GO_0008108")||
			//								mf_iri.toString().equals("http://purl.obolibrary.org/obo/GO_0003824")||
			//								mf_iri.toString().equals("http://purl.obolibrary.org/obo/GO_0004659")||
			//								mf_iri.toString().equals("http://purl.obolibrary.org/obo/GO_0004452")||
			//								mf_iri.toString().equals("http://purl.obolibrary.org/obo/GO_0047863")			
			//								)) {
			//							continue;
			//						}
			Set<String> terms = ont_terms.get("GO");
			terms.add(mf_iri.toString());
			ont_terms.put("GO", terms);

			OWLAnnotation anno = go_cam.addLiteralAnnotations2Individual(mf_iri, GoCAM.definition,"Catalysis of RHEA reaction: "+reaction.equation);
			OWLAxiom defaxiom = df.getOWLAnnotationAssertionAxiom(mf_iri, anno);
			go_cam.ontman.addAxiom(go_cam.go_cam_ont, defaxiom);
			go_cam.addComment(mf, "Logical definition added programmatically from RHEA chemical equation. See discussion https://github.com/geneontology/go-ontology/issues/14984");
			if(reaction.containsGeneric) {
				go_cam.addComment(mf, "equation contains reference to generic, chebi term reference is to its active part");
			}
			if(reaction.containsPolymer) {
				go_cam.addComment(mf, "equation contains reference to polymer, chebi references underlying molecule");
			}


			Set<String> chebis = ont_terms.get("CHEBI");
			if(chebis==null) {
				chebis = new HashSet<String>();
			}
			chebis.addAll(reaction.left_bag_chebi_stoich.keySet());
			chebis.addAll(reaction.right_bag_chebi_stoich.keySet());
			ont_terms.put("CHEBI", chebis);

			//simple via input output		
			Set<OWLClassExpression> inputslr = new HashSet<OWLClassExpression>();
			Set<OWLClassExpression> outputs_rl = new HashSet<OWLClassExpression>();
			for(String chebi : reaction.left_bag_chebi_stoich.keySet()) {
				OWLClassExpression chemclass = df.getOWLClass(IRI.create(chebi));
				inputslr.add(go_cam.df.getOWLObjectSomeValuesFrom(has_input, chemclass));
				outputs_rl.add(go_cam.df.getOWLObjectSomeValuesFrom(has_output, chemclass));
			}		
			OWLClassExpression input_setlr = df.getOWLObjectIntersectionOf(inputslr);
			OWLClassExpression output_setrl = df.getOWLObjectIntersectionOf(outputs_rl);

			Set<OWLClassExpression> outputslr = new HashSet<OWLClassExpression>();
			Set<OWLClassExpression> inputs_rl = new HashSet<OWLClassExpression>();

			for(String chebi : reaction.right_bag_chebi_stoich.keySet()) {
				OWLClassExpression chemclass = df.getOWLClass(IRI.create(chebi));
				outputslr.add(go_cam.df.getOWLObjectSomeValuesFrom(has_output, chemclass));
				inputs_rl.add(go_cam.df.getOWLObjectSomeValuesFrom(has_input, chemclass));
			}
			OWLClassExpression output_setlr = df.getOWLObjectIntersectionOf(outputslr);
			OWLClassExpression input_setrl = df.getOWLObjectIntersectionOf(inputs_rl);

			//Simple, one way, equivalence 
			//				OWLAxiom def_lr = 
			//						df.getOWLEquivalentClassesAxiom(mf,
			//								df.getOWLObjectIntersectionOf(CatalyticActivity, input_setlr, output_setlr));
			//				rmfs.put(mf, def_lr);
			//				mfc.getOWLOntologyManager().addAxiom(mfc,def_lr);		

			//union for instance classification via anonymous subclasses
			OWLAxiom def_lr = 
					df.getOWLSubClassOfAxiom(df.getOWLObjectIntersectionOf(CatalyticActivity, input_setlr, output_setlr), mf);
			rmfs.put(mf, def_lr);
			mfc.getOWLOntologyManager().addAxiom(mfc,def_lr);			
			OWLAxiom def_rl = 
					df.getOWLSubClassOfAxiom(df.getOWLObjectIntersectionOf(CatalyticActivity, input_setrl, output_setrl), mf);
			rmfs.put(mf, def_rl);
			mfc.getOWLOntologyManager().addAxiom(mfc,def_rl);	



			//builds 'bags' of reactants for each side of the equation 
			Set<OWLClassExpression> inputs = new HashSet<OWLClassExpression>();
			for(String chebi : reaction.left_bag_chebi_stoich.keySet()) {
				String s = reaction.left_bag_chebi_stoich.get(chebi);
				OWLClassExpression chemclass = df.getOWLClass(IRI.create(chebi));
				OWLLiteral stoich = go_cam.df.getOWLLiteral(s); 
				OWLClassExpression chemandstoich = go_cam.df.getOWLObjectSomeValuesFrom(has_member, 
						go_cam.df.getOWLObjectIntersectionOf(chemclass, go_cam.df.getOWLDataHasValue(has_stoichiometry, stoich)));
				inputs.add(chemandstoich);
			}
			Set<OWLClassExpression> outputs = new HashSet<OWLClassExpression>();
			for(String chebi : reaction.right_bag_chebi_stoich.keySet()) {
				String s = reaction.right_bag_chebi_stoich.get(chebi);
				OWLClassExpression chemclass = df.getOWLClass(IRI.create(chebi));
				OWLLiteral stoich = go_cam.df.getOWLLiteral(s); 
				OWLClassExpression chemandstoich = go_cam.df.getOWLObjectSomeValuesFrom(has_member, 
						go_cam.df.getOWLObjectIntersectionOf(chemclass, go_cam.df.getOWLDataHasValue(has_stoichiometry, stoich)));
				outputs.add(chemandstoich);
			}
			OWLClassExpression inputbag = df.getOWLObjectIntersectionOf(inputs);
			OWLClassExpression outputbag = df.getOWLObjectIntersectionOf(outputs);			
			//Intersection version from Yevgeny Kazakov https://github.com/liveontologies/elk-reasoner/issues/54#issuecomment-398921969 
			OWLAxiom def = 
					df.getOWLEquivalentClassesAxiom(mf,
							df.getOWLObjectIntersectionOf(CatalyticActivity,
									df.getOWLObjectSomeValuesFrom(has_directed_reaction, df.getOWLObjectIntersectionOf(
											df.getOWLObjectIntersectionOf(df.getOWLObjectSomeValuesFrom(has_input, df.getOWLObjectIntersectionOf(SubstanceSet, inputbag))),
											df.getOWLObjectIntersectionOf(df.getOWLObjectSomeValuesFrom(has_output, df.getOWLObjectIntersectionOf(SubstanceSet, outputbag))))),
									df.getOWLObjectSomeValuesFrom(has_directed_reaction, df.getOWLObjectIntersectionOf(
											df.getOWLObjectIntersectionOf(df.getOWLObjectSomeValuesFrom(has_input, df.getOWLObjectIntersectionOf(SubstanceSet, outputbag))),
											df.getOWLObjectIntersectionOf(df.getOWLObjectSomeValuesFrom(has_output, df.getOWLObjectIntersectionOf(SubstanceSet, inputbag)))))					
									));
			rmfs.put(mf, def);
			mfc.getOWLOntologyManager().addAxiom(mfc,def);					


			//original - 2 bags
			//			OWLAxiom def = 
			//			df.getOWLEquivalentClassesAxiom(mf, 
			//					df.getOWLObjectIntersectionOf(CatalyticActivity, 
			//							df.getOWLObjectSomeValuesFrom(has_substance_bag, df.getOWLObjectIntersectionOf(SubstanceSet, inputbag)),
			//							df.getOWLObjectSomeValuesFrom(has_substance_bag, df.getOWLObjectIntersectionOf(SubstanceSet, outputbag))));
			//			rmfs.put(mf, def);
			//			mfc.getOWLOntologyManager().addAxiom(mfc,def);		

			//this logically works but ELK can't handle ObjectUnionOf			
			//			OWLAxiom union_def = 
			//					df.getOWLEquivalentClassesAxiom(mf, 
			//						df.getOWLObjectUnionOf(
			//							df.getOWLObjectIntersectionOf(CatalyticActivity, 
			//									df.getOWLObjectSomeValuesFrom(has_left_substance_bag, df.getOWLObjectIntersectionOf(SubstanceSet, inputbag)),
			//									df.getOWLObjectSomeValuesFrom(has_right_substance_bag, df.getOWLObjectIntersectionOf(SubstanceSet, outputbag)))
			//							,
			//							df.getOWLObjectIntersectionOf(CatalyticActivity, 
			//									df.getOWLObjectSomeValuesFrom(has_right_substance_bag, df.getOWLObjectIntersectionOf(SubstanceSet, inputbag)),
			//									df.getOWLObjectSomeValuesFrom(has_left_substance_bag, df.getOWLObjectIntersectionOf(SubstanceSet, outputbag)))
			//							));
			//			mfc.getOWLOntologyManager().addAxiom(mfc,union_def);	
			//			rmfs.put(mf, union_def);

			//lr reaction != rl reaction but lr and rl reactions = the mf class...			
			//disjoint union of not right but this how to do it..
			//			OWLClassExpression lr = df.getOWLObjectIntersectionOf(CatalyticActivity, 
			//					df.getOWLObjectSomeValuesFrom(has_left_substance_bag, df.getOWLObjectIntersectionOf(SubstanceSet, inputbag)),
			//					df.getOWLObjectSomeValuesFrom(has_right_substance_bag, df.getOWLObjectIntersectionOf(SubstanceSet, outputbag)));
			//			OWLClassExpression rl = df.getOWLObjectIntersectionOf(CatalyticActivity, 
			//					df.getOWLObjectSomeValuesFrom(has_right_substance_bag, df.getOWLObjectIntersectionOf(SubstanceSet, inputbag)),
			//					df.getOWLObjectSomeValuesFrom(has_left_substance_bag, df.getOWLObjectIntersectionOf(SubstanceSet, outputbag)));
			//			Set<OWLClassExpression> dj = new HashSet<OWLClassExpression>();
			//			dj.add(rl); dj.add(lr);
			//			OWLAxiom dj_union_def = df.getOWLDisjointUnionAxiom(mf, dj);
			//			rmfs.put(mf, dj_union_def);

			//This looks like it works but results in 3 classes for each MF term
			//		OWLClass leftright = df.getOWLClass(IRI.create(GoCAM.base_iri+"GO_howto_mint_newGO_id_"+Math.random()));
			//			OWLClass leftright = df.getOWLClass(IRI.create(mf.getIRI().toString()+"_LR"));
			//	//		Helper.addLabel(leftright.getIRI(), Helper.getaLabel(mf, mfc)+"_l2r", mfc);
			//			OWLAxiom lrs = df.getOWLSubClassOfAxiom(leftright, mf);
			//			mfc.getOWLOntologyManager().addAxiom(mfc, lrs);
			//			OWLAxiom lr_def = df.getOWLEquivalentClassesAxiom(leftright, 
			//							df.getOWLObjectIntersectionOf(CatalyticActivity, 
			//									df.getOWLObjectSomeValuesFrom(has_left_substance_bag, df.getOWLObjectIntersectionOf(SubstanceSet, inputbag)),
			//									df.getOWLObjectSomeValuesFrom(has_right_substance_bag, df.getOWLObjectIntersectionOf(SubstanceSet, outputbag))));
			//			mfc.getOWLOntologyManager().addAxiom(mfc, lr_def);
			//	//		OWLClass rightleft = df.getOWLClass(IRI.create(GoCAM.base_iri+"GO_howto_mint_newGO_id_"+Math.random()));
			//			OWLClass rightleft = df.getOWLClass(IRI.create(mf.getIRI().toString()+"_RL"));
			//	//		Helper.addLabel(rightleft.getIRI(), Helper.getaLabel(mf, mfc)+"_r2l", mfc);
			//			OWLAxiom rrs = df.getOWLSubClassOfAxiom(rightleft, mf);
			//			mfc.getOWLOntologyManager().addAxiom(mfc, rrs);
			//			OWLAxiom rr_def = df.getOWLEquivalentClassesAxiom(rightleft, 
			//					df.getOWLObjectIntersectionOf(CatalyticActivity, 
			//							df.getOWLObjectSomeValuesFrom(has_right_substance_bag, df.getOWLObjectIntersectionOf(SubstanceSet, inputbag)),
			//							df.getOWLObjectSomeValuesFrom(has_left_substance_bag, df.getOWLObjectIntersectionOf(SubstanceSet, outputbag))));
			//			mfc.getOWLOntologyManager().addAxiom(mfc,rr_def);			
			//			rmfs.put(leftright, lr_def);
			//			rmfs.put(rightleft, rr_def);

			n_saved++;
		}
		System.out.println("Added "+n_saved+" logical definitions");
		//	checkClassificationImpact(go_cam.go_cam_ont, rmfs);

		for(String ont : ont_terms.keySet()) {
			FileWriter f = new FileWriter("/Users/bgood/git/noctua_exchange/exchange/src/main/resources/org/geneontology/gocam/exchange/"+ont+"-terms.txt");
			for(String term : ont_terms.get(ont)) {
				f.write(term+"\n");
			}
			f.close();
		}
		return mfc;
	}

	public Set<OWLSubClassOfAxiom> subClassAxiomDiff(OWLOntology before, OWLOntology after, boolean direct_only) throws OWLOntologyCreationException {
		System.out.println("Doing subclass diff");
		OWLOntologyManager man = OWLManager.createOWLOntologyManager();
		OWLDataFactory df = man.getOWLDataFactory();
		OWLReasonerFactory reasonerFactory = new ElkReasonerFactory();
		OWLReasoner before_reasoner = reasonerFactory.createReasoner(before);
		OWLReasoner after_reasoner = reasonerFactory.createReasoner(after);
		Set<OWLClass> classes = before.getClassesInSignature();
		Set<OWLSubClassOfAxiom> newSubClassAxioms = new HashSet<OWLSubClassOfAxiom>();
		for(OWLClass c : classes) {
			Set<OWLClass> scbefore = before_reasoner.getSubClasses(c, direct_only).getFlattened();
			Set<OWLClass> scafter = after_reasoner.getSubClasses(c, direct_only).getFlattened();
			scafter.removeAll(scbefore);
			for(OWLClass c2 : scafter) {
				if(c2.getIRI().toString().contains("http://purl.obolibrary.org/obo/GO_")&&
						!c.getIRI().toString().equals(OWL.Thing.getURI())) {
					OWLSubClassOfAxiom new_sc = df.getOWLSubClassOfAxiom(c2, c);
					newSubClassAxioms.add(new_sc);
				}
			}
		}
		return newSubClassAxioms;
	}

	public void checkClassificationImpact(OWLOntology ont, Map<OWLClass, OWLAxiom> defined_classes) {
		OWLReasonerFactory reasonerFactory = new ElkReasonerFactory();
		OWLReasoner reasoner = reasonerFactory.createReasoner(ont);
		OWLOntologyManager mgr = ont.getOWLOntologyManager();
		/**
		 * Finds X asserted axioms that are also inferrable from logic
		 */
		int recapitulations = 0; int n = 0; int new_classifications = 0;
		//go through each class with a new definition axiom
		for(OWLClass mfc : defined_classes.keySet()) {
			n++;
			//full subclass set related to this class
			Set<OWLClass> superclass_set_with_axiom = reasoner.getSuperClasses(mfc, true).getFlattened();
			//now take out the axiom and reset reasoner
			OWLAxiom new_axiom = defined_classes.get(mfc);
			mgr.removeAxiom(ont, new_axiom);
			reasoner.flush();
			//get new subclass set 
			Set<OWLClass> superclass_set_without_axiom = reasoner.getSuperClasses(mfc, true).getFlattened();
			//any difference?
			Set<OWLClass> new_ones = new HashSet<OWLClass>(superclass_set_with_axiom);
			new_ones.removeAll(superclass_set_without_axiom);
			boolean all_supers_in_new_set = defined_classes.keySet().containsAll(new_ones);
			Set<String> new_ones_labels = new HashSet<String>();
			for(OWLClass c : new_ones) {
				new_ones_labels.add(Helper.getaLabel(c, ont));
			}
			if(new_ones.size()>0) {				
				if(!new_ones.toString().equals("[<http://purl.obolibrary.org/obo/GO_0003824>]")) {
					new_classifications++;
					System.out.println(n+";"+new_classifications+";"+mfc+";"+Helper.getaLabel(mfc, ont)+";"+all_supers_in_new_set+";"+new_ones.size()+";"+new_ones_labels+";"+new_ones);
				}
			}
			//put it back in case there are additional impacts on other classes
			mgr.addAxiom(ont, new_axiom);
			//			
			//			for (OWLSubClassOfAxiom ax : sc_axes) {
			//				//skip ones with anonymous superclasses 
			//				if (!ax.getSuperClass().isAnonymous()) {
			//					//get the superclass
			//					OWLClass supc = (OWLClass) ax.getSuperClass();
			//					//remove the current axiom
			//					mgr.removeAxiom(ont, ax);
			//					//make the reasoner update 
			//					reasoner.flush();
			//					//get any other direct or indirect superclasses of the current subclass
			//					NodeSet<OWLClass> ances = reasoner.getSuperClasses(ax.getSubClass(), false);
			//					//System.out.println(ax + " ANCS="+ancs);
			//					//check if the superclass connected via the removed assertion is still connected some other way					
			//					if (ances.containsEntity( supc)) {
			//						//now remove the new axiom added above to see if it is the one that resulted in the classification
			//						mgr.removeAxiom(ont, new_axiom);
			//						reasoner.flush();
			//						NodeSet<OWLClass> ancs = reasoner.getSuperClasses(ax.getSubClass(), false);
			//						//if ancestor classes no longer contain the super class, the new axiom caused the inference
			//						if(!ancs.containsEntity(supc)) {
			//							String direct = "indirect";
			//							//look it up to see if its direct or indirect 
			//							if (reasoner.getSuperClasses(ax.getSubClass(), true).containsEntity( supc)) {
			//								direct = "direct";
			//							}
			//							//report 
			//							recapitulations++;												
			//							System.out.println(n+"\t"+mfc+"\t"+recapitulations+"\t"+ax.getSubClass()+"\t"+ax.getSuperClass()+"\t"+direct);	
			//						}
			//						//put it back in case there are additional impacts on other classes
			//						mgr.addAxiom(ont, new_axiom);
			//					}
			//					// put them back
			//					mgr.addAxiom(ont, ax);
			//				}
			//			}
		}
	}


	public void makeFromGoCAM() throws OWLOntologyCreationException, OWLOntologyStorageException {
		String input_go_cam = "/Users/bgood/Desktop/test/tmp/converted-Degradation_of_AXIN.ttl";
		String output_ontology = "/Users/bgood/Desktop/test/tmp/newMFsFromRhea.ttl";
		GoCAM go_cam = new GoCAM(input_go_cam);		
		OWLOntology newmfs = makeMFClassesFromGoCAM(go_cam);
		RheaConverter rc = new RheaConverter();
		Helper.writeOntology(output_ontology, newmfs);
	}

	/**
	 * take the gocam abox representation of a reaction and make a class 
	 * @param gocam_mf
	 * @return
	 * @throws OWLOntologyCreationException 
	 */
	public OWLOntology makeMFClassesFromGoCAM(GoCAM go_cam) throws OWLOntologyCreationException {
		OWLOntology mfc = go_cam.ontman.createOntology();
		OWLDataFactory df = mfc.getOWLOntologyManager().getOWLDataFactory();
		Iterator<OWLIndividual> reactions = EntitySearcher.getIndividuals(GoCAM.molecular_function, go_cam.go_cam_ont).iterator();
		int i = 0;
		while(reactions.hasNext()) {
			i++;
			OWLIndividual reaction = reactions.next();
			//this is based on inputs and outputs
			//if there aren't any, skip
			Collection<OWLIndividual> inputs = EntitySearcher.getObjectPropertyValues(reaction, GoCAM.has_input, go_cam.go_cam_ont);
			Collection<OWLIndividual> outputs = EntitySearcher.getObjectPropertyValues(reaction, GoCAM.has_output, go_cam.go_cam_ont);
			if(inputs.size()==0||outputs.size()==0) {
				continue;
			}
			//if there are, make the def
			OWLClass newmf = go_cam.df.getOWLClass(IRI.create(GoCAM.base_iri+"newMF"+i));		
			OWLClassExpression inputbag = df.getOWLObjectIntersectionOf(getChemPartsFromGoCAM(go_cam, inputs));
			OWLClassExpression outputbag = df.getOWLObjectIntersectionOf(getChemPartsFromGoCAM(go_cam, outputs));
			OWLAxiom def = 
					df.getOWLEquivalentClassesAxiom(newmf, 
							df.getOWLObjectIntersectionOf(CatalyticActivity, 
									df.getOWLObjectSomeValuesFrom(has_input, df.getOWLObjectIntersectionOf(SubstanceSet, inputbag)),
									df.getOWLObjectSomeValuesFrom(has_output, df.getOWLObjectIntersectionOf(SubstanceSet, outputbag)))
							);
			mfc.getOWLOntologyManager().addAxiom(mfc, def);
		}
		return mfc;
	}

	public Set<OWLClassExpression> getChemPartsFromGoCAM (GoCAM go_cam, Collection<OWLIndividual> chempartslist){
		Set<OWLClassExpression> parts = new HashSet<OWLClassExpression>();
		for(OWLIndividual part : chempartslist) {
			Collection<OWLClassExpression> types = EntitySearcher.getTypes(part, go_cam.go_cam_ont);
			if(types==null||types.size()!=1) {
				System.out.println("error, molecule type in getChemParts "+types);
				System.exit(0);
			}
			OWLClassExpression chemclass = types.iterator().next();
			//TODO need to actually get the stoichiometry in there - either in original conversion to go_cam or change this to use biopax data import.  
			OWLLiteral stoich = go_cam.df.getOWLLiteral(1);
			OWLClassExpression chemstoich = makeStoichedChemExpression(go_cam, chemclass, stoich);
			parts.add(chemstoich);
		}
		return parts;
	}

	public OWLClassExpression makeStoichedChemExpression(GoCAM go_cam, OWLClassExpression chemclass, OWLLiteral stoich) {
		OWLClassExpression chemandstoich = go_cam.df.getOWLObjectSomeValuesFrom(has_member, 
				go_cam.df.getOWLObjectIntersectionOf(chemclass, go_cam.df.getOWLDataHasValue(has_stoichiometry, stoich)));
		return chemandstoich;
	}


}
