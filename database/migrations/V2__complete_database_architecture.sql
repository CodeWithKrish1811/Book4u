-- =====================================================================
-- Book4u - Database Migration V2: Complete Relational Architecture
-- Description: Non-destructive migration script upgrading V1 schema to V2.
-- Preserves existing data, adds required tables, columns, constraints and indexes.
-- Technology: MySQL 8.x (InnoDB, utf8mb4)
-- =====================================================================

USE book4u_db;

-- ---------------------------------------------------------------------
-- 1. Upgrade Table: students
-- ---------------------------------------------------------------------
-- Add new columns safely
ALTER TABLE students
    ADD COLUMN IF NOT EXISTS semester TINYINT UNSIGNED NOT NULL DEFAULT 1 COMMENT 'Academic semester (1-12)' AFTER department,
    ADD COLUMN IF NOT EXISTS profile_image_url VARCHAR(512) NULL COMMENT 'Student profile avatar URL' AFTER campus_name,
    ADD COLUMN IF NOT EXISTS verification_status ENUM('PENDING', 'VERIFIED', 'REJECTED') NOT NULL DEFAULT 'PENDING' COMMENT 'Campus ID verification standing' AFTER reputation_score,
    ADD COLUMN IF NOT EXISTS account_status ENUM('ACTIVE', 'SUSPENDED', 'DEACTIVATED') NOT NULL DEFAULT 'ACTIVE' COMMENT 'Account standing' AFTER verification_status;

-- Migrate existing data from legacy status column if it exists
SET @legacy_status_exists = (
    SELECT COUNT(*) 
    FROM INFORMATION_SCHEMA.COLUMNS 
    WHERE TABLE_SCHEMA = DATABASE() 
      AND TABLE_NAME = 'students' 
      AND COLUMN_NAME = 'status'
);

-- Safely sync status values if legacy column was present
UPDATE students 
SET 
    verification_status = CASE 
        WHEN status = 'PENDING_VERIFICATION' THEN 'PENDING'
        ELSE 'VERIFIED'
    END,
    account_status = CASE 
        WHEN status = 'SUSPENDED' THEN 'SUSPENDED'
        ELSE 'ACTIVE'
    END
WHERE @legacy_status_exists > 0;

-- Drop legacy column only if migration was completed
SET @drop_legacy_sql = IF(@legacy_status_exists > 0, 'ALTER TABLE students DROP COLUMN status;', 'SELECT 1;');
PREPARE stmt FROM @drop_legacy_sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- Add check constraints and indexes on students
ALTER TABLE students
    ADD CONSTRAINT chk_students_semester CHECK (semester BETWEEN 1 AND 12),
    ADD CONSTRAINT chk_students_reputation CHECK (reputation_score BETWEEN 1.00 AND 5.00);

CREATE INDEX IF NOT EXISTS idx_students_dept_verif ON students (department, verification_status);
CREATE INDEX IF NOT EXISTS idx_students_campus ON students (campus_name);
CREATE INDEX IF NOT EXISTS idx_students_account_status ON students (account_status);

-- ---------------------------------------------------------------------
-- 2. Upgrade Table: book_listings
-- ---------------------------------------------------------------------
ALTER TABLE book_listings
    ADD COLUMN IF NOT EXISTS subject VARCHAR(128) NOT NULL DEFAULT 'General Academic' COMMENT 'Academic subject area' AFTER course_code,
    ADD COLUMN IF NOT EXISTS semester TINYINT UNSIGNED NOT NULL DEFAULT 1 COMMENT 'Target syllabus semester (1-12)' AFTER department;

-- Update status enum cleanly: map legacy values to standardized statuses
ALTER TABLE book_listings
    MODIFY COLUMN status ENUM('AVAILABLE', 'RESERVED', 'ON_LOAN', 'UNAVAILABLE') NOT NULL DEFAULT 'AVAILABLE' COMMENT 'Listing lifecycle';

-- Adjust foreign key delete rule to RESTRICT to preserve transaction lineage
ALTER TABLE book_listings DROP FOREIGN KEY fk_books_owner;
ALTER TABLE book_listings
    ADD CONSTRAINT fk_books_owner FOREIGN KEY (owner_id) REFERENCES students(id) ON DELETE RESTRICT;

-- Add check constraints
ALTER TABLE book_listings
    ADD CONSTRAINT chk_books_semester CHECK (semester BETWEEN 1 AND 12),
    ADD CONSTRAINT chk_books_lending_duration CHECK (
        sharing_mode != 'LENDING' OR (lending_max_duration_days IS NOT NULL AND lending_max_duration_days > 0)
    );

