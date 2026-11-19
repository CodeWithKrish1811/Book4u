-- =====================================================================
-- Book4u - Fictional Test Seed & Demonstration Dataset (MySQL 8.x)
-- Purpose: Realistic dataset for development, testing, and system verification
-- Notice: Strictly fictional student profiles and academic textbook records.
-- Passwords are safe development BCrypt hashes (e.g., hashed for 'Password@123').
-- =====================================================================

USE book4u_db;

-- Clear previous sample records if testing
DELETE FROM moderation_reports;
DELETE FROM notifications;
DELETE FROM student_reviews;
DELETE FROM book_passport_entries;
DELETE FROM exchanges;
DELETE FROM exchange_requests;
DELETE FROM book_listings;
DELETE FROM students;

-- Reset auto-increment counters
ALTER TABLE students AUTO_INCREMENT = 1;
ALTER TABLE book_listings AUTO_INCREMENT = 1;
ALTER TABLE exchange_requests AUTO_INCREMENT = 1;
ALTER TABLE exchanges AUTO_INCREMENT = 1;
ALTER TABLE book_passport_entries AUTO_INCREMENT = 1;
ALTER TABLE student_reviews AUTO_INCREMENT = 1;
ALTER TABLE notifications AUTO_INCREMENT = 1;
ALTER TABLE moderation_reports AUTO_INCREMENT = 1;

-- ---------------------------------------------------------------------
-- 1. INSERT STUDENTS
-- ---------------------------------------------------------------------
INSERT INTO students (
    id, university_id, full_name, email, phone, password_hash, 
    department, semester, campus_name, profile_image_url, 
    reputation_score, verification_status, account_status
) VALUES
(1, 'U2022CS014', 'Aarav Sharma', 'aarav.sharma@campus.edu', '+919876543210', 
 '$2a$10$G0xP6L0h0e0aT7S/H8kQ..pZ6M01J4l1l1h1A1E1C1D1E1F1G1H1I', 
 'Computer Science', 5, 'Main Campus', 'https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=150', 
 4.95, 'VERIFIED', 'ACTIVE'),

(2, 'U2023EC028', 'Priya Patel', 'priya.patel@campus.edu', '+919876543211', 
 '$2a$10$G0xP6L0h0e0aT7S/H8kQ..pZ6M01J4l1l1h1A1E1C1D1E1F1G1H1I', 
 'Electronics & Communication', 4, 'Main Campus', 'https://images.unsplash.com/photo-1517841905240-472988babdf9?w=150', 
 4.88, 'VERIFIED', 'ACTIVE'),

(3, 'U2021ME055', 'Rohan Verma', 'rohan.verma@campus.edu', '+919876543212', 
 '$2a$10$G0xP6L0h0e0aT7S/H8kQ..pZ6M01J4l1l1h1A1E1C1D1E1F1G1H1I', 
 'Mechanical Engineering', 6, 'North Campus', 'https://images.unsplash.com/photo-1539571696357-5a69c17a67c6?w=150', 
 5.00, 'VERIFIED', 'ACTIVE'),

(4, 'U2023IS042', 'Ananya Iyer', 'ananya.iyer@campus.edu', '+919876543213', 
 '$2a$10$G0xP6L0h0e0aT7S/H8kQ..pZ6M01J4l1l1h1A1E1C1D1E1F1G1H1I', 
 'Information Science', 3, 'Main Campus', 'https://images.unsplash.com/photo-1524504388940-b1c1722653e1?w=150', 
 4.75, 'VERIFIED', 'ACTIVE'),

(5, 'U2021CS009', 'Vikram Nair', 'vikram.nair@campus.edu', '+919876543214', 
 '$2a$10$G0xP6L0h0e0aT7S/H8kQ..pZ6M01J4l1l1h1A1E1C1D1E1F1G1H1I', 
 'Computer Science', 7, 'South Campus', 'https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?w=150', 
 4.90, 'VERIFIED', 'ACTIVE'),

