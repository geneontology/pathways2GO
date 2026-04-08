# Causal Path Bridging Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** When intermediate reactions are skipped by the early GO term gate, bridge `causally_upstream_of` links over them so the causal graph stays connected.

**Architecture:** Add a helper method `findNearestDefinedProcesses` that walks the BioPAX `PathwayStep` DAG to find the nearest defined reactions in a given direction. Replace the direct process pairing in the pathway-level causal linking loop with resolved endpoints from this helper. A deduplication set prevents redundant assertions.

**Tech Stack:** Java 11, OWL API 4.5.6, paxtools-core 5.1.0, Maven, JUnit 4

**Spec:** `.specs/2026-04-08-causal-path-bridging.md`

---

### Task 1: Write the failing test for causal path bridging

**Files:**
- Modify: `exchange/src/test/java/org/geneontology/gocam/exchange/BioPaxtoGOTest.java`

The test verifies that in pathway R-HSA-4641262, a causal link bridges from R-HSA-3601585 to R-HSA-201677 over two skipped intermediates (R-HSA-201685 and R-HSA-1504186).

**Note:** The initial `causally_upstream_of` (RO_0002411) is upgraded by SPARQL inference rules to `directly_positively_regulates` (RO_0002629). The test queries for any of the causal relations (RO_0002411, RO_0002413, RO_0002629).

- [x] **Step 1: Add test method** — inserted after `testInferProvidesInput`, queries for any causal relation from R-HSA-3601585 to R-HSA-201677.

- [x] **Step 2: Run test to verify it fails** — confirmed FAIL before implementation.

---

### Task 2: Implement `findNearestDefinedProcesses` helper

**Files:**
- Modify: `exchange/src/main/java/org/geneontology/gocam/exchange/BioPaxtoGO.java`

- [ ] **Step 1: Add the helper method**

Insert after the `resolveGoTermForReaction` method (before `getTypesFromECs`). Find the line with the comment `// --- End resolveGoTermForReaction ---` or the method signature for `getTypesFromECs` and insert before it:

```java
	/**
	 * Walk the PathwayStep DAG to find the nearest Interaction processes that were
	 * actually defined in the GO-CAM model (i.e., not skipped by the early GO term gate).
	 * Used to bridge causally_upstream_of links over skipped intermediates.
	 *
	 * @param startStep  the step to start searching from
	 * @param forward    if true, walk via getNextStep(); if false, walk via getNextStepOf()
	 * @param go_cam     the GO-CAM model (for containsEntityInSignature checks)
	 * @param pathway    the current pathway (for in-pathway filtering)
	 * @param visited    set of already-visited steps (cycle guard); caller should pass new HashSet
	 * @return set of in-pathway Interaction processes whose IRIs are in the model signature
	 */
	private Set<Process> findNearestDefinedProcesses(PathwayStep startStep, boolean forward, GoCAM go_cam, Pathway pathway, Set<PathwayStep> visited) {
		Set<Process> result = new HashSet<Process>();
		if (visited.contains(startStep)) {
			return result;
		}
		visited.add(startStep);
		// Collect in-pathway Interaction processes (not Controls) that are defined in the model
		for (Process p : startStep.getStepProcess()) {
			if ((p instanceof Interaction) && !(p instanceof Control)) {
				if (p.getPathwayComponentOf().contains(pathway)) {
					String pid = getEntityReferenceId(p);
					if (pid != null) {
						IRI pIri = GoCAM.makeGoCamifiedIRI(null, pid);
						if (go_cam.go_cam_ont.containsEntityInSignature(pIri)) {
							result.add(p);
						}
					}
				}
			}
		}
		// If this step has defined processes, return them (nearest found)
		if (!result.isEmpty()) {
			return result;
		}
		// Otherwise recurse into neighbor steps
		Set<PathwayStep> neighbors = forward ? startStep.getNextStep() : startStep.getNextStepOf();
		for (PathwayStep neighbor : neighbors) {
			result.addAll(findNearestDefinedProcesses(neighbor, forward, go_cam, pathway, visited));
		}
		return result;
	}
```

- [ ] **Step 2: Verify compilation**

Run: `cd exchange && mvn compile -q`
Expected: BUILD SUCCESS (no compilation errors)

