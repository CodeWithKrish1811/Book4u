# Book4u — Database Design Document

## 1. Relational Database Overview

- **Database Engine:** MySQL 8.x
- **Storage Engine:** InnoDB (ACID compliant, foreign key enforcement)
- **Character Set:** `utf8mb4` with `utf8mb4_unicode_ci` (full Unicode and emoji support for student notes)
- **Data Access Layer:** Spring JDBC (`JdbcTemplate`, `NamedParameterJdbcTemplate`) with parameterized SQL
- **Connection Pool:** HikariCP

---

## 2. Entity-Relationship Model

```mermaid
erDiagram
    STUDENTS ||--o{ BOOK_LISTINGS : "owns"
    STUDENTS ||--o{ EXCHANGE_REQUESTS : "initiates (requester)"
    STUDENTS ||--o{ EXCHANGE_REQUESTS : "receives (owner)"
    BOOK_LISTINGS ||--o{ EXCHANGE_REQUESTS : "subject of"
    BOOK_LISTINGS ||--o{ BOOK_PASSPORT_ENTRIES : "chronicles"
    EXCHANGE_REQUESTS ||--o{ BOOK_PASSPORT_ENTRIES : "triggers"
    STUDENTS ||--o{ STUDENT_REVIEWS : "authors"
    STUDENTS ||--o{ STUDENT_REVIEWS : "subject of"
    EXCHANGE_REQUESTS ||--o{ STUDENT_REVIEWS : "reviewed in"

    STUDENTS {
        bigint id PK
        varchar university_id UK
        varchar email UK
        varchar password_hash
        varchar full_name
        varchar phone
        varchar department
        varchar campus_name
        decimal reputation_score
        enum status
        timestamp created_at
        timestamp updated_at
    }

    BOOK_LISTINGS {
        bigint id PK
        bigint owner_id FK
        varchar title
        varchar author
        varchar isbn
        varchar edition
        varchar course_code
        varchar department
        enum condition_grade
        enum sharing_mode
        enum status
        text swap_preferences
        int lending_max_duration_days
        text description
        varchar cover_image_url
        timestamp created_at
        timestamp updated_at
    }

    EXCHANGE_REQUESTS {
        bigint id PK
        bigint listing_id FK
        bigint requester_id FK
        bigint owner_id FK
        enum exchange_mode
        bigint offered_book_id FK
        int proposed_duration_days
        date expected_return_date
        date actual_return_date
        text request_message
        enum status
        datetime handover_scheduled_at
        varchar handover_location
        timestamp created_at
        timestamp updated_at
    }

    BOOK_PASSPORT_ENTRIES {
        bigint id PK
        bigint book_id FK
        bigint exchange_request_id FK
        bigint previous_holder_id FK
        bigint new_holder_id FK
        enum exchange_mode
        varchar handover_campus_location
        text notes
        timestamp recorded_at
    }

    STUDENT_REVIEWS {
        bigint id PK
        bigint exchange_request_id FK
        bigint reviewer_id FK
        bigint reviewee_id FK
        tinyint rating
        text review_text
        timestamp created_at
    }
```

---

## 3. Normalization & Integrity Guarantees

1. **Third Normal Form (3NF):** Every non-key attribute depends strictly on the primary key, avoiding transitive dependencies.
2. **Strict Mode Invariance:**
   - Books listed under `SWAP` can specify `swap_preferences`.
   - Books listed under `LENDING` define `lending_max_duration_days`.
   - Books listed under `GIVEAWAY` require neither.
   - Absolutely no price, currency, or payment columns exist in the database, preserving the student peer-to-peer non-commercial design.
3. **Foreign Key Cascades & Safety:**
   - Deleting a listing cascades to its active requests and passport links.
   - Offered books in swap requests utilize `ON DELETE SET NULL` to avoid cascading data loss on reciprocal listings.

---

## 4. Indexing Strategy

To ensure sub-millisecond query latency across thousands of campus listings:

| Table | Index Name | Columns | Purpose |
| :--- | :--- | :--- | :--- |
| `students` | `idx_students_email` | `(email)` | Fast authentication lookup |
| `students` | `idx_students_dept` | `(department)` | Filtering students by faculty/dept |
| `book_listings` | `idx_books_owner` | `(owner_id)` | "My Listings" user profile query |
| `book_listings` | `idx_books_sharing_mode` | `(sharing_mode)` | Filtering by SWAP / GIVEAWAY / LENDING |
| `book_listings` | `idx_books_status` | `(status)` | Excludes EXCHANGED/ARCHIVED books from discovery |
| `book_listings` | `idx_books_course` | `(course_code)` | Fast lookup by campus course (e.g. `CS101`) |
| `book_listings` | `idx_books_title` | `(title)` | Search autocomplete & matching |
| `exchange_requests` | `idx_requests_listing` | `(listing_id)` | Request list per book |
| `exchange_requests` | `idx_requests_requester`| `(requester_id)`| "My Outgoing Requests" query |
| `exchange_requests` | `idx_requests_owner` | `(owner_id)` | "My Incoming Requests" query |

---

## 5. Parameterized SQL & Spring JDBC Execution

All database interactions will be implemented using Spring's `JdbcTemplate` and parameterized queries:

```java
// Example: Safe parameterized search query without SQL injection risk
String sql = """
    SELECT id, owner_id, title, author, course_code, sharing_mode, condition_grade, status 
    FROM book_listings 
    WHERE status = 'AVAILABLE' 
      AND sharing_mode = ? 
      AND department = ?
    ORDER BY created_at DESC
""";

List<BookListing> listings = jdbcTemplate.query(
    sql,
    new BookListingRowMapper(),
    sharingMode.name(),
    department
);
```
