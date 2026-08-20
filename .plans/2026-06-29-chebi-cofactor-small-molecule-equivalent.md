# Treat bare PhysicalEntity with ChEBI xref as small-molecule-equivalent — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** When reducing a catalytic Complex/EntitySet to its single enzyme protein, strip out bare `bp:PhysicalEntity` cofactors that carry a ChEBI xref (e.g. the iron-sulfur cluster 2Fe-2S, R-ALL-164296) the same way `bp:SmallMolecule` components are already stripped — so Complex R-HSA-189402 reduces to FECH and reaction R-HSA-189465 becomes `enabled_by` the single protein instead of the complex.

**Architecture:** Reactome sometimes models a chemical cofactor as a generic `bp:PhysicalEntity` carrying only a ChEBI xref rather than as a `bp:SmallMolecule`. The enabler-resolution code currently filters strictly on `instanceof SmallMolecule`, so these cofactors survive and defeat single-protein reduction. We introduce one shared predicate, `isSmallMoleculeEquivalent(PhysicalEntity)`, that returns true for a `SmallMolecule` OR a leaf (member-less) bare `PhysicalEntity` with a ChEBI xref, and use it at all three sites that decide "what is a non-catalytic chemical when resolving an enabler": `getComplexActiveUnitRecursive`, `resolveSetCatalystMembers`, and `setIsSmallMoleculesOnly`. Reaction-participant (has_input / has_output) representation is intentionally left unchanged.

**Tech Stack:** Java 8, paxtools-core (BioPAX level3 model), OWL API, JUnit 4, Blazegraph + SPARQL (test assertions), Maven.

## Global Constraints

- **No git operations.** Per the repo's CLAUDE.md / user instructions: do NOT `git add` or `git commit`. The user commits manually. Each task ends with a build/test verification step, not a commit.
- **Entity strategy:** the behavior being changed is exercised under `EntityStrategy.REACTO` (the default for `fullBuild()` in the test harness).
- **Tests are heavy:** `BioPaxtoGOTest`'s `@BeforeClass` runs the full conversion of every BioPAX file in `src/test/resources/biopax/` (plus YeastCyc) into Blazegraph and downloads `go-plus.owl` on first run. Even a single `@Test` triggers the whole build. Allow ~8GB heap and several minutes. All commands run from the `exchange/` directory.
- **Do not restructure** `BioPaxtoGO.java`; it is a large existing file — add the helper methods next to the existing `complexHasProtein` / `getComplexActiveUnitRecursive` helpers and make minimal one-line edits at the call sites.

---

## File Structure

- `exchange/src/main/java/org/geneontology/gocam/exchange/BioPaxtoGO.java` — add two private helper methods (`isSmallMoleculeEquivalent`, `hasChebiXref`) and change the small-molecule filter at three existing sites to call `isSmallMoleculeEquivalent`. All required imports (`PhysicalEntity`, `SmallMolecule`, `Xref`) already exist.
- `exchange/src/test/java/org/geneontology/gocam/exchange/BioPaxtoGOTest.java` — add one SPARQL regression test, `testComplexCofactorReducedToSingleProtein`, alongside the existing R-HSA-189451 regression tests. Uses the existing private `countSolutions(String)` helper.
- `exchange/src/test/resources/biopax/R-HSA-189451_level3.owl` — existing test fixture; no change. It already contains Complex `R-HSA-189402` → reaction `R-HSA-189465` (Catalysis10, ACTIVATION, no `activeUnit:` annotation) with the bare-PhysicalEntity 2Fe-2S cofactor.

---

### Task 1: Add `isSmallMoleculeEquivalent` helper and fix complex single-protein reduction

**Files:**
- Modify: `exchange/src/main/java/org/geneontology/gocam/exchange/BioPaxtoGO.java` (add helpers near `getComplexActiveUnitRecursive`, line ~2183; edit the filter at `getComplexActiveUnitRecursive` line ~2188)
- Test: `exchange/src/test/java/org/geneontology/gocam/exchange/BioPaxtoGOTest.java` (add `testComplexCofactorReducedToSingleProtein`)

**Interfaces:**
- Consumes: existing paxtools APIs `PhysicalEntity.getModelInterface()`, `PhysicalEntity.getMemberPhysicalEntity()`, `PhysicalEntity.getXref()`, `Xref.getDb()`; existing test helper `private int countSolutions(String sparql)`.
- Produces: `private boolean isSmallMoleculeEquivalent(PhysicalEntity entity)` and `private boolean hasChebiXref(PhysicalEntity entity)` — relied on by Task 2.

