package org.geneontology.garage;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;

import org.biopax.paxtools.io.BioPAXIOHandler;
import org.biopax.paxtools.io.SimpleIOHandler;
import org.biopax.paxtools.model.BioPAXFactory;
import org.biopax.paxtools.model.BioPAXLevel;
import org.biopax.paxtools.model.Model;
import org.biopax.paxtools.model.level3.Pathway;

public class BioPaxSplitter {

	public BioPaxSplitter() {
		// TODO Auto-generated constructor stub
	}

	public static void main(String[] args) throws FileNotFoundException {
		String input_biopax = "/Users/bgood/Desktop/test/biopax/pathway_commons/PathwayCommons10.wp.BIOPAX.owl";
		String out_folder = "/Users/bgood/Desktop/test/biopax/pathway_commons/wp_split/";
		BioPAXFactory factory = BioPAXLevel.L3.getDefaultFactory();
		BioPAXIOHandler handler = new SimpleIOHandler();
		FileInputStream f = new FileInputStream(input_biopax);
		Model model = handler.convertFromOWL(f);
		for (Pathway currentPathway : model.getObjects(Pathway.class)){
			String name = currentPathway.getDisplayName().replace(" ", "_");
			name = name.replaceAll("/", "_");

			if(name.equals("Differentiation")) {
				System.out.println("currentPathway");
			}
			Model biopax = factory.createModel();
			biopax.setAddDependencies(true);
			biopax.add(currentPathway);
			String biopax_file = out_folder+name;
			FileOutputStream outstream = new FileOutputStream(biopax_file);
			handler.convertToOWL(biopax, outstream);
		}
	}

}
