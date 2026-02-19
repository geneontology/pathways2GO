#!/usr/bin/env python3
"""Convert ShEx explanations.txt report into human-readable markdown and plain text."""

import argparse
import csv
import os
import re
from collections import Counter, defaultdict
from dataclasses import dataclass, field


# --- Constants ---

PROPERTY_LABELS = {
    "RO:0002333": "enabled by",
    "RO:0002234": "has output",
    "RO:0002233": "has input",
    "RO:0012002": "has small molecule inhibitor",
    "BFO:0000051": "has part",
    "rdfs:label": "label (annotation)",
}

SHAPE_DESCRIPTIONS = {
    "ProteinContainingComplex": "Protein-containing complex (GO:0032991)",
    "InformationBiomacromolecule": "Information biomacromolecule — proteins, nucleic acids (CHEBI:33695)",
    "ChemicalEntity": "Chemical entity (CHEBI:24431)",
    "GoCamEntity": "Basic GO-CAM entity (base shape)",
    "ProvenanceAnnotated": "Entity with provenance annotations",
    "CellularComponent": "Cellular component (GO:0005575)",
}


# --- Data structures ---

@dataclass
class Violation:
    model_title: str
    model_iri: str
    node: str
    node_types: list
    property: str
    intended_shapes: list
    obj: str
    object_types: list
    object_shapes: list
    # Resolved fields
    node_type_labels: list = field(default_factory=list)
    property_label: str = ""
    object_type_labels: list = field(default_factory=list)
    intended_shape_labels: list = field(default_factory=list)
    object_shape_labels: list = field(default_factory=list)
    category: str = ""
    explanation: str = ""


# --- Parsing helpers ---

def parse_bracketed_list(value):
    """Parse '[A, B, C]' or '[]' or 'null' into a list or None."""
    if value is None or value == "null":
        return None
    value = value.strip()
    if value == "[]":
        return []
    if value.startswith("[") and value.endswith("]"):
        inner = value[1:-1].strip()
        if not inner:
            return []
        return [item.strip() for item in inner.split(", ")]
    return [value]


def parse_shape_name(raw):
    """Strip 'obo:go/shapes/' prefix from a shape name."""
    prefix = "obo:go/shapes/"
    if raw.startswith(prefix):
        return raw[len(prefix):]
    return raw


def parse_shape_list(value):
    """Parse a bracketed list of shapes, stripping prefixes."""
    items = parse_bracketed_list(value)
    if items is None:
        return None
    return [parse_shape_name(s) for s in items]


def parse_object_type(raw):
    """Convert raw object type to a CURIE.

    Handles forms like:
    - 'GO:0032991' -> 'GO:0032991'
    - 'UniProtKB:Isoform_P13051-1' -> 'UniProtKB:Isoform_P13051-1'
    - 'CHEBI:34778' -> 'CHEBI:34778'
    - 'obo:go/extensions/reacto.owl#REACTO_R-HSA-9913618' -> 'REACTO:R-HSA-9913618'
    """
    reacto_prefix = "obo:go/extensions/reacto.owl#REACTO_"
    if raw.startswith(reacto_prefix):
        return "REACTO:" + raw[len(reacto_prefix):]
    return raw


def extract_model_id(model_iri):
    """Extract the R-HSA-XXXXXX portion from a model IRI."""
    m = re.search(r'(R-HSA-\d+)', model_iri)
    return m.group(1) if m else model_iri


# --- Label resolution ---

