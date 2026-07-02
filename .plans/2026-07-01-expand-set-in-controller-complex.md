# Expand Set Members in Controller Complexes Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

> **Project policy (CLAUDE.md): do NOT run `git add` or `git commit`.** Each task ends at a **Checkpoint** (tests green). The user commits manually. The skill's usual "commit" step is replaced by "Checkpoint" throughout.

**Goal:** Convert a Reactome Catalysis reaction whose controller is a Complex containing an EntitySet component into one flattened-complex-enabled activity per combination of set members (a "reaction diamond"), capped at 10 combinations; over-cap reactions fall back to a single flattened PCC with the REACTO set node dropped and logged.

**Architecture:** Phase 1 (build, `BioPaxtoGO.defineReactionEntity` controller loop): detect a Catalysis Complex controller containing set(s); if `2 ≤ combos ≤ 10`, enumerate the flattened protein-set per combination and wire one `enabled_by` per distinct combination (single protein directly, or a `GO:0032991` PCC with `has_part` proteins), then record the reaction IRI. Phase 2 (`GoCAM.splitSetEnabledReactions`, already in `wrapAndWrite`) clones the reaction once per `enabled_by` target — **reused unchanged**. Over-cap (`combos > 10`) and set-free complexes flatten as today, but the explosion branch no longer emits the set's REACTO union-class node (it is dropped and logged).

**Tech Stack:** Java 8, Maven, OWL API, Paxtools (BioPAX), Apache Jena, Blazegraph, JUnit 4. Spec: `.specs/2026-07-01-expand-set-in-controller-complex-design.md`.

## Global Constraints

- **No git operations** — end each task at a Checkpoint (tests green), not a commit.
- **Combination cap = 10** (`SET_COMBINATION_CAP`), gating on the raw structural count.
- **Catalysis only** — non-small-molecule regulators are already skipped at emission.
- **Entity strategy under test: REACTO** (default for `BioPaxtoGOTest.fullBuild()`).
- **All new methods/fields package-private** (no modifier) so same-package tests can call them.
- Tests are heavy where noted: `BioPaxtoGOTest`'s `@BeforeClass` converts the corpus into Blazegraph and downloads `go-plus.owl` on first run (~8 GB heap; run from `exchange/`). Unit tests in `SetEnabledReactionSplitTest` are fast (no `@BeforeClass`).

---

## File Structure

- **Modify** `exchange/src/main/java/org/geneontology/gocam/exchange/BioPaxtoGO.java`
  - New constant `SET_COMBINATION_CAP`.
  - New methods `countFlattenedComplexCombinations(Complex)`, `enumerateFlattenedProteinSets(Complex)` (+ private recursive helpers `countCombos`, `enumProteinSets`), and a small `comboKeyToken(String)` IRI-token helper. Place next to `collectFlattenedComplexLeaves` (~`:2136`).
  - New expansion branch in the controller-emission loop (Loop 2, after the `isExplodableEntitySet` branch ~`:1838`).
  - Modified flatten *decision* (Loop 1 ~`:1715`–`:1743`) to gate on combos and key on proteins.
  - Modified explosion Complex branch (~`:1305`–`:1319`) to drop `setLeaves` emission and log `DROPPED_REACTO_SET_NODE`.
- **Reuse unchanged** `exchange/src/main/java/org/geneontology/gocam/exchange/GoCAM.java` — `splitSetEnabledReactions` (`:1920`), `cloneIndividualSharingNeighbors` (`:1883`), `set_enabled_reaction_iris` (`:162`).
- **Test fixture (already generated, ground-truth)** `exchange/src/test/resources/biopax/R-HSA-204005_level3.owl` — self-contained "COPII-mediated vesicle transport" pathway (R-HSA-204005), extracted from `biopax_97/Homo_sapiens.owl` (contains `activeUnit:` annotations verbatim). Contains: `R-HSA-6814833` (controller RAB1:GTP:TBC1D20, combos=2, **no** activeUnit — the expand case), `R-HSA-5694421` (PP6, combos=2 but HAS activeUnit — the negative guard), and eligible over-cap cases `R-HSA-5694527` (combos=192) / `R-HSA-203973` (combos=1536). **Do not hand-edit the fixture** (a prior attempt deleted an activeUnit comment to force expansion — that is wrong). Regenerate if needed with the closure-extraction script (Task 3 Step 1 note).
- **Modify** `exchange/src/test/java/org/geneontology/gocam/exchange/SetEnabledReactionSplitTest.java` — fast unit tests for the two pure helpers.
- **Modify** `exchange/src/test/java/org/geneontology/gocam/exchange/BioPaxtoGOTest.java` — end-to-end expand + cap-guard assertions.

**Reference data (from the fixture):**

**IMPORTANT — expansion only fires when the Catalysis has NO `activeUnit:` annotation.** A `bp:Catalysis` comment `activeUnit: #ProteinN` makes `getActiveSites` populate `active_sites`, and an annotated active site wins (no expansion). The original PP6 example (`R-HSA-5694421`) has `activeUnit: #Protein205` (PPP6C, the catalytic subunit), so it does NOT expand — it is enabled_by PPP6C. Use it instead as a negative guard.

Driving (positive) example — `R-HSA-6814833`, Catalysis controller `RAB1:GTP:TBC1D20` (combos=2, **no** activeUnit):

| Entity | Reactome id | UniProt |
|---|---|---|
| Reaction (RAB1 GTP hydrolysis, TBC1D20-catalyzed) | `R-HSA-6814833` | — |
| Complex "RAB1:GTP:TBC1D20" (controller) | `R-HSA-6814832` | — |
| sub-complex "RAB1:GTP" | `R-HSA-6807799` | — |
| GTP (small molecule, **stripped**) | `R-ALL-29438` | — |
| Set "RAB1" | `R-HSA-6807798` | — |
| TBC1D20 (fixed subunit) | `R-HSA-6814813` | Q96BZ9 |
| RAB1B (set member) | `R-HSA-6807768` | Q9H0U4 |
| RAB1A (set member, displayName "RAB1") | `R-HSA-6807766` | P62820 |

Expected split: 2 diamonds, each `enabled_by` a `GO:0032991` PCC with `has_part` exactly **2** proteins — `{Q96BZ9 TBC1D20}` + one of `{Q9H0U4 RAB1B, P62820 RAB1A}` (GTP is stripped).

