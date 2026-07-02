# Spec: Expand EntitySet members inside controller complexes into reaction diamonds

- **Date:** 2026-07-01
- **Status:** Approved (design); ready for implementation planning
- **Component:** `exchange/` — BioPAX → GO-CAM conversion (`BioPaxtoGO`)
- **Driving example:** pathway containing reaction `R-HSA-5694421` ("PP6 dephosphorylates SEC24"), Catalysis controller complex **PP6** (`R-HSA-5694294`), from `biopax_97/Homo_sapiens.owl`
- **Builds on:** `2026-06-03-split-set-enabled-reactions-design.md` (the reaction-split/"diamond" machinery), `2026-06-30-flatten-complex-enabler-design.md` (complex flattening), `2026-06-29-skip-complex-set-regulators` (regulators skipped at emission)

## 1. Problem

The existing split-set feature turns a Catalysis controller that **is** an EntitySet into one activity per member — a "reaction diamond" (one upstream reaction → parallel per-member activities → one downstream reaction). It only fires when the controller is a bare set (`isExplodableEntitySet`).

When the Catalysis controller is a **Complex that contains an EntitySet component** (possibly nested), today's flatten path (`collectFlattenedComplexLeaves`) keeps each set as a **single `has_part` node typed with the set's REACTO union class** (`setLeaves`) inside one flattened protein-containing complex (PCC). The biological "one-of" is collapsed into an opaque REACTO-classed node, and no diamond is produced.

We want: **one diamond per combination of set members**, each enabled by a flattened complex individual for that specific member choice — generalizing the bare-set split from "controller *is* a set" to "controller *contains* set(s), possibly nested."

## 2. Driving example (expected output)

**Only Catalyses with NO `activeUnit:` annotation expand** — an annotated active site names the catalytic subunit and wins (§4.5). (The originally-chosen PP6 example, `R-HSA-5694421`, turned out to carry `activeUnit: #Protein205` → PPP6C, so it is *not* expanded; it serves as the negative guard.)

Valid driving example — `R-HSA-6814833` is catalyzed by complex **RAB1:GTP:TBC1D20** (`R-HSA-6814832`, no activeUnit):

```
RAB1:GTP:TBC1D20  (Complex)                 combos = 2
├── RAB1:GTP  (Complex)
│   ├── GTP   (SmallMolecule — stripped)
│   └── RAB1  (SET: RAB1B | RAB1A)
└── TBC1D20   (Protein, fixed subunit)
```

**Expected:** the single `R-HSA-6814833` activity is replaced by **2** activities (diamond), each of the reaction's MF type, sharing the same inputs/outputs/pathway/causal edges, each `enabled_by` a distinct flattened PCC:

- PCC₁ `has_part` → {TBC1D20 (Q96BZ9), RAB1B (Q9H0U4)}
- PCC₂ `has_part` → {TBC1D20 (Q96BZ9), RAB1A (P62820)}

Each PCC is typed `GO:0032991`; its `has_part` protein subunits are UniProt-typed; GTP is stripped. No REACTO union-class node appears.

## 3. Scale finding (why a cap is mandatory)

Streaming analysis of `biopax_97/Homo_sapiens.owl`, restricted to **Catalyses whose controller is a true Complex (has `component`s) containing a set component, and which have NO `activeUnit:` annotation** (i.e. the actual expansion-eligible set):

- **1,355** such Catalyses total; **859 are `activeUnit`-suppressed** (an annotated active site wins → no expansion); **496 are eligible** (no activeUnit).
- The combination count is recursive: `combos(Complex) = ∏ combos(component)`; `combos(Set) = Σ combos(member)`; leaf = 1; small-molecule leaves stripped.
- Of the 496 eligible, **475 have `combos > 1`**, and their counts range from 2 to **80,028** — a heavy tail (e.g. COPII `R-HSA-203973` = 1,536). A full cross-product would create hundreds of thousands of diamonds in a single model, so a per-reaction cap is mandatory.

**Decision — combination cap = 10** (resolved with user):

