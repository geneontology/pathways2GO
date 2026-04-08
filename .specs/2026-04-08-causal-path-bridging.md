# Causal Path Bridging When Intermediate Reactions Are Skipped

## Problem

The early GO term resolution gate (from the 2026-04-02 plan) skips reactions with no resolvable GO MF term. When a skipped reaction sits between two defined reactions in the BioPAX pathway step graph, the causal chain is broken. For example, in pathway R-HSA-4641262:

```
R-HSA-3601585 → R-HSA-201685 → R-HSA-201677
```

R-HSA-201685 has no GO term and is skipped. The `containsEntityInSignature` guard on `causally_upstream_of` (line ~930) correctly prevents orphaned individuals, but the causal path is lost entirely. The desired result is:

```
R-HSA-3601585 → R-HSA-201677
```

## Scope

- Applies only to the **pathway-level causal linking loop** (`BioPaxtoGO.java` lines ~876-935), not the recursive/inline causal linking inside `defineReactionEntity` (lines ~1299-1353).
- Bridges chain through multiple consecutive skipped reactions (A → B → C → D where B and C are both skipped becomes A → D).
- Bridged links use `causally_upstream_of` with no special annotation — they look identical to direct links.
- The `external_pathway` code path is unchanged.

## Design

### Helper method: `findNearestDefinedProcesses`

A new private method on `BioPaxtoGO`:

```java
private Set<Process> findNearestDefinedProcesses(
    PathwayStep startStep,
    boolean forward,
    GoCAM go_cam,
    Pathway pathway,
    Set<PathwayStep> visited)
```

**Behavior:**
1. If `startStep` is already in `visited`, return empty set (cycle guard).
2. Add `startStep` to `visited`.
3. Collect the `Interaction` processes from `startStep.getStepProcess()` that are `in_pathway` (i.e., `process.getPathwayComponentOf().contains(pathway)`) and are not `Control` instances — matching the existing filters at line ~889-894.
4. For each matching process, check `go_cam.go_cam_ont.containsEntityInSignature(iri)`. Collect defined ones.
5. If at least one defined process is found, return them.
6. If none are defined (all were skipped), recurse into neighbor steps:
   - If `forward`: iterate `startStep.getNextStep()`
   - If backward: iterate `startStep.getNextStepOf()`
   - Accumulate results from all branches and return the union.

### Integration into the causal linking loop

The existing loop at line ~876 iterates `pathway.getPathwayOrder()`, and for each step examines its previous steps via `getNextStepOf()`. For each `(prevStep, step1)` edge, it pairs `prevEvents` with `events`.

The change modifies what happens inside the `(prevStep, step1)` edge iteration:

1. Collect defined processes from `step1`: filter `step1.getStepProcess()` to in-pathway Interactions with defined IRIs.
2. Collect defined processes from `prevStep`: same filter.
3. If `step1` has no defined processes, call `findNearestDefinedProcesses(step1, forward=true, ...)` to find the nearest defined successors.
4. If `prevStep` has no defined processes, call `findNearestDefinedProcesses(prevStep, forward=false, ...)` to find the nearest defined predecessors.
5. Pair the resolved source and target processes, create `causally_upstream_of` links.

The `external_pathway` case is not affected — bridging only applies when `add_reaction.equals("in_pathway")`.

### Deduplication

Multiple paths through the step DAG can converge on the same pair of defined reactions (e.g., A → B → D and A → C → D where B and C are both skipped both yield A → D). The same pair can also be reached from different steps in `getPathwayOrder()`.

A `Set<String>` scoped to the pathway iteration collects `sourceIRI + "|" + targetIRI` strings. Before calling `addRefBackedObjectPropertyAssertion`, check the set and skip if already present.

## Testing

### Results

All 4 previously `@Ignore`d tests were re-evaluated. Bridging does not help them because they all query the skipped reaction as a SPARQL endpoint (not as an intermediate). However, 2 were restored with new example pathways where the relevant reactions have GO terms:

| Test | Outcome | Resolution |
|------|---------|------------|
| `testInferRegulatesViaOutputRegulates` | Still fails (skipped rxn is endpoint) | **Restored** with new pathway R-HSA-1445148, rxns R-HSA-1449597 → R-HSA-2316352 |
| `testInferRegulatesViaOutputEnables` | Still fails (skipped rxn is endpoint) | **Restored** with new pathway R-HSA-110362, rxns R-HSA-5649883 → R-HSA-5651723 |
| `testOccursInFromEntityLocations` | Still fails (queries skipped rxn's location) | Remains `@Ignore`d |
| `testSharedIntermediateInputs` | Still fails (queries skipped rxn's I/O) | Remains `@Ignore`d |

### New test

`testCausalPathBridging` — verifies R-HSA-3601585 bridges to R-HSA-201677 over 2 skipped intermediates (R-HSA-201685 and R-HSA-1504186) in pathway R-HSA-4641262. Queries for any causal relation (RO_0002411, RO_0002413, or RO_0002629) because SPARQL inference rules upgrade `causally_upstream_of` to more specific relations.

### Final test counts

21 tests total, 19 pass, 2 `@Ignore`d.

## Files modified

- `exchange/src/main/java/org/geneontology/gocam/exchange/BioPaxtoGO.java` — new helper method, modified causal linking loop
- `exchange/src/test/java/org/geneontology/gocam/exchange/BioPaxtoGOTest.java` — new bridging test, 2 tests restored with new pathways
- `exchange/src/test/resources/biopax/R-HSA-1445148_level3.owl` — new test BioPAX file for `testInferRegulatesViaOutputRegulates`
- `exchange/src/test/resources/biopax/R-HSA-110362_level3.owl` — new test BioPAX file for `testInferRegulatesViaOutputEnables`