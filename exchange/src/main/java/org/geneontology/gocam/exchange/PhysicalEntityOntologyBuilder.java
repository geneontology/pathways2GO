/**
 * 
 */
package org.geneontology.gocam.exchange;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.apache.commons.lang.StringUtils;
import org.apache.jena.vocabulary.RDFS;
import org.biopax.paxtools.io.BioPAXIOHandler;
import org.biopax.paxtools.io.SimpleIOHandler;
import org.biopax.paxtools.model.Model;
import org.biopax.paxtools.model.level3.CellularLocationVocabulary;
import org.biopax.paxtools.model.level3.Complex;
import org.biopax.paxtools.model.level3.Dna;
import org.biopax.paxtools.model.level3.DnaRegion;
import org.biopax.paxtools.model.level3.Entity;
import org.biopax.paxtools.model.level3.EntityFeature;
import org.biopax.paxtools.model.level3.EntityReference;
import org.biopax.paxtools.model.level3.FragmentFeature;
import org.biopax.paxtools.model.level3.ModificationFeature;
import org.biopax.paxtools.model.level3.Pathway;
import org.biopax.paxtools.model.level3.PhysicalEntity;
import org.biopax.paxtools.model.level3.Protein;
import org.biopax.paxtools.model.level3.Rna;
import org.biopax.paxtools.model.level3.RnaRegion;
import org.biopax.paxtools.model.level3.SequenceInterval;
import org.biopax.paxtools.model.level3.SequenceLocation;
import org.biopax.paxtools.model.level3.SequenceModificationVocabulary;
import org.biopax.paxtools.model.level3.SequenceRegionVocabulary;
import org.biopax.paxtools.model.level3.SequenceSite;
import org.biopax.paxtools.model.level3.SmallMolecule;
import org.biopax.paxtools.model.level3.Stoichiometry;
import org.biopax.paxtools.model.level3.UnificationXref;
import org.biopax.paxtools.model.level3.XReferrable;
import org.biopax.paxtools.model.level3.Xref;
import org.geneontology.gocam.exchange.BioPaxtoGO.ImportStrategy;
import org.geneontology.gocam.exchange.idmapping.IdMapper;
import org.openrdf.repository.RepositoryException;
import org.openrdf.rio.RDFHandlerException;
import org.openrdf.rio.RDFParseException;
import org.semanticweb.HermiT.ReasonerFactory;
import org.semanticweb.elk.owlapi.ElkReasonerFactory;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.AddImport;
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
import org.semanticweb.owlapi.model.OWLImportsDeclaration;
import org.semanticweb.owlapi.model.OWLLiteral;
import org.semanticweb.owlapi.model.OWLNamedIndividual;
import org.semanticweb.owlapi.model.OWLObjectIntersectionOf;
import org.semanticweb.owlapi.model.OWLObjectUnionOf;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyCreationException;
import org.semanticweb.owlapi.model.OWLOntologyManager;
import org.semanticweb.owlapi.model.OWLOntologyStorageException;
import org.semanticweb.owlapi.model.OWLSubClassOfAxiom;
import org.semanticweb.owlapi.reasoner.InferenceType;
import org.semanticweb.owlapi.reasoner.OWLReasoner;
import org.semanticweb.owlapi.reasoner.OWLReasonerFactory;
import org.semanticweb.owlapi.reasoner.structural.StructuralReasonerFactory;
import org.semanticweb.owlapi.search.EntitySearcher;
import org.semanticweb.owlapi.util.InferredAxiomGenerator;
import org.semanticweb.owlapi.util.InferredEquivalentClassAxiomGenerator;
import org.semanticweb.owlapi.util.InferredOntologyGenerator;
import org.semanticweb.owlapi.util.InferredSubClassAxiomGenerator;


/**
 * Handle the conversion of physical entities proteins, protein complexes, sets, etc. from BioPAX files
 * into an ontology of physical entities suitable for loading into Noctua alongside Neo.  
 * @author bgood
 *
 */
public class PhysicalEntityOntologyBuilder {

	final static boolean capture_location = true; 
	final static boolean capture_taxon = true;
	final static boolean capture_complex_stoichiometry = true;
	final static boolean add_pro_logical_connections = false;
	final static boolean add_imports = false;
	GOLego golego;
	String default_namespace_prefix;
	String base_extra_info;
	Map<String, OWLClassExpression> id_class_map;
	ReasonerImplementation reasoner;
	enum ReasonerImplementation {
		Elk, Hermit, none 
	}
	Map<String, Set<String>> pro_exact_map; 
	Map<String, Set<String>> pro_isa_map;
	/**
	 * @throws IOException 
	 * 
	 */
	public PhysicalEntityOntologyBuilder(GOLego go_lego, String default_namespace_prefix_, String base_extra_info_, ReasonerImplementation reasoner_, String pro_mapping_file) throws IOException {
		golego = go_lego;
		default_namespace_prefix = default_namespace_prefix_;
		base_extra_info = base_extra_info_;
		id_class_map = new HashMap<String, OWLClassExpression>();
		reasoner = reasoner_;
		//get mappings to PRO on hand
		pro_exact_map = PRO.readReact2PRO(pro_mapping_file, "exact");
		pro_isa_map = PRO.readReact2PRO(pro_mapping_file, "is_a");
	}

