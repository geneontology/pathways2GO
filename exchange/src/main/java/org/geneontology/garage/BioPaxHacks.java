package org.geneontology.garage;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.util.HashSet;
import java.util.Set;

import org.biopax.paxtools.io.BioPAXIOHandler;
import org.biopax.paxtools.io.SimpleIOHandler;
import org.biopax.paxtools.model.BioPAXFactory;
import org.biopax.paxtools.model.BioPAXLevel;
import org.biopax.paxtools.model.Model;
import org.biopax.paxtools.model.level3.Pathway;
import org.biopax.paxtools.model.level3.UnificationXref;
import org.biopax.paxtools.model.level3.Xref;
import org.biopax.paxtools.model.level3.Catalysis;
import org.biopax.paxtools.model.level3.Control;
import org.biopax.paxtools.model.level3.Entity;

public class BioPaxHacks {

	public BioPaxHacks() {
		// TODO Auto-generated constructor stub
	}

	public static void main(String[] args) throws FileNotFoundException {
		String input_biopax = "/Users/bgood/Desktop/test/biopax/Homo_sapiens_Jan2019.owl";
		BioPAXIOHandler handler = new SimpleIOHandler();
		FileInputStream f = new FileInputStream(input_biopax);
		Model model = handler.convertFromOWL(f);

		Set<String> multis = new HashSet<String>();
		for (Pathway p : model.getObjects(Pathway.class)){
//			if(!p.getDisplayName().equals("Antigen activates B Cell Receptor (BCR) leading to generation of second messengers")) {
//				continue;
//			}
			int n =0; int nc = 0; int cat = 0; int reg = 0; int reg_active = 0;  int cat_active = 0;
			int n_multi_active = 0;
			for(org.biopax.paxtools.model.level3.Process r : p.getPathwayComponent()) {
				for(Control c : r.getControlledOf()) {
					nc++;
					if(c.getModelInterface().equals(Catalysis.class)) {
						cat++;
					}else {
						reg++;
					}
					int n_active_per_control = 0;
					for(String comment : c.getComment()) {
						if(comment.contains("activeUnit:")){
							n_active_per_control++;
							n++;
							if(c.getModelInterface().equals(Catalysis.class)) {
								cat_active++;
								System.out.println("Catalytic activity with active site "+r.getDisplayName()+" "+getReactomeReference(r));
							}else {
								reg_active++;
								System.out.println("Regulatory activity with active site "+r.getDisplayName()+" "+getReactomeReference(r));
							}
						}else {
							if(c.getModelInterface().equals(Catalysis.class)) {
								cat_active++;
								System.out.println("Catalytic activity with NO active site "+r.getDisplayName()+" "+getReactomeReference(r));
							}else {
								reg_active++;
								System.out.println("Regulatory activity with NO active site "+r.getDisplayName()+" "+getReactomeReference(r));
							}
						}
						if(n_active_per_control>1) {
							n_multi_active++;		
							if(c.getModelInterface().equals(Catalysis.class)) {
								cat_active++;
								System.out.println("Catalytic activity with multiple active sites "+r.getDisplayName()+" "+getReactomeReference(r));
							}else {
								reg_active++;
								System.out.println("Regulatory activity with multiple active sites "+r.getDisplayName()+" "+getReactomeReference(r));
							}
							String id = getReactomeReference(r);
									 
							multis.add("["+id+" "+r.getDisplayName()+"](https://reactome.org/content/detail/"+id+")");
						}
					}
				}

			}
			if(n_multi_active > 0 && cat_active > 0 && reg_active > 0 &&
					reg > reg_active && cat > cat_active) {
				System.out.println(p.getDisplayName());
				System.out.println(nc+" control "+" active unit annotations "+ n);
				System.out.println(" cat "+cat+" reg "+reg+" reg_active "+reg_active+" cat_active "+cat_active);
				System.out.println(" n multi active "+n_multi_active);
				
			}
		}
		for(String m : multis) {
			System.out.println("- "+m);
		}
		System.out.println(multis.size());
		
	}
	
	public static String getReactomeReference(Entity e) {
		Set<Xref> xrefs = e.getXref();
		String datasource_id = null;
		String ref = null;
		for(Xref xref : xrefs) {
			if(xref.getModelInterface().equals(UnificationXref.class)) {
				UnificationXref r = (UnificationXref)xref;	    			
				if(r.getDb().equals("Reactome")) {
					datasource_id = r.getId();
					if(datasource_id.startsWith("R-HSA")) {
						ref = datasource_id;
						//"https://reactome.org/content/detail/"
						//or https://reactome.org/PathwayBrowser/#/ to go right to pathway browser
						break;
					}
				}
			}
		}	
		return ref;
	}
	
	public static void countControllers() throws FileNotFoundException {
		String input_biopax = "/Users/bgood/Desktop/test/biopax/Homo_sapiens_Jan2019.owl";
		BioPAXIOHandler handler = new SimpleIOHandler();
		FileInputStream f = new FileInputStream(input_biopax);
		Model model = handler.convertFromOWL(f);
		int n =0; int nc = 0; int cat = 0; int reg = 0; int reg_active = 0;  int cat_active = 0;
		int n_multi_active = 0;
		for (Control c : model.getObjects(Control.class)){
			nc++;
			if(c.getModelInterface().equals(Catalysis.class)) {
				cat++;
			}else {
				reg++;
			}
			int n_active_per_control = 0;
			for(String comment : c.getComment()) {
				if(comment.contains("activeUnit:")){
					n_active_per_control++;
					n++;
					if(c.getModelInterface().equals(Catalysis.class)) {
						cat_active++;
					}else {
						reg_active++;
					}
				}
				if(n_active_per_control>1) {
					n_multi_active++;
					Set<org.biopax.paxtools.model.level3.Process> in = c.getControlled();
					for(org.biopax.paxtools.model.level3.Process p : in) {
						System.out.println(p.getDisplayName());
						System.out.println(c.getDisplayName());
					}

				}
			}
		}
		System.out.println(nc+" control "+" active unit annotations "+ n);
		System.out.println(" cat "+cat+" reg "+reg+" reg_active "+reg_active+" cat_active "+cat_active);
		System.out.println(" n multi active "+n_multi_active);
		/**
7085 control  active unit annotations 1915
cat 5252 reg 1833 reg_active 618 cat_active 1297
 n multi active 204

only counting max 1 per control
7085 control  active unit annotations 1713
 cat 5252 reg 1833 reg_active 500 cat_active 1213
		 */
	}

	public static void split() throws FileNotFoundException {
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
