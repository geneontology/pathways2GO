/**
 * 
 */
package org.geneontology.gocam.exchange;

/**
 * @author bgood
 *
 */
public class ReasonerReport {
	int mf_new_class_count;
	int bp_new_class_count;
	int cc_new_class_count;
	int complex_new_class_count;
	int total_new_classified_instances;
	/**
	 * 
	 */
	public ReasonerReport(ClassificationReport dumb, ClassificationReport smart) {
		mf_new_class_count = (smart.mf_count-smart.mf_unclassified) - (dumb.mf_count-dumb.mf_unclassified);
		bp_new_class_count = (smart.bp_count-smart.bp_unclassified) - (dumb.bp_count-dumb.bp_unclassified);
		cc_new_class_count = (smart.cc_count-smart.cc_unclassified) - (dumb.cc_count-dumb.cc_unclassified);
		complex_new_class_count = (smart.complex_count-smart.complex_unclassified) - (dumb.complex_count-dumb.complex_unclassified);
		total_new_classified_instances = mf_new_class_count + bp_new_class_count + cc_new_class_count + complex_new_class_count;
	}

	public ReasonerReport() {
		mf_new_class_count = 0;
		bp_new_class_count = 0;
		cc_new_class_count = 0;
		complex_new_class_count = 0;
		total_new_classified_instances = 0;
	}
}
