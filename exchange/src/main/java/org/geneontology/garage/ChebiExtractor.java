package org.geneontology.garage;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.apache.commons.io.IOUtils;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.query.QuerySolution;
import org.apache.jena.query.ResultSet;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Resource;
import org.geneontology.gocam.exchange.rhea.RheaConverter.rheaReaction;
import org.semanticweb.elk.owlapi.ElkReasonerFactory;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLDataFactory;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyCreationException;
import org.semanticweb.owlapi.model.OWLOntologyManager;
import org.semanticweb.owlapi.reasoner.OWLReasoner;
import org.semanticweb.owlapi.reasoner.OWLReasonerFactory;

public class ChebiExtractor {

	public ChebiExtractor() {
		// TODO Auto-generated constructor stub
	}

	public static void main(String[] args) throws OWLOntologyCreationException, IOException {
		
		Map<String, String> new_chebi_label = getRheaChebis();
		Set<String> new_chebis = new_chebi_label.keySet();
		System.out.println("rhea chebis "+new_chebis.size());
		Set<String> current_go_chebis = getGOPlusChebis();
		System.out.println("Current GO chebis "+current_go_chebis.size());
		new_chebis.removeAll(current_go_chebis);
		System.out.println("New chebis: "+new_chebis.size());
		FileWriter f = new FileWriter("/Users/bgood/Desktop/test/tmp/new_chebis.txt");
		for(String c : new_chebis){
			f.write(c+" ## "+new_chebi_label.get(c)+"\n");
		}
		f.close();
	}
	
	public static Set<String> getGOPlusChebis() throws OWLOntologyCreationException{
		Set<String> chebis = new HashSet<String>();
		OWLOntologyManager man = OWLManager.createOWLOntologyManager();
		OWLDataFactory df = man.getOWLDataFactory();
		String goplus_file = "/Users/bgood/git/noctua_exchange/exchange/src/main/resources/org/geneontology/gocam/exchange/go-plus-merged.owl";
		OWLOntology ont = man.loadOntologyFromOntologyDocument(new File(goplus_file));
		OWLReasonerFactory reasonerFactory = new ElkReasonerFactory();
		OWLReasoner goplus = reasonerFactory.createReasoner(ont);
		String chemical_entity = "http://purl.obolibrary.org/obo/CHEBI_24431";
		OWLClass chem_root = df.getOWLClass(IRI.create(chemical_entity));
		Set<OWLClass> chems = goplus.getSubClasses(chem_root, false).getFlattened();
		for(OWLClass chem : chems) {
			String c = chem.getIRI().toString();
			if(c.contains("CHEBI")) {
				chebis.add(c);
			}else {
				System.out.println("GO Non chebi chemical : "+c);
			}
		}
		return chebis;
	}
	
	public static Map<String, String> getRheaChebis(){
		Map<String, String> chebis = new HashMap<String, String>();
		String getChebis = "PREFIX rh:<http://rdf.rhea-db.org/>\n" + 
		"PREFIX ch:<http://purl.obolibrary.org/obo/>\n" + 
		"PREFIX rdfs:<http://www.w3.org/2000/01/rdf-schema#>\n" + 
		"SELECT distinct ?chebi ?compoundLabel WHERE {\n" + 
		"  ?reaction rdfs:subClassOf rh:Reaction .  \n" + 
		"#to get to GO xrefs use bi\n" + 
		"  ?reaction rh:bidirectionalReaction ?rheaBi .\n" + 
		"# get participants\n" + 
		"  ?reaction rh:side ?reactionSide .\n" + 
		"  ?reactionSide rh:contains ?participant .\n" + 
		"  ?participant rh:compound ?compound .\n" + 
		"  ?compound rh:name ?compoundLabel .  \n" + 
		"# get chebi for participants\n" + 
		"# gets typical small molecule participants\n" + 
		" optional{ ?compound rh:chebi ?chebi . } \n" + 
		"#gets generic compounds \n" + 
		" optional{ \n" + 
		"   ?compound rh:reactivePart ?reactivePart . \n" + 
		"   ?reactivePart rh:chebi ?chebi }\n" + 
		"#gets polymers \n" + 
		"   optional{ \n" + 
		"   ?compound rh:underlyingChebi ?chebi .\n" + 
		"   ?compound rh:polymerizationIndex ?polymer_index}   \n" + 
		"  \n" + 
		"}";
		Map<String, rheaReaction> reactions = new HashMap<String,rheaReaction>();
		Model model = ModelFactory.createDefaultModel();
		model.read("/Users/bgood/Desktop/test/rhea/rhea.rdf");
		QueryExecution qe = QueryExecutionFactory.create(getChebis, model);
		ResultSet results = qe.execSelect();
		//?reaction ?reactionLabel ?EC ?reactionSide ?compoundLabel ?stoich ?chebi
		while (results.hasNext()) {
			QuerySolution qs = results.next();
			Resource chebi = qs.getResource("chebi"); // ## 
			String c = chebi.getURI().toString();
			String label = qs.getLiteral("compoundLabel").getString();
			if(c.contains("CHEBI")) {
				chebis.put(c, label);
			}else {
				System.out.println("RHEA: Non chebi chemical "+c);
			}
		}
		qe.close();
		return chebis;
	}

}
