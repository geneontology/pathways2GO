# Explode an annotated active unit that is an EntitySet into reaction diamonds — Design

**Date:** 2026-07-02
**Status:** Approved direction (user requested spec + implement); ready for planning.
**Component:** `exchange/` — BioPAX → GO-CAM (`BioPaxtoGO.defineReactionEntity`).
**Builds on:** `2026-06-03-split-set-enabled-reactions-design.md` (bare-set diamond split),
`2026-07-01-expand-set-in-controller-complex-design.md` (complex-with-set expansion),
`2026-07-01-eliminate-reacto-ids-design.md` (REACTO elimination that currently deletes these nodes).

## 1. Problem (root cause established)

When a Catalysis carries an `activeUnit:` annotation and that active unit **is (or resolves to) an
EntitySet**, the reaction is emitted with a single REACTO-classed active-unit node instead of being split
into one diamond per set member — even when the members are ordinary UniProt proteins.

**Driving example** (fixture `exchange/src/test/resources/biopax/R-HSA-110362_level3.owl`):
- `Catalysis2`: controller `#Complex4` (R-HSA-5649888), controlled `#BiochemicalReaction3` (R-HSA-5651723),
  comment **`activeUnit: #Complex2`**.
- `#Complex2` (R-HSA-5649884, "PARP1,PARP2 dimers") → sole component `#Protein3`.
- `#Protein3` (R-HSA-**5649876**, "PARP1,PARP2") = a Reactome EntitySet (`memberPhysicalEntity` = PARP1
  `#Protein4`, PARP2 `#Protein5`), each a UniProt-backed protein.

**Why it happens** (all in `BioPaxtoGO.defineReactionEntity`):
1. `getActiveSites` (`:~2470`) reads `activeUnit: #Complex2`; because `#Complex2` is a `Complex`, it calls
   `getComplexActiveUnitRecursive`, reducing it to its active unit `#Protein3` (the set). So
   `active_sites = { R-HSA-5649876 }` — **non-empty**.
2. The complex-with-set expansion branch (`:1854`) is gated `active_sites.isEmpty()`, so it is **skipped**
   (spec §4.5 "annotated active unit wins"). Absent the annotation, that branch would have expanded the
   controller (combos=2 ≤ cap) into the two diamonds.
3. Emission runs the **active-unit branch** (`:1937`–`:1963`): the active unit's stable id `R-HSA-5649876`
   is not `UniProt_…`, so it is typed with a **REACTO class** (`:1950`); the enabler individual IRI is
   `entity_id + "_" + active_site_stable_id` = `R-HSA-5651723_R-HSA-5649876` (`:1954`) — the exact node we
   observed. `defineReactionEntity(activeUnit, …, explode=true)` (`:1956`) is called, but **set explosion
   is hard-disabled** (`explode_sets = false`, `:1299`). So the set is emitted as one REACTO node and
   never split.

The REACTO-elimination pass then deletes this node, leaving the reaction unenabled — losing information a
clean diamond split would have preserved.

## 2. Fix

In the active-unit loop (`for(PhysicalEntity active_site_entity : active_sites)`, `:1940`), add a branch
at the top: **when the Catalysis's active unit is an explodable EntitySet, explode it into one
`enabled_by` per resolved member and mark the reaction for the existing Phase-2 diamond split**, instead
of building the single REACTO active-unit node. This mirrors the existing bare-set catalyst branch
(`:1833`–`:1848`) exactly, reusing the same member resolution, IRI scheme, and `set_enabled_reaction_iris`
machinery, so `GoCAM.splitSetEnabledReactions` produces the diamonds with no change to Phase 2.

### 2.1 Small refactor (DRY)

Factor the member test out of `isExplodableEntitySet(Controller)` (`:1138`) into a `PhysicalEntity`-level
helper, and have the existing method delegate:

```java
boolean isExplodablePhysicalEntitySet(PhysicalEntity pe) {
    if (pe instanceof Complex) return false;                 // a Complex is not a bare set
    Set<PhysicalEntity> members = pe.getMemberPhysicalEntity();
    if (members == null || members.isEmpty()) return false;  // not a set
    if (setIsSmallMoleculesOnly(members)) return false;       // small-molecule-only sets are not exploded
    return true;
}

boolean isExplodableEntitySet(Controller controller_entity) {
    if (!(controller_entity instanceof PhysicalEntity)) return false;
    return isExplodablePhysicalEntitySet((PhysicalEntity) controller_entity);
}
```

### 2.2 New branch in the active-unit loop (`:1940`, first statement inside the `for`)

