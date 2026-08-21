import datetime as dt

import pytest

from gas_tracker import (
    Refill,
    average_cost_per_period,
    build_dataset,
    efficiency_series,
    period_series,
    summarize,
)


@pytest.fixture
def steady_data():
    refills = [
        Refill(date=dt.date(2026, 6, 15), volume=40, distance=500, cost=80),
        Refill(date=dt.date(2026, 7, 15), volume=40, distance=500, cost=80),
        Refill(date=dt.date(2026, 8, 15), volume=40, distance=500, cost=80),
    ]
    return build_dataset(refills)


def test_summary_steady_consumption(steady_data):
    s = summarize(steady_data)
    assert s.n_refills == 3
    assert s.n_days == 61
    assert s.total_volume_l == pytest.approx(120)
    assert s.total_distance_km == pytest.approx(1500)
    assert s.total_cost == pytest.approx(240)
    assert s.l_per_100_km == pytest.approx(8.0)
    assert s.km_per_l == pytest.approx(12.5)
    assert s.mpg == pytest.approx((1500 / 1.609344) / (120 / 3.785411784))
    assert s.cost_per_km == pytest.approx(0.16)
    assert s.avg_price_per_liter == pytest.approx(2.0)
    assert s.distance_per_day == pytest.approx(1500 / 61)
    assert s.cost_per_week == pytest.approx(240 / 61 * 7)
    assert s.mean_days_between_refills == pytest.approx(30.5)


def test_average_cost_per_period(steady_data):
    assert average_cost_per_period(steady_data, "week") == pytest.approx(240 / (61 / 7))
    assert average_cost_per_period(steady_data, "month") == pytest.approx(240 / (61 / 30.4375))
    assert average_cost_per_period(steady_data, "year") == pytest.approx(240 / (61 / 365.25))


def test_period_series_monthly(steady_data):
    points = period_series(steady_data, "month")
    assert [p.key for p in points] == ["2026-06", "2026-07", "2026-08"]
    assert all(p.volume_l == pytest.approx(40) for p in points)
    assert all(p.l_per_100_km == pytest.approx(8.0) for p in points)
    assert points[0].start == dt.date(2026, 6, 1)


def test_period_series_yearly(steady_data):
    points = period_series(steady_data, "year")
    assert [p.key for p in points] == ["2026"]
    assert points[0].volume_l == pytest.approx(120)
    assert points[0].l_per_100_km == pytest.approx(8.0)


def test_period_series_iso_week():
    data = build_dataset(
        [
            Refill(date=dt.date(2026, 1, 5), volume=40, distance=500, cost=80),
            Refill(date=dt.date(2026, 1, 7), volume=40, distance=500, cost=80),
        ]
    )
    points = period_series(data, "week")
    assert len(points) == 1
    assert points[0].key == "2026-W02"


def test_efficiency_series_skips_unknown_distance():
    data = build_dataset(
        [
            Refill(date=dt.date(2026, 1, 1), volume=40, distance=None, cost=80),
            Refill(date=dt.date(2026, 2, 1), volume=40, distance=500, cost=80),
        ]
    )
    points = efficiency_series(data)
    assert len(points) == 1
    assert points[0].l_per_100_km == pytest.approx(8.0)
    assert points[0].mpg == pytest.approx((500 / 1.609344) / (40 / 3.785411784))
    assert points[0].cost_per_km == pytest.approx(0.16)


def test_single_sample_span_guard():
    data = build_dataset([Refill(date=dt.date(2026, 1, 1), volume=40, distance=500, cost=80)])
    s = summarize(data)
    assert s.n_days == 1
    assert s.cost_per_day == pytest.approx(80)
    assert s.mean_days_between_refills is None


def test_empty_dataset_summary():
    s = summarize(build_dataset([]))
    assert s.n_refills == 0
    assert s.total_cost is None
    assert s.mpg is None
    assert average_cost_per_period(build_dataset([]), "month") is None
