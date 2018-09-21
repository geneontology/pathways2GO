package org.geneontology.gocam.exchange.idmapping;

import java.util.Set;

public class IdMapper {
	static String wd_prop_hmdb = "P2057";
	static String wd_prop_chebi = "P683";
	public IdMapper() {
		// TODO Auto-generated constructor stub
	}

	public static void main(String[] args) {
		// TODO Auto-generated method stub

	}
	
	public static String map2chebi(String db, String id) {
		String chebi = null;
		if(db.equals("hmdb")) {
			//chebi = "CHEBI_27584"; &&id.equals("HMDB00037")
			Set<String> chebis = WikidataSparqlClient.mapFromP1toP2(wd_prop_hmdb, id, wd_prop_chebi);
			if(!chebis.isEmpty()) {
				chebi = "CHEBI_"+chebis.iterator().next();
			}
		}
		return chebi;
	}

}
