# Flatten Multi-Subunit Complex Enablers Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Emit a Catalysis complex enabler as a single protein-containing complex (PCC) whose `has_part` edges point directly to the distinct UniProtKB protein subunits found anywhere in the BioPAX complex hierarchy — stripping cofactors and intermediate sub-complex individuals — collapsing to a single protein enabler when only one distinct protein remains, dropping the enabler when none remains, and skipping non-small-molecule regulators at emission.

**Architecture:** A new pure-BioPAX traversal `collectFlattenedComplexLeaves(Complex)` dedups protein subunits by UniProt accession across the whole component tree. The Complex branch of the enabler "explosion" in `defineReactionEntity` is rewritten to emit those flat leaves. The enabler-decision block (Loop 1) uses the same traversal to route to single-protein / flat-PCC / drop. The vestigial complex node is deleted when the enabler resolves to a protein active unit, and non-small-molecule regulators are skipped before emission.

**Tech Stack:** Java 8, paxtools-core (BioPAX level3), OWL API, JUnit 4, Blazegraph + SPARQL (test assertions), Maven.

## Global Constraints

- **No git operations.** Per the repo's CLAUDE.md: do NOT `git add` or `git commit`. The user commits manually. Each task ends with a build/test verification step, not a commit.
- **Entity strategy:** the behavior being changed runs under `EntityStrategy.REACTO` (the default for `fullBuild()` in the test harness).
- **Tests are heavy:** `BioPaxtoGOTest`'s `@BeforeClass` (`fullBuild`) converts every BioPAX file in `src/test/resources/biopax/` (plus YeastCyc) into Blazegraph and downloads `go-plus.owl` on first run. Even a single `@Test` triggers the whole build. Allow ~8GB heap and several minutes. All Maven commands run from the `exchange/` directory.
- **Do not restructure** `BioPaxtoGO.java`; it is a large existing file. Add the helper near `complexHasProtein` / `getComplexActiveUnitRecursive` (~line 2096) and make minimal edits at the documented sites.
- **Preserve the existing component IRI scheme** `{leaf_curie}_{topComplexId}_{reactionId}_component` exactly — a downstream pass (`deleteRegulatorAndComponents`) and existing tests match on it.

## Reference identifiers (verified against the fixtures)

- **SDH multi-subunit case** — pathway/graph `http://model.geneontology.org/R-HSA-71403`, reaction `http://model.geneontology.org/R-HSA-70994` ("SDH complex dehydrogenates succinate"), controller complex R-HSA-70990 ("SDH complex"). Flattens to 4 distinct UniProt subunits: SDHB `P21912`, SDHA `P31040`, SDHC `Q99643`, SDHD `O14521`. Cofactors to strip: 2Fe-2S (`R-ALL-164296`), 3Fe-4S (`R-ALL-164319`), 4Fe-4S, plus heme-b small molecules. Intermediate sub-complexes that must NOT appear: R-HSA-70987 (Complex19), and the SDHC:SDHD:heme-b sub-complex (Complex20).
- **FECH single-protein case** — graph `http://model.geneontology.org/R-HSA-189451`, reaction `http://model.geneontology.org/R-HSA-189465`, complex R-HSA-189402 ("2x(FECH:2Fe-2S cluster)"), enabler protein FECH `P22830`. Already a fixture (`R-HSA-189451_level3.owl`).
- **ALAD regulator case** — graph `http://model.geneontology.org/R-HSA-189451`, regulated reaction R-HSA-189439, regulator complex R-HSA-190145 ("8xALAD:Pb2+:Zn2+"), its ALAD component IRI `http://model.geneontology.org/UniProtKB_P13716_R-HSA-190145_R-HSA-189439_component`. Already a fixture.
- **Small-molecule regulator guard** — reaction `http://model.geneontology.org/R-HSA-71670` has 4 small-molecule regulators via `obo:RO_0012001`/`obo:RO_0012002` (asserted by the existing `testInferSmallMoleculeRegulators`). Already a fixture.
- **Constant IRIs:** UniProt class = `http://identifiers.org/uniprot/<id>`; REACTO class = `http://purl.obolibrary.org/obo/go/extensions/reacto.owl#REACTO_<stableId>`; PCC = `http://purl.obolibrary.org/obo/GO_0032991`; `has_part` = `obo:BFO_0000051`; `enabled_by` = `obo:RO_0002333`.

---

## File Structure

