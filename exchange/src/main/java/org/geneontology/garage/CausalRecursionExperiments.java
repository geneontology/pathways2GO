package org.geneontology.garage;

import java.io.FileWriter;
import java.io.IOException;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;

import org.openrdf.model.Value;
import org.openrdf.query.BindingSet;
import org.openrdf.query.MalformedQueryException;
import org.openrdf.query.QueryEvaluationException;
import org.openrdf.query.QueryLanguage;
import org.openrdf.query.TupleQuery;
import org.openrdf.query.TupleQueryResult;
import org.openrdf.repository.RepositoryException;

import com.bigdata.journal.Options;
import com.bigdata.rdf.sail.BigdataSail;
import com.bigdata.rdf.sail.BigdataSailRepository;
import com.bigdata.rdf.sail.BigdataSailRepositoryConnection;

public class CausalRecursionExperiments {
	BigdataSailRepository alldata_repo;
	public CausalRecursionExperiments (String bg_jnl) {
		this.alldata_repo = initializeRepository(bg_jnl);
	}
	
	class Edge{
		String source_id;
		String target_id;
		String source_label;
		String target_label;
		String edge_id;
		String edge_label;
		public Edge(String source_id, String target_id, String source_label, String target_label, String edge_id,
				String edge_label) {
			super();
			this.source_id = source_id;
			this.target_id = target_id;
			this.source_label = source_label;
			this.target_label = target_label;
			this.edge_id = edge_id;
			this.edge_label = edge_label;
		}
		//ignore dangling labels
		@Override
	    public boolean equals(Object o) { 
	        if (o == this) { 
	            return true; 
	        } 
	        if (!(o instanceof Edge)) { 
	            return false; 
	        } 
	        Edge c = (Edge) o; 
	         
	        String triple = source_id+edge_id+target_id;
	        String c_triple = c.source_id+c.edge_id+c.target_id;
	        return(triple.equals(c_triple));
	    } 
	}
	
	public void testCausalRecurse() throws IOException {
		String reaction_uri = "http://model.geneontology.org/R-HSA-70501";
		String reaction_label =  "Pyruvate + CO2 + ATP => ADP + Orthophosphate + Oxaloacetate";
		int depth = 0;
		int max_depth = 4;
		String stopstring = "insulin";
		Set<Edge> edges = recurseUpstream(null, reaction_uri, reaction_label, depth, max_depth, stopstring);
		FileWriter f = new FileWriter("/Users/benjamingood/test/manuscript/pyruvate.txt");
		f.write("source_id\tsource_label\tedge_id\tedge_label\ttarget_id\ttarget_label\n");
		for(Edge e : edges) {
			f.write(e.source_id+"\t"+e.source_label+"\t"+e.edge_id+"\t"+e.edge_label+"\t"+e.target_id+"\t"+e.target_label+"\n");
		}
		f.close();
	}
	
	public Set<Edge> recurseUpstream(Set<Edge> edges, String reaction_uri, String reaction_label, int depth, int max_depth, String stopstring) throws IOException{
		if(edges == null) {
			edges = new HashSet<Edge>();
		}
		Set<Edge> new_edges = getUpstream(reaction_uri, reaction_label);
		edges.addAll(new_edges);
		depth++;
		for(Edge e : new_edges) {
			if((!e.target_label.contains(stopstring))
				&&(depth<max_depth)) {
				recurseUpstream(edges, e.source_id, e.source_label, depth, max_depth, stopstring);
			}
		}
		return edges;
	}
	