def load_label_map(filepath):
    """Load a robot-exported TSV (IRI|LABEL) and build a CURIE->label dict."""
    label_map = {}
    with open(filepath, "r", encoding="utf-8") as f:
        reader = csv.reader(f, delimiter="\t")
        header = next(reader, None)
        if header is None:
            return label_map
        for row in reader:
            if len(row) < 2:
                continue
            iri, label = row[0], row[1]
            if not label:
                continue
            # Convert IRI to CURIE
            # http://purl.obolibrary.org/obo/GO_0004844 -> GO:0004844
            obo_match = re.search(r'http://purl\.obolibrary\.org/obo/(\w+?)_(\S+)', iri)
            if obo_match:
                curie = f"{obo_match.group(1)}:{obo_match.group(2)}"
                label_map[curie] = label
                continue
            # http://purl.obolibrary.org/obo/go/extensions/reacto.owl#REACTO_R-ALL-164319
            reacto_match = re.search(r'reacto\.owl#REACTO_(.+)', iri)
            if reacto_match:
                label_map[f"REACTO:{reacto_match.group(1)}"] = label
                continue
            # Store raw IRI as well for any other patterns
            label_map[iri] = label
    return label_map


def resolve_label(curie, label_map):
    """Look up a CURIE in the label map with fallbacks."""
    if curie is None:
        return "null"

    # Direct lookup
    if curie in label_map:
        return label_map[curie]

    # UniProt isoform
    m = re.match(r'UniProtKB:Isoform_(.+)', curie)
    if m:
        return f"UniProt Isoform {m.group(1)}"

    # Plain UniProt
    m = re.match(r'UniProtKB:(\S+)', curie)
    if m:
        return f"UniProt {m.group(1)}"

    # REACTO entity
    m = re.match(r'REACTO:(.+)', curie)
    if m:
        return f"Reactome entity {m.group(1)}"

    return curie


def resolve_shape(name):
    """Return the shape description or the raw name."""
    return SHAPE_DESCRIPTIONS.get(name, name)


# --- Violation categorization ---