- `exchange/src/main/java/org/geneontology/gocam/exchange/BioPaxtoGO.java` — add `FlattenedComplex` holder + `collectFlattenedComplexLeaves` (2 overloads) near `complexHasProtein` (~2096); rewrite the Complex branch of the explosion (~1287–1309); replace the enabler-decision block in the controller-resolution loop (~1705–1729); declare `drop_controller_entities` (~1702); enable the residual-node deletion (~1883–1886); add the drop-skip (~1827) and the non-small-molecule-regulator skip (~1758) in the controller-emission loop.
- `exchange/src/test/resources/biopax/R-HSA-71403_level3.owl` — NEW fixture (copied from `exchange/R-HSA-71403_level3.owl`) so the SDH case is converted by `fullBuild`.
- `exchange/src/test/java/org/geneontology/gocam/exchange/BioPaxtoGOTest.java` — add `testComplexEnablerFlattenedToPCC` and `testComplexEnablerSingleProteinNoResidualNode`; augment `testComplexRegulatorLeavesNoOrphanComponents` is not required (it stays green); uses the existing `private int countSolutions(String)` helper.
- `exchange/src/main/java/org/geneontology/gocam/exchange/GoCAM.java` — UNCHANGED (`inferSmallMoleculeRegulators` / `deleteRegulatorAndComponents` remain as a backstop).

---

### Task 1: `collectFlattenedComplexLeaves` + flat PCC emission + SDH fixture

**Files:**
- Modify: `exchange/src/main/java/org/geneontology/gocam/exchange/BioPaxtoGO.java` (add helpers ~2096; rewrite explosion Complex branch ~1287–1309)
- Create: `exchange/src/test/resources/biopax/R-HSA-71403_level3.owl`
- Test: `exchange/src/test/java/org/geneontology/gocam/exchange/BioPaxtoGOTest.java` (`testComplexEnablerFlattenedToPCC`)

**Interfaces:**
- Consumes: existing `isSmallMoleculeEquivalent(PhysicalEntity)`, `extractUniprotId(Protein)`, `getEntityReferenceId(Entity)`, `getPhysicalEntityIRI(PhysicalEntity)`, `iriToCurie(IRI)`, `GoCAM.makeGoCamifiedIRI`, `GoCAM.has_part`; test helper `private int countSolutions(String)`.
- Produces: `private static class FlattenedComplex { Map<String,Protein> proteinsByUniprot; Set<PhysicalEntity> setLeaves; int totalLeaves(); boolean isEmpty(); }` and `private FlattenedComplex collectFlattenedComplexLeaves(Complex top)` — relied on by Task 2.

- [ ] **Step 1: Add the SDH fixture**

```bash
cp /Users/ebertdu/go/pathways2GO/exchange/R-HSA-71403_level3.owl \
   /Users/ebertdu/go/pathways2GO/exchange/src/test/resources/biopax/R-HSA-71403_level3.owl
```

- [ ] **Step 2: Write the failing test**

Add this method to `BioPaxtoGOTest.java`, immediately after `testComplexCofactorReducedToSingleProtein()` (ends ~line 1305):

