/**
 * 
 */
package org.geneontology.gocam.exchange;

import java.io.File;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.AxiomType;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLAnnotation;
import org.semanticweb.owlapi.model.OWLAnnotationProperty;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLClassExpression;
import org.semanticweb.owlapi.model.OWLDataFactory;
import org.semanticweb.owlapi.model.OWLLiteral;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyCreationException;
import org.semanticweb.owlapi.model.OWLOntologyManager;
import org.semanticweb.owlapi.reasoner.NodeSet;
import org.semanticweb.owlapi.reasoner.OWLReasoner;
import org.semanticweb.owlapi.reasoner.OWLReasonerFactory;
import org.semanticweb.owlapi.reasoner.structural.StructuralReasonerFactory;
import org.semanticweb.owlapi.search.EntitySearcher;
import org.semanticweb.owlapi.search.Searcher;
import org.semarglproject.vocab.OWL;

import com.google.common.base.Optional;

/**
 * @author bgood
 *
 */
public class GOLego {
	//String go_loc;
	OWLOntology golego_ont;
	OWLOntologyManager ontman;				
	OWLDataFactory df;
	Set<String> chebi_roles;
	Set<String> chebi_chemicals;
	Set<String> molecular_functions;
	Set<String> deprecated;
	Map<String, String> replaced_by_map;
	Map<String, Set<String>> xref_gos;
	String obo_base ="http://purl.obolibrary.org/obo/";
	OWLReasoner golego_reasoner;

	public GOLego(OWLOntology ontology) throws OWLOntologyCreationException {
		if(ontology==null) {	
			ontman = OWLManager.createOWLOntologyManager();	
			ontology = ontman.createOntology();
		}else {
			ontman = ontology.getOWLOntologyManager();
		}
		df = ontman.getOWLDataFactory();
		golego_ont = ontology;
		Set<OWLOntology> imports = golego_ont.getImports();
		for(OWLOntology t : imports) {
			ontman.addAxioms(golego_ont, t.getAxioms());
		}
		xref_gos = new HashMap<String, Set<String>>();	
		GoCAM tmp = new GoCAM();//make the init functions run..
		golego_reasoner = createReasoner(golego_ont);
		buildXrefMap();
	}

	/**
	 * @throws OWLOntologyCreationException 
	 * 
	 */
	//	public GOLego(String go_plus_file) throws OWLOntologyCreationException {
	//		ontman = OWLManager.createOWLOntologyManager();	
	//		df = OWLManager.getOWLDataFactory();
	//		golego_ont = loadOntology(go_plus_file);
	//		xref_gos = new HashMap<String, Set<String>>();	
	//		GoCAM tmp = new GoCAM();//make the init functions run..
	//		System.out.println("GOLego (tbox) loaded, axioms "+golego_ont.getAxiomCount());
	//		golego_reasoner = createReasoner(golego_ont);
	//		buildXrefMap();
	//	}

