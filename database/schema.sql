-- =====================================================================
-- Book4u - Complete Relational Database Schema (MySQL 8.x)
-- University Campus Peer-to-Peer Academic Book Sharing Platform
-- Engine: MySQL 8.x (InnoDB, utf8mb4, utf8mb4_unicode_ci)
-- Data Access Layer: Spring JDBC (PreparedStatement / Parameterized SQL)
-- Architectural Governance: Strictly Zero Commercial Transactions / Payments
-- Supported Sharing Modes: SWAP, GIVEAWAY, LENDING
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. Database Definition
-- ---------------------------------------------------------------------
CREATE DATABASE IF NOT EXISTS book4u_db
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

USE book4u_db;

-- ---------------------------------------------------------------------
-- 2. Drop existing tables in reverse order of foreign key dependencies
-- (Used only for clean testing/staging teardowns; production relies on migrations)
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS moderation_reports;
DROP TABLE IF EXISTS notifications;
DROP TABLE IF EXISTS student_reviews;
DROP TABLE IF EXISTS book_passport_entries;
DROP TABLE IF EXISTS exchanges;
DROP TABLE IF EXISTS exchange_requests;
DROP TABLE IF EXISTS book_listings;
DROP TABLE IF EXISTS students;

-- =====================================================================
-- TABLE 1: students
-- Description: Verified university student accounts participating in P2P sharing
-- =====================================================================
CREATE TABLE students (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    university_id VARCHAR(64) NOT NULL UNIQUE COMMENT 'Institutional student roll number or campus badge ID',
    full_name VARCHAR(128) NOT NULL COMMENT 'Full student legal/campus name',
    email VARCHAR(255) NOT NULL UNIQUE COMMENT 'Verified institutional email address (@campus.edu)',
    phone VARCHAR(20) NULL COMMENT 'Contact phone number for in-person campus handover coordination',
    password_hash VARCHAR(255) NOT NULL COMMENT 'BCrypt/Argon2 securely salted password hash (never plaintext)',
    department VARCHAR(128) NOT NULL COMMENT 'Academic department (e.g., Computer Science, Mechanical Engineering)',
    semester TINYINT UNSIGNED NOT NULL DEFAULT 1 COMMENT 'Current academic semester (1 to 12)',
    campus_name VARCHAR(128) NOT NULL DEFAULT 'Main Campus' COMMENT 'Campus branch or facility name',
    profile_image_url VARCHAR(512) NULL COMMENT 'Student avatar photo URL',
    reputation_score DECIMAL(3, 2) NOT NULL DEFAULT 5.00 COMMENT 'Aggregated 1.00 to 5.00 peer reputation score',
    verification_status ENUM('PENDING', 'VERIFIED', 'REJECTED') NOT NULL DEFAULT 'PENDING' COMMENT 'Campus institutional ID verification status',
    account_status ENUM('ACTIVE', 'SUSPENDED', 'DEACTIVATED') NOT NULL DEFAULT 'ACTIVE' COMMENT 'Account governance status',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT chk_students_semester CHECK (semester BETWEEN 1 AND 12),
    CONSTRAINT chk_students_reputation CHECK (reputation_score BETWEEN 1.00 AND 5.00),
    INDEX idx_students_email (email),
    INDEX idx_students_dept_verif (department, verification_status),
    INDEX idx_students_campus (campus_name),
    INDEX idx_students_status (account_status)
) ENGINE=InnoDB COMMENT='Verified university student accounts';

