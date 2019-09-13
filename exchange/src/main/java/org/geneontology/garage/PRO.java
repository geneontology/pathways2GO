/**
 * 
 */
package org.geneontology.garage;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.Set;

import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.ClassExpressionType;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLAxiom;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLClassExpression;
import org.semanticweb.owlapi.model.OWLObjectProperty;
import org.semanticweb.owlapi.model.OWLObjectSomeValuesFrom;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyCreationException;
import org.semanticweb.owlapi.model.OWLOntologyManager;
import org.semanticweb.owlapi.model.OWLOntologyStorageException;
import org.semanticweb.owlapi.model.OWLSubClassOfAxiom;
import org.semanticweb.owlapi.search.EntitySearcher;
import org.semanticweb.owlapi.util.OWLEntityRemover;

/**
 * @author bgood
 *
 */
public class PRO {
	OWLOntologyManager ontman = OWLManager.createOWLOntologyManager();
	OWLOntology pro_ont;
	OWLObjectProperty species_prop;
	OWLClass human_class;
	OWLClass protein_class;
	OWLObjectSomeValuesFrom is_human_axiom;
	OWLEntityRemover remover;


	/**
	 * @throws OWLOntologyCreationException 
	 * 
	 */
	public PRO(String pro_file) throws OWLOntologyCreationException {
		pro_ont = ontman.loadOntologyFromOntologyDocument(new File(pro_file));
		species_prop = ontman.getOWLDataFactory().getOWLObjectProperty(IRI.create("http://purl.obolibrary.org/obo/RO_0002160"));
		human_class = ontman.getOWLDataFactory().getOWLClass(IRI.create("http://purl.obolibrary.org/obo/NCBITaxon_9606"));
		protein_class = ontman.getOWLDataFactory().getOWLClass(IRI.create("http://purl.obolibrary.org/obo/PR_000000001"));
		is_human_axiom = ontman.getOWLDataFactory().getOWLObjectSomeValuesFrom(species_prop, human_class);
		remover = new OWLEntityRemover(Collections.singleton(pro_ont)); 
	}

	/**
	 * @param args
	 * @throws OWLOntologyCreationException 
	 * @throws FileNotFoundException 
	 */
	public static void main(String[] args) throws OWLOntologyCreationException {
		PRO pro = new PRO("/Users/bgood/Desktop/test/REO/pro_reasoned.owl");
		int n_axioms = pro.pro_ont.getAxiomCount();
		Set<OWLClass> all_class = pro.pro_ont.getClassesInSignature();
		int total = all_class.size();
		int n = 0; int n_checked = 0;
		for(OWLClass term : all_class) {
			n_checked++;
			OWLClass species = pro.getSpecies(term);
			if(species!=null&&(!species.equals(pro.human_class))) {
				term.accept(pro.remover);
				n++;
				System.out.println(n+" "+n_checked+" "+total+" removing "+term);
			}
		}
		pro.ontman.applyChanges(pro.remover.getChanges());
		int n_total = pro.pro_ont.getAxiomCount();
		System.out.println("Total axioms: "+n_total);
		System.out.println("Removed "+(n_axioms-n_total));
		try {
			FileOutputStream out = new FileOutputStream("/Users/bgood/Desktop/test/REO/human_pro_reasoned.owl");
			pro.ontman.saveOntology(pro.pro_ont, out);
			out.close();
		} catch (OWLOntologyStorageException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	public OWLClass getSpecies(OWLClass term) {
		OWLClass species = null;		
		Collection<OWLClassExpression> supers = EntitySearcher.getSuperClasses(term, pro_ont);
		Iterator<OWLClassExpression> i = supers.iterator();
		while(i.hasNext()) {
			OWLClassExpression c = i.next();
			if(c.getClassExpressionType().equals(ClassExpressionType.OBJECT_SOME_VALUES_FROM)) {
				OWLObjectSomeValuesFrom test = (OWLObjectSomeValuesFrom)c;
				if(test.getProperty().equals(species_prop)) {
					OWLClassExpression object = test.getFiller();
					species = object.asOWLClass();
					break;
				}
			}
		}
		return species;
	}

	public boolean isHuman(OWLClass term) {
		OWLSubClassOfAxiom s = ontman.getOWLDataFactory().getOWLSubClassOfAxiom(term, is_human_axiom);
		boolean is_human_thing = EntitySearcher.containsAxiom(s, pro_ont,false);
		return is_human_thing;
	}
	
}
