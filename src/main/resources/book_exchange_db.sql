-- =====================================================================
-- Student Peer-to-Peer Book Exchange System (Book4u)
-- Database Creation Script: book_exchange_db.sql
-- Technology: MySQL 8.x + Core Java JDBC (Pure SQL, No ORM/Hibernate)
-- Supported Modes: SWAP (Exchange), GIVEAWAY (Free gift), LENDING (Time-bound loan)
-- =====================================================================

-- 1. Create Database with full Unicode UTF-8 support
CREATE DATABASE IF NOT EXISTS book_exchange_db
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

USE book_exchange_db;

-- ---------------------------------------------------------------------
-- PRODUCTION SAFETY GOVERNANCE:
-- Destructive DROP TABLE / TRUNCATE statements are strictly prohibited
-- to guarantee zero data loss during application restarts or redeployments.
-- ---------------------------------------------------------------------

-- =====================================================================
-- TABLE: students
-- Description: University students registered on the campus platform
-- =====================================================================
CREATE TABLE students (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    university_id VARCHAR(64) NOT NULL UNIQUE COMMENT 'Campus roll number or institutional ID',
    full_name VARCHAR(128) NOT NULL COMMENT 'Full student legal/campus name',
    email VARCHAR(255) NOT NULL UNIQUE COMMENT 'Institutional email address (@campus.edu)',
    phone VARCHAR(20) NULL COMMENT 'Contact phone number for in-person handover coordination',
    password_hash VARCHAR(255) NOT NULL COMMENT 'Securely hashed password (SHA-256 with salt / BCrypt)',
    department VARCHAR(128) NOT NULL COMMENT 'Academic department (e.g. Computer Science, Mechanical)',
    semester TINYINT UNSIGNED NOT NULL DEFAULT 1 COMMENT 'Current semester (1-12)',
    campus_name VARCHAR(128) NOT NULL DEFAULT 'Main Campus' COMMENT 'Specific university branch/campus',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_students_email (email),
    INDEX idx_students_dept (department)
) ENGINE=InnoDB COMMENT='Registered university student accounts';

-- =====================================================================
-- TABLE: books
-- Description: Academic textbooks listed for peer sharing
-- Strictly NO price, selling or payment columns
-- =====================================================================
CREATE TABLE books (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    owner_id BIGINT NOT NULL COMMENT 'Student who owns or currently holds the book',
    title VARCHAR(255) NOT NULL COMMENT 'Title of the academic textbook',
    author VARCHAR(255) NOT NULL COMMENT 'Author(s) of the textbook',
    isbn VARCHAR(32) NULL COMMENT 'Standard ISBN (10 or 13 digits)',
    edition VARCHAR(32) NULL COMMENT 'Edition string (e.g. 3rd Edition, 2024 Revision)',
    course_code VARCHAR(32) NULL COMMENT 'Associated course code (e.g. CS201, EC302)',
    subject VARCHAR(128) NOT NULL COMMENT 'Academic subject area (e.g. Java, DBMS, DSA, Networks)',
    condition_grade ENUM('NEW', 'LIKE_NEW', 'GOOD', 'FAIR') NOT NULL DEFAULT 'GOOD' COMMENT 'Physical condition',
    sharing_mode ENUM('SWAP', 'GIVEAWAY', 'LENDING') NOT NULL COMMENT 'Supported sharing mechanism',
    status ENUM('AVAILABLE', 'RESERVED', 'ON_LOAN', 'UNAVAILABLE') NOT NULL DEFAULT 'AVAILABLE' COMMENT 'Availability status',
    description TEXT NULL COMMENT 'Annotations, highlighted chapters, and condition notes',
    image_url VARCHAR(512) NULL COMMENT 'Optional cover image or photo URL',
    lending_max_duration_days INT UNSIGNED NULL COMMENT 'Maximum allowed loan days (if mode is LENDING)',
    swap_preferences TEXT NULL COMMENT 'Books or topics the student desires in return (if mode is SWAP)',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_books_owner FOREIGN KEY (owner_id) REFERENCES students(id) ON DELETE CASCADE,
    INDEX idx_books_owner (owner_id),
    INDEX idx_books_title (title),
    INDEX idx_books_subject (subject),
    INDEX idx_books_mode (sharing_mode),
    INDEX idx_books_status (status)
) ENGINE=InnoDB COMMENT='Academic book listings';

