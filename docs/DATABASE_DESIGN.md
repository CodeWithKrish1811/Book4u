# Book4u — Complete Database Design & Architecture

## 1. Architectural Overview

- **Database Engine:** MySQL 8.x
- **Storage Engine:** InnoDB (ACID compliant, transactions, foreign key enforcement, row-level locking)
- **Character Set & Collation:** `utf8mb4` with `utf8mb4_unicode_ci` (Full multilingual and Unicode character support)
- **Data Access Layer:** Spring JDBC (`JdbcTemplate`, `NamedParameterJdbcTemplate`) with parameterized SQL
- **Connection Pool:** HikariCP
- **Zero ORM Policy:** Pure relational JDBC architecture. Strictly no Hibernate, JPA, or Spring Data JPA.

---

## 2. Core Business Workflows

Book4u is built exclusively for university students with **zero commercial transactions, payments, or buying/selling**. It strictly governs three sharing modes:

1. **SWAP:** Bilateral trade. Student A offers Book X, Student B offers Book Y. After physical campus handover is dually confirmed, ownership records for both books are swapped, and passport chronicles are generated for both copies.
2. **GIVEAWAY:** Altruistic transfer. Student A passes a textbook to Student B for free. Ownership transfers upon dual handover confirmation.
3. **LENDING:** Temporary loan. Student A lends Book X to Student B for an agreed period (`lending_max_duration_days`). Student A retains legal ownership. The book status changes from `AVAILABLE` to `RESERVED`, then to `ON_LOAN`. Once returned, it transitions back to `AVAILABLE`.

---

## 3. Database Schema Dictionary (8 Core Tables)

### Table 1: `students`
Stores verified university student accounts participating in peer book exchange.
- `id` (BIGINT, PK, AUTO_INCREMENT)
- `university_id` (VARCHAR(64), UNIQUE, NOT NULL): Campus roll number or institutional student badge ID.
- `full_name` (VARCHAR(128), NOT NULL): Legal / campus name.
- `email` (VARCHAR(255), UNIQUE, NOT NULL): Institutional email address (`@campus.edu`).
- `phone` (VARCHAR(20), NULL): Contact number for physical campus handover coordination.
- `password_hash` (VARCHAR(255), NOT NULL): Salted BCrypt/Argon2 hash. Never plaintext.
- `department` (VARCHAR(128), NOT NULL): Faculty or department (e.g. Computer Science, Mechanical).
- `semester` (TINYINT UNSIGNED, NOT NULL, DEFAULT 1): Current semester (1 to 12).
- `campus_name` (VARCHAR(128), NOT NULL, DEFAULT 'Main Campus'): Branch campus.
- `profile_image_url` (VARCHAR(512), NULL): Avatar image URL.
- `reputation_score` (DECIMAL(3,2), NOT NULL, DEFAULT 5.00): Aggregated student rating (1.00 to 5.00).
- `verification_status` (ENUM('PENDING', 'VERIFIED', 'REJECTED'), NOT NULL, DEFAULT 'PENDING').
- `account_status` (ENUM('ACTIVE', 'SUSPENDED', 'DEACTIVATED'), NOT NULL, DEFAULT 'ACTIVE').
- `created_at` / `updated_at` (TIMESTAMP).

### Table 2: `book_listings`
Academic textbooks cataloged by students for sharing.
- `id` (BIGINT, PK, AUTO_INCREMENT)
- `owner_id` (BIGINT, FK to `students.id`, ON DELETE RESTRICT)
- `title` (VARCHAR(255), NOT NULL): Full title.
- `author` (VARCHAR(255), NOT NULL): Author name(s).
- `isbn` (VARCHAR(32), NULL): ISBN-10 or ISBN-13.
- `edition` (VARCHAR(32), NULL): Edition string.
- `course_code` (VARCHAR(32), NULL): Curriculum course code (e.g., `CS201`).
- `subject` (VARCHAR(128), NOT NULL): Academic subject area (e.g., Data Structures, DBMS).
- `department` (VARCHAR(128), NOT NULL): Academic department.
- `semester` (TINYINT UNSIGNED, NOT NULL, DEFAULT 1): Syllabus semester (1 to 12).
- `condition_grade` (ENUM('NEW', 'LIKE_NEW', 'GOOD', 'FAIR'), NOT NULL).
- `sharing_mode` (ENUM('SWAP', 'GIVEAWAY', 'LENDING'), NOT NULL).
- `status` (ENUM('AVAILABLE', 'RESERVED', 'ON_LOAN', 'UNAVAILABLE'), NOT NULL, DEFAULT 'AVAILABLE').
- `description` (TEXT, NULL): Condition, notes, syllabus coverage.
- `cover_image_url` (VARCHAR(512), NULL): Book photo.
- `lending_max_duration_days` (INT UNSIGNED, NULL): Max lending days (validated if LENDING).
- `swap_preferences` (TEXT, NULL): Target books sought in exchange (for SWAP).
- `created_at` / `updated_at` (TIMESTAMP).

