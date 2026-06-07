# URL Shortener — Project Specification
> Master reference for Claude Code. Read this file in full before generating any code, scaffolding, or infrastructure.

---

## 1. Project Overview

A production-quality URL shortener built on AWS serverless infrastructure. Designed as a backend engineering portfolio piece with interview-scale system design. The application is fully functional and deployed — not a toy.

**Domain setup (placeholder — replace with your actual domains):**
- Frontend: `www.myapp.com`
- Short links: `myapp.io/{code}`

**Scale:** Small personal project (~10 users) but architected and implemented as if it could scale to millions.

---

## 2. Functional Requirements (MVP)

1. Users can submit a long URL and receive a shortened URL in return.
2. Users can visit a shortened URL and be redirected to the original destination.

> Future iterations (do NOT implement in MVP): custom aliases, link expiration/TTLs, click analytics dashboard, user accounts/auth, link deletion, link listing.

---

## 3. API Design

### Endpoints

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/shorten` | Accept a long URL, return a short code and full short URL |
| `GET` | `/{code}` | Look up the short code, return HTTP 302 redirect to original URL |

### POST /shorten

**Request body:**
```json
{
  "long_url": "https://www.amazon.com/some/very/long/product/link"
}
```

**Response (201 Created):**
```json
{
  "short_code": "x7k2p",
  "short_url": "https://myapp.io/x7k2p",
  "long_url": "https://www.amazon.com/some/very/long/product/link",
  "created_at": "2026-06-06T12:00:00Z",
  "expires_at": null
}
```

**Validation:**
- Reject null/empty URLs
- Reject malformed URLs (must be valid HTTP/HTTPS)
- Return `400 Bad Request` with descriptive error message on invalid input

### GET /{code}

**Success:** `302 Found` with `Location` header set to the original long URL.

> **Important:** Must be 302 (temporary), NOT 301 (permanent). 301 causes browsers to cache the redirect, bypassing the server on subsequent clicks and breaking future analytics.

**Not found:** `404 Not Found` if the short code does not exist in the database.

---

## 4. Tech Stack

### Backend
- **Language:** Java 21
- **Framework:** Spring Boot 3.x
- **Build tool:** Gradle (Kotlin DSL — `build.gradle.kts`)
- **Deployment:** AWS Lambda via Spring Cloud Function or `aws-serverless-java-container`
- **Key dependencies:**
  - `spring-boot-starter-web`
  - `spring-boot-starter-validation` (for URL validation)
  - `software.amazon.awssdk:dynamodb` (AWS SDK v2)
  - `software.amazon.awssdk:dynamodb-enhanced`
  - `spring-data-redis` + `lettuce-core` (for ElastiCache/Redis)
  - `aws-serverless-java-container-springboot3` (Lambda adapter)

### Frontend
- **Framework:** React 18 + TypeScript
- **Build tool:** Vite
- **Styling:** Tailwind CSS + shadcn/ui
- **HTTP client:** Axios or native fetch
- **Deployment:** AWS S3 + CloudFront

### Frontend Design Approach
- AI-generated via v0 (Vercel) for initial component scaffolding, then refined in Claude Code
- Design aesthetic: **lo-fi / cyberpunk** — dark background, neon accent colors, monospace typography, subtle grid/scanline texture. Distinctive and memorable, not generic AI purple-gradient-on-white.
- Avoid: Inter, Roboto, Arial, generic purple gradients, cookie-cutter layouts
- Use: CSS variables for theming, micro-animations, cohesive dark palette with sharp accent color

### Infrastructure (AWS)
See Section 6 for full architecture details.

---

## 5. Short Code Generation

- **Length:** 7 characters (alphanumeric: `[a-zA-Z0-9]`)
- **Strategy:** Generate random 7-char code → check DynamoDB for collision → retry if collision found (collision probability is negligible at this scale)
- **Algorithm:** `Base62` encoding or `SecureRandom` against a 62-char alphabet
- **Do NOT use:** Sequential IDs (guessable), UUIDs (too long)

---

## 6. AWS Architecture

### Services Used

| Service | Purpose | VPC Required |
|---------|---------|--------------|
| Route 53 | DNS routing for both domains | No |
| CloudFront | CDN + HTTPS termination for frontend | No |
| S3 | Static frontend file hosting | No |
| API Gateway | Public HTTP entry point for backend | No |
| Lambda: shorten | Handles POST /shorten | No |
| Lambda: redirect | Handles GET /{code}, VPC-attached | **Yes** |
| DynamoDB | Persistent URL mapping storage | No |
| ElastiCache (Redis) | In-memory cache for hot short codes | **Yes** |
| SQS | Click event queue (future analytics) | No |
| Lambda: analytics | Consumes SQS events (future) | No |

### VPC Notes
- **ElastiCache requires a VPC** (private subnet). This is the only reason a VPC exists in this architecture.
- **Lambda: redirect must be VPC-attached** to reach ElastiCache. This adds a small cold-start latency penalty (~ms).
- **Internet Gateway** is present in the VPC but unused — no public internet traffic ever enters the VPC. Only internal AWS traffic (Lambda → ElastiCache) flows through the private subnet.
- Lambda: shorten and Lambda: analytics run **outside the VPC**.

### Traffic Flows

**Loading the frontend:**
```
User → Route 53 (www.myapp.com) → CloudFront → S3
```

**Shortening a URL:**
```
User (browser) → API Gateway → Lambda: shorten → DynamoDB
```

**Clicking a short link:**
```
User → Route 53 (myapp.io/{code}) → API Gateway → Lambda: redirect
  → ElastiCache (cache hit → 302 redirect immediately)
  → DynamoDB (cache miss → 302 redirect → write to ElastiCache)
  → SQS (async, fire-and-forget for future analytics)
