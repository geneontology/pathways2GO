# Eliminate REACTO IDs From Emitted Models — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Guarantee that no REACTO physical-entity IRI (`…/reacto.owl#REACTO_…`) survives in any emitted GO-CAM model, by adding a final ABox cleanup pass that deletes every still-REACTO-typed individual and sweeps the leftover REACTO class declarations.

**Architecture:** Add one `GoCAM` method, `deleteReactoTypedIndividuals()`, that scans `go_cam_ont` for class-assertion axioms whose class IRI starts with `GoCAM.reacto_base_iri`, deletes those individuals (and their referencing axioms) via the existing `deleteOwlEntityAndAllReferencesToIt`, removes the dangling `REACTO_` `owl:Class` declarations, then rebuilds the QRunner. It is called inside `GoCAM.applySparqlRules` after the existing deletes and before `cleanOutUnconnectedNodes`, so all inference that consumes REACTO entities has already run and orphans get swept afterward.

**Tech Stack:** Java 8, OWL API (owlapi-distribution), Paxtools (BioPAX), Apache Jena/Arachne, Blazegraph (test queries via SPARQL), JUnit 4, Maven.

**Spec:** `.specs/2026-07-01-eliminate-reacto-ids-design.md`

## Global Constraints

- **Do NOT `git commit` or `git add`.** (Project instruction in CLAUDE.md; overrides the writing-plans default commit steps.) End each task by leaving the working tree modified for the user to review — never stage or commit.
- Entity strategy under test is **REACTO** (`BioPaxtoGO.EntityStrategy.REACTO`); the test go-lego is `./src/test/resources/ontology/go-lego-no-neo.owl`.
- All Maven commands run from the `exchange/` directory: `cd exchange && mvn …`. Heap is preconfigured in `exchange/pom.xml` (`-Xmx8g`); no extra `MAVEN_OPTS` needed.
- `BioPaxtoGOTest`'s `@BeforeClass` converts **every** `.owl` file in `src/test/resources/biopax/` into the Blazegraph journal; tests then query each model's named graph `<http://model.geneontology.org/<model_id>>`.
- surefire 2.12.4 cannot parse `+`-combined `-Dtest` selectors, and the class shares one heavy `@BeforeClass` per JVM — run a single method with `-Dtest=BioPaxtoGOTest#method`, or the whole class with `-Dtest=BioPaxtoGOTest`.
- The deletion targets only classes whose IRI starts with `http://purl.obolibrary.org/obo/go/extensions/reacto.owl#REACTO_`. This deliberately **excludes** `…/reacto.owl#molecular_event` (different localname prefix), and is a natural no-op for YeastCyc models (no REACTO types) — so **no `entityStrategy` guard is needed**.

---

### Task 1: Add the failing test

Demonstrates the goal: after converting "Heme biosynthesis" (R-HSA-189451) — which today emits several REACTO physical-entity individuals (e.g. `REACTO_R-HSA-189400`, `REACTO_R-HSA-189429`, `REACTO_R-HSA-190145`, `REACTO_R-HSA-9661451`) — no REACTO IRI may remain, while a known catalyzed reaction (`R-HSA-189439`, typed `obo:GO_0004655`) survives.

**Files:**
- Modify: `exchange/src/test/java/org/geneontology/gocam/exchange/BioPaxtoGOTest.java` (add one test method)

**Interfaces:**
- Consumes: the static `blaze` field (`Blazer`), the full conversion performed in `@BeforeClass`, and the existing private helper `int countSolutions(String sparql)` (already defined at `BioPaxtoGOTest.java:1218`). `countSolutions` runs a SELECT against the Blazegraph journal and returns the number of solution rows.
- Produces: test method `testNoReactoIdsInEmittedModel()`.

- [ ] **Step 1: Add the test method to `BioPaxtoGOTest`**

