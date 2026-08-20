# Design: Collision-only protein label collapse via ProteinReference

Date: 2026-07-01
Status: REVISED during execution — see "Revision" below. Original "collision-only"
approach superseded by "canonical UniProt label" approach.

## Revision (2026-07-01, during implementation)

Execution revealed the original premise no longer holds. The reported two-label
output (`"Ac-PTGS2" , "PTGS2"`) does **not** reproduce on the current branch:
`collectFlattenedComplexLeaves` (added in HEAD `5304f5e`, the flatten work)
deduplicates the two shared-UniProt components to a single `P35354` individual,
which already carries exactly one `rdfs:label`. The original report predated
`5304f5e`.

The real remaining defect is different: that single surviving label is the
**arbitrary** dedup winner's displayName (`"Ac-PTGS2"`, whichever component
`proteinsByKey.put` writes last over the unordered `getComponent()` set) — not a
deterministic, canonical label. It can flip to `"PTGS2"` run-to-run.

**Revised approach (user-approved):** whenever a `Protein` individual resolves to
a UniProt class, label it from the ProteinReference name embedding the UniProt id
(`"PTGS2"`) instead of its displayName. This is deterministic, matches the UniProt
type, and needs only `getProteinLabelFromReference` (Task 1). The collision
handler and `GoCAM.removeLabels` are **no longer needed** — every UniProt-protein
emission yields the same canonical label, so labels are idempotent by
construction. `removeLabels` and its test are reverted; Task 3 becomes a simple
"prefer canonical label" branch with a test asserting the label equals `"PTGS2"`.

Sections below describe the ORIGINAL design and are retained for history; where
they conflict with this Revision, the Revision governs.

---

## ORIGINAL DESIGN (superseded — retained for history)

Original status: Approved (design), pending implementation plan

## Problem

Converting `exchange/R-HSA-9018679_level3.owl` resolves the enabler complex
`R-HSA-2314687` to a single protein `UniProtKB:P35354`. That complex has two
protein components:

- `Protein1` — displayName `"Ac-PTGS2"` (acetylated form)
- `Protein2` — displayName `"PTGS2"`

Both reference the same `ProteinReference1` (UniProt `P35354`). The emitted
GO-CAM individual therefore receives **two** `rdfs:label` values:

```
<http://www.w3.org/2000/01/rdf-schema#label>
    "Ac-PTGS2" , "PTGS2" ;
```

The required cardinality of `rdfs:label` is 0 or 1, so exactly one label must be
emitted.

## Root cause (confirmed in code)

- `getEntityReferenceId(Protein)` (BioPaxtoGO.java ~L345) resolves a protein via
  its `EntityReference` xrefs. For a UniProt protein with no Reactome/ChEBI
  unification xref it falls through to `db + "_" + id`, yielding
  `"UniProt_P35354"`. Because `Protein1` and `Protein2` share
  `ProteinReference1`, **both return the identical id** `"UniProt_P35354"`.
- `getPhysicalEntityIRI` maps that id to the UniProt class IRI, and individuals
  keyed on the same reference id resolve to the **same** OWL individual.
- Every physical-entity label is added at a **single chokepoint**:
  `defineReactionEntity` (BioPaxtoGO.java ~L1277):

  ```java
  String entity_name = getBioPaxName(entity);   // = displayName
  if (entity_name != null) {
      go_cam.addLabel(e, entity_name);
  }
  ```

  When both components' emissions hit the same individual, each adds its own
  displayName, producing two `rdfs:label` values.

`getBioPaxName` is the only per-physical-entity `addLabel` call site (pathway,
reaction, and REACTO-class labels use separate call sites), so the collision is
always observable at this one location.

## Chosen approach: collision-aware labeling at the chokepoint

Scope decision (approved): **collapse only on collision.** Preserve the
component displayName when only one label lands on an individual; collapse to
the ProteinReference-derived canonical label only when a second, differing label
would be added. This preserves modified-form detail (e.g. `"Ac-PTGS2"`,
`"p-..."`) everywhere it does not collide.

