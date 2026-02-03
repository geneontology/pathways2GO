# Filter partToComplexIndex Keys to REACTO Classes Only

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Optimize `buildPartToComplexIndex` to only store REACTO/UniProt keys, reducing memory usage and improving initialization time.

---

## Progress

| Step | Status | Notes |
|------|--------|-------|
| Step 1: Write failing test | ✅ Done | Created `QRunnerPartToComplexIndexTest.java` |
| Step 2: Verify test fails | ✅ Done | Test failed with "CHEBI molecule should NOT be in index" |
| Step 3: Modify collectHasPartFillers | ✅ Done | Added prefix filtering to all filler extraction branches |
| Step 4: Update buildPartToComplexIndex calls | ✅ Done | Pass reactoPrefix, uniprotPrefix to collectHasPartFillers |
| Step 5: Verify test passes | ✅ Done | Test passes, index now shows "1 part classes" instead of "2" |
| Step 6: Run regression tests | ✅ Done | Tests pass without separate reacto.owl loading |
| Step 7: Commit | ⏳ Pending | Files ready to stage and commit |
| Step 8: Remove reacto-tbox option | ✅ Done | Removed from cmdline and tests - not needed |

**Architecture:** The current implementation correctly filters *complex classes* (the values) to REACTO/UniProt, but stores *all* part classes as keys regardless of their namespace. Since the lookup method `getComplexClassesWithPart` is only ever called with REACTO/UniProt classes (from enabler types in GO-CAM models), we can safely filter keys to only store REACTO/UniProt classes, discarding non-REACTO part fillers.

**Tech Stack:** Java, OWL API

---

## Analysis

### Current Behavior (QRunner.java:950-1027)

1. **Outer loop (lines 963-968)**: Iterates over `tbox.getClassesInSignature()` and filters to only process REACTO/UniProt classes as potential **complexes**
2. **Part collection (lines 970-982)**: Calls `collectHasPartFillers` which adds *any* class found as a `has_part` filler to the `parts` set - no filtering
3. **Index population (lines 985-990)**: Stores `partClass -> complexClass` where `partClass` can be any IRI

### Problem

If a REACTO complex has `has_part some CHEBI:12345` or `has_part some GO:0000123`, those non-REACTO classes become keys in `partToComplexIndex`. But these keys are never queried because:

- `getComplexClassesWithPart` is called from `GoCAM.java:1428`
- The input comes from `tbox_qrunner.getSubClasses(typeClass, false)` where `typeClass` is an enabler type
- Enabler types in GO-CAM models are always REACTO or UniProt classes

### Solution

Filter `parts` in `collectHasPartFillers` to only include classes with REACTO or UniProt prefixes.

---

### Task 1: Add REACTO/UniProt Prefix Check to collectHasPartFillers

**Files:**
- Modify: `exchange/src/main/java/org/geneontology/gocam/exchange/QRunner.java:1034-1072`

**Step 1: Write the failing test**

Add a test that verifies only REACTO/UniProt keys are stored in the index.

Create test in `exchange/src/test/java/org/geneontology/gocam/exchange/QRunnerPartToComplexIndexTest.java`:

```java
package org.geneontology.gocam.exchange;

import static org.junit.Assert.*;

import java.util.Set;

import org.junit.Test;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.*;

/**
 * Test that partToComplexIndex only stores REACTO/UniProt keys
 */
public class QRunnerPartToComplexIndexTest {

    private static final String REACTO_PREFIX = "http://purl.obolibrary.org/obo/go/extensions/reacto.owl#REACTO_";
    private static final String UNIPROT_PREFIX = "http://identifiers.org/uniprot/";

    @Test
    public void testPartToComplexIndexOnlyContainsReactoOrUniprotKeys() throws Exception {
        // This test verifies the fix for filtering non-REACTO keys
        // We'll create a minimal ontology with a REACTO complex that has:
        // 1. A REACTO protein part (should be indexed)
        // 2. A CHEBI small molecule part (should NOT be indexed)

        OWLOntologyManager manager = OWLManager.createOWLOntologyManager();
        OWLOntology tbox = manager.createOntology();
        OWLDataFactory df = manager.getOWLDataFactory();

        // Create classes
        OWLClass reactoComplex = df.getOWLClass(IRI.create(REACTO_PREFIX + "R-HSA-12345"));
        OWLClass reactoProtein = df.getOWLClass(IRI.create(REACTO_PREFIX + "R-HSA-67890"));
        OWLClass chebiMolecule = df.getOWLClass(IRI.create("http://purl.obolibrary.org/obo/CHEBI_12345"));

        // Create has_part property
        OWLObjectProperty hasPart = df.getOWLObjectProperty(IRI.create("http://purl.obolibrary.org/obo/BFO_0000051"));

        // Create axiom: reactoComplex EquivalentTo (has_part some reactoProtein) and (has_part some chebiMolecule)
        OWLClassExpression hasReactoProtein = df.getOWLObjectSomeValuesFrom(hasPart, reactoProtein);
        OWLClassExpression hasChebiMolecule = df.getOWLObjectSomeValuesFrom(hasPart, chebiMolecule);
        OWLClassExpression intersection = df.getOWLObjectIntersectionOf(hasReactoProtein, hasChebiMolecule);
        OWLAxiom eqAxiom = df.getOWLEquivalentClassesAxiom(reactoComplex, intersection);
        manager.addAxiom(tbox, eqAxiom);

        // Create QRunner with this tbox
        java.util.Collection<OWLOntology> tboxes = java.util.Collections.singletonList(tbox);
        QRunner qrunner = new QRunner(tboxes, null, null, true, false, false);

        // Verify: reactoProtein should map to reactoComplex
        Set<OWLClass> complexesForProtein = qrunner.getComplexClassesWithPart(reactoProtein);
        assertTrue("REACTO protein should be found in index", complexesForProtein.contains(reactoComplex));

        // Verify: chebiMolecule should NOT be in index (empty result)
        Set<OWLClass> complexesForChebi = qrunner.getComplexClassesWithPart(chebiMolecule);
        assertTrue("CHEBI molecule should NOT be in index", complexesForChebi.isEmpty());
    }
}
```