def categorize_violation(v):
    """Categorize a violation and return (category, explanation)."""

    prop_label = v.property_label or v.property

    # 1. Missing label
    if v.property == "rdfs:label":
        return ("Missing label",
                f"Node '{v.node}' is missing a human-readable label (rdfs:label). "
                f"This is required for all entities in a GO-CAM model.")

    # 2. Missing type (node_types is None)
    if v.node_types is None:
        return ("Missing type",
                f"Node '{v.node}' has no type assigned. The '{prop_label}' relationship "
                f"cannot be validated without knowing the node's type.")

    # 3. Empty shapes on object
    if v.object_shapes is not None and len(v.object_shapes) == 0:
        obj_types_str = ", ".join(v.object_type_labels) if v.object_type_labels else "unknown"
        return ("Empty shapes",
                f"The object '{v.obj}' (typed as {obj_types_str}) has no ShEx shapes assigned. "
                f"The '{prop_label}' relationship expects the object to conform to one of: "
                f"{', '.join(v.intended_shape_labels) if v.intended_shape_labels else 'N/A'}.")

    # Convenience
    obj_types = v.object_types or []
    obj_shapes = v.object_shapes or []
    obj_type_strs = [str(t) for t in obj_types]

    # 4. UniProt isoform not matching InformationBiomacromolecule
    has_uniprot_isoform = any(t.startswith("UniProtKB:Isoform_") for t in obj_type_strs)
    if has_uniprot_isoform and "InformationBiomacromolecule" not in obj_shapes:
        node_label = ", ".join(v.node_type_labels) if v.node_type_labels else v.node
        obj_label = ", ".join(v.object_type_labels) if v.object_type_labels else v.obj
        return ("UniProt isoform not biomacromolecule",
                f"The '{prop_label}' relationship from '{node_label}' points to '{obj_label}', "
                f"which is a UniProt isoform but does not have the InformationBiomacromolecule shape. "
                f"Its shapes are: {', '.join(v.object_shape_labels) if v.object_shape_labels else 'none'}. "
                f"Expected one of: {', '.join(v.intended_shape_labels) if v.intended_shape_labels else 'N/A'}.")

    # 5. Complex typed as GO:0032991 matching CellularComponent but not ProteinContainingComplex
    has_complex_type = "GO:0032991" in obj_type_strs
    if has_complex_type and "CellularComponent" in obj_shapes and "ProteinContainingComplex" not in obj_shapes:
        node_label = ", ".join(v.node_type_labels) if v.node_type_labels else v.node
        return ("Complex shape mismatch",
                f"The '{prop_label}' relationship from '{node_label}' points to a protein-containing complex "
                f"(GO:0032991), but it matched the CellularComponent shape instead of ProteinContainingComplex. "
                f"Expected one of: {', '.join(v.intended_shape_labels) if v.intended_shape_labels else 'N/A'}.")

    # 6. Multi-type entity (both UniProt and CHEBI)
    has_uniprot = any(t.startswith("UniProtKB:") for t in obj_type_strs)
    has_chebi = any(t.startswith("CHEBI:") for t in obj_type_strs)
    if has_uniprot and has_chebi:
        type_labels = ", ".join(v.object_type_labels) if v.object_type_labels else ", ".join(obj_type_strs)
        return ("Multi-type entity",
                f"The object '{v.obj}' has both UniProt and CHEBI types ({type_labels}), "
                f"making it ambiguous. The '{prop_label}' relationship expects: "
                f"{', '.join(v.intended_shape_labels) if v.intended_shape_labels else 'N/A'}.")

    # 7. REACTO entity mismatch
    has_reacto = any("REACTO" in t for t in obj_type_strs)
    if has_reacto:
        obj_label = ", ".join(v.object_type_labels) if v.object_type_labels else ", ".join(obj_type_strs)
        return ("REACTO entity mismatch",
                f"The object is typed as a REACTO entity ({obj_label}). "
                f"Its shapes ({', '.join(v.object_shape_labels) if v.object_shape_labels else 'none'}) "
                f"do not match the expected: {', '.join(v.intended_shape_labels) if v.intended_shape_labels else 'N/A'}.")

    # 8. CHEBI missing expected shape
    if has_chebi and not has_uniprot:
        obj_label = ", ".join(v.object_type_labels) if v.object_type_labels else ", ".join(obj_type_strs)
        return ("CHEBI missing shape",
                f"The object '{v.obj}' is typed as {obj_label} but its shapes "
                f"({', '.join(v.object_shape_labels) if v.object_shape_labels else 'none'}) "
                f"do not include the expected: {', '.join(v.intended_shape_labels) if v.intended_shape_labels else 'N/A'}.")

    # 9. Unknown / fallback
    obj_label = ", ".join(v.object_type_labels) if v.object_type_labels else (v.obj or "null")
    return ("Unknown",
            f"Shape mismatch on '{prop_label}' relationship. "
            f"Object typed as {obj_label}, "
            f"with shapes: {', '.join(v.object_shape_labels) if v.object_shape_labels else 'none'}. "
            f"Expected: {', '.join(v.intended_shape_labels) if v.intended_shape_labels else 'N/A'}.")


# --- Grouping and summary ---

def group_by_model(violations):
    """Group violations by model IRI and sort by model title."""
    groups = defaultdict(list)
    titles = {}
    for v in violations:
        groups[v.model_iri].append(v)
        titles[v.model_iri] = v.model_title
    result = []
    for iri in sorted(groups, key=lambda k: titles.get(k, k)):
        result.append({
            "model_iri": iri,
            "model_title": titles[iri],
            "model_id": extract_model_id(iri),
            "violations": groups[iri],
        })
    return result


def compute_summary(violations, groups):
    """Compute summary statistics."""
    by_category = Counter(v.category for v in violations)
    by_property = Counter(v.property_label or v.property for v in violations)
    by_model = [(g["model_title"], g["model_id"], len(g["violations"])) for g in groups]
    by_model.sort(key=lambda x: x[2], reverse=True)
    return {
        "total": len(violations),
        "models_affected": len(groups),
        "by_category": by_category.most_common(),
        "by_property": by_property.most_common(),
        "top_models": by_model[:20],
    }


# --- Output writers ---