- [ ] **Step 1: Write the failing regression test**

Add this method to `BioPaxtoGOTest.java`, immediately after `testComplexRegulatorLeavesNoOrphanComponents()` (ends at line ~1275):

```java
	/**
	 * Regression test for the FECH cofactor case. Complex R-HSA-189402
	 * ("2x(FECH:2Fe-2S cluster)") catalyzes reaction R-HSA-189465 ("FECH binds Fe2+
	 * to PRIN9 to form heme") via Catalysis10 (ACTIVATION, no activeUnit annotation).
	 * The complex's only non-protein component, 2Fe-2S (R-ALL-164296), is modeled as a
	 * bare bp:PhysicalEntity carrying a ChEBI xref rather than a bp:SmallMolecule, so it
	 * was not stripped and the complex failed to reduce to its single protein FECH
	 * (UniProt P22830). After the fix the reaction must be enabled_by FECH, not the complex.
	 */
	@Test
	public final void testComplexCofactorReducedToSingleProtein() {
		System.out.println("Testing that a complex with a bare-PhysicalEntity ChEBI cofactor reduces to its single protein");
		String graph = "<http://model.geneontology.org/R-HSA-189451>";

		// Precondition: the catalyzed reaction is present (guards against a wrong graph
		// IRI making the assertions below pass vacuously).
		int reactionTriples = countSolutions(
			"select ?p ?o where { GRAPH " + graph + " { "
			+ "<http://model.geneontology.org/R-HSA-189465> ?p ?o . } }");
		assertTrue("precondition: R-HSA-189465 should be present in " + graph
			+ " (got " + reactionTriples + " triples)", reactionTriples > 0);

		// Specific: the reaction must be enabled_by the FECH protein (UniProt P22830).
		int fechEnabler = countSolutions(
			"prefix obo: <http://purl.obolibrary.org/obo/> "
			+ "prefix rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#> "
			+ "select ?enabler where { GRAPH " + graph + " { "
			+ "<http://model.geneontology.org/R-HSA-189465> obo:RO_0002333 ?enabler . "
			+ "?enabler rdf:type <http://identifiers.org/uniprot/P22830> . } }");
		assertTrue("R-HSA-189465 should be enabled_by FECH (UniProt P22830) (got " + fechEnabler + ")",
			fechEnabler > 0);

		// Regression: the reaction must NOT be enabled_by the unreduced complex
		// (REACTO class for R-HSA-189402).
		int complexEnabler = countSolutions(
			"prefix obo: <http://purl.obolibrary.org/obo/> "
			+ "prefix rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#> "
			+ "select ?enabler where { GRAPH " + graph + " { "
			+ "<http://model.geneontology.org/R-HSA-189465> obo:RO_0002333 ?enabler . "
			+ "?enabler rdf:type <http://purl.obolibrary.org/obo/go/extensions/reacto.owl#REACTO_R-HSA-189402> . } }");
		assertEquals("R-HSA-189465 must not be enabled_by the unreduced complex R-HSA-189402",
			0, complexEnabler);
	}
```

- [ ] **Step 2: Run the test to verify it fails**

Run (from `exchange/`):
```bash
cd exchange && mvn -Dtest=BioPaxtoGOTest#testComplexCofactorReducedToSingleProtein test
```
Expected: FAIL. The `fechEnabler > 0` assertion fails (got 0) because the enabler is still the complex; and/or the `complexEnabler == 0` assertion fails (got 1) because the unreduced complex R-HSA-189402 is the enabler.

- [ ] **Step 3: Add the helper methods**

In `BioPaxtoGO.java`, insert these two methods immediately before `getComplexActiveUnitRecursive` (currently at line ~2183, just after the `ComplexActiveUnitResult` / `ReactionGoTermResult` static classes):