-- Add optimized indexes
CREATE INDEX IF NOT EXISTS idx_books_subject ON book_listings (subject);
CREATE INDEX IF NOT EXISTS idx_books_dept_sem ON book_listings (department, semester);
CREATE INDEX IF NOT EXISTS idx_books_mode_status ON book_listings (sharing_mode, status);

-- ---------------------------------------------------------------------
-- 3. Upgrade Table: exchange_requests
-- ---------------------------------------------------------------------
-- Standardize request statuses
ALTER TABLE exchange_requests
    MODIFY COLUMN status ENUM('PENDING', 'ACCEPTED', 'REJECTED', 'CANCELLED', 'EXPIRED') NOT NULL DEFAULT 'PENDING' COMMENT 'Request negotiation state';

-- Add business check constraints
ALTER TABLE exchange_requests
    ADD CONSTRAINT chk_requests_not_self CHECK (requester_id != owner_id),
    ADD CONSTRAINT chk_requests_swap CHECK (exchange_mode != 'SWAP' OR offered_book_id IS NOT NULL),
    ADD CONSTRAINT chk_requests_giveaway CHECK (exchange_mode != 'GIVEAWAY' OR offered_book_id IS NULL),
    ADD CONSTRAINT chk_requests_lending CHECK (
        exchange_mode != 'LENDING' OR (proposed_duration_days IS NOT NULL AND proposed_duration_days > 0 AND expected_return_date IS NOT NULL)
    );

-- Add compound indexes for dashboard queries and duplicate prevention
CREATE INDEX IF NOT EXISTS idx_requests_listing_status ON exchange_requests (listing_id, status);
CREATE INDEX IF NOT EXISTS idx_requests_requester_status ON exchange_requests (requester_id, status);
CREATE INDEX IF NOT EXISTS idx_requests_owner_status ON exchange_requests (owner_id, status);
CREATE INDEX IF NOT EXISTS idx_requests_duplicate_check ON exchange_requests (listing_id, requester_id, status);

-- ---------------------------------------------------------------------
-- 4. Create Table: exchanges (NEW TABLE)
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS exchanges (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    request_id BIGINT NOT NULL UNIQUE COMMENT '1:1 link to accepted request',
    listing_id BIGINT NOT NULL COMMENT 'Primary listed book',
    exchange_mode ENUM('SWAP', 'GIVEAWAY', 'LENDING') NOT NULL COMMENT 'Sharing mode snapshot',
    owner_id BIGINT NOT NULL COMMENT 'Original book owner / lender',
    requester_id BIGINT NOT NULL COMMENT 'Book requester / borrower / recipient',
    offered_book_id BIGINT NULL COMMENT 'Reciprocal book offered in SWAP mode',
    status ENUM(
        'AWAITING_HANDOVER',
        'ACTIVE',
        'OVERDUE',
        'RETURNED',
        'COMPLETED',
        'DISPUTED',
        'CANCELLED'
    ) NOT NULL DEFAULT 'AWAITING_HANDOVER' COMMENT 'Fulfillment lifecycle',
    accepted_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Agreement acceptance timestamp',
    handover_scheduled_at DATETIME NULL COMMENT 'Agreed campus meeting schedule',
    handover_location VARCHAR(255) NULL COMMENT 'Agreed campus handover spot',
    handover_completed_at DATETIME NULL COMMENT 'Dual handover confirmation timestamp',
    loan_start_date DATE NULL COMMENT 'For LENDING: start date of active loan period',
    due_date DATE NULL COMMENT 'For LENDING: agreed calendar return date',
    actual_return_date DATE NULL COMMENT 'For LENDING: date book was returned to owner',
    owner_handover_confirmed BOOLEAN NOT NULL DEFAULT FALSE COMMENT 'Owner confirmed book handover',
    requester_handover_confirmed BOOLEAN NOT NULL DEFAULT FALSE COMMENT 'Requester confirmed receiving book',
    owner_return_confirmed BOOLEAN NOT NULL DEFAULT FALSE COMMENT 'Owner confirmed receiving lent book back',
    requester_return_confirmed BOOLEAN NOT NULL DEFAULT FALSE COMMENT 'Borrower confirmed handing book back',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_exchanges_request FOREIGN KEY (request_id) REFERENCES exchange_requests(id) ON DELETE RESTRICT,
    CONSTRAINT fk_exchanges_listing FOREIGN KEY (listing_id) REFERENCES book_listings(id) ON DELETE RESTRICT,
    CONSTRAINT fk_exchanges_owner FOREIGN KEY (owner_id) REFERENCES students(id) ON DELETE RESTRICT,
    CONSTRAINT fk_exchanges_requester FOREIGN KEY (requester_id) REFERENCES students(id) ON DELETE RESTRICT,
    CONSTRAINT fk_exchanges_offered_book FOREIGN KEY (offered_book_id) REFERENCES book_listings(id) ON DELETE RESTRICT,
    CONSTRAINT chk_exchanges_not_self CHECK (owner_id != requester_id),
    CONSTRAINT chk_exchanges_due_date CHECK (due_date IS NULL OR loan_start_date IS NULL OR due_date >= loan_start_date),
    CONSTRAINT chk_exchanges_return_date CHECK (actual_return_date IS NULL OR loan_start_date IS NULL OR actual_return_date >= loan_start_date),
    INDEX idx_exchanges_participants_status (owner_id, requester_id, status),
    INDEX idx_exchanges_owner_status (owner_id, status),
    INDEX idx_exchanges_requester_status (requester_id, status),
    INDEX idx_exchanges_listing (listing_id),
    INDEX idx_exchanges_mode_status (exchange_mode, status),
    INDEX idx_exchanges_due_date (due_date)
) ENGINE=InnoDB COMMENT='Accepted exchange agreements and fulfillment';