### Table 3: `exchange_requests`
Formal student requests submitted to initiate a SWAP, GIVEAWAY, or LENDING interaction.
- `id` (BIGINT, PK, AUTO_INCREMENT)
- `listing_id` (BIGINT, FK to `book_listings.id`, ON DELETE CASCADE)
- `requester_id` (BIGINT, FK to `students.id`, ON DELETE CASCADE)
- `owner_id` (BIGINT, FK to `students.id`, ON DELETE CASCADE)
- `offered_book_id` (BIGINT, FK to `book_listings.id`, NULL, ON DELETE SET NULL)
- `exchange_mode` (ENUM('SWAP', 'GIVEAWAY', 'LENDING'), NOT NULL)
- `proposed_duration_days` (INT UNSIGNED, NULL)
- `expected_return_date` (DATE, NULL)
- `request_message` (TEXT, NULL)
- `status` (ENUM('PENDING', 'ACCEPTED', 'REJECTED', 'CANCELLED', 'EXPIRED'), NOT NULL, DEFAULT 'PENDING')
- `handover_scheduled_at` (DATETIME, NULL)
- `handover_location` (VARCHAR(255), NULL)
- `created_at` / `updated_at` (TIMESTAMP).

### Table 4: `exchanges`
Accepted exchange agreements tracking physical campus handovers and loan lifecycle.
- `id` (BIGINT, PK, AUTO_INCREMENT)
- `request_id` (BIGINT, UNIQUE, FK to `exchange_requests.id`, ON DELETE RESTRICT)
- `listing_id` (BIGINT, FK to `book_listings.id`, ON DELETE RESTRICT)
- `exchange_mode` (ENUM('SWAP', 'GIVEAWAY', 'LENDING'), NOT NULL)
- `owner_id` (BIGINT, FK to `students.id`, ON DELETE RESTRICT)
- `requester_id` (BIGINT, FK to `students.id`, ON DELETE RESTRICT)
- `offered_book_id` (BIGINT, NULL, FK to `book_listings.id`, ON DELETE RESTRICT)
- `status` (ENUM('AWAITING_HANDOVER', 'ACTIVE', 'OVERDUE', 'RETURNED', 'COMPLETED', 'DISPUTED', 'CANCELLED'), NOT NULL)
- `accepted_at` (DATETIME, NOT NULL, DEFAULT CURRENT_TIMESTAMP)
- `handover_scheduled_at` (DATETIME, NULL)
- `handover_location` (VARCHAR(255), NULL)
- `handover_completed_at` (DATETIME, NULL)
- `loan_start_date` (DATE, NULL)
- `due_date` (DATE, NULL)
- `actual_return_date` (DATE, NULL)
- `owner_handover_confirmed` (BOOLEAN, NOT NULL, DEFAULT FALSE)
- `requester_handover_confirmed` (BOOLEAN, NOT NULL, DEFAULT FALSE)
- `owner_return_confirmed` (BOOLEAN, NOT NULL, DEFAULT FALSE)
- `requester_return_confirmed` (BOOLEAN, NOT NULL, DEFAULT FALSE)
- `created_at` / `updated_at` (TIMESTAMP).

### Table 5: `book_passport_entries`
Immutable custody and journey chronicles for textbooks across student generations.
- `id` (BIGINT, PK, AUTO_INCREMENT)
- `book_id` (BIGINT, FK to `book_listings.id`, ON DELETE CASCADE)
- `exchange_id` (BIGINT, NULL, FK to `exchanges.id`, ON DELETE SET NULL)
- `previous_holder_id` (BIGINT, FK to `students.id`, ON DELETE RESTRICT)
- `new_holder_id` (BIGINT, FK to `students.id`, ON DELETE RESTRICT)
- `exchange_mode` (ENUM('SWAP', 'GIVEAWAY', 'LENDING'), NOT NULL)
- `handover_campus_location` (VARCHAR(255), NULL)
- `notes` (TEXT, NULL): Reader advice, marginalia notes, impressions (no PII).
- `recorded_at` (TIMESTAMP, NOT NULL, DEFAULT CURRENT_TIMESTAMP).

