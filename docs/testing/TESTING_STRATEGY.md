# Study Snap Testing Strategy

This document defines the testing approach for Study Snap.

The goal is to ensure that core learning logic remains stable while keeping tests maintainable and fast.

Study Snap follows a layered testing approach based on the testing pyramid.

---

# Testing Pyramid

Study Snap uses the following testing structure:

1. Unit Tests
2. Repository Tests
3. Service Integration Tests
4. Controller Tests
5. End-to-End Tests (optional in early stages)

Most tests should be unit tests.

---

# Unit Tests

Unit tests validate isolated logic without starting the Spring context.

They should cover:

* Smart Continue Studying recommendation logic
* Quick Review scoring
* retry logic
* resume session logic
* prompt builders
* activity tracking behavior

Unit tests should:

* run quickly
* avoid database usage
* mock external dependencies

Example test targets:

DashboardRecommendationService
QuickReviewService
PromptBuilderService
ActivityService

---

# Repository Tests

Repository tests verify JPA query behavior.

These tests should use an in-memory database such as H2.

Example targets:

QuickReviewSessionRepository
StudyPackRepository

Tests should verify:

* ordering
* filtering
* correct session retrieval
* pagination behavior

---

# Service Integration Tests

Service integration tests run with Spring Boot context and database.

They verify full flows such as:

Quick Review flow:
Start session → answer questions → complete session → score saved

Smart Continue Studying:
user activity → recommendation generated

These tests ensure that service interactions behave correctly.

---

# Controller Tests

Controller tests validate REST endpoints.

Use @WebMvcTest where possible.

Example endpoints:

GET /api/dashboard/continue-studying
POST /api/quick-review-sessions/start
POST /api/quick-review-sessions/{id}/complete

These tests verify:

* status codes
* response structure
* request validation

---

# End-to-End Tests (Future)

Optional E2E tests can be added later using tools such as:

Playwright
Cypress

Example E2E flows:

User signup
Generate Study Pack
Start Quick Review
Finish Quick Review
Dashboard recommendation

---

# Testing Principles

Tests should:

* verify behavior, not implementation
* be deterministic
* avoid excessive mocking
* focus on core learning logic

Avoid testing trivial getters or simple DTOs.

---

# Priority Test Coverage

Highest priority:

Smart Continue Studying
Quick Review scoring
retry logic
resume session behavior

Medium priority:

Repository queries
Activity logging

Lower priority:

Controller endpoints
