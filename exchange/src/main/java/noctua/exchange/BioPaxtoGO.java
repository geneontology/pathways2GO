/**
 * 
 */
package noctua.exchange;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.Set;

import org.biopax.paxtools.controller.PathAccessor;
import org.biopax.paxtools.impl.MockFactory;
import org.biopax.paxtools.io.BioPAXIOHandler;
import org.biopax.paxtools.io.SimpleIOHandler;
import org.biopax.paxtools.model.BioPAXElement;
import org.biopax.paxtools.model.BioPAXFactory;
import org.biopax.paxtools.model.BioPAXLevel;
import org.biopax.paxtools.model.Model;
import org.biopax.paxtools.model.level3.*;
import org.biopax.paxtools.model.level3.Process;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.io.FileDocumentTarget;
import org.semanticweb.owlapi.model.AddAxiom;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLAnnotation;
import org.semanticweb.owlapi.model.OWLAxiom;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLClassAssertionAxiom;
import org.semanticweb.owlapi.model.OWLDataFactory;
import org.semanticweb.owlapi.model.OWLEntity;
import org.semanticweb.owlapi.model.OWLLiteral;
import org.semanticweb.owlapi.model.OWLNamedIndividual;
import org.semanticweb.owlapi.model.OWLObjectProperty;
import org.semanticweb.owlapi.model.OWLObjectPropertyAssertionAxiom;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyCreationException;
import org.semanticweb.owlapi.model.OWLOntologyManager;
import org.semanticweb.owlapi.model.OWLOntologyStorageException;
import org.semanticweb.owlapi.model.OWLSubClassOfAxiom;
import org.semanticweb.owlapi.vocab.OWLRDFVocabulary;

/**
 * @author bgood
 *
 */
public class BioPaxtoGO {
	public static final IRI noctua_test_iri = IRI.create("http://noctua.berkeleybop.org/download/gomodel:59dc728000000287/owl");
	public static final IRI go_lego_iri = IRI.create("http://purl.obolibrary.org/obo/go/extensions/go-lego.owl");
	public static final IRI obo_iri = IRI.create("http://purl.obolibrary.org/obo/");
	public static final IRI uniprot_iri = IRI.create("http://identifiers.org/uniprot/");
	public static final IRI biopax_iri = IRI.create("http://www.biopax.org/release/biopax-level3.owl#");

	/**
	 * @param args
	 * @throws FileNotFoundException 
	 * @throws OWLOntologyCreationException 
	 * @throws OWLOntologyStorageException 
	 */
	public static void main(String[] args) throws FileNotFoundException, OWLOntologyCreationException, OWLOntologyStorageException {
		BioPaxtoGO bp2g = new BioPaxtoGO();
		String input_biopax = "src/main/resources/reactome/reactome-input-109581.owl";
		String converted = "src/main/resources/reactome/reactome-output-109581.owl";
		bp2g.convert(input_biopax, converted);
	}