Add this method to the `BioPaxtoGOTest` class (e.g. just below `testReusedSmallMoleculeInputIsTyped`, which uses the same fixture and helper):

```java
	/**
	 * Eliminate REACTO IDs: the emitted model must contain NO REACTO physical-entity IRI —
	 * neither as an rdf:type object nor as a subject (class declaration) — while reactions and
	 * their GO molecular functions survive.
	 *
	 * In "Heme biosynthesis" (R-HSA-189451) the pipeline currently emits several REACTO_ complex/
	 * entity individuals (e.g. REACTO_R-HSA-189400 "8x(ALAD:Zn2+)"), so before the fix this test
	 * fails on the "no individual may be typed with a REACTO_ class" assertion.
	 */
	@Test
	public final void testNoReactoIdsInEmittedModel() {
		System.out.println("Testing that no REACTO IDs remain in the emitted model");
		String graph = "<http://model.geneontology.org/R-HSA-189451>";
		String reactoPrefix = "http://purl.obolibrary.org/obo/go/extensions/reacto.owl#REACTO_";

		// Survival precondition: a known catalyzed reaction still carries its GO molecular function.
		// Guards against a vacuous pass (wrong graph) and against over-deletion of reactions.
		int reactionPresent = countSolutions(
			"prefix obo: <http://purl.obolibrary.org/obo/> "
			+ "prefix rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#> "
			+ "select ?r where { GRAPH " + graph + " { "
			+ "<http://model.geneontology.org/R-HSA-189439> rdf:type obo:GO_0004655 } }");
		assertTrue("precondition: reaction R-HSA-189439 must survive typed obo:GO_0004655 (got "
			+ reactionPresent + ")", reactionPresent > 0);

		// (1) No individual may be typed with a REACTO_ class.
		int reactoTyped = countSolutions(
			"prefix rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#> "
			+ "select ?s where { GRAPH " + graph + " { "
			+ "?s rdf:type ?c . FILTER(STRSTARTS(STR(?c), \"" + reactoPrefix + "\")) } }");
		assertEquals("no individual may be typed with a REACTO_ class", 0, reactoTyped);

		// (2) No REACTO_ IRI may appear as a subject either (dangling class declarations swept).
		int reactoSubject = countSolutions(
			"select ?c where { GRAPH " + graph + " { "
			+ "?c ?p ?o . FILTER(STRSTARTS(STR(?c), \"" + reactoPrefix + "\")) } }");
		assertEquals("no REACTO_ IRI may appear as a subject in the model", 0, reactoSubject);
	}
```

- [ ] **Step 2: Run the test and verify it FAILS for the right reason**

Run:
```bash
cd /Users/ebertdu/go/pathways2GO/exchange && mvn -Dtest=BioPaxtoGOTest#testNoReactoIdsInEmittedModel test
```
Expected: BUILD FAILURE. The **precondition must PASS** (confirms the graph IRI and model are correct), and the first failing assertion is:
`no individual may be typed with a REACTO_ class expected:<0> but was:<N>` with N ≥ 1 (R-HSA-189451 currently has several REACTO-typed individuals).

If the precondition itself fails, stop: the graph IRI or the reaction/GO anchor is wrong — re-derive them from the generated TTL at `src/test/resources/gocam/R-HSA-189451_level3-R-HSA-189451.ttl` before proceeding.

- [ ] **Step 3: Leave changes for review (no commit)**

Do not `git add`/`git commit`. Report that the failing test is in place and quote the failing assertion message (and confirm the precondition passed).

---

### Task 2: Implement the deletion pass and wire it in

Add `deleteReactoTypedIndividuals()` and call it from `applySparqlRules`, turning the Task 1 test green without regressing the inference/flatten/regulator anchors.

**Files:**
- Modify: `exchange/src/main/java/org/geneontology/gocam/exchange/GoCAM.java` (add one method; add one call inside `applySparqlRules`)
- Modify: `exchange/src/test/java/org/geneontology/gocam/exchange/BioPaxtoGOTest.java` (update one stale comment)

