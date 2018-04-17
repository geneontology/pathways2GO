/**
 * 
 */
package org.geneontology.gocam.exchange;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.OutputStream;

import org.biopax.paxtools.converter.LevelUpgrader;
import org.biopax.paxtools.io.BioPAXIOHandler;
import org.biopax.paxtools.io.SimpleIOHandler;
import org.biopax.paxtools.model.BioPAXFactory;
import org.biopax.paxtools.model.BioPAXLevel;
import org.biopax.paxtools.model.Model;
import org.biopax.paxtools.model.level3.BiochemicalReaction;

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
		String input_biopax = 
				"/Users/bgood/gocam_input/rhea/rhea-biopax_lite.owl";
		String converted = 
				"/Users/bgood/Desktop/test/rhea/rhea-go.ttl";
		String output_biopax = 
				"/Users/bgood/gocam_input/rhea/rhea-biopax_lite_bpL3.owl";
		
		BioPAXIOHandler handler = new SimpleIOHandler();
		FileInputStream f = new FileInputStream(input_biopax);
		Model l2 = handler.convertFromOWL(f);
		LevelUpgrader levelup = new LevelUpgrader();
		Model model = levelup.filter(l2);
		//if care about it going faster, save this out first
		//handler.convertToOWL(model, new FileOutputStream(output_biopax));
		
		System.out.println(model+" exists");
		int total_interactions = model.getObjects(BiochemicalReaction.class).size();
		System.out.println("bp level = "+model.getLevel());		
		System.out.println(total_interactions);
		int n_interactions = 0;
		BioPAXFactory factory = BioPAXLevel.L3.getDefaultFactory();
		Model sample = factory.createModel();
		for (BiochemicalReaction reaction : model.getObjects(BiochemicalReaction.class)){
			sample.add(reaction);
			n_interactions++;
			System.out.println(n_interactions+" of "+total_interactions+" BiochemicalReaction:"+reaction.getName()); 
			if(n_interactions > 10) {
				break;
			}
		}
		handler.convertToOWL(sample, new FileOutputStream("/Users/bgood/Desktop/test/rhea/sample.owl"));
	}

}
