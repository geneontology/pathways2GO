# Flatten multi-subunit complex enablers to a single protein-containing complex — Design

**Date:** 2026-06-30
**Status:** Approved design, pending spec review

## Problem

When a Catalysis controller is a Complex that cannot be reduced to a single enzyme
protein, the converter currently emits it as a **nested tree** of OWL individuals.
Each sub-complex becomes its own protein-containing complex (PCC) individual with its
own `has_part` children, recursing through `defineReactionEntity` (BioPaxtoGO.java
~1287–1309). Two problems result:

1. **Cofactors leak.** The component filter at line 1294 still tests
   `c instanceof SmallMolecule`, not the newer `isSmallMoleculeEquivalent(...)`. Bare
   `bp:PhysicalEntity` ChEBI cofactors (e.g. the iron–sulfur clusters 2Fe-2S
   R-ALL-164296, 3Fe-4S, 4Fe-4S) are not stripped and appear as `has_part` instances.
2. **The structure is unnecessarily deep.** A reader wanting the protein subunits of
   the enabler must walk an arbitrarily nested complex hierarchy.

Worked example — pathway `R-HSA-71403`, reaction `BiochemicalReaction11`, controller
`Complex18` ("SDH complex", R-HSA-70990):

```
Complex18  "SDH complex"
├── Complex19  "SDHB:2Fe-2S:3Fe-4S:4Fe-4S"
│   ├── PhysicalEntity1  2Fe-2S   (bare PE + ChEBI:49601)   ← leaks today
│   ├── PhysicalEntity2  3Fe-4S   (bare PE + ChEBI:64606)   ← leaks today
│   ├── Protein18        SDHB     (UniProt P21912)
│   └── PhysicalEntity3  4Fe-4S   (bare PE + ChEBI)         ← leaks today
├── Protein19            (UniProt ...)
└── Complex20            (sub-complex of UniProt proteins)
```

`getComplexActiveUnitRecursive(Complex18)` returns no active unit (three non-small-mol
top-level components: two sub-complexes + one protein), so `active_sites` is empty and
the code falls through to the nested explosion.

## Goal

Emit a complex enabler as **one** protein-containing complex individual directly
connected via `has_part` edges to the **distinct protein subunits** (UniProt-identified
where available) collected from anywhere in the BioPAX complex hierarchy, with cofactors
and non-protein leaves (DNA/RNA/bare non-ChEBI PE) stripped, and no intermediate
sub-complex individuals.

Desired output for the example:

```
Complex18  (typed GO:0032991, protein-containing complex)
  has_part → SDHB  (UniProt P21912)
  has_part → <Protein19 UniProt>
  has_part → <Complex20 subunits' distinct UniProt proteins>
  (no 2Fe-2S / 3Fe-4S / 4Fe-4S; no Complex19 / Complex20 individuals)
```

## Decisions (from brainstorming)

- **Protein identity:** dedup by **distinct UniProt ID when present, else by the
  protein's BioPAX URI**. The same protein appearing multiple times (copies, or in
  different sub-complexes) yields exactly one `has_part` protein individual. *(Amended
  2026-06-30 during execution: the collector is shared with the YeastCyc strategy, whose
  proteins carry SGD — not UniProt — identifiers; a UniProt-only key dropped them and
  broke `testYeastComplexComponents`. The URI fallback keeps real protein subunits under
  both strategies.)*
- **What becomes `has_part`:** **all protein subunits.** A protein with a UniProt xref is
  kept and deduped by accession; a protein without one (e.g. YeastCyc SGD) is kept and
  deduped by URI. Stripped/skipped: `isSmallMoleculeEquivalent` cofactors, DNA/RNA, and
  bare `bp:PhysicalEntity` without a ChEBI xref. (Exception: EntitySets, below.)
- **EntitySet subunit:** keep as a **single `has_part` individual typed with its REACTO
  class** — do not descend into it. Consistent with the project's existing
  "don't explode sets" stance (`explode_sets = false`); preserves "one-of" semantics.
  This is the one non-UniProt `has_part` exception.
