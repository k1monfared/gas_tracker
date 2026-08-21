from __future__ import annotations

import datetime as dt


RatesTable = dict[tuple[dt.date, str, str], float]


def nearest_rate_at(
    rates: RatesTable,
    day: dt.date,
    from_currency: str,
    to_currency: str,
    max_lookback: int = 7,
) -> float | None:
    if from_currency == to_currency:
        return 1.0
    key = (day, from_currency, to_currency)
    rate = rates.get(key)
    if rate is not None:
        return rate
    for i in range(1, max_lookback + 1):
        prev = day - dt.timedelta(days=i)
        rate = rates.get((prev, from_currency, to_currency))
        if rate is not None:
            return rate
    return None


def convert_amount(amount: float, rate: float | None) -> float | None:
    if rate is None:
        return None
    return amount * rate
