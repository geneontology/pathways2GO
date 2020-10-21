package org.geneontology.garage;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.query.QuerySolution;
import org.apache.jena.query.ResultSet;
import org.apache.jena.rdf.model.Literal;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Resource;
import org.biopax.paxtools.io.BioPAXIOHandler;
import org.biopax.paxtools.io.SimpleIOHandler;
import org.biopax.paxtools.model.Model;
import org.biopax.paxtools.model.level3.Control;
import org.biopax.paxtools.model.level3.Pathway;
import org.biopax.paxtools.model.level3.PathwayStep;
import org.biopax.paxtools.model.level3.Process;
import org.geneontology.gocam.exchange.BioPaxtoGO;
import org.openrdf.model.URI;
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

public class Manuscript {

	BigdataSailRepository alldata_repo;

	public static String prefixes = 
			"prefix dc: <http://purl.org/dc/elements/1.1/>\n" + 
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
			"\n";

	public Manuscript(String bg_jnl) {
		this.alldata_repo = initializeRepository(bg_jnl);
	}

	public static void main(String[] args) throws IOException {
		Manuscript m = new Manuscript("/Users/benjamingood/blazegraph/blazegraph.jnl"); //reactome-oct82020-lego
		//m.runCounts();
		//m.buildVenn("/Users/benjamingood/test/manuscript/venn_data/");
		//m.getCausalComparison("/Users/benjamingood/test/reactome/", 
		//		"/Users/benjamingood/test/biopax/June2020_Homo_sapiens.owl",
		//		 "/Users/benjamingood/test/manuscript/");
		m.getInterestingInferences("/Users/benjamingood/test/manuscript/mf_inferences.txt");
	}
	