Negative guard: `R-HSA-5694421` (PP6) has an activeUnit → is NOT split.

Over-cap example (eligible, no activeUnit): `R-HSA-5694527` combos=192, controller `R-HSA-5694523` (flattens to concrete proteins SAR1B + SEC23A → single PCC). A dropped set inside it is `R-HSA-5694244`, so its would-be REACTO class is `REACTO_R-HSA-5694244` and must have zero instances after the fix.

---

## Task 1: Combination counter + cap constant

Add the cheap arithmetic gate `countFlattenedComplexCombinations` and the `SET_COMBINATION_CAP` constant. This is a pure BioPAX-level count (no OWL, no ontology), so it is unit-testable directly on the fixture.

**Files:**
- Modify: `exchange/src/main/java/org/geneontology/gocam/exchange/BioPaxtoGO.java` (constant near the other fields; methods after `collectFlattenedComplexLeaves` ~`:2169`)
- Test: `exchange/src/test/java/org/geneontology/gocam/exchange/SetEnabledReactionSplitTest.java`

**Interfaces:**
- Produces: `static final int SET_COMBINATION_CAP = 10;` and `long countFlattenedComplexCombinations(Complex top)` — returns the product/sum combination count, or `SET_COMBINATION_CAP + 1` once the running count passes the cap.

- [ ] **Step 1: Write the failing test** — add to `SetEnabledReactionSplitTest` (the file already imports JUnit + OWL API; add the Paxtools imports shown):

```java
    @Test
    public void testCountFlattenedComplexCombinations() throws Exception {
        BioPaxtoGO bp = new BioPaxtoGO();
        org.biopax.paxtools.io.BioPAXIOHandler handler = new org.biopax.paxtools.io.SimpleIOHandler();
        org.biopax.paxtools.model.Model model = handler.convertFromOWL(
                new java.io.FileInputStream("./src/test/resources/biopax/R-HSA-204005_level3.owl"));
        bp.biopax_model = model;

        org.biopax.paxtools.model.level3.Complex pp6 = null;      // combos = 2
        org.biopax.paxtools.model.level3.Complex big = null;      // combos > cap
        for (org.biopax.paxtools.model.level3.Complex c : model.getObjects(org.biopax.paxtools.model.level3.Complex.class)) {
            if ("PP6".equals(c.getDisplayName())) pp6 = c;
            if ("R-HSA-5694334".equals(bp.getEntityReferenceId(c))) big = c;
        }
        assertNotNull("PP6 complex not found", pp6);
        assertNotNull("big complex R-HSA-5694334 not found", big);

        assertEquals(2L, bp.countFlattenedComplexCombinations(pp6));
        assertTrue("over-cap complex must short-circuit to > cap",
                bp.countFlattenedComplexCombinations(big) > BioPaxtoGO.SET_COMBINATION_CAP);
    }
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd exchange && mvn -Dtest=SetEnabledReactionSplitTest#testCountFlattenedComplexCombinations test`
Expected: **compilation failure** — `cannot find symbol: method countFlattenedComplexCombinations` / `variable SET_COMBINATION_CAP`.

- [ ] **Step 3: Add the constant** — in `BioPaxtoGO.java`, add near the top of the class body (with the other `static final` fields):

```java
	//Max flattened-complex combinations to expand a set-containing Catalysis controller complex into diamonds.
	//Above this, the reaction falls back to a single flattened PCC (REACTO set node dropped) and is not split.
	static final int SET_COMBINATION_CAP = 10;
```

- [ ] **Step 4: Add the counter** — in `BioPaxtoGO.java`, immediately after the `collectFlattenedComplexLeaves(Complex, FlattenedComplex, Set<Complex>)` method (~`:2169`):

```java
	/*
	 * Count the flattened enabler combinations a Complex produces when each internal EntitySet
	 * is expanded (one member per set): combos(Complex)=product over components, combos(Set)=sum
	 * over members, leaf=1. SmallMoleculeEquivalent leaves are stripped. Cycle-guarded (DFS stack).
	 * Short-circuits: once the running count passes SET_COMBINATION_CAP it returns SET_COMBINATION_CAP+1
	 * (avoids overflow / deep work on huge complexes). Pure BioPAX; creates no OWL.
	 */
	long countFlattenedComplexCombinations(Complex top) {
		return countCombos(top, new HashSet<String>());
	}

	private long countCombos(PhysicalEntity node, Set<String> visiting) {
		if (isSmallMoleculeEquivalent(node)) {
			return 1;
		}
		String id = node.getUri();
		if (!visiting.add(id)) {
			return 1; // cycle guard
		}
		try {
			Set<PhysicalEntity> members = node.getMemberPhysicalEntity();
			if (members != null && !members.isEmpty()) {
				// EntitySet: pick one member -> sum over non-stripped members
				long sum = 0;
				for (PhysicalEntity m : members) {
					if (isSmallMoleculeEquivalent(m)) {
						continue;
					}
					sum += countCombos(m, visiting);
					if (sum > SET_COMBINATION_CAP) {
						return SET_COMBINATION_CAP + 1;
					}
				}
				return (sum == 0) ? 1 : sum;
			}
			if (node instanceof Complex) {
				long prod = 1;
				for (PhysicalEntity c : ((Complex) node).getComponent()) {
					if (isSmallMoleculeEquivalent(c)) {
						continue;
					}
					prod *= countCombos(c, visiting);
					if (prod > SET_COMBINATION_CAP) {
						return SET_COMBINATION_CAP + 1;
					}
				}
				return prod;
			}
			return 1; // Protein / other leaf
		} finally {
			visiting.remove(id);
		}
	}
```

(`Complex`, `PhysicalEntity`, `Set`, `HashSet` are already imported in `BioPaxtoGO.java`. `isSmallMoleculeEquivalent` (`:2266`) is an existing pure method.)

- [ ] **Step 5: Run test to verify it passes**

Run: `cd exchange && mvn -Dtest=SetEnabledReactionSplitTest#testCountFlattenedComplexCombinations test`
Expected: **BUILD SUCCESS**, Tests run: 1, Failures: 0.

- [ ] **Step 6: Checkpoint** — counter green. Do not commit.

---

## Task 2: Combination enumerator