-- =====================================================================
-- TABLE: exchange_requests
-- Description: Formal requests initiated by students to request listed books
-- =====================================================================
CREATE TABLE exchange_requests (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    book_id BIGINT NOT NULL COMMENT 'Requested book listing',
    requester_id BIGINT NOT NULL COMMENT 'Student submitting the request',
    owner_id BIGINT NOT NULL COMMENT 'Owner of the requested book',
    exchange_mode ENUM('SWAP', 'GIVEAWAY', 'LENDING') NOT NULL COMMENT 'Mode of the request',
    offered_book_id BIGINT NULL COMMENT 'Reciprocal book offered in return (for SWAP mode)',
    proposed_duration_days INT UNSIGNED NULL COMMENT 'Requested loan duration in days (for LENDING mode)',
    request_message TEXT NULL COMMENT 'Introductory message explaining course need',
    status ENUM('PENDING', 'ACCEPTED', 'REJECTED', 'CANCELLED', 'EXPIRED') NOT NULL DEFAULT 'PENDING' COMMENT 'Request status',
    handover_location VARCHAR(255) NULL COMMENT 'Proposed campus handover spot (e.g. Central Library)',
    handover_time DATETIME NULL COMMENT 'Proposed meeting date and time',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_requests_book FOREIGN KEY (book_id) REFERENCES books(id) ON DELETE CASCADE,
    CONSTRAINT fk_requests_requester FOREIGN KEY (requester_id) REFERENCES students(id) ON DELETE CASCADE,
    CONSTRAINT fk_requests_owner FOREIGN KEY (owner_id) REFERENCES students(id) ON DELETE CASCADE,
    CONSTRAINT fk_requests_offered_book FOREIGN KEY (offered_book_id) REFERENCES books(id) ON DELETE SET NULL,
    INDEX idx_requests_book (book_id),
    INDEX idx_requests_requester (requester_id),
    INDEX idx_requests_owner (owner_id),
    INDEX idx_requests_status (status)
) ENGINE=InnoDB COMMENT='Book exchange requests';

-- =====================================================================
-- TABLE: exchanges
-- Description: Accepted exchange agreements and active loan tracking
-- Supports dual physical handover confirmation and dual return confirmation
-- =====================================================================
CREATE TABLE exchanges (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    request_id BIGINT NOT NULL UNIQUE COMMENT '1:1 link to accepted exchange request',
    book_id BIGINT NOT NULL COMMENT 'Textbook being shared',
    offered_book_id BIGINT NULL COMMENT 'Reciprocal book offered in return (for SWAP mode)',
    owner_id BIGINT NOT NULL COMMENT 'Original book owner / lender',
    requester_id BIGINT NOT NULL COMMENT 'Book requester / borrower',
    exchange_mode ENUM('SWAP', 'GIVEAWAY', 'LENDING') NOT NULL COMMENT 'Sharing mode',
    status VARCHAR(32) NOT NULL DEFAULT 'AWAITING_HANDOVER' COMMENT 'Lifecycle: AWAITING_HANDOVER, ACTIVE, COMPLETED, RETURNED, OVERDUE, CANCELLED, DISPUTED',
    owner_handover_confirmed BOOLEAN NOT NULL DEFAULT FALSE COMMENT 'Owner confirmed physical handover',
    requester_handover_confirmed BOOLEAN NOT NULL DEFAULT FALSE COMMENT 'Requester confirmed physical handover',
    owner_return_confirmed BOOLEAN NOT NULL DEFAULT FALSE COMMENT 'Owner confirmed book returned (lending)',
    requester_return_confirmed BOOLEAN NOT NULL DEFAULT FALSE COMMENT 'Requester confirmed book returned (lending)',
    handover_location VARCHAR(255) NULL COMMENT 'Agreed campus meeting location',
    handover_notes TEXT NULL COMMENT 'Coordination notes or instructions',
    start_date DATE NULL COMMENT 'Handover completion date / loan start date',
    due_date DATE NULL COMMENT 'Agreed return deadline (for LENDING mode)',
    return_date DATE NULL COMMENT 'Actual return date (for LENDING mode)',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_exchanges_request FOREIGN KEY (request_id) REFERENCES exchange_requests(id) ON DELETE RESTRICT,
    CONSTRAINT fk_exchanges_book FOREIGN KEY (book_id) REFERENCES books(id) ON DELETE RESTRICT,
    CONSTRAINT fk_exchanges_offered_book FOREIGN KEY (offered_book_id) REFERENCES books(id) ON DELETE SET NULL,
    CONSTRAINT fk_exchanges_owner FOREIGN KEY (owner_id) REFERENCES students(id) ON DELETE RESTRICT,
    CONSTRAINT fk_exchanges_requester FOREIGN KEY (requester_id) REFERENCES students(id) ON DELETE RESTRICT,
    INDEX idx_exchanges_status (status),
    INDEX idx_exchanges_owner (owner_id),
    INDEX idx_exchanges_requester (requester_id)
) ENGINE=InnoDB COMMENT='Active and completed exchanges';

-- =====================================================================
-- TABLE: reviews
-- Description: Mutual peer reviews submitted after completed handovers
-- =====================================================================
CREATE TABLE reviews (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    exchange_id BIGINT NOT NULL COMMENT 'Associated exchange transaction',
    reviewer_id BIGINT NOT NULL COMMENT 'Student submitting the rating',
    reviewee_id BIGINT NOT NULL COMMENT 'Student being rated',
    rating TINYINT UNSIGNED NOT NULL CHECK (rating BETWEEN 1 AND 5) COMMENT '1 to 5 star rating',
    review_text TEXT NULL COMMENT 'Feedback comments',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_reviews_exchange FOREIGN KEY (exchange_id) REFERENCES exchanges(id) ON DELETE CASCADE,
    CONSTRAINT fk_reviews_reviewer FOREIGN KEY (reviewer_id) REFERENCES students(id) ON DELETE CASCADE,
    CONSTRAINT fk_reviews_reviewee FOREIGN KEY (reviewee_id) REFERENCES students(id) ON DELETE CASCADE,
    CONSTRAINT uk_reviews_exchange_reviewer UNIQUE (exchange_id, reviewer_id)
) ENGINE=InnoDB COMMENT='Student reputation ratings';

