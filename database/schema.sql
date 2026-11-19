-- =====================================================================
-- Book4u - Relational Database Schema (MySQL 8.x)
-- University Campus Peer-to-Peer Book Exchange Platform
-- Technology: MySQL 8.x + Spring JDBC (No Hibernate/JPA)
-- =====================================================================

-- 1. Database Creation
CREATE DATABASE IF NOT EXISTS book4u_db
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

USE book4u_db;

-- 2. Drop existing tables if needed (in reverse order of foreign key dependencies)
DROP TABLE IF EXISTS student_reviews;
DROP TABLE IF EXISTS book_passport_entries;
DROP TABLE IF EXISTS exchange_requests;
DROP TABLE IF EXISTS book_listings;
DROP TABLE IF EXISTS students;

-- =====================================================================
-- TABLE: students
-- Description: University students participating in peer-to-peer exchange
-- =====================================================================
CREATE TABLE students (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    university_id VARCHAR(64) NOT NULL UNIQUE COMMENT 'Campus student roll number or institutional ID',
    email VARCHAR(255) NOT NULL UNIQUE COMMENT 'Institutional/University verified email address',
    password_hash VARCHAR(255) NOT NULL COMMENT 'Bcrypt/Argon2 securely hashed password',
    full_name VARCHAR(128) NOT NULL COMMENT 'Student full legal or campus name',
    phone VARCHAR(20) NULL COMMENT 'Contact phone number for handover coordination',
    department VARCHAR(128) NOT NULL COMMENT 'Academic department (e.g., Computer Science, Mechanical)',
    campus_name VARCHAR(128) NOT NULL DEFAULT 'Main Campus' COMMENT 'Specific university branch or campus location',
    reputation_score DECIMAL(3, 2) NOT NULL DEFAULT 5.00 COMMENT 'Aggregated 1.00 to 5.00 student rating',
    status ENUM('PENDING_VERIFICATION', 'ACTIVE', 'SUSPENDED') NOT NULL DEFAULT 'ACTIVE' COMMENT 'Account standing',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_students_email (email),
    INDEX idx_students_dept (department),
    INDEX idx_students_status (status)
) ENGINE=InnoDB COMMENT='University student accounts';

-- =====================================================================
-- TABLE: book_listings
-- Description: Academic books listed for sharing (SWAP, GIVEAWAY, LENDING)
-- Notice: Strictly NO selling or buying columns in compliance with specification
-- =====================================================================
CREATE TABLE book_listings (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    owner_id BIGINT NOT NULL COMMENT 'Current student owner or custodian',
    title VARCHAR(255) NOT NULL COMMENT 'Full title of the book',
    author VARCHAR(255) NOT NULL COMMENT 'Author(s) of the book',
    isbn VARCHAR(32) NULL COMMENT 'International Standard Book Number (10 or 13 digits)',
    edition VARCHAR(32) NULL COMMENT 'Edition string (e.g., 4th Edition, 2023 Revision)',
    course_code VARCHAR(32) NULL COMMENT 'Associated course/subject code (e.g., CS101, ENG204)',
    department VARCHAR(128) NOT NULL COMMENT 'Target academic department',
    condition_grade ENUM('NEW', 'LIKE_NEW', 'GOOD', 'FAIR') NOT NULL DEFAULT 'GOOD' COMMENT 'Physical condition',
    sharing_mode ENUM('SWAP', 'GIVEAWAY', 'LENDING') NOT NULL COMMENT 'Supported exchange mechanism',
    status ENUM('AVAILABLE', 'RESERVED', 'EXCHANGED', 'ARCHIVED') NOT NULL DEFAULT 'AVAILABLE' COMMENT 'Listing lifecycle',
    swap_preferences TEXT NULL COMMENT 'Books or topics the student is interested in receiving (SWAP mode)',
    lending_max_duration_days INT UNSIGNED NULL COMMENT 'Maximum allowed lending days (LENDING mode)',
    description TEXT NULL COMMENT 'Notes on highlights, annotations, or book condition',
    cover_image_url VARCHAR(512) NULL COMMENT 'Uploaded cover or preview photo URL',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_books_owner FOREIGN KEY (owner_id) REFERENCES students(id) ON DELETE CASCADE,
    INDEX idx_books_owner (owner_id),
    INDEX idx_books_sharing_mode (sharing_mode),
    INDEX idx_books_status (status),
    INDEX idx_books_course (course_code),
    INDEX idx_books_title (title)
) ENGINE=InnoDB COMMENT='Peer-to-peer academic book listings';

