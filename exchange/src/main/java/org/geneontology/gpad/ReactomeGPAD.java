/**
 * 
 */
package org.geneontology.gpad;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.geneontology.gpad.GPAD.Annotation;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.AxiomType;
import org.semanticweb.owlapi.model.ClassExpressionType;
import org.semanticweb.owlapi.model.OWLAxiom;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLClassExpression;
import org.semanticweb.owlapi.model.OWLEquivalentClassesAxiom;
import org.semanticweb.owlapi.model.OWLObjectIntersectionOf;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyCreationException;
import org.semanticweb.owlapi.model.OWLOntologyManager;

/**
 * @author bgood
 *
 */
public class ReactomeGPAD {
	Map<String, String> reactid_uniprotid;
	Map<String, Set<String>> complexid_uniprotids;
	Map<String, Set<String>> setid_uniprotids;
	/**
	 * @throws OWLOntologyCreationException 
	 * 
	 */
	public ReactomeGPAD(String entity_ontology_file) throws OWLOntologyCreationException {
		reactid_uniprotid = new HashMap<String, String>();
		complexid_uniprotids = new HashMap<String, Set<String>>();
		setid_uniprotids = new HashMap<String, Set<String>>();
		OWLOntologyManager man = OWLManager.createOWLOntologyManager();
		OWLOntology ont = man.loadOntologyFromOntologyDocument(new File(entity_ontology_file));
		Set<OWLEquivalentClassesAxiom> eqs = ont.getAxioms(AxiomType.EQUIVALENT_CLASSES);
		for(OWLEquivalentClassesAxiom eq : eqs) {
			Set<OWLClassExpression> classes = eq.getClassExpressions();
			String uniprot_id = null;
			Set<String> complex_members = null;
			Set<String> set_members = null;
			String named_class = null;
			Set<String> reactids = new HashSet<String>();
			for(OWLClassExpression exp : classes) {
				if(!exp.isAnonymous()) {
					String c_uri = exp.asOWLClass().getIRI().toString();
					named_class = c_uri;
					if(c_uri.contains("uniprot")) {
						uniprot_id = c_uri;
					}else {
						reactids.add(c_uri);
					}
				}else {
					Set<OWLClass> members = decomposeComplexOrSet(exp, null);
					Set<String> member_ids = new HashSet<String>();
					for(OWLClass m : members) {
						member_ids.add(m.getIRI().toString());
					}
					//its a complex
					if(exp.getClassExpressionType().equals(ClassExpressionType.OBJECT_INTERSECTION_OF)) {
						complex_members = member_ids;
					//its a union
					}else if(exp.getClassExpressionType().equals(ClassExpressionType.OBJECT_UNION_OF)) {
						set_members = member_ids;
					}
				}
			}
			//its a protein cluster
			if(uniprot_id!=null&&reactids.size()>0) {
				for(String r : reactids) {
					reactid_uniprotid.put(r, uniprot_id);
				}
			}else if(complex_members!=null){
				complexid_uniprotids.put(named_class, complex_members);
			}else if(set_members!=null) {
				setid_uniprotids.put(named_class, set_members);
			}
		}
		for(String c : complexid_uniprotids.keySet()) {
			Set<String> u = new HashSet<String>();
			for(String id : complexid_uniprotids.get(c)) {
				String uniprot_id = reactid_uniprotid.get(id);
				if(uniprot_id!=null) {
					u.add(uniprot_id);
				}else {
					u.add(id);
				}
			}if(u!=null) {
				complexid_uniprotids.put(c, u);
			}
		}
		for(String c : setid_uniprotids.keySet()) {
			Set<String> u = new HashSet<String>();
			for(String id : setid_uniprotids.get(c)) {
				String uniprot_id = reactid_uniprotid.get(id);
				if(uniprot_id!=null) {
					u.add(uniprot_id);
				}else {
					u.add(id);
				}
			}
			if(u!=null) {
				setid_uniprotids.put(c, u);
			}
		}
	}

	public Set<OWLClass> decomposeComplexOrSet(OWLClassExpression exp, Set<OWLClass> members){
		if(members==null) {
			members = new HashSet<OWLClass>();
		}
		Set<OWLClass> classes = exp.getClassesInSignature();
		for(OWLClass c : classes) {
			if(!c.isAnonymous()) {
				members.add(c);
			}else{
				members = decomposeComplexOrSet(c, members);
			}
		}
		return members;
	}
	
	
	/**
	 * @param args
	 * @throws IOException 
	 * @throws OWLOntologyCreationException 
	 */
	public static void main(String[] args) throws IOException, OWLOntologyCreationException{
		String entity_ontology_file = "/Users/bgood/gocam_ontology/Reactome_physical_entities.owl";
		ReactomeGPAD rgp = new ReactomeGPAD(entity_ontology_file);
		GPAD g = new GPAD();
		Set<Annotation> annos = g.parseFile("/Users/bgood/Desktop/test/gpad/bmp.gpad");
		Set<Annotation> filtered_annos = new HashSet<Annotation>();
		for(Annotation a : annos) {
			String react_id = "http://model.geneontology.org/"+a.DBObjectID;
			String uniprot = rgp.reactid_uniprotid.get(react_id);
			Set<String> setids = rgp.setid_uniprotids.get(react_id);
			Set<String> complexids = rgp.complexid_uniprotids.get(react_id);
			//direct annotation on a protein, map id and pass through
			if(uniprot!=null) {
				a.DBObjectID = uniprot.replace("http://identifiers.org/uniprot/", "");
				a.DB = "UniProtKB";
				filtered_annos.add(a);
			}else if(setids!=null&&setids.size()>0) {
				//annotation on a set
				//each member of the set should get the annotation
				Annotation b = a;
				for(String id : setids) {
					b.DBObjectID = id.replace("http://identifiers.org/uniprot/", "SET ");
					b.DB = "UniProtKB";
					filtered_annos.add(b);
				}
			}else if(complexids!=null&&complexids.size()>0) {
				Annotation b = a;
				if(b.Qualifier.equals("enables")) {
					b.Qualifier = "contributes_to";
				}
				for(String id : complexids) {
					b.DBObjectID = id.replace("http://identifiers.org/uniprot/", "COMPLEX ");
					b.DB = "UniProtKB";
					filtered_annos.add(b);
				}
			}
			 
			//annotation on a complex
			//complex enables function
			//each complex member 'contributes_to' function
			//complex involved_in/part_of process/location
			//each complex member involved_in/part_of process/location

		}
		g.writeFile(filtered_annos, "/Users/bgood/Desktop/test/gpad/bmp-mapped.gpad");
	}

}
