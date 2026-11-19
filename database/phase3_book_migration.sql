-- =====================================================================
-- Student Peer-to-Peer Book Exchange System (Book4u)
-- Migration Script: phase3_book_migration.sql
-- Phase 3: Book Management & Discovery
-- =====================================================================
-- Purpose:
-- 1. Adds 'image_url' to 'books' table to support optional textbook cover
--    images or student upload URLs.
-- 2. Adds composite search indexes on (status, sharing_mode) and (status, subject)
--    for high-performance discovery catalog filtering.
-- 3. Preserves all existing records and constraints (non-destructive).
-- =====================================================================

USE book_exchange_db;

-- Step 1: Add image_url column if not present
ALTER TABLE books 
ADD COLUMN IF NOT EXISTS image_url VARCHAR(512) NULL COMMENT 'Optional cover image or photo URL' AFTER description;

-- Step 2: Composite index for fast discovery filtering
ALTER TABLE books 
ADD INDEX IF NOT EXISTS idx_books_status_mode (status, sharing_mode);

ALTER TABLE books 
ADD INDEX IF NOT EXISTS idx_books_status_subject (status, subject);

-- Step 3: Populate sample cover images for initial seed books
UPDATE books SET image_url = 'https://images.unsplash.com/photo-1544716278-ca5e3f4abd8c?auto=format&fit=crop&w=400&q=80' WHERE id = 1;
UPDATE books SET image_url = 'https://images.unsplash.com/photo-1512820790803-83ca734da794?auto=format&fit=crop&w=400&q=80' WHERE id = 2;
UPDATE books SET image_url = 'https://images.unsplash.com/photo-1532012164546-f432f2e3edd4?auto=format&fit=crop&w=400&q=80' WHERE id = 3;
UPDATE books SET image_url = 'https://images.unsplash.com/photo-1524995997946-a1c2e315a42f?auto=format&fit=crop&w=400&q=80' WHERE id = 4;
UPDATE books SET image_url = 'https://images.unsplash.com/photo-1497633762265-9d179a990aa6?auto=format&fit=crop&w=400&q=80' WHERE id = 5;
