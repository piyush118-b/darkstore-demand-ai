-- ============================================================
-- V1: Dark Store Demand Forecasting Schema
-- ============================================================

-- Dark stores (each is a quick-commerce fulfilment hub)
CREATE TABLE dark_stores (
    id          VARCHAR(10)  PRIMARY KEY,          -- e.g. S001
    name        VARCHAR(100) NOT NULL,
    region      VARCHAR(50)  NOT NULL,              -- North, South, East, West
    latitude    DECIMAL(9,6),
    longitude   DECIMAL(9,6),
    is_active   BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMP    NOT NULL DEFAULT NOW()
);

-- Products stocked at dark stores
CREATE TABLE products (
    id                 VARCHAR(10)  PRIMARY KEY,    -- e.g. P0001
    name               VARCHAR(100) NOT NULL,
    category           VARCHAR(50)  NOT NULL,       -- Groceries, Electronics, Clothing, etc.
    unit_price         DECIMAL(10,2),
    reorder_threshold  INT          NOT NULL DEFAULT 50,   -- trigger reorder when stock ≤ this
    reorder_quantity   INT          NOT NULL DEFAULT 200,  -- units to reorder
    is_active          BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at         TIMESTAMP    NOT NULL DEFAULT NOW()
);

-- Hourly inventory snapshots (core fact table)
-- Daily CSV data is transformed into simulated hourly records
CREATE TABLE inventory_snapshots (
    id                  BIGSERIAL    PRIMARY KEY,
    store_id            VARCHAR(10)  NOT NULL REFERENCES dark_stores(id),
    product_id          VARCHAR(10)  NOT NULL REFERENCES products(id),
    snapshot_time       TIMESTAMP    NOT NULL,       -- hourly granularity
    inventory_level     INT          NOT NULL,
    units_sold          INT          NOT NULL DEFAULT 0,
    price               DECIMAL(10,2),
    discount_pct        INT          DEFAULT 0,
    weather_condition   VARCHAR(20),                 -- Sunny, Rainy, Cloudy, Snowy
    is_promotion        BOOLEAN      NOT NULL DEFAULT FALSE,
    competitor_price    DECIMAL(10,2),
    seasonality         VARCHAR(20),                 -- Spring, Summer, Autumn, Winter
    hour_of_day         INT          CHECK (hour_of_day BETWEEN 0 AND 23),
    day_of_week         INT          CHECK (day_of_week BETWEEN 0 AND 6),
    is_weekend          BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at          TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_snapshot_store_time   ON inventory_snapshots(store_id, snapshot_time DESC);
CREATE INDEX idx_snapshot_product_time ON inventory_snapshots(product_id, snapshot_time DESC);
CREATE INDEX idx_snapshot_time         ON inventory_snapshots(snapshot_time DESC);

-- ML demand forecasts (one row per product-store-hour prediction)
CREATE TABLE demand_forecasts (
    id               BIGSERIAL    PRIMARY KEY,
    store_id         VARCHAR(10)  NOT NULL REFERENCES dark_stores(id),
    product_id       VARCHAR(10)  NOT NULL REFERENCES products(id),
    forecast_time    TIMESTAMP    NOT NULL,          -- the hour being predicted
    predicted_units  INT          NOT NULL,
    confidence_score DECIMAL(5,4) CHECK (confidence_score BETWEEN 0 AND 1),
    model_version    VARCHAR(20)  NOT NULL DEFAULT '1.0',
    -- SHAP top-3 feature explanations (stored as JSON)
    shap_explanation JSONB,
    created_at       TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_forecast_store_time   ON demand_forecasts(store_id, forecast_time DESC);
CREATE INDEX idx_forecast_product_time ON demand_forecasts(product_id, forecast_time DESC);

-- Reorder events triggered by the reorder engine
CREATE TABLE reorder_events (
    id                 BIGSERIAL    PRIMARY KEY,
    store_id           VARCHAR(10)  NOT NULL REFERENCES dark_stores(id),
    product_id         VARCHAR(10)  NOT NULL REFERENCES products(id),
    triggered_at       TIMESTAMP    NOT NULL DEFAULT NOW(),
    trigger_reason     VARCHAR(50)  NOT NULL,        -- STOCKOUT_RISK | LOW_STOCK | SCHEDULED | MANUAL
    current_stock      INT          NOT NULL,
    forecasted_demand  INT,
    reorder_quantity   INT          NOT NULL,
    status             VARCHAR(20)  NOT NULL DEFAULT 'PENDING',  -- PENDING | APPROVED | DISPATCHED | CANCELLED
    approved_at        TIMESTAMP,
    dispatched_at      TIMESTAMP,
    notes              TEXT,
    created_at         TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_reorder_store      ON reorder_events(store_id, triggered_at DESC);
CREATE INDEX idx_reorder_status     ON reorder_events(status);

-- Local events that amplify demand (concerts, holidays, sports, etc.)
CREATE TABLE local_events (
    id                 BIGSERIAL    PRIMARY KEY,
    event_name         VARCHAR(100) NOT NULL,
    region             VARCHAR(50)  NOT NULL,        -- matches dark_stores.region
    event_date         DATE         NOT NULL,
    event_type         VARCHAR(50)  NOT NULL,        -- HOLIDAY | SALE | SPORTS | CONCERT | FESTIVAL
    demand_multiplier  DECIMAL(4,2) NOT NULL DEFAULT 1.0,  -- e.g. 1.3 = 30% demand spike
    affected_categories VARCHAR(200),               -- comma-separated, NULL = all categories
    created_at         TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_event_region_date ON local_events(region, event_date);
