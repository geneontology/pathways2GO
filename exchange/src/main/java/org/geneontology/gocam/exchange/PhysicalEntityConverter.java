/**
 * 
 */
package org.geneontology.gocam.exchange;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

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
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLClassExpression;
import org.semanticweb.owlapi.model.OWLNamedIndividual;
import org.semanticweb.owlapi.model.OWLObjectUnionOf;
import org.semanticweb.owlapi.model.OWLOntologyCreationException;
import org.semanticweb.owlapi.model.OWLOntologyStorageException;
import org.semanticweb.owlapi.search.EntitySearcher;

/**
 * Handle the conversion of physical entities proteins, protein complexes, sets, etc. from BioPAX files
 * into physical entities in go-cams.
 * @author bgood
 *
 */
public class PhysicalEntityConverter {

	GOPlus goplus;
	String default_namespace_prefix;
	boolean preserve_sets_in_complexes = true;
	/**
	 * 
	 */
	public PhysicalEntityConverter(GOPlus go_plus, String default_namespace_prefix_) {
		goplus = go_plus;
		default_namespace_prefix = default_namespace_prefix_;
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

		PhysicalEntityConverter converter = new PhysicalEntityConverter(new GOPlus(), "Reactome");

		String input_biopax = 
				"/Users/bgood/Desktop/test/biopax/Homo_sapiens_march25_2019.owl";
		//"/Users/bgood/Downloads/450294.owl";
		String converted = 
				//"/Users/bgood/Desktop/test/go_cams/Wnt_complete_2018-";
				"/Users/bgood/Desktop/test/go_cams/physical/reactome-homosapiens-";

		BioPAXIOHandler handler = new SimpleIOHandler();
		FileInputStream f = new FileInputStream(input_biopax);
		Model biopax_model = handler.convertFromOWL(f);

		String base_ont_title = "Reactome Physical Entities";
		String base_contributor = "https://orcid.org/0000-0002-7334-7852";
		String base_provider = "https://reactome.org";
		boolean add_lego_import = false;
		int n = 0;
		for (PhysicalEntity entity : biopax_model.getObjects(PhysicalEntity.class)){
			String model_id = entity.hashCode()+"";
			String iri = "http://model.geneontology.org/"+model_id;
			IRI ont_iri = IRI.create(iri);
			base_ont_title = entity.getDisplayName();
			if(base_ont_title==null) {
				base_ont_title = "no name for "+entity.getUri();
			}
			if(base_ont_title.equals("ERBB2 heterodimers")) {
				GoCAM go_cam = new GoCAM(ont_iri, base_ont_title, base_contributor, null, base_provider, add_lego_import);
				n++;
				//55S ribosome:mRNA:fMet-tRNA
				System.out.println(n+" defining "+base_ont_title+" "+entity.getModelInterface());
				converter.definePhysicalEntity(go_cam, entity, null, model_id);
				go_cam.qrunner = new QRunner(go_cam.go_cam_ont); 
				String name = base_ont_title;
				name = name.replaceAll("/", "-");	
				name = name.replaceAll(" ", "_");
				name = name.replaceAll(",", "_");
				if(name.length()>200) {
					name = name.substring(0, 200);
					name+="---";
				}
				String outfilename = converted+name+"_preserve_sets.ttl";
				go_cam.writeGoCAM_jena(outfilename, false);
			}
		}
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

	private void definePhysicalEntity(GoCAM go_cam, PhysicalEntity entity, IRI this_iri, String model_id) throws IOException {
		String entity_id = BioPaxtoGO.getEntityReferenceId(entity);
		if(this_iri==null) {			
			this_iri = GoCAM.makeGoCamifiedIRI(model_id, entity_id);
		}
		Set<String> dbids = new HashSet<String>();
		dbids.add(model_id);
		//add entity to ontology, whatever it is
		OWLNamedIndividual e = go_cam.makeAnnotatedIndividual(this_iri);

		//check specifically for Reactome id
		String reactome_entity_id = "";
		for(Xref xref : entity.getXref()) {
			if(xref.getModelInterface().equals(UnificationXref.class)) {
				UnificationXref r = (UnificationXref)xref;	    			
				if(r.getDb().equals("Reactome")) {
					reactome_entity_id = r.getId();
					if(reactome_entity_id.startsWith("R-HSA")) {
						go_cam.addDatabaseXref(e, reactome_entity_id);
						dbids.add(reactome_entity_id);
						break;
					}
				}
			}
		}		
		//this allows linkage between different OWL individuals in the GO-CAM sense that correspond to the same thing in the BioPax sense
		go_cam.addUriAnnotations2Individual(e.getIRI(),GoCAM.skos_exact_match, IRI.create(entity.getUri()));	
		//check for annotations
		//	Set<String> pubids = getPubmedIds(entity);		
		String entity_name = entity.getDisplayName();
		go_cam.addLabel(e, entity_name);
		//attempt to localize the entity (only if Physical Entity because that is how BioPAX views existence in space)
		if(entity instanceof PhysicalEntity) {
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
							//							if(deprecated) {
							//								report.deprecated_classes.add(entity.getDisplayName()+"\t"+xref_go_loc.getIRI().toString()+"\tCC");
							//							}
							Set<XReferrable> refs = uref.getXrefOf();							
							for(XReferrable ref : refs) {
								location_term = ref.toString().replaceAll("CellularLocationVocabulary_", "");
								break;
							}
							if(location_term!=null) {
								OWLNamedIndividual loc_e = go_cam.makeAnnotatedIndividual(GoCAM.makeRandomIri(model_id));
								go_cam.addLabel(xref_go_loc, location_term);
								go_cam.addTypeAssertion(loc_e, xref_go_loc);
								go_cam.addRefBackedObjectPropertyAssertion(e, GoCAM.located_in, loc_e, dbids, GoCAM.eco_imported_auto, default_namespace_prefix, null, model_id);		
								//								if(strategy == ImportStrategy.NoctuaCuration) {
								go_cam.addLiteralAnnotations2Individual(e.getIRI(), GoCAM.rdfs_comment, "located_in "+location_term);
								//								}
								break; //there can be only one 
							}
						}
					}
				}
			}
			//now get more specific type information
			//Protein or entity set
			if(entity.getModelInterface().equals(Protein.class)||entity.getModelInterface().equals(PhysicalEntity.class)) {
				String id = null;				
				if(entity.getModelInterface().equals(Protein.class)) {
					Protein protein = (Protein)entity;
					id = getUniprotProteinId(protein);
				}			
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
					PhysicalEntity entity_set = (PhysicalEntity)entity;
					Set<PhysicalEntity> prot_parts_ = entity_set.getMemberPhysicalEntity();
					Set<PhysicalEntity> prot_parts = new HashSet<PhysicalEntity>();
					prot_parts = flattenNest(prot_parts_, prot_parts, preserve_sets_in_complexes);
					Set<OWLClassExpression> prot_classes = new HashSet<OWLClassExpression>();
					if(prot_parts!=null) {					
						//if its made of parts and not otherwise typed, call it a Union.	
						for(PhysicalEntity prot_part : prot_parts) {
							//limit to proteins
							if(prot_part instanceof Protein) {
								String uniprot_id = getUniprotProteinId((Protein)prot_part);
								if(uniprot_id!=null) {
									OWLClass uniprotein_class = go_cam.df.getOWLClass(IRI.create(GoCAM.uniprot_iri + uniprot_id)); 
									prot_classes.add(uniprotein_class);
								}else {
									//then its probably an embedded set 
									if(prot_part.getMemberPhysicalEntity().size()>1) {
										Set<OWLClass> set_classes = new HashSet<OWLClass>();
										for(PhysicalEntity member : prot_part.getMemberPhysicalEntity()) {
											String member_uniprot_id = getUniprotProteinId((Protein)member);											
											if(member_uniprot_id!=null) {
												OWLClass uniprotein_class = go_cam.df.getOWLClass(IRI.create(GoCAM.uniprot_iri + member_uniprot_id)); 
												set_classes.add(uniprotein_class);
											}
										}
										OWLObjectUnionOf union_exp = go_cam.df.getOWLObjectUnionOf(set_classes);
										go_cam.addTypeAssertion(e,  union_exp);	
										prot_classes.add(union_exp);
									}
								}							
							}
						}
						if(prot_classes.size()>1) {
							OWLObjectUnionOf union_exp = go_cam.df.getOWLObjectUnionOf(prot_classes);
							go_cam.addTypeAssertion(e,  union_exp);						
						}else if(prot_classes.size()==1){
							OWLClassExpression one_protein = prot_classes.iterator().next();
							go_cam.addTypeAssertion(e,  one_protein);
						}
					}else { //punt..
						go_cam.addTypeAssertion(e,  GoCAM.chebi_protein);
					}
				}
			}
			//Dna (gene)
			else if(entity.getModelInterface().equals(Dna.class)) {
				Dna dna = (Dna)entity;
				go_cam.addTypeAssertion(e, GoCAM.chebi_dna);	
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
						//
						else if(xref.getModelInterface().equals(UnificationXref.class)) {
							UnificationXref uref = (UnificationXref)xref;	
							if(uref.getDb().equals("ENSEMBL")) {
								go_cam.addDatabaseXref(e, "ENSEMBL:"+id);
								//								OWLClass gene_class = go_cam.df.getOWLClass(IRI.create(GoCAM.obo_iri + id)); 
								//								go_cam.addSubclassAssertion(gene_class, GoCAM.chebi_dna, null);										
								//								//name the class with the gene id
								//								go_cam.addLabel(gene_class, id);
								//								//assert a continuant
								//								go_cam.addTypeAssertion(e, gene_class);
							}
						}
					}
				}
			}
			//rna 
			else if(entity.getModelInterface().equals(Rna.class)) {
				Rna rna = (Rna)entity;
				go_cam.addTypeAssertion(e, GoCAM.chebi_rna);	
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
							go_cam.addSubclassAssertion(uniprotein_class, GoCAM.chebi_protein, null);
							go_cam.addTypeAssertion(e, uniprotein_class);
						}
						//
						else if(xref.getModelInterface().equals(UnificationXref.class)) {					
							UnificationXref uref = (UnificationXref)xref;	
							if(uref.getDb().equals("ENSEMBL")) {
								go_cam.addDatabaseXref(e, "ENSEMBL:"+id);
								//TODO if at some point go-cam decides to represent transcripts etc. then we'll update here to use the ensembl etc. ids.  
								//OWLClass gene_class = go_cam.df.getOWLClass(IRI.create(GoCAM.obo_iri + id)); 
								//go_cam.addSubclassAssertion(gene_class, GoCAM.chebi_rna, null);										
								//name the class with the gene id
								//go_cam.addLabel(gene_class, id);
								//assert a continuant
								//go_cam.addTypeAssertion(e, gene_class);
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
						//						if(deprecated) {
						//							report.deprecated_classes.add(entity.getDisplayName()+"\t"+chebi_uri+"\tchebi");
						//						}
						String chebi_report_key;
						if(goplus.isChebiRole(chebi_uri)) {
							go_cam.addSubclassAssertion(mlc_class, GoCAM.chemical_role, null);
							OWLNamedIndividual rolei = go_cam.makeAnnotatedIndividual(GoCAM.makeGoCamifiedIRI(model_id, entity_id+"_chemical"));
							go_cam.addTypeAssertion(rolei, mlc_class);									
							//assert entity here is a chemical instance
							go_cam.addTypeAssertion(e, GoCAM.chemical_entity);
							//connect it to the role
							go_cam.addRefBackedObjectPropertyAssertion(e, GoCAM.has_role, rolei, dbids, GoCAM.eco_imported_auto, default_namespace_prefix, null, model_id);
							chebi_report_key = chebi_uri+"\t"+entity.getDisplayName()+"\trole";
						}else { //presumably its a chemical entity if not a role								
							go_cam.addSubclassAssertion(mlc_class, GoCAM.chemical_entity, null);	
							//assert its a chemical instance
							go_cam.addTypeAssertion(e, mlc_class);
							chebi_report_key =  chebi_uri+"\t"+entity.getDisplayName()+"\tchemical";
						}
						//count it for report because suspect these might be problems to fix
						//						Integer ncheb = report.chebi_count.get(chebi_report_key);
						//						if(ncheb==null) {
						//							ncheb = 0;
						//						}
						//						ncheb++;
						//						report.chebi_count.put(chebi_report_key, ncheb);
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
				Set<PhysicalEntity> complex_parts = flattenNest(level1, null, preserve_sets_in_complexes);

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
				//Now decide if, in GO-CAM, it should be a complex or not
				//If the complex has only 1 protein or only forms of the same protein, then just call it a protein
				//Otherwise go ahead and make the complex
				if(prots.size()==1&&prots.size()==complex_parts.size()&&id!=null) {
					//assert it as one protein 
					OWLClass uniprotein_class = go_cam.df.getOWLClass(IRI.create(GoCAM.uniprot_iri + id)); 
					go_cam.addSubclassAssertion(uniprotein_class, GoCAM.chebi_protein, null);										
					//until something is imported that understands the uniprot entities, assert that they are proteins
					go_cam.addTypeAssertion(e, uniprotein_class);
				}else {
					//note that complex.getComponent() apparently violates the rules in its documentation which stipulate that it should return
					//a flat representation of the parts of the complex (e.g. proteins) and not nested complexes (which the reactome biopax does here)
					Set<String> cnames = new HashSet<String>();
					//	Set<OWLNamedIndividual> owl_members = new HashSet<OWLNamedIndividual>();
					go_cam.addTypeAssertion(e, GoCAM.go_complex);
					for(PhysicalEntity component : complex_parts) {
						//hook up parts	
						if(component.getModelInterface().equals(Complex.class)){
							System.out.println("No nested complexes please");
							System.exit(0);
						}else {
							//its a set
							if(component.getMemberPhysicalEntity().size()>0) {
								System.out.println("Looks like a set: "+e);
							}
							cnames.add(component.getDisplayName());
							IRI comp_uri = GoCAM.makeRandomIri(model_id);
							OWLNamedIndividual component_entity = go_cam.df.getOWLNamedIndividual(comp_uri);
							definePhysicalEntity(go_cam, component, comp_uri, model_id);
							go_cam.addRefBackedObjectPropertyAssertion(e, GoCAM.has_part, component_entity, dbids, GoCAM.eco_imported_auto, default_namespace_prefix, null, model_id);
						}
					}
					//not doing this anymore assert it as an intersection of parts
					//	Set<OWLClassExpression> part_classes = new HashSet<OWLClassExpression>();
					//assert it as a complex - needed for correct inference (without loading up the subclass assertion in the above)
					//go_cam.addTypeAssertion(e, GoCAM.go_complex);
					//intersection of complex and 
					//	part_classes.add(GoCAM.go_complex);
					//	for(OWLNamedIndividual member : owl_members) {
					//		Collection<OWLClassExpression> types = EntitySearcher.getTypes(member, go_cam.go_cam_ont);
					//		for(OWLClassExpression type : types) {
					//			if(!type.asOWLClass().getIRI().toString().equals(OWL.NAMED_INDIVIDUAL)) {
					//				OWLClassExpression hasPartPclass = go_cam.df.getOWLObjectSomeValuesFrom(GoCAM.has_part, type);
					//				part_classes.add(hasPartPclass);
					//			}
					//		}
					//		go_cam.deleteOwlEntityAndAllReferencesToIt(member);
					//	}
					//build intersection class 
					//	OWLObjectIntersectionOf complex_class = go_cam.df.getOWLObjectIntersectionOf(part_classes);
					//	go_cam.addTypeAssertion(e,  complex_class);
				}
			}
			//make sure all physical things are minimally typed as a continuant
			Collection<OWLClassExpression> ptypes = EntitySearcher.getTypes(e, go_cam.go_cam_ont);		
			if(ptypes.isEmpty()) {
				if(go_cam.getaLabel(e).contains("unfolded protein")) {
					go_cam.addTypeAssertion(e, GoCAM.unfolded_protein);
				}else {
					go_cam.addTypeAssertion(e, GoCAM.continuant_class);
				}
			}
		}

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
