# Banking AI MCP Platform
### Production-Grade Multi-Module Spring Boot + Spring AI + MCP

An **8-module** Spring Boot monorepo demonstrating Model Context Protocol (MCP) in a
full banking lifecycle: **Customer Onboarding → KYC → Account Opening → Payments → Fraud Detection**,
with event-driven architecture via **Apache Kafka** for notifications, audit trail, and compliance.

---

## Module Architecture

```
banking-ai-mcp/
├── banking-common/          Shared: BaseEntity, Money, ApiResponse, Exceptions
├── banking-events/          Kafka event DTOs (pure records — no Spring, no JPA)
├── banking-notification/    Email/SMS/Push notifications + Kafka consumer
├── banking-onboarding/      Customer lifecycle, KYC, AML screening
├── banking-account/         Account management, balance, limits, holds
├── banking-payment/         NEFT/RTGS/IMPS/UPI/SWIFT payments with fund holds
├── banking-fraud/           Rule-based fraud engine (Open/Closed principle)
└── banking-ai-gateway/      Spring Boot app: security, REST APIs, AI orchestration, Kafka consumers
```

## Dependency Graph

```
banking-common
    ▲
banking-notification ─────────────────────────────────┐
    ▲                                                  │
banking-onboarding ──────────────────────────────────┐ │
    ▲                                                 │ │
banking-account ──────────────────────────────────── ┼─┤
    ▲                                                 │ │
banking-payment ─────────────────────────────────────┘ │
    ▲                                                   │
banking-fraud                                           │
    ▲                                                   │
banking-ai-gateway ◄───────── all modules ◄────────────┘
```

## MCP Tool Catalogue (20 tools)

### 🏦 Onboarding (5 tools)
| Tool | Description |
|---|---|
| `get_customer_profile` | Full KYC and onboarding status |
| `get_customer_by_email` | Look up customer by email |
| `update_kyc_status` | Approve / reject KYC |
| `get_pending_kyc_customers` | Compliance review queue |
| `complete_customer_onboarding` | Finalise onboarding |

### 💳 Account (6 tools)
| Tool | Description |
|---|---|
| `open_bank_account` | Open account post-KYC |
| `get_account_details` | Full account info + limits |
| `get_account_balance` | Live balance + holds |
| `get_customer_accounts` | All accounts for a customer |
| `check_sufficient_funds` | Pre-payment verification |
| `block_account` | Emergency account block |

### 💸 Payment (7 tools)
| Tool | Description |
|---|---|
| `initiate_payment` | Create payment (funds held immediately) |
| `process_payment` | Execute cleared payment |
| `get_payment_status` | Full payment status |
| `get_payment_history` | Paginated transaction history |
| `hold_payment_for_fraud` | Place on FRAUD_HOLD |
| `reverse_payment` | Reverse completed payment |
| `get_daily_spending_summary` | Today's analytics |

### 🔍 Fraud (2 tools)
| Tool | Description |
|---|---|
| `analyse_payment_fraud_risk` | Run all 6 rules, get score + decision |
| `get_fraud_decision_guidance` | Plain-English compliance guidance |

## Production Features

| Feature | Implementation |
|---|---|
| **JPA Auditing** | `BaseEntity` with `@CreatedBy`, `@LastModifiedBy`, `@Version` (optimistic locking) |
| **Validation** | Bean Validation on all DTOs with `@Valid` |
| **MapStruct** | Zero boilerplate mapping between entity and DTO |
| **Fund Holds** | Debit holds placed on `initiatePayment`, released on complete/fail |
| **Fraud Rules** | Strategy pattern — each rule is a separate `@Component`, easily extensible |
| **Security** | API Key filter (`X-API-Key`) on all REST endpoints; MCP endpoints (`/sse`, `/mcp/**`) are open — secure at network boundary in production |
| **Audit Logging** | AOP aspect logs all controller calls with caller, timing, and outcome |
| **Exception Hierarchy** | Typed exceptions with HTTP status codes — never leaks stack traces |
| **Session Management** | In-memory map with max 1000 sessions and 50-message history trim |
| **Observability** | Micrometer + Prometheus endpoint (`/actuator/prometheus`) |
| **Pagination** | All list endpoints use `Page<T>` with `PagedResponse<T>` wrapper |
| **Kafka Streaming** | 4 topics (notifications, payments, audit, KYC); producers publish after DB commit (`@TransactionalEventListener`); DLT + `ExponentialBackOff` on all consumers; feature-flagged via `banking.kafka.enabled` for zero-risk rollout |
| **Kafka UI** | `provectuslabs/kafka-ui` on port 8090 for dev visibility into topics and messages |
| **Correlation ID Tracing** | `X-Correlation-ID` header born at the HTTP boundary (auto-generated if absent), carried through MDC into every log line, stamped on every Kafka message header, and restored in all consumers — single traceable ID across sync and async boundaries |

## Fraud Rules Engine

Six independent rules scored and summed (capped at 1.0):

| Rule | Trigger | Score |
|---|---|---|
| `HIGH_VALUE` | > $100k / $50k / $10k | +0.45 / +0.30 / +0.15 |
| `VELOCITY` | ≥ 10 or ≥ 5 payments in 1 hour | +0.40 / +0.25 |
| `OFF_HOURS` | Between 11pm and 5am | +0.10 |
| `INTERNATIONAL_WIRE` | SWIFT payment type | +0.15 |
| `ROUND_AMOUNT` | Multiple of 1,000 | +0.08 |
| `DAILY_LIMIT` | Projected daily total > $200k | +0.25 |