-- =====================================================================
-- TABLE: exchange_requests
-- Description: Workflows for SWAP, GIVEAWAY, and LENDING between students
-- =====================================================================
CREATE TABLE exchange_requests (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    listing_id BIGINT NOT NULL COMMENT 'Requested book listing',
    requester_id BIGINT NOT NULL COMMENT 'Student initiating the request',
    owner_id BIGINT NOT NULL COMMENT 'Owner of the requested listing',
    exchange_mode ENUM('SWAP', 'GIVEAWAY', 'LENDING') NOT NULL COMMENT 'Snapshot of exchange mode',
    offered_book_id BIGINT NULL COMMENT 'For SWAP mode: listing ID offered in return',
    proposed_duration_days INT UNSIGNED NULL COMMENT 'For LENDING mode: requested days',
    expected_return_date DATE NULL COMMENT 'For LENDING mode: agreed return calendar date',
    actual_return_date DATE NULL COMMENT 'For LENDING mode: actual return date',
    request_message TEXT NULL COMMENT 'Introductory message from requester',
    status ENUM(
        'PENDING',
        'ACCEPTED',
        'REJECTED',
        'CANCELLED',
        'HANDOVER_SCHEDULED',
        'COMPLETED',
        'RETURNED'
    ) NOT NULL DEFAULT 'PENDING' COMMENT 'State machine status',
    handover_scheduled_at DATETIME NULL COMMENT 'Agreed campus meeting time',
    handover_location VARCHAR(255) NULL COMMENT 'Agreed campus spot (e.g., Central Library Lobby)',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_requests_listing FOREIGN KEY (listing_id) REFERENCES book_listings(id) ON DELETE CASCADE,
    CONSTRAINT fk_requests_requester FOREIGN KEY (requester_id) REFERENCES students(id) ON DELETE CASCADE,
    CONSTRAINT fk_requests_owner FOREIGN KEY (owner_id) REFERENCES students(id) ON DELETE CASCADE,
    CONSTRAINT fk_requests_offered_book FOREIGN KEY (offered_book_id) REFERENCES book_listings(id) ON DELETE SET NULL,
    INDEX idx_requests_listing (listing_id),
    INDEX idx_requests_requester (requester_id),
    INDEX idx_requests_owner (owner_id),
    INDEX idx_requests_status (status)
) ENGINE=InnoDB COMMENT='Exchange workflow tracking';

-- =====================================================================
-- TABLE: book_passport_entries
-- Description: Permanent lineage & handover journey history of each book
-- =====================================================================
CREATE TABLE book_passport_entries (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    book_id BIGINT NOT NULL COMMENT 'Book being tracked',
    exchange_request_id BIGINT NULL COMMENT 'Associated exchange workflow',
    previous_holder_id BIGINT NOT NULL COMMENT 'Student passing the book',
    new_holder_id BIGINT NOT NULL COMMENT 'Student receiving the book',
    exchange_mode ENUM('SWAP', 'GIVEAWAY', 'LENDING') NOT NULL COMMENT 'Sharing mode used',
    handover_campus_location VARCHAR(255) NULL COMMENT 'Where the handover happened',
    notes TEXT NULL COMMENT 'Memorable notes, batch memories, or reader impressions',
    recorded_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_passport_book FOREIGN KEY (book_id) REFERENCES book_listings(id) ON DELETE CASCADE,
    CONSTRAINT fk_passport_request FOREIGN KEY (exchange_request_id) REFERENCES exchange_requests(id) ON DELETE SET NULL,
    CONSTRAINT fk_passport_prev_holder FOREIGN KEY (previous_holder_id) REFERENCES students(id) ON DELETE CASCADE,
    CONSTRAINT fk_passport_new_holder FOREIGN KEY (new_holder_id) REFERENCES students(id) ON DELETE CASCADE,
    INDEX idx_passport_book (book_id),
    INDEX idx_passport_recorded (recorded_at)
) ENGINE=InnoDB COMMENT='Book Passport journey entries';