---

### Task 3: Integrate bridging into the causal linking loop

**Files:**
- Modify: `exchange/src/main/java/org/geneontology/gocam/exchange/BioPaxtoGO.java` (lines ~876-935)

The existing loop iterates direct step edges and creates causal links for the processes in those steps. We replace the inner process pairing with resolved endpoints using `findNearestDefinedProcesses` when a direct endpoint is skipped.

- [ ] **Step 1: Replace the causal linking loop body**

Replace the entire block from `if(!causal_recurse) {` (line ~876) through the closing of the `for(PathwayStep step1 : steps)` loop (line ~939, just before the closing `}` of `if(!causal_recurse)`), with the following:

Find this code (lines ~876-940):

```java
			if(!causal_recurse) { //else this is going to be handled recursively in the reaction definition function
				Set<PathwayStep> steps = pathway.getPathwayOrder();
				for(PathwayStep step1 : steps) {
					Set<Process> events = step1.getStepProcess();
					Set<PathwayStep> previousSteps = step1.getNextStepOf();
					//adding in previous step (which may be from a different pathway)
					for(PathwayStep prevStep : previousSteps) {
						Set<Process> prevEvents = prevStep.getStepProcess();
						for(Process event : events) {
							String event_id = getEntityReferenceId(event);
							for(Process prevEvent : prevEvents) {
								//limit to relations between conversions - was biochemical reactions but see no reason 
								//not to extend this to include e.g. degradation
								if((event instanceof Interaction)&&(prevEvent instanceof Interaction)&&
										!(event instanceof Control)&&!(prevEvent instanceof Control)) {
									Set<Pathway> event_pathways = event.getPathwayComponentOf();
									Set<Pathway> prev_event_pathways = prevEvent.getPathwayComponentOf();
									String add_reaction = null;
									if((event_pathways.contains(pathway)&&prev_event_pathways.contains(pathway))) {
										add_reaction = "in_pathway";
									}else if(add_neighboring_events_from_other_pathways) {	
										//test if there is any reason to avoid this reaction
										//e.g. from a banned pathway.  
										if(keepReaction((Interaction)prevEvent)) {
											add_reaction = "external_pathway";
										}else {
											add_reaction = null;
										}
									}
									if(add_reaction !=null) {
										String prev_event_id = getEntityReferenceId(prevEvent);
										IRI event_iri = GoCAM.makeGoCamifiedIRI(null, event_id);
										IRI prevEvent_iri = null;
										OWLNamedIndividual e1 = null;
										//in some cases, the reaction may connect off to a different pathway and hence not be caught in above loop to define reaction entities
										//e.g. Recruitment of SET1 methyltransferase complex  -> APC promotes disassembly of beta-catenin transactivation complex
										//are connected yet in different pathways
										if(add_reaction.equals("external_pathway")) {
											String external_pathway_id = null;
											for(Pathway external : prevEvent.getPathwayComponentOf()) {
												external_pathway_id = getEntityReferenceId(external);
												prevEvent_iri = GoCAM.makeGoCamifiedIRI(null, prev_event_id);
												break;
											}
											defineReactionEntity(go_cam, prevEvent, prevEvent_iri, false, external_pathway_id, pathway_iri.toString(), null, false);
											if(prevEvent_iri != null && go_cam.go_cam_ont.containsEntityInSignature(prevEvent_iri)) {
												e1 = go_cam.df.getOWLNamedIndividual(prevEvent_iri);
												go_cam.addComment(e1, "reaction from external pathway "+external_pathway_id);
											}
										}else {
											prevEvent_iri = GoCAM.makeGoCamifiedIRI(null, prev_event_id);
											e1 = go_cam.df.getOWLNamedIndividual(prevEvent_iri);
										}
										// Only add causal link if both reactions were actually defined (not skipped by early GO term gate)
										if(prevEvent_iri != null && go_cam.go_cam_ont.containsEntityInSignature(prevEvent_iri) &&
												go_cam.go_cam_ont.containsEntityInSignature(event_iri)) {
											OWLNamedIndividual e2 = go_cam.df.getOWLNamedIndividual(event_iri);
											go_cam.addRefBackedObjectPropertyAssertion(e1, GoCAM.causally_upstream_of, e2, Collections.singleton(model_id), GoCAM.eco_imported_auto, default_namespace_prefix, null, model_id);
										}										
									}
								}
							} 
						}
					} 			
				}  
			}
```

