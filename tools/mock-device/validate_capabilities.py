"""Validate a v1 capability list for firmware handoff. Optional jsonschema dependency."""
import argparse
import json
import math
from pathlib import Path

from jsonschema import Draft202012Validator

ROOT = Path(__file__).resolve().parents[2]
SCHEMA = json.loads((ROOT / "protocol" / "capability-list.schema.json").read_text(encoding="utf-8"))


def validate_document(document):
    Draft202012Validator.check_schema(SCHEMA)
    Draft202012Validator(SCHEMA).validate(document)
    if len(json.dumps(document, ensure_ascii=False, allow_nan=False, separators=(",", ":")).encode("utf-8")) > 16384:
        raise ValueError("Capability response exceeds 16 KiB in compact JSON")
    resource_ids = set()
    for resource in document["resources"]:
        if resource["id"] in resource_ids:
            raise ValueError(f"Duplicate resource ID: {resource['id']}")
        resource_ids.add(resource["id"])
        property_ids = set()
        for prop in resource["properties"]:
            if prop["id"] in property_ids:
                raise ValueError(f"Duplicate property ID: {resource['id']}/{prop['id']}")
            property_ids.add(prop["id"])
            if prop["writable"] and prop["type"] in ("integer", "number"):
                if not all(math.isfinite(prop[key]) for key in ("min", "max", "step")) or prop["min"] > prop["max"]:
                    raise ValueError(f"Invalid numeric range: {resource['id']}/{prop['id']}")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("files", nargs="*", type=Path, default=[ROOT / "protocol" / "fixtures" / "device.json", ROOT / "protocol" / "fixtures" / "capability-list.json"])
    args = parser.parse_args()
    for path in args.files:
        raw = path.read_bytes()
        if len(raw) > 16384:
            raise ValueError(f"File exceeds the response size limit: {path}")
        validate_document(json.loads(raw))
        print(f"PASS {path}")


if __name__ == "__main__":
    main()
