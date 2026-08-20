# Fix: orphan component individuals left when a Complex/Set regulator is dropped

Date: 2026-06-29
Target file: `exchange/src/test/resources/biopax/R-HSA-189451_level3.owl`
Symptom: orphan individual `gomodel:UniProtKB_P13716_R-HSA-190145_R-HSA-189439_component` (ALAD) in the generated GO-CAM.

## 1. Confirmation of the assumption (root cause)

The user's assumption is **confirmed in substance, with one correction**.

### Evidence gathered

BioPAX (`R-HSA-189451_level3.owl`):
- `Complex5` = `R-HSA-190145`, displayName "8xALAD:Pb2+:Zn2+", components = `Protein3` (ALAD / UniProt P13716) + `SmallMolecule9` + `SmallMolecule10` (lines 959–967).
- `Control2` (lines 1246–1252): `controlType = INHIBITION`, `controller = #Complex5`, `controlled = #BiochemicalReaction4`.
- `BiochemicalReaction4` (decl. line 1048) has Reactome id **189439** (`R-HSA-189439`).

So: a **Complex** (`R-HSA-190145`) is an INHIBITION regulator of reaction `R-HSA-189439`. ✔ matches the user's description.

Generated GO-CAM (`exchange/src/test/resources/gocam/R-HSA-189451_level3-R-HSA-189451.ttl`):
- The orphan `UniProtKB_P13716_R-HSA-190145_R-HSA-189439_component` (line 6929) carries only `rdf:type` (`owl:NamedIndividual`, `uniprot:P13716`) + annotation triples (label "ALAD", comments, exactMatch → `Protein3`). It appears **only as a subject** — no incoming or outgoing object-property edges → true orphan.
- The complex-as-regulator individual (`R-HSA-190145_R-HSA-189439`) is **absent** — it was deleted. (The only surviving `R-HSA-190145` individual is `R-HSA-190145_R-HSA-190141`, the complex as an *output* participant of a different reaction R-HSA-190141 — unrelated.)
- No `involved_in_*_regulation_of` and no `has_small_molecule_*` edges remain for this reaction.

### Mechanism (trace)

1. First layer — `BioPaxtoGO.defineReactionEntity()` (`BioPaxtoGO.java:1281–1350`): when a Complex is a controller/regulator (`explode_sets_complexes == true`), the complex is instantiated (`R-HSA-190145_R-HSA-189439`), typed `GO:0032991` (protein-containing complex), linked to the reaction by `involved_in_negative_regulation_of` (RO_0002430, from the INHIBITION Control), and its **non-small-molecule components are exploded** into separate individuals (`UniProtKB_P13716_..._component`) linked by `has_part` (BFO_0000051). Small-molecule components are skipped (line 1294).

2. Gate — `GoCAM.inferSmallMoleculeRegulators()` (`GoCAM.java:1669–1749`): for each regulator it computes `entity_types = superclasses(regulator rdf:type) ∪ {rdf:type}` and tests
   `entity_types.contains(CHEBI_24431) && !entity_types.contains(CHEBI_33696)` (line 1715).
   The complex's type `GO:0032991` is **not** a CHEBI chemical, so it correctly enters the **else branch** (line 1733): logs `DELETING_NON_SMALL_MOL_REGULATOR` and calls `deleteOwlEntityAndAllReferencesToIt(regulator)` (line 1740).

3. Deletion — `GoCAM.deleteOwlEntityAndAllReferencesToIt(e, delete_related_nodes=false)` (`GoCAM.java:880–930`): removes the regulator's annotation axioms and **every axiom referencing the regulator** (the `has_part` edge to the component, the `involved_in_negative_regulation_of` edge to the reaction). Because `delete_related_nodes == false`, it does **not** recurse into the now-disconnected component individual. The protein component survives with only type/annotation triples → **orphan**.

### Correction to the stated assumption

The gate is **not** "failing to catch" the complex. It *does* catch it (the CHEBI test is false for a `GO:0032991` complex) and *does* delete the complex node. The orphan arises because the deletion is **shallow**: the complex's exploded `has_part` component children are left behind. (Note: even calling the helper with `delete_related_nodes=true` would NOT fix it — that branch only recurses through `has_input` / `has_output` / `enabled_by` / `occurs_in` at `GoCAM.java:916`, never `has_part` / `has_substitutable_entity`. Also, `delete_related_nodes=true` is currently never passed by any caller.)

The gate **condition** itself is already effectively "is a SmallMolecule" — complexes (`GO:0032991`), proteins (UniProt/PR classes) and sets do not have `CHEBI_24431` as a superclass, so they all fall to the else branch. So no change to the *condition* is required; the bug is purely the incomplete deletion of exploded children.

## 2. Fix plan

Goal: when a non-small-molecule regulator (Complex, Set, protein, etc.) is dropped by the gate, it must be **fully** removed — including the component/member individuals that were exploded out of it — so no orphans remain.

