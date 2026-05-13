"""
weather_encoder.py
------------------
Encodes categorical weather and seasonality features into numeric form
for the XGBoost model. Uses consistent label encoding that matches the
training pipeline.
"""

WEATHER_MAP = {
    "Sunny":  0,
    "Cloudy": 1,
    "Rainy":  2,
    "Snowy":  3,
}

SEASON_MAP = {
    "Spring": 0,
    "Summer": 1,
    "Autumn": 2,
    "Winter": 3,
}

CATEGORY_MAP = {
    "Groceries":   0,
    "Electronics": 1,
    "Clothing":    2,
    "Toys":        3,
    "Furniture":   4,
}

REGION_MAP = {
    "North": 0,
    "South": 1,
    "East":  2,
    "West":  3,
}


def encode_weather(weather: str) -> int:
    """Convert weather condition string to integer code."""
    return WEATHER_MAP.get(weather, 0)


def encode_season(season: str) -> int:
    """Convert seasonality string to integer code."""
    return SEASON_MAP.get(season, 1)


def encode_category(category: str) -> int:
    """Convert product category string to integer code."""
    return CATEGORY_MAP.get(category, 0)


def encode_region(region: str) -> int:
    """Convert region string to integer code."""
    return REGION_MAP.get(region, 0)
