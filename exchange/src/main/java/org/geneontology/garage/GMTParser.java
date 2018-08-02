package org.geneontology.garage;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class GMTParser {
	List<GMTRecord> gmt_records; 
	
	class GMTRecord {
		String uri;
		String name;
		String datasource;
		String organism;
		String idtype;
		Set<String> gene_ids;
		
		private String getString() {
			String row = name+"~"+uri+";"+datasource+";"+organism+";"+idtype+"~";
			for(String gene : gene_ids) {
				row = row+gene+"~";
			}
			return row;
		}
	}
	
	public GMTParser(String input_file) throws IOException {
		gmt_records = new ArrayList<GMTRecord>();
		BufferedReader reader = new BufferedReader(new FileReader(input_file));
		String line = reader.readLine();
		while(line!=null) {
			GMTRecord r = new GMTRecord();
			String[] cols = line.split("\t");
			r.uri = cols[0];
			String[] meta = cols[1].split(";");
			r.name = meta[0].replaceAll("name:", "").trim();
			r.datasource = meta[1].replaceAll("datasource:", "").trim();
			r.organism = meta[2].replaceAll("organism:", "").trim();
			r.idtype = meta[3].replaceAll("idtype:", "").trim();
			r.gene_ids = new HashSet<String>();
			for(int i=2;i<cols.length;i++) {
				r.gene_ids.add(cols[i]);
			}
			gmt_records.add(r);
			line = reader.readLine();
		}
		reader.close();
	}

	public static void main(String[] args) throws IOException {
		GMTParser p = new GMTParser("/Users/bgood/git/noctua_exchange/exchange/src/main/resources/pathway_commons/PathwayCommons10.All.hgnc.gmt.txt");
		for(GMTRecord r : p.gmt_records) {
			if(r.name.contains("WNT")) { //r.name.contains("Wnt")||
				System.out.println(r.getString());
			}
		}
	}

}