(6, 'U2024CV088', 'Sneha Kulkarni', 'sneha.kulkarni@campus.edu', '+919876543215', 
 '$2a$10$G0xP6L0h0e0aT7S/H8kQ..pZ6M01J4l1l1h1A1E1C1D1E1F1G1H1I', 
 'Civil Engineering', 2, 'Main Campus', 'https://images.unsplash.com/photo-1494790108377-be9c29b29330?w=150', 
 5.00, 'PENDING', 'ACTIVE');

-- ---------------------------------------------------------------------
-- 2. INSERT BOOK LISTINGS (Java, DBMS, DSA, Computer Networks)
-- ---------------------------------------------------------------------
INSERT INTO book_listings (
    id, owner_id, title, author, isbn, edition, course_code, 
    subject, department, semester, condition_grade, sharing_mode, 
    status, description, cover_image_url, lending_max_duration_days, swap_preferences
) VALUES
-- 1. Java Book (SWAP - Available)
(1, 1, 'Effective Java', 'Joshua Bloch', '9780134685991', '3rd Edition', 'CS301', 
 'Java Programming', 'Computer Science', 4, 'LIKE_NEW', 'SWAP', 'AVAILABLE', 
 'Masterpiece for best practices in modern Java. Clean copy with light pencil annotations in concurrency chapter.', 
 'https://images.isbndb.com/covers/59/91/9780134685991.jpg', NULL, 
 'Interested in Operating System Concepts by Silberschatz or Computer Networks by Kurose & Ross'),

-- 2. Java Book (LENDING - On Loan)
(2, 5, 'Java: The Complete Reference', 'Herbert Schildt', '9781260440232', '11th Edition', 'CS202', 
 'Object Oriented Programming', 'Computer Science', 3, 'GOOD', 'LENDING', 'ON_LOAN', 
 'Comprehensive reference covering Java SE 11. Available for semester mid-term exam preparation.', 
 'https://images.isbndb.com/covers/02/32/9781260440232.jpg', 30, NULL),

-- 3. DBMS Book (SWAP - Reserved)
(3, 2, 'Database System Concepts', 'Abraham Silberschatz, Henry F. Korth, S. Sudarshan', '9780078022159', '7th Edition', 'CS401', 
 'Database Systems', 'Computer Science', 4, 'LIKE_NEW', 'SWAP', 'RESERVED', 
 'Standard university DBMS syllabus textbook covering relational algebra, SQL, normalization, and concurrency control.', 
 'https://images.isbndb.com/covers/21/59/9780078022159.jpg', NULL, 
 'Looking for Computer Networks 5th Edition by Tanenbaum'),

-- 4. DBMS Book (GIVEAWAY - Available)
(4, 5, 'Fundamentals of Database Systems', 'Ramez Elmasri, Shamkant B. Navathe', '9780133970777', '7th Edition', 'IS402', 
 'Database Management', 'Information Science', 4, 'GOOD', 'GIVEAWAY', 'AVAILABLE', 
 'Graduating senior gifting this excellent database theory book to any junior student in need. No payment required.', 
 'https://images.isbndb.com/covers/07/77/9780133970777.jpg', NULL, NULL),

-- 5. DSA Book (LENDING - On Loan / Overdue Case)
(5, 3, 'Introduction to Algorithms (CLRS)', 'Thomas H. Cormen, Charles E. Leiserson, Ronald L. Rivest, Clifford Stein', '9780262033848', '3rd Edition', 'CS201', 
 'Data Structures & Algorithms', 'Computer Science', 3, 'LIKE_NEW', 'LENDING', 'ON_LOAN', 
 'Essential guide for algorithms and data structures course. Hardcover edition, excellent condition.', 
 'https://images.isbndb.com/covers/38/48/9780262033848.jpg', 45, NULL),

