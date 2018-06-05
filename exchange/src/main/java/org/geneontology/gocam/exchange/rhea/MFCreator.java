/**
 * 
 */
package org.geneontology.gocam.exchange.rhea;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import org.biopax.paxtools.model.level3.BiochemicalReaction;
import org.geneontology.gocam.exchange.App;
import org.geneontology.gocam.exchange.GoCAM;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLAxiom;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLClassExpression;
import org.semanticweb.owlapi.model.OWLDataFactory;
import org.semanticweb.owlapi.model.OWLDataProperty;
import org.semanticweb.owlapi.model.OWLIndividual;
import org.semanticweb.owlapi.model.OWLLiteral;
import org.semanticweb.owlapi.model.OWLObjectProperty;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyCreationException;
import org.semanticweb.owlapi.model.OWLOntologyStorageException;
import org.semanticweb.owlapi.search.EntitySearcher;

/**
 * @author bgood
 *
 */
public class MFCreator {

	public GoCAM go_cam_;
	public static OWLClass SubstanceSet, CatalyticActivity;
	public static OWLObjectProperty has_substance_bag, has_member_part;
	public static OWLDataProperty has_stoichiometry;
	public static String base = "http://purl.obolibrary.org/obo/";
	/**
	 * 
	 */
	public MFCreator(GoCAM go_cam) {
		this.go_cam_ = go_cam;
		SubstanceSet = go_cam_.df.getOWLClass(IRI.create(base+"SubstanceSet"));
		CatalyticActivity = go_cam_.df.getOWLClass(IRI.create("http://purl.obolibrary.org/obo/GO_0003824"));
		has_substance_bag = go_cam_.df.getOWLObjectProperty(IRI.create(base+"has_substance_bag")); 
		has_member_part  = go_cam_.df.getOWLObjectProperty(IRI.create(base+"has_member_part")); 
		has_stoichiometry  = go_cam_.df.getOWLDataProperty(IRI.create(base+"has_stoichiometry")); 
	}

	/**
	 * @param args
	 * @throws OWLOntologyCreationException 
	 * @throws OWLOntologyStorageException 
	 */
	public static void main(String[] args) throws OWLOntologyCreationException, OWLOntologyStorageException {
		String input_go_cam = "/Users/bgood/Desktop/test/tmp/converted-Degradation_of_AXIN.ttl";
		String output_ontology = "/Users/bgood/Desktop/test/tmp/newMFs.ttl";
		GoCAM go_cam = new GoCAM(input_go_cam);
		MFCreator mfc = new MFCreator(go_cam);
		OWLOntology newmfs = mfc.makeMFClassesFromGoCAM(go_cam);
		App.writeOntology(output_ontology, newmfs);
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
							df.getOWLObjectSomeValuesFrom(has_substance_bag, df.getOWLObjectIntersectionOf(SubstanceSet, inputbag)),
							df.getOWLObjectSomeValuesFrom(has_substance_bag, df.getOWLObjectIntersectionOf(SubstanceSet, outputbag)))
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
		OWLClassExpression chemandstoich = go_cam.df.getOWLObjectSomeValuesFrom(has_member_part, 
				go_cam.df.getOWLObjectIntersectionOf(chemclass, go_cam.df.getOWLDataHasValue(has_stoichiometry, stoich)));
		return chemandstoich;
	}
	
}