```java
	/**
	 * The SDH complex (R-HSA-70990) catalyzes R-HSA-70994 with no activeUnit annotation and
	 * has multiple distinct protein subunits, so it must emit as ONE protein-containing
	 * complex (GO:0032991) whose has_part edges point directly to the 4 distinct UniProt
	 * subunits (SDHA P31040, SDHB P21912, SDHC Q99643, SDHD O14521). The iron-sulfur
	 * cofactors (2Fe-2S R-ALL-164296 etc.) must be stripped and no intermediate sub-complex
	 * individual (R-HSA-70987) may appear.
	 */
	@Test
	public final void testComplexEnablerFlattenedToPCC() {
		System.out.println("Testing that a multi-subunit complex enabler flattens to one PCC of distinct UniProt proteins");
		String graph = "<http://model.geneontology.org/R-HSA-71403>";
		String rxn = "<http://model.geneontology.org/R-HSA-70994>";
		String obo = "prefix obo: <http://purl.obolibrary.org/obo/> "
			+ "prefix rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#> ";

		// Precondition: the catalyzed reaction is present (guards against a wrong graph IRI).
		int rxnTriples = countSolutions("select ?p ?o where { GRAPH " + graph + " { " + rxn + " ?p ?o . } }");
		assertTrue("precondition: R-HSA-70994 present in " + graph + " (got " + rxnTriples + ")", rxnTriples > 0);

		// Enabler is exactly one protein-containing complex (GO:0032991).
		int pccEnabler = countSolutions(obo + "select ?pcc where { GRAPH " + graph + " { "
			+ rxn + " obo:RO_0002333 ?pcc . ?pcc rdf:type obo:GO_0032991 . } }");
		assertTrue("R-HSA-70994 must be enabled_by a GO:0032991 PCC (got " + pccEnabler + ")", pccEnabler > 0);

		// The PCC has_part SDHB (UniProt P21912).
		int sdhb = countSolutions(obo + "select ?sub where { GRAPH " + graph + " { "
			+ rxn + " obo:RO_0002333 ?pcc . ?pcc rdf:type obo:GO_0032991 . "
			+ "?pcc obo:BFO_0000051 ?sub . ?sub rdf:type <http://identifiers.org/uniprot/P21912> . } }");
		assertTrue("PCC must have_part SDHB P21912 (got " + sdhb + ")", sdhb > 0);

		// Exactly 4 distinct UniProt subunits as has_part.
		int distinctSubunits = countSolutions(obo + "select distinct ?up where { GRAPH " + graph + " { "
			+ rxn + " obo:RO_0002333 ?pcc . ?pcc rdf:type obo:GO_0032991 . "
			+ "?pcc obo:BFO_0000051 ?sub . ?sub rdf:type ?up . "
			+ "FILTER(STRSTARTS(STR(?up), \"http://identifiers.org/uniprot/\")) } }");
		assertEquals("PCC must have_part the 4 distinct SDH UniProt subunits", 4, distinctSubunits);

		// Cofactor 2Fe-2S (REACTO_R-ALL-164296) must NOT be a has_part of the enabler.
		int cofactor = countSolutions(obo + "select ?sub where { GRAPH " + graph + " { "
			+ rxn + " obo:RO_0002333 ?pcc . ?pcc rdf:type obo:GO_0032991 . "
			+ "?pcc obo:BFO_0000051 ?sub . "
			+ "?sub rdf:type <http://purl.obolibrary.org/obo/go/extensions/reacto.owl#REACTO_R-ALL-164296> . } }");
		assertEquals("2Fe-2S cofactor must be stripped from the enabler", 0, cofactor);

		// No intermediate sub-complex: no has_part is itself typed as Complex19 (R-HSA-70987),
		// and there is no nested has_part-of-has_part under the enabler.
		int subComplex = countSolutions(obo + "select ?sub where { GRAPH " + graph + " { "
			+ rxn + " obo:RO_0002333 ?pcc . ?pcc rdf:type obo:GO_0032991 . "
			+ "?pcc obo:BFO_0000051 ?sub . "
			+ "?sub rdf:type <http://purl.obolibrary.org/obo/go/extensions/reacto.owl#REACTO_R-HSA-70987> . } }");
		assertEquals("no intermediate sub-complex (R-HSA-70987) may be a has_part of the enabler", 0, subComplex);
		int nested = countSolutions(obo + "select ?y where { GRAPH " + graph + " { "
			+ rxn + " obo:RO_0002333 ?pcc . ?pcc rdf:type obo:GO_0032991 . "
			+ "?pcc obo:BFO_0000051 ?x . ?x obo:BFO_0000051 ?y . } }");
		assertEquals("the flattened enabler must have no nested has_part-of-has_part", 0, nested);
	}
```

- [ ] **Step 3: Run the test to verify it fails**

```bash
cd exchange && mvn -Dtest=BioPaxtoGOTest#testComplexEnablerFlattenedToPCC test
```
Expected: FAIL. With the nested explosion, the enabler tree contains the intermediate sub-complex (R-HSA-70987) and the 2Fe-2S cofactor, and `nested`/`subComplex`/`cofactor` are > 0 (and `distinctSubunits` is likely not 4 at the top level).

- [ ] **Step 4: Add the `FlattenedComplex` holder and `collectFlattenedComplexLeaves`**

In `BioPaxtoGO.java`, immediately before `private boolean complexHasProtein(Complex controlled_by_complex)` (~line 2096), insert:

