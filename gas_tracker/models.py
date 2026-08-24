from __future__ import annotations

import datetime as dt
import math
from dataclasses import dataclass

from .units import DistanceUnit, VolumeUnit, to_km, to_liters


def _require_finite(value: float, name: str) -> None:
    if not math.isfinite(value):
        raise ValueError(f"{name} must be finite")


@dataclass(frozen=True, slots=True)
class Refill:
    date: dt.date
    volume: float
    distance: float | None = None
    cost: float | None = None
    distance_unit: DistanceUnit = DistanceUnit.KM
    volume_unit: VolumeUnit = VolumeUnit.LITER
    currency: str = "USD"
    octane: int | None = None
    station: str | None = None
    odometer: float | None = None
    interpolate_cost: bool = True

    def __post_init__(self) -> None:
        _require_finite(self.volume, "volume")
        if self.volume <= 0:
            raise ValueError("volume must be positive")
        if self.distance is not None:
            _require_finite(self.distance, "distance")
            if self.distance < 0:
                raise ValueError("distance cannot be negative")
        if self.cost is not None:
            _require_finite(self.cost, "cost")
            if self.cost < 0:
                raise ValueError("cost cannot be negative")
        if self.octane is not None and self.octane < 0:
            raise ValueError("octane cannot be negative")
        if self.odometer is not None:
            _require_finite(self.odometer, "odometer")
            if self.odometer < 0:
                raise ValueError("odometer cannot be negative")

    @property
    def volume_l(self) -> float:
        return to_liters(self.volume, self.volume_unit)

    @property
    def distance_km(self) -> float | None:
        if self.distance is None:
            return None
        return to_km(self.distance, self.distance_unit)

    @property
    def odometer_km(self) -> float | None:
        if self.odometer is None:
            return None
        return to_km(self.odometer, self.distance_unit)
