/**
 * 
 */
package org.geneontology.gocam.exchange;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
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
	OWLOntologyManager ontman;
	OWLOntology pro_ont;
	OWLObjectProperty only_in_taxon;
	OWLObjectProperty in_taxon;
	OWLClass human_class;
	OWLClass protein_class;
	OWLObjectSomeValuesFrom is_human_axiom;
	OWLObjectSomeValuesFrom is_only_human_axiom;
	OWLEntityRemover remover;


	/**
	 * @throws OWLOntologyCreationException 
	 * 
	 */
	public PRO(String pro_file) throws OWLOntologyCreationException {
		ontman =  OWLManager.createOWLOntologyManager();
		System.out.println("Loading "+pro_file);
		pro_ont = ontman.loadOntologyFromOntologyDocument(new File(pro_file));
		System.out.println("done loading");
		in_taxon = ontman.getOWLDataFactory().getOWLObjectProperty(IRI.create("http://purl.obolibrary.org/obo/RO_0002162"));
		only_in_taxon = ontman.getOWLDataFactory().getOWLObjectProperty(IRI.create("http://purl.obolibrary.org/obo/RO_0002160"));
		human_class = ontman.getOWLDataFactory().getOWLClass(IRI.create("http://purl.obolibrary.org/obo/NCBITaxon_9606"));
		protein_class = ontman.getOWLDataFactory().getOWLClass(IRI.create("http://purl.obolibrary.org/obo/PR_000000001"));
		is_only_human_axiom = ontman.getOWLDataFactory().getOWLObjectSomeValuesFrom(only_in_taxon, human_class);
		is_human_axiom = ontman.getOWLDataFactory().getOWLObjectSomeValuesFrom(in_taxon, human_class);

		remover = new OWLEntityRemover(Collections.singleton(pro_ont)); 
	}

	/**
	 * @param args
	 * @throws OWLOntologyCreationException 
	 * @throws IOException 
	 */
	public static void main(String[] args) throws OWLOntologyCreationException, IOException {
		PRO pro = new PRO("/Users/bgood/gocam_ontology/go-lego-merged-9-23-2019.owl");
		String human_specific = "/Users/bgood/gocam_ontology/go-lego-merged-9-23-2019-human.owl";
		pro.makeSpeciesSpecificPRO(pro.human_class, human_specific);

	//	String mapping = "/Users/bgood/Desktop/test/REO/promapping.txt";
	//	Map<String, Set<String>> exact_map = readReact2PRO(mapping, "exact");
	//	Map<String, Set<String>> isa_map = readReact2PRO(mapping, "is_a");
	//	System.out.println(exact_map.size()+" "+isa_map.size());
	}

	/**
PR:000000031	Reactome:R-HSA-1027362	is_a
PR:000000031	Reactome:R-MMU-1027362	is_a
or exact
	 * @param mapping_file
	 * @return
	 * @throws IOException 
	 */
	public static Map<String, Set<String>> readReact2PRO(String mapping_file, String rtype) throws IOException{
		Map<String, Set<String>> react_pros = new HashMap<String, Set<String>>();
		BufferedReader reader = new BufferedReader(new FileReader(mapping_file));
		String line = reader.readLine();
		while(line!=null) {
			if(line.contains("Reactome")) {
				String[] row = line.split("	");
				String r = row[1];
				r = r.split(":")[1];
				String p = row[0].replace(":", "_");
				String relation = row[2];
				if(relation.equals(rtype)||rtype==null) { //is_a or exact - null for all
					Set<String> pros = react_pros.get(r);
					if(pros==null) {
						pros = new HashSet<String>();
					}
					pros.add(p);
					if(pros.size()>1) {
						System.out.println(row);
					}
					react_pros.put(r, pros);
				}
			}
			line = reader.readLine();
		}
		reader.close();
		return react_pros;
	}

	public void makeSpeciesSpecificPRO(OWLClass target_species, String outfile) {
		int n_axioms = pro_ont.getAxiomCount();
		Set<OWLClass> all_class = pro_ont.getClassesInSignature();
		int total = all_class.size();
		int n = 0; int n_checked = 0;
		for(OWLClass term : all_class) {
			if(term.toString().contains("identifiers")) {
				System.out.println("its a gene");
			}
			n_checked++;
			OWLClass species = getSpecies(term);
			if(species!=null&&(!species.equals(target_species))) {
				term.accept(remover);
				n++;
				System.out.println(n+" "+n_checked+" "+total+" removing "+term);
			}
		}
		ontman.applyChanges(remover.getChanges());
		int n_total = pro_ont.getAxiomCount();
		System.out.println("Total axioms: "+n_total);
		System.out.println("Removed "+(n_axioms-n_total));
		try {
			FileOutputStream out = new FileOutputStream(outfile);
			ontman.saveOntology(pro_ont, out);
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
				if(test.getProperty().equals(in_taxon)||test.getProperty().equals(only_in_taxon)) {
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
		OWLSubClassOfAxiom s2 = ontman.getOWLDataFactory().getOWLSubClassOfAxiom(term, is_only_human_axiom);
		boolean is_human_thing = false;
		if(EntitySearcher.containsAxiom(s, pro_ont,false)||EntitySearcher.containsAxiom(s2, pro_ont,false)) {
			is_human_thing = true;
		}
		return is_human_thing;
	}

}
