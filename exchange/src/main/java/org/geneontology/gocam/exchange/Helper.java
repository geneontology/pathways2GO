package org.geneontology.gocam.exchange;

import java.util.HashSet;
import java.util.Set;

import org.semanticweb.owlapi.model.OWLAnnotationAssertionAxiom;
import org.semanticweb.owlapi.model.OWLEntity;
import org.semanticweb.owlapi.model.OWLLiteral;
import org.semanticweb.owlapi.model.OWLOntology;

public class Helper {

	public Helper() {
		// TODO Auto-generated constructor stub
	}
	
	public static Set<String> getLabels(OWLEntity e, OWLOntology ont){
		Set<String> labels = new HashSet<String>();
		for(OWLAnnotationAssertionAxiom a : ont.getAnnotationAssertionAxioms(e.getIRI())) {
			if(a.getProperty().isLabel()) {
				if(a.getValue() instanceof OWLLiteral) {
					OWLLiteral val = (OWLLiteral) a.getValue();
					labels.add(val.getLiteral());
				}
			}
		}
		return labels;
	}
	
	public static String getaLabel(OWLEntity e, OWLOntology ont){
		Set<String> labels = getLabels(e, ont);
		String label = "";
		if(labels!=null&&labels.size()>0) {
			for(String l : labels) {
				label = l;
				break;
			}
		}
		return label;
	}
}