| Path | Condition | Behavior |
|---|---|---|
| **Expand** | `2 ≤ combos ≤ 10` | Enumerate combinations → one flattened enabler + one diamond per distinct combination (this feature). **~297** eligible human reactions. |
| **Fallback** | `combos > 10` | Flatten to one PCC with `has_part` **only its concrete protein subunits**; the set's REACTO union-class node is **not emitted**. No split. **~178** eligible human reactions. |
| **Unchanged** | activeUnit present, `combos == 1`, or controller is itself a set | Existing behavior (active site / single protein / PCC). |

The cap gates on the **raw structural** count (a cheap arithmetic upper bound); emitted diamonds are deduped by protein-set, so the actual output for an expanded reaction is ≤ combos.

## 4. Decisions (resolved)

1. **Cap = 10** on the raw combination count; short-circuit the count as soon as it passes the cap.
2. **Each expanded combination is flattened** (user decision "Flatten each combination"): a combination that reduces to exactly **one** distinct protein is `enabled_by` that protein directly (no PCC); a combination with **≥2** distinct proteins becomes a flattened PCC (`GO:0032991`) `has_part` its UniProt protein subunits; sub-complexes chosen within a combination are flattened to their proteins. Combinations are **deduped by their protein key-set**.
3. **Over-cap fallback deletes the REACTO-classed set node** (user decision): the flattened PCC keeps only concrete protein subunits; the set union-class node is dropped and **logged** (`DROPPED_REACTO_SET_NODE`, §6.3) so removed REACTO individuals stay traceable. If no protein subunit remains (set-only complex over cap), the enabler is dropped entirely. This aligns with the project's goal of removing REACTO IDs.
4. **Catalysis only.** Non-small-molecule regulators are already skipped at emission (`2026-06-29-skip-complex-set-regulators`), so regulator complexes never reach this branch. No cross-product on regulators.
5. **Annotated active sites still win.** If the Catalysis has an `activeUnit:` comment (`getActiveSites` non-empty), no expansion occurs — existing behavior. This is the dominant suppressor (859 of 1,355).
6. **Only true complexes expand (Guard A).** The controller must be a `bp:Complex` with `component`s and **empty** `memberPhysicalEntity`. A `bp:Complex` that is itself an EntitySet (carries `memberPhysicalEntity`, e.g. a pyruvate-kinase-tetramer set) keeps its existing behavior — expanding it would delete the reaction node and break `testInferSmallMoleculeRegulators`.
7. **Drop-through on zero proteins (Guard B).** Record the reaction and `continue` only when at least one enabler combination was built (`distinct ≥ 1`); if the complex yields no protein combinations, fall through to the normal path (log `SET_IN_COMPLEX_NO_PROTEINS`).

## 5. Current behavior (baseline) — code anchors

- Controller-emission is `BioPaxtoGO.defineReactionEntity(...)`. Two loops over `controller_entities`:
  - **Loop 1** (`:1715`–`:1743`): flatten *decision* for a Complex Catalysis controller with no annotated active site. `collectFlattenedComplexLeaves` (`:2136`) → `FlattenedComplex{proteinsByKey, setLeaves}` (`:2120`). `flat.isEmpty()` → `drop_controller_entities`; `proteinsByKey.size()==1 && setLeaves.isEmpty()` → single-protein `active_sites`; else → flat PCC.
  - **Loop 2** (`:1769`–…): emission. Bare-set-catalyst branch `isExplodableEntitySet` (`:1823`–`:1838`) already records `set_enabled_reaction_iris` and adds one `enabled_by` per resolved member. `drop_controller_entities` skip (`:1839`). Active-unit vs flat-PCC emission (`:1866`–`:1922`): `active_sites` empty → `defineReactionEntity(controller_entity, …, explode=true)` (`:1895`) builds the flat PCC via the explosion branch, then `e enabled_by controller_e` (`:1921`).