	/**
	 * @param args
	 * @throws OWLOntologyCreationException 
	 * @throws IOException 
	 * @throws RDFHandlerException 
	 * @throws RDFParseException 
	 * @throws RepositoryException 
	 * @throws OWLOntologyStorageException 
	 */
	public static void main(String[] args) throws OWLOntologyCreationException, IOException, OWLOntologyStorageException, RepositoryException, RDFParseException, RDFHandlerException {
		String pro_mapping = "/Users/benjamingood/gocam_ontology/REO/promapping.txt";
		String input_biopax = 
				"/Users/benjamingood/test/biopax/curator-Jan8-2020-Homo-sapiens.owl";

		String outputformat = "RDFXML";
		String outfilename = "/Users/benjamingood/gocam_ontology/REACTO";


		String base_ont_title = "Reactome Entity Ontology (REACTO)";//"SignalingByERBB2_Physical_Entities"; //"Reactome_physical_entities";
		String base_extra_info = "https://reactome.org/content/detail/";
		String base_short_namespace = "Reactome";
		ReasonerImplementation r = ReasonerImplementation.Elk;//ReasonerImplementation.Hermit; //

		BioPAXIOHandler handler = new SimpleIOHandler();
		FileInputStream f = new FileInputStream(input_biopax);
		Model biopax_model = handler.convertFromOWL(f);

		countPhysical(biopax_model);

		String biopax_build_id = biopax_model.getXmlBase();
		String base_contributor = "https://orcid.org/0000-0002-7334-7852";
		String base_provider = "https://reactome.org";
		boolean add_lego_import = false;
		int n = 0;
		String ont_uri = "http://purl.obolibrary.org/obo/go/extensions/reacto.owl";
				//"https://github.com/geneontology/pathways2GO/raw/master/exchange/generated/plant-REO.owl";
				//"https://github.com/geneontology/pathways2GO/raw/master/exchange/generated/REO.owl";
		IRI ont_iri = IRI.create(ont_uri);
		GoCAM go_cam = new GoCAM(ont_iri, base_ont_title, base_contributor, null, base_provider, add_lego_import);
		//Annotate the ontology		
		LocalDateTime now = LocalDateTime.now();
		OWLAnnotation time_anno = go_cam.df.getOWLAnnotation(GoCAM.version_info, go_cam.df.getOWLLiteral("Generated from Reactome biopax build: "+biopax_build_id+" on: "+now.toString()));
		OWLAxiom timeannoaxiom = go_cam.df.getOWLAnnotationAssertionAxiom(ont_iri, time_anno);
		go_cam.ontman.addAxiom(go_cam.go_cam_ont, timeannoaxiom);
		
		if(add_imports) {
			//add protein modification ontology
			String mod_iri = "http://purl.obolibrary.org/obo/mod.owl";
			OWLImportsDeclaration modImportDeclaration = go_cam.df.getOWLImportsDeclaration(IRI.create(mod_iri));
			go_cam.ontman.applyChange(new AddImport(go_cam.go_cam_ont, modImportDeclaration));
			//GO (for locations)
			String go_iri = "http://purl.obolibrary.org/obo/GO.owl";
			OWLImportsDeclaration goImportDeclaration = go_cam.df.getOWLImportsDeclaration(IRI.create(go_iri));
			go_cam.ontman.applyChange(new AddImport(go_cam.go_cam_ont, goImportDeclaration));
			//PRO (for proteins and complexes)
			String pro_iri = "http://purl.obolibrary.org/obo/PRO.owl";
			OWLImportsDeclaration proImportDeclaration = go_cam.df.getOWLImportsDeclaration(IRI.create(pro_iri));
			go_cam.ontman.applyChange(new AddImport(go_cam.go_cam_ont, proImportDeclaration));
			//CHEBI for everything
			String chebi_iri = "http://purl.obolibrary.org/obo/chebi.owl";
			OWLImportsDeclaration chebiImportDeclaration = go_cam.df.getOWLImportsDeclaration(IRI.create(chebi_iri));
			go_cam.ontman.applyChange(new AddImport(go_cam.go_cam_ont, chebiImportDeclaration));
		}else {
			OWLAnnotation import_comment = go_cam.df.getOWLAnnotation(GoCAM.rdfs_comment, go_cam.df.getOWLLiteral("This ontology references entities from: "
					+ "the protein modification ontology http://purl.obolibrary.org/obo/mod.owl "
					+ "the Gene Ontology http://purl.obolibrary.org/obo/GO.owl "
					+ "the Protein Ontology (optionally) http://purl.obolibrary.org/obo/PRO.owl "
					+ "and CHEBI.  For complete reasoning, these should be imported.  They are left out to reduce size and and increase tractability "
					+ "for viewing/editing tools such as Protege. "));
			OWLAxiom commentaxiom = go_cam.df.getOWLAnnotationAssertionAxiom(ont_iri, import_comment);
			go_cam.ontman.addAxiom(go_cam.go_cam_ont, commentaxiom);
		}
		if(!add_pro_logical_connections) {
			OWLAnnotation pro_comment = go_cam.df.getOWLAnnotation(
					GoCAM.rdfs_comment, go_cam.df.getOWLLiteral("This version of the ontology was created "
							+ "with annotation-level connections to the PRO ontology.  These could be replaced "
							+ "by subclass (see isa comments) and equivalentClass (see dbXref annotations) axioms if"
							+ " more direct integration is desired."));
			OWLAxiom proaxiom = go_cam.df.getOWLAnnotationAssertionAxiom(ont_iri, pro_comment);
			go_cam.ontman.addAxiom(go_cam.go_cam_ont, proaxiom);
		}
		//add this in so shex validator works without needing to import all of chebi..
		go_cam.addSubClassAssertion(GoCAM.chebi_molecular_entity, GoCAM.chemical_entity);
		go_cam.addSubClassAssertion(GoCAM.chebi_protein, GoCAM.chebi_information_biomacromolecule);
		//build it all!  
		//		if(local_catalogue_file!=null) {
		//			ontman.setIRIMappers(Collections.singleton(new CatalogXmlIRIMapper(local_catalogue_file)));
		//		}
		OWLOntology tbox = go_cam.ontman.loadOntologyFromOntologyDocument(new File("/Users/benjamingood/gocam_ontology/go-plus.owl"));

		PhysicalEntityOntologyBuilder converter = new PhysicalEntityOntologyBuilder(new GOLego(tbox), base_short_namespace, base_extra_info, r, pro_mapping);
		for (PhysicalEntity entity : biopax_model.getObjects(PhysicalEntity.class)){		
			String model_id = entity.hashCode()+"";
			n++;
			//System.out.println(n+" defining "+entity.getDisplayName()+" "+entity.getModelInterface()+" "+entity.getUri());
			converter.definePhysicalEntity(go_cam, entity, null, model_id);
		}

		System.out.println(" running reasoner ");
		if(!converter.reasoner.equals(ReasonerImplementation.none)) {
			OWLReasonerFactory reasonerFactory = null;
			if(converter.reasoner.equals(ReasonerImplementation.Hermit)) {
				reasonerFactory = new ReasonerFactory();
			}else if(converter.reasoner.equals(ReasonerImplementation.Elk)) {
				reasonerFactory = new ElkReasonerFactory();
			}		
			System.out.println(" creating reasoner ");
			OWLReasoner reasoner = reasonerFactory.createReasoner(go_cam.go_cam_ont);
			// Classify the ontology.
			System.out.println(" computing inferences ");
			reasoner.precomputeInferences(InferenceType.CLASS_HIERARCHY);
			// inferred axiom generators
			List<InferredAxiomGenerator<? extends OWLAxiom>> gens = 
					new ArrayList<InferredAxiomGenerator<? extends OWLAxiom>>();
			gens.add(new InferredSubClassAxiomGenerator());
			gens.add(new InferredEquivalentClassAxiomGenerator());
			InferredOntologyGenerator iog = new InferredOntologyGenerator(reasoner, gens);
			// Put the inferred axioms into a fresh empty ontology.
			//OWLOntology infOnt = go_cam.ontman.createOntology();
			//here just adding them to the original
			System.out.println(" adding inferences to ontology ");
			iog.fillOntology(go_cam.ontman.getOWLDataFactory(), go_cam.go_cam_ont);
			System.out.println(" disposing of reasoner ");
			reasoner.dispose();
		}

		System.out.println(" exporting ");
		go_cam.qrunner = new QRunner(go_cam.go_cam_ont); 
		if(outputformat.equals("RDFXML")) {
			outfilename = outfilename+".owl";
		}else if(outputformat.equals("TURTLE")) {
			outfilename = outfilename+".ttl";
		} 

		go_cam.writeGoCAM_jena(outfilename, false, outputformat);

		//		int n_objects = 0;
		//		for(String id : converter.id_class_map.keySet()) {
		//			System.out.println(id+" "+(n_objects++)+" "+converter.id_class_map.get(id));
		//		}
	}


