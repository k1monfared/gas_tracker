from __future__ import annotations

import datetime as dt
from dataclasses import dataclass
from typing import Literal

from .processing import Dataset, Sample, paired_cost_distance, paired_distance_volume
from .units import km_to_miles, liters_to_gallons

PeriodKind = Literal["week", "month", "year"]

DAYS_PER_WEEK = 7.0
DAYS_PER_MONTH = 30.4375
DAYS_PER_YEAR = 365.25


@dataclass(frozen=True, slots=True)
class EfficiencyPoint:
    date: dt.date
    l_per_100_km: float
    mpg: float
    km_per_l: float
    cost_per_km: float | None = None
    price_per_liter: float | None = None


@dataclass(frozen=True, slots=True)
class PeriodPoint:
    key: str
    start: dt.date
    distance_km: float | None
    volume_l: float
    cost: float | None
    l_per_100_km: float | None
    mpg: float | None


@dataclass(frozen=True, slots=True)
class Summary:
    n_refills: int
    n_days: int
    total_distance_km: float | None
    total_volume_l: float
    total_cost: float | None
    km_per_l: float | None
    l_per_100_km: float | None
    mpg: float | None
    cost_per_km: float | None
    avg_price_per_liter: float | None
    distance_per_day: float | None
    cost_per_day: float | None
    cost_per_week: float | None
    cost_per_month: float | None
    cost_per_year: float | None
    mean_days_between_refills: float | None


def _span_days(samples: tuple[Sample, ...]) -> int:
    if len(samples) < 2:
        return 1
    return max((samples[-1].date - samples[0].date).days, 1)


def _efficiency(distance: float, volume: float) -> tuple[float, float, float]:
    km_per_l = distance / volume
    l_per_100 = volume / distance * 100
    mpg = km_to_miles(distance) / liters_to_gallons(volume)
    return km_per_l, l_per_100, mpg


def summarize(data: Dataset) -> Summary:
    samples = data.samples
    total_volume = sum(s.volume_l for s in samples)
    distances = [s.distance_km for s in samples if s.distance_km is not None]
    costs = [s.cost for s in samples if s.cost is not None]
    total_distance = sum(distances) if distances else None
    total_cost = sum(costs) if costs else None
    n_days = _span_days(samples)

    paired = paired_distance_volume(samples)
    if paired:
        km_per_l, l_per_100_km, mpg = _efficiency(paired[0], paired[1])
    else:
        km_per_l = l_per_100_km = mpg = None

    cost_pair = paired_cost_distance(samples)
    cost_per_km = cost_pair[0] / cost_pair[1] if cost_pair else None
    priced = [s for s in samples if s.cost is not None]
    avg_price = (
        sum(s.cost for s in priced) / sum(s.volume_l for s in priced) if priced else None
    )

    gaps = [
        (b.date - a.date).days
        for a, b in zip(samples, samples[1:])
        if b.date > a.date
    ]
    mean_gap = sum(gaps) / len(gaps) if gaps else None

    def per_day(amount: float | None) -> float | None:
        return amount / n_days if amount is not None else None

    return Summary(
        n_refills=len(samples),
        n_days=n_days,
        total_distance_km=total_distance,
        total_volume_l=total_volume,
        total_cost=total_cost,
        km_per_l=km_per_l,
        l_per_100_km=l_per_100_km,
        mpg=mpg,
        cost_per_km=cost_per_km,
        avg_price_per_liter=avg_price,
        distance_per_day=per_day(total_distance),
        cost_per_day=per_day(total_cost),
        cost_per_week=per_day(total_cost) * DAYS_PER_WEEK if total_cost is not None else None,
        cost_per_month=per_day(total_cost) * DAYS_PER_MONTH if total_cost is not None else None,
        cost_per_year=per_day(total_cost) * DAYS_PER_YEAR if total_cost is not None else None,
        mean_days_between_refills=mean_gap,
    )


def efficiency_series(data: Dataset) -> list[EfficiencyPoint]:
    points: list[EfficiencyPoint] = []
    for s in data.samples:
        d = s.distance_km
        if not d:
            continue
        km_per_l = d / s.volume_l
        cost_per_km = s.cost / d if s.cost is not None else None
        price_per_liter = s.cost / s.volume_l if s.cost is not None else None
        points.append(
            EfficiencyPoint(
                date=s.date,
                l_per_100_km=s.volume_l / d * 100,
                mpg=km_to_miles(d) / liters_to_gallons(s.volume_l),
                km_per_l=km_per_l,
                cost_per_km=cost_per_km,
                price_per_liter=price_per_liter,
            )
        )
    return points


def _period_start_and_key(date: dt.date, kind: PeriodKind) -> tuple[dt.date, str]:
    if kind == "week":
        iso = date.isocalendar()
        return dt.date.fromisocalendar(iso.year, iso.week, 1), f"{iso.year:04d}-W{iso.week:02d}"
    if kind == "month":
        return date.replace(day=1), f"{date.year:04d}-{date.month:02d}"
    if kind == "year":
        return date.replace(month=1, day=1), f"{date.year:04d}"
    raise ValueError(f"unknown period kind: {kind}")


def period_series(data: Dataset, kind: PeriodKind) -> list[PeriodPoint]:
    buckets: dict[str, list[Sample]] = {}
    starts: dict[str, dt.date] = {}
    for s in data.samples:
        start, key = _period_start_and_key(s.date, kind)
        buckets.setdefault(key, []).append(s)
        starts[key] = min(starts.get(key, start), start)

    points: list[PeriodPoint] = []
    for key in sorted(buckets):
        group = buckets[key]
        distance = sum(s.distance_km for s in group if s.distance_km is not None)
        any_distance = any(s.distance_km is not None for s in group)
        costs = [s.cost for s in group if s.cost is not None]
        cost = sum(costs) if costs else None
        volume = sum(s.volume_l for s in group)
        paired = paired_distance_volume(group)
        if paired:
            _, l_per_100, mpg = _efficiency(paired[0], paired[1])
        else:
            l_per_100 = mpg = None
        points.append(
            PeriodPoint(
                key=key,
                start=starts[key],
                distance_km=distance if any_distance else None,
                volume_l=volume,
                cost=cost,
                l_per_100_km=l_per_100,
                mpg=mpg,
            )
        )
    return points


def average_cost_per_period(data: Dataset, kind: PeriodKind) -> float | None:
    summary = summarize(data)
    if summary.total_cost is None:
        return None
    divisors: dict[str, float] = {
        "week": DAYS_PER_WEEK,
        "month": DAYS_PER_MONTH,
        "year": DAYS_PER_YEAR,
    }
    if kind == "day":
        return summary.cost_per_day
    return summary.total_cost / (summary.n_days / divisors[kind])
