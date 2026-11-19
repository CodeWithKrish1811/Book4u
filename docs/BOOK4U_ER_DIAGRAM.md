# Book4u — Complete Entity-Relationship (ER) Architecture

## 1. Executive Summary

This document defines the formal relational database architecture for **Book4u**, a university campus peer-to-peer textbook sharing platform.

The schema is normalized to **Third Normal Form (3NF)**, strictly enforces relational constraints, and encapsulates the state machines for the three supported sharing modes:
1. **SWAP** — Mutual bilateral book exchange with reciprocal ownership transfer.
2. **GIVEAWAY** — Altruistic book transfer with zero return or financial obligation.
3. **LENDING** — Time-bound custody with return tracking (`ACTIVE`, `OVERDUE`, `RETURNED`, `DISPUTED`).

---

## 2. Complete Mermaid ER Diagram

```mermaid
erDiagram
    STUDENTS ||--o{ BOOK_LISTINGS : "owns / curates (owner_id)"
    STUDENTS ||--o{ EXCHANGE_REQUESTS : "initiates (requester_id)"
    STUDENTS ||--o{ EXCHANGE_REQUESTS : "receives (owner_id)"
    BOOK_LISTINGS ||--o{ EXCHANGE_REQUESTS : "target of (listing_id)"
    BOOK_LISTINGS ||--o{ EXCHANGE_REQUESTS : "offered in swap (offered_book_id)"
    
    EXCHANGE_REQUESTS ||--|| EXCHANGES : "fulfilled as (request_id)"
    BOOK_LISTINGS ||--o{ EXCHANGES : "primary book (listing_id)"
    BOOK_LISTINGS ||--o{ EXCHANGES : "swap reciprocal book (offered_book_id)"
    STUDENTS ||--o{ EXCHANGES : "owner participant (owner_id)"
    STUDENTS ||--o{ EXCHANGES : "requester participant (requester_id)"
    
    BOOK_LISTINGS ||--o{ BOOK_PASSPORT_ENTRIES : "chronicles lineage (book_id)"
    EXCHANGES ||--o{ BOOK_PASSPORT_ENTRIES : "provenance source (exchange_id)"
    STUDENTS ||--o{ BOOK_PASSPORT_ENTRIES : "previous custodian (previous_holder_id)"
    STUDENTS ||--o{ BOOK_PASSPORT_ENTRIES : "new custodian (new_holder_id)"
    
    EXCHANGES ||--o{ STUDENT_REVIEWS : "evaluated in (exchange_id)"
    STUDENTS ||--o{ STUDENT_REVIEWS : "submits review (reviewer_id)"
    STUDENTS ||--o{ STUDENT_REVIEWS : "receives review (reviewee_id)"
    
    STUDENTS ||--o{ NOTIFICATIONS : "receives alert (recipient_id)"
    STUDENTS ||--o{ NOTIFICATIONS : "triggers alert (sender_id)"
    
    STUDENTS ||--o{ MODERATION_REPORTS : "files complaint (reporter_id)"
    STUDENTS ||--o{ MODERATION_REPORTS : "accused subject (reported_student_id)"
    BOOK_LISTINGS ||--o{ MODERATION_REPORTS : "flagged item (reported_listing_id)"
    EXCHANGES ||--o{ MODERATION_REPORTS : "disputed exchange (exchange_id)"

    STUDENTS {
        bigint id PK
        varchar university_id UK "Unique roll number"
        varchar full_name
        varchar email UK "Verified campus domain"
        varchar phone
        varchar password_hash "Salted BCrypt/Argon2"
        varchar department
        tinyint semester "1 to 12"
        varchar campus_name
        varchar profile_image_url
        decimal reputation_score "1.00 to 5.00"
        enum verification_status "PENDING, VERIFIED, REJECTED"
        enum account_status "ACTIVE, SUSPENDED, DEACTIVATED"
        timestamp created_at
        timestamp updated_at
    }

    BOOK_LISTINGS {
        bigint id PK
        bigint owner_id FK "References students(id)"
        varchar title
        varchar author
        varchar isbn
        varchar edition
        varchar course_code
        varchar subject
        varchar department
        tinyint semester "1 to 12"
        enum condition_grade "NEW, LIKE_NEW, GOOD, FAIR"
        enum sharing_mode "SWAP, GIVEAWAY, LENDING"
        enum status "AVAILABLE, RESERVED, ON_LOAN, UNAVAILABLE"
        text description
        varchar cover_image_url
        int lending_max_duration_days "Mandatory if LENDING"
        text swap_preferences "Desired books if SWAP"
        timestamp created_at
        timestamp updated_at
    }

    EXCHANGE_REQUESTS {
        bigint id PK
        bigint listing_id FK "References book_listings(id)"
        bigint requester_id FK "References students(id)"
        bigint owner_id FK "References students(id)"
        bigint offered_book_id FK "Nullable; mandatory if SWAP"
        enum exchange_mode "SWAP, GIVEAWAY, LENDING"
        int proposed_duration_days "Nullable; mandatory if LENDING"
        date expected_return_date "Nullable; mandatory if LENDING"
        text request_message
        enum status "PENDING, ACCEPTED, REJECTED, CANCELLED, EXPIRED"
        datetime handover_scheduled_at
        varchar handover_location
        timestamp created_at
        timestamp updated_at
    }

    EXCHANGES {
        bigint id PK
        bigint request_id UK "Unique FK to exchange_requests(id)"
        bigint listing_id FK "References book_listings(id)"
        enum exchange_mode "SWAP, GIVEAWAY, LENDING"
        bigint owner_id FK "References students(id)"
        bigint requester_id FK "References students(id)"
        bigint offered_book_id FK "Nullable FK to book_listings(id)"
        enum status "AWAITING_HANDOVER, ACTIVE, OVERDUE, RETURNED, COMPLETED, DISPUTED, CANCELLED"
        datetime accepted_at
        datetime handover_scheduled_at
        varchar handover_location
        datetime handover_completed_at
        date loan_start_date
        date due_date
        date actual_return_date
        boolean owner_handover_confirmed
        boolean requester_handover_confirmed
        boolean owner_return_confirmed
        boolean requester_return_confirmed
        timestamp created_at
        timestamp updated_at
    }

    BOOK_PASSPORT_ENTRIES {
        bigint id PK
        bigint book_id FK "References book_listings(id)"
        bigint exchange_id FK "References exchanges(id)"
        bigint previous_holder_id FK "References students(id)"
        bigint new_holder_id FK "References students(id)"
        enum exchange_mode "SWAP, GIVEAWAY, LENDING"
        varchar handover_campus_location
        text notes "Reader reflections & marginalia"
        timestamp recorded_at
    }

    STUDENT_REVIEWS {
        bigint id PK
        bigint exchange_id FK "References exchanges(id)"
        bigint reviewer_id FK "References students(id)"
        bigint reviewee_id FK "References students(id)"
        tinyint rating "1 to 5"
        text review_text
        timestamp created_at
    }

    NOTIFICATIONS {
        bigint id PK
        bigint recipient_id FK "References students(id)"
        bigint sender_id FK "References students(id)"
        enum notification_type "REQUEST_RECEIVED, REQUEST_ACCEPTED, etc."
        varchar title
        text message
        enum reference_type "EXCHANGE_REQUEST, EXCHANGE, etc."
        bigint reference_id
        boolean is_read
        timestamp created_at
    }

    MODERATION_REPORTS {
        bigint id PK
        bigint reporter_id FK "References students(id)"
        bigint reported_student_id FK "References students(id)"
        bigint reported_listing_id FK "References book_listings(id)"
        bigint exchange_id FK "References exchanges(id)"
        enum reason "INAPPROPRIATE_CONTENT, FAKE_LISTING, etc."
        text details
        enum status "PENDING, UNDER_REVIEW, RESOLVED, DISMISSED"
        text admin_notes
        datetime resolved_at
        timestamp created_at
        timestamp updated_at
    }
```

