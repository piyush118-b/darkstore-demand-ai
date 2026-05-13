from pydantic import BaseModel, Field
from typing import Optional, Dict


class ForecastRequest(BaseModel):
    """
    Feature vector sent by the Java Spring Boot backend.
    All fields mirror ForecastRequestDto in the Java service.
    """
    storeId: str
    productId: str
    category: str
    region: str

    # Temporal features
    hourOfDay: int = Field(ge=0, le=23)
    dayOfWeek: int = Field(ge=0, le=6)   # 0=Monday, 6=Sunday
    isWeekend: bool
    month: int = Field(ge=1, le=12)

    # Price signals
    price: Optional[float] = None
    discountPct: Optional[int] = Field(default=0, ge=0, le=100)
    competitorPrice: Optional[float] = None

    # Context signals
    weatherCondition: Optional[str] = "Sunny"   # Sunny | Rainy | Cloudy | Snowy
    isPromotion: Optional[bool] = False
    seasonality: Optional[str] = "Summer"        # Spring | Summer | Autumn | Winter

    # Inventory context
    currentInventoryLevel: Optional[int] = 0

    # Local event demand multiplier
    eventMultiplier: Optional[float] = Field(default=1.0, ge=0.5, le=5.0)


class ForecastResponse(BaseModel):
    """
    Prediction result returned to the Java backend.
    Includes SHAP feature contributions for explainability.
    """
    storeId: str
    productId: str
    forecastTime: str                          # ISO-8601 datetime string
    predictedUnits: int
    confidenceScore: float = Field(ge=0.0, le=1.0)
    modelVersion: str = "xgboost-1.0"

    # SHAP feature contributions (top features, sorted by abs value)
    # Positive = increases predicted demand; negative = decreases it
    shapExplanation: Dict[str, float] = {}


class HealthResponse(BaseModel):
    status: str
    modelLoaded: bool
    modelVersion: str
