package org.geneontology.gocam.exchange;

import static org.junit.Assert.*;
import java.util.Collections;
import org.junit.Test;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLNamedIndividual;
import org.semanticweb.owlapi.search.EntitySearcher;

public class SetEnabledReactionSplitTest {

    @Test
    public void testCloneSharesNeighborsAndCopiesTypeWithoutDuplicating() throws Exception {
        GoCAM go = new GoCAM();
        OWLClass mf = go.df.getOWLClass(IRI.create("http://purl.obolibrary.org/obo/GO_0003674"));
        OWLNamedIndividual rxn = go.makeUnannotatedIndividual(IRI.create("http://model.geneontology.org/rxn1"));
        OWLNamedIndividual input = go.makeUnannotatedIndividual(IRI.create("http://model.geneontology.org/in1"));
        OWLNamedIndividual downstream = go.makeUnannotatedIndividual(IRI.create("http://model.geneontology.org/down1"));
        OWLNamedIndividual upstream = go.makeUnannotatedIndividual(IRI.create("http://model.geneontology.org/up1"));
        go.addTypeAssertion(rxn, mf);
        go.addLabel(rxn, "my reaction");
        go.addObjectPropertyAssertion(rxn, GoCAM.has_input, input, null);
        go.addObjectPropertyAssertion(rxn, GoCAM.causally_upstream_of, downstream, null);
        go.addObjectPropertyAssertion(upstream, GoCAM.causally_upstream_of, rxn, null);

        IRI cloneIri = IRI.create("http://model.geneontology.org/rxn1_clone");
        int before = go.go_cam_ont.getIndividualsInSignature().size();
        OWLNamedIndividual clone = go.cloneIndividualSharingNeighbors(
                rxn, cloneIri, Collections.singleton(GoCAM.enabled_by), "m");

        assertTrue("clone keeps MF type", EntitySearcher.getTypes(clone, go.go_cam_ont).contains(mf));
        assertTrue("clone shares input", EntitySearcher.getObjectPropertyValues(clone, GoCAM.has_input, go.go_cam_ont).contains(input));
        assertTrue("clone shares outgoing causal neighbor", EntitySearcher.getObjectPropertyValues(clone, GoCAM.causally_upstream_of, go.go_cam_ont).contains(downstream));
        assertTrue("clone also receives incoming causal edge from upstream", EntitySearcher.getObjectPropertyValues(upstream, GoCAM.causally_upstream_of, go.go_cam_ont).contains(clone));
        assertTrue("original still intact", EntitySearcher.getObjectPropertyValues(rxn, GoCAM.has_input, go.go_cam_ont).contains(input));
        // cloning adds exactly one new individual (the clone) — neighbors are shared, not duplicated
        assertEquals("neighbors must not be duplicated", before + 1, go.go_cam_ont.getIndividualsInSignature().size());
    }

