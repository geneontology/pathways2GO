package org.geneontology.gocam.exchange.idmapping;

public class IdMapper {
	String wd_prop_hmdb = "P2057";
	String wd_prop_chebi = "P683";
	public IdMapper() {
		// TODO Auto-generated constructor stub
	}

	public static void main(String[] args) {
		// TODO Auto-generated method stub

	}
	
	public static String map2chebi(String db, String id) {
		String chebi = null;
		if(db.equals("hmdb")&&id.equals("HMDB00037")) {
			//chebi = "CHEBI_27584";
			//Set<String>getItemsByIdProp
		}
		return chebi;
	}

}
