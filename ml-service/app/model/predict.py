"""
predict.py
----------
Loads the serialized XGBoost model and runs inference with SHAP explanations.
Called by main.py on every /predict request from the Java backend.
"""

import shap
import joblib
import numpy as np
from pathlib import Path
from datetime import datetime, timedelta

from app.features.hourly_aggregator import build_feature_vector, get_feature_names
from app.schemas import ForecastRequest, ForecastResponse

MODEL_PATH = Path(__file__).parent / "forecast_model.pkl"

# ── Lazy-loaded globals ────────────────────────────────────────────────────────
_payload       = None
_model         = None
_explainer     = None
_model_version = "xgboost-1.0"


def _load():
    """Load model from disk (once, at first request)."""
    global _payload, _model, _explainer, _model_version

    if _model is not None:
        return  # already loaded

    if not MODEL_PATH.exists():
        raise FileNotFoundError(
            f"Model not found at {MODEL_PATH}. "
            "Run: python -m app.model.train"
        )

    _payload       = joblib.load(MODEL_PATH)
    _model         = _payload["model"]
    _model_version = _payload.get("model_version", "xgboost-1.0")

    # Build SHAP TreeExplainer (fast, exact for tree models)
    _explainer = shap.TreeExplainer(_model)


def is_model_loaded() -> bool:
    return _model is not None


def get_model_version() -> str:
    _load()
    return _model_version


def run_inference(req: ForecastRequest) -> ForecastResponse:
    """
    Run XGBoost prediction + SHAP explanation for one feature vector.

    Args:
        req: ForecastRequest from the API

    Returns:
        ForecastResponse with predictedUnits, confidence, shapExplanation
    """
    _load()

    # Build feature matrix
    X = build_feature_vector(req)

    # Predict
    raw_prediction = float(_model.predict(X)[0])
    predicted_units = max(0, int(round(raw_prediction)))

    # SHAP explanation — shap>=0.46 returns an Explanation object
    shap_explanation = _explainer(X)
    shap_values_array = shap_explanation.values[0]   # shape: (n_features,)
    feature_names  = get_feature_names()

    # Sort by absolute SHAP value, take top 5
    shap_dict      = dict(zip(feature_names, shap_values_array))
    top_shap       = dict(
        sorted(shap_dict.items(), key=lambda kv: abs(kv[1]), reverse=True)[:5]
    )
    # Round to 4 decimal places for clean JSON
    top_shap       = {k: round(float(v), 4) for k, v in top_shap.items()}

    # Confidence: derived from prediction relative to training MAE
    # Uses a simple heuristic: higher predictions with lower uncertainty = higher confidence
    model_mae      = _payload.get("metrics", {}).get("MAE", 15.0)
    confidence     = float(np.clip(1.0 - (model_mae / max(predicted_units, model_mae + 1)), 0.5, 0.99))

    # Forecast target: next full hour
    forecast_time  = (datetime.now().replace(minute=0, second=0, microsecond=0)
                      + timedelta(hours=1))

    return ForecastResponse(
        storeId         = req.storeId,
        productId       = req.productId,
        forecastTime    = forecast_time.isoformat(),
        predictedUnits  = predicted_units,
        confidenceScore = round(confidence, 4),
        modelVersion    = _model_version,
        shapExplanation = top_shap,
    )