def write_markdown(stats, groups, path):
    """Write a markdown report."""
    with open(path, "w", encoding="utf-8") as f:
        f.write("# ShEx Validation Report — Human-Readable\n\n")

        # Summary
        f.write("## Summary\n\n")
        f.write(f"- **Total violations:** {stats['total']}\n")
        f.write(f"- **Models affected:** {stats['models_affected']}\n\n")

        # By category
        f.write("### Violations by Category\n\n")
        f.write("| Category | Count | % |\n")
        f.write("|----------|------:|--:|\n")
        for cat, count in stats["by_category"]:
            pct = 100.0 * count / stats["total"]
            f.write(f"| {cat} | {count} | {pct:.1f}% |\n")
        f.write("\n")

        # By relationship
        f.write("### Violations by Relationship\n\n")
        f.write("| Relationship | Count | % |\n")
        f.write("|-------------|------:|--:|\n")
        for prop, count in stats["by_property"]:
            pct = 100.0 * count / stats["total"]
            f.write(f"| {prop} | {count} | {pct:.1f}% |\n")
        f.write("\n")

        # Top models
        f.write("### Top 20 Models by Violation Count\n\n")
        f.write("| Model | ID | Violations |\n")
        f.write("|-------|-----|----------:|\n")
        for title, mid, count in stats["top_models"]:
            f.write(f"| {title} | {mid} | {count} |\n")
        f.write("\n---\n\n")

        # Per-model violations
        f.write("## Violations by Model\n\n")
        for group in groups:
            title = group["model_title"]
            mid = group["model_id"]
            f.write(f"### {title} ({mid})\n\n")
            for i, v in enumerate(group["violations"], 1):
                f.write(f"**Violation {i}** [{v.category}]\n\n")
                node_labels = ", ".join(v.node_type_labels) if v.node_type_labels else "untyped"
                f.write(f"- **Node:** `{v.node}` ({node_labels})\n")
                f.write(f"- **Relationship:** {v.property_label} (`{v.property}`)\n")
                if v.intended_shape_labels:
                    f.write(f"- **Expected shapes:** {', '.join(v.intended_shape_labels)}\n")
                if v.obj:
                    obj_types_str = ", ".join(v.object_type_labels) if v.object_type_labels else "untyped"
                    f.write(f"- **Object:** `{v.obj}` typed as {obj_types_str}\n")
                if v.object_shape_labels:
                    f.write(f"- **Object shapes:** {', '.join(v.object_shape_labels)}\n")
                f.write(f"- **Explanation:** {v.explanation}\n\n")
            f.write("---\n\n")


def write_plaintext(stats, groups, path):
    """Write a plain text report."""
    with open(path, "w", encoding="utf-8") as f:
        f.write("ShEx Validation Report — Human-Readable\n")
        f.write("=" * 50 + "\n\n")

        f.write("SUMMARY\n")
        f.write("-" * 50 + "\n")
        f.write(f"Total violations: {stats['total']}\n")
        f.write(f"Models affected:  {stats['models_affected']}\n\n")

        f.write("Violations by Category:\n")
        for cat, count in stats["by_category"]:
            pct = 100.0 * count / stats["total"]
            f.write(f"  {cat:<45s} {count:>5d}  ({pct:.1f}%)\n")
        f.write("\n")

        f.write("Violations by Relationship:\n")
        for prop, count in stats["by_property"]:
            pct = 100.0 * count / stats["total"]
            f.write(f"  {prop:<45s} {count:>5d}  ({pct:.1f}%)\n")
        f.write("\n")

        f.write("Top 20 Models by Violation Count:\n")
        for title, mid, count in stats["top_models"]:
            f.write(f"  {mid:<20s} {count:>4d}  {title}\n")
        f.write("\n" + "=" * 50 + "\n\n")

        f.write("VIOLATIONS BY MODEL\n")
        f.write("=" * 50 + "\n\n")
        for group in groups:
            title = group["model_title"]
            mid = group["model_id"]
            f.write(f"{title} ({mid})\n")
            f.write("-" * 50 + "\n")
            for i, v in enumerate(group["violations"], 1):
                f.write(f"\n  Violation {i} [{v.category}]\n")
                node_labels = ", ".join(v.node_type_labels) if v.node_type_labels else "untyped"
                f.write(f"    Node:         {v.node} ({node_labels})\n")
                f.write(f"    Relationship: {v.property_label} ({v.property})\n")
                if v.intended_shape_labels:
                    f.write(f"    Expected:     {', '.join(v.intended_shape_labels)}\n")
                if v.obj:
                    obj_types_str = ", ".join(v.object_type_labels) if v.object_type_labels else "untyped"
                    f.write(f"    Object:       {v.obj} typed as {obj_types_str}\n")
                if v.object_shape_labels:
                    f.write(f"    Obj shapes:   {', '.join(v.object_shape_labels)}\n")
                f.write(f"    Explanation:  {v.explanation}\n")
            f.write("\n\n")