```java
	/**
	 * Result of flattening a Complex enabler to the distinct protein subunits and EntitySet
	 * subunits found anywhere in its component hierarchy. Pure data holder, no OWL.
	 */
	private static class FlattenedComplex {
		final Map<String, Protein> proteinsByUniprot = new HashMap<String, Protein>();
		final Set<PhysicalEntity> setLeaves = new HashSet<PhysicalEntity>();
		int totalLeaves() { return proteinsByUniprot.size() + setLeaves.size(); }
		boolean isEmpty() { return totalLeaves() == 0; }
	}

	/**
	 * Walk a Complex's component hierarchy and collect the leaves that should become has_part
	 * of a flattened protein-containing-complex enabler: distinct UniProt proteins (deduped by
	 * accession) plus EntitySet subunits (kept by their REACTO class, not descended into).
	 * SmallMoleculeEquivalent cofactors are stripped; UniProt-less proteins, DNA, RNA and
	 * non-ChEBI bare PhysicalEntities are skipped. Creates no OWL individuals.
	 */
	private FlattenedComplex collectFlattenedComplexLeaves(Complex top) {
		FlattenedComplex result = new FlattenedComplex();
		collectFlattenedComplexLeaves(top, result, new HashSet<Complex>());
		return result;
	}

	private void collectFlattenedComplexLeaves(Complex complex, FlattenedComplex result, Set<Complex> visited) {
		if (!visited.add(complex)) {
			return; // guard against cyclic complex references
		}
		for (PhysicalEntity c : complex.getComponent()) {
			if (isSmallMoleculeEquivalent(c)) {
				continue;
			}
			if (!c.getMemberPhysicalEntity().isEmpty()) {
				// EntitySet subunit: keep as a single has_part by its REACTO class; do not descend.
				result.setLeaves.add(c);
			} else if (c instanceof Complex) {
				collectFlattenedComplexLeaves((Complex) c, result, visited);
			} else if (c instanceof Protein) {
				String uniprot = extractUniprotId((Protein) c);
				if (uniprot != null) {
					result.proteinsByUniprot.put(uniprot, (Protein) c);
				}
			}
			// else: UniProt-less protein, Dna, Rna, non-ChEBI bare PhysicalEntity -> skip
		}
	}
```

(`Map`, `HashMap`, `Set`, `HashSet`, `Protein`, `PhysicalEntity`, `Complex` are already imported in this file.)

- [ ] **Step 5: Rewrite the Complex branch of the explosion**

In `BioPaxtoGO.java`, in the `if (explode_sets_complexes)` block, replace the Complex branch (currently ~lines 1287–1309).

Replace:
```java
				if(entity instanceof Complex && !(((Complex) entity).getComponent().isEmpty())) {
					// Definitely is a complex, so update entity_class to PCC
					entity_class_iri = IRI.create("http://purl.obolibrary.org/obo/GO_0032991");  // protein-containing complex
					
					// Dig out component protein IDs
					Set<PhysicalEntity> components = ((Complex) entity).getComponent();
					for(PhysicalEntity c : components) {
						if (c instanceof SmallMolecule) {
							// Skip small mols
							continue;
						}
						String component_id = getEntityReferenceId(c);
						System.out.println("Complex component ID: "+component_id);
						IRI component_class_iri = getPhysicalEntityIRI(c);
						String component_id_curie = iriToCurie(component_class_iri);
						String parent_component_id = entity_id;
						IRI iri = GoCAM.makeGoCamifiedIRI(null, (component_id_curie+"_"+parent_component_id+"_"+reaction_id+"_component").replace(":", "_"));
						OWLNamedIndividual component_e = go_cam.makeAnnotatedIndividual(iri);
		//					OWLClass component_class = go_cam.df.getOWLClass(component_class_iri);
						defineReactionEntity(go_cam, c, iri, true, model_id, root_pathway_iri, reaction_id, explode_sets_complexes);
		//					go_cam.addTypeAssertion(component_e,  component_class);
						go_cam.addRefBackedObjectPropertyAssertion(e, GoCAM.has_part, component_e, dbids, GoCAM.eco_imported_auto, default_namespace_prefix, null, model_id);
					}
				} else if (explode_sets && !((PhysicalEntity) entity).getMemberPhysicalEntity().isEmpty()) {
```
With:
```java
				if(entity instanceof Complex && !(((Complex) entity).getComponent().isEmpty())) {
					// Definitely is a complex, so update entity_class to PCC
					entity_class_iri = IRI.create("http://purl.obolibrary.org/obo/GO_0032991");  // protein-containing complex

					// Flatten the whole complex hierarchy to its distinct UniProt protein subunits
					// (+ any EntitySet subunits), stripping cofactors and dropping intermediate
					// sub-complex individuals. has_part edges point directly from this PCC to the leaves.
					FlattenedComplex flat = collectFlattenedComplexLeaves((Complex) entity);
					Set<PhysicalEntity> leaves = new HashSet<PhysicalEntity>();
					leaves.addAll(flat.proteinsByUniprot.values());
					leaves.addAll(flat.setLeaves);
					for(PhysicalEntity c : leaves) {
						String component_id = getEntityReferenceId(c);
						System.out.println("Complex component ID: "+component_id);
						IRI component_class_iri = getPhysicalEntityIRI(c);
						String component_id_curie = iriToCurie(component_class_iri);
						String parent_component_id = entity_id;
						IRI iri = GoCAM.makeGoCamifiedIRI(null, (component_id_curie+"_"+parent_component_id+"_"+reaction_id+"_component").replace(":", "_"));
						OWLNamedIndividual component_e = go_cam.makeAnnotatedIndividual(iri);
						defineReactionEntity(go_cam, c, iri, true, model_id, root_pathway_iri, reaction_id, explode_sets_complexes);
						go_cam.addRefBackedObjectPropertyAssertion(e, GoCAM.has_part, component_e, dbids, GoCAM.eco_imported_auto, default_namespace_prefix, null, model_id);
					}
				} else if (explode_sets && !((PhysicalEntity) entity).getMemberPhysicalEntity().isEmpty()) {
```

