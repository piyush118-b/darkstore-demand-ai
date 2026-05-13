"""
train.py
--------
XGBoost training script for the Dark Store Demand Forecasting ML microservice.

Upgrades the original notebook pipeline to:
- Add hourly time features (hour_of_day, rush_hour, lunch_hour)
- Add event_multiplier as a feature
- Add competitive price signal (price_diff)
- Properly encode all categoricals
- Serialize model + feature metadata for inference

Run:
    python -m app.model.train

Output:
    app/model/forecast_model.pkl
"""

import os
import sys
import joblib
import numpy as np
import pandas as pd
from pathlib import Path
from sklearn.model_selection import train_test_split
from sklearn.preprocessing import LabelEncoder
from sklearn.metrics import mean_absolute_error, mean_squared_error, r2_score
import xgboost as xgb

# ── Paths ────────────────────────────────────────────────────────────────────
ROOT      = Path(__file__).resolve().parents[3]   # project root
DATA_PATH = ROOT / "data" / "retail_store_inventory.csv"
MODEL_OUT = Path(__file__).parent / "forecast_model.pkl"

# Feature ordering must match hourly_aggregator.py exactly
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
    "price_diff",
    "is_rush_hour",
    "is_lunch_hour",
]

RUSH_HOURS  = {7, 8, 9, 17, 18, 19, 20}
LUNCH_HOURS = {12, 13, 14}

# ── Label maps (must match weather_encoder.py) ────────────────────────────────
WEATHER_MAP  = {"Sunny": 0, "Cloudy": 1, "Rainy": 2, "Snowy": 3}
SEASON_MAP   = {"Spring": 0, "Summer": 1, "Autumn": 2, "Winter": 3}
CATEGORY_MAP = {"Groceries": 0, "Electronics": 1, "Clothing": 2, "Toys": 3, "Furniture": 4}
REGION_MAP   = {"North": 0, "South": 1, "East": 2, "West": 3}


def load_and_engineer(path: Path) -> pd.DataFrame:
    """Load CSV and engineer all features."""
    print(f"Loading data from {path} ...")
    df = pd.read_csv(path)
    print(f"  Rows loaded: {len(df):,}")

    # Parse date
    df["Date"] = pd.to_datetime(df["Date"])

    # ── Drop leaky columns (known from EDA) ───────────────────────────────
    df = df.drop(columns=["Units Ordered", "Demand Forecast"], errors="ignore")
    df = df.dropna(subset=["Units Sold"])
    df = df[df["Units Sold"] >= 0]

    # ── Categorical encodings ─────────────────────────────────────────────
    df["category_enc"]  = df["Category"].map(CATEGORY_MAP).fillna(0).astype(int)
    df["region_enc"]    = df["Region"].map(REGION_MAP).fillna(0).astype(int)
    df["weather_enc"]   = df["Weather Condition"].map(WEATHER_MAP).fillna(0).astype(int)
    df["season_enc"]    = df["Seasonality"].map(SEASON_MAP).fillna(1).astype(int)

    # ── Temporal features ──────────────────────────────────────────────────
    # Simulate hourly granularity: assign a representative peak hour per row
    np.random.seed(42)
    peak_hours = np.random.choice([8, 13, 18, 22], size=len(df), p=[0.25, 0.30, 0.35, 0.10])
    df["hour_of_day"] = peak_hours
    df["day_of_week"] = df["Date"].dt.dayofweek
    df["is_weekend"]  = (df["day_of_week"] >= 5).astype(int)
    df["month"]       = df["Date"].dt.month

    # ── Derived features ───────────────────────────────────────────────────
    df["is_promotion"]   = df["Holiday/Promotion"].fillna(0).astype(int)
    df["price"]          = df["Price"].fillna(df["Price"].median())
    df["discount_pct"]   = df["Discount"].fillna(0).astype(int)
    df["competitor_price"] = df["Competitor Pricing"].fillna(df["Price"])
    df["inventory_level"]  = df["Inventory Level"].fillna(df["Inventory Level"].median())
    df["price_diff"]       = df["price"] - df["competitor_price"]

    # ── Event multiplier (static 1.0 for training; injected at inference) ─
    df["event_multiplier"] = 1.0

    # ── Rush / Lunch hour flags ────────────────────────────────────────────
    df["is_rush_hour"]  = df["hour_of_day"].isin(RUSH_HOURS).astype(int)
    df["is_lunch_hour"] = df["hour_of_day"].isin(LUNCH_HOURS).astype(int)

    return df


def train(df: pd.DataFrame):
    """Train XGBoost model and return (model, metrics)."""
    X = df[FEATURE_NAMES].values
    y = df["Units Sold"].values

    X_train, X_test, y_train, y_test = train_test_split(
        X, y, test_size=0.2, random_state=42)

    print(f"\nTraining XGBoost on {len(X_train):,} samples ...")
    model = xgb.XGBRegressor(
        n_estimators=300,
        max_depth=6,
        learning_rate=0.05,
        subsample=0.8,
        colsample_bytree=0.8,
        min_child_weight=5,
        reg_alpha=0.1,
        reg_lambda=1.0,
        random_state=42,
        n_jobs=-1,
        verbosity=0,
    )
    model.fit(
        X_train, y_train,
        eval_set=[(X_test, y_test)],
        verbose=50,
    )

    # ── Evaluate ───────────────────────────────────────────────────────────
    y_pred = model.predict(X_test)
    mae  = mean_absolute_error(y_test, y_pred)
    rmse = np.sqrt(mean_squared_error(y_test, y_pred))
    r2   = r2_score(y_test, y_pred)

    metrics = {"MAE": round(mae, 2), "RMSE": round(rmse, 2), "R2": round(r2, 4)}
    print(f"\n📊 Evaluation Results:")
    print(f"  MAE  : {metrics['MAE']}")
    print(f"  RMSE : {metrics['RMSE']}")
    print(f"  R²   : {metrics['R2']}")

    return model, metrics


def save_model(model, metrics: dict):
    """Serialize model and metadata."""
    payload = {
        "model":          model,
        "feature_names":  FEATURE_NAMES,
        "metrics":        metrics,
        "model_version":  "xgboost-1.0",
    }
    joblib.dump(payload, MODEL_OUT)
    print(f"\n✅ Model saved to {MODEL_OUT}")


if __name__ == "__main__":
    if not DATA_PATH.exists():
        print(f"ERROR: Data file not found: {DATA_PATH}")
        sys.exit(1)

    df      = load_and_engineer(DATA_PATH)
    model, metrics = train(df)
    save_model(model, metrics)