-- 6. DSA Book (SWAP - Available)
(6, 4, 'Data Structures and Algorithm Analysis in Java', 'Mark Allen Weiss', '9780132576277', '3rd Edition', 'CS201', 
 'Data Structures', 'Computer Science', 3, 'GOOD', 'SWAP', 'AVAILABLE', 
 'Includes code examples and algorithmic complexity proofs in Java. Great condition.', 
 'https://images.isbndb.com/covers/62/77/9780132576277.jpg', NULL, 
 'Looking to swap for Web Development or Database Systems books'),

-- 7. Computer Networks (SWAP - Available)
(7, 4, 'Computer Networking: A Top-Down Approach', 'James F. Kurose, Keith W. Ross', '9780136681557', '8th Edition', 'EC501', 
 'Computer Networks', 'Electronics & Communication', 5, 'NEW', 'SWAP', 'AVAILABLE', 
 'Brand new copy covering modern Internet protocols, transport layer, socket programming and Wi-Fi.', 
 'https://images.isbndb.com/covers/15/57/9780136681557.jpg', NULL, 
 'Interested in Machine Learning or Artificial Intelligence reference texts'),

-- 8. Computer Networks (GIVEAWAY - Unavailable / Completed)
(8, 5, 'Computer Networks', 'Andrew S. Tanenbaum, David J. Wetherall', '9780132126953', '5th Edition', 'CS502', 
 'Computer Networks', 'Computer Science', 5, 'FAIR', 'GIVEAWAY', 'UNAVAILABLE', 
 'Classic textbook by Tanenbaum. Successfully handed over to junior peer Ananya Iyer.', 
 'https://images.isbndb.com/covers/69/53/9780132126953.jpg', NULL, NULL);

-- ---------------------------------------------------------------------
-- 3. INSERT EXCHANGE REQUESTS
-- ---------------------------------------------------------------------
INSERT INTO exchange_requests (
    id, listing_id, requester_id, owner_id, offered_book_id, 
    exchange_mode, proposed_duration_days, expected_return_date, 
    request_message, status, handover_scheduled_at, handover_location
) VALUES
-- Request 1: Accepted SWAP (Listing 3 requested by Student 1, offered Listing 1)
(1, 3, 1, 2, 1, 'SWAP', NULL, NULL, 
 'Hi Priya! I need Database System Concepts for my upcoming DBMS lab. Would love to swap it for my copy of Effective Java.', 
 'ACCEPTED', '2026-11-21 15:30:00', 'Central Library Ground Floor Study Hall'),

-- Request 2: Accepted LENDING (Listing 2 requested by Student 4)
(2, 2, 4, 5, NULL, 'LENDING', 30, '2026-12-15', 
 'Hello Vikram, requesting your Java Complete Reference for my Object Oriented Programming semester exams.', 
 'ACCEPTED', '2026-11-15 11:00:00', 'Student Center Cafeteria'),

-- Request 3: Accepted LENDING (Listing 5 requested by Student 2 - Overdue case)
(3, 5, 2, 3, NULL, 'LENDING', 45, '2026-11-10', 
 'Hi Rohan, preparing for tech interviews and algorithms coursework, requesting CLRS for 45 days.', 
 'ACCEPTED', '2026-09-25 14:00:00', 'North Campus Engineering Block B'),

-- Request 4: Accepted GIVEAWAY (Listing 8 requested by Student 4)
(4, 8, 4, 5, NULL, 'GIVEAWAY', NULL, NULL, 
 'Hello senior Vikram! I would be grateful to receive your Tanenbaum Networks textbook for my 4th semester studies.', 
 'ACCEPTED', '2026-11-10 16:00:00', 'Campus Main Gate Fountain Area'),

-- Request 5: Pending SWAP (Listing 1 requested by Student 4, offering Listing 6)
(5, 1, 4, 1, 6, 'SWAP', NULL, NULL, 
 'Hi Aarav, offering Weiss Data Structures in Java in exchange for your Effective Java copy.', 
 'PENDING', NULL, NULL),

