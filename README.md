<div align="center">

# SHR.T

**A production-grade URL shortener, architected for scale — built serverless on AWS.**

![Java](https://img.shields.io/badge/Java-21-orange?logo=openjdk&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.x-6DB33F?logo=springboot&logoColor=white)
![AWS](https://img.shields.io/badge/AWS-Serverless-FF9900?logo=amazonaws&logoColor=white)
![Terraform](https://img.shields.io/badge/Terraform-IaC-7B42BC?logo=terraform&logoColor=white)
![React](https://img.shields.io/badge/React-18-61DAFB?logo=react&logoColor=black)
![TypeScript](https://img.shields.io/badge/TypeScript-3178C6?logo=typescript&logoColor=white)
![License](https://img.shields.io/badge/License-MIT-blue)
![Status](https://img.shields.io/badge/Status-Backend%20Live%20%7C%20Frontend%20In%20Progress-brightgreen)

</div>

---

## Overview

SHR.T shortens long URLs into compact, shareable links — architected the way you'd design a system meant to handle millions of users, not a weekend toy. Multi-AZ high availability, async event-driven analytics, cache-aside caching, and defense against SSRF attacks are all built in from day one.

**The backend is live in AWS right now.** Every piece of infrastructure — Lambda, API Gateway, DynamoDB, ElastiCache, SQS, VPC, IAM — is provisioned entirely through Terraform, and every push to `main` auto-deploys real application code via a GitHub Actions pipeline authenticated through OIDC federation (no long-lived AWS credentials stored anywhere).

<div align="center">
  <img src="./assets/url-shortener-mockup.png" alt="SHR.T UI Mockup" width="700">
</div>

---

## Architecture

<div align="center">
  <img src="./assets/shrt-architecture-diagram.png" alt="AWS Architecture Diagram" width="900">
</div>

**Highlights:**
- Fully serverless compute via AWS Lambda (Java 21 + Spring Boot 3)
- Multi-AZ ElastiCache (Redis) with auto-failover for sub-millisecond redirect lookups
- Async, event-driven click analytics via SQS — redirects never wait on tracking
- VPC scoped down to exactly what needs network isolation, with VPC Endpoints (not a NAT Gateway) so the VPC-attached Lambda can still reach DynamoDB and SQS
- SSRF-protected input validation, least-privilege IAM, CORS locked to a single origin
- Every AWS resource — nine categories in total — provisioned via Terraform, zero manual console clicks
- CI/CD deploys real code via GitHub OIDC federation — no static AWS credentials in CI/CD, ever
- CloudWatch alarms (Lambda errors, API Gateway 5xx, DynamoDB throttling) + SNS email alerts, plus an AWS Budget alert — deploying without failure or cost visibility was treated as a real gap, not a nice-to-have

Full design rationale — including why 302 over 301, why cache-aside over write-through, why DynamoDB over a relational store, and every real-world gap caught and fixed along the way (IAM permissions, VPC networking, Lambda packaging, OIDC trust policies) — is documented in [`PROJECT_SPEC.md`](./PROJECT_SPEC.md).

---

## Tech Stack

| Layer | Technology |
|---|---|
| **Backend** | Java 21, Spring Boot 3, Gradle (Shadow plugin for Lambda packaging) |
| **Frontend** | React 18, TypeScript, Vite, Tailwind CSS, shadcn/ui *(in progress)* |
| **Database** | DynamoDB (URL mappings + click events) |
| **Cache** | ElastiCache (Redis), Multi-AZ with auto-failover |
| **Messaging** | SQS (async analytics pipeline) |
| **Compute** | AWS Lambda (3 functions: shorten, redirect, analytics) |
| **Networking** | API Gateway (HTTP API), CloudFront, VPC, VPC Endpoints |
| **IaC** | Terraform — all 9 AWS resource categories |
| **CI/CD** | GitHub Actions, OIDC federation (no static credentials) |
| **Monitoring** | CloudWatch Alarms + SNS, AWS Budget alerts |

---

## Live Backend

The backend API is deployed and publicly reachable right now. Example, verified working:

```bash
curl -X POST https://<api-invoke-url>/shorten \
  -H "Content-Type: application/json" \
  -d '{"long_url":"https://example.com"}'
# → 201 Created, real short code, real DynamoDB write

curl https://<api-invoke-url>/{short_code}
# → 302 Found, redirects to the original URL, click event recorded async via SQS
```

*(Custom domain pending — see Project Status below. Currently served on AWS's default API Gateway/CloudFront domains.)*

---

## API

<details>
<summary><strong>POST /shorten</strong> — create a short link</summary>

**Request:**
```json
{
  "long_url": "https://www.amazon.com/some/very/long/product/link"
}
```

**Response — 201 Created:**
```json
{
  "short_code": "x7k2p",
  "short_url": "https://shrt.link/x7k2p",
  "long_url": "https://www.amazon.com/some/very/long/product/link",
  "created_at": "2026-07-12T12:00:00Z",
  "expires_at": "2026-07-19T12:00:00Z"
}
```

Links expire 7 days after creation (fixed MVP default — see spec for rationale).

</details>

<details>
<summary><strong>GET /{code}</strong> — resolve and redirect</summary>

Returns `302 Found` with a `Location` header pointing to the original URL. Uses 302 (not 301) intentionally — see the spec for why.

`404 Not Found` if the code doesn't exist or has expired.

</details>

---

## Project Status

- [x] System design & architecture diagram
- [x] Backend: data models, repositories, services, controllers
- [x] Backend: SQS async analytics pipeline
- [x] Backend: SSRF protection, centralized error handling
- [x] Backend: fixed 7-day link expiration
- [x] Unit tests — 6 test classes, 44 tests, zero real AWS/Redis dependencies
- [x] CI/CD pipeline (GitHub Actions) — build + test on every push
- [x] Infrastructure as Code — all 9 AWS resource categories provisioned via Terraform
- [x] AWS deployment — **backend live and verified working end-to-end**
- [x] CI/CD deploy — OIDC federation, auto-deploys real code on push to `main`
- [x] Monitoring — CloudWatch alarms + SNS, AWS Budget alerts
- [ ] Custom domain (Route 53 + ACM) *(intentionally deferred)*
- [ ] Frontend implementation *(in progress)*
- [ ] Full end-to-end verification via frontend

Following along? The full build log — every design decision, every AWS concept learned, every real gap caught and fixed along the way — is in [`PROJECT_SPEC.md`](./PROJECT_SPEC.md).

---

## Getting Started (Local Development)

```bash
# Clone the repo
git clone https://github.com/szargo5329/url-shortener.git
cd url-shortener/backend

# Build and run tests
./gradlew clean test

# Build the Lambda-deployable artifact
./gradlew shadowJar
```

Infrastructure is managed via Terraform in `infrastructure/` — see [`PROJECT_SPEC.md`](./PROJECT_SPEC.md) Section 19 for the full setup and deployment process.

---

## License

MIT
