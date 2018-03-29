package org.geneontology.gocam.exchange;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLAnnotation;
import org.semanticweb.owlapi.model.OWLAxiom;
import org.semanticweb.owlapi.model.OWLObjectProperty;
import org.semanticweb.owlapi.model.OWLObjectPropertyAssertionAxiom;
import org.semanticweb.owlapi.util.OWLOntologyWalker;
import org.semanticweb.owlapi.util.OWLOntologyWalkerVisitor;

public class UpdateAnnotationsVisitor extends OWLOntologyWalkerVisitor{

	IRI source_iri;
	IRI prop_iri;
	IRI target_iri;
	Set<OWLAxiom> toremove = new HashSet<OWLAxiom>();
	Set<OWLAnnotation> oldannos = new HashSet<OWLAnnotation>();
	
	public Set<OWLAxiom> getToremove() {
		return toremove;
	}

	public Set<OWLAnnotation> getOldannos() {
		return oldannos;
	}
	
	public UpdateAnnotationsVisitor(OWLOntologyWalker walker, IRI s_iri, IRI p_iri , IRI t_iri) {
		super(walker);
		source_iri = s_iri;
		prop_iri = p_iri;
		target_iri = t_iri;
	}

	@Override
	public void visit(OWLObjectPropertyAssertionAxiom axiom) {
		if(axiom.getSubject().isNamed()&&axiom.getObject().isNamed()){
			IRI sub = axiom.getSubject().asOWLNamedIndividual().getIRI();
			IRI ob = axiom.getObject().asOWLNamedIndividual().getIRI();
			OWLObjectProperty prop = axiom.getProperty().asOWLObjectProperty();
			if(sub.equals(source_iri)&&ob.equals(target_iri)&&prop.getIRI().equals(prop_iri)) {
				oldannos.addAll(axiom.getAnnotations());
				System.out.println("hee hee "+axiom.getAnnotations());
				toremove.add(axiom);
				System.out.println("ha ha "+axiom);
			}
		}		
		return;
	}
	
}
