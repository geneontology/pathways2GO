/**
 * 
 */
package org.geneontology.gocam.exchange.rhea;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.apache.jena.rdf.model.ModelFactory;
import org.biopax.paxtools.converter.LevelUpgrader;
import org.biopax.paxtools.io.BioPAXIOHandler;
import org.biopax.paxtools.io.SimpleIOHandler;
import org.biopax.paxtools.model.BioPAXFactory;
import org.biopax.paxtools.model.BioPAXLevel;
//import org.biopax.paxtools.model.Model;
import org.biopax.paxtools.model.level3.BiochemicalReaction;
import org.geneontology.gocam.exchange.App;
import org.apache.commons.io.IOUtils;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.query.QuerySolution;
import org.apache.jena.query.ResultSet;
import org.apache.jena.rdf.model.Literal;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.Statement;

/**
 * @author bgood
 *
 */
public class RheaConverter {

	/**
	 * 
	 */
	public RheaConverter() {
		// TODO Auto-generated constructor stub
	}

	/**
	 * @param args
	 * @throws FileNotFoundException 
	 */
	public static void main(String[] args) throws FileNotFoundException {
		RheaConverter rc = new RheaConverter();
		Map<String, rheaReaction> reactions = rc.getReactionsFromRDF();
		System.out.println(reactions);
	}

	class rheaReaction {
		Map<String, String> left_bag_chebi_stoich;
		Map<String, String> right_bag_chebi_stoich;
		String ec_number;
		String equation;
		String rhea_master_id;
		String rhea_bidirectional_id;
		boolean containsPolymer;
		boolean containsGeneric;
		rheaReaction(){
			left_bag_chebi_stoich = new HashMap<String, String>();
			right_bag_chebi_stoich = new HashMap<String, String>();
			containsPolymer = false;
			containsGeneric = false;
		}
	}

	public Map<String, rheaReaction> getReactionsFromRDF() {
		Map<String, rheaReaction> reactions = new HashMap<String,rheaReaction>();
		Model model = ModelFactory.createDefaultModel();
		model.read("/Users/bgood/Desktop/test/rhea/rhea.rdf");
		String q = null;
		try {
			q = IOUtils.toString(App.class.getResourceAsStream("rhea_get_reactions.rq"), StandardCharsets.UTF_8);
		} catch (IOException e) {
			System.out.println("Could not load SPARQL query from jar \n"+e);
		}
		QueryExecution qe = QueryExecutionFactory.create(q, model);
		ResultSet results = qe.execSelect();
		//?reaction ?reactionLabel ?EC ?reactionSide ?compoundLabel ?stoich ?chebi
		while (results.hasNext()) {
			QuerySolution qs = results.next();
			Resource reaction_id = qs.getResource("reaction");
			Resource side = qs.getResource("reactionSide");
			Resource chebi = qs.getResource("chebi");
			Literal s = qs.getLiteral("stoich");
			Literal eq = qs.getLiteral("reactionLabel");
			Resource ec = qs.getResource("EC");
			Literal rhea_id = qs.getLiteral("rhea");
			Literal rhea_bi = qs.getLiteral("rheaBiAccession");
			rheaReaction reaction = reactions.get(reaction_id.getURI());
			if(reaction==null) {
				reaction = new rheaReaction();
				if(ec!=null) {
					reaction.ec_number = ec.getURI();
				}
				if(eq!=null) {
					reaction.equation = eq.getString();
				}
				if(rhea_id!=null) {
					reaction.rhea_master_id = rhea_id.getString();
				}
				if(rhea_bi!=null) {
					reaction.rhea_bidirectional_id = rhea_bi.getString(); 
				}
			}
			//reaction components, by side 
			//if its a generic, then chebis are attached to reactive parts of the generic
			Resource reactivePart = qs.getResource("reactivePart"); 
			if(reactivePart!=null) {
				reaction.containsGeneric = true;
			}
			//if its a polymer then it should have a polymer index 
			Literal polymer_index = qs.getLiteral("polymer_index");
			if(polymer_index!= null) {
				reaction.containsPolymer = true;
			}
			//in either case query should be smart enough to fish out corresponding chebis
			if(chebi!=null&&chebi.getURI()!="") {
				if(side.getURI().contains("_L")) {
					reaction.left_bag_chebi_stoich.put(chebi.getURI(), s.getString());
				}else if(side.getURI().contains("_R")) {
					reaction.right_bag_chebi_stoich.put(chebi.getURI(), s.getString());
				}else {
					System.out.println("no sided reaction ?");
					System.exit(0);
				}			
				reactions.put(reaction_id.getURI(), reaction);
			}else {
				System.out.println("no chebi "+rhea_id+" "+chebi);
				System.exit(0);
			}
		}
		qe.close();		
		model.close();
		return reactions;
	}

	public void convertBioPax() throws FileNotFoundException {
		String input_biopax = 
				//"/Users/bgood/gocam_input/rhea/rhea-biopax_lite.owl";rhea-biopax_full
				"/Users/bgood/gocam_input/rhea/rhea-biopax_full.owl";
		String converted = 
				"/Users/bgood/Desktop/test/rhea/rhea-go.ttl";
		String output_biopax = 
				"/Users/bgood/gocam_input/rhea/rhea-biopax_lite_bpL3.owl";

		BioPAXIOHandler handler = new SimpleIOHandler();
		FileInputStream f = new FileInputStream(input_biopax);
		org.biopax.paxtools.model.Model l2 = handler.convertFromOWL(f);
		LevelUpgrader levelup = new LevelUpgrader();
		org.biopax.paxtools.model.Model model = levelup.filter(l2);
		//if care about it going faster, save this out first
		//handler.convertToOWL(model, new FileOutputStream(output_biopax));

		System.out.println(model+" exists");
		int total_interactions = model.getObjects(BiochemicalReaction.class).size();
		System.out.println("bp level = "+model.getLevel());		
		System.out.println(total_interactions);
		int n_interactions = 0;
		BioPAXFactory factory = BioPAXLevel.L3.getDefaultFactory();
		org.biopax.paxtools.model.Model sample = factory.createModel();
		for (BiochemicalReaction reaction : model.getObjects(BiochemicalReaction.class)){
			sample.add(reaction);
			n_interactions++;
			System.out.println(n_interactions+" of "+total_interactions+" BiochemicalReaction:"+reaction.getName()); 
			if(n_interactions > 10) {
				break;
			}
		}
		handler.convertToOWL(sample, new FileOutputStream("/Users/bgood/Desktop/test/rhea/sample_full.owl"));
	}

}
