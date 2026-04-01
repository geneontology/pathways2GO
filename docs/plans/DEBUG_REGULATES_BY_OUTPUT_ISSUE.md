# Debugging: testInferRegulatesViaOutputRegulates Test Failure

## Issue Summary

The test `testInferRegulatesViaOutputRegulates` in `BioPaxtoGOTest.java` is failing because the SPARQL query `getInferredRegulatorsQ1` finds 0 results when it should find 1.

## Test Details

- **Test file**: `exchange/src/test/java/org/geneontology/gocam/exchange/BioPaxtoGOTest.java` (lines 795-834)
- **Test pathway**: R-HSA-1810476 "RIP-mediated NFkB activation via ZBP1"
- **BioPAX source**: `src/test/resources/biopax/RIP-mediate.owl`
- **Generated GO-CAM**: `src/test/resources/gocam/RIP-mediate-R-HSA-1810476.ttl`

## Expected Behavior

The test expects:
1. R-HSA-1810457 (reaction1, upstream) `provides_direct_input_for` a binding_reaction
2. That binding_reaction `directly_positively_regulates` R-HSA-168910 (reaction2, downstream)

This pattern should be created by `GoCAM.inferRegulatesViaOutputRegulates()` which uses the SPARQL query `getInferredRegulatorsQ1` (in `query2update_regulation_1.rq`).

## Root Cause Analysis

### What the SPARQL Query Expects

The query `query2update_regulation_1.rq` (line 1251 in GoCAM.java calls `qrunner.getInferredRegulatorsQ1()`) requires:

1. `?reaction1` and `?reaction2` both `part_of` same `?pathway` - **EXISTS**
2. `?reaction2 causally_upstream_of ?reaction1` - **EXISTS** (R-HSA-1810457 → R-HSA-168910)
3. `?entityZ involved_in_positive/negative_regulation_of ?reaction1` - **MISSING**
4. `?reaction2 has_output ?entityOutput` where `?entityOutput` has same type as `?entityZ`

### The Missing Piece

**The `involved_in_positive_regulation_of` relation is not present in the final TTL file.**

### BioPAX Source Data

The BioPAX file contains:
- `Control3` (ID: R-HSA-1810459) with:
  - `controlType = ACTIVATION`
  - `controller = Complex8` (Reactome ID: R-HSA-1810470)
  - `controlled = BiochemicalReaction8` (R-HSA-168910)

- `BiochemicalReaction7` (R-HSA-1810457) has `right = Complex8` (output)

So Complex8 is BOTH the output of R-HSA-1810457 AND the controller that activates R-HSA-168910.

### Debug Findings

Added debug logging to `BioPaxtoGO.java` confirmed:

```
DEBUG_CONTROLLERS	R-HSA-168910	controllers_found=2
DEBUG_CONTROLLER_LOOP	R-HSA-168910	controller=R-HSA-1810459	type=Control
DEBUG_NON_CATALYSIS	R-HSA-168910	controller=R-HSA-1810459	ctype=ACTIVATION
DEBUG_PROCESSING_CONTROLLER_ENTITY	R-HSA-168910	controller_entity=R-HSA-1810470
DEBUG_ADDING_INVOLVED_IN_POS_REG	R-HSA-168910	controller_e=http://model.geneontology.org/R-HSA-1810470_R-HSA-168910_controller
```

**The code IS calling `addRefBackedObjectPropertyAssertion` to add the `involved_in_positive_regulation_of` relation!**

But the relation and controller entity (`R-HSA-1810470_R-HSA-168910_controller`) do NOT appear in the final TTL file.

### Likely Cause: cleanOutUnconnectedNodes()

In `GoCAM.java` line 1972-1998, the function `cleanOutUnconnectedNodes()` removes nodes that don't have object property assertions referencing them:

```java
private void cleanOutUnconnectedNodes() {
    for(OWLNamedIndividual node : nodes) {
        Collection<OWLAxiom> ref_axioms = EntitySearcher.getReferencingAxioms(node, go_cam_ont);
        boolean drop = true;
        for(OWLAxiom a : ref_axioms) {
            if(a.isOfType(AxiomType.OBJECT_PROPERTY_ASSERTION)) {
                drop = false;
                break;
            }
        }
        if(drop) {
            deleteOwlEntityAndAllReferencesToIt(node);
        }
    }
}
```

This function is called at line 997 AFTER `inferRegulatesViaOutputRegulates` at line 985.