Add `enumerateFlattenedProteinSets`, which materializes the per-combination protein sets. Same recursion shape as the counter; only called when `combos ≤ cap`, so it never explodes.

**Files:**
- Modify: `exchange/src/main/java/org/geneontology/gocam/exchange/BioPaxtoGO.java` (after `countCombos`)
- Test: `exchange/src/test/java/org/geneontology/gocam/exchange/SetEnabledReactionSplitTest.java`

**Interfaces:**
- Consumes: `isSmallMoleculeEquivalent`, `extractUniprotId` (existing).
- Produces: `List<Map<String,Protein>> enumerateFlattenedProteinSets(Complex top)` — one map per combination, keyed by UniProt id else BioPAX URI. Combinations are **not** deduped here (the wiring branch dedups by key-set).

- [ ] **Step 1: Write the failing test** — add to `SetEnabledReactionSplitTest`:

```java
    @Test
    public void testEnumerateFlattenedProteinSets() throws Exception {
        BioPaxtoGO bp = new BioPaxtoGO();
        org.biopax.paxtools.io.BioPAXIOHandler handler = new org.biopax.paxtools.io.SimpleIOHandler();
        org.biopax.paxtools.model.Model model = handler.convertFromOWL(
                new java.io.FileInputStream("./src/test/resources/biopax/R-HSA-204005_level3.owl"));
        bp.biopax_model = model;

        org.biopax.paxtools.model.level3.Complex pp6 = null;
        for (org.biopax.paxtools.model.level3.Complex c : model.getObjects(org.biopax.paxtools.model.level3.Complex.class)) {
            if ("PP6".equals(c.getDisplayName())) { pp6 = c; break; }
        }
        assertNotNull(pp6);

        java.util.List<java.util.Map<String, org.biopax.paxtools.model.level3.Protein>> combos =
                bp.enumerateFlattenedProteinSets(pp6);
        assertEquals("PP6 should enumerate 2 combinations", 2, combos.size());

        java.util.Set<java.util.Set<String>> keySets = new java.util.HashSet<java.util.Set<String>>();
        for (java.util.Map<String, org.biopax.paxtools.model.level3.Protein> m : combos) {
            keySets.add(new java.util.TreeSet<String>(m.keySet()));
        }
        // each combination = the two fixed subunits + one set member
        assertTrue(keySets.contains(new java.util.TreeSet<String>(java.util.Arrays.asList("O00743", "O15084", "Q9UPN7"))));
        assertTrue(keySets.contains(new java.util.TreeSet<String>(java.util.Arrays.asList("O00743", "O15084", "Q5H9R7"))));
    }
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd exchange && mvn -Dtest=SetEnabledReactionSplitTest#testEnumerateFlattenedProteinSets test`
Expected: **compilation failure** — `cannot find symbol: method enumerateFlattenedProteinSets`.

- [ ] **Step 3: Add the enumerator** — in `BioPaxtoGO.java`, immediately after `countCombos`:

```java
	/*
	 * Enumerate the flattened enabler protein-sets a Complex produces when each internal EntitySet
	 * is expanded (one member per set), matching countFlattenedComplexCombinations. Each combination
	 * is a Map keyed by UniProt id (else BioPAX URI). Complex -> cartesian product across components;
	 * Set -> concatenation across members; Protein -> singleton; SmallMoleculeEquivalent / DNA / RNA /
	 * bare non-ChEBI PE -> empty. Cycle-guarded. Combinations are NOT deduped here. Only call when
	 * countFlattenedComplexCombinations(top) <= SET_COMBINATION_CAP.
	 */
	List<Map<String, Protein>> enumerateFlattenedProteinSets(Complex top) {
		return enumProteinSets(top, new HashSet<String>());
	}

	private List<Map<String, Protein>> enumProteinSets(PhysicalEntity node, Set<String> visiting) {
		List<Map<String, Protein>> result = new ArrayList<Map<String, Protein>>();
		if (isSmallMoleculeEquivalent(node)) {
			result.add(new HashMap<String, Protein>());
			return result;
		}
		String id = node.getUri();
		if (!visiting.add(id)) {
			result.add(new HashMap<String, Protein>()); // cycle guard
			return result;
		}
		try {
			Set<PhysicalEntity> members = node.getMemberPhysicalEntity();
			if (members != null && !members.isEmpty()) {
				// EntitySet: OR over members (each member choice is a separate combination)
				for (PhysicalEntity m : members) {
					if (isSmallMoleculeEquivalent(m)) {
						continue;
					}
					result.addAll(enumProteinSets(m, visiting));
				}
				if (result.isEmpty()) {
					result.add(new HashMap<String, Protein>());
				}
				return result;
			}
			if (node instanceof Complex) {
				// Cartesian product across components (union the protein maps)
				result.add(new HashMap<String, Protein>());
				for (PhysicalEntity c : ((Complex) node).getComponent()) {
					if (isSmallMoleculeEquivalent(c)) {
						continue;
					}
					List<Map<String, Protein>> child = enumProteinSets(c, visiting);
					List<Map<String, Protein>> merged = new ArrayList<Map<String, Protein>>();
					for (Map<String, Protein> base : result) {
						for (Map<String, Protein> add : child) {
							Map<String, Protein> combo = new HashMap<String, Protein>(base);
							combo.putAll(add);
							merged.add(combo);
						}
					}
					result = merged;
				}
				return result;
			}
			if (node instanceof Protein) {
				String key = extractUniprotId((Protein) node);
				if (key == null) {
					key = node.getUri();
				}
				Map<String, Protein> m = new HashMap<String, Protein>();
				m.put(key, (Protein) node);
				result.add(m);
				return result;
			}
			// Dna / Rna / bare non-ChEBI PhysicalEntity -> contributes nothing
			result.add(new HashMap<String, Protein>());
			return result;
		} finally {
			visiting.remove(id);
		}
	}
```

(`List`, `ArrayList`, `Map`, `HashMap`, `Protein` are already imported in `BioPaxtoGO.java`. `extractUniprotId` (`:2363`) is an existing static method.)

- [ ] **Step 4: Run test to verify it passes**

Run: `cd exchange && mvn -Dtest=SetEnabledReactionSplitTest#testEnumerateFlattenedProteinSets test`
Expected: **BUILD SUCCESS**, Tests run: 1, Failures: 0.

