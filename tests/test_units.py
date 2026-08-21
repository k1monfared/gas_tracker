import math

from gas_tracker.units import (
    DistanceUnit,
    VolumeUnit,
    from_km,
    from_liters,
    gallons_to_liters,
    km_to_miles,
    liters_to_gallons,
    miles_to_km,
    to_km,
    to_liters,
)


def test_mile_conversion():
    assert miles_to_km(1) == 1.609344
    assert km_to_miles(1.609344) == 1


def test_gallon_conversion():
    assert gallons_to_liters(1) == 3.785411784
    assert liters_to_gallons(3.785411784) == 1


def test_roundtrips():
    assert math.isclose(km_to_miles(miles_to_km(42.5)), 42.5)
    assert math.isclose(liters_to_gallons(gallons_to_liters(13.3)), 13.3)


def test_enum_dispatch():
    assert to_km(1, DistanceUnit.MILE) == 1.609344
    assert to_km(5, DistanceUnit.KM) == 5
    assert from_km(1.609344, DistanceUnit.MILE) == 1
    assert to_liters(1, VolumeUnit.GALLON) == 3.785411784
    assert from_liters(2, VolumeUnit.LITER) == 2