**Interfaces:**
- Consumes: `GoCAM.reacto_base_iri` (`GoCAM.java:118`, the `…#REACTO_` prefix); `go_cam_ont` (the ABox `OWLOntology`); `deleteOwlEntityAndAllReferencesToIt(OWLEntity)` (`GoCAM.java:880`, one-arg overload → `delete_related_nodes=false`); the `qrunner` field and `QRunner(OWLOntology)` constructor. `AxiomType`, `OWLClassAssertionAxiom`, `OWLClassExpression`, `OWLClass`, `OWLNamedIndividual`, `Set`, `HashSet` are all already imported/used in `GoCAM.java` (e.g. `deleteDisallowedRelations` uses `go_cam_ont.getAxioms(AxiomType.OBJECT_PROPERTY_ASSERTION)`).
- Produces: `private void deleteReactoTypedIndividuals()`; a new call to it inside `applySparqlRules`.

- [ ] **Step 1: Add the `deleteReactoTypedIndividuals()` method to `GoCAM.java`**

Insert this method immediately **after** the `deleteDisallowedRelations()` method (which ends at `GoCAM.java:1811`, the `}` following the `"Eliminated 'located in' assertions"` println):

```java
	/**
	 * Delete every individual still typed with a REACTO physical-entity class
	 * (http://purl.obolibrary.org/obo/go/extensions/reacto.owl#REACTO_...), then remove the
	 * now-dangling REACTO class declarations, so no REACTO IRI survives in the emitted model.
	 *
	 * Resolvable entities (small molecules -> CHEBI, UniProt-backed proteins -> UniProt, flattened
	 * complex enablers -> GO_0032991) were already typed with a non-REACTO class upstream, so anything
	 * still typed REACTO_ is by definition unresolved and is dropped here. Reactions are typed with GO
	 * MF classes or molecular_event (never REACTO_), so they are never targets; deleting a REACTO
	 * participant only drops that participant and its edges (delete_related_nodes=false). molecular_event
	 * (localname does not start with "REACTO_") is deliberately not matched.
	 */
	private void deleteReactoTypedIndividuals() {
		String reactoPrefix = GoCAM.reacto_base_iri.toString();
		//1. collect all individuals typed with a REACTO_ class (collect first, then delete, so we do
		//not mutate the ontology while iterating its axioms)
		Set<OWLNamedIndividual> toDelete = new HashSet<OWLNamedIndividual>();
		for(OWLClassAssertionAxiom ax : go_cam_ont.getAxioms(AxiomType.CLASS_ASSERTION)) {
			OWLClassExpression type = ax.getClassExpression();
			if(type.isAnonymous()) {
				continue;
			}
			if(type.asOWLClass().getIRI().toString().startsWith(reactoPrefix) && ax.getIndividual().isNamed()) {
				toDelete.add(ax.getIndividual().asOWLNamedIndividual());
			}
		}
		//2. delete each individual and every axiom referencing it (edges + evidence-annotated axioms)
		for(OWLNamedIndividual ind : toDelete) {
			deleteOwlEntityAndAllReferencesToIt(ind);
		}
		//3. sweep the leftover REACTO_ class declarations so no REACTO IRI remains anywhere
		for(OWLClass c : new HashSet<OWLClass>(go_cam_ont.getClassesInSignature())) {
			if(c.getIRI().toString().startsWith(reactoPrefix)) {
				deleteOwlEntityAndAllReferencesToIt(c);
			}
		}
		//4. resync the sparqlable model with the mutated ontology (matches deleteDisallowedRelations)
		qrunner = new QRunner(go_cam_ont);
		System.out.println("Deleted "+toDelete.size()+" REACTO-typed individuals");
	}
```

- [ ] **Step 2: Wire the call into `applySparqlRules`**

In `GoCAM.applySparqlRules` (`GoCAM.java:976`), find this block (`:994-999`):

