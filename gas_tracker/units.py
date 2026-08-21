from __future__ import annotations

import enum

KM_PER_MILE = 1.609344
LITERS_PER_GALLON = 3.785411784


class DistanceUnit(enum.Enum):
    KM = "km"
    MILE = "mi"


class VolumeUnit(enum.Enum):
    LITER = "l"
    GALLON = "gal"


def miles_to_km(miles: float) -> float:
    return miles * KM_PER_MILE


def km_to_miles(km: float) -> float:
    return km / KM_PER_MILE


def gallons_to_liters(gallons: float) -> float:
    return gallons * LITERS_PER_GALLON


def liters_to_gallons(liters: float) -> float:
    return liters / LITERS_PER_GALLON


def to_km(value: float, unit: DistanceUnit) -> float:
    if unit is DistanceUnit.KM:
        return value
    if unit is DistanceUnit.MILE:
        return miles_to_km(value)
    raise ValueError(f"unknown distance unit: {unit}")


def from_km(value: float, unit: DistanceUnit) -> float:
    if unit is DistanceUnit.KM:
        return value
    if unit is DistanceUnit.MILE:
        return km_to_miles(value)
    raise ValueError(f"unknown distance unit: {unit}")


def to_liters(value: float, unit: VolumeUnit) -> float:
    if unit is VolumeUnit.LITER:
        return value
    if unit is VolumeUnit.GALLON:
        return gallons_to_liters(value)
    raise ValueError(f"unknown volume unit: {unit}")


def from_liters(value: float, unit: VolumeUnit) -> float:
    if unit is VolumeUnit.LITER:
        return value
    if unit is VolumeUnit.GALLON:
        return liters_to_gallons(value)
    raise ValueError(f"unknown volume unit: {unit}")
