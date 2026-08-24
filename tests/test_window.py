import datetime as dt

from gas_tracker import (
    Refill,
    RatioMetrics,
    WindowResult,
    YearlyView,
    flow_value,
    recent_window,
    window_ratios,
    yearly_view,
)
from gas_tracker.processing import Sample


def s(date: str, volume: float, distance: float | None = None, cost: float | None = None):
    return Sample(date=dt.date.fromisoformat(date), volume_l=volume, distance_km=distance, cost=cost)


def test_empty_window():
    result = recent_window([], dt.date(2026, 8, 21))
    assert result == WindowResult(
        window_days=28, n_refills=0, start=None,
        total_distance_km=None, total_volume_l=0.0, total_cost=None,
        distance_per_day=None, cost_per_day=None,
    )


def test_primary_window_used_when_enough_data():
    today = dt.date(2026, 8, 21)
    samples = [
        s("2026-08-21", 40, 500, 80),
        s("2026-08-14", 40, 500, 80),
        s("2026-06-01", 40, 500, 80),
    ]
    result = recent_window(samples, today, primary_days=28, expanded_days=90, min_refills=2)
    assert result.n_refills == 2
    assert result.window_days == 8
    assert result.total_cost == 160
    assert result.total_distance_km == 1000
    assert result.total_volume_l == 80


def test_expands_to_three_months_when_sparse():
    today = dt.date(2026, 8, 21)
    samples = [
        s("2026-08-21", 40, 500, 80),
        s("2026-06-01", 40, 500, 80),
    ]
    result = recent_window(samples, today, primary_days=28, expanded_days=90, min_refills=2)
    assert result.n_refills == 2
    assert result.window_days == (today - dt.date(2026, 6, 1)).days + 1


def test_uses_primary_if_at_least_min_refills():
    today = dt.date(2026, 8, 21)
    samples = [s("2026-08-21", 40, 500, 80), s("2026-08-01", 40, 500, 80)]
    result = recent_window(samples, today)
    assert result.n_refills == 2
    assert result.window_days == 21


def test_flow_value_scaling():
    assert flow_value(10.0, 7) == 70.0
    assert flow_value(10.0, 28) == 280.0
    assert flow_value(10.0, 365.25) == 3652.5
    assert flow_value(None, 28) is None


def test_window_ratios():
    ratios = window_ratios([s("2026-08-21", 40, 500, 80)])
    assert isinstance(ratios, RatioMetrics)
    assert ratios.l_per_100_km == 8.0
    assert ratios.cost_per_km == 0.16
    assert ratios.avg_price_per_liter == 2.0


def test_window_ratios_missing_distance():
    ratios = window_ratios([s("2026-08-21", 40, None, 80)])
    assert ratios.l_per_100_km is None


def test_yearly_view_extrapolates():
    today = dt.date(2026, 8, 21)
    samples = [
        s("2026-08-21", 40, 500, 80),
        s("2026-07-21", 40, 500, 80),
    ]
    view = yearly_view(samples, today)
    assert isinstance(view, YearlyView)
    assert view.n_refills == 2
    assert view.actual_cost == 160
    assert view.actual_distance_km == 1000
    coverage = (today - dt.date(2026, 7, 21)).days + 1
    assert view.extrapolated_cost == 160 / coverage * view.period_days
    assert view.extrapolated_distance_km == 1000 / coverage * view.period_days


def test_yearly_view_empty():
    view = yearly_view([], dt.date(2026, 8, 21))
    assert view.n_refills == 0
    assert view.actual_cost is None


def test_single_refill_does_not_extrapolate():
    today = dt.date(2026, 8, 21)
    result = recent_window([s("2026-08-21", 40, 500, 80)], today)
    assert result.n_refills == 1
    assert result.can_extrapolate is False
    assert result.cost_per_day is None
    view = yearly_view([s("2026-08-21", 40, 500, 80)], today)
    assert view.actual_cost == 80
    assert view.extrapolated_cost is None


def test_empty_after_fallback_window_reports_expanded_days():
    today = dt.date(2026, 8, 21)
    result = recent_window([s("2026-01-01", 40, 500, 80)], today)
    assert result.n_refills == 0
    assert result.window_days == 90


def test_window_ratios_partial_distance():
    ratios = window_ratios(
        [
            s("2026-01-01", 40, None, 80),
            s("2026-02-01", 40, 500, 80),
        ]
    )
    assert ratios.l_per_100_km == 8.0
    assert ratios.cost_per_km == 0.16


def test_octane_and_station_roundtrip():
    r = Refill(
        date=dt.date(2026, 8, 21),
        volume=40,
        octane=95,
        station="Shell",
    )
    assert r.octane == 95
    assert r.station == "Shell"


def test_negative_octane_rejected():
    try:
        Refill(date=dt.date(2026, 8, 21), volume=40, octane=-1)
        assert False
    except ValueError:
        pass