### Table 6: `student_reviews`
Mutual reputation ratings submitted post-handover or post-return.
- `id` (BIGINT, PK, AUTO_INCREMENT)
- `exchange_id` (BIGINT, FK to `exchanges.id`, ON DELETE CASCADE)
- `reviewer_id` (BIGINT, FK to `students.id`, ON DELETE RESTRICT)
- `reviewee_id` (BIGINT, FK to `students.id`, ON DELETE RESTRICT)
- `rating` (TINYINT UNSIGNED, NOT NULL, CHECK between 1 and 5)
- `review_text` (TEXT, NULL)
- `created_at` (TIMESTAMP).
- **Constraint:** `UNIQUE (exchange_id, reviewer_id)` guarantees each participant reviews at most once per transaction.

### Table 7: `notifications`
Real-time student alerts for request updates, handover schedules, and due dates.
- `id` (BIGINT, PK, AUTO_INCREMENT)
- `recipient_id` (BIGINT, FK to `students.id`, ON DELETE CASCADE)
- `sender_id` (BIGINT, NULL, FK to `students.id`, ON DELETE SET NULL)
- `notification_type` (ENUM)
- `title` (VARCHAR(255), NOT NULL)
- `message` (TEXT, NOT NULL)
- `reference_type` (ENUM, DEFAULT 'NONE')
- `reference_id` (BIGINT, NULL)
- `is_read` (BOOLEAN, NOT NULL, DEFAULT FALSE)
- `created_at` (TIMESTAMP).

### Table 8: `moderation_reports`
Community governance and dispute resolution for damaged or unreturned books.
- `id` (BIGINT, PK, AUTO_INCREMENT)
- `reporter_id` (BIGINT, FK to `students.id`, ON DELETE RESTRICT)
- `reported_student_id` (BIGINT, NULL, FK to `students.id`, ON DELETE SET NULL)
- `reported_listing_id` (BIGINT, NULL, FK to `book_listings.id`, ON DELETE SET NULL)
- `exchange_id` (BIGINT, NULL, FK to `exchanges.id`, ON DELETE SET NULL)
- `reason` (ENUM)
- `details` (TEXT, NOT NULL)
- `status` (ENUM('PENDING', 'UNDER_REVIEW', 'RESOLVED', 'DISMISSED'), NOT NULL, DEFAULT 'PENDING')
- `admin_notes` (TEXT, NULL)
- `resolved_at` (DATETIME, NULL)
- `created_at` / `updated_at` (TIMESTAMP).

---

## 4. Transactional Integrity & Concurrency Control

In a multi-user campus environment, race conditions and integrity violations must be rigorously prevented. Below is the technical specification of how the Book4u database and Spring JDBC services enforce data guarantees:

### 4.1 Preventing Simultaneous Double-Acceptance of Books
**Scenario:** Two students simultaneously request the same `AVAILABLE` book, and the owner attempts to accept both requests concurrently, or two requests race to reservation.
**Resolution Strategy:**
- **Pessimistic Row-Level Locking:** Spring JDBC executes `SELECT ... FOR UPDATE` on the book listing row inside an isolated transaction.
- **Atomic State Transition:**
  ```sql
  -- Step 1: Lock the target book row
  SELECT id, status FROM book_listings WHERE id = ? FOR UPDATE;
  
  -- Step 2: Validate book status is still 'AVAILABLE'
  -- Step 3: Transition status atomically
  UPDATE book_listings SET status = 'RESERVED' WHERE id = ? AND status = 'AVAILABLE';
  ```
- **Affected Rows Verification:** The repository verifies `int affected = jdbcTemplate.update(...)`. If `affected == 0`, a concurrent transaction has already claimed the book; the service immediately aborts and throws `ListingAlreadyReservedException`, triggering an automatic transaction rollback.

