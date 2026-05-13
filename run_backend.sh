#!/bin/bash

# Simple script to help run the backend
# Usage: ./run_backend.sh

PROJECT_ROOT="/Users/piyush_18/Desktop/demand_forcasting"
BACKEND_DIR="$PROJECT_ROOT/backend"

echo "🚀 Starting Dark Store Backend Setup..."

# Force Java 17 (Lombok compatibility)
if /usr/libexec/java_home -v 17 &>/dev/null; then
    export JAVA_HOME=$(/usr/libexec/java_home -v 17)
    echo "☕ Using Java 17: $JAVA_HOME"
else
    echo "⚠️  Java 17 not found. This project may fail with newer Java versions due to Lombok."
fi

# Check for Maven
if ! command -v mvn &> /dev/null; then
    echo "❌ Maven not found. Attempting to install via Homebrew..."
    brew install maven
fi

# Check for PostgreSQL
if ! command -v psql &> /dev/null; then
    echo "⚠️  psql not found. You might need to install postgresql: 'brew install postgresql@15'"
fi

cd "$BACKEND_DIR" || exit

echo "📦 Building and Running Backend..."
mvn clean spring-boot:run