    @Test
    public void testSplitMakesOneActivityPerEnablerAndDeletesOriginal() throws Exception {
        GoCAM go = new GoCAM();
        OWLClass mf = go.df.getOWLClass(IRI.create("http://purl.obolibrary.org/obo/GO_0003674"));
        OWLNamedIndividual rxn = go.makeUnannotatedIndividual(IRI.create("http://model.geneontology.org/R-TEST-1"));
        OWLNamedIndividual p1 = go.makeUnannotatedIndividual(IRI.create("http://model.geneontology.org/prot1"));
        OWLNamedIndividual p2 = go.makeUnannotatedIndividual(IRI.create("http://model.geneontology.org/prot2"));
        OWLNamedIndividual input = go.makeUnannotatedIndividual(IRI.create("http://model.geneontology.org/in1"));
        OWLNamedIndividual output = go.makeUnannotatedIndividual(IRI.create("http://model.geneontology.org/out1"));
        go.addTypeAssertion(rxn, mf);
        go.addObjectPropertyAssertion(rxn, GoCAM.has_input, input, null);
        go.addObjectPropertyAssertion(rxn, GoCAM.has_output, output, null);
        go.addObjectPropertyAssertion(rxn, GoCAM.enabled_by, p1, null);
        go.addObjectPropertyAssertion(rxn, GoCAM.enabled_by, p2, null);
        go.set_enabled_reaction_iris.add(rxn.getIRI());

        go.splitSetEnabledReactions("m");

        assertFalse("original reaction removed", go.go_cam_ont.containsIndividualInSignature(rxn.getIRI()));
        OWLNamedIndividual c1 = go.df.getOWLNamedIndividual(IRI.create("http://model.geneontology.org/R-TEST-1_enabled_by_prot1"));
        OWLNamedIndividual c2 = go.df.getOWLNamedIndividual(IRI.create("http://model.geneontology.org/R-TEST-1_enabled_by_prot2"));
        assertTrue(go.go_cam_ont.containsIndividualInSignature(c1.getIRI()));
        assertTrue(go.go_cam_ont.containsIndividualInSignature(c2.getIRI()));
        assertEquals(1, EntitySearcher.getObjectPropertyValues(c1, GoCAM.enabled_by, go.go_cam_ont).size());
        assertTrue(EntitySearcher.getObjectPropertyValues(c1, GoCAM.enabled_by, go.go_cam_ont).contains(p1));
        assertEquals(1, EntitySearcher.getObjectPropertyValues(c2, GoCAM.enabled_by, go.go_cam_ont).size());
        assertTrue(EntitySearcher.getObjectPropertyValues(c2, GoCAM.enabled_by, go.go_cam_ont).contains(p2));
        assertTrue("clone shares input", EntitySearcher.getObjectPropertyValues(c1, GoCAM.has_input, go.go_cam_ont).contains(input));
        assertTrue("clone shares output", EntitySearcher.getObjectPropertyValues(c2, GoCAM.has_output, go.go_cam_ont).contains(output));
        assertTrue("clone keeps MF type", EntitySearcher.getTypes(c1, go.go_cam_ont).contains(mf));
    }

    @Test
    public void testResolveSetCatalystMembersReducesComplexAndKeepsProteins() throws Exception {
        BioPaxtoGO bp = new BioPaxtoGO();
        bp.entityStrategy = BioPaxtoGO.EntityStrategy.REACTO;
        org.biopax.paxtools.io.BioPAXIOHandler handler = new org.biopax.paxtools.io.SimpleIOHandler();
        org.biopax.paxtools.model.Model model = handler.convertFromOWL(
                new java.io.FileInputStream("./src/test/resources/biopax/R-HSA-1482922_level3.owl"));
        bp.biopax_model = model;

        org.biopax.paxtools.model.level3.PhysicalEntity set = null;
        for(org.biopax.paxtools.model.level3.PhysicalEntity pe : model.getObjects(org.biopax.paxtools.model.level3.PhysicalEntity.class)) {
            if("PLA2(11)".equals(pe.getDisplayName()) && bp.isExplodableEntitySet(pe)) {
                set = pe;
                break;
            }
        }
        assertNotNull("PLA2(11) EntitySet not found / not detected as explodable", set);

        java.util.List<org.biopax.paxtools.model.level3.PhysicalEntity> members = bp.resolveSetCatalystMembers(set);
        java.util.Set<String> names = new java.util.HashSet<String>();
        for(org.biopax.paxtools.model.level3.PhysicalEntity m : members) {
            names.add(m.getDisplayName());
        }
        assertEquals("expected 5 resolved enablers, got " + names, 5, members.size());
        // 4 direct proteins + the protein reduced from the PLA2G4A:Ca2+ complex (its displayName is "cPLA2")
        assertTrue("got " + names, names.containsAll(java.util.Arrays.asList("PLA2G4D", "PLA2G4F", "PLA2G16", "PLBD1", "cPLA2")));
    }
}
