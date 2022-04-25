package org.geneontology.gocam.exchange;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.biopax.paxtools.model.level3.RelationshipXref;
import org.biopax.paxtools.model.level3.Xref;
import org.semanticweb.owlapi.formats.OBODocumentFormat;
import org.semanticweb.owlapi.formats.TurtleDocumentFormat;
import org.semanticweb.owlapi.io.FileDocumentTarget;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLAnnotation;
import org.semanticweb.owlapi.model.OWLAnnotationAssertionAxiom;
import org.semanticweb.owlapi.model.OWLAnnotationProperty;
import org.semanticweb.owlapi.model.OWLAxiom;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLDataFactory;
import org.semanticweb.owlapi.model.OWLEntity;
import org.semanticweb.owlapi.model.OWLLiteral;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyStorageException;
import org.semanticweb.owlapi.search.EntitySearcher;

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
	
	public static Set<String> getAnnotations(OWLEntity e, OWLOntology ont, OWLAnnotationProperty anno_prop){
		Set<String> values = new HashSet<String>();
		Collection<OWLAnnotation> annos = EntitySearcher.getAnnotationObjects(e, ont, anno_prop);
		for(OWLAnnotation a : annos) {
			if(a.getValue() instanceof OWLLiteral) {
				OWLLiteral val = (OWLLiteral) a.getValue();
				values.add(val.getLiteral());
			}
		}
		return values;
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
		String label = null;
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
		String label = null;
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
	
	public static Map<String, String> parseGPI(String gpiFile) throws IOException {
		Map<String, String> idLookup = new HashMap<String, String>();
		
		//neo needs e.g.
		//http://identifiers.org/sgd/S000001024
		//we get e.g.
		//YeastCyc YIL155C-MONOMER
		//we get the mapping from a YeastCyc GPI file downloaded from http://sgd-archive.yeastgenome.org/curation/literature/ 
		//e.g. http://sgd-archive.yeastgenome.org/curation/literature/gp_information.559292_sgd.gpi.gz
		InputStream stream = Helper.class.getResourceAsStream(gpiFile);
		BufferedReader reader = new BufferedReader(new InputStreamReader(stream));
		String line = reader.readLine();
		while(line!=null) {
			if(line.startsWith("!")) {
				line = reader.readLine();
			}else {
				String[] cols = line.split("	");
				String yeastcyc = cols[4];
				Set<String> yeastcyc_ids = new HashSet<String>();
				if(yeastcyc.contains("|")) {
					String[] ids = yeastcyc.split("\\|");
					for(String id : ids) {
						yeastcyc_ids.add(id);
					}
				}else {
					yeastcyc_ids.add(yeastcyc);
				}
				if(cols[0].contentEquals("ComplexPortal")) {
					for(String yeastacc : yeastcyc_ids) {
						idLookup.put(yeastacc, "http://purl.obolibrary.org/obo/ComplexPortal_"+cols[1]);
					}	
				}else if(cols.length>=8) {
					String sgd = cols[8];
					Set<String> sgd_ids = new HashSet<String>();
					if(sgd.contains("|")) {
						String[] ids = sgd.split("\\|");
						for(String id : ids) {
							sgd_ids.add(id);
						}
					}else {
						sgd_ids.add(sgd);
					}				
					for(String yeastacc : yeastcyc_ids) {
						for(String sgdid : sgd_ids) {
							idLookup.put(yeastacc, sgdid.replace("SGD:", "http://identifiers.org/sgd/"));
						}
					}					
				}
				line = reader.readLine();
			}
		}
		reader.close();
		
		return idLookup;
	}
	
	public static Map<String, String> parseSgdIdToEcFile(String sgdIdToEcFilePath) throws IOException {
		Map<String, Set<String>> ecLookup = new HashMap<String, Set<String>>();  // First track SGDIDs having multiple EC mappings
		
		InputStream stream = Helper.class.getResourceAsStream(sgdIdToEcFilePath);
		BufferedReader sgd2ECReader = new BufferedReader(new InputStreamReader(stream));
		String sgdLine = sgd2ECReader.readLine();
		while(sgdLine!=null) {
			String[] cols = sgdLine.split("	");
			String yeastcyc = cols[1];
			String ecNumber = cols[5];
			if(!ecLookup.containsKey(yeastcyc)) {
				ecLookup.put(yeastcyc, new HashSet<String>());
			}
			if(!ecLookup.get(yeastcyc).contains(ecNumber)) {
				ecLookup.get(yeastcyc).add(ecNumber);
			}
			
			sgdLine = sgd2ECReader.readLine();
		}
		sgd2ECReader.close();
		
		Map<String, String> cleanedEcLookup = new HashMap<String, String>();
		for(Map.Entry<String, Set<String>> ecMapping : ecLookup.entrySet()) {
			String yeastcyc = ecMapping.getKey();
			Set<String> ecNumbers = ecMapping.getValue();
			// Ensure only 1:1 mappings are used
			if(ecNumbers.size() == 1) {
				cleanedEcLookup.put(yeastcyc, ecNumbers.iterator().next());
			}
		}
		return cleanedEcLookup;
	}
	
	public static HashSet<String> extractGoTermsFromXrefs(Set<Xref> xrefs) {
		HashSet<String> extractedGos = new HashSet<String>();
		for(Xref xref : xrefs) {
			//dig out any xreferenced GO processes and assign them as types
			if(xref.getModelInterface().equals(RelationshipXref.class)) {
				RelationshipXref r = (RelationshipXref)xref;	    			
				//System.out.println(xref.getDb()+" "+xref.getId()+" "+xref.getUri()+"----"+r.getRelationshipType());
				//note that relationship types are not defined beyond text strings like RelationshipTypeVocabulary_gene ontology term for cellular process
				//you just have to know what to do.
				//here we add the referenced GO class as a type.  
				String db = r.getDb().toLowerCase();
				if(db.contains("gene ontology")) {
					String goid = r.getId().replaceAll(":", "_");
					//record mappings
					extractedGos.add(goid);
				}
			}
		}
		return extractedGos;
	}
}
