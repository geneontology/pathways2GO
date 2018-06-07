/**
 * 
 */
package org.geneontology.gocam.exchange.rhea;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import org.apache.commons.io.IOUtils;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.query.ResultSet;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.biopax.paxtools.model.level3.BiochemicalReaction;
import org.geneontology.gocam.exchange.App;
import org.geneontology.gocam.exchange.GoCAM;
import org.geneontology.gocam.exchange.rhea.RheaConverter.rheaReaction;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLAnnotation;
import org.semanticweb.owlapi.model.OWLAxiom;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLClassExpression;
import org.semanticweb.owlapi.model.OWLDataFactory;
import org.semanticweb.owlapi.model.OWLDataProperty;
import org.semanticweb.owlapi.model.OWLIndividual;
import org.semanticweb.owlapi.model.OWLLiteral;
import org.semanticweb.owlapi.model.OWLObjectProperty;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyCreationException;
import org.semanticweb.owlapi.model.OWLOntologyStorageException;
import org.semanticweb.owlapi.search.EntitySearcher;

/**
 * @author bgood
 *
 */
public class MFCreator {

	public GoCAM go_cam;
	public static OWLClass SubstanceSet, CatalyticActivity;
	public static OWLObjectProperty has_substance_bag, has_member_part;
	public static OWLDataProperty has_stoichiometry;
	public static String base = "http://purl.obolibrary.org/obo/";
	public Model go_jena;
	public Map<String, Set<OWLClass>> rhea_go;
	public Map<String, Set<OWLClass>> rhea_ec_go;
	/**
	 * @throws OWLOntologyCreationException 
	 * 
	 */
	public MFCreator() throws OWLOntologyCreationException {
		this.go_cam = new GoCAM();
		SubstanceSet = go_cam.df.getOWLClass(IRI.create(base+"SubstanceSet"));
		CatalyticActivity = go_cam.df.getOWLClass(IRI.create("http://purl.obolibrary.org/obo/GO_0003824"));
		has_substance_bag = go_cam.df.getOWLObjectProperty(IRI.create(base+"has_substance_bag")); 
		has_member_part  = go_cam.df.getOWLObjectProperty(IRI.create(base+"has_member_part")); 
		has_stoichiometry  = go_cam.df.getOWLDataProperty(IRI.create(base+"has_stoichiometry")); 
		go_jena = ModelFactory.createDefaultModel();
		go_jena.read("/Users/bgood/git/noctua_exchange/exchange/src/main/resources/org/geneontology/gocam/exchange/go.owl");
		rhea_go = new HashMap<String, Set<OWLClass>>();
		rhea_ec_go =  new HashMap<String, Set<OWLClass>>();
	}

	/**
	 * @param args
	 * @throws OWLOntologyCreationException 
	 * @throws OWLOntologyStorageException 
	 */
	public static void main(String[] args) throws OWLOntologyCreationException, OWLOntologyStorageException {
		String output_ontology = "/Users/bgood/Desktop/test/tmp/newMFsFromRhea.ttl";
		//String input_go_cam = "/Users/bgood/Desktop/test/tmp/converted-Degradation_of_AXIN.ttl";
		//GoCAM go_cam = new GoCAM(input_go_cam);		
		//OWLOntology newmfs = mfc.makeMFClassesFromGoCAM(input_go_cam);
		MFCreator mfc = new MFCreator();
		RheaConverter rc = new RheaConverter();
		Map<String, rheaReaction> reactions = rc.getReactionsFromRDF();
		OWLOntology newmfs = mfc.makeMFClassesFromRheaReactions(reactions);
		App.writeOntology(output_ontology, newmfs);
	}


	

	public Set<OWLClass> getGObyDbXref(String xref) {
		String q = "select ?c where "
				+ "{?c <http://www.geneontology.org/formats/oboInOwl#hasDbXref> ?xref "
				+ "filter (?xref = \""+xref+"\")" 
				+ "} ";
		QueryExecution qe = QueryExecutionFactory.create(q, go_jena);
		ResultSet results = qe.execSelect();
		String iri = "";
		Set<OWLClass> gos = new HashSet<OWLClass>();
		while (results.hasNext()) {
			String cl = results.next().getResource("c").getURI();
			if(cl!=null) {
				iri = cl;
				gos.add(go_cam.df.getOWLClass(IRI.create(iri)));			
			}
		}			
		return gos;
	}


