-- =====================================================================
-- Book4u - Database Migration V1: Initial Baseline Schema
-- Description: Historical snapshot of initial Phase 1 foundation schema
-- =====================================================================

CREATE DATABASE IF NOT EXISTS book4u_db
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

USE book4u_db;

CREATE TABLE IF NOT EXISTS students (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    university_id VARCHAR(64) NOT NULL UNIQUE,
    email VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    full_name VARCHAR(128) NOT NULL,
    phone VARCHAR(20) NULL,
    department VARCHAR(128) NOT NULL,
    campus_name VARCHAR(128) NOT NULL DEFAULT 'Main Campus',
    reputation_score DECIMAL(3, 2) NOT NULL DEFAULT 5.00,
    status ENUM('PENDING_VERIFICATION', 'ACTIVE', 'SUSPENDED') NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_students_email (email),
    INDEX idx_students_dept (department),
    INDEX idx_students_status (status)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS book_listings (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    owner_id BIGINT NOT NULL,
    title VARCHAR(255) NOT NULL,
    author VARCHAR(255) NOT NULL,
    isbn VARCHAR(32) NULL,
    edition VARCHAR(32) NULL,
    course_code VARCHAR(32) NULL,
    department VARCHAR(128) NOT NULL,
    condition_grade ENUM('NEW', 'LIKE_NEW', 'GOOD', 'FAIR') NOT NULL DEFAULT 'GOOD',
    sharing_mode ENUM('SWAP', 'GIVEAWAY', 'LENDING') NOT NULL,
    status ENUM('AVAILABLE', 'RESERVED', 'EXCHANGED', 'ARCHIVED') NOT NULL DEFAULT 'AVAILABLE',
    swap_preferences TEXT NULL,
    lending_max_duration_days INT UNSIGNED NULL,
    description TEXT NULL,
    cover_image_url VARCHAR(512) NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_books_owner FOREIGN KEY (owner_id) REFERENCES students(id) ON DELETE CASCADE,
    INDEX idx_books_owner (owner_id),
    INDEX idx_books_sharing_mode (sharing_mode),
    INDEX idx_books_status (status),
    INDEX idx_books_course (course_code),
    INDEX idx_books_title (title)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS exchange_requests (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    listing_id BIGINT NOT NULL,
    requester_id BIGINT NOT NULL,
    owner_id BIGINT NOT NULL,
    exchange_mode ENUM('SWAP', 'GIVEAWAY', 'LENDING') NOT NULL,
    offered_book_id BIGINT NULL,
    proposed_duration_days INT UNSIGNED NULL,
    expected_return_date DATE NULL,
    actual_return_date DATE NULL,
    request_message TEXT NULL,
    status ENUM(
        'PENDING',
        'ACCEPTED',
        'REJECTED',
        'CANCELLED',
        'HANDOVER_SCHEDULED',
        'COMPLETED',
        'RETURNED'
    ) NOT NULL DEFAULT 'PENDING',
    handover_scheduled_at DATETIME NULL,
    handover_location VARCHAR(255) NULL,
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
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS book_passport_entries (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    book_id BIGINT NOT NULL,
    exchange_request_id BIGINT NULL,
    previous_holder_id BIGINT NOT NULL,
    new_holder_id BIGINT NOT NULL,
    exchange_mode ENUM('SWAP', 'GIVEAWAY', 'LENDING') NOT NULL,
    handover_campus_location VARCHAR(255) NULL,
    notes TEXT NULL,
    recorded_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_passport_book FOREIGN KEY (book_id) REFERENCES book_listings(id) ON DELETE CASCADE,
    CONSTRAINT fk_passport_request FOREIGN KEY (exchange_request_id) REFERENCES exchange_requests(id) ON DELETE SET NULL,
    CONSTRAINT fk_passport_prev_holder FOREIGN KEY (previous_holder_id) REFERENCES students(id) ON DELETE CASCADE,
    CONSTRAINT fk_passport_new_holder FOREIGN KEY (new_holder_id) REFERENCES students(id) ON DELETE CASCADE,
    INDEX idx_passport_book (book_id),
    INDEX idx_passport_recorded (recorded_at)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS student_reviews (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    exchange_request_id BIGINT NOT NULL,
    reviewer_id BIGINT NOT NULL,
    reviewee_id BIGINT NOT NULL,
    rating TINYINT UNSIGNED NOT NULL CHECK (rating BETWEEN 1 AND 5),
    review_text TEXT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_reviews_request FOREIGN KEY (exchange_request_id) REFERENCES exchange_requests(id) ON DELETE CASCADE,
    CONSTRAINT fk_reviews_reviewer FOREIGN KEY (reviewer_id) REFERENCES students(id) ON DELETE CASCADE,
    CONSTRAINT fk_reviews_reviewee FOREIGN KEY (reviewee_id) REFERENCES students(id) ON DELETE CASCADE,
    INDEX idx_reviews_reviewee (reviewee_id)
) ENGINE=InnoDB;