-- ---------------------------------------------------------------------
-- 5. Upgrade Table: book_passport_entries
-- ---------------------------------------------------------------------
ALTER TABLE book_passport_entries
    ADD COLUMN IF NOT EXISTS exchange_id BIGINT NULL COMMENT 'Link to fulfilled exchange' AFTER book_id;

-- Add foreign key constraint to exchanges
SET @fk_passport_exchange_exists = (
    SELECT COUNT(*) 
    FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS 
    WHERE CONSTRAINT_SCHEMA = DATABASE() 
      AND TABLE_NAME = 'book_passport_entries' 
      AND CONSTRAINT_NAME = 'fk_passport_exchange'
);
SET @add_fk_sql = IF(@fk_passport_exchange_exists = 0, 
    'ALTER TABLE book_passport_entries ADD CONSTRAINT fk_passport_exchange FOREIGN KEY (exchange_id) REFERENCES exchanges(id) ON DELETE SET NULL;', 
    'SELECT 1;'
);
PREPARE stmt FROM @add_fk_sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- Add check constraint and index
ALTER TABLE book_passport_entries
    ADD CONSTRAINT chk_passport_holders CHECK (previous_holder_id != new_holder_id);

CREATE INDEX IF NOT EXISTS idx_passport_book_date ON book_passport_entries (book_id, recorded_at DESC);

-- ---------------------------------------------------------------------
-- 6. Upgrade Table: student_reviews
-- ---------------------------------------------------------------------
-- Add exchange_id column if not exists
ALTER TABLE student_reviews
    ADD COLUMN IF NOT EXISTS exchange_id BIGINT NULL COMMENT 'Exchange transaction being reviewed' AFTER id;

-- Add foreign key to exchanges
SET @fk_reviews_exchange_exists = (
    SELECT COUNT(*) 
    FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS 
    WHERE CONSTRAINT_SCHEMA = DATABASE() 
      AND TABLE_NAME = 'student_reviews' 
      AND CONSTRAINT_NAME = 'fk_reviews_exchange'
);
SET @add_fk_rev_sql = IF(@fk_reviews_exchange_exists = 0, 
    'ALTER TABLE student_reviews ADD CONSTRAINT fk_reviews_exchange FOREIGN KEY (exchange_id) REFERENCES exchanges(id) ON DELETE CASCADE;', 
    'SELECT 1;'
);
PREPARE stmt FROM @add_fk_rev_sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- Add uniqueness constraint to prevent duplicate reviews by the same reviewer for the same exchange
ALTER TABLE student_reviews
    ADD CONSTRAINT uk_reviews_exchange_reviewer UNIQUE (exchange_id, reviewer_id),
    ADD CONSTRAINT chk_reviews_not_self CHECK (reviewer_id != reviewee_id);

CREATE INDEX IF NOT EXISTS idx_reviews_reviewee_rating ON student_reviews (reviewee_id, rating);
CREATE INDEX IF NOT EXISTS idx_reviews_reviewer ON student_reviews (reviewer_id);