	public Set<Edge> getUpstream(String reaction_uri, String reaction_label) throws IOException {
		Set<Edge> edges = new HashSet<Edge>();
		try {
			BigdataSailRepositoryConnection connection = alldata_repo.getReadOnlyConnection();
			try {
				String query = ""
						+ "prefix dc: <http://purl.org/dc/elements/1.1/>\n" + 
						"prefix obo: <http://purl.obolibrary.org/obo/> \n" + 
						"prefix rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#> \n" + 
						"prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#>  \n" + 
						"prefix skos: <http://www.w3.org/2004/02/skos/core#>\n" + 
						"prefix canonical: <http://geneontology.org/lego/canonical_record> \n" + 
						"PREFIX part_of: <http://purl.obolibrary.org/obo/BFO_0000050>\n" + 
						"PREFIX has_part: <http://purl.obolibrary.org/obo/BFO_0000051>\n" + 
						"PREFIX occurs_in: <http://purl.obolibrary.org/obo/BFO_0000066>\n" + 
						"PREFIX enabled_by: <http://purl.obolibrary.org/obo/RO_0002333>\n" + 
						"PREFIX has_input: <http://purl.obolibrary.org/obo/RO_0002233>\n" + 
						"PREFIX has_output: <http://purl.obolibrary.org/obo/RO_0002234>\n" + 
						"PREFIX event: <http://purl.obolibrary.org/obo/go/extensions/reacto.owl#molecular_event>\n" + 
						"PREFIX GoInformationBiomacromolecule: <http://purl.obolibrary.org/obo/CHEBI_33695>\n" + 
						"PREFIX GoProtein: <http://purl.obolibrary.org/obo/CHEBI_36080>\n" + 
						"PREFIX GoProteinContainingComplex: <http://purl.obolibrary.org/obo/GO_0032991>\n" + 
						"PREFIX GoCellularComponent: <http://purl.obolibrary.org/obo/GO_0005575>\n" + 
						"PREFIX GoBiologicalProcess: <http://purl.obolibrary.org/obo/GO_0008150>\n" + 
						"PREFIX GoMolecularFunction: <http://purl.obolibrary.org/obo/GO_0003674>\n" + 
						"PREFIX GoChemicalEntity: <http://purl.obolibrary.org/obo/CHEBI_24431>\n" + 
						"PREFIX TransporterActivity: <http://purl.obolibrary.org/obo/GO_0005215> \n" + 
						"PREFIX xref: <http://www.geneontology.org/formats/oboInOwl#hasDbXref>\n" + 
						"PREFIX causally_upstream_of: <http://purl.obolibrary.org/obo/RO_0002411>\n" + 
						"\n" + 
						""
						+ "select distinct ?reaction ?reaction_label ?pathway ?pathway_label {\n" + 
						"  ?reaction causally_upstream_of:* <"+reaction_uri+"> . " + //#\"Pyruvate + CO2 + ATP => ADP + Orthophosphate + Oxaloacetate\"\n" + 
						"  ?reaction part_of: ?pathway . \n" + 
						"  ?pathway rdfs:label ?pathway_label .\n" + 
						"  ?reaction rdfs:label ?reaction_label .  \n" + 
						"} ";
				TupleQuery tupleQuery = connection.prepareTupleQuery(QueryLanguage.SPARQL, query);
				TupleQueryResult result = tupleQuery.evaluate();
				while (result.hasNext()) {
					BindingSet binding = result.next();
					Value r = binding.getValue("reaction");
					Value r_label = binding.getValue("reaction_label");
					Value p = binding.getValue("pathway");		
					Value p_label = binding.getValue("pathway_label");
					String edge_id = "upstream_of";
					String edge_label = "causally upstream of";
					Edge e = new Edge(r.stringValue(), reaction_uri, r_label.stringValue(),reaction_label, edge_id, edge_label);
					edges.add(e);
					Edge p_e = new Edge(r.stringValue(), p.stringValue(), r_label.stringValue(),p_label.stringValue(), "part_of", "part of");
					edges.add(p_e);
				}
			} catch (MalformedQueryException e) {
				throw new IOException(e);
			} catch (QueryEvaluationException e) {
				throw new IOException(e);
			} finally {
				connection.close();
			}
		} catch (RepositoryException e) {
			throw new IOException(e);
		}
		return edges;
	}
	
	private BigdataSailRepository initializeRepository(String pathToJournal) {
		try {
			Properties properties = new Properties();
			//properties.load(this.getClass().getResourceAsStream("onto-blazegraph.properties"));
			properties.setProperty(Options.FILE, pathToJournal);
			BigdataSail sail = new BigdataSail(properties);
			BigdataSailRepository repository = new BigdataSailRepository(sail);

			repository.initialize();
			return repository;
		} catch (RepositoryException e) {
			return null;
		}
	}
}