- **Explosion Complex branch** (`:1297`–`:1319`): when `explode_sets_complexes`, types the complex `GO:0032991` (`:1300`), and for every leaf in `flat.proteinsByKey ∪ flat.setLeaves` (`:1306`–`:1308`) builds a `has_part` component. **This is where `setLeaves` (REACTO union nodes) are emitted today.** Reached only for Catalysis controller complexes (inputs/outputs pass `explode=false`; regulators are skipped).
- **Set typing:** `getPhysicalEntityIRI` (`:2053`) returns `reacto_base_iri+id` for a set (no UniProt/CHEBI id) → the set leaf is the REACTO-classed individual; the PCC itself is overridden to `GO:0032991`.
- **Phase 2 split (reuse, unchanged):** `GoCAM.splitSetEnabledReactions(model_id)` (`GoCAM.java:1920`) clones each reaction in `set_enabled_reaction_iris` once per `enabled_by` target (sharing neighbors via `cloneIndividualSharingNeighbors`, `:1883`), deletes the original. Called from `wrapAndWrite` (`BioPaxtoGO.java:551`) before `applySparqlRules`, so `provides_input_for`/regulation rules fan out across clones.

## 6. Design

Phase 1 (build) does all the new work; Phase 2 (`splitSetEnabledReactions`) is **reused unchanged**.

### 6.1 New pure BioPAX helpers (`BioPaxtoGO`, next to `collectFlattenedComplexLeaves`)

- **`long countFlattenedComplexCombinations(Complex top)`** — recursive `∏`/`Σ` over the component/member tree, cycle-guarded, stripping `isSmallMoleculeEquivalent` leaves. Short-circuits: once the running count exceeds the cap, return a sentinel `> cap` (avoids overflow / deep work on the 12M monsters). Matches the analyzer's combos definition (uses `isSmallMoleculeEquivalent` for stripping).

