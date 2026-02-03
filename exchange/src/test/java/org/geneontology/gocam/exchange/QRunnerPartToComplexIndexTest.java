package org.geneontology.gocam.exchange;

import static org.junit.Assert.*;

import java.util.Set;

import org.junit.Test;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.*;

/**
 * Test that partToComplexIndex only stores REACTO/UniProt keys
 */
public class QRunnerPartToComplexIndexTest {

    private static final String REACTO_PREFIX = "http://purl.obolibrary.org/obo/go/extensions/reacto.owl#REACTO_";
    private static final String UNIPROT_PREFIX = "http://identifiers.org/uniprot/";

    @Test
    public void testPartToComplexIndexOnlyContainsReactoOrUniprotKeys() throws Exception {
        // This test verifies the fix for filtering non-REACTO keys
        // We'll create a minimal ontology with a REACTO complex that has:
        // 1. A REACTO protein part (should be indexed)
        // 2. A CHEBI small molecule part (should NOT be indexed)

        OWLOntologyManager manager = OWLManager.createOWLOntologyManager();
        OWLOntology tbox = manager.createOntology();
        OWLDataFactory df = manager.getOWLDataFactory();

        // Create classes
        OWLClass reactoComplex = df.getOWLClass(IRI.create(REACTO_PREFIX + "R-HSA-12345"));
        OWLClass reactoProtein = df.getOWLClass(IRI.create(REACTO_PREFIX + "R-HSA-67890"));
        OWLClass chebiMolecule = df.getOWLClass(IRI.create("http://purl.obolibrary.org/obo/CHEBI_12345"));

        // Create has_part property
        OWLObjectProperty hasPart = df.getOWLObjectProperty(IRI.create("http://purl.obolibrary.org/obo/BFO_0000051"));

        // Create axiom: reactoComplex EquivalentTo (has_part some reactoProtein) and (has_part some chebiMolecule)
        OWLClassExpression hasReactoProtein = df.getOWLObjectSomeValuesFrom(hasPart, reactoProtein);
        OWLClassExpression hasChebiMolecule = df.getOWLObjectSomeValuesFrom(hasPart, chebiMolecule);
        OWLClassExpression intersection = df.getOWLObjectIntersectionOf(hasReactoProtein, hasChebiMolecule);
        OWLAxiom eqAxiom = df.getOWLEquivalentClassesAxiom(reactoComplex, intersection);
        manager.addAxiom(tbox, eqAxiom);

        // Create QRunner with this tbox
        java.util.Collection<OWLOntology> tboxes = java.util.Collections.singletonList(tbox);
        QRunner qrunner = new QRunner(tboxes, null, null, true, false, false);

        // Verify: reactoProtein should map to reactoComplex
        Set<OWLClass> complexesForProtein = qrunner.getComplexClassesWithPart(reactoProtein);
        assertTrue("REACTO protein should be found in index", complexesForProtein.contains(reactoComplex));

        // Verify: chebiMolecule should NOT be in index (empty result)
        Set<OWLClass> complexesForChebi = qrunner.getComplexClassesWithPart(chebiMolecule);
        assertTrue("CHEBI molecule should NOT be in index", complexesForChebi.isEmpty());
    }
}
