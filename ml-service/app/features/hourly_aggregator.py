"""
hourly_aggregator.py
---------------------
Transforms a ForecastRequest into a flat feature vector (numpy array)
ready for XGBoost inference. Matches the training feature order exactly.
"""

import numpy as np
from app.features.weather_encoder import (
    encode_weather, encode_season, encode_category, encode_region
)


# Feature names in the exact order used during training
FEATURE_NAMES = [
    "category_enc",
    "region_enc",
    "hour_of_day",
    "day_of_week",
    "is_weekend",
    "month",
    "price",
    "discount_pct",
    "competitor_price",
    "weather_enc",
    "is_promotion",
    "season_enc",
    "inventory_level",
    "event_multiplier",
    "price_diff",           # price - competitor_price (competitive pressure signal)
    "is_rush_hour",         # 1 if hour in {7,8,9,17,18,19,20}
    "is_lunch_hour",        # 1 if hour in {12,13,14}
]

RUSH_HOURS  = {7, 8, 9, 17, 18, 19, 20}
LUNCH_HOURS = {12, 13, 14}


def build_feature_vector(req) -> np.ndarray:
    """
    Convert a ForecastRequest into a feature vector.

    Args:
        req: ForecastRequest (Pydantic model from schemas.py)

    Returns:
        numpy array of shape (1, n_features)
    """
    price           = req.price or 50.0
    competitor_price = req.competitorPrice or price
    price_diff      = price - competitor_price

    features = [
        encode_category(req.category),
        encode_region(req.region),
        req.hourOfDay,
        req.dayOfWeek,
        int(req.isWeekend),
        req.month,
        price,
        req.discountPct or 0,
        competitor_price,
        encode_weather(req.weatherCondition or "Sunny"),
        int(req.isPromotion or False),
        encode_season(req.seasonality or "Summer"),
        req.currentInventoryLevel or 0,
        req.eventMultiplier or 1.0,
        price_diff,
        int(req.hourOfDay in RUSH_HOURS),
        int(req.hourOfDay in LUNCH_HOURS),
    ]

    return np.array([features], dtype=np.float64)


def get_feature_names() -> list:
    return FEATURE_NAMES