---

## 3. Relationship Cardinalities & Business Invariants

| Parent Table | Child Table | Foreign Key Column | Cardinality | Business Invariant & Delete Rule |
| :--- | :--- | :--- | :--- | :--- |
| `students` | `book_listings` | `owner_id` | **1 to N** | `ON DELETE RESTRICT`. A student account cannot be deleted if active listings exist. Deactivation (`account_status = 'DEACTIVATED'`) is used instead. |
| `book_listings`| `exchange_requests`| `listing_id` | **1 to N** | `ON DELETE CASCADE`. Unfulfilled negotiation requests are purged if an un-exchanged listing is deleted. |
| `students` | `exchange_requests`| `requester_id` | **1 to N** | `ON DELETE CASCADE`. Pending requests belong to the requesting student. |
| `students` | `exchange_requests`| `owner_id` | **1 to N** | `ON DELETE CASCADE`. |
| `exchange_requests`| `exchanges` | `request_id` | **1 to 1** | `ON DELETE RESTRICT`. Exactly one exchange contract per accepted request. Enforced by `UNIQUE (request_id)`. |
| `book_listings`| `exchanges` | `listing_id` | **1 to N** | `ON DELETE RESTRICT`. Preserves transaction history. |
| `students` | `exchanges` | `owner_id`, `requester_id` | **1 to N** | `ON DELETE RESTRICT`. Completed transactions must persist for campus audit records. |
| `book_listings`| `book_passport_entries`| `book_id` | **1 to N** | `ON DELETE CASCADE`. A book's journey is tracked chronologically. |
| `exchanges` | `book_passport_entries`| `exchange_id` | **1 to N** | `ON DELETE SET NULL`. Preserves book journey entries even if exchange records are archived. |
| `exchanges` | `student_reviews` | `exchange_id` | **1 to 2** | `ON DELETE CASCADE`. At most 2 reviews per exchange (one from each student participant), enforced by `UNIQUE (exchange_id, reviewer_id)`. |
| `students` | `notifications` | `recipient_id` | **1 to N** | `ON DELETE CASCADE`. |
| `students` | `moderation_reports`| `reporter_id` | **1 to N** | `ON DELETE RESTRICT`. Moderation complaints cannot be deleted by student action. |