- [ ] **Step 6: Run the test to verify it passes**

```bash
cd exchange && mvn -Dtest=BioPaxtoGOTest#testComplexEnablerFlattenedToPCC test
```
Expected: PASS. The SDH enabler is one GO:0032991 PCC with `has_part` to the 4 distinct UniProt subunits; cofactors and the R-HSA-70987 sub-complex are absent; no nested has_part-of-has_part.

- [ ] **Step 7: Confirm the existing single-protein and regulator tests still pass**

The FECH case still reduces via the (still-present) `getComplexActiveUnitRecursive`; the ALAD regulator is still emitted-then-deleted (flat now, identical component IRI).
```bash
cd exchange && mvn -Dtest=BioPaxtoGOTest#testComplexCofactorReducedToSingleProtein+testComplexRegulatorLeavesNoOrphanComponents test
```
Expected: PASS for both.

---

### Task 2: Flatten-driven enabler decision + residual-node deletion + drop handling

Replaces `getComplexActiveUnitRecursive` as the enabler decision for a Catalysis complex controller, so a homodimer/single-protein complex collapses to one `enabled_by` protein, a multi-subunit complex stays a flat PCC, and a protein-less complex is dropped; deletes the vestigial complex node when the enabler is a protein active unit.

**Files:**
- Modify: `exchange/src/main/java/org/geneontology/gocam/exchange/BioPaxtoGO.java` (declare `drop_controller_entities` ~1702; replace enabler-decision block ~1705–1729; enable deletion ~1883–1886; add drop-skip ~1827)
- Test: `exchange/src/test/java/org/geneontology/gocam/exchange/BioPaxtoGOTest.java` (`testComplexEnablerSingleProteinNoResidualNode`)

**Interfaces:**
- Consumes: `collectFlattenedComplexLeaves(Complex)` and `FlattenedComplex` from Task 1; existing `active_sites`, `controller_e`, `getActiveSites`, `GoCAM.deleteOwlEntityAndAllReferencesToIt`.
- Produces: no new public surface; populates `Set<PhysicalEntity> drop_controller_entities` consumed within the same method.

- [ ] **Step 1: Write the failing test**

Add this method to `BioPaxtoGOTest.java`, immediately after `testComplexEnablerFlattenedToPCC()`:

```java
	/**
	 * When a Catalysis complex enabler flattens to exactly one distinct UniProt protein
	 * (FECH P22830 in complex R-HSA-189402 catalyzing R-HSA-189465), the reaction must be
	 * enabled_by that single protein with NO protein-containing-complex emitted, and the
	 * residual complex controller individual must be deleted (no stray node).
	 */
	@Test
	public final void testComplexEnablerSingleProteinNoResidualNode() {
		System.out.println("Testing single-protein flatten leaves exactly one enabler and no residual complex node");
		String graph = "<http://model.geneontology.org/R-HSA-189451>";
		String rxn = "<http://model.geneontology.org/R-HSA-189465>";
		String obo = "prefix obo: <http://purl.obolibrary.org/obo/> "
			+ "prefix rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#> ";

		// Precondition.
		int rxnTriples = countSolutions("select ?p ?o where { GRAPH " + graph + " { " + rxn + " ?p ?o . } }");
		assertTrue("precondition: R-HSA-189465 present (got " + rxnTriples + ")", rxnTriples > 0);

		// Exactly one enabled_by edge, and it is FECH (P22830).
		int enablers = countSolutions(obo + "select ?en where { GRAPH " + graph + " { "
			+ rxn + " obo:RO_0002333 ?en . } }");
		assertEquals("R-HSA-189465 must have exactly one enabled_by edge", 1, enablers);
		int fech = countSolutions(obo + "select ?en where { GRAPH " + graph + " { "
			+ rxn + " obo:RO_0002333 ?en . ?en rdf:type <http://identifiers.org/uniprot/P22830> . } }");
		assertTrue("R-HSA-189465 must be enabled_by FECH P22830 (got " + fech + ")", fech > 0);

		// No residual complex controller node for R-HSA-189402 -> R-HSA-189465.
		String controller = "<http://model.geneontology.org/R-HSA-189402_R-HSA-189465_controller>";
		int asSubject = countSolutions("select ?p ?o where { GRAPH " + graph + " { " + controller + " ?p ?o . } }");
		int asObject  = countSolutions("select ?s ?p where { GRAPH " + graph + " { ?s ?p " + controller + " . } }");
		assertEquals("residual complex controller node must have no outgoing triples", 0, asSubject);
		assertEquals("residual complex controller node must have no incoming triples", 0, asObject);
	}
```

