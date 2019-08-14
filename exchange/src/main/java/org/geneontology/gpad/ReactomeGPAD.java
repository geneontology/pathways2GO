/**
 * 
 */
package org.geneontology.gpad;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

import org.geneontology.gocam.exchange.GoCAM;
import org.geneontology.gpad.GPAD.Annotation;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.ClassExpressionType;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLClassExpression;
import org.semanticweb.owlapi.model.OWLEquivalentClassesAxiom;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyCreationException;
import org.semanticweb.owlapi.model.OWLOntologyManager;

/**
 * @author bgood
 *
 */
public class ReactomeGPAD {
	GoCAM init = new GoCAM();
	OWLOntology ont;
	OWLOntologyManager man;
	/**
	 * @throws OWLOntologyCreationException 
	 * 
	 */
	public ReactomeGPAD(String entity_ontology_file) throws OWLOntologyCreationException {
		man = OWLManager.createOWLOntologyManager();
		ont = man.loadOntologyFromOntologyDocument(new File(entity_ontology_file));
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


	public class ProteinBag{
		Set<String> uniprot_ids = new HashSet<String>();
		Set<String> checked_ids = new HashSet<String>();
		boolean is_complex = false;
		boolean contains_set = false;
	}

	public ProteinBag getProteins(String id, ProteinBag b) {
		if(b==null) {
			b = new ProteinBag();
		}
		if(b.checked_ids.contains(id)) {
			return b;
		}
		b.checked_ids.add(id);
		//its a protein
		if(id.contains("uniprot")) {
			b.uniprot_ids.add(id);
			return b;
		}
		//its equivalent to a protein
		OWLClass entity = ont.getOWLOntologyManager().getOWLDataFactory().getOWLClass(IRI.create(id));
		Set<OWLEquivalentClassesAxiom> eq = ont.getEquivalentClassesAxioms(entity);
		for(OWLEquivalentClassesAxiom e : eq) {
			Set<OWLClassExpression> eq2exps = e.getClassExpressions();
			for(OWLClassExpression eq2 : eq2exps) {
				if(eq2.isClassExpressionLiteral()) {
					String named = eq2.asOWLClass().getIRI().toString();
					b = getProteins(named, b);
				}else {
					if(eq2.getClassExpressionType().equals(ClassExpressionType.OBJECT_SOME_VALUES_FROM)||
							eq2.getClassExpressionType().equals(ClassExpressionType.OBJECT_INTERSECTION_OF)) {
						b.is_complex = true;
						Set<OWLClass> parts = eq2.getClassesInSignature();
						for(OWLClass part : parts) {
							b = getProteins(part.getIRI().toString(), b);
						}
					}
					if(eq2.getClassExpressionType().equals(ClassExpressionType.OBJECT_UNION_OF)) {
						b.contains_set = true;
						Set<OWLClass> parts = eq2.getClassesInSignature();
						for(OWLClass part : parts) {
							b = getProteins(part.getIRI().toString(), b);
						}
					}
				}


			}


		}

		return b;
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
			System.out.println("source "+a.getString());
			String react_id = "http://model.geneontology.org/"+a.DBObjectID;
			if(a.DB.equalsIgnoreCase("UniProtKB")) {
				react_id = "http://identifiers.org/uniprot/"+a.DBObjectID;
			}			
			ProteinBag bag = rgp.getProteins(react_id, null);
			if(bag.uniprot_ids.size()==0) {
				System.out.println("no map for "+a.DBObjectID);
			}else {
				if(bag.is_complex) {
					for(String id : bag.uniprot_ids) {
						Annotation b = a.clone(a);
						if(b.Qualifier.equals("enables")) {
							b.Qualifier = "contributes_to";
						}
						b.DBObjectID = id.replace("http://identifiers.org/uniprot/", "");
						b.DB = "UniProtKB";
						filtered_annos.add(b);
					}
				}else if(bag.contains_set) {
					//each member of the set should get the annotation
					for(String id : bag.uniprot_ids) {
						Annotation b = a.clone(a);
						b.DBObjectID = id.replace("http://identifiers.org/uniprot/", "");
						b.DB = "UniProtKB";
						filtered_annos.add(b);
					}
				}else {
					for(String id : bag.uniprot_ids) {
						a.DBObjectID = id.replace("http://identifiers.org/uniprot/", "");
						a.DB = "UniProtKB";
						filtered_annos.add(a);
					}
				}
			}

			//				String uniprot = rgp.reactid_uniprotid.get(react_id);
			//				Set<String> setids = rgp.setid_uniprotids.get(react_id);
			//				Set<String> complexids = rgp.complexid_uniprotids.get(react_id);
			//				//direct annotation on a protein, map id and pass through
			//				if(uniprot!=null) {
			//					a.DBObjectID = uniprot.replace("http://identifiers.org/uniprot/", "");
			//					a.DB = "UniProtKB";
			//					filtered_annos.add(a);
			//				}else if(setids!=null&&setids.size()>0) {
			//					//annotation on a set
			//					//each member of the set should get the annotation
			//					Annotation b = a;
			//					for(String id : setids) {
			//						b.DBObjectID = id.replace("http://identifiers.org/uniprot/", "SET ");
			//						b.DB = "UniProtKB";
			//						filtered_annos.add(b);
			//					}
			//				}else if(complexids!=null&&complexids.size()>0) {
			//					Annotation b = a;
			//					if(b.Qualifier.equals("enables")) {
			//						b.Qualifier = "contributes_to";
			//					}
			//					for(String id : complexids) {
			//						b.DBObjectID = id.replace("http://identifiers.org/uniprot/", "COMPLEX ");
			//						b.DB = "UniProtKB";
			//						filtered_annos.add(b);
			//					}
			//				}

			//annotation on a complex
			//complex enables function
			//each complex member 'contributes_to' function
			//complex involved_in/part_of process/location
			//each complex member involved_in/part_of process/location

		}
		g.writeFile(filtered_annos, "/Users/bgood/Desktop/test/gpad/bmp-mapped.gpad");
	}

}
