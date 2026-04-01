# readable_shex_report.py

Converts the raw ShEx `explanations.txt` validation report (produced by the Minerva CLI) into human-readable markdown and plain text. The raw report is a TSV full of opaque ontology identifiers; this script resolves them to labels and explains each violation in plain language.

## Prerequisites

### Label extraction (one-time)

The script needs a TSV mapping ontology IRIs to human-readable labels. Generate it with [ROBOT](http://robot.obolibrary.org/):

```bash
robot export \
  --input exchange/go-lego.owl \
  --header "IRI|LABEL" \
  --entity-select "NAMED" \
  --entity-format "IRI" \
  --export go-lego-labels.tsv
```

This takes a few minutes on the ~1 GB `go-lego.owl` and produces ~290k label mappings.

### Python

Python 3.6+ with no external dependencies (stdlib only).

## Usage

```bash
python3 scripts/readable_shex_report.py \
  --explanations reactome_gen_20260204/reports/explanations.txt \
  --labels go-lego-labels.tsv \
  --output-dir reactome_gen_20260204/reports/
```

### Arguments

| Argument | Description |
|----------|-------------|
| `--explanations` | Path to the ShEx `explanations.txt` TSV file |
| `--labels` | Path to `go-lego-labels.tsv` (from `robot export`) |
| `--output-dir` | Directory for output files (created if absent) |

### Outputs

| File | Format |
|------|--------|
| `readable_explanations.md` | Markdown with tables, headers, and inline code |
| `readable_explanations.txt` | Plain text with aligned columns |

Both files contain:

1. **Summary** -- total violations, models affected
2. **Violations by category** -- count and percentage table
3. **Violations by relationship** -- count and percentage table
4. **Top 20 models** by violation count
5. **Per-model violation details** -- node, relationship, expected shapes, object, actual shapes, and a natural-language explanation

## How It Works

### Input format

The `explanations.txt` file is a tab-separated file with these columns:

| Column | Example |
|--------|---------|
| `filename` | `null` |
| `model_title` | `Cleavage of the damaged pyrimidine - imported from: Reactome` |
| `model_iri` | `http://model.geneontology.org/R-HSA-110329` |
| `node` | `gomodel:R-HSA-110215` |
| `Node_types` | `[GO:0004844]` |
| `property` | `RO:0002333` |
| `Intended_range_shapes` | `[obo:go/shapes/ProteinContainingComplex, obo:go/shapes/InformationBiomacromolecule]` |
| `object` | `gomodel:R-HSA-110215_UniProt_Isoform_P13051-1` |
| `Object_types` | `[UniProtKB:Isoform_P13051-1]` |
| `Object_shapes` | `[obo:go/shapes/ProvenanceAnnotated, obo:go/shapes/GoCamEntity]` |

Bracket-enclosed lists like `[A, B]` are parsed; `null` and `[]` are handled as absent/empty.

### Label resolution

Labels are resolved in this order:

1. **go-lego-labels.tsv lookup** -- IRI-to-CURIE conversion (e.g., `GO_0004844` -> `GO:0004844`) then dict lookup
2. **Hardcoded property labels** -- the 6 relationship properties used in ShEx validation:

   | CURIE | Label |
   |-------|-------|
   | `RO:0002333` | enabled by |
   | `RO:0002234` | has output |
   | `RO:0002233` | has input |
   | `RO:0012002` | has small molecule inhibitor |
   | `BFO:0000051` | has part |
   | `rdfs:label` | label (annotation) |

3. **Pattern-based fallbacks** -- UniProt isoforms (`UniProtKB:Isoform_P13051-1` -> "UniProt Isoform P13051-1"), plain UniProt, and REACTO entities
4. **Raw CURIE** if nothing matches

### Shape descriptions

Shape names from the `obo:go/shapes/` namespace are mapped to descriptions:

| Shape | Description |
|-------|-------------|
| ProteinContainingComplex | Protein-containing complex (GO:0032991) |
| InformationBiomacromolecule | Information biomacromolecule -- proteins, nucleic acids (CHEBI:33695) |
| ChemicalEntity | Chemical entity (CHEBI:24431) |
| GoCamEntity | Basic GO-CAM entity (base shape) |
| ProvenanceAnnotated | Entity with provenance annotations |
| CellularComponent | Cellular component (GO:0005575) |

### Violation categories

Each violation is classified into one of 9 categories (checked in priority order):

| # | Category | Test |
|---|----------|------|
| 1 | Missing label | `property == "rdfs:label"` |
| 2 | Missing type | `node_types` is null and property is not rdfs:label |
| 3 | Empty shapes | `object_shapes == []` |
| 4 | UniProt isoform not biomacromolecule | Object type matches `UniProtKB:Isoform_*` but lacks InformationBiomacromolecule shape |
| 5 | Complex shape mismatch | Object type is `GO:0032991` but matched CellularComponent instead of ProteinContainingComplex |
| 6 | Multi-type entity | Object has both `UniProtKB:*` and `CHEBI:*` types |
| 7 | REACTO entity mismatch | Object type contains `REACTO_` |
| 8 | CHEBI missing shape | Object type starts with `CHEBI:` but lacks expected shape |
| 9 | Unknown | Fallback for anything else |

Each category produces a parameterized natural-language explanation.

## Example output

From the markdown report:

```
### AKT phosphorylates targets in the nucleus (R-HSA-198693)

**Violation 1** [UniProt isoform not biomacromolecule]

- **Node:** `gomodel:R-HSA-199298` (protein serine/threonine kinase activity (GO:0004674))
- **Relationship:** has input (`RO:0002233`)
- **Expected shapes:** Chemical entity (CHEBI:24431), Protein-containing complex (GO:0032991)
- **Object:** `gomodel:R-HSA-52777_R-HSA-199298` typed as UniProt Isoform P16220-1 (UniProtKB:Isoform_P16220-1)
- **Object shapes:** Entity with provenance annotations, Basic GO-CAM entity (base shape)
- **Explanation:** The 'has input' relationship from 'protein serine/threonine kinase activity
  (GO:0004674)' points to 'UniProt Isoform P16220-1 (UniProtKB:Isoform_P16220-1)', which is a
  UniProt isoform but does not have the InformationBiomacromolecule shape.
```

## Architecture

### Key functions

| Function | Purpose |
|----------|---------|
| `load_label_map(filepath)` | Read robot TSV, convert IRIs to CURIEs, build lookup dict |
| `resolve_label(curie, label_map)` | Look up with fallbacks for UniProt/REACTO patterns |
| `parse_bracketed_list(value)` | Parse `[A, B]` / `[]` / `null` TSV format |
| `parse_shape_list(value)` | Parse shapes, stripping `obo:go/shapes/` prefix |
| `categorize_violation(v)` | Decision tree returning (category, explanation) |
| `group_by_model(violations)` | Group violations by model IRI, sort by title |
| `compute_summary(violations, groups)` | Aggregate stats: by category, property, model |
| `write_markdown(stats, groups, path)` | Markdown output with tables |
| `write_plaintext(stats, groups, path)` | Plain text output with aligned columns |

### Data structures

- `Violation` class -- raw parsed fields + resolved labels + category + explanation
- Groups are plain dicts with `model_iri`, `model_title`, `model_id`, `violations`
- Summary stats are a dict with `total`, `models_affected`, `by_category`, `by_property`, `top_models`