- [ ] **Step 2: Run the test to verify it fails**

```bash
cd exchange && mvn -Dtest=BioPaxtoGOTest#testComplexEnablerSingleProteinNoResidualNode test
```
Expected: FAIL on the residual-node assertions — today `R-HSA-189402_R-HSA-189465_controller` exists (it `has_part` FECH), so `asSubject`/`asObject` are > 0. (`enablers`/`fech` already pass via the existing reduction.)

- [ ] **Step 3: Declare the drop set**

In `BioPaxtoGO.java`, find (~line 1702):
```java
					Set<PhysicalEntity> active_sites = getActiveSites(controller);
```
Add immediately after it:
```java
					Set<PhysicalEntity> drop_controller_entities = new HashSet<PhysicalEntity>();
```

- [ ] **Step 4: Replace the enabler-decision block**

In `BioPaxtoGO.java`, replace the complex active-unit resolution (~lines 1705–1729).

Replace:
```java
								if (controller_entity instanceof Complex) {
									boolean has_protein = complexHasProtein((Complex) controller_entity);
									String complex_entity_id = getEntityReferenceId(controller_entity);
									if (active_sites.size() > 0) {
										for (PhysicalEntity active_site : active_sites) {
											String active_site_id = getEntityReferenceId(active_site);
											System.out.println("COMPLEX_ACTIVE_UNIT_IS_SPECIFIED\t"+model_id+"\t"+go_cam.name+"\t"+entity_id+"\t"+entity_name+"\t"+complex_entity_id+"\t"+controller_entity.getDisplayName()+"\t"+active_site_id+"\t"+active_site.getDisplayName());
										}
										continue;
									}
									else if (has_protein && active_sites.isEmpty()) {
										System.out.println("COMPLEX_HAS_PROTEIN_NO_ACTIVE_UNIT\t"+model_id+"\t"+go_cam.name+"\t"+entity_id+"\t"+entity_name+"\t"+complex_entity_id+"\t"+controller_entity.getDisplayName());
										// If it's still empty, try more crazy stuff
										for(PhysicalEntity active_unit_result : getComplexActiveUnitRecursive((Complex) controller_entity).getActiveUnits()) {
											// Report out if active unit protein was extracted via "single-protein reduction"
											String active_site_id = getEntityReferenceId(active_unit_result);
											System.out.println("COMPLEX_REDUCED_TO_SINGLE_PROTEIN\t"+model_id+"\t"+go_cam.name+"\t"+entity_id+"\t"+entity_name+"\t"+complex_entity_id+"\t"+controller_entity.getDisplayName()+"\t"+active_site_id+"\t"+active_unit_result.getDisplayName());
											active_sites.add(active_unit_result);
										}
									}
									if (active_sites.isEmpty()) {
										// Still can't extract active unit for complex so report
										System.out.println("COMPLEX_CANT_BE_REDUCED_TO_PROTEIN\t"+model_id+"\t"+go_cam.name+"\t"+entity_id+"\t"+entity_name+"\t"+complex_entity_id+"\t"+controller_entity.getDisplayName());
									}
								}
```
With:
```java
								if (controller_entity instanceof Complex) {
									String complex_entity_id = getEntityReferenceId(controller_entity);
									if (active_sites.size() > 0) {
										for (PhysicalEntity active_site : active_sites) {
											String active_site_id = getEntityReferenceId(active_site);
											System.out.println("COMPLEX_ACTIVE_UNIT_IS_SPECIFIED\t"+model_id+"\t"+go_cam.name+"\t"+entity_id+"\t"+entity_name+"\t"+complex_entity_id+"\t"+controller_entity.getDisplayName()+"\t"+active_site_id+"\t"+active_site.getDisplayName());
										}
										continue;
									}
									// No annotated active site: decide the enabler by flattening the complex
									// hierarchy to its distinct UniProt protein subunits (+ EntitySet subunits).
									FlattenedComplex flat = collectFlattenedComplexLeaves((Complex) controller_entity);
									if (flat.isEmpty()) {
										// No usable protein anywhere -> drop the enabler entirely.
										drop_controller_entities.add((PhysicalEntity) controller_entity);
										System.out.println("COMPLEX_FLATTEN_NO_PROTEIN\t"+model_id+"\t"+go_cam.name+"\t"+entity_id+"\t"+entity_name+"\t"+complex_entity_id+"\t"+controller_entity.getDisplayName());
									} else if (flat.proteinsByUniprot.size() == 1 && flat.setLeaves.isEmpty()) {
										// Exactly one distinct protein -> enable_by it directly (no PCC).
										PhysicalEntity single = flat.proteinsByUniprot.values().iterator().next();
										active_sites.add(single);
										System.out.println("COMPLEX_FLATTENED_TO_SINGLE_PROTEIN\t"+model_id+"\t"+go_cam.name+"\t"+entity_id+"\t"+entity_name+"\t"+complex_entity_id+"\t"+controller_entity.getDisplayName()+"\t"+getEntityReferenceId(single));
									} else {
										// Two or more distinct subunits -> flat PCC (active_sites left empty).
										System.out.println("COMPLEX_FLATTENED_TO_PCC\t"+model_id+"\t"+go_cam.name+"\t"+entity_id+"\t"+entity_name+"\t"+complex_entity_id+"\t"+controller_entity.getDisplayName()+"\t"+flat.totalLeaves());
									}
								}
```
Note: this removes the only external caller of `complexHasProtein`; it becomes an unused private method (a compiler warning, not an error — leave it; `getComplexActiveUnitRecursive` is still used by `resolveSetCatalystMembers`/`getActiveSites`).