```java
	/**
	 * True when an entity should be treated as a small molecule for the purpose of
	 * finding the catalytic protein in a Complex or EntitySet. Reactome models some
	 * chemical cofactors as a bare bp:PhysicalEntity carrying only a ChEBI xref instead
	 * of a bp:SmallMolecule (e.g. the iron-sulfur cluster 2Fe-2S, R-ALL-164296). Those
	 * leaf cofactors must be stripped just like SmallMolecule components, otherwise a
	 * complex like 2x(FECH:2Fe-2S cluster) cannot reduce to its single enzyme protein.
	 */
	private boolean isSmallMoleculeEquivalent(PhysicalEntity entity) {
		if (entity instanceof SmallMolecule) {
			return true;
		}
		// Only a bare, leaf bp:PhysicalEntity qualifies - not a Protein/Complex/Dna/Rna,
		// and not a nested EntitySet (which has member physical entities and must recurse).
		if (!entity.getModelInterface().equals(PhysicalEntity.class)) {
			return false;
		}
		if (!entity.getMemberPhysicalEntity().isEmpty()) {
			return false;
		}
		return hasChebiXref(entity);
	}

	private boolean hasChebiXref(PhysicalEntity entity) {
		for (Xref xref : entity.getXref()) {
			if (xref.getDb() != null && xref.getDb().equalsIgnoreCase("ChEBI")) {
				return true;
			}
		}
		return false;
	}
```

- [ ] **Step 4: Use the helper in `getComplexActiveUnitRecursive`**

In `BioPaxtoGO.java`, in `getComplexActiveUnitRecursive` (line ~2187-2191), change the component filter from a `SmallMolecule` type check to the helper.

Replace:
```java
	    for(PhysicalEntity complex_component : (controlled_by_complex).getComponent()) {
	        if (complex_component instanceof SmallMolecule) {
	            // Don't consider small molecules in finding active sites in complexes
	            continue;
	        }
	        non_small_mol_components.add(complex_component);
	    }
```
With:
```java
	    for(PhysicalEntity complex_component : (controlled_by_complex).getComponent()) {
	        if (isSmallMoleculeEquivalent(complex_component)) {
	            // Don't consider small molecules (or bare ChEBI cofactors) in finding active sites in complexes
	            continue;
	        }
	        non_small_mol_components.add(complex_component);
	    }
```

- [ ] **Step 5: Run the test to verify it passes**

Run (from `exchange/`):
```bash
cd exchange && mvn -Dtest=BioPaxtoGOTest#testComplexCofactorReducedToSingleProtein test
```
Expected: PASS. After stripping the 2Fe-2S cofactor, `non_small_mol_components` = {FECH} (size 1, a Protein), so the complex reduces to FECH; R-HSA-189465 is `enabled_by` an individual typed `http://identifiers.org/uniprot/P22830`, and is no longer enabled_by the REACTO class for R-HSA-189402.

- [ ] **Step 6: Verify no regression in the surrounding controller/enabler tests**

Run the existing R-HSA-189451 and active-site tests to confirm the filter change did not disturb other enabler resolution:
```bash
cd exchange && mvn -Dtest=BioPaxtoGOTest#testReusedSmallMoleculeInputIsTyped+testComplexRegulatorLeavesNoOrphanComponents+testActiveSiteInController+testSetEnabledReactionSplit test
```
Expected: PASS for all four. (Do NOT commit — per Global Constraints.)

---

### Task 2: Apply the helper to EntitySet catalyst resolution (`resolveSetCatalystMembers`, `setIsSmallMoleculesOnly`)

This is the broad scope chosen for this change: use the same "bare ChEBI cofactor = small molecule" rule everywhere an enabler is resolved, so an EntitySet catalyst with a bare-PhysicalEntity cofactor member behaves like one with a SmallMolecule member.

**Coverage note:** the test BioPAX corpus has no EntitySet catalyst that contains a bare-PhysicalEntity ChEBI cofactor, so there is no failing-test-first driver for this task (unlike Task 1). The change is a mechanical, semantics-preserving extension of the same predicate; its gate is that the existing EntitySet tests stay green and the project compiles. Adding a synthetic fixture is out of scope here.

**Files:**
- Modify: `exchange/src/main/java/org/geneontology/gocam/exchange/BioPaxtoGO.java` (`setIsSmallMoleculesOnly` line ~1122; `resolveSetCatalystMembers` line ~1176)

**Interfaces:**
- Consumes: `isSmallMoleculeEquivalent(PhysicalEntity)` from Task 1.
- Produces: no new public surface.

- [ ] **Step 1: Use the helper in `setIsSmallMoleculesOnly`**

In `BioPaxtoGO.java` (line ~1119-1129), change the per-member type check to the helper.

