package org.geneontology.gocam.exchange;

import static org.junit.Assert.*;

import java.io.FileInputStream;
import org.biopax.paxtools.io.BioPAXIOHandler;
import org.biopax.paxtools.io.SimpleIOHandler;
import org.biopax.paxtools.model.Model;
import org.biopax.paxtools.model.level3.Protein;
import org.junit.Test;

public class ProteinLabelTest {

	private static final String BIOPAX =
			"./src/test/resources/biopax/R-HSA-9018679_level3.owl";
	// xml:base of the fixture; Protein1/Protein2 both reference ProteinReference1 (UniProt P35354).
	private static final String BASE = "http://www.reactome.org/biopax/97/9018679#";

	@Test
	public void testGetProteinLabelFromReferenceStripsUniprotPrefix() throws Exception {
		BioPAXIOHandler handler = new SimpleIOHandler();
		Model model = handler.convertFromOWL(new FileInputStream(BIOPAX));
		Protein p1 = (Protein) model.getByID(BASE + "Protein1"); // displayName "Ac-PTGS2"
		Protein p2 = (Protein) model.getByID(BASE + "Protein2"); // displayName "PTGS2"
		assertNotNull("Protein1 missing from fixture", p1);
		assertNotNull("Protein2 missing from fixture", p2);
		// Name "UniProt:P35354 PTGS2" -> "PTGS2" for both components.
		assertEquals("PTGS2", BioPaxtoGO.getProteinLabelFromReference(p1));
		assertEquals("PTGS2", BioPaxtoGO.getProteinLabelFromReference(p2));
	}

}