- **Scope:** **enabler/controller side only** (`explode_sets_complexes == true`).
  `has_input` / `has_output` complex participants are untouched — all such
  `defineReactionEntity` calls already pass `explode_sets_complexes = false`
  (verified: lines 1475, 1514, 1621, 1658).
- **Non-small-molecule regulators are skipped at emission.** Only small-molecule
  (`isSmallMoleculeEquivalent`) regulators are emitted (they keep an
  `involved_in_*_regulation_of` edge for the layer-2 `has_small_molecule_*` rewrite).
  Protein/Complex/Set/nucleic-acid regulators are skipped entirely in layer 1, rather
  than emitted-then-deleted. See §6.

### Edge cases

- **Exactly one distinct UniProt protein (and zero set leaves):** do **not** emit a PCC.
  Hook the UniProt protein up **directly as the enabler** (`enabled_by` the protein),
  reusing the existing active-site mechanism. **This subsumes the same-UniProt case:**
  a complex whose subunits are all the same accession (e.g. a homodimer) dedups to one
  protein and produces a **single** `enabled_by` edge — never multiple edges to the same
  reaction activity.
- **Empty (no UniProt protein and no set leaf anywhere in the tree):** **drop the whole
  enabler** — emit no `enabled_by` edge for this controller — and log a warning
  (`COMPLEX_FLATTEN_NO_PROTEIN`).
- **No stray complex node:** whenever the enabler resolves to a specific protein active
  unit (the single-protein case above, or an annotated active site), the residual
  complex individual must be **deleted** so no orphan/untyped complex node remains
  anywhere in the model.

## Architecture

All work is in `exchange/src/main/java/org/geneontology/gocam/exchange/BioPaxtoGO.java`.

### 1. New traversal — `collectFlattenedComplexLeaves`

A **pure BioPAX-level** method (creates no OWL individuals) placed next to
`complexHasProtein`, returning a small holder:

```
class FlattenedComplex {
    Map<String,Protein> proteinsByKey;        // keyed by UniProt accession, else BioPAX URI
    Set<PhysicalEntity>  setLeaves;           // EntitySet subunits kept by REACTO class
    int    totalLeaves();                     // proteinsByKey.size() + setLeaves.size()
    boolean isEmpty();                         // totalLeaves() == 0
}

FlattenedComplex collectFlattenedComplexLeaves(Complex top)
  walk top.getComponent() recursively; for each component c, in this order:
    1. isSmallMoleculeEquivalent(c)            → skip
    2. !c.getMemberPhysicalEntity().isEmpty()  → setLeaves.add(c)  (EntitySet; do NOT descend)
    3. c instanceof Complex                    → recurse into c.getComponent()
    4. c instanceof Protein                    → key = extractUniprotId(c) != null
                                                 ? uniprotId : c.getUri();
                                                 proteinsByKey.put(key, c)   // key is UniProt id or URI
    5. otherwise (DNA, RNA, non-ChEBI bare PE) → skip
```

Order rationale: the set check precedes the Complex check so that a "complex set"
(a `bp:Complex` carrying `memberPhysicalEntity`) is kept as a set leaf rather than
descended into. Assumes EntitySets are modeled with members and not `getComponent()`.

### 2. Emission — rewrite the Complex branch of the explosion (lines ~1287–1309)

Replace the per-component loop that recurses via `defineReactionEntity` (creating
nested PCC individuals) with a flat emission:

- Type the top individual `e` as `GO:0032991` (PCC) — unchanged.
- `FlattenedComplex fc = collectFlattenedComplexLeaves((Complex) entity);`
- For each `Protein` in `fc.proteinsByKey.values()` **and** each set leaf in
  `fc.setLeaves`: build one component IRI keyed on the leaf and the **top** complex id,
  call `defineReactionEntity(leaf, ...)` **on the leaf only**, and assert
  `e has_part leaf`.
- Because the collector returns proteins and sets (never sub-complexes), the leaves do
  not re-enter this Complex branch — no intermediate individuals are created.

This rewrite also resolves problem (1): the old `c instanceof SmallMolecule` filter at
line 1294 is gone; stripping now flows entirely through `isSmallMoleculeEquivalent`
inside the collector.