	public OWLOntology makeMFClassesFromRheaReactions(Map<String, rheaReaction> reactions) throws OWLOntologyCreationException {
		OWLOntology mfc = go_cam.go_cam_ont;
		OWLDataFactory df = mfc.getOWLOntologyManager().getOWLDataFactory();
		int i = 0;
		for(String reaction_id : reactions.keySet()) {
			rheaReaction reaction = reactions.get(reaction_id);
			i++;

			Set<OWLClassExpression> inputs = new HashSet<OWLClassExpression>();
			for(String chebi : reaction.left_bag_chebi_stoich.keySet()) {
				String s = reaction.left_bag_chebi_stoich.get(chebi);
				OWLClassExpression chemclass = df.getOWLClass(IRI.create(chebi));
				OWLLiteral stoich = go_cam.df.getOWLLiteral(s); 
				OWLClassExpression chemandstoich = go_cam.df.getOWLObjectSomeValuesFrom(has_member_part, 
						go_cam.df.getOWLObjectIntersectionOf(chemclass, go_cam.df.getOWLDataHasValue(has_stoichiometry, stoich)));
				inputs.add(chemandstoich);
			}
			Set<OWLClassExpression> outputs = new HashSet<OWLClassExpression>();
			for(String chebi : reaction.right_bag_chebi_stoich.keySet()) {
				String s = reaction.right_bag_chebi_stoich.get(chebi);
				OWLClassExpression chemclass = df.getOWLClass(IRI.create(chebi));
				OWLLiteral stoich = go_cam.df.getOWLLiteral(s); 
				OWLClassExpression chemandstoich = go_cam.df.getOWLObjectSomeValuesFrom(has_member_part, 
						go_cam.df.getOWLObjectIntersectionOf(chemclass, go_cam.df.getOWLDataHasValue(has_stoichiometry, stoich)));
				outputs.add(chemandstoich);
			}
			Set<OWLClass> mfs = null;
			//first check for match by rhea
			//GO uses the bidirectional version 
			String rhea = reaction.rhea_bidirectional_id;
			mfs = getGObyDbXref(rhea);
			rhea_go.put(rhea, mfs);
			OWLClass mf = null;
			if(mfs.size()==1) {
				mf = mfs.iterator().next();
				go_cam.addComment(mf, "linked to RHEA (and thus logical definition) via existing RHEA xref: "+rhea);
				System.out.println("rhea match "+rhea+" "+mf.getIRI());
			}else if(mfs.size()>1) {
				System.out.println(mfs.size()+" GO classes for "+rhea);
				System.exit(0);
			}
//			if(mf==null) {
//				//else check for match by ec number
//				String EC = reaction.ec_number.replace("http://purl.uniprot.org/enzyme/", "EC:");
//				mfs = getGObyDbXref(EC);
//				rhea_ec_go.put(rhea+"\t"+EC, mfs);
//				if(mfs.size()==1) {
//					mf = mfs.iterator().next();
//					System.out.println("EC match "+EC+" "+mf.getIRI());
//					go_cam.addComment(mf, "linked to RHEA "+rhea+" (and thus logical definition) via shared EC: "+EC);
//				}else if(mfs.size()>1) {
//					System.out.println(mfs.size()+" GO classes for "+EC);
//					//System.exit(0);
//					//TODO could use additional cross references to e.g., metacyc for further alignment
//				}
//			}
//			
//			if(mf==null) {
//				mf_iri = IRI.create(GoCAM.base_iri+"GO_howto_mint_newGO_id_"+i);			
//				mf = df.getOWLClass(mf_iri);	
//			}else {
//				mf_iri = mf.getIRI();
//			}
			//For now don't add any new ones or map to anything that wasn't directly asserted in a GO annotation
			if(mf==null) {
				continue;
			}
			IRI mf_iri = mf.getIRI();
			OWLAnnotation anno = go_cam.addLiteralAnnotations2Individual(mf_iri, GoCAM.definition,"Catalysis of reaction: "+reaction.equation);
			OWLAxiom defaxiom = df.getOWLAnnotationAssertionAxiom(mf_iri, anno);
			go_cam.ontman.addAxiom(go_cam.go_cam_ont, defaxiom);
			go_cam.addComment(mf, "Logical definition added programmatically from RHEA chemical equation. See discussion https://github.com/geneontology/go-ontology/issues/14984");
			if(reaction.containsGeneric) {
				go_cam.addComment(mf, "equation contains reference to generic, chebi term reference is to its active part");
			}
			if(reaction.containsPolymer) {
				go_cam.addComment(mf, "equation contains reference to polymer, chebi references underlying molecule");
			}
			
			OWLClassExpression inputbag = df.getOWLObjectIntersectionOf(inputs);
			OWLClassExpression outputbag = df.getOWLObjectIntersectionOf(outputs);
			OWLAxiom def = 
					df.getOWLEquivalentClassesAxiom(mf, 
							df.getOWLObjectIntersectionOf(CatalyticActivity, 
									df.getOWLObjectSomeValuesFrom(has_substance_bag, df.getOWLObjectIntersectionOf(SubstanceSet, inputbag)),
									df.getOWLObjectSomeValuesFrom(has_substance_bag, df.getOWLObjectIntersectionOf(SubstanceSet, outputbag)))
							);
			mfc.getOWLOntologyManager().addAxiom(mfc, def);
		}
		return mfc;
	}