Replace with:

```java
			if(!causal_recurse) { //else this is going to be handled recursively in the reaction definition function
				Set<PathwayStep> steps = pathway.getPathwayOrder();
				Set<String> addedCausalLinks = new HashSet<String>(); // dedup bridged links
				for(PathwayStep step1 : steps) {
					Set<Process> events = step1.getStepProcess();
					Set<PathwayStep> previousSteps = step1.getNextStepOf();
					//adding in previous step (which may be from a different pathway)
					for(PathwayStep prevStep : previousSteps) {
						Set<Process> prevEvents = prevStep.getStepProcess();
						for(Process event : events) {
							String event_id = getEntityReferenceId(event);
							for(Process prevEvent : prevEvents) {
								//limit to relations between conversions - was biochemical reactions but see no reason 
								//not to extend this to include e.g. degradation
								if((event instanceof Interaction)&&(prevEvent instanceof Interaction)&&
										!(event instanceof Control)&&!(prevEvent instanceof Control)) {
									Set<Pathway> event_pathways = event.getPathwayComponentOf();
									Set<Pathway> prev_event_pathways = prevEvent.getPathwayComponentOf();
									String add_reaction = null;
									if((event_pathways.contains(pathway)&&prev_event_pathways.contains(pathway))) {
										add_reaction = "in_pathway";
									}else if(add_neighboring_events_from_other_pathways) {	
										//test if there is any reason to avoid this reaction
										//e.g. from a banned pathway.  
										if(keepReaction((Interaction)prevEvent)) {
											add_reaction = "external_pathway";
										}else {
											add_reaction = null;
										}
									}
									if(add_reaction !=null) {
										if(add_reaction.equals("external_pathway")) {
											// External pathway reactions: define and link without bridging
											String prev_event_id = getEntityReferenceId(prevEvent);
											IRI event_iri = GoCAM.makeGoCamifiedIRI(null, event_id);
											IRI prevEvent_iri = null;
											String external_pathway_id = null;
											for(Pathway external : prevEvent.getPathwayComponentOf()) {
												external_pathway_id = getEntityReferenceId(external);
												prevEvent_iri = GoCAM.makeGoCamifiedIRI(null, prev_event_id);
												break;
											}
											defineReactionEntity(go_cam, prevEvent, prevEvent_iri, false, external_pathway_id, pathway_iri.toString(), null, false);
											if(prevEvent_iri != null && go_cam.go_cam_ont.containsEntityInSignature(prevEvent_iri)) {
												OWLNamedIndividual e1 = go_cam.df.getOWLNamedIndividual(prevEvent_iri);
												go_cam.addComment(e1, "reaction from external pathway "+external_pathway_id);
												if(go_cam.go_cam_ont.containsEntityInSignature(event_iri)) {
													String linkKey = prevEvent_iri.toString() + "|" + event_iri.toString();
													if(!addedCausalLinks.contains(linkKey)) {
														addedCausalLinks.add(linkKey);
														OWLNamedIndividual e2 = go_cam.df.getOWLNamedIndividual(event_iri);
														go_cam.addRefBackedObjectPropertyAssertion(e1, GoCAM.causally_upstream_of, e2, Collections.singleton(model_id), GoCAM.eco_imported_auto, default_namespace_prefix, null, model_id);
													}
												}
											}
										} else {
											// In-pathway reactions: bridge over skipped intermediates
											IRI event_iri = GoCAM.makeGoCamifiedIRI(null, event_id);
											String prev_event_id = getEntityReferenceId(prevEvent);
											IRI prevEvent_iri = GoCAM.makeGoCamifiedIRI(null, prev_event_id);
											boolean eventDefined = go_cam.go_cam_ont.containsEntityInSignature(event_iri);
											boolean prevDefined = go_cam.go_cam_ont.containsEntityInSignature(prevEvent_iri);
											// Resolve source processes: use prevEvent if defined, else bridge backward
											Set<Process> sourceProcesses;
											if(prevDefined) {
												sourceProcesses = Collections.singleton(prevEvent);
											} else {
												sourceProcesses = findNearestDefinedProcesses(prevStep, false, go_cam, pathway, new HashSet<PathwayStep>());
											}
											// Resolve target processes: use event if defined, else bridge forward
											Set<Process> targetProcesses;
											if(eventDefined) {
												targetProcesses = Collections.singleton(event);
											} else {
												targetProcesses = findNearestDefinedProcesses(step1, true, go_cam, pathway, new HashSet<PathwayStep>());
											}
											// Create causal links for all resolved source-target pairs
											for(Process src : sourceProcesses) {
												String srcId = getEntityReferenceId(src);
												IRI srcIri = GoCAM.makeGoCamifiedIRI(null, srcId);
												for(Process tgt : targetProcesses) {
													String tgtId = getEntityReferenceId(tgt);
													IRI tgtIri = GoCAM.makeGoCamifiedIRI(null, tgtId);
													// Skip self-links
													if(srcIri.equals(tgtIri)) continue;
													String linkKey = srcIri.toString() + "|" + tgtIri.toString();
													if(!addedCausalLinks.contains(linkKey)) {
														addedCausalLinks.add(linkKey);
														OWLNamedIndividual e1 = go_cam.df.getOWLNamedIndividual(srcIri);
														OWLNamedIndividual e2 = go_cam.df.getOWLNamedIndividual(tgtIri);
														go_cam.addRefBackedObjectPropertyAssertion(e1, GoCAM.causally_upstream_of, e2, Collections.singleton(model_id), GoCAM.eco_imported_auto, default_namespace_prefix, null, model_id);
													}
												}
											}
										}
									}
								}
							} 
						}
					} 			
				}  
			}
```

