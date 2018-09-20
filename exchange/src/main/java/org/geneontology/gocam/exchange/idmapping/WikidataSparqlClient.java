/**
 * 
 */
package org.geneontology.gocam.exchange.idmapping;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.apache.jena.query.Query;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.query.QueryFactory;
import org.apache.jena.query.QueryParseException;
import org.apache.jena.query.QuerySolution;
import org.apache.jena.query.ResultSet;
import org.apache.jena.query.ResultSetFormatter;
import org.apache.jena.rdf.model.Literal;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;

/*
 * Uses the Jena library to query Wikidata
 * @author bgood
 *
 */
public class WikidataSparqlClient {

	static String dbpedia_endpoint = "http://dbpedia.org/sparql";
	static String wikidata_endpoint = "https://query.wikidata.org/bigdata/namespace/wdq/sparql";

	public static String q_map_entrez_wikipedia =
			"PREFIX schema: <http://schema.org/> "+
					"PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#> "+
					"PREFIX wd: <http://www.wikidata.org/entity/> "+
					"PREFIX wdt: <http://www.wikidata.org/prop/direct/> "+
					"SELECT ?cid ?entrez_id ?label ?article WHERE { "+
					"    ?cid wdt:P351 ?entrez_id . "+
					" OPTIONAL { "+
					"    ?cid rdfs:label ?label filter (lang(?label) = \"en\") . "+
					"} "+
					"OPTIONAL {  "+
					"?article schema:about ?cid .  "+
					"?article schema:inLanguage \"en\" .  "+
					"FILTER (SUBSTR(str(?article), 1, 25) = \"https://en.wikipedia.org/\") "
					+ "FILTER (SUBSTR(str(?article), 1, 38) != \"https://en.wikipedia.org/wiki/Template\") "+
					"}  "+
					"}  "+
					"limit 10 ";

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		//System.out.println(getItemsByIdProp("P486", "D015451"));
		//System.out.println(getAliases("Q17675530"));
		System.out.println(mapFromP1toP2("P2057", "HMDB00037", "P683"));
	}

	public static Set<String> getAliases(String qid){
		Set<String> aliases = new HashSet<String>();

		String sparql = 
				"PREFIX wd: <http://www.wikidata.org/entity/> " +
				"PREFIX skos: <http://www.w3.org/2004/02/skos/core#> "+
				"SELECT ?alias  "+
				"WHERE { wd:"+qid+" skos:altLabel ?alias } ";
		try{
			Query query = QueryFactory.create(sparql); 
			try ( QueryExecution qexec = QueryExecutionFactory.sparqlService(wikidata_endpoint, query) ) {
				
				ResultSet rs = qexec.execSelect();
				while(rs.hasNext()){
					QuerySolution q = rs.next();
					RDFNode nid = q.get("?alias");					
					if(nid.canAs(Literal.class)){
						aliases.add(nid.asLiteral().getString());
					}
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		} catch(QueryParseException e){
			System.out.println("query parse error, label:"+":end");
			e.printStackTrace();
		}
		return aliases;
	}
	
	public static Set<String> getItemsByPreferredOrAltLabel(String label){
		Set<String> items = new HashSet<String>();
		if(label.trim().equals("")){
			System.out.println("Empty label");
			return items;
		}
		String sparql = 
				"PREFIX skos: <http://www.w3.org/2004/02/skos/core#> "+
						"PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#> "+
						"SELECT ?item  "+
						"WHERE "+
						" { "
						+ "{ ?item rdfs:label \""+label+"\"@en }"
						+ " UNION "
						+ " { ?item skos:altLabel \""+label+"\"@en } "
						+ "}";
		// " ?item skos:altLabel "New York"@en  "+		
		//System.out.println(sparql);
		try{
			Query query = QueryFactory.create(sparql); 

			try ( QueryExecution qexec = QueryExecutionFactory.sparqlService(wikidata_endpoint, query) ) {
				//((QueryEngineHTTP)qexec).addParam("timeout", "10000") ;
				ResultSet rs = qexec.execSelect();
				while(rs.hasNext()){
					QuerySolution q = rs.next();
					Resource r = q.getResource("item");
					String u = r.getLocalName();; //r.getURI();				
					items.add(u);
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		} catch(QueryParseException e){
			System.out.println("query parse error, label:"+":end");
			e.printStackTrace();
		}
		return items;
	}

	public static Set<String> getItemsByPreferredLabel(String label){
		Set<String> items = new HashSet<String>();
		String sparql = 
				"PREFIX skos: <http://www.w3.org/2004/02/skos/core#> "+
						"PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#> "+
						"SELECT ?item  "+
						"WHERE "+
						"{ ?item rdfs:label \""+label+"\"@en  }";
		// " ?item skos:altLabel "New York"@en  "+		
		Query query = QueryFactory.create(sparql); 
		try ( QueryExecution qexec = QueryExecutionFactory.sparqlService(wikidata_endpoint, query) ) {
			//((QueryEngineHTTP)qexec).addParam("timeout", "10000") ;
			ResultSet rs = qexec.execSelect();
			while(rs.hasNext()){
				QuerySolution q = rs.next();
				Resource r = q.getResource("item");
				String u = r.getLocalName();; //r.getURI();		
				items.add(u);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return items;
	}

	/**
	 * Creates a complete mapping of all Wikidata identifiers found at the specified prop.  
	 * @param namespace
	 * @param prop
	 * @param onlyhuman
	 * @return
	 */
	public static Map<String, Set<String>> getIdPropItemMap(String namespace, String prop, boolean onlyhuman){
		Map<String, Set<String>> id_items = new HashMap<String, Set<String>>();
		String sparql = 
				"PREFIX wd: <http://www.wikidata.org/entity/> "+
						"PREFIX bd: <http://www.bigdata.com/rdf#> "+
						"PREFIX wikibase: <http://wikiba.se/ontology#> "+
						"PREFIX wdt: <http://www.wikidata.org/prop/direct/> "+
						"SELECT distinct ?item ?id "+
						"WHERE { "+
						" ?item wdt:"+prop+" ?id . ";
		if(onlyhuman){ //special limit to items with taxon human (e.g. useful for genes)
			sparql+= " ?item wdt:P703 wd:Q15978631 . ";
		}
		sparql+=				" } ";

		Query query = QueryFactory.create(sparql); 
		try ( QueryExecution qexec = QueryExecutionFactory.sparqlService(wikidata_endpoint, query) ) {
			//((QueryEngineHTTP)qexec).addParam("timeout", "60000") ;
			ResultSet rs = qexec.execSelect();
			while(rs.hasNext()){
				QuerySolution q = rs.next();
				Resource r = q.getResource("item");
				String u = r.getLocalName();; //r.getURI();		
				RDFNode nid = q.get("?id");
				String id = "";
				if(nid.canAs(Literal.class)){
					id = nid.asLiteral().getString();
				}else if(nid.canAs(Resource.class)){
					id = nid.asResource().getLocalName();
				}
				String key = namespace+"_"+id;
				Set<String> items = id_items.get(key);
				if(items==null){
					items = new HashSet<String>();
				}
				items.add(u);
				id_items.put(key,  items);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return id_items;
	}

	public static Set<String> mapFromP1toP2(String prop1, String id, String prop2){
		Set<String> items = new HashSet<String>();
		String sparql = 
				"PREFIX bd: <http://www.bigdata.com/rdf#> "
						+"PREFIX wikibase: <http://wikiba.se/ontology#> "+
						"PREFIX wdt: <http://www.wikidata.org/prop/direct/> "+
						"SELECT distinct ?id2 "+
						"WHERE { "+
						" ?item wdt:"+prop1+" '"+id+"' . "
						+ " ?item wdt:"+prop2+ " ?id2 . "+
						" } ";

		Query query = QueryFactory.create(sparql); 
		try ( QueryExecution qexec = QueryExecutionFactory.sparqlService(wikidata_endpoint, query) ) {
			//((QueryEngineHTTP)qexec).addParam("timeout", "10000") ;
			ResultSet rs = qexec.execSelect();
			while(rs.hasNext()){
				QuerySolution q = rs.next();
			//	Resource r = q.getResource("item");
				RDFNode nid = q.get("?id2");
				String id2 = "";
				if(nid.canAs(Literal.class)){
					id2 = nid.asLiteral().getString();
				}else if(nid.canAs(Resource.class)){
					id2 = nid.asResource().getLocalName();
				}
				items.add(id2);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return items;
	}
	
	public static Set<String> getItemsByIdProp(String prop, String id){
		Set<String> items = new HashSet<String>();
		String sparql = 
				"PREFIX bd: <http://www.bigdata.com/rdf#> "
						+"PREFIX wikibase: <http://wikiba.se/ontology#> "+
						"PREFIX wdt: <http://www.wikidata.org/prop/direct/> "+
						"SELECT distinct ?item ?itemLabel "+
						"WHERE { "+
						" ?item wdt:"+prop+" '"+id+"' . "+
						" 	SERVICE wikibase:label { "+
						"      bd:serviceParam wikibase:language \"en\" . "+
						"	} "+
						" } ";

		Query query = QueryFactory.create(sparql); 
		try ( QueryExecution qexec = QueryExecutionFactory.sparqlService(wikidata_endpoint, query) ) {
			//((QueryEngineHTTP)qexec).addParam("timeout", "10000") ;
			ResultSet rs = qexec.execSelect();
			while(rs.hasNext()){
				QuerySolution q = rs.next();
				Resource r = q.getResource("item");
				String u = r.getLocalName();; //r.getURI();		
				items.add(u);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return items;
	}

	public static Set<String> getItemsByIdPropSet(Map<String,Set<String>> prop_ids){
		Set<String> items = new HashSet<String>();

		String sparql =
				"PREFIX bd: <http://www.bigdata.com/rdf#> "
						+"PREFIX wikibase: <http://wikiba.se/ontology#> "+
						"PREFIX wdt: <http://www.wikidata.org/prop/direct/> "+
						"SELECT distinct ?item ?itemLabel "+
						"WHERE { ";

		for(String prop : prop_ids.keySet()){
			for(String id : prop_ids.get(prop)){
				sparql += "{ ?item wdt:"+prop+" '"+id+"' . } UNION";
			}
		}
		//take off that last union
		sparql = sparql.substring(0, sparql.length()-5);
		//add on the end								
		sparql +=		
				" 	SERVICE wikibase:label { "+
						"      bd:serviceParam wikibase:language \"en\" . "+
						"	} "+
						" } ";
		System.out.println(sparql);
		Query query = QueryFactory.create(sparql); 
		try ( QueryExecution qexec = QueryExecutionFactory.sparqlService(wikidata_endpoint, query) ) {
			//((QueryEngineHTTP)qexec).addParam("timeout", "10000") ;
			ResultSet rs = qexec.execSelect();
			while(rs.hasNext()){
				QuerySolution q = rs.next();
				Resource r = q.getResource("item");
				String u = r.getLocalName();; //r.getURI();		
				items.add(u);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return items;
	}


	public static void testQuery(String queryStr){
		System.out.println(queryStr);
		Query query = QueryFactory.create(queryStr); 
		try ( QueryExecution qexec = QueryExecutionFactory.sparqlService(wikidata_endpoint, query) ) {
			//((QueryEngineHTTP)qexec).addParam("timeout", "10000") ;
			// Execute.
			ResultSet rs = qexec.execSelect();
			ResultSetFormatter.out(System.out, rs, query);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}


}