```java
// If the annotated active unit is itself an explodable EntitySet, explode it into one enabler per
// member and mark the reaction for the Phase-2 diamond split (splitSetEnabledReactions), instead of
// emitting a single REACTO-classed active-unit node. Mirrors the bare-set catalyst branch (:1833).
// Catalysis only: regulator active units keep current behavior (handled by the regulator pipeline).
if (is_catalysis && isExplodablePhysicalEntitySet(active_site_entity)) {
    List<PhysicalEntity> resolved_members = resolveSetCatalystMembers(active_site_entity);
    if (!resolved_members.isEmpty()) {
        StringBuilder member_ids = new StringBuilder();
        for (PhysicalEntity member : resolved_members) {
            String member_id = getEntityReferenceId(member);
            IRI member_iri = GoCAM.makeGoCamifiedIRI(null, member_id + "_" + entity_id + "_controller");
            OWLNamedIndividual member_e = go_cam.df.getOWLNamedIndividual(member_iri);
            defineReactionEntity(go_cam, member, member_iri, true, model_id, root_pathway_iri, reaction_id, true);
            go_cam.addRefBackedObjectPropertyAssertion(e, GoCAM.enabled_by, member_e, dbids, GoCAM.eco_imported_auto, default_namespace_prefix, null, model_id);
            member_ids.append(member_id).append(",");
        }
        go_cam.set_enabled_reaction_iris.add(e.getIRI());
        System.out.println("ACTIVE_UNIT_SET_EXPANDED\t" + model_id + "\t" + go_cam.name + "\t" + entity_id + "\t" + getEntityReferenceId(active_site_entity) + "\t" + resolved_members.size() + "\t" + member_ids.toString());
        continue;   // do NOT build the REACTO active-unit node for this active site
    }
    // no resolvable members -> fall through to the existing REACTO active-unit node
}
```

**Control-flow consequences (verified against the code):**
- `active_units` is initialized to an empty `HashSet` whenever `active_sites` is non-empty (`:1938`). If all
  active sites are exploded sets, `active_units` stays **empty (not null)**, so the post-loop
  enabled_by emission `if(active_units!=null){ for(active_unit …) … }` (`:1981`–`:1988`) emits nothing (no
  double edge), and the `else` branch (`controller_e enabled_by`, `:1991`) is **not** taken. The member
  `enabled_by` edges added inside the branch are the only enabler edges.
- `controller_e` (the controller complex node, created at `:1918`) is left with no edges when the active
  unit is exploded (we skip the `controller_e has_part active_i` at `:1961`). It is a bare, untyped
  individual and is removed by the existing `cleanOutUnconnectedNodes()`. (When only *some* active sites
  are sets, the non-set ones still add `has_part`, and `deleteComplexesWithActiveUnits` removes it as
  today.)
- Mixed active-site sets (rare): each set active site is exploded independently; non-set active sites use
  the existing REACTO/UniProt active-unit path. All `enabled_by` targets fan out together in Phase 2
  (`set_enabled_reaction_iris` is a set; ≥2 enablers triggers the split).

## 3. Decisions (resolved)

1. **Mirror the bare-set branch; no new cap.** An annotated active-unit set is a direct OR of members
   (typically 2–a few); `resolveSetCatalystMembers` already dedups and is cycle-guarded and flattens
   nested members exactly as the bare-set branch does. No `SET_COMBINATION_CAP` gate (that cap governs the
   *cross-product* of a whole complex; an active-unit set has no cross-product).
2. **Catalysis only.** Gate on `is_catalysis`. Regulator active-unit sets keep current behavior and are
   handled by the existing regulator pipeline (non-small-molecule regulators are skipped/deleted).
3. **Reuse member typing.** `defineReactionEntity(member, …, explode=true)` types a UniProt-backed protein
   member with its UniProt class (non-REACTO) — which is what resolves the REACTO problem. A member that is
   itself a complex/nested set is handled identically to the bare-set branch (same helper).
4. **Deterministic, split-compatible IRIs.** Member enabler IRI = `{member_id}_{entity_id}_controller`
   (identical to the bare-set branch), so `splitSetEnabledReactions` names diamonds
   `{reaction_id}_enabled_by_{member_iri_localname}` as it already does for bare-set reactions.

## 4. Interaction with `testInferRegulatesViaOutputEnables` (IMPORTANT)

The driving reaction `R-HSA-5651723` is the **same `reaction2`** used by the regression anchor
`testInferRegulatesViaOutputEnables` (pathway R-HSA-110362; `reaction1 = R-HSA-5649883`). Today that test
(as amended by the REACTO-elimination work) asserts `reaction1 RO_0002629 R-HSA-5651723` and
`reaction1 BFO_0000050 pathway`.

