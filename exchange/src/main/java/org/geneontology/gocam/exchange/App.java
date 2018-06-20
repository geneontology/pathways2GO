package org.geneontology.gocam.exchange;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.jena.rdf.model.Statement;
import org.apache.jena.rdf.model.StmtIterator;
import org.apache.jena.vocabulary.RDFS;
import org.geneontology.rules.engine.Explanation;
import org.geneontology.rules.engine.Triple;
import org.openrdf.repository.RepositoryException;
import org.openrdf.rio.RDFHandlerException;
import org.openrdf.rio.RDFParseException;
import org.semanticweb.elk.owlapi.ElkReasonerFactory;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.formats.OBODocumentFormat;
import org.semanticweb.owlapi.formats.TurtleDocumentFormat;
import org.semanticweb.owlapi.io.FileDocumentTarget;
import org.semanticweb.owlapi.model.AxiomType;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLAnnotation;
import org.semanticweb.owlapi.model.OWLAnnotationAssertionAxiom;
import org.semanticweb.owlapi.model.OWLAnnotationAxiom;
import org.semanticweb.owlapi.model.OWLAnnotationProperty;
import org.semanticweb.owlapi.model.OWLAnnotationValue;
import org.semanticweb.owlapi.model.OWLAxiom;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLClassExpression;
import org.semanticweb.owlapi.model.OWLDataFactory;
import org.semanticweb.owlapi.model.OWLDataProperty;
import org.semanticweb.owlapi.model.OWLDocumentFormat;
import org.semanticweb.owlapi.model.OWLEntity;
import org.semanticweb.owlapi.model.OWLEquivalentClassesAxiom;
import org.semanticweb.owlapi.model.OWLIndividual;
import org.semanticweb.owlapi.model.OWLLiteral;
import org.semanticweb.owlapi.model.OWLNamedIndividual;
import org.semanticweb.owlapi.model.OWLObjectProperty;
import org.semanticweb.owlapi.model.OWLObjectPropertyAssertionAxiom;
import org.semanticweb.owlapi.model.OWLObjectPropertyAxiom;
import org.semanticweb.owlapi.model.OWLObjectSomeValuesFrom;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyCreationException;
import org.semanticweb.owlapi.model.OWLOntologyManager;
import org.semanticweb.owlapi.model.OWLOntologyStorageException;
import org.semanticweb.owlapi.model.OWLSubClassOfAxiom;
import org.semanticweb.owlapi.model.RemoveAxiom;
import org.semanticweb.owlapi.reasoner.NodeSet;
import org.semanticweb.owlapi.reasoner.OWLReasoner;
import org.semanticweb.owlapi.reasoner.OWLReasonerFactory;
import org.semanticweb.owlapi.reasoner.structural.StructuralReasonerFactory;
import org.semanticweb.owlapi.search.EntitySearcher;
import org.semanticweb.owlapi.util.OWLEntityRemover;
import org.semanticweb.owlapi.util.OWLOntologyWalker;
import org.semanticweb.owlapi.util.OWLOntologyWalkerVisitor;
import org.semanticweb.owlapi.util.OWLOntologyWalkerVisitorEx;
import org.semanticweb.owlapi.vocab.OWLRDFVocabulary;

/**
 * I live to test
 *
 */
public class App {
	//	String minimal_lego = "src/main/resources/org/geneontology/gocam/exchange/go-lego-trimmed.owl";
	//	String noneo_lego = "src/main/resources/org/geneontology/gocam/exchange/go-lego-noneo.owl";
	//	String maximal_lego = "src/main/resources/org/geneontology/gocam/exchange/go-lego-full.owl";	

	public static void main( String[] args ) throws OWLOntologyCreationException, OWLOntologyStorageException, RepositoryException, RDFParseException, RDFHandlerException, IOException {
		//		String ontf = "/Users/bgood/reactome-go-cam-models/human/reactome-homosapiens-A_tetrasaccharide_linker_sequence_is_required_for_GAG_synthesis.ttl";
		//		OWLOntologyManager man = OWLManager.createOWLOntologyManager();
		//		OWLOntology ont = man.loadOntologyFromOntologyDocument(new File(ontf));		
		//		ClassificationReport cr = new ClassificationReport(ont);
		//		System.out.println("bp "+cr.bp_count+" "+cr.bp_unclassified);
		//		System.out.println("mf "+cr.mf_count+" "+cr.mf_unclassified);
		//		System.out.println("cc "+cr.cc_count+" "+cr.cc_unclassified);
		//		System.out.println("complex "+cr.complex_count+" "+cr.complex_unclassified);

		updateReactomeXrefs();
	}

