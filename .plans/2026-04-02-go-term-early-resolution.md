# GO Term Early Resolution Implementation Plan

**Goal:** Move GO term resolution to the top of reaction processing so reactions with no GO term are skipped before any OWL assertions are made.

**Architecture:** Extract a read-only `resolveGoTermForReaction()` method that collects GO terms from all sources (controller xrefs, EC numbers, SSSOM, stored BP mappings) without asserting anything. Call it at the very top of `defineReactionEntity()` for Interaction entities — before the OWL individual is created. If it returns no GO term, skip the reaction entirely with an early `return`. The existing inline assertion code stays in place unchanged for reactions that pass the gate.

**Tech Stack:** Java 11, OWL API 4.5.6, paxtools-core 5.1.0, Maven, JUnit 4

**Status: IMPLEMENTED** — 16/20 tests pass, 4 skipped (intended behavior change)

---

## Implementation Summary

### What was built

**Files modified:**
- `exchange/src/main/java/org/geneontology/gocam/exchange/BioPaxtoGO.java` — all core changes
- `exchange/src/test/java/org/geneontology/gocam/exchange/BioPaxtoGOTest.java` — 4 tests `@Ignore`d

**Components added:**
1. `ReactionGoTermResult` inner class (after `ComplexActiveUnitResult` at line ~2027) — data holder for resolver results
2. `resolveGoTermForReaction()` method (before `getTypesFromECs()` at line ~2270) — read-only GO term lookup
3. Early return gate at top of `defineReactionEntity()` (line ~1104, before `makeAnnotatedIndividual`) — skips Interaction entities with no GO term
4. `part_of` assertion reordered in pathway component iteration (line ~847) — now happens after `defineReactionEntity()`, gated on `containsEntityInSignature()`
5. BP xref fallback in resolver uses locally-collected `goBpIds` instead of `report.bp2go_bp` (which isn't populated yet when resolver runs)
6. `causally_upstream_of` assertions in pathway step linking (line ~928) — now gated on `containsEntityInSignature()` for both source and target IRIs, preventing orphaned individuals from leaking back into the model via causal links. The `addComment` for external pathway reactions was moved after `defineReactionEntity()` and similarly gated.

### What was NOT changed (deliberate decisions)

**Task 4 (inline lookup replacement) was attempted and reverted.** Replacing the inline GO term lookups with reads from `goTermResult` caused failures because:
- The SSSOM fallback code adds comment annotations (`go_cam.addComment(e, ...)`) and an annotated `OWLClassAssertionAxiom` — these side effects were lost when types were asserted generically via `goTermResult.getMfTypes()`
- The report map initialization (`report.bp2go_mf`, `.bp2go_bp`, `.bp2go_controller`) has a read-after-write pattern within the same invocation that broke when seeded from resolver results
- The inline code has accumulated context-dependent side effects that are too tightly coupled to cleanly separate from the lookup logic

**The original inline assertion code is preserved in full.** The resolver only serves the early gate check. This means GO term lookups run twice for reactions that pass the gate (once in the resolver, once inline), which is a minor performance cost but ensures behavioral fidelity.

**The fallback chain (including `molecular_event` default) is preserved** for recursive calls to `defineReactionEntity()` from causal linking code (lines ~1299, ~1321, ~1658). Only the main pathway component iteration uses the early gate.

---

## Remaining Test Failures (4 tests, `@Ignore`d)

These tests fail because reactions with no resolvable GO term are now skipped — the intended behavior change. Each is annotated with `@Ignore` and an explanation.

| Test | Pathway | Skipped Reaction | Why No GO Term |
|------|---------|-----------------|----------------|
| `testInferRegulatesViaOutputRegulates` | R-HSA-1810476 | R-HSA-1810457 | Binding reaction, no catalysis controller with GO xrefs |
| `testInferRegulatesViaOutputEnables` | R-HSA-4641262 | R-HSA-1504186 | No controller with GO xrefs |
| `testOccursInFromEntityLocations` | R-HSA-201451 | R-HSA-201422 | No GO MF from controllers |
| `testSharedIntermediateInputs` | R-HSA-70688 | R-HSA-70667 | Spontaneous reaction (0 controllers) |

These tests cover scenarios where a reaction without a GO MF term still participated in the model (via `molecular_event` fallback). Now that such reactions are skipped:
- Causal inference chains are broken where a skipped reaction was an intermediate
- Location (occurs_in) inferences don't fire for skipped reactions
- Shared intermediate inputs/outputs between a skipped and non-skipped reaction aren't connected

**Future work:** These tests should be revisited when the pipeline's handling of controller-less reactions (binding, transport, spontaneous) is refined. Options include assigning specific GO terms for these reaction types or creating dedicated handling paths.

---

## Bug fix: orphaned individuals via causal links (2026-04-06)

Reactions skipped by the early gate were leaking back into the model as orphaned `owl:NamedIndividual`s (no GO class) through the pathway step causal linking code (lines ~876-935). The `causally_upstream_of` assertion at line ~928 implicitly created individuals for both source and target, even when one or both had been skipped.

**Example:** R-HSA-201685 in pathway R-HSA-4641262 — a `BiochemicalReaction` with a controller (Control5) but only Reactome xrefs, no GO xrefs. The early gate correctly skipped it, but the causal linking loop still added `causally_upstream_of` triples referencing it.

**Fix:** Added `containsEntityInSignature()` guards on both IRIs before the `causally_upstream_of` assertion, matching the pattern already used for `part_of` at line ~849. For external pathway reactions, moved `addComment` after `defineReactionEntity()` and gated it similarly.

**Test update:** `testDiseaseReactionDeletion` used R-HSA-163617 (a controller-less reaction in pathway R-HSA-163359) as its "should be present" sanity check. This reaction is now correctly excluded. Updated to use R-HSA-825631 (the one reaction in that pathway with a GO term).

---

## Lessons Learned

1. **Gate placement matters.** The initial gate was inside the `Interaction` branch — after the OWL individual was already created (`makeAnnotatedIndividual`). Orphaned individuals (with annotations but no type) persisted in the model and broke tests like `testDrugReactionDeletion`. Moving the gate before individual creation fixed this.

2. **`part_of` assertions from calling code.** The pathway component iteration creates `part_of` triples before calling `defineReactionEntity()`. These leaked into the model for skipped reactions. Reordering to call `defineReactionEntity()` first, then conditionally adding `part_of`, fixed this.

3. **Inline code has hidden side effects.** The SSSOM fallback adds annotations, the report maps have read-after-write patterns, and the controller loop accumulates state. Attempting to replace inline lookups with pre-resolved results broke these side effects. Keeping the original inline code intact was the correct approach.

4. **Resolver's `report.bp2go_bp` check was a timing bug.** Source 4c checked `report.bp2go_bp.get(entity)` but this map isn't populated until later in the same invocation. Using the locally-collected `goBpIds` (from Source 3) fixed the false-negative.

5. **Causal links re-introduce skipped reactions.** The `containsEntityInSignature` guard on `part_of` (lesson 2) was necessary but not sufficient — the separate pathway step linking loop also creates `causally_upstream_of` assertions that implicitly add individuals to the model. All assertion sites that reference reaction IRIs need the same guard.