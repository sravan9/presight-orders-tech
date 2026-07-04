# Presight Microservices - Architecture & Deployment

**Author:** Sravan Kumar Sajja  
**Email:** sravankumar.rom@gmail.com  
**Phone:** +91 7204663372

---

## Functional Requirements

| ID | Requirement | Description |
|----|-------------|-------------|
| FR-1 | **Order Placement** | Users can create orders specifying a product code and quantity. System validates stock availability before confirming. |
| FR-2 | **Order Cancellation** | Confirmed orders can be cancelled. Cancellation restores previously deducted stock. |
| FR-3 | **Inventory Deduction** | Stock is atomically deducted upon order confirmation. Insufficient stock results in order failure. |
| FR-4 | **Inventory Restoration** | Stock is restored on order cancellation or failed confirmation (compensating transaction). |
| FR-5 | **Low-Stock Alerting** | System flags products whose stock drops below a configurable threshold. |
| FR-6 | **Dynamic Threshold Config** | Low-stock threshold can be updated at runtime without redeployment (via REST or K8s ConfigMap). |
| FR-7 | **Idempotent Operations** | Deduction and restoration calls are idempotent per order — retries or duplicate calls do not cause double stock mutations. |
| FR-8 | **Order Status Tracking** | Orders transition through `PENDING → CONFIRMED / FAILED → CANCELLED` with full lifecycle visibility. |

---

## Non-Functional Requirements (NFRs)

| ID | Category | Requirement |
|----|----------|-------------|
| NFR-1 | **Consistency** | Saga pattern with compensating transactions ensures eventual consistency across services. No distributed transactions (2PC) required. |
| NFR-2 | **Idempotency** | All inventory mutations are guarded by `orderId`-based deduplication in a `stock_reservations` table. |
| NFR-3 | **Concurrency** | Optimistic locking (`@Version`) on Product entity prevents overselling under concurrent requests. Retries on `ObjectOptimisticLockingFailureException`. |
| NFR-4 | **Scalability** | Stateless services deployable as multiple K8s replicas. No shared in-process state. |
| NFR-5 | **Resilience** | Spring Retry (exponential backoff) for transient failures. Compensation on non-recoverable failures. |
| NFR-6 | **Observability** | Actuator health/info endpoints exposed. Structured logging with SLF4J. Low-stock warnings logged at WARN level. |
| NFR-7 | **Containerization** | Multi-stage Docker builds. Docker Compose for local orchestration with health checks and dependency ordering. |
| NFR-8 | **Deployability** | Full Kubernetes manifests (Namespace, ConfigMap, Deployments, Services) with liveness/readiness probes. |
| NFR-9 | **Testability** | Unit tests with MockBean isolation. All critical flows covered (success, failure, compensation, concurrency). |

---

## Technology Stack

| Layer | Technology |
|-------|-----------|
| Language | Java 17 |
| Framework | Spring Boot 3.2.5, Spring Cloud 2023.0.1 |
| API Gateway | Spring Cloud Gateway (Reactive/Netty) |
| Persistence | Spring Data JPA, H2 (dev) |
| Resilience | Spring Retry (exponential backoff) |
| API Docs | springdoc-openapi (Swagger UI) |
| Build | Maven 3.9, multi-module |
| Containerization | Docker (multi-stage), Docker Compose |
| Orchestration | Kubernetes (Deployments, Services, ConfigMaps) |
| Testing | JUnit 5, Mockito, SpringBootTest |

---

## System Architecture

```mermaid
graph TB
    Client[Clients]
    LB[LoadBalancer Service :80]
    GW[API Gateway<br/>Spring Cloud Gateway<br/>Port 8080<br/>2 replicas]
    OS[Order Service<br/>Port 8081<br/>2 replicas]
    IS[Inventory Service<br/>Port 8082<br/>2 replicas]
    CM[ConfigMap<br/>low-stock-threshold=10]
    ODB[(Order DB<br/>H2 In-Memory)]
    IDB[(Inventory DB<br/>H2 In-Memory)]

    Client --> LB
    LB --> GW
    GW -->|/api/orders/**| OS
    GW -->|/api/inventory/**| IS
    OS -->|REST: deduct/restore| IS
    CM -.->|config injection| IS
    OS --- ODB
    IS --- IDB

    subgraph Kubernetes Cluster
        GW
        OS
        IS
        CM
        ODB
        IDB
        LB
    end
```

---

## Service Communication Flow