	public static void updateReactomeXrefs() throws OWLOntologyCreationException, IOException {
		String mapf = "src/main/resources/org/geneontology/gocam/exchange/StId_OldStId_Mapping_Human_Reactions_v65.txt";
		Map<String, String> old_new = new HashMap<String, String>();
		BufferedReader f = new BufferedReader(new FileReader(mapf));
		String line = f.readLine();
		line = f.readLine();//skip header
		while(line!=null) {
			String[] new_old_name_type = line.split("\t");
			old_new.put(new_old_name_type[1], new_old_name_type[0]);
			line = f.readLine();
		}
		f.close();
		String ontf = "src/main/resources/org/geneontology/gocam/exchange/go.owl";//-edit.obo";
		//"/Users/bgood/git/noctua_exchange/exchange/src/main/resources/org/geneontology/gocam/exchange/go-plus-merged.owl";
		OWLOntologyManager mgr = OWLManager.createOWLOntologyManager();
		OWLDataFactory df = mgr.getOWLDataFactory();
		OWLOntology ont = mgr.loadOntologyFromOntologyDocument(new File(ontf));
		Set<OWLClass> classes = ont.getClassesInSignature();
		OWLAnnotationProperty xref = df.getOWLAnnotationProperty(IRI.create("http://www.geneontology.org/formats/oboInOwl#hasDbXref"));
		OWLAnnotationProperty rdfslabel = df.getOWLAnnotationProperty(OWLRDFVocabulary.RDFS_LABEL.getIRI());
		for(OWLClass c : classes) {
			Collection<OWLAnnotationAssertionAxiom> aaa = EntitySearcher.getAnnotationAssertionAxioms(c, ont);
			for(OWLAnnotationAssertionAxiom a : aaa) {
				OWLAnnotation anno = a.getAnnotation();
				if(anno.getProperty().equals(xref)) {
					OWLAnnotationValue v = anno.getValue();
					String xref_id = v.asLiteral().get().getLiteral();
					Collection<OWLAnnotation> anno_annos = a.getAnnotations(rdfslabel);
					if(xref_id.contains("REACT_")) {
						for(OWLAnnotation anno_anno : anno_annos) {
							if(anno_anno.getProperty().equals(rdfslabel)) {
								OWLAnnotationValue vv = anno_anno.getValue();
								String xref_label = vv.asLiteral().get().getLiteral();
								System.out.println(c+" "+xref_id+" "+xref_label);
							}
						}
					}
				}
			}
		}
	}
	public static void demoReasoner() throws OWLOntologyCreationException {
		String ontf = "/Users/bgood/Desktop/test/tmp/GoPlusPlusRhea.ttl";
		//"/Users/bgood/git/noctua_exchange/exchange/src/main/resources/org/geneontology/gocam/exchange/go-plus-merged.owl";
		OWLOntologyManager mgr = OWLManager.createOWLOntologyManager();
		OWLDataFactory df = mgr.getOWLDataFactory();
		OWLOntology ont = mgr.loadOntologyFromOntologyDocument(new File(ontf));

		OWLReasonerFactory reasonerFactory = new ElkReasonerFactory();
		OWLReasoner reasoner = reasonerFactory.createReasoner(ont);
		/**
		 * Finds X asserted axioms that are also inferrable from:
		 * GO: 26144 
		 * GOPlus: 61629
		 * GOPlusRheaViaRheaXref: 61655
		 */
		int n_redundant = 0;
		//get all subclass axioms in ontology
		for (OWLSubClassOfAxiom ax : ont.getAxioms(AxiomType.SUBCLASS_OF)) {
			//skip ones with anonymous superclasses 
			if (!ax.getSuperClass().isAnonymous()) {
				//get the superclass
				OWLClass supc = (OWLClass) ax.getSuperClass();
				//remove the current axiom
				mgr.removeAxiom(ont, ax);
				//make the reasoner update 
				reasoner.flush();
				//get any other direct or indirect superclasses of the current subclass
				NodeSet<OWLClass> ancs = reasoner.getSuperClasses(ax.getSubClass(), false);
				//System.out.println(ax + " ANCS="+ancs);
				//check if the superclass connected via the removed assertion is still connected some other way
				if (ancs.containsEntity( supc)) {
					String direct = "indirect";
					//look it up to see if its direct or indirect 
					if (reasoner.getSuperClasses(ax.getSubClass(), true).containsEntity( supc)) {
						direct = "direct";
					}
					//report 
					n_redundant++;
					System.out.println(n_redundant+" SCA = "+ax+" D="+direct);
				}
				else {
					// put it back
					mgr.addAxiom(ont, ax);
				}
			}
		}
	}