- [ ] **Step 5: Add the drop-skip in the controller-emission loop**

In `BioPaxtoGO.java`, find the line that begins the non-recursive controller emission (~line 1827):
```java
							//this is the non-recursive part.. (and we usually aren't recursing anyway)
							IRI iri = null;
```
Insert immediately BEFORE that comment:
```java
							if (drop_controller_entities.contains(controller_entity)) {
								System.out.println("DROP_FLATTENED_ENABLER_NO_PROTEIN\t"+entity_id+"\tcontroller_entity="+getEntityReferenceId(controller_entity));
								continue;
							}
```

- [ ] **Step 6: Enable the residual-node deletion**

In `BioPaxtoGO.java` (~lines 1883–1886), replace the commented block:
```java
						//per discussion in pathways2GO/issues/91 removing the connection to the and the complex individual
						//if(active_units!=null) {
						//	go_cam.deleteOwlEntityAndAllReferencesToIt(controller_e);
						//}
```
With:
```java
						//per discussion in pathways2GO/issues/91 removing the connection to the complex individual:
						//when the enabler resolved to a specific protein active unit, the complex node is
						//vestigial (only the target of a has_part edge), so delete it.
						if(active_units!=null) {
							go_cam.deleteOwlEntityAndAllReferencesToIt(controller_e);
						}
```

- [ ] **Step 7: Run the new test to verify it passes**

```bash
cd exchange && mvn -Dtest=BioPaxtoGOTest#testComplexEnablerSingleProteinNoResidualNode test
```
Expected: PASS. Exactly one `enabled_by` to FECH P22830; the `R-HSA-189402_R-HSA-189465_controller` node has zero triples.

- [ ] **Step 8: Confirm related enabler/PCC/active-site tests still pass**

```bash
cd exchange && mvn -Dtest=BioPaxtoGOTest#testComplexEnablerFlattenedToPCC+testComplexCofactorReducedToSingleProtein+testActiveSiteInController test
```
Expected: PASS for all three. (`testActiveSiteInController` still finds exactly one `enabled_by` active part; deleting the complex node does not touch that edge.)

**Coverage note (drop case):** the test corpus has no Catalysis complex enabler that is entirely protein-less (`flat.isEmpty()`), so there is no failing-test driver for the `COMPLEX_FLATTEN_NO_PROTEIN` / drop-skip branch. Its correctness rests on code review and the invariant that an empty PCC is never emitted; adding a synthetic fixture is out of scope.

---

### Task 3: Skip non-small-molecule regulators at emission

**Files:**
- Modify: `exchange/src/main/java/org/geneontology/gocam/exchange/BioPaxtoGO.java` (add the skip at the top of the controller-emission loop ~1758)
- Test: `exchange/src/test/java/org/geneontology/gocam/exchange/BioPaxtoGOTest.java` (reuse the existing `testComplexRegulatorLeavesNoOrphanComponents` and `testInferSmallMoleculeRegulators`)

**Interfaces:**
- Consumes: existing `is_catalysis` (in scope at the loop), `isSmallMoleculeEquivalent(PhysicalEntity)`.
- Produces: no new public surface.

- [ ] **Step 1: Add the skip at the top of the controller-emission loop**

