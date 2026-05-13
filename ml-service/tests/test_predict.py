"""
test_predict.py
---------------
Unit tests for the ML microservice prediction pipeline.
Tests feature engineering and heuristic validation without requiring
the trained model file (mocked away).
"""

import pytest
import numpy as np
from unittest.mock import MagicMock, patch


# ── Feature engineering tests ─────────────────────────────────────────────────

class TestWeatherEncoder:
    def test_sunny_encodes_to_0(self):
        from app.features.weather_encoder import encode_weather
        assert encode_weather("Sunny") == 0

    def test_rainy_encodes_to_2(self):
        from app.features.weather_encoder import encode_weather
        assert encode_weather("Rainy") == 2

    def test_unknown_weather_defaults_to_0(self):
        from app.features.weather_encoder import encode_weather
        assert encode_weather("Foggy") == 0

    def test_all_seasons(self):
        from app.features.weather_encoder import encode_season
        assert encode_season("Spring") == 0
        assert encode_season("Summer") == 1
        assert encode_season("Autumn") == 2
        assert encode_season("Winter") == 3


class TestHourlyAggregator:
    def _make_request(self, **kwargs):
        """Build a minimal ForecastRequest-like object."""
        defaults = dict(
            storeId="S001", productId="P0001",
            category="Groceries", region="North",
            hourOfDay=8, dayOfWeek=0, isWeekend=False, month=6,
            price=50.0, discountPct=10, competitorPrice=48.0,
            weatherCondition="Sunny", isPromotion=False,
            seasonality="Summer", currentInventoryLevel=200,
            eventMultiplier=1.0,
        )
        defaults.update(kwargs)
        return MagicMock(**defaults)

    def test_feature_vector_shape(self):
        from app.features.hourly_aggregator import build_feature_vector, FEATURE_NAMES
        req = self._make_request()
        X = build_feature_vector(req)
        assert X.shape == (1, len(FEATURE_NAMES))

    def test_rush_hour_flag_set(self):
        from app.features.hourly_aggregator import build_feature_vector, FEATURE_NAMES
        req = self._make_request(hourOfDay=8)
        X = build_feature_vector(req)
        rush_idx = FEATURE_NAMES.index("is_rush_hour")
        assert X[0, rush_idx] == 1

    def test_lunch_hour_flag_set(self):
        from app.features.hourly_aggregator import build_feature_vector, FEATURE_NAMES
        req = self._make_request(hourOfDay=13)
        X = build_feature_vector(req)
        lunch_idx = FEATURE_NAMES.index("is_lunch_hour")
        assert X[0, lunch_idx] == 1

    def test_price_diff_computed(self):
        from app.features.hourly_aggregator import build_feature_vector, FEATURE_NAMES
        req = self._make_request(price=60.0, competitorPrice=50.0)
        X = build_feature_vector(req)
        diff_idx = FEATURE_NAMES.index("price_diff")
        assert X[0, diff_idx] == pytest.approx(10.0)

    def test_weekend_flag(self):
        from app.features.hourly_aggregator import build_feature_vector, FEATURE_NAMES
        req = self._make_request(isWeekend=True)
        X = build_feature_vector(req)
        we_idx = FEATURE_NAMES.index("is_weekend")
        assert X[0, we_idx] == 1

    def test_event_multiplier_injected(self):
        from app.features.hourly_aggregator import build_feature_vector, FEATURE_NAMES
        req = self._make_request(eventMultiplier=1.8)
        X = build_feature_vector(req)
        em_idx = FEATURE_NAMES.index("event_multiplier")
        assert X[0, em_idx] == pytest.approx(1.8)


# ── Integration-style test (mocks the model) ──────────────────────────────────

class TestPredictEndpoint:
    @patch("app.model.predict._load")
    @patch("app.model.predict._model")
    @patch("app.model.predict._explainer")
    @patch("app.model.predict._model_version", "xgboost-1.0")
    @patch("app.model.predict._payload", {"metrics": {"MAE": 12.5}})
    def test_predict_returns_non_negative_units(self, mock_explainer, mock_model, mock_load):
        from app.model.predict import run_inference
        from app.schemas import ForecastRequest

        mock_model.predict = MagicMock(return_value=np.array([75.3]))
        mock_explainer.shap_values = MagicMock(
            return_value=[np.array([0.1] * 17)]
        )

        req = ForecastRequest(
            storeId="S001", productId="P0001",
            category="Groceries", region="North",
            hourOfDay=18, dayOfWeek=4, isWeekend=False, month=10,
            price=33.5, discountPct=20, competitorPrice=29.7,
            weatherCondition="Rainy", isPromotion=True,
            seasonality="Autumn", currentInventoryLevel=150,
            eventMultiplier=1.5,
        )

        response = run_inference(req)
        assert response.predictedUnits >= 0
        assert 0.5 <= response.confidenceScore <= 0.99
        assert response.storeId == "S001"
        assert response.productId == "P0001"
        assert len(response.shapExplanation) <= 5
