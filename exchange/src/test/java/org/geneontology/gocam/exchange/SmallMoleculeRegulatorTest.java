package org.geneontology.gocam.exchange;

import static org.junit.Assert.*;

import java.util.HashSet;
import java.util.Set;

import org.junit.Test;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLDataFactory;
import org.semanticweb.owlapi.model.OWLNamedIndividual;
import org.semanticweb.owlapi.model.OWLObjectProperty;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyManager;

/**
 * Unit tests for the regulator-classification helpers used by
 * {@link GoCAM#inferSmallMoleculeRegulators}. These exercise the decision logic directly
 * (over hand-built type closures / ontologies) so they are deterministic and independent
 * of which tbox happens to be loaded — the production go-lego-reacto tbox types every
 * UniProt protein under CHEBI:36080 (protein) -> ... -> CHEBI:24431 (chemical entity),
 * which is exactly what makes the too-broad "chemical entity" test misfire in production
 * but never in the stripped test tbox.
 */
public class SmallMoleculeRegulatorTest {

	private static final String OBO = "http://purl.obolibrary.org/obo/";
	private static final OWLDataFactory DF = OWLManager.getOWLDataFactory();

	private static OWLClass cls(String frag) {
		return DF.getOWLClass(IRI.create(OBO + frag));
	}

	// A protein's type-closure (UniProt class -> CHEBI:36080 -> ... -> CHEBI:24431) must NOT
	// be treated as a small molecule, even though it contains CHEBI:24431 (chemical entity).
	@Test
	public void testProteinIsNotSmallMoleculeRegulator() {
		Set<OWLClass> proteinClosure = new HashSet<OWLClass>();
		proteinClosure.add(cls("CHEBI_36080"));  // protein
		proteinClosure.add(cls("CHEBI_33695"));  // information biomacromolecule
		proteinClosure.add(cls("CHEBI_24431"));  // chemical entity
		assertFalse(GoCAM.isSmallMoleculeRegulatorType(proteinClosure));
	}

	// A genuine small molecule (chemical entity, not protein, not nucleic acid) still qualifies.
	@Test
	public void testSmallMoleculeIsSmallMoleculeRegulator() {
		Set<OWLClass> smallMolClosure = new HashSet<OWLClass>();
		smallMolClosure.add(cls("CHEBI_30616"));  // ATP(4-)
		smallMolClosure.add(cls("CHEBI_24431"));  // chemical entity
		assertTrue(GoCAM.isSmallMoleculeRegulatorType(smallMolClosure));
	}

	// Nucleic acids (CHEBI:33696) are already excluded and must stay excluded.
	@Test
	public void testNucleicAcidIsNotSmallMoleculeRegulator() {
		Set<OWLClass> naClosure = new HashSet<OWLClass>();
		naClosure.add(cls("CHEBI_33696"));  // nucleic acid
		naClosure.add(cls("CHEBI_24431"));  // chemical entity
		assertFalse(GoCAM.isSmallMoleculeRegulatorType(naClosure));
	}

	// A complex is typed GO:0032991 (not under CHEBI:24431) and is not a small molecule.
	@Test
	public void testComplexIsNotSmallMoleculeRegulator() {
		Set<OWLClass> complexClosure = new HashSet<OWLClass>();
		complexClosure.add(cls("GO_0032991"));  // protein-containing complex
		assertFalse(GoCAM.isSmallMoleculeRegulatorType(complexClosure));
	}

	// An individual used as the object of enabled_by (RO_0002333) is an enabler and must be
	// recognised as such, so a dropped non-small-molecule regulation edge does not delete it.
	@Test
	public void testEnabledByObjectIsRecognisedAsEnabler() throws Exception {
		OWLOntologyManager m = OWLManager.createOWLOntologyManager();
		OWLOntology ont = m.createOntology();
		OWLNamedIndividual reaction = DF.getOWLNamedIndividual(IRI.create("http://model.geneontology.org/rxn"));
		OWLNamedIndividual entity = DF.getOWLNamedIndividual(IRI.create("http://model.geneontology.org/P14618-1"));
		OWLObjectProperty enabledBy = DF.getOWLObjectProperty(IRI.create(OBO + "RO_0002333"));
		m.addAxiom(ont, DF.getOWLObjectPropertyAssertionAxiom(enabledBy, reaction, entity));

		assertTrue(GoCAM.isEnablerOrInput(ont, entity));
	}

	// An individual that only regulates (no incoming enabled_by / has_input) is not an enabler,
	// so it is safe to delete as a pure non-small-molecule regulator.
	@Test
	public void testPureRegulatorIsNotEnabler() throws Exception {
		OWLOntologyManager m = OWLManager.createOWLOntologyManager();
		OWLOntology ont = m.createOntology();
		OWLNamedIndividual reaction = DF.getOWLNamedIndividual(IRI.create("http://model.geneontology.org/rxn"));
		OWLNamedIndividual entity = DF.getOWLNamedIndividual(IRI.create("http://model.geneontology.org/complexReg"));
		OWLObjectProperty involvedInNegReg = DF.getOWLObjectProperty(IRI.create(OBO + "RO_0002430"));
		m.addAxiom(ont, DF.getOWLObjectPropertyAssertionAxiom(involvedInNegReg, entity, reaction));

		assertFalse(GoCAM.isEnablerOrInput(ont, entity));
	}

}