	public static GoCAM loadGoCAM(String filename) throws OWLOntologyCreationException {
		return new GoCAM(filename);
	}

	public static void testBuildMFDef() throws OWLOntologyCreationException, OWLOntologyStorageException {
		GoCAM go_cam = new GoCAM(IRI.create("http://test133"), " ", " ", " ", " ", false);
		String root = "http://www.semanticweb.org/bgood/ontologies/2018/4/untitled-ontology-147#";
		OWLClass SubstanceSet = go_cam.df.getOWLClass(IRI.create(root+"SubstanceSet"));
		OWLClass CatalyticActivity = go_cam.df.getOWLClass(IRI.create("http://purl.obolibrary.org/obo/GO_0003824"));
		OWLObjectProperty has_substance_bag = go_cam.df.getOWLObjectProperty(IRI.create(root+"has_substance_bag")); 
		OWLObjectProperty has_member_part  = go_cam.df.getOWLObjectProperty(IRI.create(root+"has_member_part")); 
		OWLDataProperty has_stoichiometry  = go_cam.df.getOWLDataProperty(IRI.create(root+"has_stoichiometry")); 

		OWLOntology mfc = go_cam.ontman.createOntology();
		OWLDataFactory df = mfc.getOWLOntologyManager().getOWLDataFactory();

		OWLClass newmf = df.getOWLClass(IRI.create(root+"newmfterm"));
		OWLClass chemthing1 = df.getOWLClass(IRI.create(root+"water"));
		OWLClass chemthing2 = df.getOWLClass(IRI.create(root+"molecule2"));
		OWLClass chemthing3 = df.getOWLClass(IRI.create(root+"molecule3"));
		OWLLiteral stoich1 = df.getOWLLiteral(1);
		Set<OWLClassExpression> parts = new HashSet<OWLClassExpression>();

		OWLClassExpression chemandstoich1 = df.getOWLObjectSomeValuesFrom(has_member_part, 
				df.getOWLObjectIntersectionOf(chemthing1, df.getOWLDataHasValue(has_stoichiometry, stoich1)));
		parts.add(chemandstoich1);

		OWLClassExpression chemandstoich2 = df.getOWLObjectSomeValuesFrom(has_member_part, 
				df.getOWLObjectIntersectionOf(chemthing2, df.getOWLDataHasValue(has_stoichiometry, stoich1)));
		parts.add(chemandstoich2);

		Set<OWLClassExpression> parts2 = new HashSet<OWLClassExpression>();

		OWLClassExpression chemandstoich3 = df.getOWLObjectSomeValuesFrom(has_member_part, 
				df.getOWLObjectIntersectionOf(chemthing3, df.getOWLDataHasValue(has_stoichiometry, stoich1)));
		parts2.add(chemandstoich3);

		OWLClassExpression bag1 = df.getOWLObjectIntersectionOf(parts);
		OWLClassExpression bag2 = df.getOWLObjectIntersectionOf(parts2);

		OWLAxiom def = 
				df.getOWLEquivalentClassesAxiom(newmf, 
						df.getOWLObjectIntersectionOf(CatalyticActivity, 
								df.getOWLObjectSomeValuesFrom(has_substance_bag, df.getOWLObjectIntersectionOf(SubstanceSet, bag1)),
								df.getOWLObjectSomeValuesFrom(has_substance_bag, df.getOWLObjectIntersectionOf(SubstanceSet, bag2)))
						);
		mfc.getOWLOntologyManager().addAxiom(mfc, def);
		writeOntology("/Users/bgood/Desktop/test.owl", mfc);

	}

