from __future__ import annotations

import datetime as dt
import itertools
from dataclasses import dataclass, replace
from typing import Iterable, Sequence

from .models import Refill


@dataclass(frozen=True, slots=True)
class Sample:
    date: dt.date
    volume_l: float
    distance_km: float | None = None
    cost: float | None = None
    currency: str = "USD"
    odometer_km: float | None = None


@dataclass(frozen=True, slots=True)
class Dataset:
    samples: tuple[Sample, ...]

    @property
    def currency(self) -> str | None:
        if not self.samples:
            return None
        return self.samples[0].currency


def _canonical(refill: Refill) -> Sample:
    return Sample(
        date=refill.date,
        volume_l=refill.volume_l,
        distance_km=refill.distance_km,
        cost=refill.cost,
        currency=refill.currency,
        odometer_km=refill.odometer_km,
    )


def _distances_from_odometer(samples: list[Sample]) -> list[Sample]:
    out: list[Sample] = []
    prev: float | None = None
    for s in samples:
        distance = s.distance_km
        odo = s.odometer_km
        if distance is None and odo is not None and prev is not None:
            distance = odo - prev
            if distance is not None and distance < 0:
                distance = None
        out.append(replace(s, distance_km=distance))
        if odo is not None:
            prev = odo
    return out


def merge_same_day(samples: Sequence[Sample]) -> list[Sample]:
    ordered = sorted(samples, key=lambda s: s.date)
    merged: list[Sample] = []
    for date, group_iter in itertools.groupby(ordered, key=lambda s: s.date):
        group = list(group_iter)
        currencies = {s.currency for s in group}
        if len(currencies) > 1:
            raise ValueError(f"mixed currencies on {date}: {sorted(currencies)}")

        def coalesce(field: str) -> float | None:
            values = [getattr(s, field) for s in group]
            if any(v is None for v in values):
                return None
            return sum(values)

        odometers = [s.odometer_km for s in group if s.odometer_km is not None]
        merged_odometer = max(odometers) if odometers else None
        merged_distance = None if merged_odometer is not None else coalesce("distance_km")

        merged.append(
            Sample(
                date=date,
                volume_l=sum(s.volume_l for s in group),
                distance_km=merged_distance,
                cost=coalesce("cost"),
                currency=currencies.pop(),
                odometer_km=merged_odometer,
            )
        )
    return merged


def _interpolate_distances(samples: list[Sample]) -> list[Sample]:
    cums: list[float | None] = []
    running = 0.0
    for s in samples:
        if s.distance_km is None:
            cums.append(None)
        else:
            running += s.distance_km
            cums.append(running)
    known = [i for i, c in enumerate(cums) if c is not None]
    if len(known) >= 2:
        for a, b in itertools.pairwise(known):
            ca, cb = cums[a], cums[b]
            for i in range(a + 1, b):
                t = (i - a) / (b - a)
                cums[i] = ca + (cb - ca) * t
    distances: list[float | None] = []
    prev = 0.0
    for c in cums:
        if c is None:
            distances.append(None)
        else:
            distances.append(c - prev)
            prev = c
    return [replace(s, distance_km=d) for s, d in zip(samples, distances)]


def _interpolate_costs(samples: list[Sample]) -> list[Sample]:
    ppls: list[float | None] = [
        s.cost / s.volume_l if s.cost is not None else None for s in samples
    ]
    known = [i for i, p in enumerate(ppls) if p is not None]
    out = list(samples)
    for i, s in enumerate(out):
        if s.cost is not None:
            continue
        left = max((k for k in known if k < i), default=None)
        right = min((k for k in known if k > i), default=None)
        if left is None and right is None:
            continue
        if left is None:
            ppl = ppls[right]
        elif right is None:
            ppl = ppls[left]
        else:
            t = (i - left) / (right - left)
            ppl = ppls[left] + (ppls[right] - ppls[left]) * t
        out[i] = replace(s, cost=ppl * s.volume_l)
    return out


def build_dataset(refills: Iterable[Refill]) -> Dataset:
    samples = [_canonical(r) for r in refills]
    if not samples:
        return Dataset(samples=())
    currencies = {s.currency for s in samples}
    if len(currencies) > 1:
        raise ValueError(f"mixed currencies in dataset: {sorted(currencies)}")
    ordered = sorted(samples, key=lambda s: s.date)
    merged = merge_same_day(ordered)
    merged = _distances_from_odometer(merged)
    merged = _interpolate_distances(merged)
    merged = _interpolate_costs(merged)
    return Dataset(samples=tuple(merged))
