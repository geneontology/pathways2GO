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



	public Manuscript(String bg_jnl) {
		this.alldata_repo = initializeRepository(bg_jnl);
	}

	public static void main(String[] args) throws IOException {
		Manuscript m = new Manuscript("/Users/benjamingood/blazegraph/reactome-oct82020-lego.jnl");
		
		String reaction_uri = "http://model.geneontology.org/R-HSA-70501";
		String reaction_label =  "Pyruvate + CO2 + ATP => ADP + Orthophosphate + Oxaloacetate";
		int depth = 0;
		int max_depth = 4;
		String stopstring = "insulin";
		Set<Edge> edges = m.recurseUpstream(null, reaction_uri, reaction_label, depth, max_depth, stopstring);
		FileWriter f = new FileWriter("/Users/benjamingood/test/manuscript/pyruvate.txt");
		f.write("source_id\tsource_label\tedge_id\tedge_label\ttarget_id\ttarget_label\n");
		for(Edge e : edges) {
			f.write(e.source_id+"\t"+e.source_label+"\t"+e.edge_id+"\t"+e.edge_label+"\t"+e.target_id+"\t"+e.target_label+"\n");
		}
		f.close();
	}
	
	public static void getCausalComparison() throws IOException {
		Map<String, String> prop_label = new HashMap<String, String>();
		prop_label.put("RO_0002413","provides_direct_input_for");		
		prop_label.put("RO_0002629", "directly_positively_regulates");
		prop_label.put("RO_0002630", "directly_negatively_regulates"); 
		prop_label.put("RO_0002411", "causally_upstream_of");

		String out = "/Users/benjamingood/test/manuscript/causal_comparison.txt";
		String network = "/Users/benjamingood/test/manuscript/reaction_network_asserted_in_pathways.txt";
		FileWriter net_writer = new FileWriter(network);
		net_writer.write("source\tsource label\tproperty\tproperty label\ttarget\ttarget_label\n");

		//count the go-cam causal connections
		Map<String, Integer> pathway_causal = new HashMap<String, Integer>();
		Map<String, Set<String>> pathway_causalpairs = new HashMap<String, Set<String>>();
		Map<String, Map<String, Integer>> pathway_rel_count = new HashMap<String, Map<String, Integer>>();
		SortedSet<String> causal_props = new TreeSet<String>();
		String gocamdir = //"/Users/benjamingood/GitHub/noctua_exchange/exchange/src/test/resources/gocam/";
				"/Users/benjamingood/test/reactome/";
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
		String input_biopax = "/Users/benjamingood/test/biopax/June2020_Homo_sapiens.owl";
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
				if(m+pathway_causal.get(pathway_id)!=all_pairs.size()) {
					System.out.println(pathway_id+"\t"+all_pairs.size()+"\t"+internal_pairs.size()+"\t"+pathway_causal.get(pathway_id)+"\t"+m+"\t");
					Set<String> extra_causal = new HashSet<String>(pathway_causalpairs.get(pathway_id));
					extra_causal.removeAll(internal_pairs);
					System.out.println(pathway_id+" oddity - extra causal "+extra_causal);
					Set<String> extra_internal = new HashSet<String>(internal_pairs);
					extra_internal.removeAll(pathway_causalpairs.get(pathway_id));
					System.out.println(pathway_id+" oddity - extra internal "+extra_internal);
					System.out.println();
				}
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
		System.out.println("pathways "+np+" total links "+total_links+" missing links "+missing+" captured links "+caught);

		//report
		//		for(String pathway_id : pathway_instepcount.keySet()) {
		//			Integer reactome_insteps = pathway_instepcount.get(pathway_id);
		//			Integer reactome_outsteps = pathway_outstepcount.get(pathway_id);
		//			Integer reactome_allsteps = pathway_allstepcount.get(pathway_id);
		//			Integer gocam_causal = pathway_causal.get(pathway_id);
		//			Integer reactome_steps_internal = pathway_internalstepcount.get(pathway_id);			
		//			System.out.println(pathway_id+"\t"+reactome_allsteps+"\t"+reactome_insteps+"\t"+reactome_outsteps+"\t"+reactome_steps_internal+"\t"+gocam_causal);
		//			if(gocam_causal!=reactome_steps_internal) {
		//				System.out.println(" Oddity "+pathway_id);
		//			}
		//		}
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
		if(pathway_id.equals("R-HSA-3232118")) {
			System.out.println("");
		}
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