- [ ] **Step 5: Run both new unit tests together**

Run: `cd exchange && mvn -Dtest=SetEnabledReactionSplitTest test`
Expected: all green (the two new tests plus the pre-existing ones in the class).

- [ ] **Step 6: Checkpoint** — enumerator green. Do not commit.

---

## Task 3: Wire the expansion branch + end-to-end split test

Insert the Loop-2 branch that builds one enabler per distinct combination and records the reaction for splitting, then assert reaction `R-HSA-6814833` (controller `RAB1:GTP:TBC1D20`, combos=2, no activeUnit) becomes 2 diamonds. Phase 2 (`splitSetEnabledReactions`) already runs in `wrapAndWrite` — no change there.

The branch has two extra guards beyond the naive gate (both required — a naive `controller_entity instanceof Complex` gate breaks existing tests):
- **Guard A** — `((Complex) controller_entity).getMemberPhysicalEntity().isEmpty()`: only fire for a *true* complex (variation embedded as `component`s). A `bp:Complex` that is itself an EntitySet (`memberPhysicalEntity` non-empty, no components — e.g. a pyruvate-kinase-tetramer set) must keep its existing behavior; expanding it deletes the reaction node and breaks `testInferSmallMoleculeRegulators`.
- **Guard B** — only `continue` (and record) when `distinct >= 1`; if the complex yields zero protein combinations, fall through to the normal path (log `SET_IN_COMPLEX_NO_PROTEINS`).

**Files:**
- Modify: `exchange/src/main/java/org/geneontology/gocam/exchange/BioPaxtoGO.java` (add `comboKeyToken`; insert branch in Loop 2 ~`:1838`)
- Test: `exchange/src/test/java/org/geneontology/gocam/exchange/BioPaxtoGOTest.java`

**Interfaces:**
- Consumes: `countFlattenedComplexCombinations`, `enumerateFlattenedProteinSets` (Tasks 1–2); `GoCAM.set_enabled_reaction_iris`, `GoCAM.splitSetEnabledReactions` (existing); `iriToCurie`, `getPhysicalEntityIRI`, `getEntityReferenceId`, `defineReactionEntity` (existing).

- [ ] **Step 1: Confirm the fixture is present**

Run: `ls -la exchange/src/test/resources/biopax/R-HSA-204005_level3.owl`
Expected: the file exists (~1.4 MB). If missing, regenerate from `biopax_97/Homo_sapiens.owl` by extracting the transitive `rdf:resource` closure of the Pathway with Reactome id `R-HSA-204005` and re-serializing as RDF/XML (preserve `xml:base="http://www.reactome.org/biopax/97/48887#"` and the `owl:Ontology`/`owl:imports` header). Verify with `xmllint --noout` and that it declares `rdf:ID="BiochemicalReaction23"` and `rdf:ID="Catalysis6"` with zero dangling internal refs.

- [ ] **Step 2: Write the failing test** — add to `BioPaxtoGOTest` (among the other `@Test` methods; the `blaze`/`TupleQueryResult`/`BindingSet` helpers used here already exist in this class):

```java
	/**
	 * Pathway R-HSA-204005: reaction R-HSA-6814833 is catalyzed by complex RAB1:GTP:TBC1D20
	 * (R-HSA-6814832) = TBC1D20 + [RAB1:GTP -> GTP(stripped) + RAB1 set {RAB1B|RAB1A}], combos=2,
	 * NO activeUnit. It must become 2 activities, each enabled_by a GO:0032991 PCC has_part exactly
	 * two proteins: TBC1D20 (Q96BZ9) + one of {RAB1B Q9H0U4, RAB1A P62820}. GTP is stripped.
	 * Negative guard: R-HSA-5694421 (PP6) HAS an activeUnit, so it must NOT be split.
	 */
	@Test
	public final void testSetInComplexReactionExpanded() {
		System.out.println("Testing set-in-complex reaction expansion");
		try {
			// original un-split reaction node must be gone (it was replaced by the diamond clones)
			TupleQueryResult orig = blaze.runSparqlQuery(
				"prefix obo: <http://purl.obolibrary.org/obo/> select ?e where { "
				+ "<http://model.geneontology.org/R-HSA-6814833> obo:RO_0002333 ?e }");
			int origN = 0;
			while (orig.hasNext()) { orig.next(); origN++; }
			orig.close();
			assertTrue("original reaction should be split away, enablers on it = " + origN, origN == 0);

			// exactly 2 split activities, each enabled_by a protein-containing complex (GO:0032991)
			TupleQueryResult res = blaze.runSparqlQuery(
				"prefix obo: <http://purl.obolibrary.org/obo/> select ?reaction ?pcc where { "
				+ "?reaction obo:RO_0002333 ?pcc . ?pcc a obo:GO_0032991 . "
				+ "FILTER(STRSTARTS(STR(?reaction), \"http://model.geneontology.org/R-HSA-6814833_enabled_by\")) }");
			Set<String> reactions = new HashSet<String>();
			Set<String> pccs = new HashSet<String>();
			while (res.hasNext()) {
				BindingSet b = res.next();
				reactions.add(b.getValue("reaction").stringValue());
				pccs.add(b.getValue("pcc").stringValue());
			}
			res.close();
			assertTrue("expected 2 split activities, got " + reactions.size(), reactions.size() == 2);
			assertTrue("expected 2 distinct PCC enablers, got " + pccs.size(), pccs.size() == 2);

			// both PCCs share TBC1D20 (Q96BZ9) as a has_part protein type.
			// UniProt class IRIs use GoCAM.uniprot_iri = http://identifiers.org/uniprot/ (full-IRI form,
			// matching existing tests, e.g. BioPaxtoGOTest.java:1401 <http://identifiers.org/uniprot/P21912>).
			TupleQueryResult shared = blaze.runSparqlQuery(
				"prefix obo: <http://purl.obolibrary.org/obo/> select ?pcc where { "
				+ "?reaction obo:RO_0002333 ?pcc . ?pcc a obo:GO_0032991 . "
				+ "?pcc obo:BFO_0000051 ?fixed . ?fixed a <http://identifiers.org/uniprot/Q96BZ9> . "
				+ "FILTER(STRSTARTS(STR(?reaction), \"http://model.geneontology.org/R-HSA-6814833_enabled_by\")) }");
			Set<String> sharedPccs = new HashSet<String>();
			while (shared.hasNext()) { sharedPccs.add(shared.next().getValue("pcc").stringValue()); }
			shared.close();
			assertTrue("both PCCs must has_part TBC1D20, got " + sharedPccs.size(), sharedPccs.size() == 2);

			// the variable subunit: one PCC has RAB1B (Q9H0U4), the other RAB1A (P62820)
			for (String acc : new String[] { "Q9H0U4", "P62820" }) {
				TupleQueryResult v = blaze.runSparqlQuery(
					"prefix obo: <http://purl.obolibrary.org/obo/> select ?pcc where { "
					+ "?reaction obo:RO_0002333 ?pcc . ?pcc a obo:GO_0032991 . "
					+ "?pcc obo:BFO_0000051 ?var . ?var a <http://identifiers.org/uniprot/" + acc + "> . "
					+ "FILTER(STRSTARTS(STR(?reaction), \"http://model.geneontology.org/R-HSA-6814833_enabled_by\")) }");
				int n = 0;
				while (v.hasNext()) { v.next(); n++; }
				v.close();
				assertTrue("exactly one PCC must has_part " + acc + ", got " + n, n == 1);
			}

			// negative guard: PP6 (R-HSA-5694421) HAS an activeUnit annotation, so it is NOT expanded/split
			TupleQueryResult pp6 = blaze.runSparqlQuery(
				"prefix obo: <http://purl.obolibrary.org/obo/> select ?reaction where { "
				+ "?reaction obo:RO_0002333 ?e . "
				+ "FILTER(STRSTARTS(STR(?reaction), \"http://model.geneontology.org/R-HSA-5694421_enabled_by\")) }");
			int pp6N = 0;
			while (pp6.hasNext()) { pp6.next(); pp6N++; }
			pp6.close();
			assertTrue("PP6 has an activeUnit and must NOT be split, split clones = " + pp6N, pp6N == 0);
		} catch (QueryEvaluationException e) {
			e.printStackTrace();
			fail(e.getMessage());
		}
		System.out.println("Done testing set-in-complex reaction expansion");
	}
```