	private void getInterestingInferences(String out) throws IOException {
		String mf_inferences_q = 
				prefixes+""
			+ " select distinct ?mfi ?mfi_label (count (distinct ?xref) as ?n_reactions) { \n" + 
				"	?reaction xref: ?xref . \n" + 
				"    ?reaction rdfs:label ?rlabel . \n" + 
				"    ?reaction rdf:type GoMolecularFunction: . \n" + 
				"  graph ?asserted {\n" + 
				"    ?reaction rdf:type ?mf . \n" + 
				"  }\n" + 
				"  filter(?mf != owl:NamedIndividual) . \n" + 
				"  filter(?asserted != <”http://model.geneontology.org/inferences”>) . \n" + 
				"  \n" + 
				"  graph <”http://model.geneontology.org/inferences”> {	\n" + 
				"   	?reaction rdf:type ?mfi . \n" + 
				"   }\n" + 
				"   filter(?mfi != owl:NamedIndividual) . \n" + 
				"   filter(?mfi != ?mf) . \n" + 
				"  minus{?mf rdfs:subClassOf* ?mfi} .\n" + 
				"  ?mfi rdfs:label ?mfi_label . \n" + 
				"}\n" + 
				"group by ?mfi ?mfi_label ";
		try {
			BigdataSailRepositoryConnection connection = alldata_repo.getReadOnlyConnection();
			try {
			FileWriter f = new FileWriter(out);	
				TupleQuery tupleQuery = connection.prepareTupleQuery(QueryLanguage.SPARQL, mf_inferences_q);
				TupleQueryResult result = tupleQuery.evaluate();
				while (result.hasNext()) {
					BindingSet binding = result.next();
					Value mfi = binding.getValue("mfi");
					Value mfi_label = binding.getValue("mfi_label");
					Value n_reactions = binding.getValue("n_reactions");
					f.write(mfi.stringValue()+"\t"+mfi_label.stringValue()+"\t"+n_reactions.stringValue()+"\n");
				}
			f.close();
			String n_reactions_q = "select (count (distinct ?xref) as ?n_reactions) { \n" + 
					"	?reaction xref: ?xref . \n" + 
					"    ?reaction rdfs:label ?rlabel . \n" + 
					"    ?reaction rdf:type GoMolecularFunction: . \n" + 
					"  graph ?asserted {\n" + 
					"    ?reaction rdf:type ?mf . \n" + 
					"  }\n" + 
					"  filter(?mf != owl:NamedIndividual) . \n" + 
					"  filter(?asserted != <”http://model.geneontology.org/inferences”>) . \n" + 
					"  \n" + 
					"  graph <”http://model.geneontology.org/inferences”> {	\n" + 
					"   	?reaction rdf:type ?mfi . \n" + 
					"   }\n" + 
					"   filter(?mfi != owl:NamedIndividual) . \n" + 
					"   filter(?mfi != ?mf) . \n" + 
					"  minus{?mf rdfs:subClassOf* ?mfi} .\n" + 
					"  ?mfi rdfs:label ?mfi_label . \n" + 
					"}";
			tupleQuery = connection.prepareTupleQuery(QueryLanguage.SPARQL, n_reactions_q);
			result = tupleQuery.evaluate();
			while (result.hasNext()) {
				BindingSet binding = result.next();
				Value n_reactions = binding.getValue("n_reactions");
				System.out.println("n reactions with non-parent inferred MF "+n_reactions);
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
	}
	
	private void runCounts() throws NumberFormatException, IOException {
		String n_reactions_q = "select (count(distinct ?reaction) as ?r) {\n" + 
				"  {?reaction rdf:type event:}\n" + 
				"  UNION\n" + 
				"  {?reaction rdf:type GoMolecularFunction:} \n" + 
				"  ?reaction xref: ?xref \n" + 
				"}\n";
		String n_mf_reactions_q = "select (count(distinct ?reaction) as ?r) {\n" + 
				"  ?reaction rdf:type GoMolecularFunction: . \n" + 
				"  ?reaction xref: ?xref \n" + 
				"}\n";
		String n_me_reactions_q = "select (count(distinct ?reaction) as ?r)  where  { \n" + 
				"  ?reaction xref: ?xref .   \n" + 
				"  ?reaction rdf:type event: .\n" + 
				"  minus{?reaction rdf:type GoMolecularFunction:}\n" + 
				"}\n";
		String n_reactions_with_enabler_q = "select (count(distinct ?reaction) as ?r)   {\n" + 
				"  	?reaction xref: ?xref . \n" + 
				"    ?reaction enabled_by: ?enabler . \n" + 
				"  	{       ?reaction rdf:type GoMolecularFunction:   }\n" + 
				"    UNION \n" + 
				"    {     ?reaction rdf:type event:}  \n" + 
				"}\n";
		String n_reactions_no_enabler_q = "select (count(distinct ?reaction) as ?r)   {\n" + 
				"  	?reaction xref: ?xref . \n" + 
				"    minus{?reaction enabled_by: ?enabler} \n" + 
				"  	{       ?reaction rdf:type GoMolecularFunction:  }\n" + 
				"    UNION \n" + 
				"    {     ?reaction rdf:type event:}\n" + 
				"}\n";
		String n_reactions_mf_no_enabler_q = "select (count(distinct ?reaction) as ?r)   {\n" + 
				"  	?reaction xref: ?xref .\n" + 
				"    ?reaction rdf:type GoMolecularFunction: . \n" + 
				"    minus{?reaction enabled_by: ?enabler} \n" + 
				"}\n";
		String reactions_with_bp_q = "select (count(distinct ?reaction) as ?r)  { \n" + 
				"	?reaction xref: ?xref . # ensure we don't count generated binding nodes - only direct conversions\n" + 
				"    { ?reaction rdf:type GoMolecularFunction:} UNION {?reaction rdf:type event:} # get all the converted reactions\n" + 
				"  graph ?graph {	\n" + 
				"    #minus{?reaction enabled_by: ?enabler}\n" + 
				"    #minus{?reaction occurs_in: ?location } . \n" + 
				"   	?reaction part_of: ?bp . \n" + 
				"   	?bp rdf:type ?bpclass .\n" + 
				"    filter(?bpclass != owl:NamedIndividual ) . \n" + 
				"    filter(?bpclass != GoBiologicalProcess: ) \n" + 
				"   }\n" + 
				"}\n";
		String reactions_with_causal_q = "select (count(distinct ?reaction) as ?r)  where  { \n" + 
				"    { ?reaction rdf:type GoMolecularFunction:} UNION {?reaction rdf:type event:} # get all the converted reactions\n" + 
				"    {  ?reaction xref: ?xref . \n" + 
				"      ?up_reaction obo:RO_0002411 ?reaction .\n" + 
				"      ?up_reaction xref: ?xref2 . } \n" + 
				"  UNION {\n" + 
				"    ?reaction xref: ?xref . \n" + 
				"    ?reaction obo:RO_0002411 ?down_reaction . \n" + 
				"     ?down_reaction xref: ?xref3 }\n" + 
				"}\n";
		String n_reactions_no_causal_q = "select (count(distinct ?reaction) as ?r) where{\n" + 
				"{ ?reaction rdf:type GoMolecularFunction:} UNION {?reaction rdf:type event:} # get all the converted reactions\n" + 
				"	?reaction xref: ?xref . \n" + 
				"  minus  { \n" + 
				"    { \n" + 
				"      ?up_reaction obo:RO_0002411 ?reaction .\n" + 
				"      ?up_reaction xref: ?xref2 . } \n" + 
				"  UNION {\n" + 
				"    ?reaction obo:RO_0002411 ?down_reaction . \n" + 
				"     ?down_reaction xref: ?xref3 }\n" + 
				"      }\n" + 
				"}\n";
		String n_reactions_no_occurs_in_q = "select (count(distinct ?reaction) as ?r)   {\n" + 
				"  	?reaction xref: ?xref .\n" + 
				"    { ?reaction rdf:type GoMolecularFunction:} UNION {?reaction rdf:type event:} # get all the converted reactions\n" + 
				"    minus{?reaction occurs_in: ?location} \n" + 
				"}\n";
		String n_reactions_with_occurs_in_q = "select (count(distinct ?reaction) as ?r)   {\n" + 
				"  	?reaction xref: ?xref .\n" + 
				"    { ?reaction rdf:type GoMolecularFunction:} UNION {?reaction rdf:type event:} # get all the converted reactions\n" + 
				"    ?reaction occurs_in: ?location \n" + 
				"}\n";
		String n_reactions_with_part_of_q = "select (count(distinct ?reaction) as ?r)   {\n" + 
				"  	?reaction xref: ?xref .\n" + 
				"    { ?reaction rdf:type GoMolecularFunction:} UNION {?reaction rdf:type event:} # get all the converted reactions\n" + 
				"    ?reaction part_of: ?bp \n" + 
				"}\n";
		String n_reactions_complete_q = "select (count(distinct ?reaction) as ?r)  where  { \n" + 
				" #graph ?graph {	\n" + 
				"    { ?reaction rdf:type GoMolecularFunction:} UNION {?reaction rdf:type event:} # get all the converted reactions\n" + 
				"    {  ?reaction xref: ?xref . \n" + 
				"      ?up_reaction obo:RO_0002411 ?reaction .\n" + 
				"      ?up_reaction xref: ?xref2 . } \n" + 
				"  UNION {\n" + 
				"    ?reaction xref: ?xref . \n" + 
				"    ?reaction obo:RO_0002411 ?down_reaction . \n" + 
				"     ?down_reaction xref: ?xref3 }\n" + 
				"    ?reaction enabled_by: ?enabler . \n" + 
				"    ?reaction occurs_in: ?location  . \n" + 
				"   	?reaction part_of: ?bp . \n" + 
				"   	?bp rdf:type ?bpclass .\n" + 
				"    filter(?bpclass != owl:NamedIndividual ) . \n" + 
				"    filter(?bpclass != GoBiologicalProcess: ) \n" + 
				"}\n";
		int n_reactions, n_mf_reactions, n_me_reactions, n_reactions_with_enabler, n_reactions_no_enabler;
		int n_reactions_mf_no_enabler, n_reactions_with_bp, n_reactions_with_causal, n_reactions_no_causal;
		int n_reactions_no_occurs_in, n_reactions_with_part_of, n_reactions_no_part_of, n_reactions_complete;
		
		n_reactions = Integer.parseInt(runSingleResultQuery(prefixes+" "+n_reactions_q).iterator().next());
		System.out.println("n_reactions "+n_reactions);
		n_mf_reactions = Integer.parseInt(runSingleResultQuery(prefixes+" "+n_mf_reactions_q).iterator().next());
		System.out.println("n_mf_reactions "+n_mf_reactions);
		n_me_reactions = Integer.parseInt(runSingleResultQuery(prefixes+" "+n_me_reactions_q).iterator().next());
		System.out.println("n_me_reactions "+n_me_reactions);
		n_reactions_with_enabler = Integer.parseInt(runSingleResultQuery(prefixes+" "+n_reactions_with_enabler_q).iterator().next());
		System.out.println("n_reactions_with_enabler "+n_reactions_with_enabler);		
		n_reactions_no_enabler = Integer.parseInt(runSingleResultQuery(prefixes+" "+n_reactions_no_enabler_q).iterator().next());
		System.out.println("n_reactions_no_enabler "+n_reactions_no_enabler);
		n_reactions_mf_no_enabler = Integer.parseInt(runSingleResultQuery(prefixes+" "+n_reactions_mf_no_enabler_q).iterator().next());
		System.out.println("n_reactions_mf_no_enabler "+n_reactions_mf_no_enabler);
		
		n_reactions_with_bp = Integer.parseInt(runSingleResultQuery(prefixes+" "+reactions_with_bp_q).iterator().next());
		System.out.println("n_reactions_with (non-root) bp "+n_reactions_with_bp);
		System.out.println("n_reactions_with no (non-root) bp "+(n_reactions - n_reactions_with_bp));
		
		n_reactions_with_causal = Integer.parseInt(runSingleResultQuery(prefixes+" "+reactions_with_causal_q).iterator().next());
		System.out.println("n_reactions with causal "+n_reactions_with_causal);		
		n_reactions_no_causal = Integer.parseInt(runSingleResultQuery(prefixes+" "+n_reactions_no_causal_q).iterator().next());
		System.out.println("n_reactions with no causal "+n_reactions_no_causal);
		n_reactions_no_occurs_in = Integer.parseInt(runSingleResultQuery(prefixes+" "+n_reactions_no_occurs_in_q).iterator().next());
		System.out.println("n_reactions_no_occurs_in "+n_reactions_no_occurs_in);	
		n_reactions_complete = Integer.parseInt(runSingleResultQuery(prefixes+" "+n_reactions_complete_q).iterator().next());
		System.out.println("n_reactions_complete "+n_reactions_complete);		
	}	
	
	private void buildVenn(String outfolder) throws IOException {
		String no_enabler_q =  
				prefixes+
				"select distinct ?r  {\n" + 
				"  { ?reaction rdf:type GoMolecularFunction: } "
				+ "UNION { ?reaction rdf:type event:} . 	\n" + 
				"  ?reaction xref: ?r . \n" + 
				"    minus{?reaction enabled_by: ?enabler} \n" + 
				"  	\n" + 
				"}";
		Set<String> no_enablers = runSingleResultQuery(no_enabler_q);
		String no_function_q =  
				prefixes+
				"select distinct ?r where  { \n" + 
				"  ?reaction xref: ?r .   \n" + 
				"  ?reaction rdf:type event: .\n" + 
				"  minus{?reaction rdf:type GoMolecularFunction:}\n" + 
				"}\n" + 
				"";
		Set<String> no_function = runSingleResultQuery(no_function_q);
		
		String no_location_q = 
				prefixes+
				"select distinct ?r  {\n" + 
				"  	?reaction xref: ?r .\n" + 
				"    { ?reaction rdf:type GoMolecularFunction:} UNION {?reaction rdf:type event:} # get all the converted reactions\n" + 
				"    minus{?reaction occurs_in: ?location} \n" + 
				"}\n";
		Set<String> no_occurs = runSingleResultQuery(no_location_q);
		
		String no_causal_q = 
				prefixes+
				"select distinct ?r where{\n" + 
				"{ ?reaction rdf:type GoMolecularFunction:} UNION {?reaction rdf:type event:} # get all the converted reactions\n" + 
				"	?reaction xref: ?r . \n" + 
				"  minus  { \n" + 
				"    { \n" + 
				"      ?up_reaction obo:RO_0002411 ?reaction .\n" + 
				"      ?up_reaction xref: ?xref2 . } \n" + 
				"  UNION {\n" + 
				"    ?reaction obo:RO_0002411 ?down_reaction . \n" + 
				"     ?down_reaction xref: ?xref3 }\n" + 
				"      }\n" + 
				"}\n";
		Set<String> no_causal = runSingleResultQuery(no_causal_q);
		
		System.out.println("no enablers "+no_enablers.size()+"\nno function "+no_function.size()+"\nno location "+no_occurs.size()+"\nno causal "+no_causal.size());
		String enablers_out = outfolder+"no_enablers.txt";
		FileWriter f = new FileWriter(enablers_out);
		for(String r : no_enablers) {
			f.write(r+"\n");
		}
		f.close();
		String functions_out = outfolder+"no_function.txt";
		f = new FileWriter(functions_out);
		for(String r : no_function) {
			f.write(r+"\n");
		}
		f.close();
		String location_out = outfolder+"no_location.txt";
		f = new FileWriter(location_out);
		for(String r : no_occurs) {
			f.write(r+"\n");
		}
		f.close();
		String causal_out = outfolder+"no_causal.txt";
		f = new FileWriter(causal_out);
		for(String r : no_causal) {
			f.write(r+"\n");
		}
		f.close();
	}

	private void getCausalComparison(String gocamdir, String input_biopax, String outfolder) throws IOException {
		Map<String, String> prop_label = new HashMap<String, String>();
		prop_label.put("RO_0002413","provides_direct_input_for");		
		prop_label.put("RO_0002629", "directly_positively_regulates");
		prop_label.put("RO_0002630", "directly_negatively_regulates"); 
		prop_label.put("RO_0002411", "causally_upstream_of");

		String out = outfolder+"causal_comparison.txt";
		String network = outfolder+"reaction_network_asserted_in_pathways.txt";
		FileWriter net_writer = new FileWriter(network);
		net_writer.write("source\tsource label\tproperty\tproperty label\ttarget\ttarget_label\n");

		//count the go-cam causal connections
		Map<String, Integer> pathway_causal = new HashMap<String, Integer>();
		Map<String, Set<String>> pathway_causalpairs = new HashMap<String, Set<String>>();
		Map<String, Map<String, Integer>> pathway_rel_count = new HashMap<String, Map<String, Integer>>();
		SortedSet<String> causal_props = new TreeSet<String>();
		File gf = new File(gocamdir);
		if(gf.isDirectory()) {
			for(File file : gf.listFiles()) {
				if(file.getName().endsWith("ttl")) {
					//String pathway_id = file.getName().replace(".ttl", "");
					//get the model as RDF
					org.apache.jena.rdf.model.Model model = ModelFactory.createDefaultModel();
					model.read(file.getAbsolutePath());
					String pathway_id = lookupPathwayId(model);
					//extract the causal relations
					String query = "PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#> "
							+ "PREFIX obo: <http://purl.obolibrary.org/obo/> "
							+ "PREFIX part_of: <http://purl.obolibrary.org/obo/BFO_0000050> " 
							+ "PREFIX xref: <http://www.geneontology.org/formats/oboInOwl#hasDbXref> "
							+ "prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#> "
							+ "select distinct ?reaction ?reaction_label ?reaction_xref ?upstream_reaction ?upstream_label ?upstream_xref ?uprel ?binding_reaction " + 
							"where { " + 
							" ?reaction xref: ?reaction_xref . " +
							//	" ?reaction part_of: ?pathway . "
							//	+ "?pathway xref: ?pathway_id . " + 
							" ?upstream_reaction xref: ?upstream_xref . " +
							" ?upstream_reaction rdfs:label ?upstream_label . "+
							" ?reaction          rdfs:label ?reaction_label . "+
							" {"
							+ "VALUES ?uprel {obo:RO_0002024 obo:RO_0002023 obo:RO_0002413 obo:RO_0002411 obo:RO_0002212 obo:RO_0002213 obo:RO_0002629 obo:RO_0002630} . "  
							+ "?upstream_reaction ?uprel ?reaction . "
							+ "} "
							+ "UNION { "
							+ "VALUES ?uprel {obo:RO_0002629 obo:RO_0002630} . "  
							+ " ?upstream_reaction obo:RO_0002413 ?binding_reaction . "
							+ " ?binding_reaction rdf:type obo:GO_0005488 . "
							+ " ?binding_reaction ?uprel ?reaction }"
							+ "}";
					//pathways 1817 total links 11718 missing links 4128 captured links 7602
					//limit to unique pairs of reactions to avoid counting multiple causal relations between the same reaction pair as more than 1
					Set<String> r1r2 = new HashSet<String>();
					QueryExecution qe = QueryExecutionFactory.create(query, model);
					ResultSet results = qe.execSelect();
					boolean found_causal = false;
					while (results.hasNext()) {
						found_causal = true;
						QuerySolution qs = results.next();
						Integer causal_count = pathway_causal.get(pathway_id);
						if(causal_count==null) {
							causal_count = 0;
						}
						Map<String, Integer> rel_count = pathway_rel_count.get(pathway_id);
						if(rel_count==null) {
							rel_count = new HashMap<String, Integer>();
						}
						Resource reaction = qs.getResource("reaction");
						Resource upstream_reaction = qs.getResource("upstream_reaction");
						Literal reaction_xref = qs.getLiteral("reaction_xref");
						Literal reaction_label = qs.getLiteral("reaction_label");
						Literal upstream_xref = qs.getLiteral("upstream_xref");
						Literal upstream_label = qs.getLiteral("upstream_label");

						Resource prop = qs.getResource("uprel");						
						//Resource binding_reaction = qs.getResource("binding_reaction");
						if(reaction!=null&&upstream_reaction!=null) {
							if(r1r2.add(upstream_xref.getString().replace("Reactome:", "")+"_"+reaction_xref.getString().replace("Reactome:", ""))) {
								String net_row = 
										pathway_id+"_"+upstream_xref.getString()+"\t"+
												upstream_label.getString()+
												"\t"+prop.getLocalName()+
												"\t"+prop_label.get(prop.getLocalName())+"\t"+
												pathway_id+"_"+reaction_xref.getString()+
												"\t"+reaction_label.getString()+
												"\n";
								net_writer.write(net_row);
								causal_count++;
								String causal_prop = prop.getLocalName();
								//								if(binding_reaction!=null) {
								//									causal_prop = "provides_input_for_binding_regulates";
								//								}else {
								//									causal_prop = prop.getLocalName();
								//								}
								causal_props.add(causal_prop);
								Integer rel_count_n = rel_count.get(causal_prop);
								if(rel_count_n==null) {
									rel_count_n=0;
								}
								rel_count_n++;
								rel_count.put(causal_prop, rel_count_n);
								pathway_rel_count.put(pathway_id, rel_count);
							}
						}
						pathway_causal.put(pathway_id, causal_count);
					}
					if(!found_causal) {
						pathway_causal.put(pathway_id, 0);
					}else {
						pathway_causalpairs.put(pathway_id, r1r2);
					}
				}
			}
		}
		net_writer.close();
		FileWriter writer = new FileWriter(out);
		//count the pathway steps 
		BioPAXIOHandler handler = new SimpleIOHandler();
		FileInputStream f = new FileInputStream(input_biopax);
		Model biopax_model = handler.convertFromOWL(f);
		//	Map<String, Integer> pathway_instepcount = new HashMap<String, Integer>();
		//	Map<String, Integer> pathway_outstepcount = new HashMap<String, Integer>();
		Map<String, Integer> pathway_allstepcount = new HashMap<String, Integer>();
		Map<String, Integer> pathway_internalstepcount = new HashMap<String, Integer>();
		int missing = 0; int caught = 0; int np = 0; int total_links = 0;
		String header = "pathway\ttotal_links\tinternal_links\tconverted_links\tmissing_links\t";
		for(String causal : causal_props) {
			String c = causal; 
			if(c.contentEquals("RO_0002411")) {
				c = "causally upstream of";
			}else if(c.contentEquals("RO_0002413")) {
				c = "directly provides input for";
			}else if (c.contentEquals("RO_0002629")) {
				c = "directly positively regulates";
			}else if (c.contentEquals("RO_0002630")) {
				c = "directly negatively regulates";
			}
			header+=c+"\t";
		}
		writer.write(header+"\n");
		for (Pathway currentPathway : biopax_model.getObjects(Pathway.class)){
			String pathway_id = BioPaxtoGO.getEntityReferenceId(currentPathway);
			Set<String> all_pairs = getStepPairs(currentPathway, false);
			pathway_allstepcount.put(pathway_id, all_pairs.size());
			Set<String> internal_pairs = getStepPairs(currentPathway, true);
			pathway_internalstepcount.put(pathway_id, internal_pairs.size());		
			if(pathway_causal.get(pathway_id)!=null) { //only count pathways where we have the conversion here. 
				np++;
				int m = (all_pairs.size()-internal_pairs.size());
				missing+=m;
				caught+=pathway_causal.get(pathway_id);
				total_links+=all_pairs.size();
				writer.write(pathway_id+"\t"+all_pairs.size()+"\t"+internal_pairs.size()+"\t"+pathway_causal.get(pathway_id)+"\t"+m+"\t");
				for(String causal_prop : causal_props) {
					int in_c = 0;
					if(pathway_rel_count.get(pathway_id)!=null) {
						if(pathway_rel_count.get(pathway_id).get(causal_prop)!=null) {
							in_c = pathway_rel_count.get(pathway_id).get(causal_prop);
						}
					}
					writer.write(in_c+"\t");
				}
				writer.write("\n");
			}
		}
		writer.close();
		System.out.println("pathways "+np+" total biopax links "+total_links+" cross pathway links "+missing+" captured links "+caught);
	}

	
	private Set<String> runSingleResultQuery(String query) throws IOException{
		Set<String> results = new HashSet<String>();
		try {
			BigdataSailRepositoryConnection connection = alldata_repo.getReadOnlyConnection();
			try {
				
				TupleQuery tupleQuery = connection.prepareTupleQuery(QueryLanguage.SPARQL, query);
				TupleQueryResult result = tupleQuery.evaluate();
				while (result.hasNext()) {
					BindingSet binding = result.next();
					Value r = binding.getValue("r");
					results.add(r.stringValue());
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
		return results;
	}
	
	public static Set<String> getStepPairs(Pathway currentPathway, boolean internal_only){
		Set<String> r1r2 = new HashSet<String>();
		String pathway_id = BioPaxtoGO.getEntityReferenceId(currentPathway);
		for(PathwayStep step1 : currentPathway.getPathwayOrder()) {
			Set<Process> processes1 = step1.getStepProcess();
			String r1 = null;
			for(Process p : processes1) {
				if(p instanceof Control || p instanceof Pathway) {
					continue;
				}
				r1=BioPaxtoGO.getEntityReferenceId(p);
			}
			//all the nextsteps!  (commented out to support global comparisons and not double count the same edge from different pathway start points)
			//			Set<PathwayStep> step2s = step1.getNextStep();
			//			if(step2s!=null) {
			//				for(PathwayStep step2 : step2s) {
			//					String r2 = null;
			//					Set<Process> processes2 = step2.getStepProcess();
			//					for(Process p : processes2) {
			//						if(p instanceof Control || p instanceof Pathway) {
			//							continue;
			//						}						
			//						if(internal_only) {
			//							if(inPathway(p, currentPathway)) {
			//								r2 = BioPaxtoGO.getEntityReferenceId(p);
			//							}
			//						}else {
			//							r2 = BioPaxtoGO.getEntityReferenceId(p);
			//						}
			//					}
			//					if(r1!=null&&r2!=null) {
			//						r1r2.add(r1+"_"+r2);
			//					}					
			//				}
			//			}
			//all the prevsteps
			Set<PathwayStep> step0s = step1.getNextStepOf();
			if(step0s!=null) {
				for(PathwayStep step0 : step0s) {
					String r0 = null;
					Set<Process> processes0 = step0.getStepProcess();
					for(Process p : processes0) {
						if(p instanceof Control || p instanceof Pathway) {
							continue;
						}
						if(internal_only) {
							if(inPathway(p, currentPathway)) {
								r0 = BioPaxtoGO.getEntityReferenceId(p);
							}
						}else {
							r0 = BioPaxtoGO.getEntityReferenceId(p);
						}					
					}
					if(r0!=null&&r1!=null) {
						r1r2.add(r0+"_"+r1);
					}
				}
			}
		}
		return r1r2;
	}

	public static boolean inPathway(Process p, Pathway currentPathway) {
		boolean inpathway = false;
		for(Pathway pathway : p.getPathwayComponentOf()) {
			if(pathway.equals(currentPathway)) {
				inpathway = true;
				break;
			}
		}
		return inpathway;
	}


	public static Set<PathwayStep> getSteps(Pathway currentPathway, boolean instep) {
		String pathway_id = BioPaxtoGO.getEntityReferenceId(currentPathway);
//		if(pathway_id.equals("R-HSA-3232118")) {
//			System.out.println("");
//		}
		Set<PathwayStep> allsteps = new HashSet<PathwayStep>();
		for(PathwayStep step : currentPathway.getPathwayOrder()) {
			Set<PathwayStep> steps = null;
			if(instep) {
				steps = step.getNextStepOf();
			}else {
				steps = step.getNextStep();
			}
			if(steps!=null) {
				for(PathwayStep s : steps) {
					Set<Process> processes = s.getStepProcess();
					for(Process p : processes) {
						if(p instanceof Control || p instanceof Pathway) {
							continue;
						}
						allsteps.add(s);
					}
				}
			}
		}
		return allsteps;
	}

	public static String lookupPathwayId(org.apache.jena.rdf.model.Model model) {
		String query = "PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#> "
				+ "select distinct ?contributor " + 
				"where { " + 
				" ?gocam rdf:type <http://www.w3.org/2002/07/owl#Ontology> . " +
				" ?gocam <http://purl.org/dc/elements/1.1/contributor> ?contributor "
				+ "}";
		String pathway_id = null;
		QueryExecution qe = QueryExecutionFactory.create(query, model);
		ResultSet results = qe.execSelect();
		if (results.hasNext()) {
			QuerySolution qs = results.next();
			Literal contributor = qs.getLiteral("contributor");
			pathway_id = contributor.getString().replace("https://reactome.org/content/detail/", "");
		}
		return pathway_id;
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