Replace:
```java
	private boolean setIsSmallMoleculesOnly(Set<PhysicalEntity> set_members) {
		boolean isSmallMolOnly = false;
		for(PhysicalEntity member : set_members) {
			if (member instanceof SmallMolecule) {
				isSmallMolOnly = true;
			} else {
				return false;
			}
		}
		return isSmallMolOnly;
	}
```
With:
```java
	private boolean setIsSmallMoleculesOnly(Set<PhysicalEntity> set_members) {
		boolean isSmallMolOnly = false;
		for(PhysicalEntity member : set_members) {
			if (isSmallMoleculeEquivalent(member)) {
				isSmallMolOnly = true;
			} else {
				return false;
			}
		}
		return isSmallMolOnly;
	}
```

- [ ] **Step 2: Use the helper in `resolveSetCatalystMembers`**

In `BioPaxtoGO.java` (line ~1175-1178), change the member-skip check to the helper.

Replace:
```java
		for(PhysicalEntity m : set.getMemberPhysicalEntity()) {
			if(m instanceof SmallMolecule) {
				continue;
			}
```
With:
```java
		for(PhysicalEntity m : set.getMemberPhysicalEntity()) {
			if(isSmallMoleculeEquivalent(m)) {
				continue;
			}
```

- [ ] **Step 3: Compile to verify the edits are well-formed**

Run (from `exchange/`):
```bash
cd exchange && mvn -q compile
```
Expected: BUILD SUCCESS (no compilation errors).

- [ ] **Step 4: Run the EntitySet-catalyst regression tests**

Run the tests that exercise set explosion and complex/set regulator handling to confirm no behavior change for existing SmallMolecule-based sets:
```bash
cd exchange && mvn -Dtest=BioPaxtoGOTest#testSetEnabledReactionSplit+testComplexRegulatorLeavesNoOrphanComponents+testInferSmallMoleculeRegulators test
```
Expected: PASS for all three. The predicate only *adds* bare-ChEBI-cofactor leaves to the stripped set; sets built from `SmallMolecule` members (e.g. PLA2G4A:Ca2+) are unaffected.

---

### Task 3: Full-suite verification

**Files:** none (verification only).

- [ ] **Step 1: Run the complete BioPaxtoGO test class**

Run (from `exchange/`):
```bash
cd exchange && mvn -Dtest=BioPaxtoGOTest test
```
Expected: the new `testComplexCofactorReducedToSingleProtein` passes and all previously-passing tests still pass. Note: `@Ignore`-annotated tests stay skipped; that is expected.

- [ ] **Step 2: Report results**

Summarize: confirm `testComplexCofactorReducedToSingleProtein` passed and report the full-suite pass/skip/fail counts from the Maven `Tests run:` line. Do not claim success without the actual Maven output. Do NOT commit — leave the working tree for the user to review and commit.

---

## Self-Review

**Spec coverage:**
- "Treat a bare PhysicalEntity carrying a ChEBI xref as small-molecule-equivalent" → `isSmallMoleculeEquivalent` (Task 1, Step 3).
- Fix the reported FECH/R-HSA-189465 case → `getComplexActiveUnitRecursive` edit (Task 1, Step 4) + regression test (Task 1, Step 1).
- Apply consistently across enabler-resolution sites (user-chosen broad scope) → `setIsSmallMoleculesOnly` + `resolveSetCatalystMembers` edits (Task 2). `has_input`/`has_output` participant sites (lines ~1573/1583/1640) deliberately excluded per design.

**Placeholder scan:** No TBD/TODO/"handle edge cases"/"write tests for the above". Every code step shows the full code; every command shows expected output.

**Type consistency:** Helper name `isSmallMoleculeEquivalent(PhysicalEntity)` and `hasChebiXref(PhysicalEntity)` are used identically in Tasks 1 and 2. `countSolutions(String)` matches the existing test helper signature. Type IRI `http://identifiers.org/uniprot/P22830` matches `GoCAM.uniprot_iri` + UniProt id; REACTO class IRI matches `GoCAM.reacto_base_iri` (`...reacto.owl#REACTO_`) + `R-HSA-189402`. Reaction/graph IRIs (`http://model.geneontology.org/R-HSA-189465`, `...R-HSA-189451`) match the convention used by the existing R-HSA-189451 tests.
