# ============================================================
# Makefile for Expense Analyzer Backend
# Provides common commands for development and deployment
# ============================================================

.PHONY: help build run test clean docker-build docker-up docker-down

VERSION ?= latest

# Default target
help:
	@echo "Expense Analyzer Backend - Available Commands:"
	@echo ""
	@echo "  make build              - Build the backend JAR"
	@echo "  make run               - Run the backend locally"
	@echo "  make test              - Run tests"
	@echo "  make clean             - Clean build artifacts"
	@echo "  make docker-build      - Build Docker image"
	@echo "  make docker-up         - Start all services with Docker Compose"
	@echo "  make docker-down       - Stop all services"
	@echo "  make docker-logs       - View Docker logs"
	@echo "  make auth-test         - Test authentication endpoints"

# Build the backend JAR
build:
	@echo "Building Expense Analyzer Backend..."
	gradle clean bootJar --no-daemon

# Run the backend locally
run: build
	@echo "Starting Expense Analyzer Backend..."
	java -jar build/libs/expense-analyzer-0.0.1-SNAPSHOT.jar \
		--server.port=8080 \
		--spring.profiles.active=default

# Run tests
test:
	@echo "Running tests..."
	gradle test --no-daemon

# Clean build artifacts
clean:
	@echo "Cleaning build artifacts..."
	gradle clean --no-daemon
	rm -rf build/libs/*.jar

# Build Docker image
docker-build:
	@echo "Building Docker image..."
	docker build -t expense-analyzer-backend:${VERSION} ..

# Start all services with Docker Compose
docker-up:
	@echo "Starting all services with Docker Compose..."
	docker-compose up --build -d

# Stop all services
docker-down:
	@echo "Stopping all services..."
	docker-compose down

# View Docker logs
docker-logs:
	@echo "Viewing Docker logs..."
	docker-compose logs -f backend

# Test authentication endpoints
auth-test: build
	@echo "Testing Authentication Endpoints..."

	# Register a new user
	echo "Registering user..."
	REGISTRATION_RESPONSE=$$(curl -s -X POST http://localhost:8080/auth/register \
		-H "Content-Type: application/json" \
		-d '{
			"email": "test@example.com",
			"fullName": "Test User",
			"password": "SecurePass123!"
		}')
	echo "$$REGISTRATION_RESPONSE"

	# Login and get JWT token
	echo ""
	echo "Logging in..."
	LOGIN_RESPONSE=$$(curl -s -X POST http://localhost:8080/auth/login \
		-H "Content-Type: application/json" \
		-d '{
			"email": "test@example.com",
			"password": "SecurePass123!"
		}')
	echo "$$LOGIN_RESPONSE"

	# Extract JWT token
	JWT_TOKEN=$$(echo "$$LOGIN_RESPONSE" | jq -r '.data.token')

	if [ -n "$$JWT_TOKEN" ]; then
		echo ""
		echo "JWT Token obtained: $$JWT_TOKEN"

		# Test protected endpoint
		echo ""
		echo "Testing protected endpoint..."
		PROTECTED_RESPONSE=$$(curl -s http://localhost:8080/actuator/health \
			-H "Authorization: Bearer $$JWT_TOKEN")
		echo "$$PROTECTED_RESPONSE"

		if echo "$$PROTECTED_RESPONSE" | grep -q '"status":"UP"'; then
			echo ""
			echo "✅ Authentication test PASSED!"
		else
			echo ""
			echo "❌ Authentication test FAILED!"
		fi
	else
		echo ""
		echo "⚠️  Could not obtain JWT token"
	fi

# Build and run with Docker Compose (all services)
docker-compose-up:
	@echo "Starting all services with Docker Compose..."
	docker-compose up --build -d

# Build and run backend only with Docker Compose
docker-compose-backend-up:
	@echo "Starting backend service with Docker Compose..."
	docker-compose up --build backend
