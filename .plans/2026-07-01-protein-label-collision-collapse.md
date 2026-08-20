# Protein Label Collision Collapse Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** When two BioPAX complex components share one UniProt `ProteinReference` and collapse to the same GO-CAM protein individual, emit exactly one `rdfs:label` (the ProteinReference-derived name) instead of two.

**Architecture:** Physical-entity labels are all added at a single chokepoint in `BioPaxtoGO.defineReactionEntity` (`getBioPaxName(entity)` → `GoCAM.addLabel`). For `Protein` entities, detect at that chokepoint whether a *different* label already exists on the individual; if so (collision), remove the existing labels and set the canonical label derived from the ProteinReference name that embeds the UniProt id (`"UniProt:P35354 PTGS2"` → `"PTGS2"`). Non-colliding single labels keep the displayName so modified-form detail (`"Ac-PTGS2"`) is preserved.

**Tech Stack:** Java 8, Maven, OWL API, paxtools (BioPAX), JUnit 4, Blazegraph/openrdf SPARQL (existing test harness).

## Global Constraints

- Build/test run from the `exchange/` directory; the full pipeline test needs ~8GB heap (per CLAUDE.md).
- **Do NOT run `git commit` or `git add`** (user's global instruction). Each task ends with a **Checkpoint** (build + tests green); the user handles commits.
- REACTO entity strategy. UniProt class IRI base is `http://identifiers.org/uniprot/` (`GoCAM.uniprot_iri`).
- `rdfs:label` cardinality on any individual must be 0 or 1.
- All new code lives in package `org.geneontology.gocam.exchange`.
- Fix scope: `Protein` individuals backed by a UniProt `ProteinReference` only. Non-protein multi-label individuals are out of scope. If the canonical label cannot be derived, fall back to the displayName.

---

## REVISION 2026-07-01 (during execution) — READ THIS FIRST

The original premise (a 2-label collision) does **not** reproduce on the current
branch: the flatten dedup `collectFlattenedComplexLeaves` (HEAD `5304f5e`) already
collapses the two shared-UniProt components to a single `P35354` individual with
one label. The real defect is that the surviving label is the **arbitrary** dedup
winner's displayName (`"Ac-PTGS2"`), not the deterministic canonical name.

**Task 1 stands as-is (done, reviewed clean).** Task 2 (`removeLabels`) and the
original Task 3 (collision handler) are **superseded** — replaced by **Task R**
below. Task R reverts `removeLabels` + its test and implements a simple
"prefer the canonical ProteinReference label for UniProt proteins" branch.

### Task R: Canonical UniProt protein label (supersedes Tasks 2 & 3)

**Files:**
- Modify: `exchange/src/main/java/org/geneontology/gocam/exchange/GoCAM.java` — REVERT the `removeLabels` method added in Task 2 (delete it).
- Modify: `exchange/src/test/java/org/geneontology/gocam/exchange/ProteinLabelTest.java` — REVERT Task 2's `testRemoveLabelsClearsAllRdfsLabels` method and the three imports it added (`java.util.Collections`, `org.semanticweb.owlapi.model.IRI`, `org.semanticweb.owlapi.model.OWLNamedIndividual`). Keep Task 1's method and imports.
- Modify: `exchange/src/main/java/org/geneontology/gocam/exchange/BioPaxtoGO.java` — the label block at ~L1277 (see below). If Task 3 already added `addProteinLabelHandlingCollision`, delete that method too.
- Modify: `exchange/src/test/java/org/geneontology/gocam/exchange/BioPaxtoGOTest.java` — strengthen the integration test to assert the canonical label value.

**Interfaces:**
- Consumes: `static String BioPaxtoGO.getProteinLabelFromReference(Protein)` (Task 1) → `"PTGS2"` or `null`.
- No new production methods. `GoCAM.removeLabels` is removed.

- [ ] **Step R1: Revert Task 2's `removeLabels` from `GoCAM.java`**

Delete the entire method added after `addLabel`:

```java
	public void removeLabels(OWLEntity entity) {
		Set<OWLAxiom> to_remove = new HashSet<OWLAxiom>();
		for (OWLAnnotationAssertionAxiom ax :
				EntitySearcher.getAnnotationAssertionAxioms(entity.getIRI(), go_cam_ont)) {
			if (ax.getProperty().equals(rdfs_label)) {
				to_remove.add(ax);
			}
		}
		ontman.removeAxioms(go_cam_ont, to_remove);
	}
```

- [ ] **Step R2: Revert Task 2's unit test from `ProteinLabelTest.java`**

Delete the `testRemoveLabelsClearsAllRdfsLabels` method and remove the three imports it introduced (`java.util.Collections`, `org.semanticweb.owlapi.model.IRI`, `org.semanticweb.owlapi.model.OWLNamedIndividual`). Leave `testGetProteinLabelFromReferenceStripsUniprotPrefix` and its imports intact.

- [ ] **Step R3: Strengthen the integration test to assert the canonical label**

In `BioPaxtoGOTest.java`, replace the body of `testProteinLabelCollisionCollapse` (added in the original Task 3; if it is not present, add the whole method plus `import java.util.HashMap;`) so it asserts every `UniProt:P35354` individual in the model graph has exactly one `rdfs:label` and that label is `"PTGS2"`:

```java
	// R-HSA-2314687 (PTGS2 dimer) reduces to a single UniProt:P35354 protein.
	// That individual is typed as the canonical UniProt class, so its label must be
	// the canonical "PTGS2" (from the ProteinReference), not an arbitrary modified-form
	// displayName like "Ac-PTGS2", and there must be exactly one label.
	@Test
	public final void testProteinLabelCollisionCollapse() {
		System.out.println("test canonical UniProt protein label from ProteinReference");
		String model_graph = "<http://model.geneontology.org/R-HSA-9018679>";
		String q =
				"SELECT ?ind ?lbl \n" +
				"WHERE { \n" +
				"  GRAPH graph_id { \n" +
				"    ?ind a <http://identifiers.org/uniprot/P35354> . \n" +
				"    ?ind <http://www.w3.org/2000/01/rdf-schema#label> ?lbl . \n" +
				"  } \n" +
				"} \n";
		Map<String, Set<String>> labels_by_ind = new HashMap<String, Set<String>>();
		TupleQueryResult result = null;
		try {
			result = blaze.runSparqlQuery(q.replace("graph_id", model_graph));
			while (result.hasNext()) {
				BindingSet bs = result.next();
				String ind = bs.getValue("ind").stringValue();
				String lbl = bs.getValue("lbl").stringValue();
				if (!labels_by_ind.containsKey(ind)) {
					labels_by_ind.put(ind, new HashSet<String>());
				}
				labels_by_ind.get(ind).add(lbl);
			}
		} catch (QueryEvaluationException e) {
			e.printStackTrace();
		} finally {
			try {
				if (result != null) {
					result.close();
				}
			} catch (QueryEvaluationException e) {
				e.printStackTrace();
			}
		}
		assertTrue("expected a UniProt:P35354 individual in R-HSA-9018679",
				labels_by_ind.size() > 0);
		for (Map.Entry<String, Set<String>> en : labels_by_ind.entrySet()) {
			assertEquals("individual " + en.getKey()
					+ " must have exactly one rdfs:label", 1, en.getValue().size());
			assertEquals("individual " + en.getKey()
					+ " must be labeled canonically from the ProteinReference",
					"PTGS2", en.getValue().iterator().next());
		}
	}
```

- [ ] **Step R4: Run the test to verify it FAILS (RED) for the right reason**

Run: `cd exchange && MAVEN_OPTS="-Xmx8g" mvn -Dtest=BioPaxtoGOTest#testProteinLabelCollisionCollapse test`
Expected: FAIL on the `assertEquals("PTGS2", ...)` — the individual is currently labeled `"Ac-PTGS2"`. (If it instead fails on `size() > 0` or `size()==1`, STOP and report — the graph/type IRI assumption is off.)

- [ ] **Step R5: Apply the canonical-label branch in `BioPaxtoGO.java`**

Replace the label block at ~L1277 (whatever form Task 3 left it in — the original `getBioPaxName`/`addLabel` block, or the `addProteinLabelHandlingCollision` routing) with:

```java
		String entity_name = getBioPaxName(entity);
		if(entity instanceof Protein) {
			// A protein individual is typed as its canonical UniProt class; prefer the
			// ProteinReference name embedding the UniProt id (e.g. "PTGS2") so the label is
			// deterministic and matches the type, rather than an arbitrary modified-form
			// displayName (e.g. "Ac-PTGS2") that depends on complex-component iteration order.
			String canonical = getProteinLabelFromReference((Protein) entity);
			if(canonical != null) {
				entity_name = canonical;
			}
		}
		if(entity_name!=null) {
			go_cam.addLabel(e, entity_name);
		}
```

If Task 3 added `addProteinLabelHandlingCollision`, delete that method (it is no longer referenced).

- [ ] **Step R6: Run the test to verify it PASSES (GREEN)**

Run: `cd exchange && MAVEN_OPTS="-Xmx8g" mvn -Dtest=BioPaxtoGOTest#testProteinLabelCollisionCollapse test`
Expected: PASS — the P35354 individual is now labeled `"PTGS2"`.

- [ ] **Step R7: Full-class regression + unit tests**

Run: `cd exchange && MAVEN_OPTS="-Xmx8g" mvn -Dtest=ProteinLabelTest test` then `cd exchange && MAVEN_OPTS="-Xmx8g" mvn -Dtest=BioPaxtoGOTest test`
Expected: PASS. **If any existing test now fails because a protein label changed from a displayName to its canonical UniProt name, do NOT edit that test to pass — STOP and report the failing test(s) and the label change; the controller decides whether the new label is correct.**

- [ ] **Step R8: Checkpoint** — leave git to the user (no commit).

---

### Task 1: `getProteinLabelFromReference` helper + test fixture

Derives the canonical protein label from the ProteinReference name that embeds the UniProt id. Also makes the existing `extractUniprotId` static (it is already a pure function of its argument) so the new helper and its test can call it without an instance.

**Files:**
- Modify: `exchange/src/main/java/org/geneontology/gocam/exchange/BioPaxtoGO.java` (make `extractUniprotId` static at ~L2352; add `getProteinLabelFromReference` next to it)
- Create (fixture): `exchange/src/test/resources/biopax/R-HSA-9018679_level3.owl` (copy of `exchange/R-HSA-9018679_level3.owl`)
- Create (test): `exchange/src/test/java/org/geneontology/gocam/exchange/ProteinLabelTest.java`

**Interfaces:**
- Produces: `static String BioPaxtoGO.getProteinLabelFromReference(Protein protein)` — returns e.g. `"PTGS2"`, or `null` when no UniProt id / no embedding name.
- Produces: `private static String BioPaxtoGO.extractUniprotId(Protein protein)` (visibility unchanged, now static).

- [ ] **Step 1: Copy the BioPAX fixture into test resources**

```bash
cp exchange/R-HSA-9018679_level3.owl exchange/src/test/resources/biopax/R-HSA-9018679_level3.owl
```

- [ ] **Step 2: Write the failing test**

Create `exchange/src/test/java/org/geneontology/gocam/exchange/ProteinLabelTest.java`:

```java
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
```

- [ ] **Step 3: Run test to verify it fails**

Run: `cd exchange && mvn -Dtest=ProteinLabelTest#testGetProteinLabelFromReferenceStripsUniprotPrefix test`
Expected: FAIL — compilation error `cannot find symbol: method getProteinLabelFromReference(...)`.

- [ ] **Step 4: Make `extractUniprotId` static**

In `BioPaxtoGO.java` (~L2352), change the signature:

```java
	// Helper method to extract UniProt ID from a Protein entity
	private static String extractUniprotId(Protein protein) {
	    if (protein.getEntityReference() != null) {
	        for (Xref xref : protein.getEntityReference().getXref()) {
	            if ("UniProt".equals(xref.getDb()) || "uniprot".equals(xref.getDb())) {
	                return xref.getId();
	            }
	        }
	    }
	    return null;
	}
```

- [ ] **Step 5: Add `getProteinLabelFromReference`**

In `BioPaxtoGO.java`, immediately after `extractUniprotId`, add:

```java
	/**
	 * Canonical protein label for a UniProt-backed Protein, taken from the
	 * ProteinReference name that embeds the UniProt id (e.g. "UniProt:P35354 PTGS2"
	 * -> "PTGS2"). Two complex components that share one ProteinReference therefore
	 * yield the same label, so a collision collapses to a single value. Returns null
	 * when there is no UniProt id or no embedding name, in which case callers fall
	 * back to the displayName.
	 */
	static String getProteinLabelFromReference(Protein protein) {
		String uniprot = extractUniprotId(protein);
		if (uniprot == null || protein.getEntityReference() == null) {
			return null;
		}
		for (String name : protein.getEntityReference().getName()) {
			int idx = name.indexOf(uniprot);
			if (idx >= 0) {
				String tail = name.substring(idx + uniprot.length()).trim();
				if (!tail.isEmpty()) {
					return tail;
				}
			}
		}
		return null;
	}
```

- [ ] **Step 6: Run test to verify it passes**

Run: `cd exchange && mvn -Dtest=ProteinLabelTest#testGetProteinLabelFromReferenceStripsUniprotPrefix test`
Expected: PASS.

- [ ] **Step 7: Checkpoint**

Confirm the test passes. Leave git to the user (do not commit).

---

### Task 2: `GoCAM.removeLabels` helper

Removes all `rdfs:label` annotation-assertion axioms from an entity so the collision branch can replace two labels with one.

**Files:**
- Modify: `exchange/src/main/java/org/geneontology/gocam/exchange/GoCAM.java` (add `removeLabels` after `addLabel` at ~L602)
- Modify (test): `exchange/src/test/java/org/geneontology/gocam/exchange/ProteinLabelTest.java`

**Interfaces:**
- Consumes: existing `GoCAM.addLabel(OWLEntity, String)`, `GoCAM.getLabels(OWLEntity)` → `Set<String>`.
- Produces: `public void GoCAM.removeLabels(OWLEntity entity)`.

- [ ] **Step 1: Write the failing test**

Add to `ProteinLabelTest.java` (and add the imports shown):

```java
// add to the import block:
import java.util.Collections;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLNamedIndividual;
```

```java
	@Test
	public void testRemoveLabelsClearsAllRdfsLabels() throws Exception {
		GoCAM go_cam = new GoCAM(); // lightweight: empty ontology, no go-lego import
		OWLNamedIndividual ind =
				go_cam.df.getOWLNamedIndividual(IRI.create("http://example.org/ind1"));
		go_cam.addLabel(ind, "Ac-PTGS2");
		go_cam.addLabel(ind, "PTGS2");
		assertEquals(2, go_cam.getLabels(ind).size());

		go_cam.removeLabels(ind);
		assertTrue("all labels should be removed", go_cam.getLabels(ind).isEmpty());

		go_cam.addLabel(ind, "PTGS2");
		assertEquals(Collections.singleton("PTGS2"), go_cam.getLabels(ind));
	}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd exchange && mvn -Dtest=ProteinLabelTest#testRemoveLabelsClearsAllRdfsLabels test`
Expected: FAIL — compilation error `cannot find symbol: method removeLabels(...)`.

- [ ] **Step 3: Implement `removeLabels`**

In `GoCAM.java`, directly after the `addLabel` method (ends ~L602), add:

```java
	public void removeLabels(OWLEntity entity) {
		Set<OWLAxiom> to_remove = new HashSet<OWLAxiom>();
		for (OWLAnnotationAssertionAxiom ax :
				EntitySearcher.getAnnotationAssertionAxioms(entity.getIRI(), go_cam_ont)) {
			if (ax.getProperty().equals(rdfs_label)) {
				to_remove.add(ax);
			}
		}
		ontman.removeAxioms(go_cam_ont, to_remove);
	}
```

Note: `EntitySearcher`, `OWLAnnotationAssertionAxiom`, `OWLAxiom`, `OWLEntity`, `Set`, `HashSet`, and `rdfs_label` are all already imported/defined in `GoCAM.java` (used by `deleteOwlEntityAndAllReferencesToIt` and `addLabel`). No new imports needed.

- [ ] **Step 4: Run test to verify it passes**

Run: `cd exchange && mvn -Dtest=ProteinLabelTest#testRemoveLabelsClearsAllRdfsLabels test`
Expected: PASS.

- [ ] **Step 5: Checkpoint**

Confirm both `ProteinLabelTest` methods pass:
`cd exchange && mvn -Dtest=ProteinLabelTest test` → Expected: PASS. Leave git to the user.

---

### Task 3: Collision-aware protein labeling in `defineReactionEntity` + integration test

Wires the helpers into the label chokepoint: single label → keep displayName; a differing label already present → collapse to the ProteinReference canonical. Adds an end-to-end pipeline assertion that the reduced `UniProt:P35354` individual carries at most one `rdfs:label`.

**Files:**
- Modify: `exchange/src/main/java/org/geneontology/gocam/exchange/BioPaxtoGO.java` (label branch at ~L1277–1280; new private method `addProteinLabelHandlingCollision`)
- Modify (test): `exchange/src/test/java/org/geneontology/gocam/exchange/BioPaxtoGOTest.java` (add `import java.util.HashMap;` and one `@Test`)

**Interfaces:**
- Consumes: `BioPaxtoGO.getProteinLabelFromReference(Protein)` (Task 1), `GoCAM.removeLabels(OWLEntity)` (Task 2), existing `GoCAM.getLabels`, `GoCAM.addLabel`, `getEntityReferenceId`.
- Produces: `private void BioPaxtoGO.addProteinLabelHandlingCollision(GoCAM go_cam, OWLNamedIndividual e, Protein protein, String displayName)`.
- The fixture `src/test/resources/biopax/R-HSA-9018679_level3.owl` (added in Task 1) is converted by `BioPaxtoGOTest`'s `@BeforeClass`; the model graph is `<http://model.geneontology.org/R-HSA-9018679>`.

- [ ] **Step 1: Write the failing integration test**

In `BioPaxtoGOTest.java`, add to the import block:

```java
import java.util.HashMap;
```

Add this test method (alongside the other `@Test` methods, e.g. after `testDiseaseReactionDeletion`):

```java
	// R-HSA-2314687 (PTGS2 dimer) reduces to a single UniProt:P35354 protein.
	// Its two components "Ac-PTGS2" and "PTGS2" share ProteinReference1, so the
	// P35354 individual must carry at most one rdfs:label (cardinality 0 or 1).
	@Test
	public final void testProteinLabelCollisionCollapse() {
		System.out.println("test protein label collision collapse for shared ProteinReference");
		String model_graph = "<http://model.geneontology.org/R-HSA-9018679>";
		String q =
				"SELECT ?ind ?lbl \n" +
				"WHERE { \n" +
				"  GRAPH graph_id { \n" +
				"    ?ind a <http://identifiers.org/uniprot/P35354> . \n" +
				"    ?ind <http://www.w3.org/2000/01/rdf-schema#label> ?lbl . \n" +
				"  } \n" +
				"} \n";
		Map<String, Set<String>> labels_by_ind = new HashMap<String, Set<String>>();
		TupleQueryResult result = null;
		try {
			result = blaze.runSparqlQuery(q.replace("graph_id", model_graph));
			while (result.hasNext()) {
				BindingSet bs = result.next();
				String ind = bs.getValue("ind").stringValue();
				String lbl = bs.getValue("lbl").stringValue();
				if (!labels_by_ind.containsKey(ind)) {
					labels_by_ind.put(ind, new HashSet<String>());
				}
				labels_by_ind.get(ind).add(lbl);
			}
		} catch (QueryEvaluationException e) {
			e.printStackTrace();
		} finally {
			try {
				if (result != null) {
					result.close();
				}
			} catch (QueryEvaluationException e) {
				e.printStackTrace();
			}
		}
		assertTrue("expected a UniProt:P35354 individual in R-HSA-9018679",
				labels_by_ind.size() > 0);
		for (Map.Entry<String, Set<String>> en : labels_by_ind.entrySet()) {
			assertTrue("individual " + en.getKey()
					+ " must have <=1 rdfs:label but has " + en.getValue(),
					en.getValue().size() <= 1);
		}
	}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd exchange && MAVEN_OPTS="-Xmx8g" mvn -Dtest=BioPaxtoGOTest#testProteinLabelCollisionCollapse test`
Expected: FAIL — the P35354 individual has two labels (`"Ac-PTGS2"`, `"PTGS2"`), so the `size() <= 1` assertion fails. (The `@BeforeClass` converts all `src/test/resources/biopax/` models first; this is slow and may download go-plus.owl on first run.)

- [ ] **Step 3: Add the collision-handling method**

In `BioPaxtoGO.java`, add this private method (e.g. directly after `getProteinLabelFromReference` from Task 1):

```java
	/**
	 * Add an rdfs:label to a protein individual, collapsing to the canonical
	 * ProteinReference-derived label only when a DIFFERENT label is already
	 * present. Two complex components that share one UniProt ProteinReference
	 * resolve to the same individual; without this, each would add its own
	 * displayName, violating rdfs:label cardinality (0 or 1). Single,
	 * non-colliding labels keep the displayName so modified-form detail
	 * (e.g. "Ac-PTGS2") is preserved.
	 */
	private void addProteinLabelHandlingCollision(GoCAM go_cam, OWLNamedIndividual e,
			Protein protein, String displayName) {
		boolean collision = false;
		for (String existing : go_cam.getLabels(e)) {
			if (!existing.equals(displayName)) {
				collision = true;
				break;
			}
		}
		if (!collision) {
			// 0 existing labels, or the only label already equals displayName.
			go_cam.addLabel(e, displayName);
			return;
		}
		String canonical = getProteinLabelFromReference(protein);
		if (canonical == null) {
			canonical = displayName;
			System.out.println("PROTEIN_LABEL_COLLISION_NO_REFERENCE\t"
					+ getEntityReferenceId(protein) + "\texisting=" + go_cam.getLabels(e)
					+ "\tdisplayName=" + displayName);
		}
		go_cam.removeLabels(e);
		go_cam.addLabel(e, canonical);
	}
```

- [ ] **Step 4: Route protein labels through the collision handler**

In `BioPaxtoGO.java`, replace the existing label block at ~L1277–1280:

```java
		String entity_name = getBioPaxName(entity);
		if(entity_name!=null) {
			go_cam.addLabel(e, entity_name);
		}
```

with:

```java
		String entity_name = getBioPaxName(entity);
		if(entity_name!=null) {
			if(entity instanceof Protein) {
				addProteinLabelHandlingCollision(go_cam, e, (Protein) entity, entity_name);
			} else {
				go_cam.addLabel(e, entity_name);
			}
		}
```

- [ ] **Step 5: Run test to verify it passes**

Run: `cd exchange && MAVEN_OPTS="-Xmx8g" mvn -Dtest=BioPaxtoGOTest#testProteinLabelCollisionCollapse test`
Expected: PASS — the P35354 individual now has exactly one label (`"PTGS2"`).

- [ ] **Step 6: Run the fast unit tests and the full test class to check for regressions**

Run: `cd exchange && MAVEN_OPTS="-Xmx8g" mvn -Dtest=ProteinLabelTest,BioPaxtoGOTest test`
Expected: PASS (no regressions in existing pipeline assertions).

- [ ] **Step 7: Checkpoint**

Confirm all tests pass. Leave git to the user (do not commit).

---

## Self-Review

**Spec coverage:**
- `getProteinLabelFromReference` (spec §"New helper: getProteinLabelFromReference") → Task 1. ✓
- `GoCAM.removeLabels` (spec §"New helper: removeLabels") → Task 2. ✓
- Collision-aware label branch, Protein-only, displayName fallback + logging (spec §"Replacement label logic", §"Scope / boundaries") → Task 3. ✓
- Determinism / convergence (spec §"Determinism and convergence") → Task 3 code (recompute canonical on every differing add; identical labels idempotent). ✓
- Tests: unit test for extraction + integration cardinality assertion (spec §"Testing") → Tasks 1 & 3. The `removeLabels` unit test (Task 2) additionally covers the clear-then-reset behavior. ✓

**Placeholder scan:** No TBD/TODO/"handle edge cases"; every code step shows complete code. ✓

**Type consistency:** `getProteinLabelFromReference(Protein)→String` (static, Task 1) called from Task 3; `extractUniprotId(Protein)→String` (static, Task 1); `removeLabels(OWLEntity)` (Task 2) called from Task 3; `getLabels(OWLEntity)→Set<String>` and `addLabel(OWLEntity,String)` (existing) used consistently; `addProteinLabelHandlingCollision(GoCAM,OWLNamedIndividual,Protein,String)` defined and called in Task 3. ✓