```mermaid
sequenceDiagram
    participant C as Client
    participant GW as API Gateway :8080
    participant OS as Order Service :8081
    participant IS as Inventory Service :8082

    C->>GW: POST /api/orders
    GW->>OS: Route request
    OS->>OS: Save order (PENDING)
    OS->>IS: POST /api/inventory/deduct
    IS->>IS: Validate stock & deduct atomically
    IS-->>OS: 200 OK (success)
    OS->>OS: Update order → CONFIRMED
    OS-->>GW: 201 Created
    GW-->>C: Order Response

    Note over C,IS: Direct inventory access also routed via Gateway
    C->>GW: GET /api/inventory/{productCode}
    GW->>IS: Route request
    IS-->>GW: Inventory details
    GW-->>C: Response
```

## Data Consistency Strategy

### Saga Pattern (Choreography-based)

---

### Flow 1: Order Creation — Happy Path

```mermaid
sequenceDiagram
    participant C as Client
    participant OS as Order Service
    participant ODB as Order DB
    participant IS as Inventory Service
    participant IDB as Inventory DB

    C->>OS: POST /api/orders {productCode, quantity}
    OS->>ODB: INSERT order (status=PENDING)
    ODB-->>OS: orderId generated
    OS->>IS: POST /api/inventory/deduct {productCode, quantity, orderId}
    IS->>IDB: Check stock_reservations (orderId, DEDUCT)
    Note over IS,IDB: No existing reservation → proceed
    IS->>IDB: Deduct stock (optimistic lock check)
    IS->>IDB: INSERT stock_reservations (orderId, DEDUCT)
    IS-->>OS: 200 OK
    OS->>ODB: UPDATE order SET status=CONFIRMED
    OS-->>C: 201 Created (status=CONFIRMED)
```

---

### Flow 2: Order Creation — Insufficient Stock (No Compensation Needed)

```mermaid
sequenceDiagram
    participant C as Client
    participant OS as Order Service
    participant ODB as Order DB
    participant IS as Inventory Service

    C->>OS: POST /api/orders {productCode, quantity}
    OS->>ODB: INSERT order (status=PENDING)
    ODB-->>OS: orderId generated
    OS->>IS: POST /api/inventory/deduct {productCode, quantity, orderId}
    IS-->>OS: 409 Conflict (insufficient stock)
    OS->>ODB: UPDATE order SET status=FAILED
    OS-->>C: 409 Conflict (INVENTORY_DEDUCTION_FAILED)
    Note over OS: No compensation needed — stock was never deducted
```

---

### Flow 3: Order Creation — Confirmation Save Fails (Compensation Triggered)

```mermaid
sequenceDiagram
    participant C as Client
    participant OS as Order Service
    participant ODB as Order DB
    participant IS as Inventory Service

    C->>OS: POST /api/orders {productCode, quantity}
    OS->>ODB: INSERT order (status=PENDING)
    ODB-->>OS: orderId generated
    OS->>IS: POST /api/inventory/deduct {productCode, quantity, orderId}
    IS-->>OS: 200 OK (stock deducted)
    OS->>ODB: UPDATE order SET status=CONFIRMED
    ODB--xOS: ❌ DB write fails (timeout, constraint, etc.)
    
    rect rgb(255, 230, 230)
        Note over OS,IS: COMPENSATING TRANSACTION
        OS->>IS: POST /api/inventory/restore {productCode, quantity, orderId}
        IS-->>OS: 200 OK (stock restored)
    end
    
    OS->>ODB: UPDATE order SET status=FAILED
    OS-->>C: 500 Internal Server Error
```

---

### Flow 4: Double Failure — Compensation Also Fails (Requires Reconciliation)

```mermaid
sequenceDiagram
    participant C as Client
    participant OS as Order Service
    participant ODB as Order DB
    participant IS as Inventory Service

    C->>OS: POST /api/orders {productCode, quantity}
    OS->>ODB: INSERT order (status=PENDING)
    ODB-->>OS: orderId generated
    OS->>IS: POST /api/inventory/deduct {productCode, quantity, orderId}
    IS-->>OS: 200 OK (stock deducted)
    OS->>ODB: UPDATE order SET status=CONFIRMED
    ODB--xOS: ❌ DB write fails

    rect rgb(255, 200, 200)
        Note over OS,IS: COMPENSATION ATTEMPT
        OS->>IS: POST /api/inventory/restore {productCode, quantity, orderId}
        IS--xOS: ❌ Network timeout / Service down
    end

    Note over OS: CRITICAL: Stock deducted but order not confirmed
    Note over OS: Logged as CRITICAL for reconciliation worker
    OS->>ODB: UPDATE order SET status=FAILED
    OS-->>C: 500 Internal Server Error

    rect rgb(255, 255, 200)
        Note over OS,IS: BACKGROUND RECONCILIATION (async)
        Note over OS: Sweeper queries PENDING/FAILED orders > 5 min old
        Note over OS: Checks stock_reservations for orphaned DEDUCTs
        OS->>IS: POST /api/inventory/restore {orderId} (retry)
        IS-->>OS: 200 OK (idempotent — safe to retry)
    end
```

