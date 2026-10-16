import copy
import json
import unittest
from pathlib import Path

try:
    from jsonschema.exceptions import ValidationError
    from validate_capabilities import validate_document
except ModuleNotFoundError:
    validate_document = None
    ValidationError = ValueError

FIXTURES = Path(__file__).resolve().parents[2] / "protocol" / "fixtures"


@unittest.skipIf(validate_document is None, "Install schema-requirements.txt to run capability schema validation")
class CapabilitySchemaTest(unittest.TestCase):
    def setUp(self):
        self.document = json.loads((FIXTURES / "capability-list.json").read_text(encoding="utf-8"))

    def test_new_and_legacy_examples(self):
        validate_document(self.document)
        validate_document(json.loads((FIXTURES / "device.json").read_text(encoding="utf-8")))

    def test_bad_names_and_ranges_are_rejected(self):
        for name in ["", " ", "x" * 65, "bad\nname"]:
            doc = copy.deepcopy(self.document)
            doc["name"] = name
            with self.assertRaises(ValidationError):
                validate_document(doc)
        self.document["resources"][2]["properties"][0]["min"] = 200
        with self.assertRaises(ValueError):
            validate_document(self.document)

    def test_duplicate_ids_are_rejected(self):
        self.document["resources"][1]["id"] = self.document["resources"][0]["id"]
        with self.assertRaises(ValueError):
            validate_document(self.document)

    def test_missing_numeric_constraints_are_rejected(self):
        del self.document["resources"][2]["properties"][0]["step"]
        with self.assertRaises(ValidationError):
            validate_document(self.document)

    def test_empty_list_and_unknown_types_are_compatible(self):
        self.document["resources"][0]["kind"] = "future-output"
        self.document["resources"][0]["properties"][0]["type"] = "future-value"
        validate_document(self.document)
        self.document["resources"] = []
        validate_document(self.document)


if __name__ == "__main__":
    unittest.main()
