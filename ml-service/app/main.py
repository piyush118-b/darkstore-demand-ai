"""
main.py
-------
FastAPI entry point for the Dark Store ML microservice.

Endpoints:
  POST /predict  — Run XGBoost forecast for a product-store-hour
  GET  /health   — Health check (confirms model is loaded)
  GET  /model    — Returns model metadata (version, training metrics)

Run locally:
  uvicorn app.main:app --reload --port 8000
"""

from fastapi import FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from contextlib import asynccontextmanager

from app.schemas import ForecastRequest, ForecastResponse, HealthResponse
from app.model.predict import run_inference, is_model_loaded, get_model_version
from app.model.predict import _load, _payload


# ── Lifespan: preload model on startup ────────────────────────────────────────
@asynccontextmanager
async def lifespan(app: FastAPI):
    """Pre-warm the model at startup so first request is fast."""
    try:
        _load()
        print(f"✅ Model loaded: {get_model_version()}")
    except FileNotFoundError as e:
        print(f"⚠️  Model not found: {e}")
        print("   Run: python -m app.model.train")
    yield
    # cleanup (if needed)


# ── App ───────────────────────────────────────────────────────────────────────
app = FastAPI(
    title="Dark Store Demand Forecasting — ML Microservice",
    description=(
        "XGBoost-based demand forecasting microservice for quick-commerce dark stores. "
        "Predicts hourly units sold based on weather, pricing, events, and time signals. "
        "Returns SHAP feature explanations with every prediction."
    ),
    version="1.0.0",
    lifespan=lifespan,
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=["http://localhost:8080"],   # Spring Boot backend
    allow_methods=["GET", "POST"],
    allow_headers=["*"],
)


# ── Endpoints ─────────────────────────────────────────────────────────────────

@app.post(
    "/predict",
    response_model=ForecastResponse,
    summary="Generate demand forecast",
    description=(
        "Accepts a feature vector from the Spring Boot backend and returns "
        "the predicted units sold for the next hour along with SHAP explanations."
    ),
)
def predict(req: ForecastRequest) -> ForecastResponse:
    if not is_model_loaded():
        raise HTTPException(
            status_code=503,
            detail="Model not loaded. Run python -m app.model.train first."
        )
    try:
        return run_inference(req)
    except Exception as exc:
        raise HTTPException(status_code=500, detail=str(exc))


@app.get(
    "/health",
    response_model=HealthResponse,
    summary="Health check",
)
def health() -> HealthResponse:
    loaded = is_model_loaded()
    return HealthResponse(
        status       = "ok" if loaded else "degraded",
        modelLoaded  = loaded,
        modelVersion = get_model_version() if loaded else "not-loaded",
    )


@app.get(
    "/model",
    summary="Model metadata",
    description="Returns training metrics and the feature list used by the model.",
)
def model_info():
    if not is_model_loaded():
        raise HTTPException(status_code=503, detail="Model not loaded.")
    return {
        "modelVersion":  _payload.get("model_version"),
        "featureNames":  _payload.get("feature_names"),
        "trainingMetrics": _payload.get("metrics"),
    }
