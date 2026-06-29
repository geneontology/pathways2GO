# Design: Type small-molecule inputs reused from skipped molecular events

**Date:** 2026-06-29
**Status:** Approved (design)
**Component:** `exchange/src/main/java/org/geneontology/gocam/exchange/BioPaxtoGO.java`
**Related:** #324 (skip molecular events), #352 (split set-enabled reactions)

## Problem

The conversion can emit a GO-CAM input individual that has no class (only
`owl:NamedIndividual`).

Concrete case (`exchange/R-HSA-189451_level3.owl`, "Heme biosynthesis"):

- `R-ALL-189489_cytosol` is dALA-in-cytosol (`SmallMolecule8`, ChEBI:356416).
- It is the **output** of `R-HSA-189456` ("Mitochondrial dALA translocates to
  cytosol" — `BiochemicalReaction2`, an **uncatalyzed transport**: no
  `eCNumber`, no `Catalysis`/`Control`).
- It is the **input** of `R-HSA-189439` ("ALAD condenses 2 dALAs to form PBG" —
  `BiochemicalReaction4`, catalyzed, EC 4.2.1.24).
- `R-HSA-189456` → `nextStep` → `R-HSA-189439` (PathwayStep2 → PathwayStep4).

In the output, `R-ALL-189489_cytosol` is referenced via `has_input` of
`R-HSA-189439` but carries no class. The expected class `CHEBI_356416`
("5-ammoniolevulinate") exists in `go-lego.owl` and is not obsolete, so the
class is missing because the code path that asserts it never runs — not because
the term is unavailable.

## Root cause

A physical entity's class is asserted only inside `defineReactionEntity`
(`BioPaxtoGO.java:1349-1350`, via `getPhysicalEntityIRI` → `addTypeAssertion`).

For a small-molecule input there are two paths:

1. **Reuse path** (`:1583-1603`): if the input was a previous step's output, the
   code grabs a *bare reference* — `go_cam.df.getOWLNamedIndividual(i_iri)` — and
   does **not** call `defineReactionEntity`. It assumes the producing reaction
   already typed the individual (via that reaction's own output handling at
   `:1650`).
2. **Fresh path** (`:1604-1614`): taken only when `i_iri == null`; this one
   *does* call `defineReactionEntity`, which asserts the class.

`previous_steps = pathway_step.getNextStepOf()` (`:1538`) reads the **BioPAX
model**, so `R-HSA-189439` finds `R-HSA-189456` as a predecessor and takes the
reuse path — blind to whether `R-HSA-189456` was actually emitted into the
GO-CAM.

`R-HSA-189456` is an uncatalyzed transport: `resolveGoTermForReaction` finds no
EC, no controller MF xrefs, and (in REACTO) no SSSOM fallback, so `mfTypes` is
empty → `hasNoGoTerm()` is true → the early gate at `:1224-1234` returns
**before** the output-defining code at `:1650`. The output `R-ALL-189489_cytosol`
is therefore never created-and-typed, yet the downstream reaction reuses its IRI.

This is a regression from #324: in the pre-#324 output
(`reacto-out-full/R-HSA-189451.ttl`, dated 2025-09-03), `R-HSA-189456` was
emitted as a `molecular_event` individual that defined its output, so the
molecule correctly got `a CHEBI_356416`. Once #324 began skipping no-GO-term
events, that definition step disappeared, but the reuse logic that depends on it
did not.

## Scope

The bug is inherently limited to **small-molecule inputs**. The reuse shortcut at
`:1583` is the only `defineReactionEntity`-skipping path, and it is guarded by
`input instanceof SmallMolecule`. Outputs (`:1650`) and all non-small-molecule
entities always go through `defineReactionEntity` and are always typed. The fix
therefore lives entirely in the input-reuse block.

## Chosen approach: guard reuse on a non-skipped producer

Inside the reuse loop, do not reuse a candidate producer's output if that
producer reaction *will be skipped* by the GO-term gate. When every candidate
producer is skipped, `i_iri` stays `null` and control falls through to the
existing fresh path, which types the molecule.

### Change

In `defineReactionEntity`, within the small-molecule reuse loop
(`BioPaxtoGO.java:1585-1602`), after resolving the candidate `reaction`:

```java
for (PathwayStep previous_step : previous_steps) {
    BiochemicalReaction reaction = getBiochemicalReaction(previous_step);
    if (reaction == null) {
        continue;
    }
    // Don't reuse the output of a reaction that the GO-term gate will drop:
    // it never reaches defineReactionEntity, so its output individual is never
    // typed. Falling through leaves i_iri == null, so the fresh path below
    // calls defineReactionEntity and types the molecule. See #352 / issue
    // about untyped small-molecule inputs (R-ALL-189489_cytosol).
    if (resolveGoTermForReaction(reaction, go_cam).hasNoGoTerm()) {
        continue;
    }
    ConversionDirectionType prev_step_direction = getDirection(reaction);
    ...
}
```

No other code changes are required; the fresh path (`:1604-1614`) already builds
the identical IRI (`input_id + "_" + input_location`) and calls
`defineReactionEntity`.

### Why this is correct and safe

- **Node-sharing preserved.** Both the reuse and fresh paths construct the same
  IRI, so the molecule remains a single shared individual across reactions — now
  typed. Two consumers each taking the fresh path produce the same IRI and type
  (idempotent: the location individual id is deterministic,
  `entityRef + "_" + GO-loc-id`, `:1389`; OWL axioms are set-valued).
- **Order-independent.** The guard checks the deterministic GO-term gate
  (`resolveGoTermForReaction(...).hasNoGoTerm()`), not model state, so it is
  correct regardless of whether the producer has been processed yet. (Considered
  alternative: `go_cam.go_cam_ont.containsEntityInSignature(reactionIri)` — the
  `findNearestDefinedProcesses` idiom — cheaper but order-dependent; rejected for
  exactness.)
- **No side effects.** `resolveGoTermForReaction` only reads the BioPAX entity
  and go-lego; it does not mutate the model.
- **Complete.** Any small-molecule individual that survives into the model is
  referenced by some *defined* reaction, whose input handling now types it.
  Molecules referenced only by skipped reactions never appear in the output, so
  there are no orphans. This also covers chains of consecutive skipped transports.

### Performance

`resolveGoTermForReaction` runs per candidate producer per small-molecule input.
Pathways are small (≤ dozens of reactions, ~1–few previous steps each), so the
cost is negligible. Memoization (a `Map` keyed by reaction) is a possible future
optimization but is intentionally out of scope for the first cut.

## Testing (TDD)

1. Add `R-HSA-189451_level3.owl` to `src/test/resources/biopax/` (currently it
   lives only at the `exchange/` root).
2. Add a focused test (modeled on `SetEnabledReactionSplitTest`) that converts
   `R-HSA-189451` with the REACTO strategy and asserts:
   - **Specific:** the `has_input` individual of reaction `R-HSA-189439`
     corresponding to dALA-in-cytosol (`R-ALL-189489_cytosol`) carries a CHEBI
     class type (`CHEBI_356416`). Fails before the change, passes after.
   - **Invariant:** no individual that is the object of `has_input` or
     `has_output` is left with only `owl:NamedIndividual` (i.e., every reaction
     participant has at least one asserted class).

## Out of scope

- Memoizing GO-term resolution.
- Re-introducing skipped molecular events or their participants (Approach C,
  rejected).
- Any change to output handling or non-small-molecule entity typing.
