/**
 * 
 */
package org.geneontology.gocam.exchange;

import java.io.File;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLClassExpression;
import org.semanticweb.owlapi.model.OWLDataFactory;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyCreationException;
import org.semanticweb.owlapi.model.OWLOntologyManager;
import org.semanticweb.owlapi.reasoner.NodeSet;
import org.semanticweb.owlapi.reasoner.OWLReasoner;
import org.semanticweb.owlapi.reasoner.OWLReasonerFactory;
import org.semanticweb.owlapi.reasoner.structural.StructuralReasonerFactory;

/**
 * @author bgood
 *
 */
public class Onto {
	String chebi_loc = "/Users/bgood/git/noctua_exchange/exchange/src/main/resources/org/geneontology/gocam/exchange/goplus/chebi_import.owl";
	String go_loc = "/Users/bgood/git/noctua_exchange/exchange/src/main/resources/org/geneontology/gocam/exchange/go-plus-merged.owl";
	OWLOntology chebi;
	OWLOntology go;
	Set<String> chebi_roles;
	Set<String> chebi_chemicals;
	Set<String> deprecated;
	Map<String, String> replaced_by;
	String obo_base ="http://purl.obolibrary.org/obo/";
	/**
	 * @throws OWLOntologyCreationException 
	 * 
	 */
	public Onto() throws OWLOntologyCreationException {
		chebi = loadOntology(chebi_loc);
		System.out.println("chebi axioms "+chebi.getAxiomCount());
		OWLReasoner chebi_reasoner = createReasoner(chebi);
		OWLClass chebi_role = chebi.getOWLOntologyManager().getOWLDataFactory().getOWLClass(IRI.create(obo_base+"CHEBI_50906"));
		Set<OWLClass> roles = getSubClasses(chebi_role, false, chebi_reasoner);
		chebi_roles = new HashSet<String>();
		for(OWLClass r : roles) {
			chebi_roles.add(r.getIRI().toString());
		}
		OWLClass chebi_chemical = chebi.getOWLOntologyManager().getOWLDataFactory().getOWLClass(IRI.create(obo_base+"CHEBI_24431"));
		Set<OWLClass> chemicals = getSubClasses(chebi_chemical, false, chebi_reasoner);
		chebi_chemicals = new HashSet<String>();
		for(OWLClass c : chemicals) {
			chebi_chemicals.add(c.getIRI().toURI().toString());
		}
	}

	/**
	 * @param args
	 * @throws OWLOntologyCreationException 
	 */
	public static void main(String[] args) throws OWLOntologyCreationException {
		Onto o = new Onto();

	}

	public boolean isDeprecated(String uri) {
		return true; //TODO
	}
	
	public String replacementUri(String uri) {
		return null;
	}
	
	public boolean isChebiRole(String uri) {
		if(chebi_roles.contains(uri)) {
			return true;
		}else {
			return false;
		}
	}
	
	public boolean isChebiChemical(String uri) {
		if(chebi_chemicals.contains(uri)) {
			return true;
		}else {
			return false;
		}
	}
	
    OWLReasoner createReasoner(OWLOntology rootOntology) {
        // Create a reasoner factory.
    		//just doing simple class hierarchies..
        OWLReasonerFactory reasonerFactory = new StructuralReasonerFactory();
        return reasonerFactory.createReasoner(rootOntology);
    }
    
    OWLOntology loadOntology(String ontf) throws OWLOntologyCreationException {
		OWLOntologyManager man = OWLManager.createOWLOntologyManager();
		OWLDataFactory df = man.getOWLDataFactory();
		OWLOntology ont = man.loadOntologyFromOntologyDocument(new File(ontf));
		return ont;
    }
    
    Set<OWLClass> getSubClasses(OWLClassExpression classExpression, boolean direct, OWLReasoner reasoner) {
        NodeSet<OWLClass> subClasses = reasoner.getSubClasses(classExpression, direct);
        return subClasses.getFlattened();
    }
    
}