```

### Caching Strategy
- **Pattern:** Cache-aside (lazy loading)
- **Cache population:** On first cache miss, load from DynamoDB and write to Redis
- **TTL:** Set a reasonable expiry (e.g. 24h) on Redis entries so stale/dead links don't persist in memory
- **Cache hit:** Return immediately, skip DynamoDB entirely
- **Cache miss:** Hit DynamoDB, populate cache, return result

### DynamoDB Table Design

**Table name:** `url-mappings`

**Primary key:** `short_code` (String, partition key)

**Attributes:**
```
short_code  (String)  — partition key, e.g. "x7k2p"
long_url    (String)  — the original full URL
created_at  (String)  — ISO 8601 timestamp
expires_at  (String)  — ISO 8601 timestamp, nullable (null = no expiry)
```

**Access pattern:** Always a single key lookup by `short_code`. No GSIs needed for MVP.

**Billing mode:** On-demand (PAY_PER_REQUEST) — no capacity planning needed at this scale, and cost is effectively zero.

---

## 7. Project Structure

```
url-shortener/
├── backend/                          # Spring Boot Java application
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/com/urlshortener/
│   │   │   │   ├── UrlShortenerApplication.java
│   │   │   │   ├── controller/
│   │   │   │   │   └── UrlController.java        # REST endpoints
│   │   │   │   ├── service/
│   │   │   │   │   └── UrlService.java           # Business logic
│   │   │   │   ├── repository/
│   │   │   │   │   ├── DynamoDbRepository.java   # DynamoDB access
│   │   │   │   │   └── CacheRepository.java      # Redis access
│   │   │   │   ├── model/
│   │   │   │   │   ├── UrlMapping.java           # DynamoDB entity
│   │   │   │   │   ├── ShortenRequest.java       # Request DTO
│   │   │   │   │   └── ShortenResponse.java      # Response DTO
│   │   │   │   ├── util/
│   │   │   │   │   └── ShortCodeGenerator.java   # Base62 code gen
│   │   │   │   └── exception/
│   │   │   │       └── GlobalExceptionHandler.java
│   │   │   └── resources/
│   │   │       └── application.yml
│   │   └── test/
│   └── build.gradle.kts
│
├── frontend/                         # React + TypeScript application
│   ├── src/
│   │   ├── components/
│   │   │   ├── UrlForm.tsx           # Main shorten form
│   │   │   └── ResultCard.tsx        # Displays the shortened URL
│   │   ├── services/
│   │   │   └── api.ts                # API calls to backend
│   │   ├── App.tsx
│   │   ├── main.tsx
│   │   └── index.css
│   ├── index.html
│   ├── vite.config.ts
│   ├── tailwind.config.ts
│   └── package.json
│
├── infrastructure/                   # AWS IaC (Terraform or SAM — TBD)
│   └── (scaffold but do not implement in MVP scaffolding phase)
│
└── PROJECT_SPEC.md                   # This file
```

---

## 8. Environment Variables / Config

### Backend (application.yml / Lambda env vars)
```
AWS_REGION=us-east-1
DYNAMODB_TABLE_NAME=url-mappings
REDIS_HOST=<elasticache-endpoint>
REDIS_PORT=6379
BASE_SHORT_URL=https://myapp.io
```

### Frontend (.env)
```
VITE_API_BASE_URL=https://api.myapp.com
```

---

## 9. Error Handling

| Scenario | HTTP Status | Response |
|----------|------------|----------|
| Valid request | 201 / 302 | See API section |
| Invalid / malformed URL | 400 | `{ "error": "Invalid URL format" }` |
| Missing request body | 400 | `{ "error": "long_url is required" }` |
| Short code not found | 404 | `{ "error": "Short code not found" }` |
| Internal server error | 500 | `{ "error": "Internal server error" }` |

---

## 10. What to Scaffold First (Implementation Order)

Claude Code should scaffold in this order:

1. **Backend project skeleton** — Spring Boot 3 + Gradle, all dependencies in `build.gradle.kts`, `application.yml` with placeholder config, package structure as defined in Section 7
2. **Data models** — `UrlMapping`, `ShortenRequest`, `ShortenResponse`
3. **ShortCodeGenerator utility** — Base62, 7 characters, collision-safe
4. **DynamoDB repository** — CRUD using AWS SDK v2 enhanced client
5. **Redis cache repository** — get/set with TTL using Spring Data Redis
6. **UrlService** — business logic: cache-aside lookup, code generation, persistence
7. **UrlController** — REST endpoints, validation, error handling
8. **GlobalExceptionHandler** — `@ControllerAdvice` for consistent error responses
9. **Frontend scaffold** — Vite + React + TS + Tailwind + shadcn/ui, lo-fi/cyberpunk aesthetic, UrlForm and ResultCard components wired to the API
10. **CI/CD** — `.github/workflows/backend.yml` and `frontend.yml` as defined in Section 14

---

## 11. Frontend Design Directive (for Claude Code)

When generating the frontend, commit to a **lo-fi cyberpunk** aesthetic:
- Dark background (`#0d0d0f` or similar near-black)
- Neon accent color (green `#39ff14` or cyan `#00f5ff` — pick one and commit)
- Monospace font for code/URLs (e.g. `JetBrains Mono`, `IBM Plex Mono`)
- Clean sans-serif for UI text (e.g. `Syne`, `DM Sans`) — NOT Inter or Roboto
- Subtle scanline or grid texture overlay on the background
- Sharp borders, no rounded corners on primary elements
- Micro-animation on form submit (loading state, result reveal)
- Single-page layout — hero section with the form, result displayed inline
- Mobile responsive