Note: the UniProt class IRIs use `GoCAM.uniprot_iri` = `http://identifiers.org/uniprot/` (confirmed `:119`), written as full IRIs above to match the existing tests (e.g. `:1401`).

- [ ] **Step 3: Run test to verify it fails**

Run: `cd exchange && MAVEN_OPTS="-Xmx8g" mvn -Dtest=BioPaxtoGOTest#testSetInComplexReactionExpanded test`
Expected: **FAIL** — `original reaction should be split away, enablers on it = 1` (the fixture is converted by `@BeforeClass`, but with no expansion branch yet R-HSA-6814833 is still a single PCC enabler, un-split). `go-plus.owl` is already cached; the build is a few minutes.

- [ ] **Step 4a: Add the `comboKeyToken` helper** — in `BioPaxtoGO.java`, next to the enumerator:

```java
	/*
	 * Deterministic IRI-safe token for a combination's sorted key list. Sanitizes to
	 * [A-Za-z0-9_]; hashes when long so the enabler IRI stays bounded.
	 */
	private static String comboKeyToken(String combo_key) {
		String token = combo_key.replaceAll("[^A-Za-z0-9]", "_");
		if (token.length() <= 60) {
			return token;
		}
		return "combo" + Integer.toHexString(combo_key.hashCode());
	}
```

- [ ] **Step 4b: Insert the expansion branch** — in `BioPaxtoGO.defineReactionEntity`, inside Loop 2 (`for(Controller controller_entity : controller_entities)` at ~`:1769`), immediately **after** the `isExplodableEntitySet` block's closing brace (~`:1838`) and **before** the `if (drop_controller_entities.contains(controller_entity))` check (~`:1839`):

```java
						//Catalysis controller Complex containing EntitySet component(s): expand into one
						//flattened-complex enabler per combination of set members (capped), split later in
						//splitSetEnabledReactions. An annotated active site (active_sites non-empty) wins.
						//Guard A: only a TRUE complex (variation embedded as components); a bp:Complex that is
						//itself an EntitySet (memberPhysicalEntity non-empty) keeps its existing behavior.
						if (is_catalysis && active_sites.isEmpty() && controller_entity instanceof Complex
								&& ((Complex) controller_entity).getMemberPhysicalEntity().isEmpty()) {
							long combos = countFlattenedComplexCombinations((Complex) controller_entity);
							if (combos >= 2 && combos <= SET_COMBINATION_CAP) {
								Set<String> seen_combos = new HashSet<String>();
								int distinct = 0;
								String top_curie = iriToCurie(getPhysicalEntityIRI(controller_entity));
								for (Map<String, Protein> ps : enumerateFlattenedProteinSets((Complex) controller_entity)) {
									if (ps.isEmpty()) {
										continue;
									}
									List<String> sorted_keys = new ArrayList<String>(ps.keySet());
									Collections.sort(sorted_keys);
									String combo_key = String.join("_", sorted_keys);
									if (!seen_combos.add(combo_key)) {
										continue; // dedup combinations that yield the same protein set
									}
									distinct++;
									if (ps.size() == 1) {
										// single distinct protein -> enable_by it directly (no PCC)
										Protein only = ps.values().iterator().next();
										String only_id = getEntityReferenceId(only);
										IRI enabler_iri = GoCAM.makeGoCamifiedIRI(null, only_id + "_" + entity_id + "_controller");
										OWLNamedIndividual enabler_e = go_cam.df.getOWLNamedIndividual(enabler_iri);
										defineReactionEntity(go_cam, only, enabler_iri, true, model_id, root_pathway_iri, reaction_id, false);
										go_cam.addRefBackedObjectPropertyAssertion(e, GoCAM.enabled_by, enabler_e, dbids, GoCAM.eco_imported_auto, default_namespace_prefix, null, model_id);
									} else {
										// >=2 distinct proteins -> one flattened protein-containing complex enabler
										String combo_token = comboKeyToken(combo_key);
										IRI pcc_iri = GoCAM.makeGoCamifiedIRI(null, (top_curie + "_" + combo_token + "_" + entity_id + "_controller").replace(":", "_"));
										OWLNamedIndividual pcc_e = go_cam.makeAnnotatedIndividual(pcc_iri);
										go_cam.addTypeAssertion(pcc_e, go_cam.df.getOWLClass(IRI.create("http://purl.obolibrary.org/obo/GO_0032991")));
										if (controller_entity.getDisplayName() != null) {
											go_cam.addLabel(pcc_e, controller_entity.getDisplayName());
										}
										String pcc_local = pcc_iri.toString().replace("http://model.geneontology.org/", "");
										for (Protein prot : ps.values()) {
											IRI comp_iri = GoCAM.makeGoCamifiedIRI(null, (iriToCurie(getPhysicalEntityIRI(prot)) + "_" + pcc_local + "_component").replace(":", "_"));
											OWLNamedIndividual comp_e = go_cam.makeAnnotatedIndividual(comp_iri);
											defineReactionEntity(go_cam, prot, comp_iri, true, model_id, root_pathway_iri, reaction_id, false);
											go_cam.addRefBackedObjectPropertyAssertion(pcc_e, GoCAM.has_part, comp_e, dbids, GoCAM.eco_imported_auto, default_namespace_prefix, null, model_id);
										}
										go_cam.addRefBackedObjectPropertyAssertion(e, GoCAM.enabled_by, pcc_e, dbids, GoCAM.eco_imported_auto, default_namespace_prefix, null, model_id);
									}
								}
								// Guard B: only record + continue if at least one enabler was built; otherwise
								// (distinct == 0, e.g. a set-of-complexes with no UniProt leaves) fall through.
								if (distinct >= 1) {
									go_cam.set_enabled_reaction_iris.add(e.getIRI());
									System.out.println("SET_IN_COMPLEX_REACTION_EXPANDED\t" + model_id + "\t" + go_cam.name + "\t" + entity_id + "\t" + getEntityReferenceId(controller_entity) + "\t" + combos + "\t" + distinct);
									continue;
								}
								System.out.println("SET_IN_COMPLEX_NO_PROTEINS\t" + model_id + "\t" + go_cam.name + "\t" + entity_id + "\t" + getEntityReferenceId(controller_entity) + "\t" + combos);
							}
						}
```