	public void makeFromGoCAM() throws OWLOntologyCreationException, OWLOntologyStorageException {
		String input_go_cam = "/Users/bgood/Desktop/test/tmp/converted-Degradation_of_AXIN.ttl";
		String output_ontology = "/Users/bgood/Desktop/test/tmp/newMFsFromRhea.ttl";
		GoCAM go_cam = new GoCAM(input_go_cam);		
		OWLOntology newmfs = makeMFClassesFromGoCAM(go_cam);
		RheaConverter rc = new RheaConverter();
		App.writeOntology(output_ontology, newmfs);
	}

	/**
	 * take the gocam abox representation of a reaction and make a class 
	 * @param gocam_mf
	 * @return
	 * @throws OWLOntologyCreationException 
	 */
	public OWLOntology makeMFClassesFromGoCAM(GoCAM go_cam) throws OWLOntologyCreationException {
		OWLOntology mfc = go_cam.ontman.createOntology();
		OWLDataFactory df = mfc.getOWLOntologyManager().getOWLDataFactory();
		Iterator<OWLIndividual> reactions = EntitySearcher.getIndividuals(GoCAM.molecular_function, go_cam.go_cam_ont).iterator();
		int i = 0;
		while(reactions.hasNext()) {
			i++;
			OWLIndividual reaction = reactions.next();
			//this is based on inputs and outputs
			//if there aren't any, skip
			Collection<OWLIndividual> inputs = EntitySearcher.getObjectPropertyValues(reaction, GoCAM.has_input, go_cam.go_cam_ont);
			Collection<OWLIndividual> outputs = EntitySearcher.getObjectPropertyValues(reaction, GoCAM.has_output, go_cam.go_cam_ont);
			if(inputs.size()==0||outputs.size()==0) {
				continue;
			}
			//if there are, make the def
			OWLClass newmf = go_cam.df.getOWLClass(IRI.create(GoCAM.base_iri+"newMF"+i));		
			OWLClassExpression inputbag = df.getOWLObjectIntersectionOf(getChemPartsFromGoCAM(go_cam, inputs));
			OWLClassExpression outputbag = df.getOWLObjectIntersectionOf(getChemPartsFromGoCAM(go_cam, outputs));
			OWLAxiom def = 
					df.getOWLEquivalentClassesAxiom(newmf, 
							df.getOWLObjectIntersectionOf(CatalyticActivity, 
									df.getOWLObjectSomeValuesFrom(has_substance_bag, df.getOWLObjectIntersectionOf(SubstanceSet, inputbag)),
									df.getOWLObjectSomeValuesFrom(has_substance_bag, df.getOWLObjectIntersectionOf(SubstanceSet, outputbag)))
							);
			mfc.getOWLOntologyManager().addAxiom(mfc, def);
		}
		return mfc;
	}

	public Set<OWLClassExpression> getChemPartsFromGoCAM (GoCAM go_cam, Collection<OWLIndividual> chempartslist){
		Set<OWLClassExpression> parts = new HashSet<OWLClassExpression>();
		for(OWLIndividual part : chempartslist) {
			Collection<OWLClassExpression> types = EntitySearcher.getTypes(part, go_cam.go_cam_ont);
			if(types==null||types.size()!=1) {
				System.out.println("error, molecule type in getChemParts "+types);
				System.exit(0);
			}
			OWLClassExpression chemclass = types.iterator().next();
			//TODO need to actually get the stoichiometry in there - either in original conversion to go_cam or change this to use biopax data import.  
			OWLLiteral stoich = go_cam.df.getOWLLiteral(1);
			OWLClassExpression chemstoich = makeStoichedChemExpression(go_cam, chemclass, stoich);
			parts.add(chemstoich);
		}
		return parts;
	}

	public OWLClassExpression makeStoichedChemExpression(GoCAM go_cam, OWLClassExpression chemclass, OWLLiteral stoich) {
		OWLClassExpression chemandstoich = go_cam.df.getOWLObjectSomeValuesFrom(has_member_part, 
				go_cam.df.getOWLObjectIntersectionOf(chemclass, go_cam.df.getOWLDataHasValue(has_stoichiometry, stoich)));
		return chemandstoich;
	}


}