	public void convert(String input_biopax, String converted) throws FileNotFoundException, OWLOntologyCreationException, OWLOntologyStorageException  {
		//set up ontology 
		OWLOntologyManager ontman = OWLManager.createOWLOntologyManager();
		OWLOntology go_cam_ont = ontman.createOntology();
		//Will add classes and relations as we need them now. 
		//TODO Work on using imports later to ensure we don't produce incorrect ids..
		OWLDataFactory df = OWLManager.getOWLDataFactory();
		//biological process
		OWLClass bp_class = df.getOWLClass(IRI.create(obo_iri + "GO_0008150")); 
		addLabel(ontman, go_cam_ont, df, bp_class, "Biological Process");
		//continuant 
		OWLClass continuant_class = df.getOWLClass(IRI.create(obo_iri + "BFO_0000002")); 
		addLabel(ontman, go_cam_ont, df, continuant_class, "Continuant");
		//protein
		OWLClass protein_class = df.getOWLClass(IRI.create(biopax_iri + "Protein")); 
		addLabel(ontman, go_cam_ont, df, protein_class, "Protein");
		//reaction
		OWLClass reaction_class = df.getOWLClass(IRI.create(biopax_iri + "Reaction")); 
		addLabel(ontman, go_cam_ont, df, reaction_class, "Reaction");

		//tmp for viewing while debugging
		OWLSubClassOfAxiom prot = df.getOWLSubClassOfAxiom(protein_class, continuant_class);
		ontman.addAxiom(go_cam_ont, prot);
		ontman.applyChanges();

		//part of
		OWLObjectProperty part_of = df.getOWLObjectProperty(IRI.create(obo_iri + "BFO_0000050"));
		addLabel(ontman, go_cam_ont, df, part_of, "part of");
		//has input 
		OWLObjectProperty has_input = df.getOWLObjectProperty(IRI.create(obo_iri + "RO_0002233"));
		addLabel(ontman, go_cam_ont, df, has_input, "has input");
		//has output 
		OWLObjectProperty has_output = df.getOWLObjectProperty(IRI.create(obo_iri + "RO_0002234"));
		addLabel(ontman, go_cam_ont, df, has_output, "has output");
		//directly provides input for (process to process)
		OWLObjectProperty provides_direct_input_for = df.getOWLObjectProperty(IRI.create(obo_iri + "RO_0002413"));
		addLabel(ontman, go_cam_ont, df, provides_direct_input_for, "directly provides input for (process to process)");

		//read biopax pathway(s)
		BioPAXIOHandler handler = new SimpleIOHandler();
		FileInputStream f = new FileInputStream(input_biopax);
		Model model = handler.convertFromOWL(f);
		//list pathways
		for (Pathway currentPathway : model.getObjects(Pathway.class)){
			System.out.println("Pathway:"+currentPathway.getName()); 
			String uri = currentPathway.getUri();
			//make the OWL individual
			OWLNamedIndividual p = df.getOWLNamedIndividual(IRI.create(uri));
			//label it
			for(String pathway_name : currentPathway.getName()) {
				addLabel(ontman, go_cam_ont, df, p, pathway_name);
			}		
			//set a default type of biological process
			OWLClassAssertionAxiom isa_bp = df.getOWLClassAssertionAxiom(bp_class, p);
			ontman.addAxiom(go_cam_ont, isa_bp);
			ontman.applyChanges();
			//dig out any xreferenced GO processes 
			Set<Xref> xrefs = currentPathway.getXref();
			for(Xref xref : xrefs) {
				if(xref.getModelInterface().equals(RelationshipXref.class)) {
					RelationshipXref r = (RelationshipXref)xref;	    			
					//System.out.println(xref.getDb()+" "+xref.getId()+" "+xref.getUri()+"----"+r.getRelationshipType());
					//note that relationship types are not defined beyond text strings like RelationshipTypeVocabulary_gene ontology term for cellular process
					//you just have to know what to do.
					//here we add the referenced GO class as a type.  
					if(r.getDb().equals("GENE ONTOLOGY")) {
						OWLClass xref_go_parent = df.getOWLClass(IRI.create(obo_iri + r.getId().replaceAll(":", "_")));
						//add it into local hierarchy (temp pre inport)
						OWLSubClassOfAxiom tmp = df.getOWLSubClassOfAxiom(xref_go_parent, bp_class);
						ontman.addAxiom(go_cam_ont, tmp);
						OWLClassAssertionAxiom isa_xrefedbp = df.getOWLClassAssertionAxiom(xref_go_parent, p);
						ontman.addAxiom(go_cam_ont, isa_xrefedbp);
						ontman.applyChanges();
					}
				}

			}
			//get any pathway-part of relationships
			for(Pathway parent_pathway : currentPathway.getPathwayComponentOf()) {
				System.out.println("Component of Pathway:"+parent_pathway.getName()); 
				OWLNamedIndividual parent = df.getOWLNamedIndividual(IRI.create(parent_pathway.getUri()));
				OWLObjectPropertyAssertionAxiom add_partof_axiom = df.getOWLObjectPropertyAssertionAxiom(part_of, p, parent);
				AddAxiom addAxiom = new AddAxiom(go_cam_ont, add_partof_axiom);
				ontman.applyChanges(addAxiom);
			}
			//get the pieces of the pathway
			//Process subsumes Pathway and Reaction.  A pathway may have either or both reaction or pathway components.  
			for(Process process : currentPathway.getPathwayComponent()) {
				System.out.println("Process of Pathway:"+process.getName()+" "+process.getModelInterface()); 
				//Reaction if process.getModelInterface()==org.biopax.paxtools.model.level3.BiochemicalReaction
				if(process.getModelInterface().equals(BiochemicalReaction.class)) {
					BiochemicalReaction reaction = (BiochemicalReaction)process;
					String reaction_name = reaction.getDisplayName();
					String reaction_uri = reaction.getUri();
					//label reaction
					OWLNamedIndividual r = df.getOWLNamedIndividual(IRI.create(reaction_uri));
					addLabel(ontman, go_cam_ont, df, r, reaction_name);
					//type it
					OWLClassAssertionAxiom isa_reaction = df.getOWLClassAssertionAxiom(reaction_class, r);
					ontman.addAxiom(go_cam_ont, isa_reaction);
					ontman.applyChanges();				
					//connect reaction to pathway via part of
					OWLObjectPropertyAssertionAxiom add_partof_axiom = df.getOWLObjectPropertyAssertionAxiom(part_of, r, p);
					AddAxiom addAxiom = new AddAxiom(go_cam_ont, add_partof_axiom);
					ontman.applyChanges(addAxiom);

					//Create entities for reaction components
					Set<Entity> entities = reaction.getParticipant();
					for(Entity entity : entities) {
						//add participant
						OWLNamedIndividual e = df.getOWLNamedIndividual(IRI.create(entity.getUri()));
						String entity_name = entity.getDisplayName();
						addLabel(ontman, go_cam_ont, df, e, entity_name);

						//either a Complex or a Protein or a SmallMolecule or Dna DnaRegion Rna RnaRegion 
						//set a type of continuant to start
						//set a default type of biological process
						OWLClassAssertionAxiom isa_continuant = df.getOWLClassAssertionAxiom(continuant_class, e);
						ontman.addAxiom(go_cam_ont, isa_continuant);
						ontman.applyChanges();
						//Protein	
						if(entity.getModelInterface().equals(Protein.class)) {
							Protein protein = (Protein)entity;
							EntityReference entity_ref = protein.getEntityReference();	
							if(entity_ref!=null) {
								Set<Xref> p_xrefs = entity_ref.getXref();
								for(Xref xref : p_xrefs) {
									if(xref.getModelInterface().equals(UnificationXref.class)) {
										UnificationXref uref = (UnificationXref)xref;	
										if(uref.getDb().equals("UniProt")) {
											String id = uref.getId();
											//going from original conversion results here, the uri for uniprot proteins is like http://identifiers.org/uniprot/P09848
											//recent output from prolog converter produces &UniProtIsoform;Q14790 but not sure what the namespace is that is referred to
											//create the specific protein class
											OWLClass uniprotein_class = df.getOWLClass(IRI.create(uniprot_iri + id)); 
											OWLSubClassOfAxiom prot_instance = df.getOWLSubClassOfAxiom(uniprotein_class, protein_class);
											ontman.addAxiom(go_cam_ont, prot_instance);
											ontman.applyChanges();											
											//name the class with the uniprot id for now..
											//NOTE different protein versions are grouped together into the same root class by the conversion
											//e.g. Q9UKV3 gets the uniproteins ACIN1, ACIN1(1-1093), ACIN1(1094-1341)
											addLabel(ontman, go_cam_ont, df, uniprotein_class, id);
											//until something is imported that understands the uniprot entities, assert that they are proteins
											OWLClassAssertionAxiom isa_uniprotein = df.getOWLClassAssertionAxiom(uniprotein_class, e);
											ontman.addAxiom(go_cam_ont, isa_uniprotein);
											ontman.applyChanges();
										}
									}
								}
							}
						}
						//Dna (gene)
						if(entity.getModelInterface().equals(Dna.class)) {
							Dna dna = (Dna)entity;
							EntityReference entity_ref = dna.getEntityReference();	
							if(entity_ref!=null) {
								Set<Xref> p_xrefs = entity_ref.getXref();
								for(Xref xref : p_xrefs) {
									if(xref.getModelInterface().equals(UnificationXref.class)) {
										UnificationXref uref = (UnificationXref)xref;	
										if(uref.getDb().equals("ENSEMBL")) {
											String id = uref.getId();
											//recent output from prolog converter produces 
//											<obo:BFO_0000002 rdf:about="http://www.reactome.org/biopax/63/109581#Dna1"
//												    rdfs:label="NOXA Gene">
//												  <rdf:type rdf:resource="&obo;ENSEMBL_ENSG00000141682"/>
//												</obo:BFO_0000002>
											OWLClass dna_class = df.getOWLClass(IRI.create(obo_iri + id)); 
											OWLSubClassOfAxiom dna_instance = df.getOWLSubClassOfAxiom(dna_class, continuant_class);
											ontman.addAxiom(go_cam_ont, dna_instance);
											ontman.applyChanges();											
											//name the class with the gene id
											addLabel(ontman, go_cam_ont, df, dna_class, id);
											//assert a continuant
											OWLClassAssertionAxiom isa_dna = df.getOWLClassAssertionAxiom(dna_class, e);
											ontman.addAxiom(go_cam_ont, isa_dna);
											ontman.applyChanges();
										}
									}
								}
							}
						}
						//SmallMolecule
						if(entity.getModelInterface().equals(SmallMolecule.class)) {
							SmallMolecule mlc = (SmallMolecule)entity;
							EntityReference entity_ref = mlc.getEntityReference();	
							if(entity_ref!=null) {
								Set<Xref> p_xrefs = entity_ref.getXref();
								for(Xref xref : p_xrefs) {
									if(xref.getModelInterface().equals(UnificationXref.class)) {
										UnificationXref uref = (UnificationXref)xref;	
										if(uref.getDb().equals("ChEBI")) {
											String id = uref.getId().replace(":", "_");
											OWLClass mlc_class = df.getOWLClass(IRI.create(obo_iri + id)); 
											OWLSubClassOfAxiom mlc_instance = df.getOWLSubClassOfAxiom(mlc_class, continuant_class);
											ontman.addAxiom(go_cam_ont, mlc_instance);
											ontman.applyChanges();											
											//name the class with the chebi id
											addLabel(ontman, go_cam_ont, df, mlc_class, id);
											//assert its a chemical instance
											OWLClassAssertionAxiom isa_mlc = df.getOWLClassAssertionAxiom(mlc_class, e);
											ontman.addAxiom(go_cam_ont, isa_mlc);
											ontman.applyChanges();
										}
									}
								}
							}
						}
						//Complex 
						if(entity.getModelInterface().equals(Complex.class)) {
							Complex complex = (Complex)entity;
							//TODO
//							<obo:BFO_0000002 rdf:about="http://www.reactome.org/biopax/63/109581#Complex14"
//								    rdfs:label="TRAF2:TRADD:RIP1(325-671)">
//								  <obo:BFO_0000051 rdf:resource="http://www.reactome.org/biopax/63/109581#Protein10"/>
//								  <obo:BFO_0000051 rdf:resource="http://www.reactome.org/biopax/63/109581#Protein11"/>
//								  <obo:BFO_0000051 rdf:resource="http://www.reactome.org/biopax/63/109581#Protein12"/>
//								  <rdf:type rdf:resource="&obo;GO_0032991"/>
//								</obo:BFO_0000002>
							
						}
						
						//link to participants in reaction
						//biopax#left -> obo:input , biopax#right -> obo:output
						Set<PhysicalEntity> inputs = reaction.getLeft();
						for(PhysicalEntity input : inputs) {
							OWLNamedIndividual input_entity = df.getOWLNamedIndividual(IRI.create(input.getUri()));
							OWLObjectPropertyAssertionAxiom add_input_axiom = df.getOWLObjectPropertyAssertionAxiom(has_input, r, input_entity);
							AddAxiom addInputAxiom = new AddAxiom(go_cam_ont, add_input_axiom);
							ontman.applyChanges(addInputAxiom);
						}
						Set<PhysicalEntity> outputs = reaction.getRight();
						for(PhysicalEntity output : outputs) {
							OWLNamedIndividual output_entity = df.getOWLNamedIndividual(IRI.create(output.getUri()));
							OWLObjectPropertyAssertionAxiom add_output_axiom = df.getOWLObjectPropertyAssertionAxiom(has_output, r, output_entity);
							AddAxiom addOutputAxiom = new AddAxiom(go_cam_ont, add_output_axiom);
							ontman.applyChanges(addOutputAxiom);
						}
						//TODO how to link to other reactions.. 
						//e.g. connection between reaction 2 and 4 below.  
//						<owl:NamedIndividual rdf:about="http://www.reactome.org/biopax/63/109581#BiochemicalReaction2"
//							    rdfs:label="TRAIL-mediated dimerization of procaspase-8">
//							  <obo:RO_0002233 rdf:resource="http://www.reactome.org/biopax/63/109581#Complex7"/>
//							  <obo:RO_0002233 rdf:resource="http://www.reactome.org/biopax/63/109581#Protein4"/>
//							  <obo:BFO_0000050 rdf:resource="http://www.reactome.org/biopax/63/109581#Pathway4"/>
//							  <obo:RO_0002234 rdf:resource="http://www.reactome.org/biopax/63/109581#Complex11"/>
//							  <obo:RO_0002334 rdf:resource="http://www.reactome.org/biopax/63/109581#Protein5"/>
//							  <obo:RO_0002413 rdf:resource="http://www.reactome.org/biopax/63/109581#BiochemicalReaction4"/>
//							</owl:NamedIndividual>
					}
				}
				//Pathway if equals org.biopax.paxtools.model.level3.Pathway
				//ignore subpathways - assume they come on their own
			}
		}	
		//export
		FileDocumentTarget outfile = new FileDocumentTarget(new File(converted));
		ontman.saveOntology(go_cam_ont,outfile);
	}


	public OWLOntology addLabel(OWLOntologyManager ontman, OWLOntology go_cam_ont, OWLDataFactory df, OWLEntity entity, String label) {
		OWLLiteral lbl = df.getOWLLiteral(label);
		OWLAnnotation label_anno = df.getOWLAnnotation(df.getOWLAnnotationProperty(OWLRDFVocabulary.RDFS_LABEL.getIRI()), lbl);
		OWLAxiom labelaxiom = df.getOWLAnnotationAssertionAxiom(entity.getIRI(), label_anno);
		ontman.addAxiom(go_cam_ont, labelaxiom);
		ontman.applyChanges();
		return go_cam_ont;
	}






	private void outputModel(Model m, OutputStream out) {
		(new SimpleIOHandler()).convertToOWL(m, out);
	}
}