### Recommended approach (Option 1 — targeted cleanup in the gate)

In the else branch of `inferSmallMoleculeRegulators` (`GoCAM.java:1733–1741`), before deleting the regulator, collect and delete its exploded children:

- Gather objects of `regulator has_part ?child` (BFO_0000051) and `regulator has_substitutable_entity ?child` (RO_0019003) — these are the complex components / set members created at `BioPaxtoGO.java:1308` and `:1345`.
- Delete each child via `deleteOwlEntityAndAllReferencesToIt(child)`, recursing for nested complexes (a `has_part` child that is itself a complex has its own `has_part` children). A child that is itself a complex will, in turn, have its components deleted.
- Then delete the regulator itself (existing call).

Implement as a small private helper, e.g. `deleteRegulatorAndComponents(OWLNamedIndividual regulator)`, that walks `has_part` / `has_substitutable_entity` from the regulator and deletes leaves first, then the regulator. Use `EntitySearcher.getObjectPropertyValues(regulator, has_part, go_cam_ont)` (and `has_substitutable_entity`) to find children — this reads outgoing edges only, so it cannot accidentally walk *up* to the reaction or to shared participants.

Safety: the component IRIs are scoped `<component>_<parentComplex>_<reaction>_component`, i.e. unique to this complex-in-this-reaction (verified: the orphan appears exactly once in the output). Deleting them cannot affect the complex instance used as a participant elsewhere (`R-HSA-190145_R-HSA-190141`), which is a distinct individual.

### Alternative (Option 2 — extend the shared helper)

Add `has_part` and `has_substitutable_entity` to the recursive branch in `deleteOwlEntityAndAllReferencesToIt` (`GoCAM.java:914–924`) and call it with `delete_related_nodes=true` from the else branch. Lower code volume and reuses existing machinery, but changes the semantics of a shared helper (currently `delete_related_nodes=true` is dead — no caller uses it). Acceptable, but Option 1 keeps the blast radius inside the gate. **Prefer Option 1.**

### Why not fix it upstream (don't explode regulator complexes)?

The first-layer explosion at `BioPaxtoGO.java:1287` is shared between *enablers* (where active-unit extraction needs the components) and *regulators*. Distinguishing "regulator-only" at that point is harder and riskier than cleaning up at the gate, which is the single place the small-molecule-vs-not decision is made. Keep the fix at the gate.

## 3. Test plan (write the failing test first — TDD)

Test harness: `BioPaxtoGOTest` converts the sample BioPAX in `@BeforeClass` (`bp2g.convert(...)`) and loads results into Blazegraph (`blaze`); tests run SPARQL via `blaze.runSparqlQuery(...)` / `countSolutions(...)`. R-HSA-189451 is already converted by the suite.

Add a regression test (mirroring `testReusedSmallMoleculeInputIsTyped`, `BioPaxtoGOTest.java:1198`):

1. **Specific (reproduces the bug, must fail before the fix):** assert the orphan is gone —
   `ASK`/count: no triples where subject = `gomodel:UniProtKB_P13716_R-HSA-190145_R-HSA-189439_component`. Expect 0.

2. **Invariant (generalizes):** no individual in graph `<.../R-HSA-189451>` is a bare orphan — i.e. there is no `?i` that has an `rdf:type` other than `owl:NamedIndividual` but participates in **no** object-property assertion as subject or object (excluding annotation/type triples). Expect 0. Scope carefully so legitimately-standalone nodes (if any) are not flagged; if needed, narrow to "individuals typed with a UniProt/CHEBI/REACTO class that have zero object-property edges".

3. **Precondition guard:** assert reaction `R-HSA-189439` is present (so the test can't pass vacuously on a wrong graph IRI), as done at `BioPaxtoGOTest.java:1205`.

Then implement the fix and confirm the new test passes and the existing suite (esp. `testReusedSmallMoleculeInputIsTyped`, `convertEntityRegulatorsToBindingFunctions` tests) still passes.

## 4. Files to touch

- `exchange/src/main/java/org/geneontology/gocam/exchange/GoCAM.java` — `inferSmallMoleculeRegulators` else branch (~line 1733–1741); add helper.
- `exchange/src/test/java/org/geneontology/gocam/exchange/BioPaxtoGOTest.java` — new regression test.
- (Regenerated) `exchange/src/test/resources/gocam/R-HSA-189451_level3-R-HSA-189451.ttl` will lose the orphan after the fix.

## 5. Verification

```bash
cd exchange && mvn -Dtest=BioPaxtoGOTest test
```
Plus a focused grep on the regenerated TTL confirming `UniProtKB_P13716_R-HSA-190145_R-HSA-189439_component` is absent, and that `R-HSA-190145_R-HSA-190141` (the legitimate participant) is still present.