Because line ~1277 reads and writes the live ontology and is the sole labeling
point for protein individuals, the collision is handled there directly — no
separate post-processing pass and no BioPAX reverse lookup are needed. This is
robust regardless of which two emission paths happen to collide on the shared
individual.

### New helper: `getProteinLabelFromReference(Protein) -> String`

Returns the canonical label derived from the ProteinReference name that contains
the UniProt id, or `null` if none is derivable.

- `uniprot = extractUniprotId(protein)` — reuses the existing helper
  (BioPaxtoGO.java ~L2352), returns e.g. `"P35354"`.
- If `uniprot == null` or `protein.getEntityReference() == null`, return `null`.
- Iterate `protein.getEntityReference().getName()`. For the first name that
  contains `uniprot`, return the trimmed substring **after** the id.
  - `"UniProt:P35354 PTGS2"` -> `"PTGS2"`.
  - A name with no trailing symbol (e.g. `"UniProt:P35354"`) yields an empty
    remainder and is skipped.
- If no name yields a non-empty remainder, return `null`.

### New helper: `GoCAM.removeLabels(OWLEntity)`

Removes all `rdfs:label` annotation-assertion axioms for the given entity from
`go_cam_ont`. No such method exists today; it mirrors the existing
`addLabel` / axiom-removal patterns (`ontman.removeAxioms`).

### Replacement label logic (Protein only)

At BioPaxtoGO.java ~L1277, for `entity instanceof Protein`:

```
existing = go_cam.getLabels(e)          // current rdfs:label values on the individual
if none of `existing` differs from displayName:   // 0 labels, or all == displayName
    go_cam.addLabel(e, displayName)                // unchanged behavior
else:                                              // COLLISION
    canonical = getProteinLabelFromReference(protein)
    if canonical == null: canonical = displayName  // fallback for pre-format data (logged)
    go_cam.removeLabels(e)
    go_cam.addLabel(e, canonical)
```

Non-`Protein` entities keep calling `go_cam.addLabel(e, entity_name)` exactly as
today.

### Determinism and convergence

- **Single emission** -> no collision -> displayName preserved (`"Ac-PTGS2"`
  survives where it does not collide).
- **Two differing labels** -> collapse to the ProteinReference canonical
  (`"PTGS2"`), independent of emission order, because both components share
  `ProteinReference1`.
- **Three-or-more-way collisions** converge: each subsequent differing add
  recomputes the same canonical and resets to a single value.
- OWL annotation-assertion axioms are idempotent, so identical labels never
  duplicate.

## Scope / boundaries

- Targets `Protein` individuals backed by a UniProt `ProteinReference` (the
  reported REACTO case).
- Non-protein individuals with multiple labels (if any exist) are **out of
  scope** for this change.
- If the canonical label cannot be derived (older data lacking the
  `"UniProt:<id> <symbol>"` name form), the code falls back to the displayName
  and logs a warning; Reactome data always provides the `"UniProt:<id> ..."`
  name.

## Testing (TDD — failing test written first)

1. **Unit test (primary):** load `R-HSA-9018679_level3.owl` with paxtools;
   assert `getProteinLabelFromReference` returns `"PTGS2"` for both `Protein1`
   and `Protein2`; then drive the collision path on a `GoCAM` individual
   (add `"Ac-PTGS2"`, then emit the second protein) and assert the individual
   ends with exactly one `rdfs:label` equal to `"PTGS2"`.
2. **Integration assertion (optional):** add `R-HSA-9018679_level3.owl` to the
   pipeline test suite (`src/test/resources/biopax/`) and assert via SPARQL that
   the P35354 enabler individual has exactly one `rdfs:label` equal to
   `"PTGS2"`.

## Affected files

- `exchange/src/main/java/org/geneontology/gocam/exchange/BioPaxtoGO.java`
  - new `getProteinLabelFromReference(Protein)`
  - collision-aware label branch at ~L1277
- `exchange/src/main/java/org/geneontology/gocam/exchange/GoCAM.java`
  - new `removeLabels(OWLEntity)`
- `exchange/src/test/java/org/geneontology/gocam/exchange/BioPaxtoGOTest.java`
  (and/or a focused unit test class) + test BioPAX fixture.
