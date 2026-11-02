# Core Banking REST API

A secure banking backend where customers register, open accounts, deposit, withdraw, transfer money
between accounts and view paginated statements.
Built with **Java 17+, Spring Boot 3, Spring Security, JWT, Spring Data JPA, PostgreSQL and Docker**.

## Features

- **JWT authentication** with BCrypt-hashed passwords (stateless, no sessions)
- **Savings and Current accounts** with auto-generated 12-digit account numbers
- **Deposit, withdraw and transfer** using `BigDecimal` for exact money arithmetic
- **Atomic transfers**: debit and credit happen in one database transaction, so they succeed or fail together
- **Concurrency safety**: pessimistic row locks (`SELECT … FOR UPDATE`) acquired in a fixed order to prevent
  deadlocks, plus `@Version` optimistic locking as a second guard
- **Business rules**: ₹500 minimum balance on savings, ₹1,00,000 per-transaction limit,
  frozen/closed accounts blocked, no transfers to the same account, closing requires zero balance
- **Ownership checks**: customers can only see and use their own accounts
- **Paginated statements**, newest first; both legs of a transfer share one reference ID
- Consistent JSON errors with machine-readable codes (`INSUFFICIENT_FUNDS`, `LIMIT_EXCEEDED`, …)
- Integration tests covering transfers, rollback, rules, ownership and authentication

## Architecture

```
            ┌──────────── JwtAuthFilter (validates Bearer token) ────────────┐
Client ──►  │  AuthController ──► AuthService ──► CustomerRepository          │ ──► PostgreSQL
            │  AccountController ──► AccountService ──► AccountRepository     │     customers
            │                                     └──► TransactionRepository  │     accounts
            └─────────────────────────────────────────────────────────────────┘     transactions
```

## Data model

| Table | Key columns |
|-------|-------------|
| `customers` | id, full_name, email (unique), password_hash |
| `accounts` | id, account_number (unique), type, balance, status, customer_id, version |
| `transactions` | id, account_id, type, amount, balance_after, reference, counterparty_account, created_at |

## API

All endpoints except `/api/auth/**` need the header `Authorization: Bearer <token>`.

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/auth/register` | Create a customer, returns a JWT |
| POST | `/api/auth/login` | Log in, returns a JWT |
| POST | `/api/accounts` | Open an account (`SAVINGS` / `CURRENT`, optional initial deposit) |
| GET | `/api/accounts` | List my accounts |
| GET | `/api/accounts/{no}` | Account details and balance |
| POST | `/api/accounts/{no}/deposit` | Deposit money |
| POST | `/api/accounts/{no}/withdraw` | Withdraw money |
| POST | `/api/transfers` | Transfer between accounts |
| GET | `/api/accounts/{no}/transactions?page=0&size=20` | Paginated statement |
| PATCH | `/api/accounts/{no}/status?status=FROZEN` | Freeze, reactivate or close an account |

### Example flow

```bash
# 1. Register
curl -X POST http://localhost:8080/api/auth/register -H "Content-Type: application/json" \
  -d '{"fullName":"Yavanika","email":"yavanika@example.com","password":"Secret@123"}'
# → copy "token" from the response
TOKEN=<paste token>

# 2. Open a savings account with ₹5,000
curl -X POST http://localhost:8080/api/accounts -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" -d '{"type":"SAVINGS","initialDeposit":5000}'

# 3. Transfer ₹1,000 to another account
curl -X POST http://localhost:8080/api/transfers -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"fromAccount":"<your no>","toAccount":"<other no>","amount":1000,"description":"Rent"}'

# 4. Statement
curl "http://localhost:8080/api/accounts/<your no>/transactions" -H "Authorization: Bearer $TOKEN"
```

Error example:
```json
{ "status": 422, "code": "INSUFFICIENT_FUNDS",
  "message": "Insufficient funds: savings accounts must keep a minimum balance of 500.00" }
```

## Run it

**Option 1 – Docker (app + PostgreSQL):**
```bash
docker compose up --build
```

**Option 2 – Without a database (H2 in memory):**
```bash
mvn spring-boot:run -Dspring-boot.run.profiles=h2
```

**Option 3 – Local PostgreSQL:** create a database `bankingdb`, then `mvn spring-boot:run`.

**Tests:**
```bash
mvn test
```

Set a real `JWT_SECRET` (base64, 32+ bytes) outside development: `openssl rand -base64 48`.

## Design notes

- **Why lock in account-number order?** If A→B and B→A transfers run at the same time and each locks
  its own "from" account first, they wait on each other forever (deadlock). Locking the smaller account
  number first means both requests lock in the same order.
- **Why `BigDecimal`?** `double` cannot represent values like 0.1 exactly, which causes rounding errors
  in money calculations.
- **Why 404 for someone else's account?** Returning 403 would confirm the account number exists.
