package org.geneontology.gocam.exchange.rhea;

import java.util.HashSet;
import java.util.Set;

import org.semanticweb.owlapi.model.OWLClass;

public class ReasoningImpactReport {

	OWLClass topic;
	Set<OWLClass> new_inferred_superclasses;
	Set<OWLClass> recapitulated_inferred_superclasses;
	Set<OWLClass> recapitulated_direct_superclasses;
	
	public ReasoningImpactReport() {
		new_inferred_superclasses = new HashSet<OWLClass>();
		recapitulated_inferred_superclasses  = new HashSet<OWLClass>();
		recapitulated_direct_superclasses  = new HashSet<OWLClass>();
	}

}