	public static void testLoadTime() throws OWLOntologyCreationException {
		String ontf = "/Users/bgood/gocam_input/neo.owl";
		long t0 = System.currentTimeMillis();
		OWLOntologyManager man = OWLManager.createOWLOntologyManager();
		OWLDataFactory df = man.getOWLDataFactory();
		System.out.println("loading");
		OWLOntology ont = man.loadOntologyFromOntologyDocument(new File(ontf));
		long t1 = System.currentTimeMillis();
		System.out.println("loaded in "+(t1-t0)/1000+" seconds with n axioms = "+ont.getAxiomCount());
		OWLClass test = df.getOWLClass(IRI.create(("http://identifiers.org/uniprot/Q16774")));
		//4.5gb 66 seconds
		OWLReasonerFactory reasonerFactory = new StructuralReasonerFactory();
		OWLReasoner r = reasonerFactory.createReasoner(ont);
		//5.12gb took 18 more seconds
		long t3 = System.currentTimeMillis();
		System.out.println("reasoner loaded in "+(t3-t1)/1000+" seconds");
	}


	public static void testUpdateAnnotations() throws OWLOntologyCreationException {
		String ontf = "/Users/bgood/Desktop/test/bmp_output/converted-bmp-Signaling_by_BMP.ttl";
		OWLOntologyManager man = OWLManager.createOWLOntologyManager();
		OWLDataFactory df = man.getOWLDataFactory();
		OWLOntology ont = man.loadOntologyFromOntologyDocument(new File(ontf));
		IRI source_iri = IRI.create("http://www.reactome.org/biopax/63/201451#BiochemicalReaction9");
		IRI prop_iri = IRI.create("http://purl.obolibrary.org/obo/RO_0002333");
		IRI target_iri = IRI.create("http://www.reactome.org/biopax/63/201451#Protein29-842491573");
		//get any existing axioms and any annotations for said triple		
		System.out.println(ont.getAxiomCount());
		OWLOntologyWalker walker = new OWLOntologyWalker(Collections.singleton(ont));
		UpdateAnnotationsVisitor updater = new UpdateAnnotationsVisitor(walker, source_iri, prop_iri, target_iri);
		walker.walkStructure(updater); 
		//now ready to update by deleting and then creating again...
		man.removeAxioms(ont, updater.getAxioms());
		System.out.println(ont.getAxiomCount());
		Set<OWLAnnotation> annos = updater.getAnnotations();
		//add new ones now..
		//		OWLAnnotation title_anno = df.getOWLAnnotation(title_prop, df.getOWLLiteral(gocam_title));
		OWLAnnotationProperty comment = df.getOWLAnnotationProperty(OWLRDFVocabulary.RDFS_COMMENT.getIRI());
		OWLAnnotation comment1 = df.getOWLAnnotation(comment, df.getOWLLiteral("Yay I did it"));	
		annos.add(comment1);
		//and add the axiom back in
		OWLObjectPropertyAssertionAxiom back = df.getOWLObjectPropertyAssertionAxiom(df.getOWLObjectProperty(prop_iri), df.getOWLNamedIndividual(source_iri), df.getOWLNamedIndividual(target_iri), annos);
		man.addAxiom(ont, back);
		System.out.println(ont.getAxiomCount());
	}

	public static void buildSparqlable() throws OWLOntologyCreationException, OWLOntologyStorageException, FileNotFoundException{
		String input_folder = "/Users/bgood/reactome-go-cam-models/humantest/";
		String output_folder = "/Users/bgood/reactome-go-cam-models/humantest_reasoned/";
		String tbox_file = "src/main/resources/org/geneontology/gocam/exchange/ro-merged.owl";
		OWLOntologyManager tman = OWLManager.createOWLOntologyManager();
		OWLOntology tbox = tman.loadOntologyFromOntologyDocument(new File(tbox_file));	
		ArachneAccessor a = new ArachneAccessor(Collections.singleton(tbox));
		boolean add_property_definitions = false;
		boolean add_class_definitions = false;
		a.reasonAllInFolder(input_folder, output_folder, add_property_definitions, add_class_definitions);
	}

