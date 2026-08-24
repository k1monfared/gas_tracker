import json
from datetime import date
from pathlib import Path

import pytest

from gas_tracker import Refill, build_dataset, summarize

FIXTURES = Path(__file__).parent / "fixtures" / "core_cases.json"


def _refill(raw: dict) -> Refill:
    payload = dict(raw)
    payload["date"] = date.fromisoformat(payload["date"])
    return Refill(**payload)


def test_shared_core_fixtures():
    data = json.loads(FIXTURES.read_text())
    assert data["version"] == 1
    for case in data["cases"]:
        summary = summarize(build_dataset(_refill(item) for item in case["refills"]))
        expected = case["summary"]
        assert summary.n_refills == expected["n_refills"], case["id"]
        assert summary.total_volume_l == pytest.approx(expected["total_volume_l"]), case["id"]
        if "total_distance_km" in expected:
            assert summary.total_distance_km == pytest.approx(expected["total_distance_km"]), case["id"]
        if "l_per_100_km" in expected:
            assert summary.l_per_100_km == pytest.approx(expected["l_per_100_km"]), case["id"]
        if "total_cost" in expected:
            assert summary.total_cost == pytest.approx(expected["total_cost"]), case["id"]
        if "n_days" in expected:
            assert summary.n_days == expected["n_days"], case["id"]