```java
		logger.debug("deleting complexes with active units");
		deleteComplexesWithActiveUnits();
		logger.debug("deleting disallowed relations like that between a non-gene product molecular and the reaction it regulates");		
		deleteDisallowedRelations();
		logger.debug("clean up any stray individuals");
		cleanOutUnconnectedNodes();
```

Insert the new call **between** `deleteDisallowedRelations()` and the `cleanOutUnconnectedNodes()` logger line, so the block becomes:

```java
		logger.debug("deleting complexes with active units");
		deleteComplexesWithActiveUnits();
		logger.debug("deleting disallowed relations like that between a non-gene product molecular and the reaction it regulates");		
		deleteDisallowedRelations();
		logger.debug("deleting individuals still typed with a REACTO class");
		deleteReactoTypedIndividuals();
		logger.debug("clean up any stray individuals");
		cleanOutUnconnectedNodes();
```

- [ ] **Step 3: Run the new test and verify it PASSES**

Run:
```bash
cd /Users/ebertdu/go/pathways2GO/exchange && mvn -Dtest=BioPaxtoGOTest#testNoReactoIdsInEmittedModel test
```
Expected: BUILD SUCCESS — all three assertions green (precondition survives; zero REACTO as `rdf:type` object; zero REACTO as subject).

- [ ] **Step 4: Update the stale comment in `BioPaxtoGOTest` (`testOverCapComplexDropsReactoSetNode`)**