	public static void queryCollection() throws OWLOntologyCreationException, OWLOntologyStorageException, IOException {
		String input_folder = "/Users/bgood/reactome-go-cam-models/humantest/";
		OWLOntology abox = ArachneAccessor.makeOneOntologyFromDirectory(input_folder);
		//prepare tbox
		String tbox_file = "src/main//resources/org/geneontology/gocam/exchange/ro-merged.owl";
		OWLOntologyManager tman = OWLManager.createOWLOntologyManager();
		OWLOntology tbox = tman.loadOntologyFromOntologyDocument(new File(tbox_file));	
		//test inference
		boolean add_inferences = false;
		boolean add_property_definitions = true;
		boolean add_class_definitions = false;
		QRunner q = testInference(abox, tbox, add_inferences, add_property_definitions, add_class_definitions);
		q.dumpModel("/Users/bgood/reactome-go-cam-models/all_human_no_inference.ttl", "TURTLE");
	}

	public static void testInference(String abox_file, String tbox_file) throws OWLOntologyCreationException, OWLOntologyStorageException, FileNotFoundException {
		//prepare an abox (taken from Arachne test case)
		// https://github.com/balhoff/arachne/tree/master/src/test/resources/org/geneontology/rules
		OWLOntologyManager aman = OWLManager.createOWLOntologyManager();
		OWLOntology abox = aman.loadOntologyFromOntologyDocument(new File(abox_file));	

		//prepare tbox
		//String tbox_file = "src/main/resources/org/geneontology/gocam/exchange/ro-merged.owl";
		OWLOntologyManager tman = OWLManager.createOWLOntologyManager();
		System.out.println("Loading tbox ontology ");
		OWLOntology tbox = tman.loadOntologyFromOntologyDocument(new File(tbox_file));	
		System.out.println("Building arachne for inference ");

		boolean add_inferences = true;
		boolean add_property_definitions = true;
		boolean add_class_definitions = true;
		QRunner inf = testInference(abox, tbox, add_inferences, add_property_definitions, add_class_definitions);
	}

	//TODO Maybe someday unit tests..  
	public static QRunner testInference(OWLOntology abox, OWLOntology tbox, 
			boolean add_inferences, boolean add_property_definitions, boolean add_class_definitions)  throws OWLOntologyCreationException, OWLOntologyStorageException, FileNotFoundException {
		//Test reading, reasoning, query

		//build the graph
		QRunner q = new QRunner(Collections.singleton(tbox), abox, add_inferences, add_property_definitions, add_class_definitions);
		//ask it questions
		boolean c = q.isConsistent();
		System.out.println("Is it consistent? "+c);
		if(!c) {
			Set<String> uns = q.getUnreasonableEntities();
			System.out.println("Entities that equal owl:Nothing");
			for(String u : uns) {
				System.out.println(u);
			}
		}
		//how big is it?
		int n = q.nTriples();
		System.out.println("N triples "+n); 
		//how many inferred triples? (assuming inference on)
		if(add_inferences) {
			System.out.println("inferred "+(q.wm.facts().size()-q.wm.asserted().size()));
			System.out.println("All "+q.wm.facts().size());
			scala.collection.Iterator<Triple> triples = q.wm.facts().toList().iterator();
			while(triples.hasNext()) {				
				Triple triple = triples.next();
				if(q.wm.asserted().contains(triple)) {
					continue;
				}else { //<http://arachne.geneontology.org/indirect_type>
					if(triple.p().toString().equals("<http://www.w3.org/1999/02/22-rdf-syntax-ns#type>")) {
						System.out.println("inferred "+triple.s()+" "+triple.p()+" "+triple.o());
						scala.collection.immutable.Set<Explanation> explanations = q.wm.explain(triple);
						scala.collection.Iterator<Explanation> e = explanations.iterator();
						while(e.hasNext()) {
							Explanation exp = e.next();
							System.out.println(exp.toString());
						}
					}
				}
			}
		}

		//57c82fad00000639.ttl + ro-merged.owl no inference = 6630 triples
		//57c82fad00000639.ttl + ro-merged.owl with inference = 2852 triples, including 282 inferred
		//57c82fad00000639.ttl + ro-merged.owl with inference, without indirectRules = 2834 triples, including 264 inferred		
		//57c82fad00000639.ttl + ro-merged.owl with inference, without triples from tbox = 629 triples, including 282 inferred
		//57c82fad00000639.ttl + ro-merged.owl with inference, without indirectRules, without triples from tbox = 611 triples, including 264 inferred
		//test says arachneInferredTriples.size shouldEqual 611  
		//arachneInferredTriples = wm.facts
		return q;
	}