**Hypothesis**: The controller entity is being deleted by `cleanOutUnconnectedNodes()` because it's not recognized as having object property assertions, even though `involved_in_positive_regulation_of` was added.

## Debug Logging Added

### BioPaxtoGO.java

1. After line 1537 - logs controller count for each entity
2. Inside controller loop - logs each controller being processed
3. At skip_drug_controller check - logs if skipped
4. At is_catalysis decision - logs control type
5. At relation decision point - logs which branch (catalysis vs regulation)
6. When relations are added - logs the actual addition

### GoCAM.java

Added logging to `cleanOutUnconnectedNodes()` to see which controller entities are being removed and what axioms they have.

## Files Modified (with debug logging)

1. `exchange/src/main/java/org/geneontology/gocam/exchange/BioPaxtoGO.java`
2. `exchange/src/main/java/org/geneontology/gocam/exchange/GoCAM.java`

## Next Steps

1. Run tests with Java 8 (required due to JAXB dependency):
   ```bash
   JAVA_HOME=$(/usr/libexec/java_home -v 1.8) mvn test -Dtest=BioPaxtoGOTest
   ```

2. Capture output and grep for:
   ```bash
   grep -E "DEBUG_REMOVING_CONTROLLER|DEBUG_CONTROLLER_AXIOM" /tmp/test_output.txt
   ```

3. Determine WHY the controller entity is being deleted:
   - Is the object property assertion not being added to the ontology?
   - Is it being added with wrong format (annotation vs object property)?
   - Is something else deleting it before cleanup?

4. Once root cause is confirmed, fix the issue - likely need to ensure the `involved_in_*_regulation_of` assertion is properly added as an ObjectPropertyAssertion that survives cleanup.

## Key Files

- `exchange/src/main/java/org/geneontology/gocam/exchange/BioPaxtoGO.java` - Controller processing at lines 1537-1787
- `exchange/src/main/java/org/geneontology/gocam/exchange/GoCAM.java` - Inference rules at lines 1248-1294, cleanup at 1972-1998
- `exchange/src/main/java/org/geneontology/gocam/exchange/QRunner.java` - SPARQL query at lines 461-488
- `exchange/src/main/resources/org/geneontology/gocam/exchange/query2update_regulation_1.rq` - The SPARQL query

## Key Relationships (OBO IDs)

- `RO_0002429` - involved_in_positive_regulation_of
- `RO_0002430` - involved_in_negative_regulation_of
- `RO_0002411` - causally_upstream_of
- `RO_0002413` - provides_direct_input_for
- `RO_0002629` - directly_positively_regulates
- `BFO_0000050` - part_of
- `RO_0002234` - has_output

---

## Session 2 Findings (2026-01-29)

### Test Result Confirmed

The test is definitively failing:
```
Tests run: 1, Failures: 1, Errors: 0, Skipped: 0
java.lang.AssertionError: should have been 1, but got n results: 0
```

### TTL File Analysis

Examined the generated TTL file (`src/test/resources/gocam/RIP-mediate-R-HSA-1810476.ttl`):

1. **Controller entity `R-HSA-1810470_R-HSA-168910_controller` is NOT present** in the final TTL
2. **No `RO_0002429` (involved_in_positive_regulation_of) relations exist** in the TTL file at all
3. The entity `R-HSA-1810470` does appear in OTHER contexts (e.g., as output of R-HSA-1810457)

### Code Flow Analysis

Traced the code path in BioPaxtoGO.java (lines 1673-1785):

1. **Line 1677**: IRI created as `controller_entity_id+"_"+entity_id+"_controller"`
2. **Line 1679**: `controller_e = go_cam.df.getOWLNamedIndividual(iri)` - creates OWL individual object
3. **Line 1726**: When no active_sites, calls `defineReactionEntity(go_cam, controller_entity, iri, ...)`
4. **Line 1770-1771**: For ACTIVATION type, calls `addRefBackedObjectPropertyAssertion(controller_e, involved_in_positive_regulation_of, e, ...)`

### Key Observation

At line 1679, `controller_e` is created with `df.getOWLNamedIndividual(iri)` which only creates an in-memory reference - it does NOT add the individual to the ontology.

Then `defineReactionEntity` is called at line 1726 which DOES add the individual to the ontology via `makeAnnotatedIndividual`.

The `addRefBackedObjectPropertyAssertion` at line 1771 uses the `controller_e` reference, which should work since both refer to the same IRI.

### SPARQL Query Requirements

