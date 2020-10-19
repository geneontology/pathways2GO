/**
 * 
 */
package org.geneontology.garage;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Reactome has information in it that is not in its BioPAX export
 * This class is meant to dig it out.  
 * @author bgood
 *
 */
public class ReactomeExtras {
	String active_site_file = "reactome/reactome_graph_active_units.csv";
	
	Map<String, ActiveSite> controller_active;
	class ActiveSite {
		String reaction_id;
		String controller_event_id;
		String active_unit_id;
	}
	/**
	 * @throws IOException 
	 * 
	 */
	public ReactomeExtras() throws IOException {
		controller_active = new HashMap<String, ActiveSite>();
		String local_file = getClass().getResource(active_site_file).getFile();
		BufferedReader reader = new BufferedReader(new FileReader(local_file));
		reader.readLine();//skip header
		String line = reader.readLine();
		while(line!=null) {
			ActiveSite a = new ActiveSite();
			String[] cols = line.split(",");
			a.reaction_id = cols[0];
			a.controller_event_id = cols[1];
			a.active_unit_id = cols[2];
			controller_active.put(a.controller_event_id, a);
			line = reader.readLine();
		}
		reader.close();
	}

	/**
	 * @param args
	 * @throws IOException 
	 */
	public static void main(String[] args) throws IOException {
		ReactomeExtras r = new ReactomeExtras();
		System.out.println(r.controller_active.size());

	}

}
