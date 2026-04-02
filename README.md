### problem statement

# 📘 EMI Scheduler & Personal Loan Management System

**Project Type:** Mini Project

**Version:** 1.1.0

**Date:** March 23, 2026

**Technology Stack:** Spring Boot 3.x / PostgreSQL

---

# 1. Introduction

## 1.1 Purpose

This document specifies the functional and non-functional requirements for the **EMI Scheduler & Personal Loan Management System**.

The system is designed to manage the complete loan lifecycle starting from:

- Application submission
- Final repayment

Including:

- Automated strategy suggestion engine
- EMI scheduling mechanism

---

## 1.2 Scope

The backend REST API is responsible for:

- User Authentication & Authorization (JWT-based)
- Loan Application Intake & Financial Analysis
- Automated Loan Strategy Suggestion
- Dynamic EMI Schedule Generation
- Payment Simulation
- Automated Notifications (Email-based)

---

# 2. System Roles & Responsibilities

The system follows a **two-role model** along with an **internal automated system engine**.

---

## 2.1 BORROWER

The end-user applying for loans.

### Capabilities

| Capability | Description |
| --- | --- |
| Register/Login | Create and authenticate account |
| Apply Loan | Submit financial and personal details |
| View Loans | Track loan status |
| View EMI Schedule | Access amortization table |
| Simulate Payments | Mark EMI as paid (simulation only) |

### Constraints

- Can access only their own data
- Maximum 3 active loans allowed

---

## 2.2 LOAN_OFFICER

Internal staff responsible for credit evaluation.

### Capabilities

| Capability | Description |
| --- | --- |
| View Applications | See all pending loan applications |
| Review Strategy | Analyze system-suggested strategy |
| Approve/Reject | Final decision authority |
| View Reports | Access overdue and loan summaries |

### Constraints

- Cannot modify global system configurations
- Cannot delete users

---

## 2.3 THE SYSTEM (Automated Engine)

An internal logical entity performing automated operations:

- Strategy Suggestion Engine
- EMI Schedule Generation
- Overdue Monitoring
- Email Notifications

---

# 3. Automated Strategy Suggestion Engine

The system determines loan strategy based on **Debt-to-Income (DTI) ratio** and tenure.

---

## Strategy Decision Table

| DTI Ratio | Requested Tenure | Suggested Strategy |
| --- | --- | --- |
| < 20% (Low Risk) | Any | FLAT_RATE_LOAN |
| 20% – 40% (Mid Risk) | < 24 Months | REDUCING_BALANCE_LOAN |
| 20% – 40% (Mid Risk) | ≥ 24 Months | STEP_UP_EMI_LOAN |
| > 40% (High Risk) | Any | REJECT |

---

### Key Notes

- Borrower cannot choose strategy manually
- Loan Officer may override system suggestion
- High-risk applications are flagged for rejection

---

# 4. Functional Requirements

## 4.1 Loan Lifecycle & Strategy Resolution

- **FR-1:** System shall calculate DTI ratio at application submission
- **FR-2:** System shall implement Strategy Pattern for EMI calculation:

| Strategy | Description |
| --- | --- |
| Flat Rate | Interest applied on initial principal |
| Reducing Balance | Interest on outstanding principal |
| Step-Up EMI | EMI increases by 5% annually |
- **FR-3:** Factory Pattern shall resolve strategy implementation based on officer selection

---

## 4.2 EMI Scheduling & Payments

- **FR-4:** Upon approval, system shall generate a complete amortization schedule including:
    - Installment Number
    - Due Date
    - Principal
    - Interest
    - Remaining Balance
- **FR-5:** Borrower shall be able to mark EMI as Paid (simulation)
- **FR-6:** A scheduled background job shall:
    - Scan overdue EMIs daily
    - Update status to Overdue

---

## 4.3 Notifications

- **FR-7:** System shall send automated HTML emails (Thymeleaf-based) for:

| Event | Trigger |
| --- | --- |
| Application Submission | On loan request |
| Approval/Rejection | After officer decision |
| Payment Reminder | 3 days before due date |
| Overdue Alert | When EMI crosses due date |

---

# 5. Non-Functional Requirements

| Requirement ID | Description |
| --- | --- |
| NFR-1 Security | All endpoints secured using stateless JWT authentication |
| NFR-2 Performance | EMI schedule generation for 120-month loan must complete within 100 ms |
| NFR-3 Auditability | Every state transition must log Officer ID and timestamp |
| NFR-4 Scalability | System follows layered + hexagonal architecture for microservice readiness |

---

# 6. API Endpoints

## Loan APIs

| Method | Endpoint | Description | Role |
| --- | --- | --- | --- |
| POST | /api/v1/loans/apply | Submit loan application | Borrower |
| GET | /api/v1/loans/{id}/schedule | View EMI schedule | Borrower / Officer |

---

## Officer APIs

| Method | Endpoint | Description | Role |
| --- | --- | --- | --- |
| GET | /api/v1/officer/applications | View applications with suggestions | Officer |
| PUT | /api/v1/officer/approve/{id} | Approve/reject loan | Officer |

---

# 7. Summary of System Behavior

- Borrower submits application → system calculates DTI
- System suggests strategy → Loan Officer reviews
- Officer approves/rejects (can override strategy)
- On approval → EMI schedule is generated
- System monitors payments → marks overdue automatically
- Notifications are sent at each lifecycle stage

---

© 2026 Swabhav Techlabs Pvt. Ltd. All rights reserved.
