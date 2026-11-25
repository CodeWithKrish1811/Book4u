-- =====================================================================
-- Book4u - Safe Production Database Schema Initialization Script
-- Engine: MySQL 8.x / Compatible with Core Java JDBC
-- Governance: Strictly Non-Destructive. Zero DROP, TRUNCATE, or Data Deletion.
-- Supported Sharing Modes: SWAP, GIVEAWAY, LENDING
-- =====================================================================

-- ---------------------------------------------------------------------
-- TABLE 1: students
-- Description: Verified university student accounts participating in P2P sharing
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS students (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    university_id VARCHAR(64) NOT NULL UNIQUE COMMENT 'Campus roll number or institutional ID',
    full_name VARCHAR(128) NOT NULL COMMENT 'Full student legal/campus name',
    email VARCHAR(255) NOT NULL UNIQUE COMMENT 'Institutional email address (@campus.edu)',
    phone VARCHAR(20) NULL COMMENT 'Contact phone number for in-person handover coordination',
    password_hash VARCHAR(255) NOT NULL COMMENT 'Securely hashed password (PBKDF2 / BCrypt)',
    department VARCHAR(128) NOT NULL COMMENT 'Academic department (e.g. Computer Science, Mechanical)',
    semester TINYINT UNSIGNED NOT NULL DEFAULT 1 COMMENT 'Current semester (1-12)',
    campus_name VARCHAR(128) NOT NULL DEFAULT 'Main Campus' COMMENT 'Specific university branch/campus',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_students_email (email),
    INDEX idx_students_dept (department),
    INDEX idx_students_campus (campus_name)
) ENGINE=InnoDB COMMENT='Registered university student accounts';

-- ---------------------------------------------------------------------
-- TABLE 2: books
-- Description: Academic textbooks listed for peer sharing
-- Strictly zero price, selling, or payment columns
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS books (
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
    INDEX idx_books_author (author),
    INDEX idx_books_isbn (isbn),
    INDEX idx_books_subject (subject),
    INDEX idx_books_mode (sharing_mode),
    INDEX idx_books_status (status),
    INDEX idx_books_status_created (status, created_at DESC),
    INDEX idx_books_owner_status_created (owner_id, status, created_at DESC),
    INDEX idx_books_subject_status (subject, status),
    INDEX idx_books_mode_status (sharing_mode, status)
) ENGINE=InnoDB COMMENT='Academic book listings';

-- ---------------------------------------------------------------------
-- TABLE 3: exchange_requests
-- Description: Formal requests initiated by students to request listed books
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS exchange_requests (
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
    INDEX idx_requests_status (status),
    INDEX idx_requests_dup_check (book_id, requester_id, status),
    INDEX idx_requests_owner_created (owner_id, created_at DESC),
    INDEX idx_requests_requester_created (requester_id, created_at DESC)
) ENGINE=InnoDB COMMENT='Book exchange requests';

-- ---------------------------------------------------------------------
-- TABLE 4: exchanges
-- Description: Accepted exchange agreements and active loan tracking
-- Supports dual physical handover confirmation and dual return confirmation
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS exchanges (
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
    INDEX idx_exchanges_requester (requester_id),
    INDEX idx_exchanges_book_status (book_id, status),
    INDEX idx_exchanges_owner_updated (owner_id, updated_at DESC),
    INDEX idx_exchanges_requester_updated (requester_id, updated_at DESC)
) ENGINE=InnoDB COMMENT='Active and completed exchanges';

-- ---------------------------------------------------------------------
-- TABLE 5: reviews
-- Description: Mutual peer reviews submitted after completed handovers
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS reviews (
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