-- =====================================================================
-- TABLE 2: book_listings
-- Description: Academic textbooks cataloged for sharing (SWAP, GIVEAWAY, LENDING)
-- Note: Strictly zero price, currency, or payment columns
-- =====================================================================
CREATE TABLE book_listings (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    owner_id BIGINT NOT NULL COMMENT 'Current student owner or custodian',
    title VARCHAR(255) NOT NULL COMMENT 'Full title of the academic textbook',
    author VARCHAR(255) NOT NULL COMMENT 'Author(s) of the textbook',
    isbn VARCHAR(32) NULL COMMENT 'Standard ISBN (10 or 13 digits)',
    edition VARCHAR(32) NULL COMMENT 'Edition string (e.g., 3rd Edition, 2024 Revision)',
    course_code VARCHAR(32) NULL COMMENT 'Associated course code (e.g., CS201, EC302)',
    subject VARCHAR(128) NOT NULL COMMENT 'Academic subject area (e.g., Data Structures, Database Systems)',
    department VARCHAR(128) NOT NULL COMMENT 'Target academic department',
    semester TINYINT UNSIGNED NOT NULL DEFAULT 1 COMMENT 'Recommended syllabus semester (1 to 12)',
    condition_grade ENUM('NEW', 'LIKE_NEW', 'GOOD', 'FAIR') NOT NULL DEFAULT 'GOOD' COMMENT 'Physical book condition grade',
    sharing_mode ENUM('SWAP', 'GIVEAWAY', 'LENDING') NOT NULL COMMENT 'Exclusive sharing mechanism',
    status ENUM('AVAILABLE', 'RESERVED', 'ON_LOAN', 'UNAVAILABLE') NOT NULL DEFAULT 'AVAILABLE' COMMENT 'Listing availability status',
    description TEXT NULL COMMENT 'Physical annotations, highlighted chapters, and syllabus coverage notes',
    cover_image_url VARCHAR(512) NULL COMMENT 'Uploaded photo of the actual book cover',
    lending_max_duration_days INT UNSIGNED NULL COMMENT 'Maximum allowed loan duration in days (mandatory when LENDING)',
    swap_preferences TEXT NULL COMMENT 'Target textbooks or topics desired in return (for SWAP mode)',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_books_owner FOREIGN KEY (owner_id) REFERENCES students(id) ON DELETE RESTRICT,
    CONSTRAINT chk_books_semester CHECK (semester BETWEEN 1 AND 12),
    CONSTRAINT chk_books_lending_duration CHECK (
        sharing_mode != 'LENDING' OR (lending_max_duration_days IS NOT NULL AND lending_max_duration_days > 0)
    ),
    INDEX idx_books_owner (owner_id),
    INDEX idx_books_title (title),
    INDEX idx_books_subject (subject),
    INDEX idx_books_dept_sem (department, semester),
    INDEX idx_books_mode_status (sharing_mode, status),
    INDEX idx_books_course (course_code)
) ENGINE=InnoDB COMMENT='Academic book listings';

-- =====================================================================
-- TABLE 3: exchange_requests
-- Description: Negotiation requests submitted by students for listed books
-- =====================================================================
CREATE TABLE exchange_requests (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    listing_id BIGINT NOT NULL COMMENT 'Target book listing being requested',
    requester_id BIGINT NOT NULL COMMENT 'Student initiating the request',
    owner_id BIGINT NOT NULL COMMENT 'Student who owns the requested listing',
    offered_book_id BIGINT NULL COMMENT 'Reciprocal book offered in return (mandatory for SWAP mode)',
    exchange_mode ENUM('SWAP', 'GIVEAWAY', 'LENDING') NOT NULL COMMENT 'Exchange mode of the request',
    proposed_duration_days INT UNSIGNED NULL COMMENT 'Requested loan duration in days (for LENDING mode)',
    expected_return_date DATE NULL COMMENT 'Agreed calendar return deadline (for LENDING mode)',
    request_message TEXT NULL COMMENT 'Introductory message explaining course need or swap proposal',
    status ENUM('PENDING', 'ACCEPTED', 'REJECTED', 'CANCELLED', 'EXPIRED') NOT NULL DEFAULT 'PENDING' COMMENT 'Request negotiation state',
    handover_scheduled_at DATETIME NULL COMMENT 'Proposed campus meeting date and time',
    handover_location VARCHAR(255) NULL COMMENT 'Proposed physical campus meeting spot',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_requests_listing FOREIGN KEY (listing_id) REFERENCES book_listings(id) ON DELETE CASCADE,
    CONSTRAINT fk_requests_requester FOREIGN KEY (requester_id) REFERENCES students(id) ON DELETE CASCADE,
    CONSTRAINT fk_requests_owner FOREIGN KEY (owner_id) REFERENCES students(id) ON DELETE CASCADE,
    CONSTRAINT fk_requests_offered_book FOREIGN KEY (offered_book_id) REFERENCES book_listings(id) ON DELETE SET NULL,
    CONSTRAINT chk_requests_not_self CHECK (requester_id != owner_id),
    CONSTRAINT chk_requests_swap CHECK (exchange_mode != 'SWAP' OR offered_book_id IS NOT NULL),
    CONSTRAINT chk_requests_giveaway CHECK (exchange_mode != 'GIVEAWAY' OR offered_book_id IS NULL),
    CONSTRAINT chk_requests_lending CHECK (
        exchange_mode != 'LENDING' OR (proposed_duration_days IS NOT NULL AND proposed_duration_days > 0 AND expected_return_date IS NOT NULL)
    ),
    INDEX idx_requests_listing_status (listing_id, status),
    INDEX idx_requests_requester_status (requester_id, status),
    INDEX idx_requests_owner_status (owner_id, status),
    INDEX idx_requests_duplicate_check (listing_id, requester_id, status),
    INDEX idx_requests_mode (exchange_mode)
) ENGINE=InnoDB COMMENT='Peer-to-peer exchange negotiation requests';

