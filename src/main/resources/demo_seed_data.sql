-- =====================================================================
-- Book4u - Academic Demo Seed Data (TESTING & LOCAL DEMO ONLY)
-- NOT FOR PRODUCTION INITIALIZATION
-- =====================================================================

-- 1. Sample Students (Passwords hashed for 'Password@123' using PBKDF2WithHmacSHA256)
INSERT INTO students (id, university_id, full_name, email, phone, password_hash, department, semester, campus_name)
VALUES
(1, 'U2023CS001', 'Aarav Sharma', 'aarav.sharma@campus.edu', '+919876543210', 'PBKDF2:65536:0123456789abcdef0123456789abcdef:e363d54d6489d14c40c0e57fbe90e7fe6256665c2c73d3e7b7e7ad663c8256e6', 'Computer Science', 5, 'Main Campus'),
(2, 'U2023EC015', 'Priya Patel', 'priya.patel@campus.edu', '+919876543211', 'PBKDF2:65536:0123456789abcdef0123456789abcdef:e363d54d6489d14c40c0e57fbe90e7fe6256665c2c73d3e7b7e7ad663c8256e6', 'Electronics & Communication', 4, 'Main Campus'),
(3, 'U2022ME042', 'Rohan Verma', 'rohan.verma@campus.edu', '+919876543212', 'PBKDF2:65536:0123456789abcdef0123456789abcdef:e363d54d6489d14c40c0e57fbe90e7fe6256665c2c73d3e7b7e7ad663c8256e6', 'Mechanical Engineering', 6, 'North Campus'),
(4, 'U2024IS030', 'Ananya Iyer', 'ananya.iyer@campus.edu', '+919876543213', 'PBKDF2:65536:0123456789abcdef0123456789abcdef:e363d54d6489d14c40c0e57fbe90e7fe6256665c2c73d3e7b7e7ad663c8256e6', 'Information Science', 3, 'Main Campus');

-- 2. Sample Academic Books (Java, DBMS, DSA, Networks)
INSERT INTO books (id, owner_id, title, author, isbn, edition, course_code, subject, condition_grade, sharing_mode, status, description, image_url, lending_max_duration_days, swap_preferences)
VALUES
(1, 1, 'Effective Java', 'Joshua Bloch', '9780134685991', '3rd Edition', 'CS301', 'Java', 'LIKE_NEW', 'SWAP', 'AVAILABLE', 'Classic Java best practices guide. Crisp copy with minor pencil notations in concurrency chapter.', 'https://covers.openlibrary.org/b/isbn/9780134685991-L.jpg', NULL, 'Looking for Operating Systems by Silberschatz or Computer Networks by Kurose & Ross'),
(2, 4, 'Database System Concepts', 'Abraham Silberschatz, Henry F. Korth', '9780078022159', '7th Edition', 'CS401', 'DBMS', 'GOOD', 'LENDING', 'AVAILABLE', 'Standard university DBMS syllabus textbook. Great condition, available for 30-day exam revision.', 'https://covers.openlibrary.org/b/isbn/9780078022159-L.jpg', 30, NULL),
(3, 2, 'Introduction to Algorithms (CLRS)', 'Thomas H. Cormen, Charles E. Leiserson', '9780262033848', '3rd Edition', 'CS201', 'DSA', 'LIKE_NEW', 'LENDING', 'AVAILABLE', 'Hardcover edition. Available to lend for midterm preparation.', 'https://covers.openlibrary.org/b/isbn/9780262033848-L.jpg', 45, NULL),
(4, 3, 'Computer Networking: A Top-Down Approach', 'James F. Kurose, Keith W. Ross', '9780136681557', '8th Edition', 'EC501', 'Networks', 'GOOD', 'SWAP', 'AVAILABLE', 'Excellent textbook covering transport layer, IP protocol and socket programming.', 'https://covers.openlibrary.org/b/isbn/9780132856201-L.jpg', NULL, 'Interested in Database Management or Web Technologies'),
(5, 1, 'Fundamentals of Database Systems', 'Ramez Elmasri, Shamkant Navathe', '9780133970777', '7th Edition', 'CS401', 'DBMS', 'FAIR', 'GIVEAWAY', 'AVAILABLE', 'Senior student giving away free of cost to any junior who needs it for the upcoming semester!', 'https://covers.openlibrary.org/b/isbn/9780133970777-L.jpg', NULL, NULL);

-- 3. Sample Exchange Request
INSERT INTO exchange_requests (id, book_id, requester_id, owner_id, exchange_mode, offered_book_id, proposed_duration_days, request_message, status, handover_location, handover_time)
VALUES
(1, 1, 2, 1, 'SWAP', 3, NULL, 'Hi Aarav, would love to swap my CLRS copy for your Effective Java for this semester.', 'PENDING', 'Central Library Ground Floor', '2026-11-22 14:00:00');
