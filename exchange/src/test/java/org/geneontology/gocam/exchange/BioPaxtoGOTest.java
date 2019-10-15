/**
 * 
 */
package org.geneontology.gocam.exchange;

import static org.junit.Assert.*;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * @author bgood
 *
 */
public class BioPaxtoGOTest {

	/**
	 * @throws java.lang.Exception
	 */
	@BeforeClass
	public static void setUpBeforeClass() throws Exception {
	}

	/**
	 * @throws java.lang.Exception
	 */
	@AfterClass
	public static void tearDownAfterClass() throws Exception {
	}

	/**
	 * @throws java.lang.Exception
	 */
	@Before
	public void setUp() throws Exception {
	}

	/**
	 * @throws java.lang.Exception
	 */
	@After
	public void tearDown() throws Exception {
	}

	/**
	 * Test method for {@link org.geneontology.gocam.exchange.BioPaxtoGO#BioPaxtoGO()}.
	 * 
	 * 		//"Glycolysis"; //"Signaling by BMP"; //"TCF dependent signaling in response to WNT"; //"RAF-independent MAPK1/3 activation";//"Oxidative Stress Induced Senescence"; //"Activation of PUMA and translocation to mitochondria";//"HDR through Single Strand Annealing (SSA)";  //"IRE1alpha activates chaperones"; //"Generation of second messenger molecules";//null;//"Clathrin-mediated endocytosis";
		//next tests: 
		//for continuant problem: Import of palmitoyl-CoA into the mitochondrial matrix 
		//error in rule rule:reg3 NTRK2 activates RAC1
		//
		//(rule:reg3) The relation 'DOCK3 binds FYN associated with NTRK2' 'directly positively regulates' 'DOCK3 activates RAC1' was inferred because: reaction1 has an output that is the enabler of reaction 2.
		//test for active site recognition
		//	test_pathways.add("SCF(Skp2)-mediated degradation of p27/p21");
		//unions
		//			test_pathways.add("GRB2 events in ERBB2 signaling");
		//			test_pathways.add("Elongator complex acetylates replicative histone H3, H4");
		//looks good
		//	test_pathways.add("Attenuation phase");
		//		test_pathways.add("NTRK2 activates RAC1");
		//		test_pathways.add("Unwinding of DNA");
		//		test_pathways.add("Regulation of TNFR1 signaling");
		//		test_pathways.add("SCF(Skp2)-mediated degradation of p27/p21");
		//inconsistent, but not sure how to fix		
		//test_pathways.add("tRNA modification in the nucleus and cytosol");
		//inconsistent
		//test_pathways.add("Apoptosis induced DNA fragmentation");

		//		test_pathways.add("SHC1 events in ERBB4 signaling");
		//looks good.  example of converting binding function to regulatory process template
		//	 test_pathways.add("FRS-mediated FGFR3 signaling");
		//	 test_pathways.add("FRS-mediated FGFR4 signaling");
		//looks good, nice inference for demo	 
		//		 test_pathways.add("Activation of G protein gated Potassium channels");
		//		 test_pathways.add("Regulation of actin dynamics for phagocytic cup formation");
		//		 test_pathways.add("SHC-mediated cascade:FGFR2");
		//		 test_pathways.add("SHC-mediated cascade:FGFR3");
		//check this one for annotations on regulates edges
		//		test_pathways.add("RAF-independent MAPK1/3 activation");
		//great example of why we are not getting a complete data set without inter model linking.  
		//		test_pathways.add("TCF dependent signaling in response to WNT");
		//looks great..
		//looks good 
		//	test_pathways.add("activated TAK1 mediates p38 MAPK activation");
		//check for relations between events that might not be biopax typed chemical reactions - e.g. degradation
		//			test_pathways.add("HDL clearance");
	 * 
	 */
	@Test
	public final void testBioPaxtoGO() {
		fail("Not yet implemented"); // TODO
	}

	/**
	 * Test method for {@link org.geneontology.gocam.exchange.BioPaxtoGO#main(java.lang.String[])}.
	 */
	@Test
	public final void testMain() {
		fail("Not yet implemented"); // TODO
	}

	/**
	 * Test method for {@link org.geneontology.gocam.exchange.BioPaxtoGO#getEntityReferenceId(org.biopax.paxtools.model.level3.Entity)}.
	 */
	@Test
	public final void testGetEntityReferenceId() {
		fail("Not yet implemented"); // TODO
	}

}
