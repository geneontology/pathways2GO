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

	public Set<Annotation> convertReactomeToUniprotEntities(String reactome_gpad_file, String uniprot_gpad_file) throws IOException {
		GPAD g = new GPAD();
		Set<Annotation> annos = g.parseFile(reactome_gpad_file);
		Set<Annotation> filtered_annos = new HashSet<Annotation>();
		for(Annotation a : annos) {
			System.out.println("source "+a.getString());
			String react_id = "http://model.geneontology.org/"+a.DBObjectID;
			if(a.DB.equalsIgnoreCase("UniProtKB")) {
				react_id = "http://identifiers.org/uniprot/"+a.DBObjectID;
			}			
			ProteinBag bag = getProteins(react_id, null);
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
		}
		g.writeFile(filtered_annos, uniprot_gpad_file);
		return filtered_annos;
	}
	
	public void compareAnnotations(Set<GPAD.Annotation> source, Set<GPAD.Annotation> target) {
		int source_count = source.size(); int target_count = target.size();
		System.out.println("Reactome provides: ");
		for(GPAD.Annotation a : source) {
			System.out.println(a.getString());
		}
		Set<GPAD.Annotation> overlap = new HashSet<GPAD.Annotation>(source);
		overlap.retainAll(target);
		int overlap_count = overlap.size();
		System.out.println(source_count+"\t"+overlap_count+"\t"+target_count);
		System.out.println("These are in the reactome output: ");
		for(GPAD.Annotation a : overlap) {
			System.out.println(a.getString());
		}
		System.out.println("These are missing from the reactome output: ");
		source.removeAll(overlap);
		int n_id_present = 0; int n_id_missing = 0;
		for(GPAD.Annotation a : source) {
			Set<String> q_g = new HashSet<String>();
			for(GPAD.Annotation r : target) {
				if(a.DBObjectID.equals(r.DBObjectID)) {
					q_g.add(r.Qualifier+"_"+r.GOID);
				}
			}
			System.out.println(q_g+"\t"+a.getString());
			if(q_g.size()>0) {
				n_id_present++;
			}else{
				n_id_missing++;
			}
		}
		System.out.println("Of "+source.size()+" missing "+n_id_present+" "
				+ "have an annotation with a matching gene id in the target set and"
				+ " "+n_id_missing+" have no target annotations for the gene id");
	}
	
	/**
	 * @param args
	 * @throws IOException 
	 * @throws OWLOntologyCreationException 
	 */
	public static void main(String[] args) throws IOException, OWLOntologyCreationException{
		GPAD g = new GPAD();
		String entity_ontology_file = "/Users/bgood/gocam_ontology/Reactome_physical_entities.owl";
		ReactomeGPAD rgp = new ReactomeGPAD(entity_ontology_file);
		String reactome_gpad_file = "/Users/bgood/Desktop/test/gpad/bmp.gpad";
		String uniprot_gpad_file = "/Users/bgood/Desktop/test/gpad/bmp-mapped.gpad.txt";
		//rgp.convertReactomeToUniprotEntities(reactome_gpad_file, uniprot_gpad_file);	
		String provided_gpad_file = "/Users/bgood/Desktop/test/gpad/bmp-provided.gpad.txt";
		Set<GPAD.Annotation> source_annos = g.parseFile(provided_gpad_file);
		Set<GPAD.Annotation> target_annos = g.parseFile(uniprot_gpad_file);
		rgp.compareAnnotations(source_annos, target_annos);
	}

}
