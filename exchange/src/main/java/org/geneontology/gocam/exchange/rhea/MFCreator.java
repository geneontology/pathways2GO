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
import org.geneontology.gocam.exchange.GoCAMReport;
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
	Map<String, rheaReaction> reactions;
	AxiomStrategy axiom_strategy;
	enum AxiomStrategy {
		Simple, //
		GCI_union, 
		UltraIntersection,   //
		Union;
	}
	Set<String> test_classes;
	String axioms_outfile;
	/**
	 * @throws OWLOntologyCreationException 
	 * 
	 */
	public MFCreator(String existing_ontology_to_add_to, String axioms_output_file, AxiomStrategy strategy, Set<String> itest_classes) throws OWLOntologyCreationException {
		if(existing_ontology_to_add_to==null) {
			this.go_cam = new GoCAM();
		}else {
			this.go_cam = new GoCAM(existing_ontology_to_add_to);
		}
		axioms_outfile = axioms_output_file;
		axiom_strategy = strategy;
		test_classes = itest_classes;
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
		reactions = loadRheaMapFromRDF();
	}

	public Map<String, rheaReaction> loadRheaMapFromRDF() {
		RheaConverter rc = new RheaConverter();
		return rc.getReactionsFromRDF();
	}

	/**
	 * @param args
	 * @throws OWLOntologyCreationException 
	 * @throws OWLOntologyStorageException 
	 * @throws IOException 
	 */
	public static void main(String[] args) throws OWLOntologyCreationException, OWLOntologyStorageException, IOException {
		String ontology_input_file = null; //add an ontology you want to modofy or leave null to make a new one
		String ontology_output_file = "/Users/bgood/Desktop/test/tmp/test_rhea_axioms_union.ttl";
		Set<String> test_classes = new HashSet<String>(); //leave null for all
		test_classes.add("http://purl.obolibrary.org/obo/GO_0003978");
		test_classes.add("http://purl.obolibrary.org/obo/GO_0008108");
		test_classes.add("http://purl.obolibrary.org/obo/GO_0003824");
		test_classes.add("http://purl.obolibrary.org/obo/GO_0004659");
		test_classes.add("http://purl.obolibrary.org/obo/GO_0004452");
		test_classes.add("http://purl.obolibrary.org/obo/GO_0047863");
		MFCreator m = new MFCreator(ontology_input_file, ontology_output_file, AxiomStrategy.Union, test_classes);		
		m.buildAndExportMFAxioms();
	}

	public void buildAndExportMFAxioms() throws OWLOntologyCreationException, IOException, OWLOntologyStorageException {
		OWLOntology mf_axioms = makeMFClassesFromRheaReactions(reactions);
		System.out.println("Writing ontology to "+axioms_outfile);
		Helper.writeOntology(axioms_outfile, mf_axioms);
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
			//just for testing 
			boolean skip = false;
			if(test_classes!=null) {
				if(!test_classes.contains(mf_iri.toString())) {
					skip = true;
				}
			}
			if(skip) {
				continue;
			}
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
			if(axiom_strategy == AxiomStrategy.Simple) {
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
				//just picks one direction and uses that.
				OWLAxiom def_lr = 
						df.getOWLEquivalentClassesAxiom(mf,
								df.getOWLObjectIntersectionOf(CatalyticActivity, input_setlr, output_setlr));
				rmfs.put(mf, def_lr);
				mfc.getOWLOntologyManager().addAxiom(mfc,def_lr);		
			}
			if(axiom_strategy == AxiomStrategy.GCI_union) {
				//General Class Inclusion Axioms  GCI
				//union for instance classification via anonymous subclasses
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
				
				OWLAxiom def_lr = 
						df.getOWLSubClassOfAxiom(df.getOWLObjectIntersectionOf(CatalyticActivity, input_setlr, output_setlr), mf);
				rmfs.put(mf, def_lr);
				mfc.getOWLOntologyManager().addAxiom(mfc,def_lr);			
				OWLAxiom def_rl = 
						df.getOWLSubClassOfAxiom(df.getOWLObjectIntersectionOf(CatalyticActivity, input_setrl, output_setrl), mf);
				rmfs.put(mf, def_rl);
				mfc.getOWLOntologyManager().addAxiom(mfc,def_rl);	
			} else if(axiom_strategy == AxiomStrategy.UltraIntersection) {


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
			}else if(axiom_strategy == AxiomStrategy.Union) {
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
				//mf is equivalent to CA and anything with one side of the substance set as an input and the other as an output OR vice versa
				//this logically works but ELK can't handle ObjectUnionOf			
				OWLAxiom union_def = 
						df.getOWLEquivalentClassesAxiom(mf, 
								df.getOWLObjectUnionOf(
										df.getOWLObjectIntersectionOf(CatalyticActivity, 
												df.getOWLObjectSomeValuesFrom(has_input, df.getOWLObjectIntersectionOf(SubstanceSet, inputbag)),
												df.getOWLObjectSomeValuesFrom(has_output, df.getOWLObjectIntersectionOf(SubstanceSet, outputbag)))
										,
										df.getOWLObjectIntersectionOf(CatalyticActivity, 
												df.getOWLObjectSomeValuesFrom(has_output, df.getOWLObjectIntersectionOf(SubstanceSet, inputbag)),
												df.getOWLObjectSomeValuesFrom(has_input, df.getOWLObjectIntersectionOf(SubstanceSet, outputbag)))
										));
				mfc.getOWLOntologyManager().addAxiom(mfc,union_def);	
				rmfs.put(mf, union_def);
			}
			n_saved++;
		}
		System.out.println("Added "+n_saved+" logical definitions");

		for(String ont : ont_terms.keySet()) {
			FileWriter f = new FileWriter("/Users/bgood/git/noctua_exchange/exchange/src/main/resources/org/geneontology/gocam/exchange/"+ont+"-terms.txt");
			for(String term : ont_terms.get(ont)) {
				f.write(term+"\n");
			}
			f.close();
		}
		return mfc;
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

	private void notes() {
		return;
		//Notes from failed attempts					
		//original - 2 bags
		//			OWLAxiom def = 
		//			df.getOWLEquivalentClassesAxiom(mf, 
		//					df.getOWLObjectIntersectionOf(CatalyticActivity, 
		//							df.getOWLObjectSomeValuesFrom(has_substance_bag, df.getOWLObjectIntersectionOf(SubstanceSet, inputbag)),
		//							df.getOWLObjectSomeValuesFrom(has_substance_bag, df.getOWLObjectIntersectionOf(SubstanceSet, outputbag))));
		//			rmfs.put(mf, def);
		//			mfc.getOWLOntologyManager().addAxiom(mfc,def);				
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
	}



}
