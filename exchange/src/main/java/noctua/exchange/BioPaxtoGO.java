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


	/**
	 * @param args
	 * @throws FileNotFoundException 
	 * @throws OWLOntologyCreationException 
	 * @throws OWLOntologyStorageException 
	 */
	public static void main(String[] args) throws FileNotFoundException, OWLOntologyCreationException, OWLOntologyStorageException {
		//BioPaxtoGO bp = new BioPaxtoGO();

		//set up ontology 
		OWLOntologyManager ontman = OWLManager.createOWLOntologyManager();
		OWLOntology go_cam_ont = ontman.createOntology();
		//Will add classes and relations as we need them now. 
		//TODO Work on using imports later to ensure we don't produce incorrect ids..
		OWLDataFactory df = OWLManager.getOWLDataFactory();
		OWLClass bp_class = df.getOWLClass(IRI.create(obo_iri + "GO_0008150")); 
		OWLLiteral lbl = df.getOWLLiteral("Biological Process");
		OWLAnnotation label = df.getOWLAnnotation(df.getOWLAnnotationProperty(OWLRDFVocabulary.RDFS_LABEL.getIRI()), lbl);
		OWLAxiom labelaxiom = df.getOWLAnnotationAssertionAxiom(bp_class.getIRI(), label);
		ontman.applyChange(new AddAxiom(go_cam_ont, labelaxiom));

		OWLObjectProperty part_of = df.getOWLObjectProperty(IRI.create(obo_iri + "BFO_0000050"));
		OWLLiteral lbl2 = df.getOWLLiteral("Part Of");
		OWLAnnotation label2 = df.getOWLAnnotation(df.getOWLAnnotationProperty(OWLRDFVocabulary.RDFS_LABEL.getIRI()), lbl2);
		OWLAxiom labelaxiom2 = df.getOWLAnnotationAssertionAxiom(part_of.getIRI(), label2);
		ontman.applyChange(new AddAxiom(go_cam_ont, labelaxiom2));

		//read biopax pathway(s)
		BioPAXIOHandler handler = new SimpleIOHandler();
		FileInputStream f = new FileInputStream("src/main/resources/reactome/reactome-input-109581.owl");
		Model model = handler.convertFromOWL(f);
		//list pathways
		for (Pathway currentPathway : model.getObjects(Pathway.class)){
			System.out.println("Pathway:"+currentPathway.getName()); 
			String uri = currentPathway.getUri();
			//make the OWL individual
			OWLNamedIndividual p = df.getOWLNamedIndividual(IRI.create(uri));
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

			//get any part of relationships
			for(Pathway parent_pathway : currentPathway.getPathwayComponentOf()) {
				System.out.println("Component of Pathway:"+parent_pathway.getName()); 
				OWLNamedIndividual parent = df.getOWLNamedIndividual(IRI.create(parent_pathway.getUri()));
				OWLObjectPropertyAssertionAxiom add_partof_axiom = df.getOWLObjectPropertyAssertionAxiom(part_of, p, parent);
				AddAxiom addAxiom = new AddAxiom(go_cam_ont, add_partof_axiom);
				ontman.applyChanges(addAxiom);
			}
			//get the pieces of the pathway
			//Process subsumes Pathway and Reaction.  A pathway may have either or both reaction or pathway components.  
			//			for(Process process : currentPathway.getPathwayComponent()) {
			//				System.out.println("Process of Pathway:"+process.getName()+" "+process.getModelInterface()); 
			//				//Reaction if process.getModelInterface()==org.biopax.paxtools.model.level3.BiochemicalReaction
			//				//Pathway if equals org.biopax.paxtools.model.level3.Pathway
			//			}
		}	
		//export
		FileDocumentTarget outfile = new FileDocumentTarget(new File("src/main/resources/reactome/test.owl"));
		ontman.saveOntology(go_cam_ont,outfile);
	}


	public final void tutorial() throws FileNotFoundException {
		// Read a BioPAX pathway in using PaxTools

		//read biopax pathway(s)
		BioPAXIOHandler handler = new SimpleIOHandler();
		FileInputStream f = new FileInputStream("src/main/resources/reactome/reactome-input-109581.owl");
		Model model = handler.convertFromOWL(f);
		// Iterate through all BioPAX Elements and print basic info
		//		 Set<BioPAXElement> elementSet = model.getObjects();
		//		 for (BioPAXElement currentElement : elementSet)
		//		 {
		//		  String rdfId = currentElement.getUri();
		//		  String className =	 currentElement.getClass().getName();
		//		  System.out.println("Element: " + rdfId + ": " + className);
		//		 }
		//Just get proteins
		//		Set<Protein> proteinSet = model.getObjects(Protein.class);
		//		for (Protein currentProtein : proteinSet){
		//			System.out.println(currentProtein.getName() +": " + currentProtein.getDisplayName());
		//		}
		//Just get Pathways
		//		Set<Pathway> pathwaySet = model.getObjects(Pathway.class);
		//		for(Pathway pathway : pathwaySet) {
		//			System.out.println(pathway.getDisplayName());
		//		}

		// Set up the Path Accessor
		//				PathAccessor pathAccessor = new PathAccessor(
		//					    "ProteinReference/xref:UnificationXref", BioPAXLevel.L3);

		PathAccessor proteinIDAccessor = new PathAccessor("Pathway/pathwayComponent*/participant*:Protein/entityReference/xref:UnificationXref", BioPAXLevel.L3);

		// Iterate through all proteins in the model
		for (Pathway currentPathway : model.getObjects(Pathway.class))
		{
			System.out.println("Pathway:"+currentPathway.getName()); 
			for(Pathway compOf : currentPathway.getPathwayComponentOf()) {
				System.out.println("Component of Pathway:"+compOf.getName()); 
			}
			//Process subsumes Pathway and Reaction.  A pathway may have either or both reaction or pathway components.  
			for(Process process : currentPathway.getPathwayComponent()) {
				System.out.println("Process of Pathway:"+process.getName()+" "+process.getModelInterface()); 
				//Reaction if process.getModelInterface()==org.biopax.paxtools.model.level3.BiochemicalReaction
				//Pathway if equals org.biopax.paxtools.model.level3.Pathway
			}

			//					Set<Xref> unificationXrefs = proteinIDAccessor.getValueFromBean(currentPathway);
			//					for (Xref currentRef : unificationXrefs){
			//						System.out.println(
			//								"Unification XRef: " + currentRef.getDb() + ": " + currentRef.getId());
			//					}
		}
	}

	public final void testBioPAXDocument()
	{

		String ID_COMPARTMENT_1 = "compartment_1";
		String ID_PROTEIN_1 = "PROTEIN_1";
		String ID_PROTEIN_2 = "PROTEIN_2";
		String ID_PROTEIN_3 = "PROTEIN_3";

		String ID_PROTEIN_REFERENCE_1 = "PROTEIN_REFERENCE_1";
		String ID_PROTEIN_REFERENCE_2 = "PROTEIN_REFERENCE_2";
		String ID_PROTEIN_REFERENCE_3 = "PROTEIN_REFERENCE_3";

		BioPAXFactory level3Factory = BioPAXLevel.L3.getDefaultFactory();
		Model biopaxModel = level3Factory.createModel();

		// Create a compartment
		CellularLocationVocabulary clv =
				biopaxModel.addNew(CellularLocationVocabulary.class, ID_COMPARTMENT_1);
		String compartmentName = "golgi";
		clv.addTerm(compartmentName);


		// Create three proteins in golgi
		Protein p1 = biopaxModel.addNew(Protein.class, ID_PROTEIN_1);
		p1.setDisplayName("PE-Pro-BACE-1");
		Protein p2 = biopaxModel.addNew(Protein.class, ID_PROTEIN_2);
		p2.setDisplayName("PE-BACE-1");
		Protein p3 = biopaxModel.addNew(Protein.class, ID_PROTEIN_3);
		p3.setDisplayName("Furin");
		p1.setCellularLocation(clv);
		p2.setCellularLocation(clv);
		p3.setCellularLocation(clv);

		// Create entity references for the proteins
		ProteinReference pr1 = biopaxModel.addNew(ProteinReference.class, ID_PROTEIN_REFERENCE_1);
		pr1.setStandardName("Pro-BACE-1");
		p1.setEntityReference(pr1);
		Stoichiometry stoichiometry1 = biopaxModel.addNew(Stoichiometry.class, "ST1");
		stoichiometry1.setPhysicalEntity(p1);
		stoichiometry1.setStoichiometricCoefficient(1);

		ProteinReference pr2 = biopaxModel.addNew(ProteinReference.class, ID_PROTEIN_REFERENCE_2);
		pr2.setStandardName("BACE-1");
		p2.setEntityReference(pr2);
		Stoichiometry stoichiometry2 = biopaxModel.addNew(Stoichiometry.class, "ST2");
		stoichiometry2.setPhysicalEntity(p2);
		stoichiometry2.setStoichiometricCoefficient(1);

		ProteinReference pr3 = biopaxModel.addNew(ProteinReference.class, ID_PROTEIN_REFERENCE_3);
		pr3.setStandardName("Furin");
		p3.setEntityReference(pr3);
		Stoichiometry stoichiometry3 = biopaxModel.addNew(Stoichiometry.class, "ST3");
		stoichiometry3.setPhysicalEntity(p3);
		stoichiometry3.setStoichiometricCoefficient(1);

		// Create a reaction involving the three proteins
		BiochemicalReaction r = biopaxModel.addNew(BiochemicalReaction.class, "r1");
		r.addLeft(p1);
		r.addRight(p2);

		Control c = biopaxModel.addNew(Catalysis.class, "cat1"); 
		c.setControlType(ControlType.ACTIVATION);
		c.addControlled(r);


		// Write out the owl file
		try
		{
			System.out.println("test");
			File f = new File(getClass().getClassLoader()
					.getResource("").getPath() + File.separator + "test.owl");
			FileOutputStream anOutputStream = new FileOutputStream(f);
			outputModel(biopaxModel, anOutputStream);
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}
	}

	private void outputModel(Model m, OutputStream out) {
		(new SimpleIOHandler()).convertToOWL(m, out);
	}
}