The query in `query2update_regulation_1.rq` needs:
```sparql
?entityZ ?prop ?reaction1 .   # where ?prop is RO_0002429 or RO_0002430
?entityZ rdf:type ?ztype .
filter(?ztype != owl:NamedIndividual) .
```

So the query needs:
1. An entity with `involved_in_positive_regulation_of` relation to the reaction
2. That entity must have a TYPE (not just be a NamedIndividual)

### Refined Hypothesis

The controller entity might be getting removed because:
1. It has the `involved_in_positive_regulation_of` ObjectPropertyAssertion added
2. BUT `cleanOutUnconnectedNodes()` uses `EntitySearcher.getReferencingAxioms()` which returns axioms that **reference** the entity
3. An ObjectPropertyAssertion where the entity is the **subject** (not object) might not count as "referencing" the entity in OWLAPI's interpretation

**Alternative hypothesis**: The entity might lack a proper type assertion beyond `owl:NamedIndividual`, causing the SPARQL query to filter it out even if it survives cleanup.

### Debug Output from Test Run

The test processes multiple pathways. Debug logging shows `DEBUG_ADDING_INVOLVED_IN_POS_REG` being called for various controller entities, confirming the code path is executed:
```
DEBUG_ADDING_INVOLVED_IN_POS_REG	R-HSA-163743	controller_e=http://model.geneontology.org/R-HSA-163700_R-HSA-163743_controller
DEBUG_ADDING_INVOLVED_IN_POS_REG	R-HSA-381727	controller_e=http://model.geneontology.org/R-HSA-381702_R-HSA-381727_controller
```

### Next Steps

1. **Add more targeted debug logging** to capture:
   - What type assertions exist on controller_e after `defineReactionEntity`
   - Whether the ObjectPropertyAssertion is present before `cleanOutUnconnectedNodes`
   - Whether it survives cleanup

2. **Check `defineReactionEntity`** to see what type it assigns to controller entities (complexes vs proteins)

3. **Run SPARQL query manually** on the generated TTL to see what's missing:
   ```sparql
   SELECT ?entity ?type WHERE {
     ?entity <http://purl.obolibrary.org/obo/RO_0002429> ?reaction .
     ?entity rdf:type ?type .
   }
   ```

4. **Consider if the issue is in the query itself** - maybe it's too restrictive

### Commands to Run

```bash
# Run test with full output
cd exchange && JAVA_HOME=$(/usr/libexec/java_home -v 1.8) mvn test -Dtest=BioPaxtoGOTest#testInferRegulatesViaOutputRegulates 2>&1 | tee /tmp/full_test.txt

# Search for specific pathway processing
grep -E "1810476|1810470|1810457|168910|RIP" /tmp/full_test.txt

# Search for controller removal
grep -E "DEBUG_REMOVING_CONTROLLER|DEBUG_CONTROLLER_AXIOM" /tmp/full_test.txt

# Check TTL for involved_in relations
grep "RO_0002429\|RO_0002430" src/test/resources/gocam/RIP-mediate-R-HSA-1810476.ttl
```

---

## Session 3 Findings (2026-01-29) - ROOT CAUSE IDENTIFIED

### Root Cause: Type Mismatch Between Output and Controller Individuals

The SPARQL query `query2update_regulation_1.rq` (Part 2) requires output and controller individuals to share a **common type**:

```sparql
{
  ?reaction2 obo:RO_0002234 ?entityOutput .
  ?entityOutput rdf:type ?ztype .
  ?entityZ ?prop  ?reaction1 .
}
?entityZ rdf:type ?ztype .   # REQUIRES same type as entityOutput
filter(?ztype != owl:NamedIndividual) .
```

**The Problem:**

1. **Output individual** (R-HSA-1810470, created with `explode_sets_complexes = false`):
   - Created at line 1383: `defineReactionEntity(go_cam, output, o_iri, true, model_id, root_pathway_iri, reaction_id, false)`
   - `getPhysicalEntityIRI(entity)` returns `REACTO:R-HSA-1810470`
   - **Type: `http://purl.obolibrary.org/obo/REACTO_R-HSA-1810470`**

2. **Controller individual** (R-HSA-1810470_R-HSA-168910_controller, created with `explode_sets_complexes = true`):
   - Created at line 1726: `defineReactionEntity(go_cam, controller_entity, iri, true, model_id, root_pathway_iri, reaction_id, true)`
   - Because `explode_sets_complexes = true` and entity is a Complex, line 1158 overrides:
     `entity_class_iri = IRI.create("http://purl.obolibrary.org/obo/GO_0032991")`
   - **Type: `http://purl.obolibrary.org/obo/GO_0032991` (protein-containing complex)**