### 3. Enabler decision — controller resolution loop (lines ~1702–1731)

Today, when a Catalysis controller is a Complex with no annotated active site,
`getComplexActiveUnitRecursive` is tried; if it yields nothing, the code logs
`COMPLEX_CANT_BE_REDUCED_TO_PROTEIN` and later falls through to the explosion. Its
same-UniProt branch adds **all** matching proteins to `active_units`, which produces
multiple `enabled_by` edges for a homodimer.

The flatten **replaces** `getComplexActiveUnitRecursive` as the enabler decision for a
Catalysis Complex controller (the call at ~line 1718). Because the collector dedups by
UniProt across the whole tree, it gives one unified, correct outcome for every shape:
single-component chains → 1 protein; homodimers → 1 protein; multi-subunit → N proteins;
cofactor-only → 0. The new logic replaces the `has_protein`/reduce block and the
`COMPLEX_CANT_BE_REDUCED_TO_PROTEIN` log:

```
if (active_sites.size() > 0) { log COMPLEX_ACTIVE_UNIT_IS_SPECIFIED; continue; }  // annotated active site
FlattenedComplex fc = collectFlattenedComplexLeaves((Complex) controller_entity);
if (fc.isEmpty()) {
    drop_controller_entities.add(controller_entity);     // → Loop 2 skips it
    log COMPLEX_FLATTEN_NO_PROTEIN
} else if (fc.proteinsByKey.size() == 1 && fc.setLeaves.isEmpty()) {
    active_sites.add(fc.proteinsByKey.values().iterator().next());  // → single enabled_by protein
    log COMPLEX_FLATTENED_TO_SINGLE_PROTEIN
} else {
    log COMPLEX_FLATTENED_TO_PCC (count)                 // active_sites stays empty → flat PCC
}
```

A new `Set<PhysicalEntity> drop_controller_entities` is declared next to `active_sites`
(~line 1702). `getComplexActiveUnitRecursive` is **retained** for its other callers
(`resolveSetCatalystMembers` ~line 1180; `getActiveSites` ~line 2311); only the
enabler-decision call site changes.

### 4. Residual complex node — already handled by existing code (no new change)

*(Amended 2026-06-30 during execution.)* The plan originally called for enabling the
commented-out `deleteOwlEntityAndAllReferencesToIt(controller_e)` block (~line 1888).
Investigation showed that is **redundant**: the residual complex controller node is
already removed by the existing `GoCAM.deleteComplexesWithActiveUnits()` pass, called
unconditionally from `applySparqlRules()` (GoCAM.java:995). Its query
(`getComplexesWithActiveUnits`, QRunner.java:1212) selects any `?complex` where
`?complex BFO_0000051 ?active_part` and `?reaction (RO_0002233|RO_0002333) ?active_part`
— exactly the vestigial controller node that `has_part` the active unit which
enables/inputs the reaction. So when the enabler resolves to a single protein active
unit, the complex node is deleted downstream with **no new in-layer code**.

