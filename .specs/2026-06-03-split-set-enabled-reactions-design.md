# Spec: Split set-enabled reactions into per-member activities

- **Date:** 2026-06-03
- **Status:** Approved (design); ready for implementation planning
- **Component:** `exchange/` — BioPAX → GO-CAM conversion (`BioPaxtoGO`, `GoCAM`)
- **Driving example:** pathway `R-HSA-1482922`, reaction `R-HSA-1482825`, file `exchange/R-HSA-1482922_level3.owl`

## 1. Problem

In Reactome BioPAX, a reaction is often catalyzed by an **EntitySet** — "any one of these gene products can catalyze this reaction" (a biological OR). Paxtools converts an EntitySet to a bare `bp:PhysicalEntity` carrying `memberPhysicalEntity` links and the comment *"Converted from EntitySet in Reactome"*.

Today, Pathways2GO maps a set catalyst to a **single** activity enabled by a single node typed as the set's REACTO class (REACTO defines the set as an `owl:unionOf` of its members — see `PhysicalEntityOntologyBuilder.checkForAndAddSet`, ~`:864`). That collapses the OR into one opaque node.

We want the more correct GO-CAM modeling: **one activity per set member**, each `enabled_by` a distinct gene product, all sharing the reaction's inputs, outputs, location in the pathway, and causal connections.

## 2. Driving example (expected output)

`R-HSA-1482825` ("PI is hydrolyzed to 1-acyl LPI by PLA2[11]") is catalyzed (BioPAX `Catalysis1`, `controlType=ACTIVATION`) by EntitySet **PLA2(11)** / `R-HSA-1524151`, whose members are:

| BioPAX member | Kind | Resolves to (enabler) |
|---|---|---|
| Protein2 — PLA2G4D | Protein | PLA2G4D |
| Protein3 — PLA2G4F | Protein | PLA2G4F |
| Protein4 — PLA2G16 | Protein | PLA2G16 |
| Protein5 — PLBD1 | Protein | PLBD1 |
| Complex1 — PLA2G4A:Ca2+ (`R-HSA-1500626`) | Complex (1 protein + Ca²⁺) | **reduced** → PLA2G4A (`R-HSA-378502`) |

**Expected:** the single `R-HSA-1482825` activity is replaced by **5 activities**, each of the reaction's MF type, each `enabled_by` one of {PLA2G4D, PLA2G4F, PLA2G16, PLBD1, PLA2G4A}, each sharing the same inputs (PI), outputs (1-acyl LPI), `part_of` the pathway, and the same causal edges.

The sibling set reaction (catalyzed by **PLA2(12)** / `R-HSA-1524141`: Protein6 PLA2G4C + Complex2 PLA2G2A:Ca²⁺ → PLA2G2A) splits into **2 activities**. The file also contains further sets (PLA2(13), PLA2(15), …) usable as test cases.

## 3. Decisions (resolved)

1. **Complex member that cannot reduce to a single protein** (≥2 proteins): create one activity **enabled by the complex itself** (a protein-containing-complex node with `has_part` its proteins) — consistent with how complex catalysts are handled today. *(A reducible complex — exactly one protein — is reduced to that protein, as in the example.)*
2. **Scope: catalysis only.** Only EntitySets controlling via `Catalysis` (enabling) are split. Non-catalytic regulator sets, and sets appearing as inputs/outputs, are out of scope and unchanged.
3. **Cross-product on causal edges accepted.** If a split reaction A (m ways) is causally linked to another split reaction B (n ways), the result is the full A₁…Aₘ → B₁…Bₙ cross-product (the OR×OR). Acceptable as default.

## 4. Current behavior (baseline) — code anchors

- `BioPaxtoGO.defineReactionEntity(...)` (`:1140`) builds a reaction node `e`. Within one call, for a reaction the order is: causal `causally_upstream_of` from pathway steps (`:1335`), `part_of` pathway + `has_input`/`has_output` (`:1450`–`:1579`), then the **controller loop** that adds `enabled_by` (`:1598`–`:1841`), then BP-xref handling and **MF type fallback** (`:1843`–`:1922`).
- Set catalyst today: `controller_entity` is the set; it is not a `Complex`, so the complex active-unit logic (`:1622`–`:1650`) is skipped; `defineReactionEntity` types it as the set's REACTO class (`:1781`); `e enabled_by set_node` (`:1791`–`:1803`).
- Causal `provides_input_for` and regulation edges are **not** built inline — they are produced later by `applySparqlRules` (`GoCAM.java:974`), invoked from `wrapAndWrite` (`BioPaxtoGO.java:562`) after the model is built, from rule files (`query2update_provides_input_for.rq`, `query2update_regulation_*.rq`).
- A disabled `explode_sets` branch (`:1237`–`:1273`) adds `has_substitutable_entity` to a single node — **not** what we want; left as-is/removed at implementer's discretion (it is currently dead: `explode_sets = false`).
- **Reuse infrastructure:** `GoCAM.cloneIndividual(...)` (`:1786`/`:1792`), `cloneAnnotations(...)` (`:1852`), `applyAnnotatedTripleRemover(...)` (`:858`), `deleteOwlEntityAndAllReferencesToIt(...)` (`:878`/`:885`). REACTO already mints a class per set member (`PhysicalEntityOntologyBuilder.checkForAndAddSet`, `:864`), so per-member enabler nodes will have types.

