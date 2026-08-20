# Split Set-Enabled Reactions Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

> **Project policy (CLAUDE.md): do NOT run `git add` or `git commit`.** Each task ends at a **Checkpoint** (tests green). The user commits manually. The skill's usual "commit" step is replaced by "Checkpoint" throughout.

**Goal:** Convert a Reactome reaction catalyzed by an EntitySet into one GO-CAM activity per set member, each `enabled_by` a distinct gene product, sharing the reaction's inputs/outputs/pathway/causal edges.

**Architecture:** Two phases. Phase 1 (during build, in `BioPaxtoGO.defineReactionEntity`'s catalysis controller loop): detect an EntitySet catalyst, resolve its members to leaf enablers (reducing single-protein complexes, keeping multi-protein complexes whole), wire `reaction enabled_by memberᵢ` for each, and record the reaction IRI. Phase 2 (`GoCAM.splitSetEnabledReactions`, called in `wrapAndWrite` before the SPARQL rules): clone the reaction once per enabler — sharing the same neighbor individuals — give each clone a single `enabled_by`, and delete the original. The existing `provides_input_for`/regulation SPARQL rules then fan out across the clones for free.

**Tech Stack:** Java 8, Maven, OWL API, Paxtools (BioPAX), Apache Jena, Blazegraph, JUnit 4. Spec: `.specs/2026-06-03-split-set-enabled-reactions-design.md`.

---

## File Structure

- **Modify** `exchange/src/main/java/org/geneontology/gocam/exchange/GoCAM.java`
  - New field `Set<IRI> set_enabled_reaction_iris` (records reactions to split).
  - New method `cloneIndividualSharingNeighbors(...)` (clone a node, share its neighbors, fresh edge evidence).
  - New method `splitSetEnabledReactions(String model_id)` (Phase 2 pass).
- **Modify** `exchange/src/main/java/org/geneontology/gocam/exchange/BioPaxtoGO.java`
  - New methods `isExplodableEntitySet(Controller)` and `resolveSetCatalystMembers(PhysicalEntity)`.
  - Wire detection + recording into the catalysis controller loop (~`:1729`).
  - Call `go_cam.splitSetEnabledReactions(reactome_id)` in `wrapAndWrite` before `:551`.
- **Create** `exchange/src/test/java/org/geneontology/gocam/exchange/SetEnabledReactionSplitTest.java` (fast unit tests; no heavy `@BeforeClass`).
- **Create** `exchange/src/test/resources/biopax/R-HSA-1482922_level3.owl` (copy of `exchange/R-HSA-1482922_level3.owl`; converted by `BioPaxtoGOTest`'s `@BeforeClass`).
- **Modify** `exchange/src/test/java/org/geneontology/gocam/exchange/BioPaxtoGOTest.java` (end-to-end assertion).

All new methods/fields are **package-private** (no modifier) so same-package tests can call them.

---

## Task 1: `GoCAM.cloneIndividualSharingNeighbors`

Clone an individual so the clone points at the **same** neighbor individuals (unlike the existing `cloneIndividual`, which duplicates neighbors). Each cloned edge gets **fresh** evidence via the existing `cloneAnnotations(...)`, so later deleting the original (which deletes its edges' evidence) can't dangle a clone's evidence.

**Files:**
- Modify: `exchange/src/main/java/org/geneontology/gocam/exchange/GoCAM.java` (add method near `cloneIndividual`, ~`:1786`)
- Test: `exchange/src/test/java/org/geneontology/gocam/exchange/SetEnabledReactionSplitTest.java`

- [ ] **Step 1: Write the failing test** — create the new test file:

```java
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
        OWLNamedIndividual clone = go.cloneIndividualSharingNeighbors(
                rxn, cloneIri, Collections.singleton(GoCAM.enabled_by), "m");

        assertTrue("clone keeps MF type", EntitySearcher.getTypes(clone, go.go_cam_ont).contains(mf));
        assertTrue("clone shares input", EntitySearcher.getObjectPropertyValues(clone, GoCAM.has_input, go.go_cam_ont).contains(input));
        assertTrue("clone shares outgoing causal neighbor", EntitySearcher.getObjectPropertyValues(clone, GoCAM.causally_upstream_of, go.go_cam_ont).contains(downstream));
        assertTrue("incoming edge re-pointed to clone", EntitySearcher.getObjectPropertyValues(upstream, GoCAM.causally_upstream_of, go.go_cam_ont).contains(clone));
        assertTrue("original still intact", EntitySearcher.getObjectPropertyValues(rxn, GoCAM.has_input, go.go_cam_ont).contains(input));
        // no neighbor duplication: rxn, in1, down1, up1, rxn1_clone = exactly 5 individuals
        assertEquals("neighbors must not be duplicated", 5, go.go_cam_ont.getIndividualsInSignature().size());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd exchange && mvn -Dtest=SetEnabledReactionSplitTest#testCloneSharesNeighborsAndCopiesTypeWithoutDuplicating test`
Expected: **compilation failure** — `cannot find symbol: method cloneIndividualSharingNeighbors`.

- [ ] **Step 3: Write minimal implementation** — add to `GoCAM.java` immediately after the existing `cloneIndividual(OWLNamedIndividual, ...)` method (the one ending at ~`:1846`):

```java
	/*
	 * Clone an individual so the clone points at the SAME neighbor individuals
	 * (does not duplicate neighbors, unlike cloneIndividual). Copies class
	 * assertions and node annotations. Each cloned edge gets fresh evidence via
	 * cloneAnnotations so deleting the source later cannot dangle a clone's evidence.
	 * Edges whose property is in exclude_props are skipped.
	 */
	OWLNamedIndividual cloneIndividualSharingNeighbors(OWLNamedIndividual source, IRI new_iri, Set<OWLObjectProperty> exclude_props, String model_id) {
		OWLNamedIndividual clone = makeUnannotatedIndividual(new_iri);
		for(OWLClassExpression type : EntitySearcher.getTypes(source, go_cam_ont)) {
			addTypeAssertion(clone, type);
		}
		for(OWLAnnotationAssertionAxiom ax : EntitySearcher.getAnnotationAssertionAxioms(source.getIRI(), go_cam_ont)) {
			OWLAnnotationAssertionAxiom a_ax = df.getOWLAnnotationAssertionAxiom((OWLAnnotationSubject) clone.getIRI(), ax.getAnnotation());
			ontman.applyChange(new AddAxiom(go_cam_ont, a_ax));
		}
		for(OWLAxiom ax : EntitySearcher.getReferencingAxioms(source, go_cam_ont)) {
			if(!ax.isOfType(AxiomType.OBJECT_PROPERTY_ASSERTION)) {
				continue;
			}
			OWLObjectPropertyAssertionAxiom op = (OWLObjectPropertyAssertionAxiom) ax;
			if(exclude_props != null && exclude_props.contains(op.getProperty().asOWLObjectProperty())) {
				continue;
			}
			Set<OWLAnnotation> edge_annos = cloneAnnotations(op.getAnnotations(), model_id, new_iri);
			OWLObjectPropertyAssertionAxiom add = null;
			if(source.equals(op.getSubject())) {
				add = df.getOWLObjectPropertyAssertionAxiom(op.getProperty(), clone, op.getObject(), edge_annos);
			} else if(source.equals(op.getObject())) {
				add = df.getOWLObjectPropertyAssertionAxiom(op.getProperty(), op.getSubject(), clone, edge_annos);
			}
			if(add != null) {
				ontman.applyChange(new AddAxiom(go_cam_ont, add));
			}
		}
		return clone;
	}
```

All referenced types (`AxiomType`, `OWLObjectPropertyAssertionAxiom`, `OWLAnnotationAssertionAxiom`, `OWLAnnotationSubject`, `AddAxiom`, `EntitySearcher`, `OWLClassExpression`, `OWLAnnotation`, `OWLObjectProperty`, `Set`) are already imported in `GoCAM.java` (used by `cloneIndividual`). If the compiler reports a missing import, add it.

- [ ] **Step 4: Run test to verify it passes**

Run: `cd exchange && mvn -Dtest=SetEnabledReactionSplitTest#testCloneSharesNeighborsAndCopiesTypeWithoutDuplicating test`
Expected: **BUILD SUCCESS**, Tests run: 1, Failures: 0.

- [ ] **Step 5: Checkpoint** — confirm the test is green. Do not commit (user commits). Stop here for review if running task-by-task.

---

## Task 2: `GoCAM.splitSetEnabledReactions` + recording field

Split each recorded reaction (≥2 `enabled_by` targets) into one activity per enabler, then delete the original.

**Files:**
- Modify: `exchange/src/main/java/org/geneontology/gocam/exchange/GoCAM.java` (field near `:160`; method near Task 1's)
- Test: `exchange/src/test/java/org/geneontology/gocam/exchange/SetEnabledReactionSplitTest.java`

- [ ] **Step 1: Write the failing test** — add to `SetEnabledReactionSplitTest`:

```java
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
        assertTrue(EntitySearcher.getObjectPropertyValues(c2, GoCAM.enabled_by, go.go_cam_ont).contains(p2));
        assertTrue("clone shares input", EntitySearcher.getObjectPropertyValues(c1, GoCAM.has_input, go.go_cam_ont).contains(input));
        assertTrue("clone shares output", EntitySearcher.getObjectPropertyValues(c2, GoCAM.has_output, go.go_cam_ont).contains(output));
        assertTrue("clone keeps MF type", EntitySearcher.getTypes(c1, go.go_cam_ont).contains(mf));
    }
```

Add these imports to the test file's import block (alongside the Task 1 imports):

```java
import org.semanticweb.owlapi.model.OWLIndividual;
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd exchange && mvn -Dtest=SetEnabledReactionSplitTest#testSplitMakesOneActivityPerEnablerAndDeletesOriginal test`
Expected: **compilation failure** — `cannot find symbol: variable set_enabled_reaction_iris` and `method splitSetEnabledReactions`.

- [ ] **Step 3a: Add the recording field** — in `GoCAM.java`, immediately after `String name;` (`:160`):

```java
	//reactions catalyzed by an EntitySet, to be split into one activity per member in splitSetEnabledReactions()
	Set<IRI> set_enabled_reaction_iris = new HashSet<IRI>();
```

- [ ] **Step 3b: Add the split method** — in `GoCAM.java`, after `cloneIndividualSharingNeighbors` (from Task 1):

```java
	/*
	 * For each reaction recorded as catalyzed by an EntitySet, replace it with one
	 * clone per enabler (sharing the same neighbor individuals), each enabled_by a
	 * single member, then delete the original. Must run before applySparqlRules so
	 * provides_input_for / regulation rules fan out across the clones.
	 */
	void splitSetEnabledReactions(String model_id) {
		for(IRI reaction_iri : new HashSet<IRI>(set_enabled_reaction_iris)) {
			OWLNamedIndividual reaction = df.getOWLNamedIndividual(reaction_iri);
			Collection<OWLIndividual> enablers = EntitySearcher.getObjectPropertyValues(reaction, enabled_by, go_cam_ont);
			if(enablers.size() < 2) {
				continue;
			}
			String reaction_id = reaction_iri.toString().replace("http://model.geneontology.org/", "");
			for(OWLIndividual enabler_ind : new HashSet<OWLIndividual>(enablers)) {
				OWLNamedIndividual enabler = enabler_ind.asOWLNamedIndividual();
				String member_id = enabler.getIRI().toString().replace("http://model.geneontology.org/", "");
				IRI clone_iri = makeGoCamifiedIRI(null, reaction_id + "_enabled_by_" + member_id);
				OWLNamedIndividual clone = cloneIndividualSharingNeighbors(reaction, clone_iri, Collections.singleton(enabled_by), model_id);
				Set<OWLAnnotation> enabler_annos = getObjectPropertyEdgeAnnotations(reaction, enabled_by, enabler);
				OWLObjectPropertyAssertionAxiom eb = df.getOWLObjectPropertyAssertionAxiom(
						enabled_by, clone, enabler, cloneAnnotations(enabler_annos, model_id, clone_iri));
				ontman.applyChange(new AddAxiom(go_cam_ont, eb));
				addComment(clone, "split from set-enabled reaction " + reaction_id);
			}
			deleteOwlEntityAndAllReferencesToIt(reaction, false);
		}
	}

	private Set<OWLAnnotation> getObjectPropertyEdgeAnnotations(OWLNamedIndividual subject, OWLObjectProperty prop, OWLNamedIndividual object) {
		for(OWLObjectPropertyAssertionAxiom ax : go_cam_ont.getObjectPropertyAssertionAxioms(subject)) {
			if(ax.getProperty().equals(prop) && ax.getObject().equals(object)) {
				return ax.getAnnotations();
			}
		}
		return new HashSet<OWLAnnotation>();
	}
```

(`Collection` and `OWLIndividual` are already imported in `GoCAM.java`; if not, add `java.util.Collection` / `org.semanticweb.owlapi.model.OWLIndividual`.)

- [ ] **Step 4: Run test to verify it passes**

Run: `cd exchange && mvn -Dtest=SetEnabledReactionSplitTest#testSplitMakesOneActivityPerEnablerAndDeletesOriginal test`
Expected: **BUILD SUCCESS**, Tests run: 1, Failures: 0.

- [ ] **Step 5: Run both unit tests together**

Run: `cd exchange && mvn -Dtest=SetEnabledReactionSplitTest test`
Expected: Tests run: 2, Failures: 0.

- [ ] **Step 6: Checkpoint** — both graph-surgery methods green. Do not commit.

---

## Task 3: `BioPaxtoGO` set detection + member resolution

Decide whether a catalysis controller is an explodable EntitySet, and resolve its members to leaf enablers (single-protein complex → reduced protein; multi-protein complex → the complex itself; nested set → recurse).

**Files:**
- Modify: `exchange/src/main/java/org/geneontology/gocam/exchange/BioPaxtoGO.java` (add two methods; e.g. just before `defineReactionEntity` at `:1140`)
- Create fixture: `exchange/src/test/resources/biopax/R-HSA-1482922_level3.owl`
- Test: `exchange/src/test/java/org/geneontology/gocam/exchange/SetEnabledReactionSplitTest.java`

- [ ] **Step 1: Add the test fixture file**

Run: `cp exchange/R-HSA-1482922_level3.owl exchange/src/test/resources/biopax/R-HSA-1482922_level3.owl`
Expected: file exists at the destination (verify with `ls exchange/src/test/resources/biopax/R-HSA-1482922_level3.owl`).

- [ ] **Step 2: Write the failing test** — add to `SetEnabledReactionSplitTest`:

```java
    @Test
    public void testResolveSetCatalystMembersReducesComplexAndKeepsProteins() throws Exception {
        BioPaxtoGO bp = new BioPaxtoGO();
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
        // 4 direct proteins + the protein reduced from the PLA2G4A:Ca2+ complex (displayName "cPLA2")
        assertTrue("got " + names, names.containsAll(java.util.Arrays.asList("PLA2G4D", "PLA2G4F", "PLA2G16", "PLBD1", "cPLA2")));
    }
```

- [ ] **Step 3: Run test to verify it fails**

Run: `cd exchange && mvn -Dtest=SetEnabledReactionSplitTest#testResolveSetCatalystMembersReducesComplexAndKeepsProteins test`
Expected: **compilation failure** — `cannot find symbol: method isExplodableEntitySet` / `resolveSetCatalystMembers`.

- [ ] **Step 4: Write minimal implementation** — add to `BioPaxtoGO.java` just before `private void defineReactionEntity(...)` (`:1140`):

```java
	/*
	 * True when a catalysis controller is a Reactome EntitySet we should explode:
	 * a bare PhysicalEntity (not a Complex) with members that are not all small molecules.
	 */
	boolean isExplodableEntitySet(Controller controller_entity) {
		if(!(controller_entity instanceof PhysicalEntity)) {
			return false;
		}
		if(controller_entity instanceof Complex) {
			return false;
		}
		PhysicalEntity pe = (PhysicalEntity) controller_entity;
		Set<PhysicalEntity> members = pe.getMemberPhysicalEntity();
		if(members == null || members.isEmpty()) {
			return false;
		}
		if(setIsSmallMoleculesOnly(members)) {
			return false;
		}
		return true;
	}

	/*
	 * Resolve an EntitySet's members to leaf enablers:
	 *  - small molecule members are dropped
	 *  - a complex that reduces to exactly one protein -> that protein
	 *  - a complex that cannot reduce (>=2 proteins, or none) -> the complex itself
	 *  - a nested set -> recurse
	 * De-duplicated by Reactome id.
	 */
	List<PhysicalEntity> resolveSetCatalystMembers(PhysicalEntity set) {
		List<PhysicalEntity> resolved = new ArrayList<PhysicalEntity>();
		Set<String> seen = new HashSet<String>();
		Set<String> visiting = new HashSet<String>();
		resolveSetCatalystMembers(set, resolved, seen, visiting);
		return resolved;
	}

	private void resolveSetCatalystMembers(PhysicalEntity set, List<PhysicalEntity> resolved, Set<String> seen, Set<String> visiting) {
		String set_id = getEntityReferenceId(set);
		if(set_id != null && !visiting.add(set_id)) {
			return; // cycle guard for nested sets
		}
		for(PhysicalEntity m : set.getMemberPhysicalEntity()) {
			if(m instanceof SmallMolecule) {
				continue;
			}
			if(m instanceof Complex) {
				Set<PhysicalEntity> units = getComplexActiveUnitRecursive((Complex) m).getActiveUnits();
				PhysicalEntity enabler = (units.size() == 1) ? units.iterator().next() : m;
				addResolvedMember(enabler, resolved, seen);
			} else if(!m.getMemberPhysicalEntity().isEmpty()) {
				resolveSetCatalystMembers(m, resolved, seen, visiting);
			} else {
				addResolvedMember(m, resolved, seen);
			}
		}
	}

	private void addResolvedMember(PhysicalEntity enabler, List<PhysicalEntity> resolved, Set<String> seen) {
		String key = getEntityReferenceId(enabler);
		if(key == null || seen.add(key)) {
			resolved.add(enabler);
		}
	}
```

`Controller`, `PhysicalEntity`, `Complex`, `SmallMolecule`, `List`, `ArrayList`, `Set`, `HashSet` are already imported in `BioPaxtoGO.java`. `setIsSmallMoleculesOnly` (`:1117`) and `getComplexActiveUnitRecursive` (`:2085`) are existing private methods in the same class.

- [ ] **Step 5: Run test to verify it passes**

Run: `cd exchange && mvn -Dtest=SetEnabledReactionSplitTest#testResolveSetCatalystMembersReducesComplexAndKeepsProteins test`
Expected: **BUILD SUCCESS**, Tests run: 1, Failures: 0.

- [ ] **Step 6: Checkpoint** — resolver green. Do not commit.

---

## Task 4: Wire detection into the build, run the split, end-to-end test

Connect Phase 1 (record + per-member `enabled_by`) in the controller loop and Phase 2 (`splitSetEnabledReactions`) in `wrapAndWrite`, then assert the whole pipeline on the fixture.

**Files:**
- Modify: `exchange/src/main/java/org/geneontology/gocam/exchange/BioPaxtoGO.java` (controller loop ~`:1729`; `wrapAndWrite` ~`:551`)
- Test: `exchange/src/test/java/org/geneontology/gocam/exchange/BioPaxtoGOTest.java`

- [ ] **Step 1: Write the failing test** — add to `BioPaxtoGOTest` (anywhere among the other `@Test` methods, e.g. after `testInferProvidesInput`):

```java
	/**
	 * Pathway R-HSA-1482922: reaction R-HSA-1482825 is catalyzed by EntitySet PLA2(11)
	 * (4 proteins + PLA2G4A:Ca2+ complex). It must become 5 activities, each enabled_by a
	 * distinct protein, with nothing left on the original reaction node. Sibling reaction
	 * R-HSA-1482868 (EntitySet PLA2(12): PLA2G4C + PLA2G2A:Ca2+) must become 2 activities.
	 */
	@Test
	public final void testSetEnabledReactionSplit() {
		System.out.println("Testing set-enabled reaction split");
		try {
			TupleQueryResult orig = blaze.runSparqlQuery(
				"prefix obo: <http://purl.obolibrary.org/obo/> select ?e where { "
				+ "<http://model.geneontology.org/R-HSA-1482825> obo:RO_0002333 ?e }");
			int origN = 0;
			while(orig.hasNext()) { orig.next(); origN++; }
			orig.close();
			assertTrue("original set reaction should be split away, enablers on it = " + origN, origN == 0);

			TupleQueryResult res = blaze.runSparqlQuery(
				"prefix obo: <http://purl.obolibrary.org/obo/> select ?reaction ?enabler where { "
				+ "?reaction obo:RO_0002333 ?enabler . "
				+ "FILTER(STRSTARTS(STR(?reaction), \"http://model.geneontology.org/R-HSA-1482825_enabled_by\")) }");
			Set<String> reactions = new HashSet<String>();
			Set<String> enablers = new HashSet<String>();
			while(res.hasNext()) {
				BindingSet b = res.next();
				reactions.add(b.getValue("reaction").stringValue());
				enablers.add(b.getValue("enabler").stringValue());
			}
			res.close();
			assertTrue("expected 5 split activities, got " + reactions.size(), reactions.size() == 5);
			assertTrue("expected 5 distinct enablers, got " + enablers.size(), enablers.size() == 5);

			TupleQueryResult sib = blaze.runSparqlQuery(
				"prefix obo: <http://purl.obolibrary.org/obo/> select ?reaction where { "
				+ "?reaction obo:RO_0002333 ?enabler . "
				+ "FILTER(STRSTARTS(STR(?reaction), \"http://model.geneontology.org/R-HSA-1482868_enabled_by\")) }");
			Set<String> sibs = new HashSet<String>();
			while(sib.hasNext()) { sibs.add(sib.next().getValue("reaction").stringValue()); }
			sib.close();
			assertTrue("expected 2 split activities for sibling, got " + sibs.size(), sibs.size() == 2);
		} catch (QueryEvaluationException e) {
			e.printStackTrace();
			fail(e.getMessage());
		}
		System.out.println("Done testing set-enabled reaction split");
	}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd exchange && mvn -Dtest=BioPaxtoGOTest#testSetEnabledReactionSplit test`
Expected: **FAIL** — assertion `original set reaction should be split away, enablers on it = 1` (the fixture is converted by `@BeforeClass`, but no split happens yet, so the original reaction is still a single enabled-by-set activity). First run also downloads `go-plus.owl` and runs the full build; this is slow (several minutes).

- [ ] **Step 3a: Wire Phase 1 into the controller loop** — in `BioPaxtoGO.defineReactionEntity`, inside `for(Controller controller_entity : controller_entities)` (the loop at `:1676`), immediately after the `DEBUG_PROCESSING_CONTROLLER_ENTITY` println (`:1728`) and before `IRI iri = null;` (`:1730`), insert:

```java
						//Reactome EntitySet catalyst: explode into one activity per member (split happens later in splitSetEnabledReactions)
						if(is_catalysis && isExplodableEntitySet(controller_entity)) {
							PhysicalEntity set_controller = (PhysicalEntity) controller_entity;
							List<PhysicalEntity> resolved_members = resolveSetCatalystMembers(set_controller);
							StringBuilder member_ids = new StringBuilder();
							for(PhysicalEntity member : resolved_members) {
								String member_id = getEntityReferenceId(member);
								IRI member_iri = GoCAM.makeGoCamifiedIRI(null, member_id+"_"+entity_id+"_controller");
								OWLNamedIndividual member_e = go_cam.df.getOWLNamedIndividual(member_iri);
								defineReactionEntity(go_cam, member, member_iri, true, model_id, root_pathway_iri, reaction_id, true);
								go_cam.addRefBackedObjectPropertyAssertion(e, GoCAM.enabled_by, member_e, dbids, GoCAM.eco_imported_auto, default_namespace_prefix, null, model_id);
								member_ids.append(member_id).append(",");
							}
							go_cam.set_enabled_reaction_iris.add(e.getIRI());
							System.out.println("SET_ENABLED_REACTION_SPLIT\t"+model_id+"\t"+go_cam.name+"\t"+entity_id+"\t"+resolved_members.size()+"\t"+member_ids.toString());
							continue;
						}
```

All names used here are already in scope at that point: `is_catalysis` (`:1652`), `e`, `entity_id`, `dbids`, `default_namespace_prefix`, `model_id`, `root_pathway_iri`, `reaction_id`, and the statics `GoCAM.enabled_by`, `GoCAM.eco_imported_auto`. `Controller`/`PhysicalEntity`/`List`/`IRI`/`OWLNamedIndividual`/`StringBuilder` are already imported.

- [ ] **Step 3b: Wire Phase 2 into `wrapAndWrite`** — in `BioPaxtoGO.wrapAndWrite`, immediately **before** `go_cam.qrunner = new QRunner(go_cam.go_cam_ont);` (`:551`), insert:

```java
		//split reactions catalyzed by an EntitySet into one activity per member, before the SPARQL rules run
		go_cam.splitSetEnabledReactions(reactome_id);
```

`reactome_id` is the last parameter of `wrapAndWrite` (`:549`).

- [ ] **Step 4: Run test to verify it passes**

Run: `cd exchange && mvn -Dtest=BioPaxtoGOTest#testSetEnabledReactionSplit test`
Expected: **BUILD SUCCESS**, Tests run: 1, Failures: 0. (Watch for `SET_ENABLED_REACTION_SPLIT` log lines during the build confirming detection fired.)

- [ ] **Step 5: Run the unit tests and the targeted integration test together**

Run: `cd exchange && mvn -Dtest=SetEnabledReactionSplitTest,BioPaxtoGOTest#testSetEnabledReactionSplit test`
Expected: all green.

- [ ] **Step 6: Checkpoint** — feature works end-to-end. Do not commit.

---

## Task 5: Regression — full suite

Confirm the change didn't break existing conversions (causal/regulation/provides-input tests run on the same shared build).

**Files:** none (verification only).

- [ ] **Step 1: Run the full BioPaxtoGO test class**

Run: `cd exchange && MAVEN_OPTS="-Xmx8g" mvn -Dtest=BioPaxtoGOTest test`
Expected: **BUILD SUCCESS**, all existing tests still pass (e.g. `testInferProvidesInput`, `testInferRegulatesViaOutputEnables`, `testCausalPathBridging`, `testDrugReactionDeletion`). The new fixture adds a pathway to the shared build; existing assertions reference their own pathways and must be unaffected.

- [ ] **Step 2: Run the full project test suite**

Run: `cd exchange && MAVEN_OPTS="-Xmx8g" mvn test`
Expected: **BUILD SUCCESS** across `BioPaxtoGOTest`, `BioPaxtoGOTestYeastCyc`, `PhysicalEntityOntologyBuilderTest`, `QRunnerPartToComplexIndexTest`, `SetEnabledReactionSplitTest`.

- [ ] **Step 3: Checkpoint** — full suite green. Hand back to the user to review and commit.

---

## Self-Review (performed against the spec)

**Spec coverage:**
- Phase 1 detection/resolution (spec §5.1) → Task 3 + Task 4 Step 3a. ✓
- `cloneIndividualSharingNeighbors` (§5.2) → Task 1. ✓
- `splitSetEnabledReactions` + `set_enabled_reaction_iris` (§5.3/§5.4) → Task 2. ✓
- Insertion before QRunner/`applySparqlRules` (§5.3 call site) → Task 4 Step 3b. ✓
- Decision §3.1 (non-reducible complex kept whole) → `resolveSetCatalystMembers` else-branch (Task 3) + `defineReactionEntity` on the complex builds its `has_part` proteins (Task 4 Step 3a). ✓
- Decision §3.2 (catalysis only) → `is_catalysis &&` guard (Task 4 Step 3a). ✓
- Decision §3.3 (cross-product) → emerges from shared-neighbor cloning + delete-after-clone in Task 2; not separately tested (parallel reactions in the fixture). ✓ (noted)
- Reporting (§5 / report line) → `SET_ENABLED_REACTION_SPLIT` in Task 4 Step 3a. ✓
- Edge cases n≤1 / small-mol-only / nested (§6) → `enablers.size() < 2` guard (Task 2), `setIsSmallMoleculesOnly` guard (Task 3), recursion (Task 3). ✓
- Evidence-deletion constraint (§4) → fresh edge evidence via `cloneAnnotations` (Task 1). ✓
- Testing (§8): unit (Tasks 1–2), resolver (Task 3), end-to-end 5+2 (Task 4), regression (Task 5). ✓

**Placeholder scan:** none — every code/command step is concrete.

**Type consistency:** method names (`cloneIndividualSharingNeighbors`, `splitSetEnabledReactions`, `isExplodableEntitySet`, `resolveSetCatalystMembers`, `addResolvedMember`, `getObjectPropertyEdgeAnnotations`), the field `set_enabled_reaction_iris`, the clone-IRI scheme (`<reaction_id>_enabled_by_<member_id>`), and the `enabled_by`/`RO_0002333` references match across tasks and the SPARQL assertions.

**Open risk to watch during execution:** if `testSetEnabledReactionSplit` finds the original `R-HSA-1482825` node *still present* after wiring, confirm `wrapAndWrite`'s `reactome_id` arg is the model id used for the reaction IRIs; if a drug-removal interaction appears, move the `splitSetEnabledReactions` call after drug removal (`:558`) and rebuild `go_cam.qrunner` before `:562` (spec §5.3 fallback).