---

### Flow 5: Order Cancellation — Happy Path

```mermaid
sequenceDiagram
    participant C as Client
    participant OS as Order Service
    participant ODB as Order DB
    participant IS as Inventory Service
    participant IDB as Inventory DB

    C->>OS: PUT /api/orders/{id}/cancel
    OS->>ODB: SELECT order WHERE id={id}
    ODB-->>OS: order (status=CONFIRMED)
    OS->>IS: POST /api/inventory/restore {productCode, quantity, orderId}
    IS->>IDB: Check stock_reservations (orderId, RESTORE)
    Note over IS,IDB: No existing restore → proceed
    IS->>IDB: Restore stock (optimistic lock)
    IS->>IDB: INSERT stock_reservations (orderId, RESTORE)
    IS-->>OS: 200 OK
    OS->>ODB: UPDATE order SET status=CANCELLED
    OS-->>C: 200 OK (status=CANCELLED)
```

---

### Flow 6: Order Cancellation — Restore Fails (Order Stays Consistent)

```mermaid
sequenceDiagram
    participant C as Client
    participant OS as Order Service
    participant ODB as Order DB
    participant IS as Inventory Service

    C->>OS: PUT /api/orders/{id}/cancel
    OS->>ODB: SELECT order WHERE id={id}
    ODB-->>OS: order (status=CONFIRMED)
    OS->>IS: POST /api/inventory/restore {productCode, quantity, orderId}
    IS--xOS: ❌ Service unavailable / timeout

    rect rgb(230, 255, 230)
        Note over OS: Order stays CONFIRMED (consistent state)
        Note over OS: Stock stays deducted (no partial mutation)
        Note over OS: Client can safely retry cancellation later
    end

    OS-->>C: 409 Conflict (INVENTORY_RESTORE_FAILED)
```

---

### Flow 7: Idempotent Retry — Duplicate Deduction Safely Skipped

```mermaid
sequenceDiagram
    participant OS as Order Service
    participant IS as Inventory Service
    participant IDB as Inventory DB

    Note over OS: Network timeout on first attempt (response lost)
    OS->>IS: POST /api/inventory/deduct {productCode, qty, orderId=42}
    IS->>IDB: Check stock_reservations WHERE orderId=42 AND type=DEDUCT
    IDB-->>IS: ✅ Record exists (already processed)
    Note over IS: Idempotency check passes — skip deduction
    IS-->>OS: 200 OK (treated as success)
    Note over OS: Safe — no double deduction occurred
```

---

### Flow 8: Concurrent Deductions — Optimistic Locking

```mermaid
sequenceDiagram
    participant R1 as Request 1
    participant R2 as Request 2
    participant IS as Inventory Service
    participant DB as Product Table

    R1->>IS: deduct(PROD-001, qty=5, orderId=10)
    R2->>IS: deduct(PROD-001, qty=3, orderId=11)
    IS->>DB: SELECT * FROM product WHERE code='PROD-001' (version=1)
    IS->>DB: SELECT * FROM product WHERE code='PROD-001' (version=1)
    IS->>DB: UPDATE product SET stock=95, version=2 WHERE version=1
    DB-->>IS: ✅ Success (R1 wins)
    IS->>DB: UPDATE product SET stock=97, version=2 WHERE version=1
    DB--xIS: ❌ OptimisticLockException (R2 stale version)

    rect rgb(230, 240, 255)
        Note over R2,IS: SPRING RETRY (exponential backoff)
        IS->>DB: SELECT * FROM product WHERE code='PROD-001' (version=2)
        IS->>DB: UPDATE product SET stock=90, version=3 WHERE version=2
        DB-->>IS: ✅ Success (R2 retried)
    end

    IS-->>R1: 200 OK
    IS-->>R2: 200 OK
```

---

### Order State Machine

```mermaid
stateDiagram-v2
    [*] --> PENDING: Order created
    PENDING --> CONFIRMED: Inventory deducted successfully
    PENDING --> FAILED: Inventory deduction failed
    CONFIRMED --> CANCELLED: Cancel + inventory restored
    FAILED --> [*]: Terminal state
    CANCELLED --> [*]: Terminal state

    note right of PENDING: orderId assigned<br/>Idempotency key established
    note right of CONFIRMED: Stock deducted<br/>Reservation recorded
    note left of FAILED: No stock deducted<br/>OR compensation completed
    note right of CANCELLED: Stock restored<br/>Restore reservation recorded
```