---

## 12. Key Design Decisions & Rationale

| Decision | Choice | Why |
|----------|--------|-----|
| Redirect type | 302 | Prevents browser caching; every click hits the server for analytics |
| Short code length | 7 chars Base62 | ~3.5 trillion combinations; collision-proof at any realistic scale |
| Database | DynamoDB | Key-value lookups by short_code; no relational data; scales automatically |
| Cache | ElastiCache Redis | In-memory; dramatically faster than DynamoDB for hot links |
| Cache pattern | Cache-aside | Only cache what's actually accessed; no wasted memory |
| Compute | Lambda | Serverless; scales to zero; effectively free at personal project scale |
| VPC | Minimal (ElastiCache only) | ElastiCache requires VPC; everything else runs outside it |
| Billing | DynamoDB on-demand | No capacity planning; ~$0 at this scale |

---

## 13. Out of Scope for MVP

The following are **planned future iterations** — do NOT implement or scaffold:
- User authentication / accounts
- Custom aliases
- Link expiration / TTLs (field exists in DB schema, logic not implemented)
- Click analytics dashboard
- `GET /links` (list all links)
- `DELETE /links/{code}`
- `PATCH /links/{code}`
- SQS analytics pipeline (SQS and Lambda: analytics exist in architecture but are wired up in a future iteration)
- Terraform / CDK infrastructure as code


