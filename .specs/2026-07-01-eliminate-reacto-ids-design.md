# Eliminate REACTO IDs From Emitted Models — Design

**Date:** 2026-07-01
**Status:** Approved (brainstorm complete). Ready for implementation planning.
**Branch context:** `issue-352-sets-to-diamonds` (follows the flatten-complex-enabler and
skip-regulators line of work).

## Goal

Guarantee that **no REACTO physical-entity IRI survives in any emitted GO-CAM model**. Concretely:
add a final ABox cleanup pass that deletes every individual still typed with a
`http://purl.obolibrary.org/obo/go/extensions/reacto.owl#REACTO_…` class, and removes the now-dangling
`REACTO_` class declarations, so a converted model's output graph contains zero `REACTO_` IRIs.

## Background / current behavior

During conversion, physical-entity individuals are typed by `BioPaxtoGO.getPhysicalEntityIRI()`
(`BioPaxtoGO.java:~2123`) with **exactly one** class, chosen by kind:

- Small molecules → a CHEBI class (`obo/CHEBI_…`) — non-REACTO.
- Proteins with a UniProt reference → a UniProt class (`GoCAM.uniprot_iri…`) — non-REACTO.
- Complex enablers that get flattened → a fresh `GO_0032991` (protein-containing complex) node —
  non-REACTO (created by the completed flatten feature; verified present with **no** REACTO type).
- Everything else (unflattened complexes, entity-sets, entities lacking an external xref) →
  a `reacto.owl#REACTO_<reactome-id>` class — **REACTO**.

The emitted TTL is the plain ABox (`GoCAM.writeGoCAM_jena` → `qrunner.dumpModel`, written at
`BioPaxtoGO.java:607`); it does **not** inline the reacto.owl tbox, so a `REACTO_` class appears only as
(a) the `rdf:type` object on one or more individuals and (b) a bare `<…REACTO_x> a owl:Class`
declaration. Nothing in the emitted model links a `REACTO_` class to a non-REACTO class — that linkage
lives in reacto.owl (loaded in production via `-lego go-lego-reacto.owl`, absent from the test tbox).

**Key consequence of the above:** an individual still typed `REACTO_x` is, by construction, one that was
*not* resolved to CHEBI/UniProt/GO during typing. There is therefore no per-model resolution step to run;
"unresolvable" == "still REACTO-typed". (This is the decision recorded under *Decisions*, option
"delete any still-REACTO individual".)

The pipeline already has a family of final-stage deletion passes in `GoCAM.applySparqlRules`
(`GoCAM.java:976`), run **after** all inference so that inference which *consumes* entities before
deleting them (regulation, complex flattening, small-molecule regulators) is not disturbed:

```
inferMolecularFunctionFromEnablers → inferOccursInFromEntityLocations →
inferRegulatesViaOutputRegulates → inferRegulatesViaOutputEnables → inferProvidesInput →
inferSmallMoleculeRegulators → deleteComplexesWithActiveUnits → deleteDisallowedRelations →
cleanOutUnconnectedNodes
```

Sibling deletes operate directly on `go_cam_ont` via `deleteOwlEntityAndAllReferencesToIt(...)` and
resync with `qrunner = new QRunner(go_cam_ont)` (see `deleteDisallowedRelations`, `GoCAM.java:1809`).
`cleanOutUnconnectedNodes` (`GoCAM.java:2078`) iterates `go_cam_ont` directly and drops individuals that
have no remaining object-property assertion (evidence nodes excluded).

## Decisions (from brainstorm)