**Step 2: Run test to verify it fails**

Run: `cd /Users/ebertdu/go/pathways2GO/exchange && mvn -Dtest=QRunnerPartToComplexIndexTest test`

Expected: Test fails because `complexesForChebi` returns the REACTO complex (CHEBI key is currently being stored)

**Step 3: Modify collectHasPartFillers to filter parts**

In `QRunner.java`, modify `collectHasPartFillers` to accept prefix strings and filter:

```java
/**
 * Collect all classes that appear as fillers in 'has_part/has_component' expressions.
 * Only includes classes with REACTO or UniProt prefixes.
 * Handles OWLObjectSomeValuesFrom, OWLObjectExactCardinality, OWLObjectMinCardinality,
 * OWLObjectIntersectionOf, and OWLObjectUnionOf expressions.
 */
private void collectHasPartFillers(org.semanticweb.owlapi.model.OWLClassExpression expr,
        Set<IRI> partPropertyIRIs, Set<OWLClass> parts,
        String reactoPrefix, String uniprotPrefix) {
    if (expr instanceof org.semanticweb.owlapi.model.OWLObjectSomeValuesFrom) {
        org.semanticweb.owlapi.model.OWLObjectSomeValuesFrom someExpr = (org.semanticweb.owlapi.model.OWLObjectSomeValuesFrom) expr;
        org.semanticweb.owlapi.model.OWLObjectPropertyExpression prop = someExpr.getProperty();
        if (!prop.isAnonymous() && partPropertyIRIs.contains(prop.asOWLObjectProperty().getIRI())) {
            org.semanticweb.owlapi.model.OWLClassExpression filler = someExpr.getFiller();
            if (!filler.isAnonymous()) {
                OWLClass fillerClass = filler.asOWLClass();
                String fillerIri = fillerClass.getIRI().toString();
                if (fillerIri.startsWith(reactoPrefix) || fillerIri.startsWith(uniprotPrefix)) {
                    parts.add(fillerClass);
                }
            }
        }
    } else if (expr instanceof org.semanticweb.owlapi.model.OWLObjectExactCardinality) {
        org.semanticweb.owlapi.model.OWLObjectExactCardinality exactExpr = (org.semanticweb.owlapi.model.OWLObjectExactCardinality) expr;
        org.semanticweb.owlapi.model.OWLObjectPropertyExpression prop = exactExpr.getProperty();
        if (!prop.isAnonymous() && partPropertyIRIs.contains(prop.asOWLObjectProperty().getIRI())) {
            org.semanticweb.owlapi.model.OWLClassExpression filler = exactExpr.getFiller();
            if (!filler.isAnonymous()) {
                OWLClass fillerClass = filler.asOWLClass();
                String fillerIri = fillerClass.getIRI().toString();
                if (fillerIri.startsWith(reactoPrefix) || fillerIri.startsWith(uniprotPrefix)) {
                    parts.add(fillerClass);
                }
            }
        }
    } else if (expr instanceof org.semanticweb.owlapi.model.OWLObjectMinCardinality) {
        org.semanticweb.owlapi.model.OWLObjectMinCardinality minExpr = (org.semanticweb.owlapi.model.OWLObjectMinCardinality) expr;
        org.semanticweb.owlapi.model.OWLObjectPropertyExpression prop = minExpr.getProperty();
        if (!prop.isAnonymous() && partPropertyIRIs.contains(prop.asOWLObjectProperty().getIRI())) {
            org.semanticweb.owlapi.model.OWLClassExpression filler = minExpr.getFiller();
            if (!filler.isAnonymous()) {
                OWLClass fillerClass = filler.asOWLClass();
                String fillerIri = fillerClass.getIRI().toString();
                if (fillerIri.startsWith(reactoPrefix) || fillerIri.startsWith(uniprotPrefix)) {
                    parts.add(fillerClass);
                }
            }
        }
    } else if (expr instanceof org.semanticweb.owlapi.model.OWLObjectIntersectionOf) {
        org.semanticweb.owlapi.model.OWLObjectIntersectionOf intersection = (org.semanticweb.owlapi.model.OWLObjectIntersectionOf) expr;
        for (org.semanticweb.owlapi.model.OWLClassExpression operand : intersection.getOperands()) {
            collectHasPartFillers(operand, partPropertyIRIs, parts, reactoPrefix, uniprotPrefix);
        }
    } else if (expr instanceof org.semanticweb.owlapi.model.OWLObjectUnionOf) {
        org.semanticweb.owlapi.model.OWLObjectUnionOf union = (org.semanticweb.owlapi.model.OWLObjectUnionOf) expr;
        for (org.semanticweb.owlapi.model.OWLClassExpression operand : union.getOperands()) {
            collectHasPartFillers(operand, partPropertyIRIs, parts, reactoPrefix, uniprotPrefix);
        }
    }
}
```