The commented-out block (per issue #91) therefore **stays commented**; a comment there
now points to `deleteComplexesWithActiveUnits`. The "no stray complex node" requirement
is confirmed by a post-condition assertion in the test — nothing `has_part` the enabler.
(That assertion cannot be driven red by toggling this one block, precisely because the
downstream pass already guarantees the deletion; it stands as a regression guard on the
end state.) When `active_units == null` — the flat-PCC case and genuine PCC regulators —
`controller_e` **is** the PCC and is the `enabled_by` target; `deleteComplexesWithActiveUnits`
does not remove it because it has no single active unit.

### 5. Drop handling — controller emission loop (lines ~1827)

At the top of the per-`controller_entity` body, after the explodable-EntitySet block and
before the controller individual is created:

```
if (drop_controller_entities.contains(controller_entity)) { continue; }
```

Skipping here means no controller individual and no `enabled_by` edge are created.
Nothing needs to be cleaned up because no individuals for the dropped complex exist yet
(`getComplexActiveUnitRecursive` and `collectFlattenedComplexLeaves` are both pure, and
`controller_e` is created later).

### 6. Skip non-small-molecule regulators (top of Loop 2 body, ~line 1758)

A non-catalytic Control whose regulating entity is not a small molecule is skipped at
emission instead of emitted-then-deleted. At the top of the per-`controller_entity`
body, before any individual is created:

```
if (!is_catalysis && !isSmallMoleculeEquivalent(controller_entity)) {
    log SKIP_NON_SMALL_MOL_REGULATOR
    continue;   // no explosion, no involved_in_*_regulation_of edge
}
```

- `is_catalysis` is already computed (~line 1734) and in scope.
- **Small-molecule (and bare-ChEBI-cofactor) regulators are NOT skipped:** they still
  emit `involved_in_negative/positive/regulation_of`, which `inferSmallMoleculeRegulators`
  then rewrites to `has_small_molecule_inhibitor` / `has_small_molecule_activator`. That
  layer-2 rewrite depends on the layer-1 edge, so it must be preserved — hence the skip
  is gated on `!isSmallMoleculeEquivalent`, not on a blanket regulator skip.
- **Consequence:** a Complex regulator is now never emitted, so it never reaches the
  flatten/explosion branch (§2). That branch is therefore exclusively for **Catalysis**
  enabler complexes.
- `inferSmallMoleculeRegulators`' non-chemical-deletion branch (`deleteRegulatorAndComponents`)
  is left in place as a backstop (e.g. a small-molecule that resolves to a nucleic-acid
  ChEBI class would still be emitted in layer 1 and removed in layer 2); in practice it
  will rarely fire after this change.
- Placement is at the very top of the loop body; the only earlier block is the
  upstream-event pull, which is gated on `add_upstream_controller_events_from_other_pathways`
  (default **false**), so default behavior is unaffected.

## Data flow

```
Non-catalytic Control (regulator):
  └─ Loop 2: !isSmallMoleculeEquivalent(controller_entity) → continue   (§6, skip; nothing emitted)
             small-molecule regulator → involved_in_*_regulation_of
                                        → layer 2 rewrites to has_small_molecule_*

Catalysis (Complex controller, no activeUnit annotation):
  └─ Loop 1: getActiveSites → empty
       collectFlattenedComplexLeaves(complex):     (dedup by UniProt across whole tree)
         0 leaves           → drop_controller_entities += complex ; warn
         1 protein, 0 sets  → active_sites += protein          (homodimer collapses here)
         else               → (active_sites stays empty)
  └─ Loop 2 (per controller_entity):
       drop?                       → continue (no enabled_by)
       active_sites non-empty      → enabled_by protein(s)            [existing path]
                                       (residual controller_e node removed downstream by
                                        deleteComplexesWithActiveUnits — no new code)
       active_sites empty          → defineReactionEntity(complex, explode=true)
                                       → Complex branch flattens → PCC has_part leaves
                                       → enabled_by PCC               [existing path, line 1901]
```

## Behavior preserved / explicitly out of scope

- **`has_input` / `has_output`** complex participants: unchanged (explode flag false).
- **Annotated active sites** (`getActiveSites` non-empty): unchanged — they are resolved
  before the flatten and continue to drive `enabled_by`. (They now also benefit from the
  residual-node deletion in section 4.)
- **Regulators (non-Catalysis controls):** now handled by §6 — non-small-molecule
  regulators (Protein/Complex/Set/nucleic acid) are skipped at emission; small-molecule
  regulators are emitted and rewritten to `has_small_molecule_*` by
  `inferSmallMoleculeRegulators` (GoCAM.java:1669) exactly as today. The **final** model
  is unchanged from today (which already retained only small-molecule regulators, via
  emit-then-delete); §6 just reaches that state without the wasted emit-then-delete and
  keeps complex regulators out of the flatten path entirely. `deleteRegulatorAndComponents`
  (GoCAM.java:1761) remains as a backstop. The drop/single-protein decision (§3) is
  Catalysis-only and never touches regulators.
- **`getComplexActiveUnitRecursive`** is retained for its non-enabler callers
  (`resolveSetCatalystMembers`, `getActiveSites`); only the enabler-decision call site is
  replaced by the flatten.
- **Component IRI scheme:** flat leaves keep the existing
  `{leaf_curie}_{topComplexId}_{reactionId}_component` IRI. For a **direct** component
  (e.g. the ALAD regulator case) this is byte-for-byte the same IRI as today. For a
  **deeply nested** protein the parent id is now the top complex (not the intermediate
  sub-complex), so its IRI changes — no existing test depends on a nested component's IRI.

## Testing

**New PCC test** in `BioPaxtoGOTest.java` against `R-HSA-71403` / `BiochemicalReaction11`:

- **Precondition:** the reaction is present in its graph.
- **Enabler is one PCC:** the reaction is `enabled_by` an individual typed
  `GO:0032991`.
- **Subunits present:** that PCC has `has_part` to the SDH UniProt proteins (e.g. SDHB
  P21912 and the other distinct subunits).
- **Cofactors stripped:** no instance is typed with the REACTO/ChEBI class for 2Fe-2S
  (R-ALL-164296), 3Fe-4S, or 4Fe-4S, and the PCC has no `has_part` to them.
- **No intermediate sub-complex individuals:** no individual typed with the REACTO class
  for `R-HSA-70987` (Complex19) appears as a `has_part` of the enabler.

**New single-enabler test** (covers the same-UniProt/single-protein collapse and node
deletion): pick a Catalysis complex enabler that flattens to one distinct UniProt protein
(the FECH case `R-HSA-189402` → `R-HSA-189465` is a ready fixture) and assert:

- the reaction has **exactly one** `enabled_by` edge, to the UniProt protein (P22830);
- **no** residual complex individual remains — nothing is typed with the REACTO class for
  `R-HSA-189402`, and there is no `enabled_by`/`has_part` referencing it.

(If a same-UniProt homodimer fixture exists in the corpus, add a count assertion that it
yields a single `enabled_by` edge; otherwise the FECH single-protein case is the gate.)

Regression guard: re-run the existing complex/enabler/set/regulator tests
(`testComplexCofactorReducedToSingleProtein`, `testComplexRegulatorLeavesNoOrphanComponents`,
`testActiveSiteInController`, `testSetEnabledReactionSplit`,
`testReusedSmallMoleculeInputIsTyped`, `testInferSmallMoleculeRegulators`). Expected
interactions:
- `testComplexCofactorReducedToSingleProtein` (FECH): still `enabled_by` P22830; now also
  has no residual `R-HSA-189402` complex node.
- `testActiveSiteInController`: still exactly one `enabled_by` active part (deletion of the
  complex node does not touch that edge).
- `testComplexRegulatorLeavesNoOrphanComponents`: the ALAD complex is a *regulator*, now
  **skipped at emission** (§6), so its component is never created — `orphanTriples == 0`
  and the no-orphan invariant both hold (trivially). Add an assertion that a known
  small-molecule regulator in the same model still produces a `has_small_molecule_inhibitor`
  or `has_small_molecule_activator` edge, to guard that §6 did not over-skip.

## Constraints

- **No git operations** (per repo CLAUDE.md): do not `git add` / `git commit`. Each task
  ends with a build/test verification, not a commit.
- Tests are heavy (`@BeforeClass` runs the full corpus conversion into Blazegraph,
  downloads `go-plus.owl` on first run; ~8GB heap; run from `exchange/`).
- Do not restructure `BioPaxtoGO.java`; add the new `collectFlattenedComplexLeaves`
  helper (+ `FlattenedComplex` holder) near `complexHasProtein`, and make minimal edits
  at four existing sites: the explosion Complex branch (§2), the enabler-decision block
  in Loop 1 (§3), the drop-skip (§5) and the non-small-molecule-regulator skip (§6) near
  the top of the Loop 2 controller body, plus the `drop_controller_entities` declaration.
  §4 requires **no code change** (residual-node cleanup is handled by the existing
  `deleteComplexesWithActiveUnits`). `inferSmallMoleculeRegulators` in `GoCAM.java` is
  left unchanged (backstop only).
- Entity strategy under test is `REACTO` (default for `fullBuild()`).
