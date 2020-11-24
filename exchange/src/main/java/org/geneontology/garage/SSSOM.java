/**
 * 
 */
package org.geneontology.garage;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * @author benjamingood
 *
 */
public class SSSOM {
	String license;
	String mapping_tool;
	String mapping_date;
	Map<String, String> curie_map;
	Set<Mapping> mappings; 
	
	class Mapping{
		String subject_id;
		String subject_label;
		String predicate_id;
		String object_id;
		String object_label;
		String match_type;
		String subject_source;
		String object_source;
		String mapping_tool;
		double confidence;
		String subject_match_field;
		String object_match_field;
		String subject_category;
		String object_category;
		String match_string;
		String match_category;
		String comment;
	
	}
	
	SSSOM(String sssom_file) throws IOException{
		mappings = new HashSet<Mapping>();
		BufferedReader reader = new BufferedReader(new FileReader(sssom_file));
		String line = reader.readLine();
		while(line!=null) {
			if(line.startsWith("#")) {
				String[] cols = line.split(": ");
				if(cols[0].startsWith("#license")) {
					license = cols[1];
				}else if(cols[0].startsWith("#mapping_tool")) {
					mapping_tool = cols[1];
				}else if(cols[0].startsWith("#mapping_date")) {
					mapping_date = cols[1];
				}else if(cols[0].startsWith("#curie_map")) {
					curie_map = new HashMap<String, String>();
					String prefix_line = reader.readLine();
					while(prefix_line!=null&&prefix_line.startsWith("# ")) {
						String[] map = prefix_line.split(" ");
						String prefix = map[2].replace(":", "");
						String uri_stub = map[3].replaceAll("\"", "");
						curie_map.put(prefix, uri_stub);
						prefix_line = reader.readLine();
					}
				}
			}else {
				String[] cols = line.split("	");
				Mapping m = new Mapping();
				m.subject_id = cols[0];
				m.subject_label = cols[1];
				m.predicate_id = cols[2];
				m.object_id = cols[3];
				m.object_label = cols[4];
				m.match_type = cols[5];
				m.subject_source = cols[6];
				m.object_source = cols[7];
				m.mapping_tool = cols[8];
				m.confidence = Double.parseDouble(cols[9]);
				m.subject_match_field = cols[10];
				m.object_match_field = cols[11];
				m.subject_category = cols[12];
				m.object_category = cols[13];
				m.match_string = cols[14];
				m.match_category = cols[15];
				m.comment = cols[16];
				mappings.add(m);
			}
			line = reader.readLine();
		}
		reader.close();
	}
	
	Set<Mapping> lookupBySubjectId(String subject_id, double cutoff){
		Set<Mapping> smap = new HashSet<Mapping>();
		for(Mapping m : mappings) {
			if(subject_id.equals(m.subject_id)&&m.confidence>cutoff) {
				smap.add(m);
			}
		}
		return smap;
	}
	
	String expandId(String id) {
		String[] cols = id.split(":");
		String expanded = curie_map.get(cols[0])+cols[1];		
		return expanded;
	}
	/**
	 * @param args
	 * @throws IOException 
	 */
	public static void main(String[] args) throws IOException {
		SSSOM sssom = new SSSOM("./target/classes/YeastCyc/obomatch-go-yeastpathway.sssom.tsv.txt");
		System.out.println(sssom.license+"\n"+sssom.mapping_date+"\n"+sssom.mapping_tool+"\nmappings: "+sssom.mappings.size()+"\n"+sssom.curie_map);
		Set<Mapping> smaps = sssom.lookupBySubjectId("yeastpathway:type=3%38object=GLYCLEAV-PWY#Pathway785728", 0.5);
		for(Mapping m : smaps) {
			System.out.println(sssom.expandId(m.subject_id)+"\t"+m.subject_label+" mapped to "+m.object_label+"\n\tobject id "+m.object_id+"\n\t\t"+sssom.expandId(m.object_id));
		}
	}

}