The comment at `BioPaxtoGOTest.java:~1964-1969` states the dropped set "may still legitimately appear as a reaction input/output participant … and KEEPS its REACTO class." That is no longer true — REACTO participants are now deleted globally. The assertion itself (scoped to the enabler's `has_part`, expects 0) still holds. Replace the stale comment:

Find:
```java
			// the dropped set node is gone from the ENABLER: the over-cap reaction's flattened PCC enabler
			// has no has_part typed with the set's REACTO class. IMPORTANT: the query is SCOPED to the
			// enabler (via enabled_by/has_part). The same set may still legitimately appear as a reaction
			// input/output participant, which is OUT OF SCOPE (spec §8) and KEEPS its REACTO class — so an
			// unscoped "no instance anywhere" assertion would be wrong. Full-IRI form (hyphenated localname
			// cannot be a CURIE), matching existing tests e.g. BioPaxtoGOTest.java:1423 <...#REACTO_R-HSA-70987>.
```
Replace with:
```java
			// the dropped set node is gone from the ENABLER: the over-cap reaction's flattened PCC enabler
			// has no has_part typed with the set's REACTO class. This assertion is SCOPED to the enabler
			// (via enabled_by/has_part) and expects 0. Note: as of the "eliminate REACTO IDs" feature,
			// REACTO-typed participants are deleted from the whole model (deleteReactoTypedIndividuals),
			// so this set no longer survives as an input/output participant either. Full-IRI form
			// (hyphenated localname cannot be a CURIE), matching existing tests e.g.
			// BioPaxtoGOTest.java:1423 <...#REACTO_R-HSA-70987>.
```

- [ ] **Step 5: Run the full `BioPaxtoGOTest` class and verify no regressions**

The class shares one `@BeforeClass` conversion, so this re-validates the inference/flatten/regulator behavior alongside the new test:
```bash
cd /Users/ebertdu/go/pathways2GO/exchange && mvn -Dtest=BioPaxtoGOTest test
```
Expected: BUILD SUCCESS. In particular confirm these anchors stay green (they encode load-bearing behavior; deleting REACTO participants runs *after* they are consumed by inference):
- `testInferRegulatesViaOutputRegulates`, `testInferRegulatesViaOutputEnables`, `testInferSmallMoleculeRegulators`
- `testComplexRegulatorLeavesNoOrphanComponents`, `testCausalPathBridging`
- `testComplexCofactorReducedToSingleProtein`, `testComplexEnablerFlattenedToPCC`, `testComplexEnablerSingleProteinNoResidualNode`, `testOverCapComplexDropsReactoSetNode`
- `testReusedSmallMoleculeInputIsTyped` (its CHEBI input is non-REACTO, so it must still be typed and present).

If any test that asserts the **presence** of a specific REACTO-typed participant edge fails (none are expected — all known REACTO-type assertions are absence checks), report it: it is a genuine behavior change from this feature and needs the user's decision, not a silent test edit.

- [ ] **Step 6: Run the focused neighbor tests**

```bash
cd /Users/ebertdu/go/pathways2GO/exchange && mvn -Dtest=SetEnabledReactionSplitTest test
cd /Users/ebertdu/go/pathways2GO/exchange && mvn -Dtest=QRunnerPartToComplexIndexTest test
```
Expected: BUILD SUCCESS for both. `QRunnerPartToComplexIndexTest` exercises the tbox part→complex index with synthetic REACTO classes and is independent of emitted-model ABox — it must be unaffected.

- [ ] **Step 7: Leave changes for review (no commit)**

Do not `git add`/`git commit`. Summarize: the new method + call site, the Task 1 test now passing, the stale comment fixed, and the full-class + neighbor test results (quote the BUILD SUCCESS lines / test counts).

---

## Self-Review

**Spec coverage:**
- New `GoCAM.deleteReactoTypedIndividuals()` (find REACTO-typed individuals, delete, sweep class decls, resync) → Task 2, Step 1. ✓
- Called in `applySparqlRules` after `deleteDisallowedRelations`, before `cleanOutUnconnectedNodes` → Task 2, Step 2. ✓
- Scope = `REACTO_` prefix only; `molecular_event` excluded; no `entityStrategy` guard → method prefix match (Task 2, Step 1) + Global Constraints. ✓
- Zero REACTO as `rdf:type` object AND as subject; reactions survive → Task 1 test assertions (1), (2), precondition. ✓
- Existing REACTO assertions are absence checks / unaffected; stale comment fixed; presence-of-participant sweep → Task 2, Steps 4–6. ✓
- Out-of-scope items (resolution/retyping, molecular_event handling, causal bridging, typing changes) → no task touches them. ✓

**Placeholder scan:** No TBD/TODO; all Java and SPARQL is concrete; every command has expected output. ✓

## Update (2026-07-02) — anchor test revised during execution

Task 2 Step 5 surfaced the anticipated behavior change: `testInferRegulatesViaOutputEnables` regressed
because reaction2 (`R-HSA-5651723`) is `enabled_by` a REACTO-typed protein-set active unit
(`REACTO_R-HSA-5649876`, "PARP1,PARP2") that `deleteReactoTypedIndividuals()` deletes. The inferred
`reaction1 RO_0002629 reaction2` relation survives (it does not reference the enabler); only the test's
`?reaction2 RO_0002333 ?active_part` clause fails. Per the controller/user decision, the anchor was
updated (not the feature): the enabler-presence clause was removed so the test verifies the regulation
inference (`RO_0002629` + `BFO_0000050` pathway) without requiring the deleted enabler. The `n==1` and
pathway-equality assertions are unchanged. See spec "Update (2026-07-02)".

---

**Type consistency:** `countSolutions(String)` matches the existing helper at `BioPaxtoGOTest.java:1218`. `deleteReactoTypedIndividuals()` is defined once (Task 2, Step 1) and called once (Step 2) with the same name. `deleteOwlEntityAndAllReferencesToIt(OWLEntity)` (one-arg) matches `GoCAM.java:880`. `GoCAM.reacto_base_iri` matches `GoCAM.java:118`. `AxiomType.CLASS_ASSERTION` / `OWLClassAssertionAxiom.getClassExpression()` / `.getIndividual()` are OWL API standard and consistent with usage in `deleteDisallowedRelations`. ✓
