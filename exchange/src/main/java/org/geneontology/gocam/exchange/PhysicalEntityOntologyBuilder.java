/**
 * 
 */
package org.geneontology.gocam.exchange;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.jena.vocabulary.RDFS;
import org.biopax.paxtools.io.BioPAXIOHandler;
import org.biopax.paxtools.io.SimpleIOHandler;
import org.biopax.paxtools.model.Model;
import org.biopax.paxtools.model.level3.CellularLocationVocabulary;
import org.biopax.paxtools.model.level3.Complex;
import org.biopax.paxtools.model.level3.Dna;
import org.biopax.paxtools.model.level3.DnaRegion;
import org.biopax.paxtools.model.level3.Entity;
import org.biopax.paxtools.model.level3.EntityReference;
import org.biopax.paxtools.model.level3.Pathway;
import org.biopax.paxtools.model.level3.PhysicalEntity;
import org.biopax.paxtools.model.level3.Protein;
import org.biopax.paxtools.model.level3.Rna;
import org.biopax.paxtools.model.level3.RnaRegion;
import org.biopax.paxtools.model.level3.SmallMolecule;
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
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLAnnotation;
import org.semanticweb.owlapi.model.OWLAxiom;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLClassExpression;
import org.semanticweb.owlapi.model.OWLNamedIndividual;
import org.semanticweb.owlapi.model.OWLObjectIntersectionOf;
import org.semanticweb.owlapi.model.OWLObjectUnionOf;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyCreationException;
import org.semanticweb.owlapi.model.OWLOntologyStorageException;
import org.semanticweb.owlapi.reasoner.InferenceType;
import org.semanticweb.owlapi.reasoner.OWLReasoner;
import org.semanticweb.owlapi.reasoner.OWLReasonerFactory;
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

	GOPlus goplus;
	String default_namespace_prefix;
	String base_extra_info;
	Map<String, OWLClassExpression> id_class_map;
	ReasonerImplementation reasoner;
	enum ReasonerImplementation {
		Elk, Hermit, none 
	}
	/**
	 * 
	 */
	public PhysicalEntityOntologyBuilder(GOPlus go_plus, String default_namespace_prefix_, String base_extra_info_, ReasonerImplementation reasoner_) {
		goplus = go_plus;
		default_namespace_prefix = default_namespace_prefix_;
		base_extra_info = base_extra_info_;
		id_class_map = new HashMap<String, OWLClassExpression>();
		reasoner = reasoner_;
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
		String input_biopax = 
				"/Users/bgood/Desktop/test/biopax/Homo_sapiens_march25_2019.owl";
				//"/Users/bgood/Desktop/test/biopax/SignalingByERBB2.owl";
				//"/Users/bgood/Desktop/test/biopax/SCF.owl";
		String converted = 
				//"/Users/bgood/Desktop/test/go_cams/Wnt_complete_2018-";
				//"/Users/bgood/Desktop/test/go_cams/";
				"/Users/bgood/gocam_ontology/";
		String base_ont_title = "Reactome_physical_entities";//"SignalingByERBB2_Physical_Entities"; //"Reactome_physical_entities";
		String base_extra_info = "https://reactome.org/content/detail/";
		String base_short_namespace = "Reactome";
		String outfilename = converted+base_ont_title;
		ReasonerImplementation r = ReasonerImplementation.Hermit;
		
		BioPAXIOHandler handler = new SimpleIOHandler();
		FileInputStream f = new FileInputStream(input_biopax);
		Model biopax_model = handler.convertFromOWL(f);
		String biopax_build_id = biopax_model.getXmlBase();
		String base_contributor = "https://orcid.org/0000-0002-7334-7852";
		String base_provider = "https://reactome.org";
		boolean add_lego_import = false;
		int n = 0;
		//String iri = "http://model.geneontology.org/"+base_ont_title;
		//making it resolvable.. where it currently lives
		//TODO better URIs
		String ont_uri = "https://github.com/geneontology/pathways2GO/raw/master/exchange/generated/Reactome_physical_entities.owl";
		IRI ont_iri = IRI.create(ont_uri);
		GoCAM go_cam = new GoCAM(ont_iri, base_ont_title, base_contributor, null, base_provider, add_lego_import);
		//Annotate the ontology
		OWLAnnotation source_anno = go_cam.df.getOWLAnnotation(GoCAM.rdfs_comment, go_cam.df.getOWLLiteral("Generated from biopax build: "+biopax_build_id));
		OWLAxiom annoaxiom = go_cam.df.getOWLAnnotationAssertionAxiom(ont_iri, source_anno);
		go_cam.ontman.addAxiom(go_cam.go_cam_ont, annoaxiom);
		//build it all!  
		PhysicalEntityOntologyBuilder converter = new PhysicalEntityOntologyBuilder(new GOPlus(), base_short_namespace, base_extra_info, r);
		for (PhysicalEntity entity : biopax_model.getObjects(PhysicalEntity.class)){
			String model_id = entity.hashCode()+"";
			n++;
			System.out.println(n+" defining "+base_ont_title+" "+entity.getModelInterface());
			converter.definePhysicalEntity(go_cam, entity, null, model_id);
		}
		if(!converter.reasoner.equals(ReasonerImplementation.none)) {
			OWLReasonerFactory reasonerFactory = null;
			if(converter.reasoner.equals(ReasonerImplementation.Hermit)) {
				reasonerFactory = new ReasonerFactory();
			}else if(converter.reasoner.equals(ReasonerImplementation.Elk)) {
				reasonerFactory = new ElkReasonerFactory();
			}			
			OWLReasoner reasoner = reasonerFactory.createReasoner(go_cam.go_cam_ont);
			// Classify the ontology.
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
			iog.fillOntology(go_cam.ontman.getOWLDataFactory(), go_cam.go_cam_ont);
			reasoner.dispose();
		}
		
		go_cam.qrunner = new QRunner(go_cam.go_cam_ont); 
		String outputformat = "RDFXML";//"TURTLE";
		if(outputformat.equals("RDFXML")) {
			outfilename = outfilename+".owl";
		}else if(outputformat.equals("TURTLE")) {
			outfilename = outfilename+".ttl";
		} 
			
		go_cam.writeGoCAM_jena(outfilename, false, outputformat);
		
		int n_objects = 0;
		for(String id : converter.id_class_map.keySet()) {
			System.out.println(id+" "+(n_objects++)+" "+converter.id_class_map.get(id));
		}
	}

	private OWLClassExpression definePhysicalEntity(GoCAM go_cam, PhysicalEntity entity, IRI this_iri, String model_id) throws IOException {
		String entity_id = BioPaxtoGO.getEntityReferenceId(entity);
		if(id_class_map.containsKey(entity_id)) {
			return id_class_map.get(entity_id);
		}		
		if(this_iri==null&&entity_id!=null) {	
			this_iri = IRI.create(GoCAM.base_iri+entity_id);
		}else if(this_iri==null&&entity_id==null) {			
			this_iri = GoCAM.makeGoCamifiedIRI(model_id, entity_id);
		}
		//add entity to ontology as a class, whatever it is
		OWLClass e = go_cam.df.getOWLClass(this_iri); 	
		if(entity_id!=null) {
			String ns_id = default_namespace_prefix+":"+entity_id;
			go_cam.addDatabaseXref(e, ns_id);
			String reactome_url = base_extra_info+entity_id;
			go_cam.addSeeAlso(e, reactome_url);
			go_cam.addComment(e, "BioPAX type: "+entity.getModelInterface());
		}
		//tag the class with a basic upper level type
		go_cam.addSubClassAssertion(e, GoCAM.chebi_molecular_entity);
		//this allows linkage between different entities in the GO-CAM sense that correspond to the same thing in the BioPax sense
		go_cam.addUriAnnotations2Individual(e.getIRI(),GoCAM.skos_exact_match, IRI.create(entity.getUri()));		
		String entity_name = entity.getDisplayName();
		for(String label : entity.getName()) {
			go_cam.addAltLabel(e, label);
		}
		
		//attempt to localize the class 
		OWLClass go_loc_class = null;
		//OWLClassExpression occurs_in_exp = null;
		CellularLocationVocabulary loc = ((PhysicalEntity) entity).getCellularLocation();
		if(loc!=null) {			
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
						go_loc_class = goplus.getOboClass(uri, true);
						Set<XReferrable> refs = uref.getXrefOf();							
						for(XReferrable ref : refs) {
							location_term = ref.toString().replaceAll("CellularLocationVocabulary_", "");
							break;
						}
						if(location_term!=null) {							 
							//decided not to express this in the tbox for now.  
							//occurs_in_exp =	go_cam.df.getOWLObjectSomeValuesFrom(GoCAM.located_in, go_loc_class);
							//go_cam.addSubclassAssertion(e, occurs_in_exp, null);
							go_cam.addLiteralAnnotations2Individual(e.getIRI(), GoCAM.rdfs_comment, "located_in "+location_term);
							entity_name = entity_name+" ("+location_term+")";
						}
					}
				}
			}
			go_cam.addLabel(e, entity_name);
			//basically anything can be represented as a set of things
			//check for sets and add them if present
			go_cam = checkForAndAddSet(go_cam, model_id, entity, e);

			//now get more specific type information
			//Complex 
			if(entity.getModelInterface().equals(Complex.class)) {
				Complex complex = (Complex)entity;	
				go_cam.addSubClassAssertion(e, GoCAM.go_complex);
				//get all known parts
				Set<PhysicalEntity> known_parts = complex.getComponent();
				Set<OWLClassExpression> owl_parts = new HashSet<OWLClassExpression>();
				for(PhysicalEntity part : known_parts) {
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
			//Protein (or often entity set)
			else if(entity.getModelInterface().equals(Protein.class)||entity.getModelInterface().equals(PhysicalEntity.class)) {
				String id = null;				
				if(entity.getModelInterface().equals(Protein.class)) {
					Protein protein = (Protein)entity;
					id = getUniprotProteinId(protein);
				}			
				if(id!=null) {
					//create the specific protein class
					OWLClass uniprotein_class = go_cam.df.getOWLClass(IRI.create(GoCAM.uniprot_iri + id)); 									
					go_cam.addSubclassAssertion(uniprotein_class, GoCAM.chebi_protein, null);	
					OWLAxiom eq_prot_loc = go_cam.df.getOWLEquivalentClassesAxiom(e, uniprotein_class);
					go_cam.ontman.addAxiom(go_cam.go_cam_ont, eq_prot_loc);					
				}
			}
			//Dna (gene)
			else if(entity.getModelInterface().equals(Dna.class)) {
				Dna dna = (Dna)entity;
				go_cam.addSubClassAssertion(e, GoCAM.chebi_dna);	
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
							OWLAxiom eq_prot = go_cam.df.getOWLEquivalentClassesAxiom(e, uniprotein_class);
							go_cam.ontman.addAxiom(go_cam.go_cam_ont, eq_prot);
						}
						//
						else if(xref.getModelInterface().equals(UnificationXref.class)) {
							UnificationXref uref = (UnificationXref)xref;	
							if(uref.getDb().equals("ENSEMBL")) {
								go_cam.addDatabaseXref(e, "ENSEMBL:"+id);
							}
						}
					}
				}
			}
			//rna 
			else if(entity.getModelInterface().equals(Rna.class)) {
				Rna rna = (Rna)entity;
				go_cam.addSubClassAssertion(e, GoCAM.chebi_rna);	
				EntityReference entity_ref = rna.getEntityReference();	
				if(entity_ref!=null) {
					Set<Xref> p_xrefs = entity_ref.getXref();
					for(Xref xref : p_xrefs) {
						//In GO-CAM we almost always want to talk about proteins
						//if there is a uniprot identifier to use, use that before anything else.
						String db = xref.getDb().toLowerCase();
						String id = xref.getId();
						if(db.contains("uniprot")) {
							OWLClass uniprotein_class = go_cam.df.getOWLClass(IRI.create(GoCAM.uniprot_iri + id)); 
							OWLAxiom eq_prot = go_cam.df.getOWLEquivalentClassesAxiom(e, uniprotein_class);
							go_cam.ontman.addAxiom(go_cam.go_cam_ont, eq_prot);
						}
						//
						else if(xref.getModelInterface().equals(UnificationXref.class)) {					
							UnificationXref uref = (UnificationXref)xref;	
							if(uref.getDb().equals("ENSEMBL")) {
								go_cam.addDatabaseXref(e, "ENSEMBL:"+id);
								//TODO if at some point go-cam decides to represent transcripts etc. then we'll update here to use the ensembl etc. ids.  
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
						if(goplus.isChebiRole(chebi_uri)) {
							go_cam.addSubclassAssertion(mlc_class, GoCAM.chemical_role, null);
							OWLNamedIndividual rolei = go_cam.makeAnnotatedIndividual(GoCAM.makeGoCamifiedIRI(model_id, entity_id+"_chemical"));
							go_cam.addTypeAssertion(rolei, mlc_class);									
							//assert entity here is a chemical instance
							go_cam.addSubclassAssertion(e, GoCAM.chemical_entity, null);
							//connect it to the role
							//	go_cam.addRefBackedObjectPropertyAssertion(e, GoCAM.has_role, rolei, dbids, GoCAM.eco_imported_auto, default_namespace_prefix, null, model_id);
							OWLClassExpression role_exp = go_cam.df.getOWLObjectSomeValuesFrom(GoCAM.has_role, (OWLClassExpression)mlc_class);
							OWLAxiom eq_role = go_cam.df.getOWLEquivalentClassesAxiom(e, role_exp);
							go_cam.ontman.addAxiom(go_cam.go_cam_ont, eq_role);
						}else { //presumably its a chemical entity if not a role								
							go_cam.addSubclassAssertion(mlc_class, GoCAM.chemical_entity, null);	
							//assert its a chemical instance
							OWLAxiom eq_mlc = go_cam.df.getOWLEquivalentClassesAxiom(e, mlc_class);
							go_cam.ontman.addAxiom(go_cam.go_cam_ont, eq_mlc);
						}
					}else {
						//no chebi so we don't know what it is (for Noctua) aside from being some kind of chemical entity
						go_cam.addSubclassAssertion(e, GoCAM.chemical_entity, null);
					}
				}
			}
		}
		if(entity_id!=null) {
			id_class_map.put(entity_id, e);
		}
		return e;
	}

	private GoCAM checkForAndAddSet(GoCAM go_cam, String model_id, PhysicalEntity entity_set, OWLClass e) throws IOException {
		if(entity_set.getMemberPhysicalEntity()!=null&&entity_set.getMemberPhysicalEntity().size()>0) {
			Set<PhysicalEntity> parts_list = entity_set.getMemberPhysicalEntity();
			Set<OWLClassExpression> owl_parts = new HashSet<OWLClassExpression>();
			for(PhysicalEntity part : parts_list) {
				OWLClassExpression part_exp = definePhysicalEntity(go_cam, part, null, model_id);
				owl_parts.add(part_exp);
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
			}
		}
		return go_cam;
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

	public static void countPhysical(Model biopax_model) {
		int n_all = 0; int n_complex = 0; int n_set = 0; int n_protein = 0; int n_small_molecule = 0;
		int n_dna = 0; int n_rna = 0; int n_dna_region = 0;  int n_rna_region = 0;
		int n_other = 0; int n_physical = 0; int n_sets = 0;
		int n_sets_of_complexes = 0; int n_sets_of_sets = 0;
		Set<String> set_types = new HashSet<String>();
		for (PhysicalEntity e : biopax_model.getObjects(PhysicalEntity.class)){
			n_all++;
			if(!e.getMemberPhysicalEntity().isEmpty()) {
				n_sets++;
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
			}			
			if(e instanceof Complex) {
				n_complex++;
			}else if(e instanceof Protein) {
				n_protein++;
			}else if(e instanceof SmallMolecule) {
				n_small_molecule++;
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
		System.out.println("n_all\tn_physical\tn_sets\tn_complex\tn_set\tn_protein\tn_small_molecule"
				+"\tn_dna\tn_rna\tn_dna_region\tn_rna_region\tn_other");
		System.out.println( n_all+"\t"+n_physical+"\t"+n_sets+"\t"+n_complex+"\t"+n_set+"\t"+n_protein+"\t"+n_small_molecule 
				+"\t"+n_dna+"\t"+n_rna+"\t"+n_dna_region+"\t"+n_rna_region 
				+"\t"+n_other);
		System.out.println("n_sets_of_complexes = "+n_sets_of_complexes+" n_sets_of_sets = "+n_sets_of_sets);
		System.out.println(set_types);

	}

}