**Decision thresholds:**
- `< 0.40`  → `APPROVE` → call `process_payment`
- `0.40–0.70` → `HOLD_FOR_REVIEW` → call `hold_payment_for_fraud`
- `≥ 0.70`  → `BLOCK` → call `hold_payment_for_fraud` + consider `block_account`

## Setup & Run

```bash
export OPENAI_API_KEY=sk-your-key-here
export BANKING_API_KEY=my-secret-key        # optional, default: banking-demo-key-2024

# Run with demo data (Alice, Bob, Charlie pre-seeded):
cd banking-ai-gateway
SPRING_PROFILES_ACTIVE=dev ../gradlew bootRun

# Run with demo data + Kafka enabled (requires Kafka on localhost:9092/9094/9096):
SPRING_PROFILES_ACTIVE=dev,kafka ../gradlew bootRun

# Run without demo data (clean slate):
../gradlew bootRun

# Full production stack (Kafka 3-node cluster + Kafka UI included):
docker-compose up
# Kafka UI: http://localhost:8090
```

## API Reference

All requests require: `X-API-Key: banking-demo-key-2024`
Optional: `X-Correlation-ID: <your-id>` — traces the request through logs and Kafka. Auto-generated and returned in the response if not supplied.

### Onboarding
```bash
# Onboard a customer
curl -X POST http://localhost:8080/api/v1/onboarding/customers \
  -H "X-API-Key: banking-demo-key-2024" \
  -H "Content-Type: application/json" \
  -d '{
    "firstName":"Alice","lastName":"Johnson",
    "dateOfBirth":"1990-05-15","gender":"FEMALE",
    "email":"alice@example.com","mobile":"+447700900123",
    "nationality":"British","panNumber":"ABCDE1234F",
    "idType":"PAN_CARD","idExpiryDate":"2030-01-01",
    "address":{"line1":"123 Main St","city":"London","state":"England","postalCode":"EC1A 1BB","country":"GBR"},
    "employmentType":"SALARIED","annualIncome":80000,"incomeCurrency":"GBP"
  }'

# Approve KYC
curl -X PATCH http://localhost:8080/api/v1/onboarding/customers/{customerId}/kyc \
  -H "X-API-Key: banking-demo-key-2024" \
  -d '{"kycStatus":"VERIFIED"}'

# Complete onboarding
curl -X POST http://localhost:8080/api/v1/onboarding/customers/{customerId}/complete \
  -H "X-API-Key: banking-demo-key-2024"
```

### Accounts
```bash
# Open account
curl -X POST http://localhost:8080/api/v1/accounts \
  -H "X-API-Key: banking-demo-key-2024" \
  -d '{"customerId":"CUST-001","accountType":"SAVINGS","currency":"USD","initialDeposit":5000}'

# Get balance
curl http://localhost:8080/api/v1/accounts/{accountId}/balance \
  -H "X-API-Key: banking-demo-key-2024"
```

### AI Chat
```bash
# Full onboarding via AI
curl -X POST http://localhost:8080/api/v1/banking-ai/chat \
  -H "X-API-Key: banking-demo-key-2024" \
  -d '{"sessionId":"s1","message":"Onboard a new customer Bob Smith, DOB 1985-03-20, email bob@example.com, mobile +447700900999, PAN XYZAB9876C, expiry 2028-12-31, address 10 High St, Manchester, M1 1AA, GBR. Employment: SALARIED at Tech Corp, income 60000 GBP."}'

# Full payment workflow via AI
curl -X POST http://localhost:8080/api/v1/banking-ai/chat \
  -H "X-API-Key: banking-demo-key-2024" \
  -d '{"sessionId":"s1","message":"Send 85000 USD from ACC-001 to ACC-002 via SWIFT. Run fraud check and take appropriate action."}'
```

### MCP Inspector (dev tool)
Connect directly to the 20 banking tools without AI — useful for testing individual tools:
```
Transport: SSE
URL:       http://localhost:8080/sse
```
No API key required. Once connected, Inspector calls `POST /mcp/message` automatically for `tools/list` and `tools/call`.

### H2 Console (dev only)
```
http://localhost:8080/h2-console
JDBC URL: jdbc:h2:mem:bankingdb
Username: sa | Password: (empty)
```

## Production Readiness Checklist

- [ ] Replace H2 with PostgreSQL + Flyway migrations
- [ ] Replace `ApiKeyAuthFilter` with Spring Security OAuth2 + JWT
- [ ] Replace in-memory session `ConcurrentHashMap` with Redis (`spring-session-data-redis`)
- [x] Kafka event streaming (notifications, payments, audit, KYC) — activate via `kafka` profile
- [ ] Replace `NotificationService` log stubs with real Mail + Twilio integrations
- [ ] Add external KYC bureau integration (Onfido, Jumio, or govt API)
- [ ] Add external AML/sanctions screening (OFAC SDN list, PEP database)
- [ ] Wire ML fraud model (ONNX runtime or Python sidecar via REST)
- [x] Correlation ID tracing (`X-Correlation-ID` header → MDC → Kafka header → consumer MDC)
- [ ] Micrometer Tracing + Zipkin/Jaeger for span-level distributed tracing
- [ ] Add rate limiting (Bucket4j or API Gateway)
- [ ] Add Flyway for database migrations (`ddl-auto: validate`)
- [ ] Add Spring Cache (`@Cacheable`) on read-heavy account queries
- [ ] Store audit logs to immutable store (AWS CloudTrail or dedicated audit DB)