-- Request 6: Pending GIVEAWAY (Listing 4 requested by Student 1)
(6, 4, 1, 5, NULL, 'GIVEAWAY', NULL, NULL, 
 'Hi Vikram, interested in your Elmasri DBMS giveaway book if still available!', 
 'PENDING', NULL, NULL),

-- Request 7: Rejected SWAP
(7, 7, 3, 4, 5, 'SWAP', NULL, NULL, 
 'Can we swap Kurose Networks for CLRS?', 
 'REJECTED', NULL, NULL),

-- Request 8: Cancelled Request
(8, 1, 3, 1, 5, 'SWAP', NULL, NULL, 
 'Changed my schedule, cancelling request.', 
 'CANCELLED', NULL, NULL);

-- ---------------------------------------------------------------------
-- 4. INSERT EXCHANGES (Accepted agreements tracking physical handovers)
-- ---------------------------------------------------------------------
INSERT INTO exchanges (
    id, request_id, listing_id, exchange_mode, owner_id, requester_id, 
    offered_book_id, status, accepted_at, handover_scheduled_at, 
    handover_location, handover_completed_at, loan_start_date, due_date, 
    actual_return_date, owner_handover_confirmed, requester_handover_confirmed, 
    owner_return_confirmed, requester_return_confirmed
) VALUES
-- Exchange 1: SWAP - Awaiting physical handover on campus
(1, 1, 3, 'SWAP', 2, 1, 1, 
 'AWAITING_HANDOVER', '2026-11-18 10:00:00', '2026-11-21 15:30:00', 
 'Central Library Ground Floor Study Hall', NULL, NULL, NULL, NULL, 
 FALSE, FALSE, FALSE, FALSE),

-- Exchange 2: LENDING - Active loan in progress (Due Dec 15, 2026)
(2, 2, 2, 'LENDING', 5, 4, NULL, 
 'ACTIVE', '2026-11-15 09:30:00', '2026-11-15 11:00:00', 
 'Student Center Cafeteria', '2026-11-15 11:15:00', '2026-11-15', '2026-12-15', NULL, 
 TRUE, TRUE, FALSE, FALSE),

-- Exchange 3: LENDING - Overdue loan (Due Nov 10, 2026; currently unreturned)
(3, 3, 5, 'LENDING', 3, 2, NULL, 
 'OVERDUE', '2026-09-24 16:00:00', '2026-09-25 14:00:00', 
 'North Campus Engineering Block B', '2026-09-25 14:20:00', '2026-09-25', '2026-11-10', NULL, 
 TRUE, TRUE, FALSE, FALSE),

-- Exchange 4: GIVEAWAY - Completed with dual confirmation
(4, 4, 8, 'GIVEAWAY', 5, 4, NULL, 
 'COMPLETED', '2026-11-09 14:00:00', '2026-11-10 16:00:00', 
 'Campus Main Gate Fountain Area', '2026-11-10 16:10:00', NULL, NULL, NULL, 
 TRUE, TRUE, FALSE, FALSE);

-- ---------------------------------------------------------------------
-- 5. INSERT BOOK PASSPORT ENTRIES (Lineage & historical custody)
-- ---------------------------------------------------------------------
INSERT INTO book_passport_entries (
    id, book_id, exchange_id, previous_holder_id, new_holder_id, 
    exchange_mode, handover_campus_location, notes, recorded_at
) VALUES
-- Lineage for Computer Networks (Listing 8)
(1, 8, 4, 5, 4, 'GIVEAWAY', 'Campus Main Gate Fountain Area', 
 'Passed down to Ananya. Notes in Chapters 3 and 5 highlighting socket programming and TCP congestion control.', 
 '2026-11-10 16:10:00'),

-- Historical custody for Java Complete Reference (Listing 2)
(2, 2, 2, 5, 4, 'LENDING', 'Student Center Cafeteria', 
 'Temporarily loaned to Ananya for exam revision. Please keep pages 200-250 bookmark intact.', 
 '2026-11-15 11:15:00'),