**The query requires `?entityOutput rdf:type ?ztype` AND `?entityZ rdf:type ?ztype` to match the same type. Since the output has type `REACTO:R-HSA-1810470` and the controller has type `GO_0032991`, they don't share a type, so the query returns 0 results.**

### Code Path Analysis

In `BioPaxtoGO.defineReactionEntity()`:

```java
// Line 1151-1219
if(entity instanceof PhysicalEntity) {
    IRI entity_class_iri = getPhysicalEntityIRI(entity);  // Gets REACTO:R-HSA-{id}

    if (explode_sets_complexes) {
        if(entity instanceof Complex && !(((Complex) entity).getComponent().isEmpty())) {
            // OVERRIDE: Changes type to GO_0032991
            entity_class_iri = IRI.create("http://purl.obolibrary.org/obo/GO_0032991");
            // ... process components ...
        }
    }

    OWLClass entity_class = go_cam.df.getOWLClass(entity_class_iri);
    go_cam.addTypeAssertion(e, entity_class);  // Adds the type
}
```

When `explode_sets_complexes = true` (for controllers), Complex types are overridden to `GO_0032991`.
When `explode_sets_complexes = false` (for outputs), the original `REACTO:R-HSA-{id}` type is used.

### Potential Fixes

1. **Add REACTO type to controller individuals as additional type** - Ensure controller individuals have BOTH `GO_0032991` AND `REACTO:R-HSA-{id}` types

2. **Don't override type for complexes** - Remove or modify line 1158 so complexes keep their REACTO type even when `explode_sets_complexes = true`

3. **Use skos:exactMatch instead of type in query** - Both individuals have `skos:exactMatch` pointing to the same BioPAX entity URI. Modify the SPARQL query to match on this instead of type.

4. **Ensure same individual is used** - Instead of creating separate output and controller individuals, reuse the same individual with the same IRI.

### Recommended Fix

Option 1 seems safest - add the REACTO type as an **additional** type to controller individuals without removing `GO_0032991`. This preserves existing semantics while enabling the SPARQL query to match.

In `defineReactionEntity`, after line 1219, add:
```java
// For complexes with explode_sets_complexes=true, also add the original REACTO type
if (explode_sets_complexes && entity instanceof Complex) {
    IRI original_type_iri = getPhysicalEntityIRI(entity);
    OWLClass original_type = go_cam.df.getOWLClass(original_type_iri);
    go_cam.addTypeAssertion(e, original_type);
}
```

### Key Files for Fix

- `BioPaxtoGO.java` line 1156-1219 - Where type is set for physical entities
- `query2update_regulation_1.rq` - The SPARQL query that requires matching types

---

## Fix Applied (2026-01-29)

**Chosen fix:** Option 3 - Use `skos:exactMatch` instead of `rdf:type` in query

Modified `query2update_regulation_1.rq` to match output and regulator individuals via their shared `skos:exactMatch` to the same BioPAX entity URI, rather than requiring them to share a common `rdf:type`.

**Part 1 change:** (complex has_part matching)
```sparql
# Before:
?outputEntityComplex rdf:type ?outputEntityComplexType .
?inputEntityComplex rdf:type ?outputEntityComplexType .

# After:
?outputEntityComplex skos:exactMatch ?biopax_complex .
?inputEntityComplex skos:exactMatch ?biopax_complex .
```

**Part 2 change:** (direct entity matching)
```sparql
# Before:
?entityOutput rdf:type ?ztype .
# (entityZ matched to entityOutput by having same type ?ztype)

# After:
?entityOutput skos:exactMatch ?biopax_entity .
?entityZ skos:exactMatch ?biopax_entity .
```

This works because `defineReactionEntity()` at line 1144 adds:
```java
go_cam.addUriAnnotations2Individual(e.getIRI(), GoCAM.skos_exact_match, IRI.create(entity.getUri()));
```

Both output and controller individuals get `skos:exactMatch` pointing to the same BioPAX entity URI (e.g., the BioPAX Complex8 URI), enabling the query to match them.

---

## Status: FIX APPLIED

**Date:** 2026-01-29

**Fix:** Modified `query2update_regulation_1.rq` to use `skos:exactMatch` instead of `rdf:type` for matching output and regulator individuals.

**File changed:** `exchange/src/main/resources/org/geneontology/gocam/exchange/query2update_regulation_1.rq`

**Next step:** Run `testInferRegulatesViaOutputRegulates` to verify the fix works.