- **`List<Map<String,Protein>> enumerateFlattenedProteinSets(Complex top)`** — returns one protein-map per combination (keyed by UniProt-id-else-URI, matching `collectFlattenedComplexLeaves`'s dedup key), cycle-guarded. Only called when `combos ≤ cap`.
  - `isSmallMoleculeEquivalent` / DNA / RNA / bare-non-ChEBI → `[ {} ]` (contributes nothing)
  - `Protein` → `[ { key → protein } ]`
  - Set (`getMemberPhysicalEntity()` non-empty) → **concatenate** each non-stripped member's enumeration (OR: one member per combination)
  - `Complex` → **cartesian-merge** across components (union the protein-maps; intra-combination UniProt dedup is automatic via the map key)

  Distinct combinations are then deduped by their sorted key-set.

### 6.2 New expansion branch (`BioPaxtoGO`, Loop 2, right after `isExplodableEntitySet` ~`:1838`)

```
if (is_catalysis && active_sites.isEmpty()
        && controller_entity instanceof Complex
        && ((Complex) controller_entity).getMemberPhysicalEntity().isEmpty()   // Guard A: true complex, not a set
        && (combos = countFlattenedComplexCombinations(controller_entity)) between 2 and SET_COMBINATION_CAP) {
    distinct = 0
    for each protein-set ps in enumerateFlattenedProteinSets(controller_entity):
        if ps.isEmpty(): continue
        skip if ps's sorted key-set already seen (dedup); else distinct++
        if ps.size()==1:
            build the single protein enabler individual (defineReactionEntity(protein, …, explode=false));
            e enabled_by protein            // reuses the bare-set-member path shape
        else:
            build a flattened PCC individual typed GO:0032991;
            for each protein in ps: build a has_part component individual (defineReactionEntity(protein, …, explode=false)); PCC has_part protein
            e enabled_by PCC
    if (distinct >= 1) {                     // Guard B
        go_cam.set_enabled_reaction_iris.add(e.getIRI());
        log SET_IN_COMPLEX_REACTION_EXPANDED  <model> <reaction_id> <combos> <distinct>
        continue;   // do NOT build the normal controller_e / flat PCC
    }
    log SET_IN_COMPLEX_NO_PROTEINS           // distinct == 0: fall through to normal path
}
```

- **Gate on `active_sites.isEmpty()`** so an annotated active site still wins. A complex-with-set never reaches Loop 1's single-protein branch (that requires `setLeaves.isEmpty()`), so `active_sites` is empty here unless annotated.
- **`SET_COMBINATION_CAP = 10`** as a named constant in `BioPaxtoGO`.
- **Deterministic IRIs** (dedup-friendly, no enumeration-order dependence):
  - combination PCC: `makeGoCamifiedIRI(null, {topComplexCurie}_{comboKey}_{entity_id}_controller)` where `comboKey` = sorted protein CURIEs joined, hashed if long.
  - PCC `has_part` protein component: `{proteinCurie}_{pccLocalName}_component` (unique per PCC; proteins are duplicated across combination PCCs by design — each PCC is a distinct enabler instance).
  - single-protein enabler: `{proteinCurie}_{entity_id}_controller` (matches the existing bare-set-member scheme).
- Then **Phase 2** clones `e` once per `enabled_by` target → the diamonds. No change to `splitSetEnabledReactions`.
- `deleteComplexesWithActiveUnits` does **not** touch these enablers: the reaction is `enabled_by` the PCC itself (not `enabled_by` a `has_part` active unit), so the query pattern does not match.

### 6.3 Over-cap fallback + REACTO-node deletion (explosion Complex branch, ~`:1306`–`:1319`)

Stop emitting `setLeaves`. The explosion branch's `has_part` leaves become **`flat.proteinsByKey.values()` only**; the set's REACTO union-class node is no longer created. For a **controller complex**, combos ≤ 10 cases are handled by §6.2 (they never call `defineReactionEntity(controller, explode=true)`) and combos == 1 cases have empty `setLeaves`, so the explosion branch encounters a controller's `setLeaves` **only** in the over-cap fallback — dropping their emission implements decision §4.3 with no separate delete pass.

Each dropped set node is **logged** so the removed REACTO individuals are traceable — one line per dropped `setLeaf`:

```
DROPPED_REACTO_SET_NODE  <model_id>  <go_cam.name>  <reaction_id>  <topComplex_id>  <setLeaf_reactome_id>  <setLeaf_reacto_class_curie>  <setLeaf_displayName>
```

where `setLeaf_reacto_class_curie` is `iriToCurie(getPhysicalEntityIRI(setLeaf))` (the REACTO class that would have typed the dropped node). This runs in the explosion branch as each `setLeaf` is skipped.

Loop 1's flatten decision gains a **combos gate** (so a combos≤10 complex-with-set is left for §6.2 expansion, not captured by the single-protein branch) and then keys on proteins only:
- `2 ≤ combos ≤ 10` → `continue` (expanded in Loop 2; do not touch `active_sites`). **This gate is required**: without it, the new `proteinsByKey.size()==1` branch would add a combos≤10 complex-with-set's lone fixed protein to `active_sites`, suppressing the Loop-2 expansion (e.g. RAB1:GTP:TBC1D20, whose only fixed protein is TBC1D20).
- otherwise (`combos == 1` or `combos > 10`): `flat.proteinsByKey.isEmpty()` → `drop_controller_entities` (over-cap set-only complex loses its enabler; log `COMPLEX_FLATTEN_NO_PROTEIN`); `flat.proteinsByKey.size()==1` → single-protein `active_sites`; else → flat PCC (proteins only).

(The broader consequence — a set nested inside a *whole-complex set-member* or *active-unit* passed to `defineReactionEntity(explode=true)` also loses its REACTO node — is consistent with the REACTO-removal goal and low-risk: no existing fixture depends on a retained set union node inside such a complex.)

## 7. Edge cases

- `combos == 1` (no set): unchanged.
- `2 ≤ combos ≤ 10`, set-only complex (no fixed proteins): each single-protein combination → `enabled_by` that protein directly — same shape as the bare-set split.
- Protein appears both as a fixed subunit and inside a set: the per-combination Map dedups by UniProt, so the combination collapses correctly (e.g. `{P1}` and `{P1,P2}`).
- Nested sets / sets-of-complexes / complexes-of-sets: handled by the recursive enumeration; cycle-guarded.
- `combos > 10`: fallback PCC with proteins only, REACTO set node dropped; set-only-over-cap → enabler dropped.
- Reaction with both a bare-set controller and a complex-with-set controller: `set_enabled_reaction_iris` is a `Set`; all `enabled_by` targets fan out together (accepted union).

## 8. Out of scope

- Regulator (non-Catalysis) sets/complexes (already skipped at emission).
- Set components appearing as reaction inputs/outputs (still typed with their REACTO union class; `explode=false`).
- Changing `reacto.owl` generation, `splitSetEnabledReactions`, or `cloneIndividualSharingNeighbors`.
- Raising/removing the cap or making it a CLI option (fixed constant for now).

## 9. Testing

Fixture: `exchange/src/test/resources/biopax/R-HSA-204005_level3.owl` (COPII-mediated vesicle transport), extracted verbatim (incl. `activeUnit:` comments) from `Homo_sapiens.owl` and auto-converted by `fullBuild()`.

**Unit** (fast, no heavy `@BeforeClass`, in `SetEnabledReactionSplitTest`, load the fixture directly):
- `countFlattenedComplexCombinations` on the `PP6` complex (`R-HSA-5694294`) → 2; on the big COPII complex (`R-HSA-5694334`) → short-circuits `> SET_COMBINATION_CAP`.
- `enumerateFlattenedProteinSets` on `PP6` → 2 protein-sets keyed by UniProt; count equals the counter.

**Integration** (`BioPaxtoGOTest`, REACTO):
- `R-HSA-6814833` (controller RAB1:GTP:TBC1D20, no activeUnit) → exactly **2** activities, each `enabled_by` a `GO:0032991` PCC `has_part` `{Q96BZ9 TBC1D20}` + one of `{Q9H0U4 RAB1B, P62820 RAB1A}` (GTP stripped); original node absent.
- **Negative guard:** `R-HSA-5694421` (PP6, HAS `activeUnit: #Protein205`) is **not** split (no `R-HSA-5694421_enabled_by_*` clones).
- **Cap guard:** `R-HSA-5694527` (combos=192, no activeUnit) stays a single un-split PCC, and **no** individual is typed `REACTO_R-HSA-5694244` (a dropped set).
- **Regression:** `testSetEnabledReactionSplit`, `testComplexCofactorReducedToSingleProtein`, `testActiveSiteInController`, `testComplexRegulatorLeavesNoOrphanComponents`, `testInferSmallMoleculeRegulators` still pass.

Run: `cd exchange && MAVEN_OPTS="-Xmx8g" mvn -Dtest=BioPaxtoGOTest test`.

## 10. Risks / watch-items

- **Evidence-node growth:** each diamond clones inputs/outputs/causal edges with fresh evidence. Cap = 10 bounds per-reaction growth; ~297 eligible human reactions expand (per-model much smaller). Monitor model size.
- **Fixture (already generated):** `R-HSA-204005_level3.owl` was extracted as the transitive-closure sub-model of Pathway `R-HSA-204005` from `Homo_sapiens.owl`. Do not hand-edit it (must retain real `activeUnit:` comments); regenerate via the closure-extraction script if needed.
- **`activeUnit` dominates:** 859 of 1,355 complex-with-set Catalyses are activeUnit-suppressed. Any analysis of "how many expand" MUST account for `getActiveSites`, not just structure.
- **Count/enumerate consistency:** both must use `isSmallMoleculeEquivalent` and the same UniProt-else-URI key so the cap gate and the emitted set agree.

## 11. Key code anchors (summary)

- `BioPaxtoGO.defineReactionEntity` explosion Complex branch `:1297`–`:1319` (drop `setLeaves` emission); Loop 1 flatten decision `:1715`–`:1743`; Loop 2 `isExplodableEntitySet` branch `:1823`–`:1838` (insert new branch after); flat-PCC emission `:1866`–`:1922`.
- `collectFlattenedComplexLeaves` `:2136`; `FlattenedComplex` `:2120`; `getPhysicalEntityIRI` `:2053`; `isSmallMoleculeEquivalent`, `extractUniprotId` (existing).
- `GoCAM.splitSetEnabledReactions` `:1920`; `cloneIndividualSharingNeighbors` `:1883`; call site `BioPaxtoGO.wrapAndWrite:551`.