-- ---------------------------------------------------------------------
-- 7. Create Table: notifications (NEW TABLE)
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS notifications (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    recipient_id BIGINT NOT NULL COMMENT 'Recipient student',
    sender_id BIGINT NULL COMMENT 'Sender student (NULL for automated system alerts)',
    notification_type ENUM(
        'REQUEST_RECEIVED',
        'REQUEST_ACCEPTED',
        'REQUEST_REJECTED',
        'REQUEST_CANCELLED',
        'HANDOVER_SCHEDULED',
        'HANDOVER_REMINDER',
        'HANDOVER_CONFIRMED',
        'LENDING_DUE_REMINDER',
        'LENDING_OVERDUE',
        'LENDING_RETURNED',
        'REVIEW_RECEIVED',
        'DISPUTE_RAISED',
        'SYSTEM_ALERT'
    ) NOT NULL COMMENT 'Notification event category',
    title VARCHAR(255) NOT NULL COMMENT 'Notification headline',
    message TEXT NOT NULL COMMENT 'Notification body content',
    reference_type ENUM(
        'EXCHANGE_REQUEST',
        'EXCHANGE',
        'BOOK_LISTING',
        'MODERATION_REPORT',
        'STUDENT_REVIEW',
        'NONE'
    ) NOT NULL DEFAULT 'NONE' COMMENT 'Target entity type for deep linking',
    reference_id BIGINT NULL COMMENT 'Target entity identifier',
    is_read BOOLEAN NOT NULL DEFAULT FALSE COMMENT 'Read status flag',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_notifications_recipient FOREIGN KEY (recipient_id) REFERENCES students(id) ON DELETE CASCADE,
    CONSTRAINT fk_notifications_sender FOREIGN KEY (sender_id) REFERENCES students(id) ON DELETE SET NULL,
    INDEX idx_notifications_recipient_read (recipient_id, is_read, created_at DESC),
    INDEX idx_notifications_created (created_at)
) ENGINE=InnoDB COMMENT='Real-time student notifications';

-- ---------------------------------------------------------------------
-- 8. Create Table: moderation_reports (NEW TABLE)
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS moderation_reports (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    reporter_id BIGINT NOT NULL COMMENT 'Student who submitted the report',
    reported_student_id BIGINT NULL COMMENT 'Student accused of violation (if applicable)',
    reported_listing_id BIGINT NULL COMMENT 'Flagged book listing (if applicable)',
    exchange_id BIGINT NULL COMMENT 'Associated exchange under dispute (if applicable)',
    reason ENUM(
        'INAPPROPRIATE_CONTENT',
        'FAKE_LISTING',
        'BOOK_NOT_RETURNED',
        'DAMAGED_BOOK',
        'HARASSMENT',
        'NO_SHOW_HANDOVER',
        'SUSPICIOUS_ACCOUNT',
        'OTHER'
    ) NOT NULL COMMENT 'Report reason classification',
    details TEXT NOT NULL COMMENT 'Student explanation of the incident',
    status ENUM('PENDING', 'UNDER_REVIEW', 'RESOLVED', 'DISMISSED') NOT NULL DEFAULT 'PENDING' COMMENT 'Moderation workflow state',
    admin_notes TEXT NULL COMMENT 'Internal administrative resolution notes',
    resolved_at DATETIME NULL COMMENT 'Timestamp when administrator resolved report',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_reports_reporter FOREIGN KEY (reporter_id) REFERENCES students(id) ON DELETE RESTRICT,
    CONSTRAINT fk_reports_reported_student FOREIGN KEY (reported_student_id) REFERENCES students(id) ON DELETE SET NULL,
    CONSTRAINT fk_reports_reported_listing FOREIGN KEY (reported_listing_id) REFERENCES book_listings(id) ON DELETE SET NULL,
    CONSTRAINT fk_reports_exchange FOREIGN KEY (exchange_id) REFERENCES exchanges(id) ON DELETE SET NULL,
    CONSTRAINT chk_reports_target CHECK (reported_student_id IS NOT NULL OR reported_listing_id IS NOT NULL OR exchange_id IS NOT NULL),
    CONSTRAINT chk_reports_not_self CHECK (reported_student_id IS NULL OR reporter_id != reported_student_id),
    INDEX idx_reports_status (status, created_at DESC),
    INDEX idx_reports_reporter (reporter_id),
    INDEX idx_reports_reported_student (reported_student_id),
    INDEX idx_reports_reported_listing (reported_listing_id),
    INDEX idx_reports_exchange (exchange_id)
) ENGINE=InnoDB COMMENT='Dispute resolution and moderation reports';