### 4.2 Preventing a Student from Offering a Book They Do Not Own (SWAP)
**Scenario:** A student attempts to submit a SWAP request offering an arbitrary `offered_book_id` they do not possess.
**Resolution Strategy:**
- Validated via parameterized SQL join prior to request insertion:
  ```sql
  SELECT COUNT(*) FROM book_listings 
  WHERE id = ? AND owner_id = ? AND status = 'AVAILABLE';
  ```
- If the count is 0, the request is rejected immediately with `InvalidOfferedBookException`.
- Database Foreign Key `fk_requests_offered_book` ensures `offered_book_id` is a valid listing.

### 4.3 Preventing a Book from Being Transferred Twice
**Scenario:** In SWAP or GIVEAWAY, ownership transfer is initiated multiple times.
**Resolution Strategy:**
- An accepted request creates **exactly one** record in `exchanges` enforced by `UNIQUE (request_id)`.
- Physical handover completion requires **both** `owner_handover_confirmed = TRUE` AND `requester_handover_confirmed = TRUE`.
- The ownership transfer runs in a single atomic transaction:
  ```sql
  -- Transfer primary book ownership
  UPDATE book_listings SET owner_id = ?, status = 'AVAILABLE' WHERE id = ?;
  
  -- If SWAP, transfer reciprocal book ownership
  UPDATE book_listings SET owner_id = ?, status = 'AVAILABLE' WHERE id = ?;
  
  -- Mark exchange completed
  UPDATE exchanges SET status = 'COMPLETED', handover_completed_at = NOW() WHERE id = ?;
  
  -- Generate Passport Entries
  INSERT INTO book_passport_entries (book_id, exchange_id, previous_holder_id, new_holder_id, exchange_mode, handover_campus_location, notes)
  VALUES (?, ?, ?, ?, ?, ?, ?);
  ```

### 4.4 Preventing Invalid Lending Return Dates
**Scenario:** A borrower attempts to propose a lending period with a return date in the past, or past the owner's allowed maximum duration.
**Resolution Strategy:**
- Database CHECK constraint: `chk_exchanges_due_date CHECK (due_date IS NULL OR due_date >= loan_start_date)`.
- Database CHECK constraint: `chk_requests_lending CHECK (exchange_mode != 'LENDING' OR (proposed_duration_days IS NOT NULL AND proposed_duration_days > 0 AND expected_return_date IS NOT NULL))`.
- Service layer validates: `proposed_duration_days <= lending_max_duration_days`.

### 4.5 Preventing Duplicate Reviews
**Scenario:** A user repeatedly submits reviews for the same transaction to artificially inflate or deflate ratings.
**Resolution Strategy:**
- Database UNIQUE constraint: `CONSTRAINT uk_reviews_exchange_reviewer UNIQUE (exchange_id, reviewer_id)`.
- Any subsequent insert attempts fail immediately with a MySQL duplicate key error (SQLState 23000), caught by Spring JDBC as `DuplicateKeyException` and mapped by `GlobalExceptionHandler` to HTTP 409 Conflict.
- Database CHECK constraint: `CONSTRAINT chk_reviews_not_self CHECK (reviewer_id != reviewee_id)` prevents reviewing oneself.

### 4.6 Ensuring Lent Books Cannot Become Available Before Return
**Scenario:** A book on loan is accidentally re-listed or requested.
**Resolution Strategy:**
- When lending handover is confirmed, the listing status is transitioned to `ON_LOAN`.
- Search and request creation filters strictly require `status = 'AVAILABLE'`.
- Status transitions back to `AVAILABLE` **only** upon dual return confirmation (`owner_return_confirmed = TRUE` AND `requester_return_confirmed = TRUE`), triggering `actual_return_date = CURDATE()` and `status = 'RETURNED'`.

---

## 5. Spring JDBC Transactional Architecture Specification

```java
@Service
public class ExchangeFulfillmentService {

    private final JdbcTemplate jdbcTemplate;

    // Declarative transaction boundary with strict rollback
    @Transactional(rollbackFor = Exception.class, isolation = Isolation.READ_COMMITTED)
    public void confirmHandover(Long exchangeId, Long studentId) {
        // 1. Lock exchange record
        Exchange exchange = lockExchangeForUpdate(exchangeId);
        
        // 2. Register participant confirmation
        // 3. When dual confirmation is complete:
        //    a. Update listing statuses / owners
        //    b. Record passport chronicles
        //    c. Issue student notifications
    }
}
```
