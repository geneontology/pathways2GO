# Revert BP-Curated Reaction Nodes (Skip them like other Mol Events) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Revert the change made in PR #387 (issue #318). Reactions that have only a curated GO BP xref but no GO MF (no controller-derived MF, no EC-derived MF, no SSSOM mapping) should be **skipped entirely** by the early GO-term gate, just like any other no-MF "molecular event" reaction. They should not be emitted as either (a) a reaction individual typed with the BP class, or (b) a reaction individual linked via `part_of` to a separate BP individual.

**Why this revert:** Issue #318 / PR #387 special-cased BP-curated reactions so that the curated BP term anchored the reaction node. As of issue #324 (this branch), all no-MF reactions are now skipped and causal paths are bridged over them — there is no longer a special case for BP-curated nodes; they should fall under the same "skip" rule.

**Architecture:** Three localized edits in `BioPaxtoGO.java`:
1. In the read-only resolver `resolveGoTermForReaction()`, drop the "BP-as-fallback" branch (Source 4c) so reactions with only BP xrefs return `mfTypes.isEmpty() && !hasFallbackBpMapping`, which makes `hasNoGoTerm()` return true and triggers the early skip in `defineReactionEntity()`.
2. In `defineReactionEntity()`, remove the inline "fallback BP-as-type" block added by PR #387 (asserts BP class as the reaction's type and walks preceding PathwaySteps to a placeholder TODO loop). With the resolver change above, this block is unreachable for BP-only reactions, so removing it is dead-code cleanup that also undoes PR #387.
3. Restore the pre-PR-387 layout of the BP-xref → separate-BP-individual loop: drop the `if(!types.isEmpty())` wrapper that PR #387 added, and move the `Collection<OWLClassExpression> types = …` declaration back to its original location (just above the `if(types.isEmpty())` fallback block). This restores the original behavior for reactions that have **both** an MF and a curated BP term: a BP individual is created and linked via `part_of`, regardless of MF state at line ~1843.

**Tech Stack:** Java 11, OWL API 4.5.6, paxtools-core 5.1.0, Maven, JUnit 4

**Branch:** `issue-324-skip-mol-events` (current). Do not create a new branch.

**Reference:** PR diff at <https://github.com/geneontology/pathways2GO/pull/387/files>. Issue at <https://github.com/geneontology/pathways2GO/issues/318>.

---

## Test Target

The reaction we will use to verify the revert is **R-HSA-201669** ("Beta-catenin translocates to the nucleus") in pathway **R-HSA-201681** (TCF dependent signaling in response to WNT). It lives in the existing test BioPAX file `exchange/src/test/resources/biopax/tcf-wnt-73.owl`.

This reaction is BP-only:
- No controllers (`Catalysis` / `Control`) are attached, so no controller-xref MF.
- No `eCNumber` annotation.
- No SSSOM match.
- One `RelationshipXref` with `db = "Gene Ontology"` and `id = "GO:0060828"` (regulation of canonical Wnt signaling pathway).

**Currently** (post-PR-387 + skip-mol-events branch): R-HSA-201669 is emitted as an `OWLNamedIndividual` typed with `GO_0060828` directly on the reaction.

**After revert:** R-HSA-201669 must not appear in the GO-CAM at all — no triples should reference `<http://model.geneontology.org/R-HSA-201669>`.

The only existing test that mentions R-HSA-201669 is `testInferProteinLocalizationProcess` at `BioPaxtoGOTest.java:651`, which is already disabled (`// @Test` comment) and references it in dead code only — it does not need to be touched.

---

### Task 1: Add the failing test for BP-only reaction skip

**Files:**
- Modify: `exchange/src/test/java/org/geneontology/gocam/exchange/BioPaxtoGOTest.java`

This is a TDD test that should **fail** before the implementation changes (because the reaction is currently emitted) and **pass** after them (because the reaction is skipped).

The test follows the exact pattern of `testDiseaseReactionDeletion` (`BioPaxtoGOTest.java:304-363`): it runs a `SELECT` over the pathway graph for any triple where the reaction IRI is in the subject position, and asserts the result count is zero.

- [ ] **Step 1: Write the failing test**

Insert immediately **after** the closing brace of `testCausalPathBridging` (currently at `BioPaxtoGOTest.java:1111`) and **before** the `@Ignore("Skipped: R-HSA-70667 (spontaneous reaction) ...")` annotation at line 1113. Add this method exactly:

```java
	/**
	 * Test that a reaction whose only GO annotation is a curated GO BP xref
	 * (no MF from controllers, no EC, no SSSOM) is skipped by the early gate
	 * in defineReactionEntity() — just like any other no-MF molecular event.
	 *
	 * Reverts the special-case behavior introduced by PR #387 (issue #318).
	 *
	 * Target: R-HSA-201669 "Beta-catenin translocates to the nucleus" in pathway
	 * R-HSA-201681 (TCF dependent signaling in response to WNT). Its sole GO
	 * annotation is RelationshipXref to GO:0060828 (regulation of canonical
	 * Wnt signaling pathway). It has zero controllers.
	 */
	@Test
	public final void testBpOnlyReactionSkipped() {
		System.out.println("Testing that BP-only reactions are skipped by early gate");
		String pathway = "<http://model.geneontology.org/R-HSA-201681>";
		String reaction_delete = "<http://model.geneontology.org/R-HSA-201669>";
		String all_reaction_q =
				"SELECT distinct ?reaction_prop ?reaction_value \n" +
				"WHERE {\n" +
				"  GRAPH pathway_id {  \n" +
				"    	reaction_id ?reaction_prop ?reaction_value . \n" +
				"    }\n" +
				"  } \n";
		TupleQueryResult result = null;
		int n = 0;
		try {
			String q = all_reaction_q.replace("pathway_id", pathway);
			q = q.replace("reaction_id", reaction_delete);
			result = blaze.runSparqlQuery(q);
			while (result.hasNext()) {
				result.next();
				n++;
			}
		} catch (QueryEvaluationException e) {
			e.printStackTrace();
		} finally {
			try {
				if (result != null) result.close();
			} catch (QueryEvaluationException e) {
				e.printStackTrace();
			}
		}
		assertTrue("BP-only reaction "+reaction_delete+" should have been skipped (got "+n+" triples)", n == 0);
	}
```

- [ ] **Step 2: Run the new test to verify it fails (with current code)**

Run from the `exchange/` directory:

```bash
cd exchange && mvn -Dtest=BioPaxtoGOTest#testBpOnlyReactionSkipped test
```

Expected: **FAIL** with an assertion message similar to `"BP-only reaction <http://model.geneontology.org/R-HSA-201669> should have been skipped (got N triples)"` where N > 0.

If the test passes already, something is different from the assumptions — stop and re-investigate before continuing.

---

### Task 2: Drop the BP-as-fallback branch in `resolveGoTermForReaction`

**Files:**
- Modify: `exchange/src/main/java/org/geneontology/gocam/exchange/BioPaxtoGO.java`

The resolver currently treats a curated BP xref as a "fallback mapping" — it copies the BP class into `mfTypes` and sets `hasFallbackBpMapping = true`, both of which make `hasNoGoTerm()` return false (so the reaction is not skipped). We remove that branch so BP-only reactions report `hasNoGoTerm() == true`.

- [ ] **Step 1: Remove the Source 4c block**

Open `exchange/src/main/java/org/geneontology/gocam/exchange/BioPaxtoGO.java` and find the block starting at line 2438 (inside `resolveGoTermForReaction`):

```java
			// 4c. Reaction's own BP xrefs (collected in Source 3 above) as type fallback.
			// The original code stores BP xrefs in report.bp2go_bp then reads them
			// back in the fallback; since the resolver runs before that store, we use
			// the goBpIds we already collected directly.
			if (mfTypes.isEmpty() && !goBpIds.isEmpty()) {
				hasFallbackBpMapping = true;
				for (String go_id : goBpIds) {
					String uri = GoCAM.obo_iri + go_id;
					OWLClass xref_go_func = golego.getOboClass(uri, true);
					mfTypes.add(xref_go_func);
				}
			}
```

Delete this block in its entirety. The local variable `boolean hasFallbackBpMapping = false;` (currently at line 2419) stays — it is still passed to the `ReactionGoTermResult` constructor and remains `false` for every code path now.

- [ ] **Step 2: Verify the resolver still compiles**

Run from the project root:

```bash
cd exchange && mvn -q -DskipTests compile
```

Expected: **BUILD SUCCESS** with no errors. Warnings about an unused local are fine.

Note: We deliberately keep `hasFallbackBpMapping` as a constructor parameter and `ReactionGoTermResult` field, even though it is now always `false`. This minimizes diff churn and preserves the public shape of `ReactionGoTermResult`. A later cleanup pass can remove it if desired — that is **out of scope** for this revert.

---