---

### Idempotency Mechanism

```mermaid
erDiagram
    STOCK_RESERVATIONS {
        Long id PK
        Long order_id
        String product_code
        Int quantity
        String operation_type "DEDUCT or RESTORE"
    }

    PRODUCT {
        Long id PK
        String product_code UK
        String name
        Int available_quantity
        Int version "Optimistic lock"
    }

    STOCK_RESERVATIONS }o--|| PRODUCT : references
```

**Unique constraint:** `(order_id, operation_type)`
- Duplicate deduct/restore for the same orderId is silently skipped
- Ensures at-most-once semantics for each operation per order

---

> ### ⚠️ NOTE: Background Reconciliation Job (Not Implemented)
>
> **Scenario:** A "double failure" can leave the system in an inconsistent state:
> - Inventory stock is deducted (DEDUCT recorded in `stock_reservations`)
> - Order confirmation DB write fails
> - Compensating restore call ALSO fails (network timeout, inventory service down)
>
> **Result:** Stock is deducted but the order is stuck in `PENDING` or `FAILED` — money/stock leak.
>
> **Resolution:** A background **Saga Sweeper / Reconciliation Worker** should:
> 1. Periodically query orders in `PENDING` status older than a threshold (e.g., 5 minutes)
> 2. For each, check `stock_reservations` in Inventory Service:
>    - If `DEDUCT` exists but no `RESTORE` → stock is reserved but order not confirmed
>      - **Action:** Either confirm the order (if business allows) OR call restore with the orderId
>    - If neither `DEDUCT` nor `RESTORE` exists → no stock was taken
>      - **Action:** Mark order as `FAILED`
> 3. Same pattern applies to stuck cancellations: if order is `CONFIRMED` but a cancel was requested and restore failed, the sweeper retries the restore.
>
> **This is the standard Saga pattern resolution for distributed dual-write problems.** The idempotency keys (orderId) make all retry operations safe.

## High Concurrency Design

- **Optimistic Locking**: `@Version` on Product entity prevents concurrent overselling. Two concurrent deductions for the same product result in one success and one automatic retry (up to 3 attempts with exponential backoff).
- **Idempotency Keys**: `orderId` in `stock_reservations` prevents duplicate mutations even under network retries or at-least-once delivery.
- **Transaction Isolation**: `@Transactional` on service methods with DB-level row versioning.
- **Stateless Services**: No shared in-process state; horizontally scalable via K8s replicas.

## Kubernetes Deployment

```
k8s/
├── namespace.yaml         # presight namespace
├── configmap.yaml         # inventory.low-stock-threshold (dynamic)
├── inventory-service.yaml # Deployment (2 replicas) + ClusterIP Service
├── order-service.yaml     # Deployment (2 replicas) + ClusterIP Service
└── api-gateway.yaml       # Deployment (2 replicas) + LoadBalancer Service

Deploy order:
  kubectl apply -f k8s/namespace.yaml
  kubectl apply -f k8s/configmap.yaml
  kubectl apply -f k8s/inventory-service.yaml
  kubectl apply -f k8s/order-service.yaml
  kubectl apply -f k8s/api-gateway.yaml
```

## Running Locally

```bash
# Build all services
mvn clean package -DskipTests

# Run individually
java -jar inventory-service/target/inventory-service-1.0.0-SNAPSHOT.jar
java -jar order-service/target/order-service-1.0.0-SNAPSHOT.jar
java -jar api-gateway/target/api-gateway-1.0.0-SNAPSHOT.jar

# Or with Docker Compose
docker-compose up --build
```

## API Endpoints

### Order Service (via Gateway at :8080)
| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | /api/orders | Create new order (body: `{productCode, quantity}`) |
| GET | /api/orders/{id} | Get order by ID |
| PUT | /api/orders/{id}/cancel | Cancel a confirmed order (restores inventory) |

### Inventory Service (via Gateway at :8080)
| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | /api/inventory/{productCode} | Get stock info |
| POST | /api/inventory/deduct | Deduct stock (body: `{productCode, quantity, orderId}`) |
| POST | /api/inventory/restore?productCode=X&quantity=N&orderId=M | Restore stock (idempotent) |
| GET | /api/inventory/config/threshold | Get current low-stock threshold |
| PUT | /api/inventory/config/threshold?value=N | Update threshold dynamically |

### Documentation & Monitoring
| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | /swagger-ui.html | Swagger UI (per service) |
| GET | /actuator/health | Health check |
| GET | /h2-console | H2 database console (dev only) |
