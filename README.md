# 🏪 DarkStore Demand AI

> **Real-time demand forecasting and auto-reorder system for quick-commerce dark stores.**  
> Predicts short-term inventory demand within 10–20 minute delivery windows using XGBoost ML, SHAP explainability, and automated reorder triggers.

![Java](https://img.shields.io/badge/Java_17-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring_Boot_3.2-6DB33F?style=for-the-badge&logo=spring&logoColor=white)
![Python](https://img.shields.io/badge/Python_3.13-3776AB?style=for-the-badge&logo=python&logoColor=white)
![FastAPI](https://img.shields.io/badge/FastAPI-009688?style=for-the-badge&logo=fastapi&logoColor=white)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-316192?style=for-the-badge&logo=postgresql&logoColor=white)
![XGBoost](https://img.shields.io/badge/XGBoost-FF6600?style=for-the-badge&logo=xgboost&logoColor=white)

---

## 🎯 Problem Statement

Quick-commerce dark stores promise 10–20 minute delivery. When a product goes out of stock, that promise breaks. Traditional daily restocking cycles are too slow. This system solves that by:

- **Predicting hourly demand** using historical sales, weather, and local events
- **Automatically triggering reorders** when predicted demand exceeds current stock
- **Explaining every prediction** using SHAP values so managers can trust and audit AI decisions

---

## 🏗️ Architecture

```
┌─────────────────────────────────────────────────────────┐
│                   Client / Swagger UI                   │
│                 http://localhost:8080                   │
└────────────────────────┬────────────────────────────────┘
                         │ REST
┌────────────────────────▼────────────────────────────────┐
│              Java Spring Boot Backend (:8080)           │
│                                                         │
│  ┌─────────────────┐  ┌──────────────┐  ┌───────────┐  │
│  │DataIngestionSvc │  │ForecastingSvc│  │ReorderSvc │  │
│  │CSV → DB (hourly)│  │ML orchestrate│  │Auto-trigger│  │
│  └─────────────────┘  └──────┬───────┘  └───────────┘  │
│                              │ POST /predict             │
│  ┌─────────────────────────┐ │                          │
│  │    PostgreSQL (Flyway)  │ │                          │
│  │  dark_stores, products  │ │                          │
│  │  inventory_snapshots    │ │                          │
│  │  demand_forecasts (JSONB│ │                          │
│  │  reorder_events         │ │                          │
│  └─────────────────────────┘ │                          │
└──────────────────────────────┼──────────────────────────┘
                               │
┌──────────────────────────────▼──────────────────────────┐
│           Python FastAPI ML Microservice (:8000)        │
│                                                         │
│  ┌──────────────────────────────────────────────────┐   │
│  │ XGBoost Model                                    │   │
│  │  Features: hour_of_day, rush_hour, price_diff,   │   │
│  │            event_multiplier, weather, seasonality │   │
│  └──────────────────────────────────────────────────┘   │
│  ┌──────────────────────────────────────────────────┐   │
│  │ SHAP TreeExplainer → top-5 feature contributions │   │
│  └──────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────┘
```

---

## ✨ Key Features

| Feature | Description |
|---|---|
| **Hourly Demand Simulation** | Converts daily CSV data into hourly snapshots using a peak-demand distribution curve |
| **XGBoost Forecasting** | Predicts next-hour unit demand per product per store |
| **SHAP Explainability** | Every prediction comes with a `shap_explanation` JSON showing top-5 contributing factors |
| **Auto Reorder Engine** | Runs every 30 minutes, scans all 5 stores × all products, raises reorder events automatically |
| **Duplicate Suppression** | 2-hour deduplication window prevents double-ordering the same product |
| **Graceful ML Fallback** | If the Python service is down, Java falls back to a rule-based heuristic — system never crashes |
| **Local Event Awareness** | Demand multipliers for festivals (Diwali ×1.8), flash sales (Big Billion Day ×2.0), sports events |

---

## 🛠️ Tech Stack

| Layer | Technology |
|---|---|
| Backend API | Java 17, Spring Boot 3.2, Spring Data JPA |
| Database | PostgreSQL 15, Flyway (schema migration) |
| ML Microservice | Python 3.13, FastAPI, XGBoost 3.x |
| Explainability | SHAP (TreeExplainer) |
| Data Processing | Pandas, NumPy, scikit-learn |
| API Docs | SpringDoc OpenAPI (Swagger UI) |
| CSV Parsing | OpenCSV |

---

## 📂 Project Structure

```
darkstore-demand-ai/
├── backend/                          # Java Spring Boot
│   ├── src/main/java/com/darkstore/
│   │   ├── model/                    # JPA Entities
│   │   ├── repository/               # Spring Data repositories
│   │   ├── service/                  # Business logic
│   │   │   ├── DataIngestionService  # CSV → hourly snapshots
│   │   │   ├── ForecastingService    # Calls ML + persists forecasts
│   │   │   ├── ReorderEngineService  # Auto-reorder logic
│   │   │   └── DashboardService      # KPI aggregation
│   │   ├── controller/               # REST endpoints
│   │   └── scheduler/                # 30-min reorder sweep
│   └── src/main/resources/
│       ├── db/migration/             # Flyway V1, V2 SQL scripts
│       └── application.yml
│
├── ml-service/                       # Python FastAPI
│   ├── app/
│   │   ├── main.py                   # FastAPI app
│   │   └── model/
│   │       ├── train.py              # XGBoost training pipeline
│   │       └── predict.py            # Inference + SHAP
│   ├── tests/                        # 11 pytest unit tests
│   └── requirements.txt
│
└── data/
    └── retail_store_inventory.csv    # 73,100 rows of retail data
```

---

## 🚀 Local Setup

### Prerequisites
- Java 17
- Maven 3.9+
- Python 3.11+
- PostgreSQL 15

### 1. Clone the repo
```bash
git clone https://github.com/piyush118-b/darkstore-demand-ai.git
cd darkstore-demand-ai
```

### 2. Setup the Database
```bash
# Start PostgreSQL and create the database
brew services start postgresql@15
psql -U postgres -f setup_db.sql
```

### 3. Train the ML Model & Start the Python service
```bash
cd ml-service
python -m venv venv && source venv/bin/activate
pip install -r requirements.txt
python -m app.model.train          # Trains XGBoost, saves forecast_model.pkl
uvicorn app.main:app --port 8000   # Start the microservice
```

### 4. Start the Java Backend
```bash
cd backend
export JAVA_HOME=$(/usr/libexec/java_home -v 17)
mvn spring-boot:run
```

### 5. Explore the API
Open **http://localhost:8080/swagger-ui.html** in your browser.

---

## 📊 Sample API Responses

### Generate Forecasts for a Store
```bash
curl -X POST http://localhost:8080/api/forecast/store/S001
```
```json
{
  "storeId": "S001",
  "productId": "P0001",
  "productName": "Premium Basmati Rice 5kg",
  "predictedUnits": 108,
  "riskLevel": "WARNING",
  "shapExplanation": {
    "inventory_level": -38.22,
    "price_diff": 4.31,
    "competitor_price": 3.11,
    "day_of_week": 1.09
  }
}
```

### Trigger Auto-Reorder Sweep
```bash
curl -X POST http://localhost:8080/api/reorders/sweep
```
```json
{
  "message": "Reorder sweep complete",
  "newReordersCreated": 3,
  "reorders": [
    {
      "storeId": "S001",
      "productName": "Instant Noodles Pack x5",
      "currentStock": 50,
      "triggerReason": "LOW_STOCK",
      "reorderQuantity": 400,
      "status": "PENDING"
    }
  ]
}
```

---

## 🧪 Running Tests

```bash
cd ml-service
source venv/bin/activate
python -m pytest tests/ -v   # 11/11 tests pass
```

---

## 💡 How the Reorder Engine Works

```
Every 30 minutes:
  For each active store × each product:
    IF currentStock ≤ reorderThreshold   → LOW_STOCK reorder
    IF predictedDemand > currentStock×0.8 → STOCKOUT_RISK reorder
    IF same reorder exists within 2hrs   → Skip (deduplication)
```

---

## 🗄️ Database Schema

| Table | Purpose |
|---|---|
| `dark_stores` | 5 regional micro-fulfilment hubs |
| `products` | 21 products across 5 categories |
| `inventory_snapshots` | 20,000+ hourly stock records (simulated from CSV) |
| `demand_forecasts` | ML predictions with SHAP JSON explanations |
| `reorder_events` | Auto-triggered + manual reorder workflow |
| `local_events` | Regional demand multipliers (festivals, sports, sales) |

---

## 📈 Model Performance

| Metric | Value |
|---|---|
| Training samples | 58,480 |
| MAE | 69.16 units/hour |
| RMSE | 88.63 units/hour |
| R² | 0.33 |

> *Note: R² is lower than daily models because hourly demand is inherently noisier. The model is optimized for directional accuracy (will demand be high or low?) rather than exact unit counts.*

---

## 👤 Author

**Piyush** — [GitHub](https://github.com/piyush118-b)