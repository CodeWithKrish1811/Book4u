# Book4u — Project Overview

## 1. Executive Summary

**Book4u** is a university campus peer-to-peer book exchange platform designed to solve the challenge of textbook affordability and physical waste in higher education. University students spend substantial sums each semester purchasing academic textbooks that sit idle after final exams. 

Book4u provides a trusted, hyper-local, campus-exclusive platform where university students can discover, exchange, give away, or temporarily lend textbooks directly to their peers.

> **Tagline:** *Your Books. Your Campus. Your Community.*

---

## 2. Core Philosophy & Value Proposition

1. **Academic Sustainability:** Extends the lifecycle of physical academic books across student generations.
2. **Financial Relief:** Eliminates redundant semester expenditures by facilitating zero-cost book sharing.
3. **Hyper-Local Trust:** Restricts exchange to verified campus members, enabling direct, safe, in-person campus handovers (e.g., Central Library, Student Center).
4. **Pedagogical Lineage (The "Book Passport"):** Celebrates the journey of every book through reader notes, annotations, and batch memories passed between seniors and juniors.

---

## 3. Supported Book Sharing Modes

Book4u is intentionally engineered **without commercial buying or selling transactions** in its foundational release. It exclusively supports three sharing mechanisms:

| Mode | Identifier | Description | Financial Transaction |
| :--- | :--- | :--- | :--- |
| **Swap** | `SWAP` | Direct bilateral book exchange. Student A offers Book X in exchange for Book Y from Student B. | **None ($0)** |
| **Giveaway** | `GIVEAWAY` | Altruistic gifting. Senior or graduating students pass books down to juniors with no return expectation. | **None ($0)** |
| **Lending** | `LENDING` | Temporary loan for a defined duration (e.g., 14, 30, or 60 days) with an agreed return calendar date. | **None ($0)** |

---

## 4. Key Stakeholders

- **Student Lenders / Owners:** Students listing books they currently hold for swapping, lending, or giving away.
- **Student Borrowers / Requesters:** Students searching for curriculum-aligned materials for their current semester courses.
- **Campus Administrators / Moderation:** Campus representatives overseeing verification, community standards, and dispute resolution.

---

## 5. Scope & Future Evolution

### Phase 1 (Current)
- Monorepo foundation with React (Vite) and Spring Boot 3.x (Java 21).
- Native Spring JDBC architecture without ORM overhead.
- Environment isolation and security hygiene.
- Baseline health and connectivity diagnostics.

### Upcoming Phases
- Campus email/domain-based authentication and student identity verification.
- Book catalog management with course code matching and department filtering.
- State-machine driven exchange request lifecycles.
- Smart Match algorithm suggesting optimal multi-way or direct book swaps.
- Digital "Book Passport" tracking the provenance and reader history of each physical copy.
- Student reputation scoring and campus handover coordination.