### Key constraint discovered
`deleteOwlEntityAndAllReferencesToIt(e, false)` **deletes the evidence individuals** attached to the deleted node's edges (`:906`–`:911`). Therefore cloned edges must carry **fresh** evidence (clone via `cloneAnnotations`), so deleting the original does not dangle a clone's evidence. It also deletes `located_in` targets of the node (`:897`–`:903`); reactions carry no `located_in`, so this is safe here.

## 5. Design

Two phases, leveraging the existing rule pipeline.

```
Phase 1 — during build (BioPaxtoGO.defineReactionEntity, catalysis controller loop)
  detect EntitySet catalyst → resolve members to leaf enablers
  → build each enabler node + add `reaction enabled_by enablerᵢ` (reuse existing wiring)
  → record reaction IRI in go_cam.set_enabled_reaction_iris
  (reaction is transiently 1 node with N enabled_by — same shape the multi-active-unit
   complex path already produces)

Phase 2 — wrapAndWrite, BEFORE `new QRunner(...)` / applySparqlRules (BioPaxtoGO.java:551–562)
  go_cam.splitSetEnabledReactions(model_id):
    for each recorded reaction e with enablers {m₁…mₙ}, n ≥ 2:
      for each mᵢ: eᵢ = clone of e SHARING the same neighbor individuals, enabled_by only mᵢ
      delete original e
  → QRunner rebuilt from the split graph → provides_input_for / regulation rules fan out
```

