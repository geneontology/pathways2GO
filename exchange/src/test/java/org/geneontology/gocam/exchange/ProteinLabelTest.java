package org.geneontology.gocam.exchange;

import static org.junit.Assert.*;

import java.io.FileInputStream;
import org.biopax.paxtools.io.BioPAXIOHandler;
import org.biopax.paxtools.io.SimpleIOHandler;
import org.biopax.paxtools.model.BioPAXLevel;
import org.biopax.paxtools.model.Model;
import org.biopax.paxtools.model.level3.Protein;
import org.biopax.paxtools.model.level3.ProteinReference;
import org.biopax.paxtools.model.level3.UnificationXref;
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

	// Isoform proteins carry their UniProt accession on a UnificationXref whose db is
	// "UniProt Isoform" (not "UniProt"). extractUniprotId must still recognise it, so the
	// canonical gene symbol is used as the label. Otherwise distinct BioPAX proteins that
	// share one isoform ProteinReference (e.g. "PKM-1" and "PolyUb-PKM-1", both P14618-1)
	// collapse onto one GO-CAM individual carrying BOTH raw displayNames as two rdfs:labels.
	@Test
	public void testGetProteinLabelFromReferenceHandlesUniProtIsoformXref() {
		Model m = BioPAXLevel.L3.getDefaultFactory().createModel();
		m.setXmlBase("http://example.org/test#");
		ProteinReference pr = m.addNew(ProteinReference.class, "http://example.org/test#PR1");
		pr.addName("UniProt:P14618-1 PKM");
		pr.addName("PKM");
		UnificationXref x = m.addNew(UnificationXref.class, "http://example.org/test#X1");
		x.setDb("UniProt Isoform");
		x.setId("P14618-1");
		pr.addXref(x);

		Protein polyub = m.addNew(Protein.class, "http://example.org/test#Protein_polyub");
		polyub.setDisplayName("PolyUb-PKM-1");
		polyub.setEntityReference(pr);
		Protein plain = m.addNew(Protein.class, "http://example.org/test#Protein_plain");
		plain.setDisplayName("PKM-1");
		plain.setEntityReference(pr);

		// Both must resolve to the canonical symbol from "UniProt:P14618-1 PKM" -> "PKM",
		// not their divergent displayNames.
		assertEquals("PKM", BioPaxtoGO.getProteinLabelFromReference(polyub));
		assertEquals("PKM", BioPaxtoGO.getProteinLabelFromReference(plain));
	}

}