-- =====================================================================
-- SEED DATA (For Academic Testing & Demo)
-- =====================================================================
-- 1. Sample Students (Passwords hashed for 'Password@123' using PBKDF2WithHmacSHA256)
-- Hash: PBKDF2:65536:0123456789abcdef0123456789abcdef:e363d54d6489d14c40c0e57fbe90e7fe6256665c2c73d3e7b7e7ad663c8256e6
INSERT INTO students (id, university_id, full_name, email, phone, password_hash, department, semester, campus_name)
VALUES
(1, 'U2023CS001', 'Aarav Sharma', 'aarav.sharma@campus.edu', '+919876543210', 'PBKDF2:65536:0123456789abcdef0123456789abcdef:e363d54d6489d14c40c0e57fbe90e7fe6256665c2c73d3e7b7e7ad663c8256e6', 'Computer Science', 5, 'Main Campus'),
(2, 'U2023EC015', 'Priya Patel', 'priya.patel@campus.edu', '+919876543211', 'PBKDF2:65536:0123456789abcdef0123456789abcdef:e363d54d6489d14c40c0e57fbe90e7fe6256665c2c73d3e7b7e7ad663c8256e6', 'Electronics & Communication', 4, 'Main Campus'),
(3, 'U2022ME042', 'Rohan Verma', 'rohan.verma@campus.edu', '+919876543212', 'PBKDF2:65536:0123456789abcdef0123456789abcdef:e363d54d6489d14c40c0e57fbe90e7fe6256665c2c73d3e7b7e7ad663c8256e6', 'Mechanical Engineering', 6, 'North Campus'),
(4, 'U2024IS030', 'Ananya Iyer', 'ananya.iyer@campus.edu', '+919876543213', 'PBKDF2:65536:0123456789abcdef0123456789abcdef:e363d54d6489d14c40c0e57fbe90e7fe6256665c2c73d3e7b7e7ad663c8256e6', 'Information Science', 3, 'Main Campus');

-- 2. Sample Academic Books (Java, DBMS, DSA, Networks)
INSERT INTO books (id, owner_id, title, author, isbn, edition, course_code, subject, condition_grade, sharing_mode, status, description, lending_max_duration_days, swap_preferences)
VALUES
(1, 1, 'Effective Java', 'Joshua Bloch', '9780134685991', '3rd Edition', 'CS301', 'Java', 'LIKE_NEW', 'SWAP', 'AVAILABLE', 'Classic Java best practices guide. Crisp copy with minor pencil notations in concurrency chapter.', NULL, 'Looking for Operating Systems by Silberschatz or Computer Networks by Kurose & Ross'),
(2, 4, 'Database System Concepts', 'Abraham Silberschatz, Henry F. Korth', '9780078022159', '7th Edition', 'CS401', 'DBMS', 'GOOD', 'LENDING', 'AVAILABLE', 'Standard university DBMS syllabus textbook. Great condition, available for 30-day exam revision.', 30, NULL),
(3, 2, 'Introduction to Algorithms (CLRS)', 'Thomas H. Cormen, Charles E. Leiserson', '9780262033848', '3rd Edition', 'CS201', 'DSA', 'LIKE_NEW', 'LENDING', 'AVAILABLE', 'Hardcover edition. Available to lend for midterm preparation.', 45, NULL),
(4, 3, 'Computer Networking: A Top-Down Approach', 'James F. Kurose, Keith W. Ross', '9780136681557', '8th Edition', 'EC501', 'Networks', 'GOOD', 'SWAP', 'AVAILABLE', 'Excellent textbook covering transport layer, IP protocol and socket programming.', NULL, 'Interested in Database Management or Web Technologies'),
(5, 1, 'Fundamentals of Database Systems', 'Ramez Elmasri, Shamkant Navathe', '9780133970777', '7th Edition', 'CS401', 'DBMS', 'FAIR', 'GIVEAWAY', 'AVAILABLE', 'Senior student giving away free of cost to any junior who needs it for the upcoming semester!', NULL, NULL);

-- 3. Sample Exchange Request
INSERT INTO exchange_requests (id, book_id, requester_id, owner_id, exchange_mode, offered_book_id, proposed_duration_days, request_message, status, handover_location, handover_time)
VALUES
(1, 1, 2, 1, 'SWAP', 3, NULL, 'Hi Aarav, would love to swap my CLRS copy for your Effective Java for this semester.', 'PENDING', 'Central Library Ground Floor', '2026-11-22 14:00:00');
