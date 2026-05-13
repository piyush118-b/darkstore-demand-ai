-- Run this script to setup the database for the Dark Store Demand Forecasting system
-- Command: psql -U postgres -f setup_db.sql

-- Drop and recreate the database
DROP DATABASE IF EXISTS darkstore_db;
CREATE DATABASE darkstore_db;

-- Connect to the new database
\c darkstore_db

-- Grant privileges
GRANT ALL PRIVILEGES ON DATABASE darkstore_db TO postgres;

-- The schema and seed data will be automatically created by Flyway
-- when the Spring Boot application starts.
