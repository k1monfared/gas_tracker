import datetime as dt

from gas_tracker import nearest_rate_at, convert_amount


def test_same_currency_rate_is_one():
    assert nearest_rate_at({}, dt.date(2026, 8, 21), "EUR", "EUR") == 1.0


def test_exact_date_lookup():
    rates = {(dt.date(2026, 8, 21), "USD", "CAD"): 1.35}
    assert nearest_rate_at(rates, dt.date(2026, 8, 21), "USD", "CAD") == 1.35


def test_lookback_finds_nearest_previous():
    rates = {
        (dt.date(2026, 8, 18), "USD", "CAD"): 1.34,
        (dt.date(2026, 8, 21), "USD", "CAD"): 1.35,
    }
    assert nearest_rate_at(rates, dt.date(2026, 8, 22), "USD", "CAD") == 1.35
    assert nearest_rate_at(rates, dt.date(2026, 8, 20), "USD", "CAD") == 1.34


def test_missing_rate_returns_none():
    assert nearest_rate_at({}, dt.date(2026, 8, 21), "USD", "CAD") is None


def test_convert_amount():
    assert convert_amount(100.0, 1.35) == 135.0
    assert convert_amount(100.0, None) is None