### Task 3: Remove the inline fallback BP-as-type block in `defineReactionEntity`

**Files:**
- Modify: `exchange/src/main/java/org/geneontology/gocam/exchange/BioPaxtoGO.java`

This block was added by PR #387 at the bottom of the `if(types.isEmpty())` fallback. After Task 2, BP-only reactions skip entirely and never reach this code — but for reactions that **do** pass the gate (e.g. MF derived from EC + BP xref), this block currently asserts the BP class as a second type on the reaction individual, which is exactly the PR #387 behavior we are reverting.

The block also contains a dead PathwayStep walk that does nothing (its inner loop body is `int x = 1;` with a placeholder TODO comment) — that goes too.

- [ ] **Step 1: Delete the block**

In `BioPaxtoGO.java`, find this block (currently at lines 1925–1966, inside the `if(types.isEmpty())` fallback) and delete it in its entirety:

```java
					Set<String> mappedgo = report.bp2go_bp.get((Process)entity);
					if(mappedgo!=null) {
						for(String go_id : mappedgo) {
							String uri = GoCAM.obo_iri + go_id;
							OWLClass xref_go_func = golego.getOboClass(uri, true);
							if(golego.isDeprecated(uri)) {
								report.deprecated_classes.add(getBioPaxName(entity)+"\t"+uri+"\tBP");
							}
							//the go class can not be a type for the reaction instance as we want to classify reactions as functions
							//and MF disjoint from BP
							//so make a new individual, hook it to that class, link to it via part of 
//							OWLNamedIndividual bp_i = go_cam.makeAnnotatedIndividual(GoCAM.makeGoCamifiedIRI(model_id, entity_id+"_"+go_id+"_individual"));
//							go_cam.addLiteralAnnotations2Individual(bp_i.getIRI(), GoCAM.rdfs_comment, "Asserted direct link between reaction and biological process, independent of current pathway");
							go_cam.addTypeAssertion(e, xref_go_func);
//							go_cam.addRefBackedObjectPropertyAssertion(e,GoCAM.part_of, bp_i, dbids, GoCAM.eco_imported_auto, default_namespace_prefix, null, model_id);
							//use the same name and id as the entity in question as, from Reactome perspective, its about the same thing and otherwise we have no name..
//							go_cam.addLabel(e, "reaction:"+entity_name+": is xrefed to this process");
//							if(entity_id!=null) {
//								go_cam.addDatabaseXref(bp_i, entity_id);
//							}
							//Per https://github.com/geneontology/pathways2GO/issues/66
							//remove the default part_of pathway relationship when one of these is added. 
							go_cam.applyAnnotatedTripleRemover(e.getIRI(), GoCAM.part_of.getIRI(), IRI.create(root_pathway_iri));
							// TODO: Check if preceding rxn's term is part_of go_id
							PathwayStep pathway_step = ((Conversion) entity).getStepProcessOf().iterator().next();
							Set<PathwayStep> previous_steps = pathway_step.getNextStepOf();
							for(PathwayStep previous_step : previous_steps) {
								BiochemicalReaction reaction = getBiochemicalReaction(previous_step);
								if (reaction == null) {
									continue;
								}
								String precedingRxnId = getEntityReferenceId(reaction);
								IRI preRxnIri = GoCAM.makeGoCamifiedIRI(null, precedingRxnId);
								OWLNamedIndividual preRxnInd = go_cam.df.getOWLNamedIndividual(preRxnIri);
								Collection<OWLClassExpression> precedingRxnTypes = EntitySearcher.getTypes(preRxnInd, go_cam.go_cam_ont);
								for(OWLClassExpression rxnType : precedingRxnTypes) {
									int x = 1;  // placeholder TODO to check if rxnType has any part_of->go_id closure
								}
							}
							mapped = true;
						}
					}
```

After deletion, the surrounding fallback block should look like this (the `if(!mapped) { go_cam.addTypeAssertion(e, GoCAM.molecular_event); }` at line ~1967 stays — it is the original pre-PR-387 default):