-- =====================================================================
-- TABLE: student_reviews
-- Description: Mutual feedback after completed handovers to build campus trust
-- =====================================================================
CREATE TABLE student_reviews (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    exchange_request_id BIGINT NOT NULL COMMENT 'Exchange transaction being reviewed',
    reviewer_id BIGINT NOT NULL COMMENT 'Student submitting the rating',
    reviewee_id BIGINT NOT NULL COMMENT 'Student receiving the rating',
    rating TINYINT UNSIGNED NOT NULL CHECK (rating BETWEEN 1 AND 5) COMMENT '1 to 5 star rating',
    review_text TEXT NULL COMMENT 'Written feedback',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_reviews_request FOREIGN KEY (exchange_request_id) REFERENCES exchange_requests(id) ON DELETE CASCADE,
    CONSTRAINT fk_reviews_reviewer FOREIGN KEY (reviewer_id) REFERENCES students(id) ON DELETE CASCADE,
    CONSTRAINT fk_reviews_reviewee FOREIGN KEY (reviewee_id) REFERENCES students(id) ON DELETE CASCADE,
    INDEX idx_reviews_reviewee (reviewee_id)
) ENGINE=InnoDB COMMENT='Student reputation and trust reviews';

-- =====================================================================
-- SEED DATA (For development verification & campus demo)
-- =====================================================================
INSERT INTO students (university_id, email, password_hash, full_name, phone, department, campus_name, reputation_score, status)
VALUES
('U2024CS001', 'aarav.sharma@campus.edu', '$2a$10$EXAMPLEHASHFORDEVELOPMENTONLY01', 'Aarav Sharma', '+919876543210', 'Computer Science', 'Main Campus', 4.95, 'ACTIVE'),
('U2024EC015', 'priya.patel@campus.edu', '$2a$10$EXAMPLEHASHFORDEVELOPMENTONLY02', 'Priya Patel', '+919876543211', 'Electronics & Communication', 'Main Campus', 4.88, 'ACTIVE'),
('U2024ME042', 'rohan.verma@campus.edu', '$2a$10$EXAMPLEHASHFORDEVELOPMENTONLY03', 'Rohan Verma', '+919876543212', 'Mechanical Engineering', 'North Campus', 5.00, 'ACTIVE');

INSERT INTO book_listings (owner_id, title, author, isbn, edition, course_code, department, condition_grade, sharing_mode, status, swap_preferences, lending_max_duration_days, description)
VALUES
(1, 'Introduction to Algorithms (CLRS)', 'Thomas H. Cormen, Charles E. Leiserson', '9780262033848', '3rd Edition', 'CS201', 'Computer Science', 'LIKE_NEW', 'SWAP', 'AVAILABLE', 'Looking for Operating Systems by Silberschatz or Computer Networks by Tanenbaum', NULL, 'Clean copy with minor pencil notations on chapter 4.'),
(2, 'Signals and Systems', 'Alan V. Oppenheim, Alan S. Willsky', '9780138147570', '2nd Edition', 'EC204', 'Electronics & Communication', 'GOOD', 'LENDING', 'AVAILABLE', NULL, 30, 'Available to lend for the mid-term revision period. Please return in good condition.'),
(3, 'Engineering Thermodynamics', 'P.K. Nag', '9789352606429', '6th Edition', 'ME102', 'Mechanical Engineering', 'FAIR', 'GIVEAWAY', 'AVAILABLE', NULL, NULL, 'Completed the course, giving away free of cost to any 1st or 2nd year student who needs it!');