After this fix, Phase 2 **deletes the original `R-HSA-5651723` node** and replaces it with two clones
(`R-HSA-5651723_enabled_by_…PARP1…`, `…PARP2…`). `splitSetEnabledReactions` runs in `wrapAndWrite`
**before** `applySparqlRules`, so the Rule-3 regulation inference (`inferRegulatesViaOutputEnables`) then
matches against the **clones**, not the deleted original. The test's `VALUES ?reaction2 { R-HSA-5651723 }`
will therefore no longer match → the assertion must be updated.

**Plan:** implement the fix, then **empirically determine** (from the regenerated model) the post-split
shape of the regulation relation, and update `testInferRegulatesViaOutputEnables` to assert it against the
diamond clones (e.g. `reaction1 RO_0002629 ?c` where `?c` is a `R-HSA-5651723_enabled_by_*` clone that is
`enabled_by` a UniProt PARP protein), keeping the load-bearing meaning (reaction1 positively regulates the
downstream PARP autoPARylation activity, in pathway R-HSA-110362). If the inference does **not** survive
the split at all, that is a finding to escalate (it would mean the diamond split breaks the Rule-3 pattern),
not something to paper over. Do not weaken the test to vacuity.

## 5. Testing

**New integration test** (`BioPaxtoGOTest`, REACTO, fixture `R-HSA-110362_level3.owl` already present):
`testActiveUnitSetExpandedToDiamonds` — after conversion of model `R-HSA-110362`:
- **Split happened:** ≥2 activities exist whose IRI starts with
  `http://model.geneontology.org/R-HSA-5651723_enabled_by_` and each carries the reaction's GO MF type
  (`obo:GO_0003950`), and the original bare `R-HSA-5651723` activity is absent.
- **Enablers are the UniProt PARP proteins, not REACTO:** each diamond is `enabled_by` an individual typed
  with a UniProt class (PARP1 and PARP2 accessions), and **no** individual is typed
  `reacto.owl#REACTO_R-HSA-5649876`.
- (The general "no REACTO IRIs" invariant is already covered by `testNoReactoIdsInEmittedModel` on a
  different model.)

**Amended anchor:** `testInferRegulatesViaOutputEnables` updated per §4 (evidence-driven).

**Regression anchors that must stay green:** `testSetEnabledReactionSplit`,
`testComplexEnablerFlattenedToPCC`, `testComplexEnablerSingleProteinNoResidualNode`,
`testOverCapComplexDropsReactoSetNode`, `testInferSmallMoleculeRegulators`,
`testComplexRegulatorLeavesNoOrphanComponents`, `testActiveSiteInController` (a **non-set** annotated
active unit must still use the single-protein active-unit path), `testNoReactoIdsInEmittedModel`,
`testReusedSmallMoleculeInputIsTyped`, `testCausalPathBridging`.

Run the full class (shared heavy `@BeforeClass`): `cd exchange && mvn -Dtest=BioPaxtoGOTest test`; plus
`SetEnabledReactionSplitTest` and `QRunnerPartToComplexIndexTest`.

## 6. Out of scope

- Regulator (non-Catalysis) active-unit sets (handled by the regulator pipeline).
- Changing `getActiveSites` / `getComplexActiveUnitRecursive`, `splitSetEnabledReactions`,
  `cloneIndividualSharingNeighbors`, or the complex-with-set expansion.
- Active units that resolve to a single protein or to a complex (unchanged behavior).
- Adding a cap for active-unit sets.

## 7. Scale / risk

- Affects Catalyses whose annotated active unit resolves to an EntitySet — a subset of the 859
  activeUnit-annotated complex Catalyses. Per-reaction growth is bounded by member count (small); the
  Phase-2 clone/evidence growth matches the existing bare-set split.
- Whole-class + neighbor test runs are the safety net for cross-model regressions.

## 8. Key code anchors

- `BioPaxtoGO.isExplodableEntitySet` `:1138`; `resolveSetCatalystMembers` `:1164`; bare-set branch
  `:1833`–`:1848`; complex-with-set branch `:1854`–`:1908`; active-unit loop `:1937`–`:1963`;
  post-loop enabled_by `:1979`–`:1992`; `explode_sets=false` `:1299`; `getActiveSites` (reads
  `activeUnit:` comment, reduces a Complex active unit via `getComplexActiveUnitRecursive`).
- `GoCAM.splitSetEnabledReactions` (clones `{reaction_id}_enabled_by_{member}`; deletes original);
  called from `BioPaxtoGO.wrapAndWrite:554` before `applySparqlRules`.