-- =====================================================================
-- TABLE 4: exchanges
-- Description: Accepted exchange agreements, dual handover confirmation, and active loan tracking
-- =====================================================================
CREATE TABLE exchanges (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    request_id BIGINT NOT NULL UNIQUE COMMENT '1:1 link to accepted exchange request',
    listing_id BIGINT NOT NULL COMMENT 'Primary textbook being exchanged or lent',
    exchange_mode ENUM('SWAP', 'GIVEAWAY', 'LENDING') NOT NULL COMMENT 'Sharing mode for this transaction',
    owner_id BIGINT NOT NULL COMMENT 'Original book owner / lender',
    requester_id BIGINT NOT NULL COMMENT 'Book requester / borrower / recipient',
    offered_book_id BIGINT NULL COMMENT 'Reciprocal book in SWAP mode',
    status ENUM(
        'AWAITING_HANDOVER',
        'ACTIVE',
        'OVERDUE',
        'RETURNED',
        'COMPLETED',
        'DISPUTED',
        'CANCELLED'
    ) NOT NULL DEFAULT 'AWAITING_HANDOVER' COMMENT 'Fulfillment lifecycle state',
    accepted_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Timestamp when owner accepted the agreement',
    handover_scheduled_at DATETIME NULL COMMENT 'Agreed campus meeting schedule',
    handover_location VARCHAR(255) NULL COMMENT 'Agreed campus handover spot (e.g. Central Library Front Desk)',
    handover_completed_at DATETIME NULL COMMENT 'Timestamp when dual handover confirmation completed',
    loan_start_date DATE NULL COMMENT 'For LENDING: start date of active loan period',
    due_date DATE NULL COMMENT 'For LENDING: agreed calendar return deadline',
    actual_return_date DATE NULL COMMENT 'For LENDING: actual date book was returned to owner',
    owner_handover_confirmed BOOLEAN NOT NULL DEFAULT FALSE COMMENT 'Owner confirmed physical handover',
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

-- =====================================================================
-- TABLE 5: book_passport_entries
-- Description: Permanent physical lineage and reading legacy chronicle of books
-- =====================================================================
CREATE TABLE book_passport_entries (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    book_id BIGINT NOT NULL COMMENT 'Textbook being chronologically tracked',
    exchange_id BIGINT NULL COMMENT 'Associated fulfilled exchange agreement',
    previous_holder_id BIGINT NOT NULL COMMENT 'Student passing the book',
    new_holder_id BIGINT NOT NULL COMMENT 'Student receiving the book',
    exchange_mode ENUM('SWAP', 'GIVEAWAY', 'LENDING') NOT NULL COMMENT 'Sharing mode utilized',
    handover_campus_location VARCHAR(255) NULL COMMENT 'Campus landmark where handover happened',
    notes TEXT NULL COMMENT 'Reader reflections, course insights, or marginalia comments (no private PII)',
    recorded_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_passport_book FOREIGN KEY (book_id) REFERENCES book_listings(id) ON DELETE CASCADE,
    CONSTRAINT fk_passport_exchange FOREIGN KEY (exchange_id) REFERENCES exchanges(id) ON DELETE SET NULL,
    CONSTRAINT fk_passport_prev_holder FOREIGN KEY (previous_holder_id) REFERENCES students(id) ON DELETE RESTRICT,
    CONSTRAINT fk_passport_new_holder FOREIGN KEY (new_holder_id) REFERENCES students(id) ON DELETE RESTRICT,
    CONSTRAINT chk_passport_holders CHECK (previous_holder_id != new_holder_id),
    INDEX idx_passport_book_date (book_id, recorded_at DESC),
    INDEX idx_passport_recorded (recorded_at)
) ENGINE=InnoDB COMMENT='Book Passport journey entries';

-- =====================================================================
-- TABLE 6: student_reviews
-- Description: Mutual peer reviews submitted after confirmed handovers/returns
-- =====================================================================
CREATE TABLE student_reviews (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    exchange_id BIGINT NOT NULL COMMENT 'Fulfilled exchange transaction being reviewed',
    reviewer_id BIGINT NOT NULL COMMENT 'Student submitting the rating',
    reviewee_id BIGINT NOT NULL COMMENT 'Student receiving the rating',
    rating TINYINT UNSIGNED NOT NULL COMMENT '1 to 5 star rating',
    review_text TEXT NULL COMMENT 'Written feedback on punctuality, book condition, and communication',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_reviews_exchange FOREIGN KEY (exchange_id) REFERENCES exchanges(id) ON DELETE CASCADE,
    CONSTRAINT fk_reviews_reviewer FOREIGN KEY (reviewer_id) REFERENCES students(id) ON DELETE RESTRICT,
    CONSTRAINT fk_reviews_reviewee FOREIGN KEY (reviewee_id) REFERENCES students(id) ON DELETE RESTRICT,
    CONSTRAINT chk_reviews_rating CHECK (rating BETWEEN 1 AND 5),
    CONSTRAINT chk_reviews_not_self CHECK (reviewer_id != reviewee_id),
    CONSTRAINT uk_reviews_exchange_reviewer UNIQUE (exchange_id, reviewer_id),
    INDEX idx_reviews_reviewee_rating (reviewee_id, rating),
    INDEX idx_reviews_reviewer (reviewer_id),
    INDEX idx_reviews_exchange (exchange_id)
) ENGINE=InnoDB COMMENT='Student reputation and trust reviews';

-- =====================================================================
-- TABLE 7: notifications
-- Description: Real-time student alerts for exchange workflows and reminders
-- =====================================================================
CREATE TABLE notifications (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    recipient_id BIGINT NOT NULL COMMENT 'Recipient student account',
    sender_id BIGINT NULL COMMENT 'Originating student account (NULL for automated system alerts)',
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
    ) NOT NULL COMMENT 'Notification category',
    title VARCHAR(255) NOT NULL COMMENT 'Notification headline',
    message TEXT NOT NULL COMMENT 'Notification detailed message text',
    reference_type ENUM(
        'EXCHANGE_REQUEST',
        'EXCHANGE',
        'BOOK_LISTING',
        'MODERATION_REPORT',
        'STUDENT_REVIEW',
        'NONE'
    ) NOT NULL DEFAULT 'NONE' COMMENT 'Target entity type for frontend navigation link',
    reference_id BIGINT NULL COMMENT 'Target entity primary key',
    is_read BOOLEAN NOT NULL DEFAULT FALSE COMMENT 'Read status flag',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_notifications_recipient FOREIGN KEY (recipient_id) REFERENCES students(id) ON DELETE CASCADE,
    CONSTRAINT fk_notifications_sender FOREIGN KEY (sender_id) REFERENCES students(id) ON DELETE SET NULL,
    INDEX idx_notifications_recipient_read (recipient_id, is_read, created_at DESC),
    INDEX idx_notifications_created (created_at)
) ENGINE=InnoDB COMMENT='Student in-app notifications';

-- =====================================================================
-- TABLE 8: moderation_reports
-- Description: Community safety reports, dispute flagging, and administrator governance
-- =====================================================================
CREATE TABLE moderation_reports (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    reporter_id BIGINT NOT NULL COMMENT 'Student submitting the report',
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
    details TEXT NOT NULL COMMENT 'Detailed explanation of the grievance or policy violation',
    status ENUM('PENDING', 'UNDER_REVIEW', 'RESOLVED', 'DISMISSED') NOT NULL DEFAULT 'PENDING' COMMENT 'Moderation workflow state',
    admin_notes TEXT NULL COMMENT 'Internal administrative investigation notes',
    resolved_at DATETIME NULL COMMENT 'Timestamp when report was officially resolved',
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