```java
					//default to mf
					if(!mapped) {
//						boolean mapped = false;
						if(sssom!=null) {
							String subject_id = sssom.contractUri(entity.getUri());
							//paxtools seems to eat the # ...  
							subject_id = subject_id.replace("BiochemicalReaction", "#BiochemicalReaction");
							SSSOM.Mapping mapping = sssom.getBestMatch(subject_id, 0.5);
							if(mapping!=null) {
								String class_iri = sssom.expandId(mapping.object_id);
								OWLClass mapped_class = go_cam.df.getOWLClass(IRI.create(class_iri));
								//go_cam.addTypeAssertion(pathway_e, mapped_class);	
								//TODO further development of sssom and evidence ontology could produce a useful evidence block here
								String comment = "This type assertion was computed with: "+mapping.mapping_tool+" with confidence "+mapping.confidence;
								go_cam.addComment(e, comment);
								//add some annotations to the assertion. (this is not viewable in noctua graph editor)
								Set<OWLAnnotation> annotations = new HashSet<OWLAnnotation>();
								annotations.add(go_cam.df.getOWLAnnotation(GoCAM.rdfs_comment, go_cam.df.getOWLLiteral(comment)));
								OWLClassAssertionAxiom isa = go_cam.df.getOWLClassAssertionAxiom(mapped_class, e, annotations);
								go_cam.ontman.addAxiom(go_cam.go_cam_ont, isa);						
								mapped = true;
							}
						}
					}
					if(!mapped) {
						go_cam.addTypeAssertion(e, GoCAM.molecular_event);	
					}
```

- [ ] **Step 2: Verify it still compiles**

```bash
cd exchange && mvn -q -DskipTests compile
```

Expected: **BUILD SUCCESS**. If imports for `PathwayStep`, `BiochemicalReaction`, `Conversion`, or `OWLClassExpression` become unused as a result of this deletion, leave them — other code in the file uses them and the imports remain needed.

---

### Task 4: Restore the pre-PR-387 BP-xref → separate-BP-individual layout

**Files:**
- Modify: `exchange/src/main/java/org/geneontology/gocam/exchange/BioPaxtoGO.java`

PR #387 made two structural changes to the BP-xref handling block (lines ~1843–1880 area):
1. Added a `Collection<OWLClassExpression> types = EntitySearcher.getTypes(e, go_cam.go_cam_ont);` declaration **above** the BP-xref `for` loop (line 1843).
2. Wrapped the body of the BP-xref `for` loop in `if(!types.isEmpty()) { … }` (lines 1856 + 1877).
3. Commented out the original `Collection<OWLClassExpression> types = …` declaration that previously lived just before the `if(types.isEmpty())` fallback block (line 1888).

Reverting all three restores the original behavior: whenever a reaction has a curated GO BP xref, a separate BP individual is created and linked via `part_of`. (For BP-only reactions, this loop is now unreachable — Task 2 + Task 3 together cause the early gate to skip them — so the only reactions that hit this loop in practice have an MF, exactly as in pre-PR-387.)

- [ ] **Step 1: Remove the early `types` declaration above the BP-xref loop**

Find this line (currently at line 1843 in `BioPaxtoGO.java`):

```java
				Collection<OWLClassExpression> types = EntitySearcher.getTypes(e, go_cam.go_cam_ont);
				//If a reaction is xreffed directly to the GO it is mapping to a biological process
```

Delete the `Collection<OWLClassExpression> types = …` line so the comment immediately follows the previous closing brace:

```java
				//If a reaction is xreffed directly to the GO it is mapping to a biological process
```

- [ ] **Step 2: Remove the `if(!types.isEmpty())` wrapper around the BP-individual creation**

Find this block (currently at lines 1846–1880):

```java
				for(Xref xref : entity.getXref()) {
					if(xref.getModelInterface().equals(RelationshipXref.class)) {
						RelationshipXref ref = (RelationshipXref)xref;	    			
						//here we add the referenced GO class as a type.  
						//#BioPAX4
						String db = ref.getDb().toLowerCase();
						if(db.contains("gene ontology")) {
							String goid = ref.getId().replaceAll(":", "_");
							go_bp.add(goid);
							// Below is the regular way of converting rxn GO BP terms. Only do this if proper activity type
							if(!types.isEmpty()) {
								String uri = GoCAM.obo_iri + goid;
								OWLClass xref_go_func = golego.getOboClass(uri, true);
								if(golego.isDeprecated(uri)) {
									report.deprecated_classes.add(getBioPaxName(entity)+"\t"+uri+"\tBP");
								}
								//the go class can not be a type for the reaction instance as we want to classify reactions as functions
								//and MF disjoint from BP
								//so make a new individual, hook it to that class, link to it via part of 
								OWLNamedIndividual bp_i = go_cam.makeAnnotatedIndividual(GoCAM.makeGoCamifiedIRI(model_id, entity_id+"_"+goid+"_individual"));
								go_cam.addLiteralAnnotations2Individual(bp_i.getIRI(), GoCAM.rdfs_comment, "Asserted direct link between reaction and biological process, independent of current pathway");
								go_cam.addTypeAssertion(bp_i, xref_go_func);
								go_cam.addRefBackedObjectPropertyAssertion(e,GoCAM.part_of, bp_i, dbids, GoCAM.eco_imported_auto, default_namespace_prefix, null, model_id);
								//use the same name and id as the entity in question as, from Reactome perspective, its about the same thing and otherwise we have no name..
								go_cam.addLabel(bp_i, "reaction:"+entity_name+": is xrefed to this process");
								if(entity_id!=null) {
									go_cam.addDatabaseXref(bp_i, entity_id);
								}
								//Per https://github.com/geneontology/pathways2GO/issues/66
								//remove the default part_of pathway relationship when one of these is added. 
								go_cam.applyAnnotatedTripleRemover(e.getIRI(), GoCAM.part_of.getIRI(), IRI.create(root_pathway_iri));
							}
						}
					}
				}	
```

