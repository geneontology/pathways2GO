package org.geneontology.gocam.exchange;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.util.Collection;
import java.util.Iterator;
import java.util.Set;

import org.biopax.paxtools.io.BioPAXIOHandler;
import org.biopax.paxtools.io.SimpleIOHandler;
import org.biopax.paxtools.model.BioPAXFactory;
import org.biopax.paxtools.model.BioPAXLevel;
import org.biopax.paxtools.model.Model;
import org.biopax.paxtools.model.level3.BiochemicalReaction;
import org.biopax.paxtools.model.level3.Complex;
import org.biopax.paxtools.model.level3.Pathway;
import org.biopax.paxtools.model.level3.PhysicalEntity;
import org.biopax.paxtools.model.level3.Protein;
import org.biopax.paxtools.model.level3.SmallMolecule;
import org.semanticweb.elk.owlapi.ElkReasonerFactory;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLAxiom;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLClassExpression;
import org.semanticweb.owlapi.model.OWLDataFactory;
import org.semanticweb.owlapi.model.OWLIndividual;
import org.semanticweb.owlapi.model.OWLNamedIndividual;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyCreationException;
import org.semanticweb.owlapi.model.OWLOntologyManager;
import org.semanticweb.owlapi.reasoner.OWLReasoner;
import org.semanticweb.owlapi.reasoner.OWLReasonerFactory;
import org.semanticweb.owlapi.search.EntitySearcher;

public class GOtoBioPAX {

	public GOtoBioPAX() {
		// TODO Auto-generated constructor stub
	}

	public static void main(String[] args) throws OWLOntologyCreationException, FileNotFoundException {
		String input_go_cam = "/Users/bgood/Desktop/test/tmp/converted-Degradation_of_AXIN.ttl";
		GoCAM go_cam = new GoCAM(input_go_cam);
		GOtoBioPAX go2bp = new GOtoBioPAX();
		go2bp.makeBioPAXFromGoCAM(go_cam);
	}
	
	public void makeBioPAXFromGoCAM(GoCAM go_cam) throws OWLOntologyCreationException, FileNotFoundException {
		OWLReasonerFactory reasonerFactory = new ElkReasonerFactory();
		OWLReasoner go_cam_reasoner = reasonerFactory.createReasoner(go_cam.go_cam_ont);	
		
		String output_biopax = "/Users/bgood/Desktop/test/tmp/bp_out.owl";
		BioPAXFactory factory = BioPAXLevel.L3.getDefaultFactory();
		Model biopax = factory.createModel();
		biopax.setXmlBase(GoCAM.base_iri);
		BiochemicalReaction r1 = factory.create(BiochemicalReaction.class, GoCAM.base_iri+"r1");
		biopax.add(r1);
		
		//get the pathways (biological processes)
		Iterator<OWLIndividual> bps = EntitySearcher.getIndividuals(GoCAM.bp_class, go_cam.go_cam_ont).iterator();
		while(bps.hasNext()) {
			OWLNamedIndividual bp = bps.next().asOWLNamedIndividual();
			Pathway pathway = factory.create(Pathway.class, bp.getIRI().toString()); 
			biopax.add(pathway);
			System.out.println("Pathway "+pathway);
			//TODO retrieve a name from GO
			//get their mf/reaction parts 
			Iterator<OWLIndividual> mfs = EntitySearcher.getObjectPropertyValues(bp, GoCAM.has_part, go_cam.go_cam_ont).iterator();
			//TODO run reasoner to get part-ofs (for GO-CAMs generated other ways) or do another query
			while(mfs.hasNext()) {
				OWLNamedIndividual mf = mfs.next().asOWLNamedIndividual();
				BiochemicalReaction reaction = factory.create(BiochemicalReaction.class, mf.getIRI().toString());
				biopax.add(reaction);
				//connect them
				pathway.addPathwayComponent(reaction);
				System.out.println("Reaction "+reaction);
				//get the pieces of the reaction
				Iterator<OWLIndividual> inputs = EntitySearcher.getObjectPropertyValues(mf, GoCAM.has_input, go_cam.go_cam_ont).iterator();
				while(inputs.hasNext()) {
					OWLNamedIndividual e = inputs.next().asOWLNamedIndividual();
					reaction.addLeft(goCamEntityToBioPAXentity(e, factory, biopax, go_cam, go_cam_reasoner));
				}
				Iterator<OWLIndividual> outputs = EntitySearcher.getObjectPropertyValues(mf, GoCAM.has_output, go_cam.go_cam_ont).iterator();
				while(outputs.hasNext()) {
					OWLNamedIndividual e = outputs.next().asOWLNamedIndividual();
					reaction.addRight(goCamEntityToBioPAXentity(e, factory, biopax, go_cam, go_cam_reasoner));
				}
			}
		}
		
		BioPAXIOHandler handler = new SimpleIOHandler();
		FileOutputStream outstream = new FileOutputStream(output_biopax);
		handler.convertToOWL(biopax, outstream);
	}
	
	public PhysicalEntity goCamEntityToBioPAXentity(OWLNamedIndividual e, BioPAXFactory factory, Model biopax, GoCAM go_cam, OWLReasoner go_cam_reasoner) {
		PhysicalEntity entity = null;
		String e_iri = e.getIRI().toString();

		System.out.println(e_iri);		
		Set<OWLClass> types = go_cam_reasoner.getTypes(e, false).getFlattened();
		if(types==null) {
			System.out.println("error, no type found for reaction I/O entity "+e_iri);
			return null;
		}
		//GoCAM.chebi_protein

		if(types.contains(GoCAM.chemical_entity)) {
			SmallMolecule mlc = factory.create(SmallMolecule.class, e_iri);
			biopax.add(mlc);
			System.out.println("small "+mlc);
			return mlc;
		}else if(types.contains(GoCAM.chebi_protein)) {
			Protein protein = factory.create(Protein.class, e_iri);
			biopax.add(protein);
			System.out.println("protein "+protein);
			return protein;
		}else if(types.contains(GoCAM.go_complex)) {
			Complex complex = factory.create(Complex.class, e_iri);
			biopax.add(complex);
			System.out.println("complex "+complex);
			return complex;
		}else {
			entity = factory.create(PhysicalEntity.class, e_iri);
			System.out.println("other "+entity+" "+types);
		}
		return entity;
	}
	
}
