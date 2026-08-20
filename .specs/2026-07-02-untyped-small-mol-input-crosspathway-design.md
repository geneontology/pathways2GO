# Design: Untyped small-molecule inputs from cross-pathway reuse

**Date:** 2026-07-02
**Status:** Implemented
**Component:** `exchange/src/main/java/org/geneontology/gocam/exchange/BioPaxtoGO.java`
**Supersedes:** the input-reuse guard proposed in
`.specs/2026-06-29-type-reused-small-mol-inputs-design.md` (that guard is a
strict subset of this change and is removed here).
**Related:** #324 (skip molecular events), #352 (split set-enabled reactions).

## Problem

A GO-CAM `has_input` individual can be emitted with no class (only
`owl:NamedIndividual`). ShEx validation flags it as "typed as untyped".

Concrete case (full-`Homo_sapiens.owl` run, model `R-HSA-8964539`,
"Glutamate and glutamine metabolism"):

- `R-ALL-113587_mitochondrial_matrix` — oxaloacetate (OA, `CHEBI:16452`) — is a
  `has_input` (RO:0002233) of `R-HSA-70613` ("GOT2 transaminates OA and L-Glu").
- In the emitted model it carries no class, and — unlike its typed siblings
  (L-Glu → `CHEBI_29985`, etc.) — it lacks the `"Original Reactome ID: …"` comment
  that `defineReactionEntity` stamps. So `defineReactionEntity` never ran for it.

## Root cause

`defineReactionEntity`'s input loop had a small-molecule **reuse shortcut**: for a
small-molecule input, it looked at `pathway_step.getNextStepOf()` (preceding
steps), and if a preceding reaction produced the same molecule it grabbed a
**bare** `getOWLNamedIndividual(iri)` — no `defineReactionEntity` call, no type —
on the assumption that the producing reaction would type the shared node.

That assumption fails across pathways. OA is produced by MDH2 / PC, which live in
the **Malate-aspartate shuttle** (`R-HSA-9856872`). `R-HSA-70613` belongs to
*both* the shuttle and 8964539, so:

- `getNextStepOf()` returns MDH2's step (a preceding event in the shuttle).
- MDH2 has an EC number → a valid GO term, so the `#324` `hasNoGoTerm()` guard
  from the 2026-06-29 spec does **not** skip it.
- `processesAreInSamePathway(GOT2, MDH2)` is true — they share the shuttle.
- So reuse fires and grabs `R-ALL-113587_mitochondrial_matrix` bare.
- But MDH2 is **not** a component of 8964539, so it is never emitted into this
  model — nothing ever types the node. It ships as bare `owl:NamedIndividual`.

The prior guard only covered producers dropped by the GO-term gate. It does not
cover a producer that has a valid GO term but is simply **not part of the model
being built**. `processesAreInSamePathway` is too permissive: sharing *any*
pathway ≠ the producer being emitted into *this* pathway's model.

## Chosen fix: remove the input-reuse shortcut

Delete the small-molecule input-reuse block so every input (small-molecule or
not) always flows through `defineReactionEntity`, which types it. Also remove the
now-dead `pathway_step` / `previous_steps` locals.

### Why this is correct and safe

- **Always typed.** The input loop is reached only for reactions that pass the
  GO-term gate (the early return in `defineReactionEntity` for no-GO-term
  `Interaction`s runs first). Every input it creates is defined via
  `defineReactionEntity` and immediately linked with `has_input`.
- **Node-sharing preserved.** Both the input and output paths build the same
  deterministic IRI `input_id + "_" + input_location`. A producer's `has_output`
  and a consumer's `has_input` still resolve to one shared individual, now typed.
  Re-running `defineReactionEntity` on the shared IRI is idempotent (OWL
  type/annotation axioms are set-valued).
- **No orphans.** A dropped molecular-event reaction returns before its
  input/output loops run, so it never creates any participant individual —
  nothing to orphan. Every created small-molecule individual is created inside an
  emitted reaction's loop and immediately gets a `has_input`/`has_output` edge.
- **Subsumes the `#324` fix.** The `R-HSA-189451` dALA case (a dropped
  same-pathway producer) is still typed — via the fresh path instead of the guard.

### Cost

`defineReactionEntity` re-runs on already-defined shared nodes (idempotent,
negligible). For the rare "set of small molecules" input the two calls pass a
different `component_top_level_id`; this only affects set-member recursion and is
out of scope.

## Secondary fix: `getActiveSites` null-guard

While building the test fixture (an extracted 2-pathway sub-model) the shuttle
conversion NPE'd in `getActiveSites`: it reconstructs an active-unit protein URI
as `biopax_model.getXmlBase() + localId` from a plain-text `activeUnit:` comment
and did `active_sites.add((PhysicalEntity) getByID(...))` with no null check. Any
unresolvable reference (absent entity, or mismatched `xml:base`) added a `null`,
which NPE'd downstream in `defineReactionEntity` via `getEntityReferenceId(null)`
and aborted the entire conversion. Now unresolved references are skipped and
logged (`UNRESOLVED_ACTIVE_UNIT`). This is a general robustness fix, independent
of the input-typing change.

## Testing

- `BioPaxtoGOTest#testSmallMoleculeInputTypedNotBareViaCrossPathwayReuse` —
  converts `src/test/resources/biopax/R-HSA-8964539_shuttle_level3.owl` (pathway
  8964539 + the Malate-aspartate shuttle, extracted from the full biopax via
  paxtools so the cross-pathway OA producer is present) and asserts the
  `R-HSA-70613` `has_input` for `R-ALL-113587` carries a CHEBI class. Failed
  before, passes after. Full suite: 33 tests, 0 failures.
- `SetEnabledReactionSplitTest#testGetActiveSitesSkipsUnresolvableActiveUnit` —
  a Control with an unresolvable `activeUnit:` comment yields no null active site.

## Out of scope

- Memoizing GO-term resolution.
- Re-introducing skipped molecular events or their participants.
- Any change to output handling or non-small-molecule entity typing.