# --- Main ---

def main():
    parser = argparse.ArgumentParser(
        description="Convert ShEx explanations report to human-readable form."
    )
    parser.add_argument(
        "--explanations", required=True,
        help="Path to the ShEx explanations.txt TSV file"
    )
    parser.add_argument(
        "--labels", required=True,
        help="Path to go-lego-labels.tsv (robot export: IRI|LABEL)"
    )
    parser.add_argument(
        "--output-dir", required=True,
        help="Directory for output files"
    )
    args = parser.parse_args()

    # Load labels
    print(f"Loading labels from {args.labels}...")
    label_map = load_label_map(args.labels)
    print(f"  Loaded {len(label_map)} labels")

    # Parse violations
    print(f"Parsing violations from {args.explanations}...")
    violations = []
    with open(args.explanations, "r", encoding="utf-8") as f:
        reader = csv.DictReader(f, delimiter="\t")
        for row in reader:
            node_types = parse_bracketed_list(row.get("Node_types"))
            object_types_raw = parse_bracketed_list(row.get("Object_types"))
            object_types = [parse_object_type(t) for t in object_types_raw] if object_types_raw is not None else None

            v = Violation(
                model_title=row.get("model_title", ""),
                model_iri=row.get("model_iri", ""),
                node=row.get("node", ""),
                node_types=node_types,
                property=row.get("property", ""),
                intended_shapes=parse_shape_list(row.get("Intended_range_shapes")),
                obj=row.get("object"),
                object_types=object_types,
                object_shapes=parse_shape_list(row.get("Object_shapes")),
            )

            # Resolve labels
            if v.node_types:
                v.node_type_labels = [
                    f"{resolve_label(t, label_map)} ({t})" for t in v.node_types
                ]
            v.property_label = PROPERTY_LABELS.get(v.property, v.property)
            if v.object_types:
                v.object_type_labels = [
                    f"{resolve_label(t, label_map)} ({t})" for t in v.object_types
                ]
            if v.intended_shapes:
                v.intended_shape_labels = [resolve_shape(s) for s in v.intended_shapes]
            if v.object_shapes:
                v.object_shape_labels = [resolve_shape(s) for s in v.object_shapes]

            # Categorize
            v.category, v.explanation = categorize_violation(v)

            violations.append(v)

    print(f"  Parsed {len(violations)} violations")

    # Group and summarize
    groups = group_by_model(violations)
    stats = compute_summary(violations, groups)

    # Write outputs
    os.makedirs(args.output_dir, exist_ok=True)
    md_path = os.path.join(args.output_dir, "readable_explanations.md")
    txt_path = os.path.join(args.output_dir, "readable_explanations.txt")

    write_markdown(stats, groups, md_path)
    print(f"  Wrote {md_path}")

    write_plaintext(stats, groups, txt_path)
    print(f"  Wrote {txt_path}")

    # Print quick summary
    print(f"\nSummary: {stats['total']} violations across {stats['models_affected']} models")
    for cat, count in stats["by_category"]:
        print(f"  {cat}: {count}")


if __name__ == "__main__":
    main()