Phase 2 runs **after** the full build (so each clone copies the reaction's *final* MF type, assigned at `:1843`–`:1922`) and **before** `applySparqlRules` (so `provides_input_for`/regulation are generated for every clone for free).

### 5.1 Phase 1 — detection & member resolution (`BioPaxtoGO`)

In the catalysis branch of the controller loop (~`:1729`, after the drug-skip checks), before the normal single-enabler path:

- **`isExplodableEntitySet(Controller controller_entity)`** → true when `controller_entity instanceof PhysicalEntity` && `!getMemberPhysicalEntity().isEmpty()` && `!(controller_entity instanceof Complex)` && `!setIsSmallMoleculesOnly(members)`.
- **`resolveSetCatalystMembers(PhysicalEntity set)`** → `List<PhysicalEntity>` leaf enablers:
  - `SmallMolecule` → skip
  - `Complex` → `getComplexActiveUnitRecursive(m).getActiveUnits()`: size==1 ⇒ that protein (reduction); else ⇒ the complex `m` itself (decision §3.1)
  - nested set (`PhysicalEntity` with members) ⇒ recurse, cycle-guarded by a visited set of Reactome ids
  - else (Protein / simple PE) ⇒ `m`
  - dedupe by Reactome id
- For each resolved enabler, reuse existing enabler-node creation + `enabled_by` wiring (the same calls the single-enabler path makes: `defineReactionEntity(go_cam, enabler, enablerIri, true, …, true)` then `addRefBackedObjectPropertyAssertion(e, enabled_by, enablerNode, …)`), then `continue` (do **not** build the set node).
- Record `e.getIRI()` in `go_cam.set_enabled_reaction_iris`.
- Emit report line `SET_ENABLED_REACTION_SPLIT\t<model>\t<reaction_id>\t<n>\t<member_ids>`.

### 5.2 Phase 2 — `GoCAM.cloneIndividualSharingNeighbors(source, new_iri, Set<OWLObjectProperty> excludeProps, model_id)` (new)

Like `cloneIndividual`, but for each incident **object-property assertion** (source as subject *or* object) it recreates the edge pointing the clone at the **same** neighbor individual (not a duplicate), with the property's annotations cloned to **fresh** evidence via `cloneAnnotations(...)`. Copies class assertions (types) and node annotation assertions (labels, comments, `skos:exactMatch`, …). Skips edges whose property ∈ `excludeProps`. The existing `cloneIndividual` is left untouched (its neighbor-duplicating behavior is still used by the transport/regulator paths).

### 5.3 Phase 2 — `GoCAM.splitSetEnabledReactions(String model_id)` (new)

For each IRI in `set_enabled_reaction_iris`:
1. Gather its current `enabled_by` targets `{m₁…mₙ}`. If `n < 2`, skip.
2. For each `mᵢ`:
   - `eᵢ = cloneIndividualSharingNeighbors(e, makeGoCamifiedIRI(null, reactionId + "_enabled_by_" + memberId), {enabled_by}, model_id)`
   - Re-assert `eᵢ enabled_by mᵢ`, cloning the original `e enabled_by mᵢ` edge's annotations (fresh evidence).
   - Keep the original label; add comment `"split from set-enabled reaction <reaction_id>"`. (`skos:exactMatch` to the BioPAX reaction URI is copied, so every clone maps back to the same reaction.)
3. `deleteOwlEntityAndAllReferencesToIt(e, false)`.

Sequential processing produces the accepted cross-product (§3.3): A's incoming/outgoing edges are cloned to the surviving clones; deleting canonical A before B is processed leaves only `B → Aᵢ`, which then fan out to `Bⱼ → Aᵢ`.

Call site: `BioPaxtoGO.wrapAndWrite`, as the **first step before** `go_cam.qrunner = new QRunner(go_cam.go_cam_ont)` (`:551`), so the QRunner, drug-reaction removal (`:553`–`:558`), and `applySparqlRules` (`:562`) all see the split graph. (Clones carry the original's `has_input`/participant edges, so SPARQL-based drug-reaction detection still matches them.) If implementation reveals a bad interaction with drug removal, the fallback is to run the split *after* drug removal (`:558`) and then **rebuild** `go_cam.qrunner = new QRunner(go_cam.go_cam_ont)` before `applySparqlRules` — never leave a QRunner built from the pre-split graph in front of the rules.

### 5.4 Data structures & IRIs
- `GoCAM`: `Set<IRI> set_enabled_reaction_iris = new HashSet<>();` populated in Phase 1, consumed in Phase 2 (dedupes re-entrant `defineReactionEntity` calls).
- enabler node IRI: `makeGoCamifiedIRI(null, <member_id>_<reaction_id>_controller)`
- reaction clone IRI: `makeGoCamifiedIRI(null, <reaction_id>_enabled_by_<member_id>)` — deterministic; distinct from the deleted canonical `…/<reaction_id>`.

## 6. Edge cases
- `n ≤ 1` effective enabler → no split (stays a normal single-enabler reaction).
- Small-molecule-only set catalyst → not split (guard); existing behavior.
- Non-reducible complex member → one activity enabled by the complex node (§3.1).
- Nested sets → recursively flattened to leaves, cycle-guarded.
- Multi-active-unit **complex** catalysts → unchanged (still 1 node, N enablers; not recorded for split). Noted as related-but-out-of-scope.
- Strategy: graph surgery is strategy-agnostic; enabler typing reuses `defineReactionEntity`, so REACTO and YeastCyc both type enablers correctly. Validated on REACTO.

## 7. Out of scope
- Regulator (non-catalytic) sets; input/output sets.
- Changing `reacto.owl` generation (the set `unionOf` class still exists; it's simply no longer used as an enabler type for split reactions).
- Refactoring/repairing the multi-active-unit complex enabler pattern.

## 8. Testing
- Add `R-HSA-1482922_level3.owl` to `exchange/src/test/resources/biopax/`. New `BioPaxtoGOTest` method runs the full pipeline (REACTO) and asserts on the output model (OWL API and/or QRunner SPARQL):
  - Canonical `R-HSA-1482825` individual is **absent**.
  - **5** activities exist with the reaction's MF type, each sharing `has_input` (PI) / `has_output` (1-acyl LPI) / `part_of` pathway, each `enabled_by` a distinct protein in {PLA2G4D, PLA2G4F, PLA2G16, PLBD1, PLA2G4A}.
  - Sibling set reaction → **2** activities (PLA2G4C, PLA2G2A).
  - A downstream consumer of 1-acyl LPI (if present in the pathway) receives `provides_input_for` from all clones.
- **Guard test:** a reaction enabled by a single protein (existing fixture) remains a single activity; a multi-active-unit complex reaction is unchanged.
- Run `cd exchange && mvn -Dtest=BioPaxtoGOTest test` (≈8 GB heap; first run downloads go-plus.owl).

## 9. Risks / watch-items
- **Evidence-node growth:** fresh evidence per cloned edge × N clones. Acceptable for the small N here; monitor model size on large pathways.
- **QRunner sync:** Phase 2 mutates `go_cam_ont` via OWL API; the QRunner must be (re)built from `go_cam_ont` *after* the split — satisfied by running the split before `:551`.
- **Re-entrancy:** `defineReactionEntity` can be re-entered (bridging/causal recursion); the `Set<IRI>` guards duplicate recording, and `enabled_by` axioms are set-semantic in OWL.
- **`deleteOwlEntityAndAllReferencesToIt` is noted as slow** (`:857`); the number of split reactions per model is small, so impact is limited.

## 10. Key code anchors (summary)
- `BioPaxtoGO.defineReactionEntity` `:1140`; controller loop `:1598`–`:1841`; enabled_by wiring `:1791`–`:1803`; complex reduction `getComplexActiveUnitRecursive` (used at `:1637`,`:1255`); inputs/outputs `:1484`–`:1579`; causal `:1335`–`:1389`; MF type fallback `:1843`–`:1922`.
- `BioPaxtoGO.wrapAndWrite` `:549`; QRunner build `:551`; `applySparqlRules` call `:562`.
- `GoCAM.cloneIndividual` `:1786`/`:1792`; `cloneAnnotations` `:1852`; `applyAnnotatedTripleRemover` `:858`; `deleteOwlEntityAndAllReferencesToIt` `:878`/`:885`; `applySparqlRules` `:974`.
- `PhysicalEntityOntologyBuilder.checkForAndAddSet` `:864`.