-- Custody for CLRS (Listing 5)
(3, 5, 3, 3, 2, 'LENDING', 'North Campus Engineering Block B', 
 'Handed over to Priya for competitive programming prep. Clean margins.', 
 '2026-09-25 14:20:00');

-- ---------------------------------------------------------------------
-- 6. INSERT STUDENT REVIEWS (Reputation ratings after completed handovers)
-- ---------------------------------------------------------------------
INSERT INTO student_reviews (
    id, exchange_id, reviewer_id, reviewee_id, rating, review_text, created_at
) VALUES
-- Reviews for Completed Giveaway (Exchange 4)
(1, 4, 4, 5, 5, 
 'Vikram is an amazing senior! The Tanenbaum Networks book is in great readable condition. Punctual handover.', 
 '2026-11-11 10:30:00'),

(2, 4, 5, 4, 5, 
 'Pleasure sharing with Ananya. Very polite, arrived right on time at the campus gate.', 
 '2026-11-11 11:00:00');

-- ---------------------------------------------------------------------
-- 7. INSERT NOTIFICATIONS (Real-time student alerts)
-- ---------------------------------------------------------------------
INSERT INTO notifications (
    id, recipient_id, sender_id, notification_type, title, message, 
    reference_type, reference_id, is_read, created_at
) VALUES
-- Handover scheduled alert
(1, 1, 2, 'HANDOVER_SCHEDULED', 'Handover Meeting Scheduled', 
 'Priya Patel agreed to meet at Central Library Ground Floor on Nov 21 at 3:30 PM for your book swap.', 
 'EXCHANGE', 1, FALSE, '2026-11-18 10:15:00'),

-- Lending active alert
(2, 4, 5, 'HANDOVER_CONFIRMED', 'Book Handover Confirmed', 
 'You have received Java: The Complete Reference. Your loan period is active until Dec 15, 2026.', 
 'EXCHANGE', 2, TRUE, '2026-11-15 11:16:00'),

-- Lending overdue warning
(3, 2, NULL, 'LENDING_OVERDUE', 'Action Required: Book Return Overdue', 
 'Your loan for Introduction to Algorithms (CLRS) was due on Nov 10, 2026. Please coordinate handover with Rohan Verma.', 
 'EXCHANGE', 3, FALSE, '2026-11-11 08:00:00'),

-- Review received alert
(4, 5, 4, 'REVIEW_RECEIVED', 'New 5-Star Review Received', 
 'Ananya Iyer left you a 5-star review for the Computer Networks book handover.', 
 'STUDENT_REVIEW', 1, TRUE, '2026-11-11 10:31:00'),

-- New request alert
(5, 1, 4, 'REQUEST_RECEIVED', 'New Swap Request on Effective Java', 
 'Ananya Iyer requested your Effective Java book and offered Data Structures and Algorithm Analysis in return.', 
 'EXCHANGE_REQUEST', 5, FALSE, '2026-11-18 16:45:00');

-- ---------------------------------------------------------------------
-- 8. INSERT MODERATION REPORTS (Governance and dispute cases)
-- ---------------------------------------------------------------------
INSERT INTO moderation_reports (
    id, reporter_id, reported_student_id, reported_listing_id, exchange_id, 
    reason, details, status, admin_notes, resolved_at
) VALUES
-- Case 1: Overdue book dispute
(1, 3, 2, 5, 3, 'BOOK_NOT_RETURNED', 
 'The lending agreement for CLRS concluded on Nov 10. Student has not responded to return reminder messages on campus.', 
 'UNDER_REVIEW', 'Admin sent formal campus email reminder to Priya Patel. Pending student response before academic hold.', NULL),

-- Case 2: Resolved fake listing report
(2, 1, NULL, 6, NULL, 'FAKE_LISTING', 
 'Initially thought edition was mislabeled, but owner clarified it is 3rd Edition revision.', 
 'RESOLVED', 'Verified listing details match physical ISBN-10. Report dismissed as resolved.', '2026-11-16 14:00:00');