1. **Behavior:** delete any individual still typed with a REACTO class. No retype/resolution step — the
   resolvable entities were already typed CHEBI/UniProt/GO upstream. (Fully satisfies "eliminate all
   REACTO IDs" because the salvageable ones are already non-REACTO.)
2. **Scope:** `reacto.owl#REACTO_`-prefixed physical-entity classes **only**. `reacto.owl#molecular_event`
   is explicitly **excluded** — it is a curated placeholder (a *superclass* of GO:molecular_function,
   `PhysicalEntityOntologyBuilder.java:285`), not an opaque unresolved entity ID; it also types inferred
   regulation binding-nodes (`GoCAM.java:1634`). (Production models no longer emit `molecular_event`, so
   no separate handling is needed.)
3. **Placement/mechanism:** Approach 1 — a new `GoCAM` method invoked inside `applySparqlRules`, after the
   existing deletes and before `cleanOutUnconnectedNodes`.

## Design

### New method: `GoCAM.deleteReactoTypedIndividuals()`

Pure ABox operation on `go_cam_ont`; does not depend on `qrunner.jena` for finding targets (avoids any
stale-Jena concern). Steps:

1. **Find targets.** Iterate `go_cam_ont.getAxioms(AxiomType.CLASS_ASSERTION)`. For each axiom whose class
   expression is a **named** class whose IRI `startsWith(GoCAM.reacto_base_iri.toString())`
   (`…/reacto.owl#REACTO_`), collect the (named) individual. Collect into a `Set` first, then delete —
   don't mutate the ontology while iterating its axioms.
   - This prefix match **excludes** `molecular_event` (`…#molecular_event` does not start with `…#REACTO_`).
   - For YeastCyc models there are no `REACTO_` types, so the target set is empty — the pass is a natural
     no-op. **No `entityStrategy` guard is required.**
2. **Delete individuals.** For each target, call `deleteOwlEntityAndAllReferencesToIt(individual)` (the
   one-arg overload, `GoCAM.java:880`, which delegates with `delete_related_nodes=false`). This removes the
   individual and every axiom referencing it — its `has_input`/`has_output`/`enabled_by`/regulation edges
   and their evidence-annotated axioms — while **leaving the reactions intact** (reactions are typed with GO
   MF classes or `molecular_event`, never `REACTO_`, so they are not targets, and `delete_related_nodes=false`
   means deletion does not cascade into the reaction on the other end of an edge).
3. **Sweep dangling `REACTO_` class declarations.** After the individuals are gone, iterate a snapshot of
   `go_cam_ont.getClassesInSignature()`; for each class whose IRI `startsWith` the REACTO prefix, call
   `deleteOwlEntityAndAllReferencesToIt(class)`. This removes the `<…REACTO_x> a owl:Class` declarations (and
   any residual axioms mentioning the class) so **no REACTO IRI remains anywhere** in the output — not just
   as an `rdf:type` object.
4. **Resync.** `qrunner = new QRunner(go_cam_ont);` — matching `deleteDisallowedRelations`.

The individuals carry exactly one physical-entity class (verified: no individual is typed both `REACTO_x`
and a non-REACTO class), so unconditional deletion is correct — there is no "also has a resolved type"
case to preserve.

### Call site

In `GoCAM.applySparqlRules` (`GoCAM.java:976`), insert the call **between** `deleteDisallowedRelations()`
(`:997`) and `cleanOutUnconnectedNodes()` (`:999`):

```java
deleteDisallowedRelations();
deleteReactoTypedIndividuals();   // NEW: remove all still-REACTO physical-entity individuals + class decls
cleanOutUnconnectedNodes();       // sweeps nodes orphaned by the deletion above
```

Rationale for this position:
- **After** `inferSmallMoleculeRegulators`, `deleteComplexesWithActiveUnits`, and `deleteDisallowedRelations`
  — so genuine small-molecule regulators (already retyped to CHEBI) and flattened complex enablers (already
  a `GO_0032991` node) are non-REACTO by the time this runs, and any inference that consumes REACTO entities
  has already happened. Respects the "emit-then-delete is load-bearing" lesson from the skip-regulators work.
- **Before** `cleanOutUnconnectedNodes` — so BP nodes / other individuals left dangling by the removal of a
  REACTO participant get swept in the existing pass.

## Consequences to accept (per the chosen behavior)

- A reaction whose enabler is a REACTO complex/entity-set that **could not be flattened** loses its
  `enabled_by` edge; the activity individual remains (still has its MF type / other edges), now unenabled.
  This is the direct, intended result of "delete any still-REACTO individual".
- A reaction input/output that is a still-REACTO participant is removed as a participant (edge dropped);
  the reaction itself stays.

## Testing

**New regression test** (`BioPaxtoGOTest`), on a fixture known to currently emit `REACTO_` individuals
(e.g. `R-HSA-189451`, which today emits `REACTO_R-HSA-189400` etc.):

1. **Zero REACTO as `rdf:type` object:**
   `SELECT ?s WHERE { GRAPH <model> { ?s rdf:type ?c . FILTER(STRSTARTS(STR(?c),
   "http://purl.obolibrary.org/obo/go/extensions/reacto.owl#REACTO_")) } }` → count 0.
2. **Zero REACTO as subject** (class declarations swept):
   `SELECT ?c WHERE { GRAPH <model> { ?c ?p ?o . FILTER(STRSTARTS(STR(?c), "…#REACTO_")) } }` → count 0.
3. **Survival check** (guards against vacuous pass / over-deletion): a known reaction in the model is
   still present and still carries its GO molecular-function type — i.e. the pass removed participants, not
   reactions.

(Follow the existing `countSolutions(String)` helper / Blazegraph-per-model `@BeforeClass` pattern used
throughout `BioPaxtoGOTest`.)

**Existing tests — impact assessment:**
- `BioPaxtoGOTest.java:1368` (`testComplexCofactorReducedToSingleProtein`) and `:1423` /
  `testComplexEnablerFlattenedToPCC` — both are **absence** assertions (`assertEquals(0, …)`) about REACTO
  types. Deleting REACTO individuals keeps these at 0 → **stay green**.
- `:1974` (`testOverCapComplexDropsReactoSetNode`) — also an **absence** assertion (`reactoN == 0`,
  scoped to the enabler's `has_part`). Deletion keeps it 0 → **stays green**. Note, however, that its
  inline comment (lines ~1964-1969) documents an expectation that the dropped set "may still legitimately
  appear as a reaction input/output participant … and KEEPS its REACTO class" — that expectation is now
  **overridden** by this feature (such participants are deleted). The assertion itself does not depend on
  that survival, so it does not break; the stale comment should be updated during implementation.
- `QRunnerPartToComplexIndexTest` — a tbox part→complex index unit test using synthetic REACTO classes in a
  tbox, unrelated to emitted-model ABox. **Unaffected.**
- **Broader check during implementation:** scan `BioPaxtoGOTest` for any assertion that a specific
  *participant edge* is **present** (e.g. `reaction has_input/has_output/enabled_by <individual>`) where the
  target individual is REACTO-typed — deleting the individual drops that edge and would fail such a test.
  (None found among the REACTO-type assertions, which are all absence checks, but participant-by-IRI
  assertions must be swept when running the full suite.)
- Regulation/flatten anchors that must stay green: `testInferRegulatesViaOutputRegulates`,
  `testInferSmallMoleculeRegulators`, `testComplexRegulatorLeavesNoOrphanComponents`,
  `testCausalPathBridging`.

**Update (2026-07-02) — `testInferRegulatesViaOutputEnables`:** during implementation this anchor was
found to regress, because reaction2 (`R-HSA-5651723`) is `enabled_by` a REACTO-typed protein-set active
unit (`REACTO_R-HSA-5649876`, "PARP1,PARP2") that this feature deletes. The regulation relation itself
(`reaction1 RO_0002629 reaction2`) does not reference the enabler and survives; only the test's
`?reaction2 RO_0002333 ?active_part` enabler-presence clause fails. Per user decision, the anchor is
**updated to match the new behavior**: drop that enabler-presence clause so the test verifies the
regulation inference (`RO_0002629` + `BFO_0000050` pathway) without requiring the now-deleted REACTO
enabler to survive. This resolves the earlier internal contradiction (the deletion of REACTO enablers is
an accepted consequence, so an anchor that required a REACTO enabler to persist must be revised).

## Out of scope

- Any resolution/retyping of REACTO classes to CHEBI/GO/UniProt (rejected in favor of "delete
  still-REACTO"; resolvable entities are already non-REACTO upstream).
- `molecular_event` handling (explicitly excluded; not emitted in current production models).
- Causal-path bridging for reactions that lose a participant (reactions are not deleted, so their causal
  chains are untouched; only participant/enabler individuals are removed).
- Changes to `getPhysicalEntityIRI` typing or to the flatten / skip-regulator features.

## Constraints & anchors

- **Do NOT `git commit` / `git add`** (project instruction). Leave the tree modified for review.
- Entity strategy under test is **REACTO**; test go-lego is `./src/test/resources/ontology/go-lego-no-neo.owl`.
- Maven from `exchange/`: `cd exchange && mvn …`; heap preconfigured (`-Xmx8g`). `BioPaxtoGOTest`'s
  `@BeforeClass` converts every `.owl` in `src/test/resources/biopax/` into a Blazegraph journal; tests query
  each model's named graph `<http://model.geneontology.org/<model_id>>`. (surefire 2.12.4 can't parse
  `+`-combined `-Dtest` selectors — run the whole class.)

## Key code references

- `GoCAM.reacto_base_iri` — `GoCAM.java:118` (the `…#REACTO_` prefix to match).
- `GoCAM.applySparqlRules` — `GoCAM.java:976` (insert call at ~:998).
- `GoCAM.deleteOwlEntityAndAllReferencesToIt(OWLEntity)` — `GoCAM.java:880` (one-arg; delegates
  `delete_related_nodes=false` at `:881`; full impl `:887`).
- `GoCAM.deleteDisallowedRelations` — `GoCAM.java:1787` (sibling delete + QRunner-rebuild pattern to mirror).
- `GoCAM.cleanOutUnconnectedNodes` — `GoCAM.java:2078` (orphan sweep that runs after).
- `BioPaxtoGO.getPhysicalEntityIRI` — `BioPaxtoGO.java:~2123` (where REACTO vs CHEBI/UniProt typing is chosen).
- `BioPaxtoGO.wrapAndWrite` — `BioPaxtoGO.java:552` (calls `applySparqlRules` at `:567`, writes at `:607`).