	void buildXrefMap() {
		//build a map of all the xrefs in the ontology
		//probably a more efficient way to do this...
		Set<OWLClass> classes = golego_ont.getClassesInSignature();
		for(OWLClass c : classes) {
			Collection<OWLAnnotation> xrefs = EntitySearcher.getAnnotationObjects(c, golego_ont, GoCAM.database_cross_reference);
			for(OWLAnnotation xref : xrefs) {
				if(xref.getValue().asLiteral().isPresent()) {
					String x = xref.getValue().asLiteral().get().getLiteral();
					Set<String> gos = xref_gos.get(x);
					if(gos==null) {
						gos = new HashSet<String>();
					}
					gos.add(c.getIRI().toString());
					xref_gos.put(x, gos);
				}
			}
		}
		System.out.println("xref map created, size: "+xref_gos.size());
		//make list of roles
		OWLClass chebi_role = df.getOWLClass(IRI.create(obo_base+"CHEBI_50906"));
		Set<OWLClass> roles = getSubClasses(chebi_role, false, golego_reasoner);
		chebi_roles = new HashSet<String>();
		chebi_roles.add(obo_base+"CHEBI_50906");
		for(OWLClass r : roles) {
			chebi_roles.add(r.getIRI().toString());
		}
		//make list of chemicals
		OWLClass chebi_chemical = df.getOWLClass(IRI.create(obo_base+"CHEBI_24431"));
		Set<OWLClass> chemicals = getSubClasses(chebi_chemical, false,golego_reasoner);
		chebi_chemicals = new HashSet<String>();
		chebi_chemicals.add(obo_base+"CHEBI_24431");
		for(OWLClass c : chemicals) {
			chebi_chemicals.add(c.getIRI().toURI().toString());
		}
		//make list of functions
		OWLClass mf_root = df.getOWLClass(IRI.create(obo_base+"GO_0003674"));
		Set<OWLClass> function_classes = getSubClasses(mf_root, false, golego_reasoner);
		molecular_functions = new HashSet<String>();
		molecular_functions.add(obo_base+"GO_0003674");
		for(OWLClass fc : function_classes) {
			molecular_functions.add(fc.getIRI().toString());
		}
		//make uber list of deprecated
		OWLClass thing = golego_ont.getOWLOntologyManager().getOWLDataFactory().getOWLClass(IRI.create(OWL.THING));
		Set<OWLClass> things = getSubClasses(thing, true, golego_reasoner);
		deprecated = new HashSet<String>();
		replaced_by_map = new HashMap<String, String>();
		OWLAnnotationProperty dep = df.getOWLAnnotationProperty(IRI.create(OWL.DEPRECATED));
		OWLAnnotationProperty term_replaced_by = df.getOWLAnnotationProperty(IRI.create(obo_base+"IAO_0100001"));
		for(OWLClass c : things) {
			Collection<OWLAnnotation> annos = EntitySearcher.getAnnotationObjects(c, golego_ont, dep);
			for(OWLAnnotation anno : annos) {
				if(anno.isDeprecatedIRIAnnotation()) {
					deprecated.add(c.getIRI().toString());
					//add to replaced by list if present
					Collection<OWLAnnotation> replaced_by = EntitySearcher.getAnnotationObjects(c, golego_ont, term_replaced_by);
					for(OWLAnnotation rep : replaced_by) {
						String rep_iri = rep.getValue().toString();
						replaced_by_map.put(c.getIRI().toString(), rep_iri);
					}
				}
			}
		}
	}

	/**
	 * @param args
	 * @throws OWLOntologyCreationException 
	 */
	public static void main(String[] args) throws OWLOntologyCreationException {
		//		GOLego o = new GOLego("goplus.owl");
		//		String test = o.obo_base+"GO_0000004";
		//		System.out.println("ep "+o.isDeprecated(test));
		//		OWLClass t = o.getOboClass(test, false);
		//		System.out.println("ddd "+t);
		//		System.out.println("ep2 "+o.isDeprecated(t.getIRI().toString()));
		//		System.out.println("xref to go "+o.xref_gos.get("EC:6.3.4.9"));
	}

	public OWLClass getOboClass(String iri, boolean follow_replaced_by) {
		OWLClass c = null;
		if(follow_replaced_by&&isDeprecated(iri)) {
			String riri =  replacementUri(iri);
			if(riri!=null) {
				c = df.getOWLClass(IRI.create(riri));
				System.out.println("replaced "+iri+" with "+riri);
			}else {
				// no replacement, use provided iri but sound alert
				c = df.getOWLClass(IRI.create(iri)); 
				System.err.println("Alert! Using deprecated iri without a replacement: "+iri);
			}
		}else {
			c = df.getOWLClass(IRI.create(iri)); 
		}
		return c;
	}

	public boolean isDeprecated(String uri) {
		if(deprecated.contains(uri)) {
			return true;
		}else {
			return false;
		}
	}

	public String replacementUri(String uri) {
		return replaced_by_map.get(uri);
	}

	public boolean isChebiRole(String uri) {
		if(chebi_roles.contains(uri)) {
			return true;
		}else {
			return false;
		}
	}

	public boolean isChebiChemical(String uri) {
		if(chebi_chemicals.contains(uri)) {
			return true;
		}else {
			return false;
		}
	}

	public Set<OWLClass> getByEC(String ec){
		Set<OWLClass> mapped = new HashSet<OWLClass>();		
		return mapped;
	}

	OWLReasoner createReasoner(OWLOntology rootOntology) throws OWLOntologyCreationException {
		OWLReasonerFactory reasonerFactory = new StructuralReasonerFactory();
		return reasonerFactory.createReasoner(rootOntology);
	}

	OWLOntology loadOntology(String ontf) throws OWLOntologyCreationException {
		return ontman.loadOntologyFromOntologyDocument(new File(ontf));
	}

	Set<OWLClass> getSubClasses(OWLClassExpression classExpression, boolean direct, OWLReasoner reasoner) {
		NodeSet<OWLClass> subClasses = reasoner.getSubClasses(classExpression, direct);
		return subClasses.getFlattened();
	}

}
