/**
 * 
 */
package org.geneontology.gocam.exchange;

import java.io.File;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLAnnotation;
import org.semanticweb.owlapi.model.OWLAnnotationProperty;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLClassExpression;
import org.semanticweb.owlapi.model.OWLDataFactory;
import org.semanticweb.owlapi.model.OWLLiteral;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyCreationException;
import org.semanticweb.owlapi.model.OWLOntologyManager;
import org.semanticweb.owlapi.reasoner.NodeSet;
import org.semanticweb.owlapi.reasoner.OWLReasoner;
import org.semanticweb.owlapi.reasoner.OWLReasonerFactory;
import org.semanticweb.owlapi.reasoner.structural.StructuralReasonerFactory;
import org.semanticweb.owlapi.search.EntitySearcher;
import org.semarglproject.vocab.OWL;

import com.google.common.base.Optional;

/**
 * @author bgood
 *
 */
public class Onto {
	String go_loc = "/Users/bgood/git/noctua_exchange/exchange/src/main/resources/org/geneontology/gocam/exchange/go-plus-merged.owl";
	OWLOntology go;
	Set<String> chebi_roles;
	Set<String> chebi_chemicals;
	Set<String> deprecated;
	Map<String, String> replaced_by_map;
	String obo_base ="http://purl.obolibrary.org/obo/";
	/**
	 * @throws OWLOntologyCreationException 
	 * 
	 */
	public Onto() throws OWLOntologyCreationException {

		go = loadOntology(go_loc);
		System.out.println("GO axioms "+go.getAxiomCount());
		OWLReasoner go_reasoner = createReasoner(go);
		
		//make list of roles
		OWLClass chebi_role = go.getOWLOntologyManager().getOWLDataFactory().getOWLClass(IRI.create(obo_base+"CHEBI_50906"));
		Set<OWLClass> roles = getSubClasses(chebi_role, false, go_reasoner);
		chebi_roles = new HashSet<String>();
		for(OWLClass r : roles) {
			chebi_roles.add(r.getIRI().toString());
		}
		//make list of chemicals
		OWLClass chebi_chemical = go.getOWLOntologyManager().getOWLDataFactory().getOWLClass(IRI.create(obo_base+"CHEBI_24431"));
		Set<OWLClass> chemicals = getSubClasses(chebi_chemical, false,go_reasoner);
		chebi_chemicals = new HashSet<String>();
		for(OWLClass c : chemicals) {
			chebi_chemicals.add(c.getIRI().toURI().toString());
		}
		//make uber list of deprecated
		OWLClass thing = go.getOWLOntologyManager().getOWLDataFactory().getOWLClass(IRI.create(OWL.THING));
		Set<OWLClass> things = getSubClasses(thing, true, go_reasoner);
		deprecated = new HashSet<String>();
		replaced_by_map = new HashMap<String, String>();
		OWLAnnotationProperty dep = go.getOWLOntologyManager().getOWLDataFactory().getOWLAnnotationProperty(IRI.create(OWL.DEPRECATED));
		OWLAnnotationProperty term_replaced_by = go.getOWLOntologyManager().getOWLDataFactory().getOWLAnnotationProperty(IRI.create(obo_base+"IAO_0100001"));
		for(OWLClass c : things) {
			Collection<OWLAnnotation> annos = EntitySearcher.getAnnotationObjects(c, go, dep);
			for(OWLAnnotation anno : annos) {
				if(anno.isDeprecatedIRIAnnotation()) {
					deprecated.add(c.toString());
					//add to replaced by list if present
					Collection<OWLAnnotation> replaced_by = EntitySearcher.getAnnotationObjects(c, go, term_replaced_by);
					for(OWLAnnotation rep : replaced_by) {
						String rep_iri = rep.getValue().toString();
						replaced_by_map.put(c.getIRI().toString(), rep_iri);
					}
				}
			}
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
		if(deprecated.contains(uri)) {
			return true;
		}else {
			return false;
		}
	}
	
	public String replacementUri(String uri) {
		return replaced_by_map.get(uri);
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