---

## 4. Indexing Strategy Matrix

| Table | Index Name | Columns Indexed | Query Target / Optimization |
| :--- | :--- | :--- | :--- |
| `students` | `idx_students_email` | `(email)` | Fast authentication lookup |
| `students` | `idx_students_dept_verif` | `(department, verification_status)` | Campus directory & moderation filters |
| `book_listings` | `idx_books_owner` | `(owner_id)` | "My Books" profile view |
| `book_listings` | `idx_books_title` | `(title)` | Search autocomplete & title queries |
| `book_listings` | `idx_books_subject` | `(subject)` | Browse by academic subject area |
| `book_listings` | `idx_books_dept_sem` | `(department, semester)` | Semester syllabus book discovery |
| `book_listings` | `idx_books_mode_status`| `(sharing_mode, status)` | Filter marketplace by SWAP / GIVEAWAY / LENDING and AVAILABLE |
| `book_listings` | `idx_books_course` | `(course_code)` | Fast lookup by campus course code (e.g., `CS201`) |
| `exchange_requests` | `idx_requests_listing_status`| `(listing_id, status)` | Incoming requests per book |
| `exchange_requests` | `idx_requests_requester_status`| `(requester_id, status)` | Student's active outgoing requests |
| `exchange_requests` | `idx_requests_owner_status` | `(owner_id, status)` | Student's pending incoming requests |
| `exchange_requests` | `idx_requests_duplicate_check` | `(listing_id, requester_id, status)` | Instant duplicate request prevention |
| `exchanges` | `idx_exchanges_participants_status`| `(owner_id, requester_id, status)` | User exchange overview dashboard |
| `exchanges` | `idx_exchanges_due_date`| `(due_date)` | Daily cron job checking for overdue loans |
| `book_passport_entries`| `idx_passport_book_date`| `(book_id, recorded_at DESC)`| Book Passport public reading journey timeline |
| `student_reviews` | `idx_reviews_reviewee_rating`| `(reviewee_id, rating)` | Student reputation score aggregation |
| `notifications` | `idx_notifications_recipient_read`| `(recipient_id, is_read, created_at DESC)` | Student in-app notification dropdown / inbox |
| `moderation_reports` | `idx_reports_status` | `(status, created_at DESC)` | Admin dispute review queue |

---

## 5. Database Normalization (3NF)

1. **First Normal Form (1NF):**
   - All columns contain atomic, scalar values (no array columns, no delimited strings).
   - Each table has a unique primary key (`id` BIGINT AUTO_INCREMENT).
2. **Second Normal Form (2NF):**
   - Every non-key attribute is fully functionally dependent on the primary key.
   - Composite keys are eliminated in favor of surrogate primary keys with composite uniqueness constraints.
3. **Third Normal Form (3NF):**
   - Transitive dependencies are removed.
   - For example: Listing status and exchange status are not stored redundantly in `students`; reviews point directly to `exchanges(id)` rather than storing redundant book details; passport entries reference `book_id` and `exchange_id` without duplicating book titles or student names.
