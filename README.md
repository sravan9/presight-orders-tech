# Presight Orders Tech

A cloud-native **Order & Inventory Management System** built using **Spring Boot**, **Spring Cloud**, and **Kubernetes**. The project demonstrates how to design highly available, scalable, and resilient microservices while maintaining data consistency across distributed services using the **Saga Pattern** with compensating transactions.

## Architecture

The system consists of three core microservices:

* **API Gateway**

  * Single entry point for all client requests
  * Request routing using Spring Cloud Gateway

* **Order Service**

  * Create, retrieve, and update orders
  * Manage order lifecycle (`PENDING → CONFIRMED → CANCELLED / FAILED`)
  * Coordinates inventory reservation and restoration

* **Inventory Service**

  * Maintain product inventory
  * Atomic stock deduction and restoration
  * Configurable low-stock threshold
  * Idempotent inventory operations
  * Optimistic locking to prevent overselling

The detailed architecture, deployment topology, and design decisions are available in **architecture.md** included in this repository.

---

## Features

* Spring Boot 3 Microservices
* Spring Cloud Gateway
* REST-based inter-service communication
* Saga Pattern with compensating transactions
* Idempotent inventory operations
* Optimistic locking for concurrent inventory updates
* Dynamic configuration using Kubernetes ConfigMap
* Dockerized services
* Kubernetes deployment manifests
* Swagger/OpenAPI documentation
* Spring Boot Actuator health endpoints
* Structured logging
* Multi-module Maven project

---

## Technology Stack

| Layer            | Technology           |
| ---------------- | -------------------- |
| Language         | Java 17              |
| Framework        | Spring Boot 3        |
| API Gateway      | Spring Cloud Gateway |
| Build Tool       | Maven                |
| Database         | H2 (Development)     |
| Persistence      | Spring Data JPA      |
| Containerization | Docker               |
| Orchestration    | Kubernetes           |
| Documentation    | Swagger/OpenAPI      |
| Testing          | JUnit 5 & Mockito    |

---

# Project Structure

```text
presight-orders-tech
│
├── api-gateway/
├── order-service/
├── inventory-service/
├── common/
├── k8s/
│   ├── namespace.yaml
│   ├── configmap.yaml
│   ├── api-gateway.yaml
│   ├── order-service.yaml
│   └── inventory-service.yaml
│
├── docker-compose.yml
├── architecture.md
└── README.md
```

---

# System Architecture

Refer to:

https://github.com/sravan9/presight-orders-tech/blob/main/docs/architecture.md

The document contains:

* High-Level Architecture
* Service Communication Flow
* Saga Workflow
* Kubernetes Deployment Architecture
* Data Consistency Strategy
* Concurrency Design
* Idempotency Design

---

# Service Communication

```text
                  Client
                     │
                     ▼
          Spring Cloud Gateway
                     │
       ┌─────────────┴──────────────┐
       │                            │
       ▼                            ▼
Order Service              Inventory Service
       │                            │
       └────────────REST────────────┘
```

---

# Order Processing Flow

1. Client submits an order.
2. Order Service creates a **PENDING** order.
3. Inventory Service validates stock availability.
4. Inventory is deducted atomically.
5. Order status changes to **CONFIRMED**.
6. If any step fails, a compensating transaction restores inventory.

---

# Data Consistency

The project uses the **Saga Pattern** instead of distributed transactions (2PC).

### Order Creation

```
Create Order

↓

Reserve Inventory

↓

Success

↓

Confirm Order
```

### Failure

```
Inventory Reserved

↓

Order Save Failed

↓

Compensating Transaction

↓

Restore Inventory
```

Inventory operations are **idempotent** using the `orderId` as the idempotency key.

---

# High Availability

* Stateless microservices
* Horizontal scaling using Kubernetes replicas
* Kubernetes Services for load balancing
* Health checks using Spring Boot Actuator
* Retry mechanism using Spring Retry
* Configurable low-stock threshold through ConfigMap

---

# High Concurrency

The Inventory Service prevents overselling by combining:

* Optimistic Locking (`@Version`)
* Spring Retry
* Idempotent inventory mutations
* Transactional service methods

---

# Running the Project Locally

## Prerequisites

* Java 17+
* Maven 3.9+
* Docker Desktop
* Kubernetes (Minikube/Docker Desktop Kubernetes)
* kubectl

---

## Build

```bash
mvn clean package
```

---

## Run Using Maven

```bash
cd inventory-service
mvn spring-boot:run

cd ../order-service
mvn spring-boot:run

cd ../api-gateway
mvn spring-boot:run
```

---

## Run Using Docker Compose

Build the Docker images:

```bash
docker-compose build
```

Start all services:

```bash
docker-compose up
```

Stop all services:

```bash
docker-compose down
```

---

# Kubernetes Deployment

Apply the Kubernetes manifests in the following order:

```bash
kubectl apply -f k8s/namespace.yaml

kubectl apply -f k8s/configmap.yaml

kubectl apply -f k8s/inventory-service.yaml

kubectl apply -f k8s/order-service.yaml

kubectl apply -f k8s/api-gateway.yaml
```

Verify the deployments:

```bash
kubectl get pods -n presight

kubectl get svc -n presight
```

View logs:

```bash
kubectl logs deployment/order-service -n presight

kubectl logs deployment/inventory-service -n presight

kubectl logs deployment/api-gateway -n presight
```

Delete the deployment:

```bash
kubectl delete namespace presight
```

---

# API Endpoints

## Order Service

| Method | Endpoint                  | Description         |
| ------ | ------------------------- | ------------------- |
| POST   | `/api/orders`             | Create Order        |
| GET    | `/api/orders/{id}`        | Get Order           |
| PUT    | `/api/orders/{id}/status` | Update Order Status |

---

## Inventory Service

| Method | Endpoint                          | Description                |
| ------ | --------------------------------- | -------------------------- |
| GET    | `/api/inventory/{productCode}`    | Get Inventory              |
| POST   | `/api/inventory/deduct`           | Deduct Inventory           |
| POST   | `/api/inventory/restore`          | Restore Inventory          |
| GET    | `/api/inventory/config/threshold` | Get Low Stock Threshold    |
| PUT    | `/api/inventory/config/threshold` | Update Low Stock Threshold |

---

# Swagger

Each service exposes Swagger UI.

| Service           | URL                                     |
| ----------------- | --------------------------------------- |
| API Gateway       | `http://localhost:8080`                 |
| Order Service     | `http://localhost:8081/swagger-ui.html` |
| Inventory Service | `http://localhost:8082/swagger-ui.html` |

---

# Monitoring

Health endpoint:

```
/actuator/health
```

---

# Repository Structure

| Module              | Description                            |
| ------------------- | -------------------------------------- |
| `common`            | Shared DTOs, constants, and exceptions |
| `api-gateway`       | API Gateway and request routing        |
| `order-service`     | Order lifecycle management             |
| `inventory-service` | Inventory management                   |
| `k8s`               | Kubernetes deployment manifests        |

---

# Future Improvements

* Kafka-based event-driven Saga orchestration
* PostgreSQL support
* Distributed tracing (OpenTelemetry)
* Prometheus & Grafana monitoring
* Horizontal Pod Autoscaler (HPA)
* Authentication & Authorization using OAuth2/JWT
* CI/CD with GitHub Actions

---

# Author

**Sravan Kumar Sajja**

Senior Software Engineer
