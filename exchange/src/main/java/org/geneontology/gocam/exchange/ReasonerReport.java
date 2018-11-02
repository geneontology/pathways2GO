/**
 * 
 */
package org.geneontology.gocam.exchange;

import java.util.Map;
import java.util.Set;

/**
 * @author bgood
 *
 */
public class ReasonerReport {
	public GoCAMReport gocamreport;
	public int mf_new_class_count;
	public int bp_new_class_count;
	public int cc_new_class_count;
	public int complex_new_class_count;
	public int mf_deepened_class_count;
	public int bp_deepened_class_count;
	public int cc_deepened_class_count;
	public int complex_deepened_class_count;
	public int total_new_classified_instances;
	public int total_deepened_classified_instances;
	/**
	 * 
	 */
	
	//this version is really just measuring the results of OWL reasoning
	public ReasonerReport(GoCAMReport report) {
		gocamreport = report;
		mf_new_class_count = report.mf_inferred_type;
		bp_new_class_count = report.bp_inferred_type;
		cc_new_class_count = 0;// as we don't do anything with these
		complex_new_class_count = report.complex_inferred_type;
		total_new_classified_instances = mf_new_class_count + bp_new_class_count + cc_new_class_count + complex_new_class_count;
	
		mf_deepened_class_count = report.mf_deepened;
		bp_deepened_class_count = report.bp_deepened;
		complex_deepened_class_count = report.complex_deepened;
		total_deepened_classified_instances = mf_deepened_class_count + bp_deepened_class_count + complex_deepened_class_count;
	}
	
	//this one captures any change between the models
	public ReasonerReport(GoCAMReport dumb, GoCAMReport smart) {
		mf_new_class_count = (smart.mf_count-smart.mf_unclassified) - (dumb.mf_count-dumb.mf_unclassified);
		bp_new_class_count = (smart.bp_count-smart.bp_unclassified) - (dumb.bp_count-dumb.bp_unclassified);
		cc_new_class_count = (smart.cc_count-smart.cc_unclassified) - (dumb.cc_count-dumb.cc_unclassified);
		complex_new_class_count = (smart.complex_count-smart.complex_unclassified) - (dumb.complex_count-dumb.complex_unclassified);
		total_new_classified_instances = mf_new_class_count + bp_new_class_count + cc_new_class_count + complex_new_class_count;
	
		mf_deepened_class_count = countDeepened("mf", dumb.function_types, smart.function_types);
		bp_deepened_class_count = countDeepened("bp",dumb.pathway_types, smart.pathway_types);
		complex_deepened_class_count = countDeepened("complex", dumb.complex_types, smart.complex_types);
		total_deepened_classified_instances = mf_deepened_class_count + bp_deepened_class_count + complex_deepened_class_count;
	}

	public int countDeepened(String t, Map<String, Set<String>> dumb, Map<String, Set<String>> smart) {
		int d = 0;
		for(String thing : dumb.keySet()) {
			Set<String> dumb_types = dumb.get(thing);
			if(dumb_types==null||dumb_types.size()==0) {
				continue; //these would be counted elsewhere as new classes
			}
			Set<String> smart_types = smart.get(thing);
			if(smart_types==null||smart_types.size()==0) {
				continue; //these would be counted elsewhere as new classes
			}
			smart_types.removeAll(dumb_types);
			if(smart_types.size()>0) {
				d++;
				System.out.println(t+" deepened "+thing+" with "+smart_types+"\nadded to pre-reasoned : "+dumb_types);
			}
		}
		return d;
	}
	
	public ReasonerReport() {
		mf_new_class_count = 0;
		bp_new_class_count = 0;
		cc_new_class_count = 0;
		complex_new_class_count = 0;
		total_new_classified_instances = 0;
		mf_deepened_class_count = 0;
		bp_deepened_class_count = 0;
		cc_deepened_class_count = 0;
		total_deepened_classified_instances = 0;
	}
	
	
}