All names used are in scope at that point (`is_catalysis`, `active_sites`, `e`, `entity_id`, `dbids`, `default_namespace_prefix`, `model_id`, `root_pathway_iri`, `reaction_id`, `controller_entity`; statics `GoCAM.enabled_by`, `GoCAM.has_part`, `GoCAM.eco_imported_auto`). `Collections`, `List`, `ArrayList`, `Map`, `Protein`, `IRI`, `OWLNamedIndividual` are already imported.

- [ ] **Step 5: Run test to verify it passes**

Run: `cd exchange && MAVEN_OPTS="-Xmx8g" mvn -Dtest=BioPaxtoGOTest#testSetInComplexReactionExpanded test`
Expected: **BUILD SUCCESS**, Tests run: 1, Failures: 0. Watch the build log for `SET_IN_COMPLEX_REACTION_EXPANDED ... R-HSA-6814833 ... 2 2`.

- [ ] **Step 6: Checkpoint** — expansion works end-to-end. Do not commit.

---

## Task 4: Over-cap fallback — drop and log the REACTO set node

Make the over-cap fallback flatten to proteins only: the explosion Complex branch stops emitting the set's REACTO union-class node (logging each dropped node), and the Loop-1 decision gates on combos and keys on protein subunits.

**Files:**
- Modify: `exchange/src/main/java/org/geneontology/gocam/exchange/BioPaxtoGO.java` (explosion branch ~`:1305`–`:1319`; Loop-1 decision ~`:1715`–`:1743`)
- Test: `exchange/src/test/java/org/geneontology/gocam/exchange/BioPaxtoGOTest.java`

**Interfaces:**
- Consumes: `countFlattenedComplexCombinations` (Task 1); `collectFlattenedComplexLeaves`, `getPhysicalEntityIRI`, `iriToCurie`, `getEntityReferenceId` (existing).

- [ ] **Step 1: Write the failing test** — add to `BioPaxtoGOTest`:

```java
	/**
	 * Cap guard: reaction R-HSA-5694527 (controller complex combos=192 > SET_COMBINATION_CAP, no activeUnit)
	 * must NOT be split, and no individual may be typed with the dropped set's REACTO class (R-HSA-5694244).
	 */
	@Test
	public final void testOverCapComplexDropsReactoSetNode() {
		System.out.println("Testing over-cap complex drops REACTO set node");
		try {
			// not split: no R-HSA-5694527_enabled_by_* clones exist
			TupleQueryResult split = blaze.runSparqlQuery(
				"prefix obo: <http://purl.obolibrary.org/obo/> select ?reaction where { "
				+ "?reaction obo:RO_0002333 ?e . "
				+ "FILTER(STRSTARTS(STR(?reaction), \"http://model.geneontology.org/R-HSA-5694527_enabled_by\")) }");
			int splitN = 0;
			while (split.hasNext()) { split.next(); splitN++; }
			split.close();
			assertTrue("over-cap reaction must not be split, clones = " + splitN, splitN == 0);

			// the original reaction node survives (was not replaced) and still has its single flattened enabler
			TupleQueryResult present = blaze.runSparqlQuery(
				"prefix obo: <http://purl.obolibrary.org/obo/> select ?e where { "
				+ "<http://model.geneontology.org/R-HSA-5694527> obo:RO_0002333 ?e }");
			int presentN = 0;
			while (present.hasNext()) { present.next(); presentN++; }
			present.close();
			assertTrue("original over-cap reaction should keep exactly one flattened enabler, got " + presentN, presentN == 1);

			// the dropped set node is gone from the ENABLER: the over-cap reaction's flattened PCC enabler
			// has no has_part typed with the set's REACTO class. IMPORTANT: the query is SCOPED to the
			// enabler (via enabled_by/has_part). The same set may still legitimately appear as a reaction
			// input/output participant, which is OUT OF SCOPE (spec §8) and KEEPS its REACTO class — so an
			// unscoped "no instance anywhere" assertion would be wrong. Full-IRI form (hyphenated localname
			// cannot be a CURIE), matching existing tests e.g. BioPaxtoGOTest.java:1423 <...#REACTO_R-HSA-70987>.
			TupleQueryResult reacto = blaze.runSparqlQuery(
				"prefix obo: <http://purl.obolibrary.org/obo/> select ?x where { "
				+ "<http://model.geneontology.org/R-HSA-5694527> obo:RO_0002333 ?pcc . "
				+ "?pcc obo:BFO_0000051 ?x . "
				+ "?x a <http://purl.obolibrary.org/obo/go/extensions/reacto.owl#REACTO_R-HSA-5694244> }");
			int reactoN = 0;
			while (reacto.hasNext()) { reacto.next(); reactoN++; }
			reacto.close();
			assertTrue("dropped set REACTO node must not be a has_part of the enabler, got " + reactoN, reactoN == 0);
		} catch (QueryEvaluationException e) {
			e.printStackTrace();
			fail(e.getMessage());
		}
		System.out.println("Done testing over-cap complex drops REACTO set node");
	}
```

