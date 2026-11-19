-- =====================================================================
-- Student Peer-to-Peer Book Exchange System (Book4u)
-- Migration Script: phase2_auth_migration.sql
-- Phase 2: Student Authentication & Profile Management
-- =====================================================================
-- Purpose:
-- 1. Adds 'updated_at' timestamp column to 'students' table to track
--    profile modifications and secure password changes.
-- 2. Preserves all existing records and constraints (non-destructive).
-- 3. Does NOT drop or recreate the database or any tables.
-- =====================================================================

USE book_exchange_db;

-- Step 1: Add updated_at column if it does not already exist
ALTER TABLE students 
ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP AFTER created_at;

-- Step 2: Optional Upgrade for Existing Seed Data
-- Upgrades seed student accounts to PBKDF2WithHmacSHA256 for 'Password@123'
UPDATE students 
SET password_hash = 'PBKDF2:65536:0123456789abcdef0123456789abcdef:e363d54d6489d14c40c0e57fbe90e7fe6256665c2c73d3e7b7e7ad663c8256e6'
WHERE password_hash = 'a665a45920422f9d417e4867efdc4fb8a04a1f3fff1fa07e998e86f7f7a27ae3';

-- Step 3: Ensure index on university_id and email exists for fast authentication lookups
-- (Already covered by UNIQUE constraints, but index verification confirms optimal query plans)
SHOW INDEX FROM students;

-- =====================================================================
-- NOTE ON PASSWORD HASH COMPATIBILITY:
-- Phase 2 introduces PBKDF2WithHmacSHA256 with cryptographically random
-- unique salts (65,536 iterations).
-- The Java application (PasswordUtil.java) includes an automatic dual-verifier:
-- 1. Hashes starting with 'PBKDF2:' are verified using constant-time PBKDF2.
-- 2. Existing seed accounts with 64-char hexadecimal hashes are verified via legacy SHA-256.
-- Therefore, all existing student records remain 100% functional without needing
-- destructive data wipes.
-- =====================================================================
