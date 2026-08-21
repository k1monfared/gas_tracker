from __future__ import annotations

import datetime as dt
from dataclasses import dataclass

from .processing import Sample

DAYS_PER_WEEK = 7.0
DAYS_PER_MONTH = 28.0
DAYS_PER_YEAR = 365.25


@dataclass(frozen=True, slots=True)
class WindowResult:
    window_days: int
    n_refills: int
    start: dt.date | None
    total_distance_km: float | None
    total_volume_l: float
    total_cost: float | None
    distance_per_day: float | None
    cost_per_day: float | None


@dataclass(frozen=True, slots=True)
class RatioMetrics:
    km_per_l: float | None
    l_per_100_km: float | None
    mpg: float | None
    cost_per_km: float | None
    avg_price_per_liter: float | None


@dataclass(frozen=True, slots=True)
class YearlyView:
    period_days: int
    n_refills: int
    actual_cost: float | None
    actual_distance_km: float | None
    extrapolated_cost: float | None
    extrapolated_distance_km: float | None


def _span_days(start: dt.date, end: dt.date) -> int:
    return max((end - start).days + 1, 1)


def recent_window(
    samples: tuple[Sample, ...] | list[Sample],
    today: dt.date,
    primary_days: int = 28,
    expanded_days: int = 90,
    min_refills: int = 2,
) -> WindowResult:
    ordered = sorted(samples, key=lambda s: s.date)
    if not ordered:
        return WindowResult(
            window_days=primary_days, n_refills=0, start=None,
            total_distance_km=None, total_volume_l=0.0, total_cost=None,
            distance_per_day=None, cost_per_day=None,
        )

    def window(days: int) -> list[Sample]:
        cutoff = today - dt.timedelta(days=days - 1)
        return [s for s in ordered if s.date >= cutoff and s.date <= today]

    chosen = window(primary_days)
    if len(chosen) < min_refills:
        expanded = window(expanded_days)
        if len(expanded) >= min_refills or not chosen:
            chosen = expanded

    window_days = _span_days(min(s.date for s in chosen), today)
    distances = [s.distance_km for s in chosen if s.distance_km is not None]
    costs = [s.cost for s in chosen if s.cost is not None]
    total_distance = sum(distances) if distances else None
    total_cost = sum(costs) if costs else None

    return WindowResult(
        window_days=window_days,
        n_refills=len(chosen),
        start=min(s.date for s in chosen),
        total_distance_km=total_distance,
        total_volume_l=sum(s.volume_l for s in chosen),
        total_cost=total_cost,
        distance_per_day=total_distance / window_days if total_distance is not None else None,
        cost_per_day=total_cost / window_days if total_cost is not None else None,
    )


def flow_value(per_day: float | None, period_days: float) -> float | None:
    if per_day is None:
        return None
    return per_day * period_days


def window_ratios(samples: tuple[Sample, ...] | list[Sample]) -> RatioMetrics:
    distances = [s.distance_km for s in samples if s.distance_km is not None]
    costs = [s.cost for s in samples if s.cost is not None]
    total_distance = sum(distances) if distances else None
    total_volume = sum(s.volume_l for s in samples)
    total_cost = sum(costs) if costs else None

    if total_volume == 0:
        return RatioMetrics(None, None, None, None, None)

    from .units import km_to_miles, liters_to_gallons

    km_per_l = total_distance / total_volume if total_distance else None
    l_per_100 = total_volume / total_distance * 100 if total_distance else None
    mpg = (
        km_to_miles(total_distance) / liters_to_gallons(total_volume)
        if total_distance
        else None
    )
    cost_per_km = (
        total_cost / total_distance
        if total_cost is not None and total_distance
        else None
    )
    avg_price = total_cost / total_volume if total_cost is not None else None

    return RatioMetrics(km_per_l, l_per_100, mpg, cost_per_km, avg_price)


def yearly_view(
    samples: tuple[Sample, ...] | list[Sample],
    today: dt.date,
    year_days: int = 365,
) -> YearlyView:
    ordered = sorted(samples, key=lambda s: s.date)
    cutoff = today - dt.timedelta(days=year_days - 1)
    in_year = [s for s in ordered if s.date >= cutoff and s.date <= today]

    if not in_year:
        return YearlyView(
            period_days=year_days, n_refills=0,
            actual_cost=None, actual_distance_km=None,
            extrapolated_cost=None, extrapolated_distance_km=None,
        )

    distances = [s.distance_km for s in in_year if s.distance_km is not None]
    costs = [s.cost for s in in_year if s.cost is not None]
    total_distance = sum(distances) if distances else None
    total_cost = sum(costs) if costs else None
    coverage_days = _span_days(min(s.date for s in in_year), today)

    return YearlyView(
        period_days=year_days,
        n_refills=len(in_year),
        actual_cost=total_cost,
        actual_distance_km=total_distance,
        extrapolated_cost=flow_value(total_cost / coverage_days if total_cost is not None else None, year_days),
        extrapolated_distance_km=flow_value(
            total_distance / coverage_days if total_distance is not None else None, year_days
        ),
    )