In `BioPaxtoGO.java`, find the start of the loop body (~line 1757):
```java
						for(Controller controller_entity : controller_entities) {
							//if the controller is produced by a reaction in another pathway, then we may want to bring that reaction into this model
```
Insert the skip as the FIRST statement in the loop body (immediately after the `for(...) {` line, before the comment):
```java
						for(Controller controller_entity : controller_entities) {
							// Only small-molecule regulators are retained (rewritten to has_small_molecule_*
							// by inferSmallMoleculeRegulators). Protein/Complex/Set/nucleic-acid regulators
							// are skipped at emission rather than emitted-then-deleted.
							boolean small_mol_regulator = (controller_entity instanceof PhysicalEntity)
								&& isSmallMoleculeEquivalent((PhysicalEntity) controller_entity);
							if(!is_catalysis && !small_mol_regulator) {
								System.out.println("SKIP_NON_SMALL_MOL_REGULATOR\t"+entity_id+"\tcontroller_entity="+getEntityReferenceId(controller_entity));
								continue;
							}
							//if the controller is produced by a reaction in another pathway, then we may want to bring that reaction into this model
```

- [ ] **Step 2: Compile to verify the edit is well-formed**

```bash
cd exchange && mvn -q compile
```
Expected: BUILD SUCCESS.

- [ ] **Step 3: Run the regulator tests to verify behavior**

```bash
cd exchange && mvn -Dtest=BioPaxtoGOTest#testComplexRegulatorLeavesNoOrphanComponents+testInferSmallMoleculeRegulators test
```
Expected: PASS for both.
- `testComplexRegulatorLeavesNoOrphanComponents`: the ALAD complex regulator is now never emitted, so its component IRI (`UniProtKB_P13716_R-HSA-190145_R-HSA-189439_component`) has 0 triples and the no-orphan invariant holds.
- `testInferSmallMoleculeRegulators`: reaction R-HSA-71670 still has its 4 small-molecule regulators (the skip is gated on `!isSmallMoleculeEquivalent`, so small-molecule regulators are untouched) — guards against over-skipping.

---

### Task 4: Full-suite verification

**Files:** none (verification only).

- [ ] **Step 1: Run the complete BioPaxtoGO test class**

```bash
cd exchange && mvn -Dtest=BioPaxtoGOTest test
```
Expected: the two new tests (`testComplexEnablerFlattenedToPCC`, `testComplexEnablerSingleProteinNoResidualNode`) pass and all previously-passing tests still pass. `@Ignore`-annotated tests stay skipped.

- [ ] **Step 2: Report results**

Summarize: confirm both new tests passed and report the full-suite pass/skip/fail counts from the Maven `Tests run:` line. Do not claim success without the actual Maven output. Do NOT commit — leave the working tree for the user to review and commit.

---

## Self-Review

**Spec coverage:**
- §1 collector (`collectFlattenedComplexLeaves` + `FlattenedComplex`) → Task 1 Step 4.
- §2 flat PCC emission → Task 1 Step 5; verified by `testComplexEnablerFlattenedToPCC`.
- §3 enabler decision (drop / single-protein / PCC, dedup-by-UniProt incl. homodimer) → Task 2 Step 4.
- §4 residual-node deletion → Task 2 Step 6; verified by `testComplexEnablerSingleProteinNoResidualNode`.
- §5 drop handling → Task 2 Steps 3 (declare) + 5 (skip); coverage note records the absence of a drop fixture.
- §6 non-small-molecule regulator skip → Task 3 Step 1; verified by the two existing regulator tests.
- Enabler-only scope / inputs-outputs untouched → no edits to the `explode=false` call sites; `getComplexActiveUnitRecursive` retained for non-enabler callers (Task 2 note).

**Placeholder scan:** No TBD/TODO/"handle edge cases"/"write tests for the above". Every code step shows full code; every command shows expected output. The drop case has an explicit coverage note rather than a placeholder.

**Type consistency:** `FlattenedComplex` fields (`proteinsByUniprot : Map<String,Protein>`, `setLeaves : Set<PhysicalEntity>`, `totalLeaves()`, `isEmpty()`) are defined in Task 1 Step 4 and used identically in Task 1 Step 5 and Task 2 Step 4. `collectFlattenedComplexLeaves(Complex)` signature is consistent across uses. `drop_controller_entities : Set<PhysicalEntity>` is declared (Task 2 Step 3) and read via `.contains(controller_entity)` (Task 2 Step 5) — `Set.contains(Object)` accepts the `Controller`-typed variable, matching on object identity. Test helper `countSolutions(String)` matches the existing signature. Type IRIs (`http://identifiers.org/uniprot/P21912` etc., `...reacto.owl#REACTO_R-ALL-164296`, `obo:GO_0032991`, `obo:RO_0002333`, `obo:BFO_0000051`) match the conventions used by the existing R-HSA-189451 tests and the verified fixture identifiers.
