package org.geneontology.gocam.exchange;

import java.io.File;
import java.util.HashSet;
import java.util.Set;

import org.semanticweb.owlapi.formats.OBODocumentFormat;
import org.semanticweb.owlapi.formats.TurtleDocumentFormat;
import org.semanticweb.owlapi.io.FileDocumentTarget;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLAnnotation;
import org.semanticweb.owlapi.model.OWLAnnotationAssertionAxiom;
import org.semanticweb.owlapi.model.OWLAxiom;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLDataFactory;
import org.semanticweb.owlapi.model.OWLEntity;
import org.semanticweb.owlapi.model.OWLLiteral;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyStorageException;

public class Helper {

	public Helper() {
		// TODO Auto-generated constructor stub
	}
	
	/**
	 * For future use, how to do an ontology import
	 * //		OWLDataFactory df = OWLManager.getOWLDataFactory();
//		OWLImportsDeclaration importDeclaration = df.getOWLImportsDeclaration(go_plus_iri);
//		AddImport importGoPlus =new AddImport(go_cam, importDeclaration);
//		ontman.applyChange(importGoPlus);
//		ontman.loadOntology(go_plus_iri);
	 */
	
	
	public static String owlSetToString(Set<OWLClass> set, OWLOntology ont, String sep) {
		String s = "";
		for(OWLClass e : set) {
			s = s+Helper.getaLabel(e, ont)+sep;
		}
		if(s.length()>0) {
			s = s.substring(0,s.length()-1);
		}
		return s;
	}
	
	public static Set<String> getLabels(String uri, OWLOntology ont){
		Set<String> labels = new HashSet<String>();
		Set<OWLAnnotationAssertionAxiom> annos = ont.getAnnotationAssertionAxioms(IRI.create(uri));
		if(annos==null) {
			return labels;
		}
		for(OWLAnnotationAssertionAxiom a : annos) {
			if(a.getProperty().isLabel()) {
				if(a.getValue() instanceof OWLLiteral) {
					OWLLiteral val = (OWLLiteral) a.getValue();
					labels.add(val.getLiteral());
				}
			}
		}
		return labels;
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
	
	public static String getaLabel(String uri, OWLOntology ont){
		Set<String> labels = getLabels(uri, ont);
		String label = "";
		if(labels!=null&&labels.size()>0) {
			for(String l : labels) {
				label = l;
				break;
			}
		}
		return label;
	}
	
	public static void addLabel(IRI iri, String label, OWLOntology ont) {
		if(label==null) {
			return;
		}		
		OWLDataFactory df = ont.getOWLOntologyManager().getOWLDataFactory();
		OWLLiteral lbl = df.getOWLLiteral(label);
		OWLAnnotation label_anno = df.getOWLAnnotation(GoCAM.rdfs_label, lbl);
		OWLAxiom labelaxiom = df.getOWLAnnotationAssertionAxiom(iri, label_anno);
		ont.getOWLOntologyManager().addAxiom(ont, labelaxiom);
		return;
	}

	public void addComment(IRI iri, String comment, OWLOntology ont) {
		if(comment==null) {
			return;
		}		
		OWLDataFactory df = ont.getOWLOntologyManager().getOWLDataFactory();
		OWLLiteral c = df.getOWLLiteral(comment);
		OWLAnnotation comment_anno = df.getOWLAnnotation(GoCAM.rdfs_comment, c);
		OWLAxiom commentaxiom = df.getOWLAnnotationAssertionAxiom(iri, comment_anno);
		ont.getOWLOntologyManager().addAxiom(ont, commentaxiom);
		return;
	}
	
	public static void writeOntology(String outfile, OWLOntology ont) throws OWLOntologyStorageException {
		FileDocumentTarget outf = new FileDocumentTarget(new File(outfile));
		//ontman.setOntologyFormat(go_cam_ont, new TurtleOntologyFormat());	
		ont.getOWLOntologyManager().setOntologyFormat(ont, new TurtleDocumentFormat());	
		ont.getOWLOntologyManager().saveOntology(ont,outf);
	}

	public static void writeOntologyAsObo(String outfile, OWLOntology ont) throws OWLOntologyStorageException {
		FileDocumentTarget outf = new FileDocumentTarget(new File(outfile));
		//ontman.setOntologyFormat(go_cam_ont, new TurtleOntologyFormat());	
		ont.getOWLOntologyManager().setOntologyFormat(ont, new OBODocumentFormat());	
		ont.getOWLOntologyManager().saveOntology(ont,outf);
	}
}