- [ ] **Step 2: Verify compilation**

Run: `cd exchange && mvn compile -q`
Expected: BUILD SUCCESS

---

### Task 4: Run tests and verify

**Files:** None (test execution only)

- [ ] **Step 1: Run the new bridging test**

Run: `cd exchange && mvn -Dtest=BioPaxtoGOTest#testCausalPathBridging test`
Expected: PASS — R-HSA-3601585 now has `causally_upstream_of` R-HSA-201677 via bridging.

- [ ] **Step 2: Run the full test suite**

Run: `cd exchange && mvn test`
Expected: 21 tests run, 0 failures, 2 skipped. BUILD SUCCESS.

- [ ] **Step 3: If any test fails, diagnose and fix**

Check the failure output. Likely causes:
- A reaction that was previously linked directly now gets a duplicate bridged link — the dedup set should prevent this, but verify.
- An external pathway reaction's link is affected — shouldn't happen since external_pathway is handled separately.

---

### Task 5: Re-evaluate `@Ignore`d tests — DONE

All 4 `@Ignore`d tests were un-ignored and run. All still failed because they query the skipped reaction as a SPARQL endpoint. Bridging does not help.

However, 2 tests were **restored with new example pathways** where the relevant reactions have controllers with GO xrefs:

- [x] `testInferRegulatesViaOutputRegulates` — restored with pathway R-HSA-1445148, rxns R-HSA-1449597 → R-HSA-2316352. BioPAX file `R-HSA-1445148_level3.owl` copied to test resources.
- [x] `testInferRegulatesViaOutputEnables` — restored with pathway R-HSA-110362, rxns R-HSA-5649883 → R-HSA-5651723. BioPAX file `R-HSA-110362_level3.owl` copied to test resources.

Remaining 2 `@Ignore`d tests (`testOccursInFromEntityLocations`, `testSharedIntermediateInputs`) query properties of the skipped reaction itself (location, I/O) and cannot be resolved by re-example.

---

### Task 6: Final verification and plan update — DONE

- [x] Full test suite: 21 tests, 0 failures, 2 skipped. BUILD SUCCESS.
- [x] `.plans/2026-04-02-go-term-early-resolution.md` updated: status 19/21, remaining failures reduced to 2, bridging section added, restored tests documented.
- [x] `.specs/2026-04-08-causal-path-bridging.md` updated: testing section rewritten with actual results, new BioPAX files listed.