	OWLNamedIndividual addComplexAsSimpleClass(GoCAM go_cam, Set<String> component_names, IRI complex_instance_iri, Set<OWLAnnotation> annotations) {
		String combo_name = GoCAM.base_iri;
		for(String n : component_names) {
			combo_name=combo_name+"_"+n;
		}
		OWLClass complex_class = go_cam.df.getOWLClass(IRI.create(combo_name));
		go_cam.addSubclassAssertion(complex_class, GoCAM.go_complex, annotations);
		//complex instance
		OWLNamedIndividual complex_i = go_cam.makeAnnotatedIndividual(complex_instance_iri);
		go_cam.addTypeAssertion(complex_i, complex_class);
		return complex_i;
	}


	OWLNamedIndividual addComplexAsLogicalClass(GoCAM go_cam, Set<IRI> component_iris, IRI complex_instance_iri, Set<OWLAnnotation> annotations) {
		String combo_name = GoCAM.base_iri;
		for(IRI component_iri : component_iris) {
			combo_name=combo_name+"_"+component_iri.getShortForm();
		}
		OWLClass complex_class = go_cam.df.getOWLClass(IRI.create(combo_name));
		//could be inferred if we added an if has_protein_parts axiom to parent, but not our ontology..
		go_cam.addSubclassAssertion(complex_class, GoCAM.go_complex, annotations);
		//parts list as class expressions and individuals 
		Set<OWLClassExpression> part_classes = new HashSet<OWLClassExpression>();
		Set<OWLNamedIndividual> part_is = new HashSet<OWLNamedIndividual>();
		for(IRI component_iri : component_iris) {
			//add or get the class
			OWLClass protein_class = go_cam.df.getOWLClass(component_iri);		
			OWLClassExpression hasPartPclass = go_cam.df.getOWLObjectSomeValuesFrom(GoCAM.has_part, protein_class);
			part_classes.add(hasPartPclass);
			//make the instance
			OWLNamedIndividual prot_part_entity = go_cam.makeAnnotatedIndividual(component_iri);
			go_cam.addTypeAssertion(prot_part_entity, protein_class);
			part_is.add(prot_part_entity);
		}
		//build intersection class 
		OWLClassExpression complex_def = go_cam.df.getOWLObjectIntersectionOf(part_classes);
		OWLEquivalentClassesAxiom eq = go_cam.df.getOWLEquivalentClassesAxiom(complex_class, complex_def);
		go_cam.ontman.addAxiom(go_cam.go_cam_ont, eq);
		//complex instance
		OWLNamedIndividual complex_i = go_cam.makeAnnotatedIndividual(complex_instance_iri);
		for(OWLNamedIndividual i : part_is) {
			go_cam.addObjectPropertyAssertion(complex_i, GoCAM.has_part, i, annotations);
		}
		//this could be inferred based on definition above, but since we know right now no need to run reasoner
		go_cam.addTypeAssertion(complex_i, complex_class);
		return complex_i;
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

	public static void writeOntology(String outfile, OWLOntology ont) throws OWLOntologyStorageException {
		FileDocumentTarget outf = new FileDocumentTarget(new File(outfile));
		//ontman.setOntologyFormat(go_cam_ont, new TurtleOntologyFormat());	
		ont.getOWLOntologyManager().setOntologyFormat(ont, new TurtleDocumentFormat());	
		ont.getOWLOntologyManager().saveOntology(ont,outf);
	}

	public static void writeOntologyAsObo(String outfile, OWLOntology ont) throws OWLOntologyStorageException {
		FileDocumentTarget outf = new FileDocumentTarget(new File(outfile));
		//ontman.setOntologyFormat(go_cam_ont, new TurtleOntologyFormat());	
		ont.getOWLOntologyManager().setOntologyFormat(ont, new OBODocumentFormat());	
		ont.getOWLOntologyManager().saveOntology(ont,outf);
	}
}