	private OWLClassExpression definePhysicalEntity(GoCAM go_cam, PhysicalEntity entity, IRI this_iri, String model_id) throws IOException {

		String entity_id = BioPaxtoGO.getEntityReferenceId(entity);
		if(id_class_map.containsKey(entity_id)) {
			return id_class_map.get(entity_id);
		}		
		if(this_iri==null&&entity_id!=null) {	
			this_iri = IRI.create(GoCAM.reacto_base_iri+entity_id);
		}else if(this_iri==null&&entity_id==null) {			
			this_iri = GoCAM.makeReactoIRI(model_id, entity_id);
		}

		//add entity to ontology as a class, whatever it is
		OWLClass e = go_cam.df.getOWLClass(this_iri); 	
		if(entity_id!=null) {
			String ns_id = default_namespace_prefix+":"+entity_id;
			go_cam.addDatabaseXref(e, ns_id);
			String reactome_url = base_extra_info+entity_id;
			go_cam.addSeeAlso(e, reactome_url);
			go_cam.addComment(e, "BioPAX type: "+entity.getModelInterface());
			//add specific drug reference if its there - reactions with drugs are treated differently
			String iuphar_id = getDrugReferenceId(entity);
			if(iuphar_id!=null) {
				go_cam.addDrugReference(e, "IUPHAR:"+iuphar_id);
			}
			if(pro_exact_map.containsKey(entity_id)) {
				for(String pro_id : pro_exact_map.get(entity_id)) {
					if(add_pro_logical_connections) {
						OWLClass pro_class = go_cam.df.getOWLClass(IRI.create(GoCAM.obo_iri+pro_id));
						OWLAxiom eq_pro = go_cam.df.getOWLEquivalentClassesAxiom(e, pro_class);
						go_cam.ontman.addAxiom(go_cam.go_cam_ont, eq_pro);
					}else {
						go_cam.addDatabaseXref(e, GoCAM.obo_iri+pro_id);
					}
				}
			}
			if(pro_isa_map.containsKey(entity_id)) {
				for(String pro_id : pro_isa_map.get(entity_id)) {
					if(add_pro_logical_connections) {
						OWLClass pro_class = go_cam.df.getOWLClass(IRI.create(GoCAM.obo_iri+pro_id));
						go_cam.addSubClassAssertion(e, pro_class);
					}else {
						go_cam.addComment(e, "Isa "+GoCAM.obo_iri+pro_id);
					}
				}
			}
		}
		//tag the class with a basic upper level type	
		String entity_name = entity.getDisplayName();
		for(String label : entity.getName()) {
			go_cam.addAltLabel(e, label);
		}

		//attempt to localize the class 
		OWLClass go_loc_class = null;
		OWLClassExpression occurs_in_exp = null;
		CellularLocationVocabulary loc = ((PhysicalEntity) entity).getCellularLocation();
		if(loc!=null&&capture_location) {			
			//dig out the GO cellular location and create a class construct for it
			String location_term = null;
			Set<Xref> xrefs = loc.getXref();

			for(Xref xref : xrefs) {
				if(xref.getModelInterface().equals(UnificationXref.class)) {
					UnificationXref uref = (UnificationXref)xref;	    			
					//here we add the referenced GO class as a type.  
					String db = uref.getDb().toLowerCase();
					if(db.contains("gene ontology")) {
						String uri = GoCAM.obo_iri + uref.getId().replaceAll(":", "_");						
						go_loc_class = golego.getOboClass(uri, true);
						Set<XReferrable> refs = uref.getXrefOf();							
						for(XReferrable ref : refs) {
							location_term = ref.toString().replaceAll("CellularLocationVocabulary_", "");
							break;
						}
						if(location_term!=null) {							  
							occurs_in_exp =	go_cam.df.getOWLObjectSomeValuesFrom(GoCAM.located_in, go_loc_class);
							go_cam.addSubclassAssertion(e, occurs_in_exp, null);
						}
					}
				}
			}
		}
		go_cam.addLabel(e, entity_name);
		//basically anything can be represented as a set of things
		//check for sets and add them if present
		boolean isa_set = checkForAndAddSet(go_cam, model_id, entity, e);

		//now get more specific type information
		//Complex 
		if(entity.getModelInterface().equals(Complex.class)) {
			Complex complex = (Complex)entity;	
			go_cam.addSubClassAssertion(e, GoCAM.go_complex);
			if(capture_taxon) {
				go_cam.addSubClassAssertion(e, GoCAM.taxon_human);
			}
			//since we never have anything more specific that NEO knows about (now)
			//any downstream mapping will just call this a complex
			//todo IF we start using real external classes for complexes we can change this here. 
			go_cam.addUriAnnotations2Individual(e.getIRI(), GoCAM.canonical_record, GoCAM.go_complex.getIRI());
			//PRO pattern
			//with stoichiometry
			if(capture_complex_stoichiometry) {
				Set<Stoichiometry> stoichs = complex.getComponentStoichiometry();
				for(Stoichiometry stoich : stoichs) {
					PhysicalEntity part = stoich.getPhysicalEntity();
					String iuphar_id = getDrugReferenceId(part);
					if(iuphar_id!=null){
						go_cam.addDrugReference(e, "IUPHAR:"+iuphar_id);
					}
					Integer s = (int) stoich.getStoichiometricCoefficient();
					OWLClassExpression owl_part = definePhysicalEntity(go_cam, part,null, model_id);
					if(!owl_part.isAnonymous()) {
						Set<String> iuphar_ids = Helper.getAnnotations(owl_part.asOWLClass(), go_cam.go_cam_ont, GoCAM.iuphar_id);						
						if(iuphar_ids!=null){
							for(String iuphar : iuphar_ids) {
								go_cam.addDrugReference(e, iuphar);
							}
						}
					}
					OWLClassExpression exact_cardinality = go_cam.df.getOWLObjectExactCardinality(s, GoCAM.has_component, owl_part);
					go_cam.addSubClassAssertion(e, exact_cardinality);
				}	
			}else {
				//Some values with no cardinality (stoichiometry)
				Set<OWLClassExpression> owl_parts = new HashSet<OWLClassExpression>();
				Set<PhysicalEntity> known_parts = complex.getComponent();
				for(PhysicalEntity part : known_parts) {
					String iuphar_id = getDrugReferenceId(part);
					if(iuphar_id!=null){
						go_cam.addDrugReference(e, "IUPHAR:"+iuphar_id);
					}
					OWLClassExpression owl_part = definePhysicalEntity(go_cam, part,null, model_id);
					OWLClassExpression has_part_exp = go_cam.df.getOWLObjectSomeValuesFrom(GoCAM.has_part, owl_part);
					owl_parts.add(has_part_exp);
				}				
				//if just one, no need for intersection
				if(owl_parts.size()==1) {
					OWLClassExpression p = owl_parts.iterator().next();
					OWLAxiom eq_prot = go_cam.df.getOWLEquivalentClassesAxiom(e, p);
					go_cam.ontman.addAxiom(go_cam.go_cam_ont, eq_prot);
				}else if(owl_parts.size()>1) {					
					OWLObjectIntersectionOf complex_class = go_cam.df.getOWLObjectIntersectionOf(owl_parts);					
					OWLAxiom eq_intersect = go_cam.df.getOWLEquivalentClassesAxiom(e, complex_class);
					go_cam.ontman.addAxiom(go_cam.go_cam_ont, eq_intersect);
				}
			}
		}
		//Protein (or often entity set)
		else if(entity.getModelInterface().equals(Protein.class)||entity.getModelInterface().equals(PhysicalEntity.class)) {
			String id = null;				
			if(entity.getModelInterface().equals(Protein.class)) {
				Protein protein = (Protein)entity;
				id = getUniprotProteinId(protein);
				if(capture_taxon) {
					go_cam.addSubClassAssertion(e, GoCAM.taxon_human);
				}
			}			
			if(id!=null) {
				//create the specific protein class
				OWLClass uniprotein_class = go_cam.df.getOWLClass(IRI.create(GoCAM.uniprot_iri + id)); 									
				go_cam.addSubclassAssertion(uniprotein_class, GoCAM.chebi_protein, null);	
				go_cam.addUriAnnotations2Individual(e.getIRI(), GoCAM.canonical_record, uniprotein_class.getIRI());
				//not equivalent - more specific
				go_cam.addSubClassAssertion(e, uniprotein_class);
				//check for modifications
				Set<EntityFeature> features = entity.getFeature();
				for(EntityFeature feature : features) {
					int seq_start = -1; int seq_end = -1;
					//position of all features (relative to UniProt entry)
					SequenceLocation region = feature.getFeatureLocation();
					if(region instanceof SequenceInterval) {
						SequenceInterval interval = (SequenceInterval) region;
						SequenceSite begin = interval.getSequenceIntervalBegin();
						seq_start = begin.getSequencePosition();
						SequenceSite end = interval.getSequenceIntervalEnd();
						seq_end = end.getSequencePosition();
					}else if(region instanceof SequenceSite){
						SequenceSite site = (SequenceSite)region;
						seq_start = site.getSequencePosition();
					}
					//not used in reactome
					SequenceRegionVocabulary region_type = feature.getFeatureLocationType();
					if(region_type!=null) {
						System.out.println("region type used for first time, investigate: "+entity.getDisplayName());
						System.exit(0);
					}

					if(feature instanceof ModificationFeature) {
						ModificationFeature mod = (ModificationFeature)feature;
						SequenceModificationVocabulary mod_type = mod.getModificationType();
						if(mod_type!=null&&mod_type.getTerm()!=null) {
							String mod_type_label = mod_type.getTerm().iterator().next();
							Set<Xref> mod_xrefs = mod_type.getXref();
							for(Xref mod_xref : mod_xrefs) {
								String mod_db = mod_xref.getDb();
								String mod_id = mod_xref.getId();
								//http://purl.obolibrary.org/obo/MOD_00114
								if(mod_db.equals("MOD")) {
									mod_id = mod_id.replace(":", "_");
									OWLClass mod_class = go_cam.df.getOWLClass(IRI.create(GoCAM.obo_iri+mod_id)); 
									go_cam.addLabel(mod_class, mod_type_label);
									OWLClassExpression has_mod = go_cam.df.getOWLObjectSomeValuesFrom(GoCAM.has_part, mod_class);
									if(seq_start>-1&&seq_end==-1) {
										OWLLiteral start_literal = go_cam.df.getOWLLiteral(seq_start); 
										OWLClassExpression has_start_exp = go_cam.df.getOWLDataHasValue(GoCAM.has_start, start_literal);
										OWLClassExpression mod_with_region = go_cam.df.getOWLObjectIntersectionOf(has_mod, has_start_exp);
										go_cam.addSubclassAssertion(e, mod_with_region, null);
									}else if(seq_start>-1&&seq_end>-1) {
										OWLLiteral start_literal = go_cam.df.getOWLLiteral(seq_start); 
										OWLClassExpression has_start_exp = go_cam.df.getOWLDataHasValue(GoCAM.has_start, start_literal);
										OWLLiteral end_literal = go_cam.df.getOWLLiteral(seq_end); 
										OWLClassExpression has_end_exp = go_cam.df.getOWLDataHasValue(GoCAM.has_end, end_literal);
										OWLClassExpression mod_with_region = go_cam.df.getOWLObjectIntersectionOf(has_mod, has_start_exp, has_end_exp);
										go_cam.addSubclassAssertion(e, mod_with_region, null);
									}else {
										go_cam.addSubclassAssertion(e, has_mod, null);	
									}
								}
							}
						}else {
							go_cam.addComment(e, "Unspecified modification type.  Comment: "+mod.getComment());
							//System.exit(0);
						}
					}else if(feature instanceof FragmentFeature) {
						if(seq_start>-1&&seq_end==-1) {
							OWLLiteral start_literal = go_cam.df.getOWLLiteral(seq_start); 
							OWLClassExpression has_start_exp = go_cam.df.getOWLDataHasValue(GoCAM.has_start, start_literal);
							go_cam.addSubclassAssertion(e, has_start_exp, null);
						}else if(seq_start>-1&&seq_end>-1) {
							OWLLiteral start_literal = go_cam.df.getOWLLiteral(seq_start); 
							OWLClassExpression has_start_exp = go_cam.df.getOWLDataHasValue(GoCAM.has_start, start_literal);
							OWLLiteral end_literal = go_cam.df.getOWLLiteral(seq_end); 
							OWLClassExpression has_end_exp = go_cam.df.getOWLDataHasValue(GoCAM.has_end, end_literal);
							OWLClassExpression frag_with_region = go_cam.df.getOWLObjectIntersectionOf(has_start_exp, has_end_exp);
							go_cam.addSubclassAssertion(e, frag_with_region, null);
						}	
					}
				}
			}else if(entity.getModelInterface().equals(Protein.class)) {
				go_cam.addSubclassAssertion(e, GoCAM.chebi_protein, null);	
				//System.out.println("non uniprot protein detected: "+entity.getDisplayName());
			}else { //entity is just PhysicalEntity.class
				Set<Xref> e_xrefs = entity.getXref();
				boolean entity_type_set = false;
				if(e_xrefs!=null) {
					for(Xref x : e_xrefs) {
						if(x.getDb().equals("ChEBI")) {
							String chebi_uri = GoCAM.obo_iri + "CHEBI_"+x.getId();
							OWLClass mlc_class = golego.getOboClass(chebi_uri, true);
							go_cam.addUriAnnotations2Individual(e.getIRI(), GoCAM.canonical_record, mlc_class.getIRI());
							if(golego.isChebiRole(chebi_uri)) {
								go_cam.addSubclassAssertion(mlc_class, GoCAM.chemical_role, null);									
								//assert entity here is a chemical instance
								go_cam.addSubclassAssertion(e, GoCAM.chemical_entity, null);
								//connect it to the role
								OWLClassExpression role_exp = go_cam.df.getOWLObjectSomeValuesFrom(GoCAM.has_role, (OWLClassExpression)mlc_class);
								OWLAxiom eq_role = go_cam.df.getOWLEquivalentClassesAxiom(e, role_exp);
								go_cam.ontman.addAxiom(go_cam.go_cam_ont, eq_role);
								entity_type_set = true;
							}else { //presumably its a chemical entity if not a role								
								go_cam.addSubclassAssertion(mlc_class, GoCAM.chemical_entity, null);	
								go_cam.addSubclassAssertion(e, mlc_class, null);
								entity_type_set = true;
							}
							if(!isa_set) {
								go_cam.addUriAnnotations2Individual(e.getIRI(), GoCAM.canonical_record, IRI.create(chebi_uri));
							}
							break;
						}
					}
				}
				if(!entity_type_set&&!isa_set) {
					//everything is at least a continuant...
					go_cam.addSubclassAssertion(e, GoCAM.continuant_class, null);	
					go_cam.addUriAnnotations2Individual(e.getIRI(), GoCAM.canonical_record, GoCAM.continuant_class.getIRI());					
					System.out.println("unclassified entity\t"+e+"\t"+entity.getDisplayName());
				}
			}
		}
		//Dna (gene)
		else if(entity.getModelInterface().equals(Dna.class)) {
			Dna dna = (Dna)entity;
			go_cam.addSubClassAssertion(e, GoCAM.chebi_dna);
			EntityReference entity_ref = dna.getEntityReference();	
			boolean reference = false;
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
						go_cam.addSubclassAssertion(e, uniprotein_class, null);
						go_cam.addUriAnnotations2Individual(e.getIRI(), GoCAM.canonical_record, uniprotein_class.getIRI());					
						reference = true;
					}
					//
					else if(xref.getModelInterface().equals(UnificationXref.class)) {
						UnificationXref uref = (UnificationXref)xref;	
						if(uref.getDb().equals("ENSEMBL")) {
							go_cam.addDatabaseXref(e, "ENSEMBL:"+id);
							go_cam.addLiteralAnnotations2Individual(e.getIRI(), GoCAM.canonical_record, "ENSEMBL:"+id);					
							reference = true;
						}
					}
				}
			}
			if(!reference&&!isa_set) {
				go_cam.addUriAnnotations2Individual(e.getIRI(), GoCAM.canonical_record, GoCAM.chebi_dna.getIRI());					
			}
		}
		//rna 
		else if(entity.getModelInterface().equals(Rna.class)) {
			Rna rna = (Rna)entity;
			go_cam.addSubClassAssertion(e, GoCAM.chebi_rna);	
			EntityReference entity_ref = rna.getEntityReference();	
			boolean reference = false;
			if(entity_ref!=null) {
				Set<Xref> p_xrefs = entity_ref.getXref();
				for(Xref xref : p_xrefs) {
					//In GO-CAM we almost always want to talk about proteins
					//if there is a uniprot identifier to use, use that before anything else.
					String db = xref.getDb().toLowerCase();
					String id = xref.getId();
					if(db.contains("uniprot")) {
						OWLClass uniprotein_class = go_cam.df.getOWLClass(IRI.create(GoCAM.uniprot_iri + id)); 
						go_cam.addSubClassAssertion(e, uniprotein_class);
						go_cam.addUriAnnotations2Individual(e.getIRI(), GoCAM.canonical_record, uniprotein_class.getIRI());					
						reference = true;
					}
					//
					else if(xref.getModelInterface().equals(UnificationXref.class)) {					
						UnificationXref uref = (UnificationXref)xref;	
						//if(uref.getDb().equals("ENSEMBL")) {
						go_cam.addDatabaseXref(e, uref.getDb()+id);
						go_cam.addLiteralAnnotations2Individual(e.getIRI(), GoCAM.canonical_record, "ENSEMBL:"+id);					
						reference = true;
						//TODO if at some point go-cam decides to represent transcripts etc. then we'll update here to use the ensembl etc. ids.  
						//}
					}
				}
			}
			if(!reference&&!isa_set) {
				go_cam.addUriAnnotations2Individual(e.getIRI(), GoCAM.canonical_record, GoCAM.chebi_rna.getIRI());					
			}
		}
		//SmallMolecule
		else if(entity.getModelInterface().equals(SmallMolecule.class)) {
			SmallMolecule mlc = (SmallMolecule)entity;
			EntityReference entity_ref = mlc.getEntityReference();	
			boolean reference = false;
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
					reference = true;
					String chebi_uri = GoCAM.obo_iri + chebi_id;
					OWLClass mlc_class = golego.getOboClass(chebi_uri, true);
					go_cam.addUriAnnotations2Individual(e.getIRI(), GoCAM.canonical_record, mlc_class.getIRI());
					if(golego.isChebiRole(chebi_uri)) {
						go_cam.addSubclassAssertion(mlc_class, GoCAM.chemical_role, null);									
						//assert entity here is a chemical instance
						go_cam.addSubclassAssertion(e, GoCAM.chemical_entity, null);
						//connect it to the role
						OWLClassExpression role_exp = go_cam.df.getOWLObjectSomeValuesFrom(GoCAM.has_role, (OWLClassExpression)mlc_class);
						OWLAxiom eq_role = go_cam.df.getOWLEquivalentClassesAxiom(e, role_exp);
						go_cam.ontman.addAxiom(go_cam.go_cam_ont, eq_role);
					}else { //presumably its a chemical entity if not a role								
						go_cam.addSubclassAssertion(mlc_class, GoCAM.chemical_entity, null);	
						go_cam.addSubclassAssertion(e, mlc_class, null);
					}
				}else {
					//no chebi so we don't know what it is (for Noctua) aside from being some kind of chemical entity
					go_cam.addSubclassAssertion(e, GoCAM.chemical_entity, null);
					go_cam.addUriAnnotations2Individual(e.getIRI(), GoCAM.canonical_record, GoCAM.chemical_entity.getIRI());
				}
			}
		}
		if(entity_id!=null) {
			id_class_map.put(entity_id, e);
		}
		return e;
	}

	private boolean checkForAndAddSet(GoCAM go_cam, String model_id, PhysicalEntity entity_set, OWLClass e) throws IOException {

		boolean isa_set = false;
		//its a set if it contains members
		if(entity_set.getMemberPhysicalEntity()!=null&&entity_set.getMemberPhysicalEntity().size()>0) {
			isa_set = true;
			Set<PhysicalEntity> parts_list = entity_set.getMemberPhysicalEntity();
			Set<OWLClassExpression> owl_parts = new HashSet<OWLClassExpression>();
			Set<String> types = new HashSet<String>();
			boolean reference_found = false;
			for(PhysicalEntity part : parts_list) {
				String iuphar_id = getDrugReferenceId(part);
				if(iuphar_id!=null){
					go_cam.addDrugReference(e, "IUPHAR:"+iuphar_id);
				}
				OWLClassExpression part_exp = definePhysicalEntity(go_cam, part, null, model_id);
				owl_parts.add(part_exp);

				types.add(part.getModelInterface().getName().toLowerCase());
				//for mapping - any member of set could replace the set in a model without breaking it
				for(OWLAnnotation anno : EntitySearcher.getAnnotationObjects((OWLEntity) part_exp, go_cam.go_cam_ont, GoCAM.canonical_record)) {
					OWLAnnotationValue v = anno.getValue();
					com.google.common.base.Optional<IRI> oiri = v.asIRI();
					if(oiri.isPresent()) {
						go_cam.addUriAnnotations2Individual(e.getIRI(), GoCAM.canonical_record, anno.getValue().asIRI().get());
					}else if(v.asLiteral().isPresent()){
						go_cam.addLiteralAnnotations2Individual(e.getIRI(), GoCAM.canonical_record, anno.getValue().asLiteral().get());
					}
					reference_found = true;
				}
			}	
			if(owl_parts!=null) {			
				if(owl_parts.size()>1) {
					OWLObjectUnionOf union_exp = go_cam.df.getOWLObjectUnionOf(owl_parts);
					OWLAxiom eq_prot_set = go_cam.df.getOWLEquivalentClassesAxiom(e, union_exp);
					go_cam.ontman.addAxiom(go_cam.go_cam_ont, eq_prot_set);							
				}else if(owl_parts.size()==1){
					OWLClassExpression one_part = owl_parts.iterator().next();
					OWLAxiom eq_prot = go_cam.df.getOWLEquivalentClassesAxiom(e, one_part);
					go_cam.ontman.addAxiom(go_cam.go_cam_ont, eq_prot);
				}
				Set<OWLClass> main_types = new HashSet<OWLClass>();
				for(OWLClassExpression owlclasse : owl_parts) {
					if(!owlclasse.isAnonymous()) {
						OWLClass owlclass = owlclasse.asOWLClass();
						if(isRootClass(owlclass)) {
							main_types.add(owlclass);
						}							
						//get the class's direct superclass
						for(OWLSubClassOfAxiom s : go_cam.go_cam_ont.getSubClassAxiomsForSubClass(owlclass)) {
							//get the superclass's superclass
							OWLClassExpression sc = s.getSuperClass();
							if(!sc.isAnonymous()) {
								if(isRootClass(sc.asOWLClass())) {
									main_types.add(sc.asOWLClass());
								}
								for(OWLSubClassOfAxiom ssca : go_cam.go_cam_ont.getSubClassAxiomsForSubClass(sc.asOWLClass())) {
									OWLClassExpression ssc = ssca.getSuperClass();
									if(!ssc.isAnonymous()) {
										if(isRootClass(ssc.asOWLClass())) {
											main_types.add(ssc.asOWLClass());
										}			
									}
								}
							}
						}
					}
				}
				if(main_types.size()>0) {
					OWLClass main_type = GoCAM.continuant_class;
					if(main_types.contains(GoCAM.chebi_dna)) {
						main_type = GoCAM.chebi_dna;
					}else if(main_types.contains(GoCAM.chebi_mrna)) {
						main_type = GoCAM.chebi_mrna;
					}else if(main_types.contains(GoCAM.chebi_rna)) {
						main_type = GoCAM.chebi_rna;
					}else if(main_types.contains(GoCAM.chebi_trna_precursor)) {
						main_type = GoCAM.chebi_trna_precursor;
					}else if(main_types.contains(GoCAM.chebi_protein)) {
						main_type = GoCAM.chebi_protein;
					}else if(main_types.contains(GoCAM.chebi_information_biomacromolecule)) {
						main_type = GoCAM.chebi_information_biomacromolecule;
					}else if(main_types.contains(GoCAM.go_complex)) {
						main_type = GoCAM.go_complex;
					}else if(main_types.contains(GoCAM.chemical_entity)) {
						main_type = GoCAM.chemical_entity;
					}
					go_cam.addSubclassAssertion(e, main_type, null);					
					if(!reference_found) {
						go_cam.addUriAnnotations2Individual(e.getIRI(), GoCAM.canonical_record, main_type.getIRI());
					}
				}
			}
		}
		return isa_set;
	}


	private boolean isRootClass(OWLClass owlclass) {
		if(owlclass.equals(GoCAM.chemical_entity)||
				owlclass.equals(GoCAM.chebi_protein)||
				owlclass.equals(GoCAM.chebi_information_biomacromolecule)||
				owlclass.equals(GoCAM.chebi_dna)||
				owlclass.equals(GoCAM.chebi_rna)||
				owlclass.equals(GoCAM.chebi_mrna)||
				owlclass.equals(GoCAM.chebi_trna_precursor)||
				owlclass.equals(GoCAM.go_complex)) {
			return true;
		}
		return false;
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

	public static String getDrugReferenceId(Entity bp_entity) {
		String id = null;
		try {
			EntityReference r = null;
			if(bp_entity.getModelInterface().equals(Protein.class)) {
				r = ((Protein) bp_entity).getEntityReference();
			}else if(bp_entity.getModelInterface().equals(SmallMolecule.class)){
				r = ((SmallMolecule) bp_entity).getEntityReference();
			}else if(bp_entity.getModelInterface().equals(Rna.class)){
				r = ((Rna) bp_entity).getEntityReference();
			}else if(bp_entity.getModelInterface().equals(Dna.class)){
				r = ((Dna) bp_entity).getEntityReference();
			}else if(bp_entity.getModelInterface().equals(RnaRegion.class)){
				r = ((RnaRegion) bp_entity).getEntityReference();
			}else if(bp_entity.getModelInterface().equals(DnaRegion.class)){
				r = ((DnaRegion) bp_entity).getEntityReference();
			}else if(bp_entity.getModelInterface().equals(PhysicalEntity.class)) {
				//System.err.println("Can not access EntityReference for untyped physical entity: "+bp_entity.getDisplayName());
			}
			if(r!=null) {
				Set<Xref> erefs = r.getXref();
				for(Xref eref : erefs) {
					if(eref.getDb().equals("IUPHAR")) {
						id = eref.getId();
					}
				}
			}
		}catch(Exception e) {
			return null;
		}
		//		if(id!=null) {
		//			System.out.println("found drug id "+id+" "+bp_entity.getDisplayName());
		//		}
		return id;
	}

	public static void countPhysical(Model biopax_model) throws IOException {
		int n_all = 0; int n_complex = 0; int n_sets = 0; int n_protein = 0; int n_small_molecule = 0;
		int n_all_pro = 0; int n_complex_pro = 0; int n_sets_pro = 0; int n_protein_pro = 0; int n_small_molecule_pro = 0;
		int n_dna = 0; int n_rna = 0; int n_dna_region = 0;  int n_rna_region = 0;
		int n_other = 0; int n_physical = 0; 
		int n_drug = 0;
		int n_sets_of_complexes = 0; int n_sets_of_sets = 0;
		Set<String> set_types = new HashSet<String>();		
		String mapping = "/Users/benjamingood/gocam_ontology/REO/promapping.txt";
		Map<String, Set<String>> exact_map = PRO.readReact2PRO(mapping, "exact");
		Map<String, Set<String>> any_map = PRO.readReact2PRO(mapping, "is_a");
		Map<String, String> physical_ref = new HashMap<String, String>();
		any_map.putAll(exact_map);
		boolean isa_set = false;
		for (PhysicalEntity e : biopax_model.getObjects(PhysicalEntity.class)){		
			n_all++;
			boolean in_pro = false;
			String id = BioPaxtoGO.getEntityReferenceId(e);
			if(any_map.containsKey(id)) {
				n_all_pro++;
				in_pro = true;
			}
			String drug_id = getDrugReferenceId(e);
			if(drug_id==null) {
				drug_id = "no_IUPHAR";
			}else {
				drug_id = "IUPHAR:"+drug_id;
				n_drug++;
			}

			if(!e.getMemberPhysicalEntity().isEmpty()) {
				n_sets++;
				isa_set = true;
				if(in_pro) {
					n_sets_pro++;
				}
				set_types.add(e.getModelInterface().toString());
				for(PhysicalEntity member : e.getMemberPhysicalEntity()) {
					if(member instanceof Complex) {
						n_sets_of_complexes++;
						//System.out.println("Complex set "+e.getDisplayName()+" "+BioPaxtoGO.getEntityReferenceId(e));
						break;
					}
				}
				for(PhysicalEntity member : e.getMemberPhysicalEntity()) {
					if(!member.getMemberPhysicalEntity().isEmpty()) {
						n_sets_of_sets++;
						//System.out.println("Set set "+e.getDisplayName()+" "+BioPaxtoGO.getEntityReferenceId(e));
						break;
					}
				}
			}else {
				isa_set = false;
			}
			physical_ref.put(BioPaxtoGO.getEntityReferenceId(e)+"\t"+drug_id+"\t"+isa_set, e.getDisplayName()+"\t"+e.getModelInterface());

			if(e instanceof Complex) {
				n_complex++;
				if(in_pro) {
					n_complex_pro++;
				}
			}else if(e instanceof Protein) {
				n_protein++;
				if(in_pro) {
					n_protein_pro++;
				}
			}else if(e instanceof SmallMolecule) {
				n_small_molecule++;
				if(in_pro) {
					n_small_molecule_pro++;
				}
			}else if(e instanceof Dna) {
				n_dna++;
			}else if(e instanceof Rna) {
				n_rna++;
			}else if(e instanceof DnaRegion) {
				n_dna_region++;
			}else if(e instanceof RnaRegion) {
				n_rna_region++;
			}else if(e.getModelInterface().equals(PhysicalEntity.class)){
				n_physical++;
			}else {
				n_other++;
				System.out.println(e.getModelInterface());
			}
		}
		System.out.println("n_all\tn_physical\tn_sets\tn_complex\tn_protein\tn_small_molecule"
				+"\tn_dna\tn_rna\tn_dna_region\tn_rna_region\tn_other\tn_drug");
		System.out.println( n_all+"\t"+n_physical+"\t"+n_sets+"\t"+n_complex+"\t"+n_protein+"\t"+n_small_molecule 
				+"\t"+n_dna+"\t"+n_rna+"\t"+n_dna_region+"\t"+n_rna_region 
				+"\t"+n_other+"\t"+n_drug);
		System.out.println("n_sets_of_complexes = "+n_sets_of_complexes+" n_sets_of_sets = "+n_sets_of_sets);
		System.out.println(set_types);
		System.out.println("n_all_pro\tn_sets_pro\tn_complex_pro\tn_protein_prp\tn_small_molecule_pro");
		System.out.println( n_all_pro+"\t"+n_sets_pro+"\t"+n_complex_pro+"\t"+n_protein_pro+"\t"+n_small_molecule_pro);

		FileWriter f = new FileWriter("/Users/benjamingood/Desktop/untyped_physical.txt");
		f.write("id	drug	set	name	biopax type\n");
		for(String n : physical_ref.keySet()) {
			f.write(n+"\t"+physical_ref.get(n)+"\n");
		}
		f.close();
	}

}