Note: the reacto class IRI is `http://purl.obolibrary.org/obo/go/extensions/reacto.owl#REACTO_` + id (= `GoCAM.reacto_base_iri`, `:118`); used in full-IRI form above (the hyphenated localname cannot be a CURIE), matching existing tests at `:1368/:1415/:1423`. The query is deliberately scoped to `enabled_by → has_part` so it tests the enabler's dropped setLeaf, not participant sets (which keep their REACTO class per spec §8).

**Do NOT** make participant/input-output EntitySets stop using their REACTO class to satisfy this — that is out of scope (spec §8) and mistypes sets as PCCs corpus-wide. The scoped query above avoids that trap.

- [ ] **Step 2: Run test to verify it fails**

Run: `cd exchange && MAVEN_OPTS="-Xmx8g" mvn -Dtest=BioPaxtoGOTest#testOverCapComplexDropsReactoSetNode test`
Expected: **FAIL** — `dropped set REACTO node must not be a has_part of the enabler, got 1` (before the fix, the over-cap complex enabler PCC keeps the set as a REACTO union `has_part` node).

- [ ] **Step 3a: Drop `setLeaves` emission + log in the explosion Complex branch** — in `BioPaxtoGO.defineReactionEntity`, replace the leaf-collection block (~`:1305`–`:1308`):

```java
						FlattenedComplex flat = collectFlattenedComplexLeaves((Complex) entity);
						Set<PhysicalEntity> leaves = new HashSet<PhysicalEntity>();
						leaves.addAll(flat.proteinsByKey.values());
						leaves.addAll(flat.setLeaves);
```

with (drop the set leaves; log each one so removed REACTO individuals stay traceable):

```java
						FlattenedComplex flat = collectFlattenedComplexLeaves((Complex) entity);
						for (PhysicalEntity setLeaf : flat.setLeaves) {
							//over-cap fallback (or a set nested in a whole-complex leaf): the set's REACTO
							//union-class node is dropped, not emitted. Log it for traceability.
							System.out.println("DROPPED_REACTO_SET_NODE\t" + model_id + "\t" + go_cam.name + "\t" + reaction_id + "\t" + entity_id + "\t" + getEntityReferenceId(setLeaf) + "\t" + iriToCurie(getPhysicalEntityIRI(setLeaf)) + "\t" + setLeaf.getDisplayName());
						}
						Set<PhysicalEntity> leaves = new HashSet<PhysicalEntity>(flat.proteinsByKey.values());
```

(The existing `for(PhysicalEntity c : leaves) { ... has_part ... }` loop below is unchanged; it now iterates proteins only. `entity_id`, `reaction_id`, `model_id`, `go_cam` are in scope in this branch.)

- [ ] **Step 3b: Gate the Loop-1 flatten decision on combos and key on proteins** — in `BioPaxtoGO.defineReactionEntity`, Loop 1, replace the current decision (`:1725`–`:1740`, the block from the `// No annotated active site` comment through the `COMPLEX_FLATTENED_TO_PCC` else):

```java
									// No annotated active site: decide the enabler by flattening the complex
									// hierarchy to its distinct UniProt protein subunits (+ EntitySet subunits).
									FlattenedComplex flat = collectFlattenedComplexLeaves((Complex) controller_entity);
									if (flat.isEmpty()) {
										drop_controller_entities.add((PhysicalEntity) controller_entity);
										System.out.println("COMPLEX_FLATTEN_NO_PROTEIN\t"+...);
									} else if (flat.proteinsByKey.size() == 1 && flat.setLeaves.isEmpty()) {
										PhysicalEntity single = flat.proteinsByKey.values().iterator().next();
										active_sites.add(single);
										System.out.println("COMPLEX_FLATTENED_TO_SINGLE_PROTEIN\t"+...);
									} else {
										System.out.println("COMPLEX_FLATTENED_TO_PCC\t"+...);
									}
```

with a combos gate first (leave the log-argument tails exactly as they are today):

```java
									// combos in [2, cap] are expanded into diamonds in Loop 2; skip the flatten decision.
									long combos = countFlattenedComplexCombinations((Complex) controller_entity);
									if (combos >= 2 && combos <= SET_COMBINATION_CAP) {
										System.out.println("COMPLEX_SET_WILL_EXPAND\t"+model_id+"\t"+go_cam.name+"\t"+entity_id+"\t"+entity_name+"\t"+complex_entity_id+"\t"+controller_entity.getDisplayName()+"\t"+combos);
										continue;
									}
									// combos == 1 (no set) or over cap: flatten to protein subunits only.
									// The set's REACTO union node is not emitted (dropped + logged in the explosion branch).
									FlattenedComplex flat = collectFlattenedComplexLeaves((Complex) controller_entity);
									if (flat.proteinsByKey.isEmpty()) {
										// no concrete protein subunit (set-only / cofactor-only) -> drop the enabler
										drop_controller_entities.add((PhysicalEntity) controller_entity);
										System.out.println("COMPLEX_FLATTEN_NO_PROTEIN\t"+model_id+"\t"+go_cam.name+"\t"+entity_id+"\t"+entity_name+"\t"+complex_entity_id+"\t"+controller_entity.getDisplayName());
									} else if (flat.proteinsByKey.size() == 1) {
										PhysicalEntity single = flat.proteinsByKey.values().iterator().next();
										active_sites.add(single);
										System.out.println("COMPLEX_FLATTENED_TO_SINGLE_PROTEIN\t"+model_id+"\t"+go_cam.name+"\t"+entity_id+"\t"+entity_name+"\t"+complex_entity_id+"\t"+controller_entity.getDisplayName()+"\t"+getEntityReferenceId(single));
									} else {
										System.out.println("COMPLEX_FLATTENED_TO_PCC\t"+model_id+"\t"+go_cam.name+"\t"+entity_id+"\t"+entity_name+"\t"+complex_entity_id+"\t"+controller_entity.getDisplayName()+"\t"+flat.totalLeaves());
									}
```

