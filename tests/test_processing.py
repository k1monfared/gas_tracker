import datetime as dt
import math

import pytest

from gas_tracker import DistanceUnit, Refill, VolumeUnit, build_dataset, merge_same_day
from gas_tracker.processing import Sample


def r(date, volume, distance=None, cost=None):
    return Refill(date=dt.date.fromisoformat(date), volume=volume, distance=distance, cost=cost)


def test_empty_dataset():
    data = build_dataset([])
    assert data.samples == ()
    assert data.currency is None


def test_unsorted_input_is_sorted():
    data = build_dataset(
        [
            r("2026-03-01", 40, 500, 80),
            r("2026-01-01", 40, 500, 80),
        ]
    )
    assert [s.date for s in data.samples] == [
        dt.date(2026, 1, 1),
        dt.date(2026, 3, 1),
    ]


def test_unit_normalization():
    data = build_dataset(
        [
            Refill(
                date=dt.date(2026, 1, 1),
                volume=1,
                distance=100,
                distance_unit=DistanceUnit.MILE,
                volume_unit=VolumeUnit.GALLON,
            )
        ]
    )
    s = data.samples[0]
    assert s.volume_l == pytest.approx(3.785411784)
    assert s.distance_km == pytest.approx(160.9344)


def test_same_day_double_refill_is_merged():
    samples = [
        Sample(dt.date(2026, 5, 1), volume_l=30, distance_km=200, cost=60),
        Sample(dt.date(2026, 5, 1), volume_l=10, distance_km=0, cost=20),
    ]
    merged = merge_same_day(samples)
    assert len(merged) == 1
    assert merged[0].volume_l == 40
    assert merged[0].distance_km == 200
    assert merged[0].cost == 80


def test_merged_dataset_has_no_infinite_efficiency():
    data = build_dataset(
        [
            r("2026-05-01", 30, 200, 60),
            r("2026-05-01", 10, 0, 20),
        ]
    )
    for s in data.samples:
        if s.distance_km:
            assert s.volume_l / s.distance_km != math.inf


def test_interior_missing_distance_is_interpolated():
    data = build_dataset(
        [
            r("2026-01-01", 10, 100),
            r("2026-01-10", 10, None),
            r("2026-01-20", 10, None),
            r("2026-01-30", 10, 60),
        ]
    )
    distances = [s.distance_km for s in data.samples]
    assert distances == pytest.approx([100, 20, 20, 20])
    assert sum(d for d in distances if d is not None) == pytest.approx(160)


def test_leading_missing_distance_stays_none():
    data = build_dataset(
        [
            r("2026-01-01", 10, None),
            r("2026-01-10", 10, 100),
        ]
    )
    assert data.samples[0].distance_km is None
    assert data.samples[1].distance_km == pytest.approx(100)


def test_missing_cost_interpolated_from_price_per_liter():
    data = build_dataset(
        [
            r("2026-01-01", 10, 100, 100),
            r("2026-01-10", 10, 100, None),
            r("2026-01-20", 10, 100, 130),
        ]
    )
    assert data.samples[1].cost == pytest.approx(115)


def test_edge_missing_cost_uses_nearest_known_price():
    data = build_dataset(
        [
            r("2026-01-01", 10, 100, None),
            r("2026-01-10", 10, 100, 120),
        ]
    )
    assert data.samples[0].cost == pytest.approx(120)


def test_mixed_currencies_raise():
    with pytest.raises(ValueError):
        build_dataset(
            [
                r("2026-01-01", 10, 100, 100),
                Refill(date=dt.date(2026, 1, 10), volume=10, distance=100, cost=100, currency="EUR"),
            ]
        )


def test_distance_computed_from_odometer():
    data = build_dataset(
        [
            Refill(date=dt.date(2026, 1, 1), volume=40, odometer=1000),
            Refill(date=dt.date(2026, 2, 1), volume=40, odometer=1500),
            Refill(date=dt.date(2026, 3, 1), volume=40, odometer=2100),
        ]
    )
    distances = [s.distance_km for s in data.samples]
    assert distances[0] is None
    assert distances[1] == pytest.approx(500)
    assert distances[2] == pytest.approx(600)


def test_same_day_double_refill_with_odometer():
    data = build_dataset(
        [
            Refill(date=dt.date(2026, 4, 1), volume=30, odometer=1000),
            Refill(date=dt.date(2026, 5, 1), volume=30, odometer=1500),
            Refill(date=dt.date(2026, 5, 1), volume=10, odometer=1550),
        ]
    )
    assert len(data.samples) == 2
    assert data.samples[0].distance_km is None
    assert data.samples[1].distance_km == pytest.approx(550)
    assert data.samples[1].volume_l == pytest.approx(40)


def test_negative_odometer_rejected():
    with pytest.raises(ValueError):
        Refill(date=dt.date(2026, 1, 1), volume=10, odometer=-5)


def test_invalid_refill_rejected():
    with pytest.raises(ValueError):
        r("2026-01-01", 0)
    with pytest.raises(ValueError):
        r("2026-01-01", 10, distance=-5)
    with pytest.raises(ValueError):
        r("2026-01-01", 10, cost=-1)
