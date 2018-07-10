package org.geneontology.gocam.exchange;

import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.biopax.paxtools.model.level3.Interaction;
import org.biopax.paxtools.model.level3.Pathway;
import org.biopax.paxtools.model.level3.Process;

public class GoMappingReport {
	public Map<Process,Set<String>> bp2go_mf = new HashMap<Process, Set<String>>();
	public Map<Process,Set<String>> bp2go_bp = new HashMap<Process, Set<String>>();
	public Map<Process,Set<String>> bp2go_controller = new HashMap<Process, Set<String>>();
	public Map<String, Integer> chebi_count = new HashMap<String, Integer>();
	public Set<String> deprecated_classes = new HashSet<String>();
	public Set<String> inconsistent_models = new HashSet<String>();
	public Map<String, ReasonerReport> pathway_class_report = new HashMap<String, ReasonerReport>();
	public Map<String,Map<String, Set<String>>> pathway_inferred_types = new HashMap<String,Map<String, Set<String>>>();
	
	public void writeReport(String root_folder) throws IOException{
		String mapping_report_file = root_folder+"mapping.txt";
		String chebi_usage_file = root_folder+"chebi_usage.txt";
		String deprecated_file = root_folder+"deprecated_terms_used.txt";
		String inconsistent_file = root_folder+"inconsistent_models.txt";
		String inference_file = root_folder+"inferred_types.txt";
		String reasoner_value_file = root_folder+"reasoner_value.txt";
		String summary_file = root_folder+"ReportSummary.txt";
		Set<Process> all_processes = new HashSet<Process>(bp2go_mf.keySet());		
		all_processes.addAll(new HashSet<Process>(bp2go_bp.keySet()));
		all_processes.addAll(new HashSet<Process>(bp2go_controller.keySet()));
		float n_reactions = 0; float n_pathways = 0; 
		float n_reactions_tagged_mf = 0; float n_reactions_tagged_bp = 0; float n_reactions_tagged_both = 0; 
		float n_pathways_tagged_mf = 0; float n_pathways_tagged_bp = 0; float n_pathways_tagged_both = 0; 
		FileWriter report = new FileWriter(mapping_report_file, false);
		report.write("Reactome Node Type\tReactome label\tGO MF\tGO_BP\tboth\tcontroller_type\t\n");
		for(Process p : all_processes) {
			Set<String> mf = bp2go_mf.get(p);
			Set<String> bp = bp2go_bp.get(p);
			Set<String> controllers = bp2go_controller.get(p);
			String thingtype = ""; String both = ""; 
			if(p instanceof Interaction) {
				thingtype = "Reaction";
				n_reactions++;
				if(mf!=null&&mf.size()>0) {
					n_reactions_tagged_mf++;
				}
				if(bp!=null&&bp.size()>0) {
					n_reactions_tagged_bp++;
				}
				if(mf!=null&&mf.size()>0&&bp!=null&&bp.size()>0) {
					n_reactions_tagged_both++;
					both = "both";
				}
			}else if(p instanceof Pathway) {
				thingtype = "Pathway";
				n_pathways++;
				if(mf!=null&&mf.size()>0) {
					n_pathways_tagged_mf++;
				}
				if(bp!=null&&bp.size()>0) {
					n_pathways_tagged_bp++;
				}
				if(mf!=null&&mf.size()>0&&bp!=null&&bp.size()>0) {
					n_pathways_tagged_both++;
					both = "both";
				}
			}
			String mf_out = ""; String bp_out = ""; String control_out = "";
			if(mf==null||mf.size()==0) {
				mf_out = "none";
			}else {
				for(String m : mf) {
					mf_out = m+" "+mf_out;
				}
			}
			if(bp==null||bp.size()==0) {
				bp_out = "none";
			}else {
				for(String b : bp) {
					bp_out = b+" "+bp_out;
				}
			}
			if(controllers==null||controllers.size()==0) {
				control_out = "none";
			}else {
				for(String c : controllers) {
					control_out = c+" "+control_out;
				}
			}
			String row = thingtype+"\t"+p.getDisplayName()+"\t"+mf_out+"\t"+bp_out+"\t"+both+"\t"+control_out+"\n";
			report.write(row);
		}
		report.close();
		FileWriter chebi_report = new FileWriter(chebi_usage_file);
		chebi_report.write("chebi_uri\ttype\tcount\n");
		for(String chebi : chebi_count.keySet()) {
			chebi_report.write(chebi+"\t"+chebi_count.get(chebi)+"\n");
		}
		chebi_report.close();
		FileWriter dep_report = new FileWriter(deprecated_file);
		dep_report.write("uri\ttype\n");
		for(String d : deprecated_classes) {
			dep_report.write(d+"\n");
		}
		dep_report.close();
		FileWriter logic_report = new FileWriter(inconsistent_file);
		logic_report.write("Model Name\n");
		for(String d : inconsistent_models) {
			logic_report.write(d+"\n");
		}
		logic_report.close();
		//Map<String,Map<String, Set<String>>> pathway_inferred_types
		FileWriter inf_report = new FileWriter(inference_file);
		inf_report.write("pathway\tindividual label\tindividual uri\tinferred_type\n");
		for(String pathway : pathway_inferred_types.keySet()) {			
			Map<String, Set<String>> thing_types = pathway_inferred_types.get(pathway);
			for(String thing : thing_types.keySet()) {
				Set<String> types = thing_types.get(thing);
				for(String type : types) {
					if(type.contains("GO_")) {//skip all the BFO stuff..
						inf_report.write(pathway+"\t"+thing+"\t"+type+"\n");	
					}
				}
			}
		}
		inf_report.close();
		
		ReasonerReport inf_summary = new ReasonerReport();
		FileWriter value_file = new FileWriter(reasoner_value_file);
		value_file.write("pathway\tnew_bp\tnew_mf\tnew_cc\tnew_complex\tnew_total\n");
		for(String pathway : pathway_class_report.keySet()) {
			ReasonerReport r = pathway_class_report.get(pathway);
			inf_summary.bp_new_class_count+=r.bp_new_class_count;
			inf_summary.mf_new_class_count+=r.mf_new_class_count;
			inf_summary.cc_new_class_count+=r.cc_new_class_count;
			inf_summary.complex_new_class_count+=r.complex_new_class_count;
			inf_summary.total_new_classified_instances+=r.total_new_classified_instances;
			value_file.write(pathway+"\t"+r.bp_new_class_count+"\t"+r.mf_new_class_count+"\t"+r.cc_new_class_count+"\t"+r.complex_new_class_count+"\t"+r.total_new_classified_instances+"\n");
		}
		value_file.close();
		FileWriter summary = new FileWriter(summary_file);			
		summary.write("Without considering reasoning for instance classification - just looking at direct Reactome assertions...\n");
		summary.write("Pathways:"+n_pathways+" with bp:"+n_pathways_tagged_bp+" with mf:"+n_pathways_tagged_mf+" both:"+n_pathways_tagged_both+"\n");
		summary.write("% pathways no bp: "+((n_pathways-n_pathways_tagged_bp)/n_pathways)+"\n");
		summary.write("Reactions:"+n_reactions+" with bp:"+n_reactions_tagged_bp+" with mf:"+n_reactions_tagged_mf+" both:"+n_reactions_tagged_both+"\n");
		summary.write("% reactions no mf: "+((n_reactions-n_reactions_tagged_mf)/n_reactions)+"\n");
		summary.write("% reactions with no bp: "+((n_reactions-n_reactions_tagged_bp)/n_reactions)+"\n");
		summary.write("\nFor unclassified instances, reasoner can add "+inf_summary.total_new_classified_instances+" non-trivial (not BFO, non-root GO) classifications for:\n");
		summary.write("\t"+inf_summary.bp_new_class_count+"\tnew bp\n");
		summary.write("\t"+inf_summary.mf_new_class_count+"\tnew mf\n");
		summary.write("\t"+inf_summary.cc_new_class_count+"\tnew cc\n");
		summary.write("\t"+inf_summary.complex_new_class_count+"\tnew complex\n");
		summary.close();
	}
}