**Step 4: Update buildPartToComplexIndex to pass prefixes**

Update the calls in `buildPartToComplexIndex` to pass the prefix strings:

```java
// In buildPartToComplexIndex, change lines 973-982 from:
for (org.semanticweb.owlapi.model.OWLEquivalentClassesAxiom eqAxiom : tbox.getEquivalentClassesAxioms(cls)) {
    for (org.semanticweb.owlapi.model.OWLClassExpression expr : eqAxiom.getClassExpressions()) {
        collectHasPartFillers(expr, partPropertyIRIs, parts);
    }
}
// Check subclass axioms
for (org.semanticweb.owlapi.model.OWLSubClassOfAxiom subAxiom : tbox.getSubClassAxiomsForSubClass(cls)) {
    org.semanticweb.owlapi.model.OWLClassExpression superExpr = subAxiom.getSuperClass();
    collectHasPartFillers(superExpr, partPropertyIRIs, parts);
}

// To:
for (org.semanticweb.owlapi.model.OWLEquivalentClassesAxiom eqAxiom : tbox.getEquivalentClassesAxioms(cls)) {
    for (org.semanticweb.owlapi.model.OWLClassExpression expr : eqAxiom.getClassExpressions()) {
        collectHasPartFillers(expr, partPropertyIRIs, parts, reactoPrefix, uniprotPrefix);
    }
}
// Check subclass axioms
for (org.semanticweb.owlapi.model.OWLSubClassOfAxiom subAxiom : tbox.getSubClassAxiomsForSubClass(cls)) {
    org.semanticweb.owlapi.model.OWLClassExpression superExpr = subAxiom.getSuperClass();
    collectHasPartFillers(superExpr, partPropertyIRIs, parts, reactoPrefix, uniprotPrefix);
}
```

**Step 5: Run test to verify it passes**

Run: `cd /Users/ebertdu/go/pathways2GO/exchange && mvn -Dtest=QRunnerPartToComplexIndexTest test`

Expected: PASS

**Step 6: Run existing tests to verify no regressions**

Run: `cd /Users/ebertdu/go/pathways2GO/exchange && mvn -Dtest=BioPaxtoGOTest test`

Expected: All existing tests PASS

**Step 7: Commit**

```bash
cd /Users/ebertdu/go/pathways2GO/exchange
git add src/main/java/org/geneontology/gocam/exchange/QRunner.java
git add src/test/java/org/geneontology/gocam/exchange/QRunnerPartToComplexIndexTest.java
git commit -m "$(cat <<'EOF'
fix: Filter partToComplexIndex keys to REACTO/UniProt only

The buildPartToComplexIndex method was storing all has_part filler
classes as keys, including CHEBI, GO, and other non-REACTO classes.
Since getComplexClassesWithPart is only ever called with REACTO or
UniProt classes (from enabler types), these extra keys were never
queried and wasted memory/time during initialization.

This change filters the collected parts in collectHasPartFillers to
only include classes with REACTO or UniProt prefixes, matching the
filtering already applied to complex classes in the outer loop.

Fixes #302

Co-Authored-By: Claude Opus 4.5 <noreply@anthropic.com>
EOF
)"
```

---

## Summary

This is a focused single-task plan:

1. Add prefix filtering to `collectHasPartFillers` to only store REACTO/UniProt keys
2. Update calls from `buildPartToComplexIndex` to pass the prefix strings
3. Add a unit test to verify the filtering behavior

The change reduces memory usage and initialization time by excluding irrelevant keys that would never be queried.

---

## Update (2026-01-29): Removed reacto-tbox Option

During testing, discovered that loading `reacto.owl` separately causes "Ontology already exists" errors because it's already imported by go-lego.owl. Tests pass without loading it separately.

**Changes made:**
- Removed `-reacto-tbox` command line option from `Biopax2GOCmdLine.java`
- Removed `reacto_tbox_url` and `reacto_tbox_file` variables from `BioPaxtoGOTest.java`
- Simplified tboxes collection to only use go-lego.owl

The `partToComplexIndex` is built from the reasoner's root ontology (go-lego.owl), which already contains the necessary complex-part relationships via imports.