Replace it with the pre-PR-387 layout (drop the `if(!types.isEmpty()) { … }` wrapper and the comment that was added explaining it):

```java
				for(Xref xref : entity.getXref()) {
					if(xref.getModelInterface().equals(RelationshipXref.class)) {
						RelationshipXref ref = (RelationshipXref)xref;	    			
						//here we add the referenced GO class as a type.  
						//#BioPAX4
						String db = ref.getDb().toLowerCase();
						if(db.contains("gene ontology")) {
							String goid = ref.getId().replaceAll(":", "_");
							go_bp.add(goid);							
							String uri = GoCAM.obo_iri + goid;
							OWLClass xref_go_func = golego.getOboClass(uri, true);
							if(golego.isDeprecated(uri)) {
								report.deprecated_classes.add(getBioPaxName(entity)+"\t"+uri+"\tBP");
							}
							//the go class can not be a type for the reaction instance as we want to classify reactions as functions
							//and MF disjoint from BP
							//so make a new individual, hook it to that class, link to it via part of 
							OWLNamedIndividual bp_i = go_cam.makeAnnotatedIndividual(GoCAM.makeGoCamifiedIRI(model_id, entity_id+"_"+goid+"_individual"));
							go_cam.addLiteralAnnotations2Individual(bp_i.getIRI(), GoCAM.rdfs_comment, "Asserted direct link between reaction and biological process, independent of current pathway");
							go_cam.addTypeAssertion(bp_i, xref_go_func);
							go_cam.addRefBackedObjectPropertyAssertion(e,GoCAM.part_of, bp_i, dbids, GoCAM.eco_imported_auto, default_namespace_prefix, null, model_id);
							//use the same name and id as the entity in question as, from Reactome perspective, its about the same thing and otherwise we have no name..
							go_cam.addLabel(bp_i, "reaction:"+entity_name+": is xrefed to this process");
							if(entity_id!=null) {
								go_cam.addDatabaseXref(bp_i, entity_id);
							}
							//Per https://github.com/geneontology/pathways2GO/issues/66
							//remove the default part_of pathway relationship when one of these is added. 
							go_cam.applyAnnotatedTripleRemover(e.getIRI(), GoCAM.part_of.getIRI(), IRI.create(root_pathway_iri));
						}
					}
				}	
```

- [ ] **Step 3: Restore the `types` declaration just before the `if(types.isEmpty())` fallback**

Find this commented-out line (currently at line 1888):

```java
				//want to stay in go tbox as much as possible - even if defaulting to root nodes.  
				//if no process or function annotations, add annotation to root
//				Collection<OWLClassExpression> types = EntitySearcher.getTypes(e, go_cam.go_cam_ont);			
				if(types.isEmpty()) { //go_mf.isEmpty()&&go_bp.isEmpty()
```

Restore it (uncomment, keep the trailing whitespace as-is or trim it — both work):

```java
				//want to stay in go tbox as much as possible - even if defaulting to root nodes.  
				//if no process or function annotations, add annotation to root
				Collection<OWLClassExpression> types = EntitySearcher.getTypes(e, go_cam.go_cam_ont);				
				if(types.isEmpty()) { //go_mf.isEmpty()&&go_bp.isEmpty()
```

- [ ] **Step 4: Verify the file still compiles**

```bash
cd exchange && mvn -q -DskipTests compile
```

Expected: **BUILD SUCCESS**.

---

### Task 5: Run the new test in isolation and verify it now passes

**Files:** none modified.