---

## 14. CI/CD Pipeline

### Why from the start
CI/CD should be set up at project initialization, not bolted on later. The cost of adding it early is low (one GitHub Actions file), and it immediately gives you automated safety nets — every push gets built and tested before it can break anything. Retrofitting it later means fixing all the environment config, secrets wiring, and build assumptions that accumulated while you were working without it.

### Recommended: GitHub Actions

Since the project lives on GitHub, GitHub Actions is the natural fit — free for public repos, no external service needed, and deeply integrated with the repo.

### Pipeline Stages

**Backend (on every push to `main` and on every PR):**
```
Push / PR → Checkout → Set up Java 21 → Gradle build → Run tests → Build Lambda JAR → (on main only) Deploy to AWS Lambda
```

**Frontend (on every push to `main` and on every PR):**
```
Push / PR → Checkout → Node setup → npm install → TypeScript check → Vite build → (on main only) Upload dist/ to S3 → Invalidate CloudFront cache
```

### Files to create at scaffold time
```
.github/
└── workflows/
    ├── backend.yml    # Java build, test, Lambda deploy
    └── frontend.yml   # Node build, S3 deploy, CloudFront invalidation
```

### Required GitHub Secrets
These get stored in GitHub repo Settings → Secrets and must be configured before the deploy steps work:
```
AWS_ACCESS_KEY_ID
AWS_SECRET_ACCESS_KEY
AWS_REGION
LAMBDA_FUNCTION_NAME_SHORTEN
LAMBDA_FUNCTION_NAME_REDIRECT
S3_BUCKET_NAME
CLOUDFRONT_DISTRIBUTION_ID
```

### Branches
- `main` — production. Every merge triggers a full build + deploy.
- Feature branches — PRs trigger build + test only, no deploy.

### What CI/CD does NOT cover in MVP
- Staging / preview environments
- Integration tests against live AWS resources
- Automated rollback on failed deploy
- Infrastructure provisioning (Terraform) — that remains manual for now

---

## 15. Link Expiration / TTL Implementation

> This is a **V2 feature** — not in MVP. But the DB schema already has the `expires_at` field so it is ready to implement when you get here.

### How it works across the stack

**Redis TTL — built-in, no custom logic needed.**
Redis natively supports per-key expiration via the `EXPIRE` or `SET ... EX` command. When you write a link to the cache, you pass a TTL in seconds and Redis automatically evicts it when the time is up. You do not write any expiration logic yourself for the cache layer — you just pass the duration:

```java
// Spring Data Redis — set key with TTL
redisTemplate.opsForValue().set(shortCode, longUrl, Duration.ofHours(24));
```

When Redis evicts the key, the next lookup is a cache miss and falls through to DynamoDB as normal.

**DynamoDB TTL — also built-in, also no custom logic needed.**
DynamoDB has a native TTL feature. You designate one attribute as the TTL attribute (in your case `expires_at`), store a Unix epoch timestamp in it, and DynamoDB automatically deletes the item within ~48 hours of that timestamp passing. You enable this once at the table level — no scheduled jobs, no delete logic in your code.

```
expires_at: 1783000000   ← Unix epoch seconds, DynamoDB deletes this item automatically
expires_at: null         ← no expiry, item lives forever
```

**Application logic — this is the part you do write.**
When a redirect request comes in, after fetching the mapping (from cache or DB), check if it's expired:

```java
if (mapping.getExpiresAt() != null && Instant.now().isAfter(mapping.getExpiresAt())) {
    return 404; // treat expired link same as not found
}
```

This guard is needed because DynamoDB TTL deletion has up to a 48h delay — an item can still be returned by a query even after its TTL timestamp has passed. Your application needs to enforce expiry at the code level, not rely solely on DynamoDB having deleted it yet.

### Summary

| Layer | Mechanism | Custom code? |
|-------|-----------|--------------|
| Redis | Native `EXPIRE` / `SET EX` | No — pass TTL duration on write |
| DynamoDB | Native TTL attribute | No — enable at table level, store Unix timestamp |
| Application | Check `expires_at` on every lookup | Yes — ~3 lines, return 404 if expired |