(Copy the exact existing `System.out.println` argument tails from the current code for `COMPLEX_FLATTEN_NO_PROTEIN`, `COMPLEX_FLATTENED_TO_SINGLE_PROTEIN`, `COMPLEX_FLATTENED_TO_PCC` — they are shown abbreviated with `...` above only to mark what is unchanged. `complex_entity_id` and `entity_name` are already in scope in Loop 1.)

- [ ] **Step 4: Run test to verify it passes**

Run: `cd exchange && MAVEN_OPTS="-Xmx8g" mvn -Dtest=BioPaxtoGOTest#testOverCapComplexDropsReactoSetNode test`
Expected: **BUILD SUCCESS**, Tests run: 1, Failures: 0. Watch for `DROPPED_REACTO_SET_NODE ... R-HSA-5694244 ...` in the build log.

- [ ] **Step 5: Re-run the whole class to confirm both tests pass together (no regression)**

Run: `cd exchange && MAVEN_OPTS="-Xmx8g" mvn -Dtest=BioPaxtoGOTest test`
Expected: BUILD SUCCESS; both `testSetInComplexReactionExpanded` and `testOverCapComplexDropsReactoSetNode` green (Loop-1 combos gate leaves PP6's expansion intact). (Surefire 2.12.4 cannot parse combined `#a,#b` selectors — run the whole class; its heavy `@BeforeClass` runs once, then all methods execute cheaply.)

- [ ] **Step 6: Checkpoint** — over-cap fallback + REACTO-node drop/log working. Do not commit.

---

## Task 5: Regression — full suite

Confirm the change didn't break existing conversions (split-set, flatten-complex, regulator, causal, provides-input tests share the same corpus build).

**Files:** none (verification only).

- [ ] **Step 1: Run the full BioPaxtoGO test class**

Run: `cd exchange && MAVEN_OPTS="-Xmx8g" mvn -Dtest=BioPaxtoGOTest test`
Expected: **BUILD SUCCESS**. In particular `testSetEnabledReactionSplit`, `testComplexCofactorReducedToSingleProtein`, `testActiveSiteInController`, `testComplexRegulatorLeavesNoOrphanComponents`, `testInferProvidesInput`, `testInferRegulatesViaOutputEnables` still pass. The new fixture (R-HSA-204005) adds a pathway to the shared build; existing assertions reference their own pathways and must be unaffected.

- [ ] **Step 2: Run the full project test suite**

Run: `cd exchange && MAVEN_OPTS="-Xmx8g" mvn test`
Expected: **BUILD SUCCESS** across `BioPaxtoGOTest`, `BioPaxtoGOTestYeastCyc`, `PhysicalEntityOntologyBuilderTest`, `QRunnerPartToComplexIndexTest`, `SetEnabledReactionSplitTest`.

- [ ] **Step 3: Checkpoint** — full suite green. Hand back to the user to review and commit.

---

## Self-Review (performed against the spec)

**Spec coverage:**
- §3/§4.1 cap = 10 + cheap short-circuiting count → Task 1 (`countFlattenedComplexCombinations`, `SET_COMBINATION_CAP`). ✓
- §6.1 enumeration → Task 2 (`enumerateFlattenedProteinSets`). ✓
- §6.2 expansion branch, 1-protein-direct vs ≥2-PCC, dedup, deterministic IRIs, record + `continue` → Task 3. ✓
- §4.2 "flatten each combination" → Task 3 (single protein directly; ≥2 → GO:0032991 PCC has_part proteins). ✓
- §4.3/§6.3 over-cap drops + logs the REACTO set node; set-only-over-cap dropped → Task 4 (explosion branch drop + `DROPPED_REACTO_SET_NODE`; Loop-1 `proteinsByKey.isEmpty()` drop). ✓
- §4.5 annotated active site wins → Task 3 branch gated on `active_sites.isEmpty()`; Loop-1 keeps the `active_sites.size() > 0` short-circuit above the combos gate. ✓
- §4.4 Catalysis only → `is_catalysis` guard (Task 3) + regulators skipped upstream (unchanged). ✓
- Phase 2 reuse → no change to `splitSetEnabledReactions`; reaction recorded in `set_enabled_reaction_iris` (Task 3). ✓
- §9 testing: unit count/enumerate (Tasks 1–2), integration expand 2-diamond (Task 3), cap guard + REACTO-drop (Task 4), regression (Task 5). ✓

**Placeholder scan:** the only `...` are in Task 4 Step 3b, explicitly marking unchanged existing `println` tails to copy verbatim — not a code gap. All executable steps contain complete code/commands.

**Type consistency:** `SET_COMBINATION_CAP` (int), `countFlattenedComplexCombinations`→`long`, `enumerateFlattenedProteinSets`→`List<Map<String,Protein>>`, `comboKeyToken`→`String`, private helpers `countCombos`/`enumProteinSets`, the `enabled_by`/`RO_0002333` and `GO_0032991`/`has_part`/`BFO_0000051` references, and the IRI schemes (`{id}_{entity_id}_controller`, `{topCurie}_{comboToken}_{entity_id}_controller`, `{protCurie}_{pccLocal}_component`) match across tasks and the SPARQL assertions.

**Open risks to watch during execution:**
- UniProt class IRI = `http://identifiers.org/uniprot/` (`GoCAM.uniprot_iri:119`); Task 3 queries use full IRIs (resolved).
- Task 4 must use the full `<http://purl.obolibrary.org/obo/go/extensions/reacto.owl#REACTO_R-HSA-5694244>` IRI form (matching existing tests at `:1368/:1415/:1423`), not a hyphenated CURIE.
- If `getEntityReferenceId` for a Complex does not return the `R-HSA-...` form used in Task 1's `big` lookup, match the over-cap complex by a stable displayName prefix instead.