- [ ] **Step 1: Run the targeted test**

```bash
cd exchange && mvn -Dtest=BioPaxtoGOTest#testBpOnlyReactionSkipped test
```

Expected: **BUILD SUCCESS**, `Tests run: 1, Failures: 0, Errors: 0, Skipped: 0`.

If this fails, do not proceed to Task 6 — debug. Most likely causes if it fails:
- Task 2 not applied (resolver still treats BP as fallback). Re-check `resolveGoTermForReaction` — there should be no `mfTypes.add(xref_go_func)` inside any `goBpIds` loop.
- Task 3 not applied (inline BP-as-type block still present). Re-check the `if(types.isEmpty())` fallback in `defineReactionEntity` — it should not contain any `report.bp2go_bp.get(...)` lookup.
- The test file `tcf-wnt-73.owl` was not actually loaded into Blazegraph (e.g. test setup error). Check the build logs for `SKIPPING_NO_GO_TERM	R-HSA-201669` — that print confirms the early gate is firing for our target reaction.

---

### Task 6: Run the full test suite and triage any new failures

**Files:** possibly modify `exchange/src/test/java/org/geneontology/gocam/exchange/BioPaxtoGOTest.java` if a test must be `@Ignore`d.

The early-gate / bridging work in this branch was already designed for skipping no-MF reactions, so most tests should pass. The risk surface for this revert is any test that asserts properties of a BP-only reaction (like `R-HSA-201669`) being kept in the model. From the search done while writing this plan, only `testInferProteinLocalizationProcess` mentions R-HSA-201669, and it is already disabled (`// @Test`).

- [ ] **Step 1: Run the full suite**

```bash
cd exchange && mvn test
```

Expected: All previously passing tests still pass, plus `testBpOnlyReactionSkipped` passes. Tests already marked `@Ignore` stay skipped.

- [ ] **Step 2: Triage any new failures**

For each new failing test:
1. Read its assertion message and the reaction IRIs it queries.
2. Check whether the failing reaction is a BP-only reaction (i.e. its only GO annotation is a `RelationshipXref` with `db = "Gene Ontology"`, no controllers with GO xrefs, no EC, no SSSOM mapping).
3. **If the reaction is BP-only**: the test depends on the reverted behavior. Add an `@Ignore("Skipped: <reaction-id> is BP-only and is now skipped by early gate (revert of PR #387, issue #318)")` annotation immediately above the test's `@Test` line. Do **not** delete the test — keep it for documentation.
4. **If the reaction is not BP-only**: this is a regression. Stop and investigate before continuing.

For grep, the per-test logging line `SKIPPING_NO_GO_TERM\t<entity_id>\t<entity_name>` in stdout confirms which reactions are being dropped by the early gate. The test report writes to `exchange/target/surefire-reports/`.

- [ ] **Step 3: Re-run the full suite to confirm green**

```bash
cd exchange && mvn test
```

Expected: All non-`@Ignore`d tests pass.

---

### Task 7: Hand off

- [ ] **Step 1: Show the user the diff for review**

```bash
git diff issue-324-skip-mol-events -- exchange/src/main/java/org/geneontology/gocam/exchange/BioPaxtoGO.java exchange/src/test/java/org/geneontology/gocam/exchange/BioPaxtoGOTest.java
```

The user will commit and PR the change themselves — per CLAUDE.md, do not run `git add` or `git commit`.

- [ ] **Step 2: Summarize for the user**

Report which tests required `@Ignore` annotations (if any), and confirm `testBpOnlyReactionSkipped` passes. Note any reactions other than R-HSA-201669 that newly appeared in the `SKIPPING_NO_GO_TERM` log — this is a pure information drop; it documents the surface area of the revert.

---

## Self-Review Notes

- **Spec coverage:** Issue #318 / PR #387 introduced two behaviors: (a) wrap separate-BP-individual creation in `if(!types.isEmpty())`, and (b) add inline BP-as-type fallback. Both are reverted (Task 4 step 2 and Task 3 respectively). The user's request — "skip BP-only reactions like other no-MF mol events" — is handled by Task 2 (resolver) which makes the existing early gate fire. Test added in Task 1 verifies the user-visible behavior.
- **Type consistency:** `ReactionGoTermResult` keeps the `hasFallbackBpMapping` field/parameter (always `false` after Task 2). No call sites change.
- **No placeholders:** every code change shows the full before/after content. The triage step in Task 6 is conditional on observed test output, which is necessarily reactive — that is expected given the test surface is large.