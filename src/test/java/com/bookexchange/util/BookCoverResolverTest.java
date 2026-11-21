package com.bookexchange.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("BookCoverResolver Unit Tests")
class BookCoverResolverTest {

    @Test
    @DisplayName("Should detect and reject Unsplash and generic coffee images")
    void testIsUnsplashOrGenericImage() {
        assertTrue(BookCoverResolver.isUnsplashOrGenericImage("https://images.unsplash.com/photo-1544716278-ca5e3f4abd8c"));
        assertTrue(BookCoverResolver.isUnsplashOrGenericImage("https://images.unsplash.com/photo-1512820790803"));
        assertTrue(BookCoverResolver.isUnsplashOrGenericImage("http://unsplash.com/some-image.jpg"));
        assertFalse(BookCoverResolver.isUnsplashOrGenericImage("https://covers.openlibrary.org/b/isbn/9780134685991-L.jpg"));
        assertFalse(BookCoverResolver.isUnsplashOrGenericImage("images/covers/computer-networks-gupta.jpg"));
        assertFalse(BookCoverResolver.isUnsplashOrGenericImage(null));
        assertFalse(BookCoverResolver.isUnsplashOrGenericImage(""));
    }

    @Test
    @DisplayName("Should clean valid 10 and 13 digit ISBNs")
    void testCleanIsbn() {
        assertEquals("9780134685991", BookCoverResolver.cleanIsbn("978-0-13-468599-1"));
        assertEquals("0262033844", BookCoverResolver.cleanIsbn("0-262-03384-4"));
        assertEquals("0134494164", BookCoverResolver.cleanIsbn(" 0134494164 "));
        assertNull(BookCoverResolver.cleanIsbn("12345"));
        assertNull(BookCoverResolver.cleanIsbn(""));
        assertNull(BookCoverResolver.cleanIsbn(null));
    }

    @Test
    @DisplayName("Priority 1: Should resolve User-Supplied Real Book Covers")
    void testResolveUserSuppliedCovers() {
        // Database System Concepts (Silberschatz)
        String dbmsCover = BookCoverResolver.resolveCoverUrl("Database System Concepts", "Abraham Silberschatz, Henry F. Korth", null, null);
        assertEquals(BookCoverResolver.COVER_DBMS_SILBERSCHATZ, dbmsCover);

        // Computer Networks (Gupta)
        String netCover = BookCoverResolver.resolveCoverUrl("Computer Networks", "Sarika Gupta, Gaurav Gupta", null, null);
        assertEquals(BookCoverResolver.COVER_COMPUTER_NETWORKS_GUPTA, netCover);

        // DSA (Tripathi)
        String dsaCover = BookCoverResolver.resolveCoverUrl("DSA: Data Structures & Algorithms in C++", "Shreyash Tripathi", null, null);
        assertEquals(BookCoverResolver.COVER_DSA_TRIPATHI, dsaCover);

        // Guard: Fundamentals of Database Systems (Elmasri) must NOT hijack Silberschatz
        String elmasriCover = BookCoverResolver.resolveCoverUrl("Fundamentals of Database Systems", "Ramez Elmasri", null, null);
        assertNotEquals(BookCoverResolver.COVER_DBMS_SILBERSCHATZ, elmasriCover);
        assertEquals("https://covers.openlibrary.org/b/isbn/9780133970777-L.jpg", elmasriCover);
    }

    @Test
    @DisplayName("Priority 2 & 3: Should resolve valid ISBN-13 and ISBN-10 to Open Library")
    void testResolveByIsbn() {
        // Effective Java via ISBN-13
        String effJava = BookCoverResolver.resolveCoverUrl("Some Book", "Some Author", "978-0134685991", null);
        assertEquals("https://covers.openlibrary.org/b/isbn/9780134685991-L.jpg", effJava);

        // Clean Architecture via ISBN-13
        String cleanArch = BookCoverResolver.resolveCoverUrl("Clean Architecture", "Robert C. Martin", "978-0134494166", null);
        assertEquals("https://covers.openlibrary.org/b/isbn/9780134494166-L.jpg", cleanArch);
    }

    @Test
    @DisplayName("Priority 4: Should resolve curated title + author when ISBN is missing")
    void testResolveByCuratedCatalog() {
        // Introduction to Algorithms (CLRS)
        String clrs = BookCoverResolver.resolveCoverUrl("Introduction to Algorithms", "Thomas H. Cormen", null, null);
        assertEquals("https://covers.openlibrary.org/b/isbn/9780262033848-L.jpg", clrs);

        // Clean Architecture
        String cleanArch = BookCoverResolver.resolveCoverUrl("Clean Architecture", "Robert C. Martin", null, null);
        assertEquals("https://covers.openlibrary.org/b/isbn/9780134494166-L.jpg", cleanArch);

        // Operating System Concepts
        String os = BookCoverResolver.resolveCoverUrl("Operating System Concepts", "Abraham Silberschatz, Peter B. Galvin", null, null);
        assertEquals("https://covers.openlibrary.org/b/isbn/9781118063330-L.jpg", os);
    }

    @Test
    @DisplayName("Should discard Unsplash URL and resolve authentic cover instead")
    void testDiscardUnsplashUrl() {
        String effJavaWithUnsplash = BookCoverResolver.resolveCoverUrl(
            "Effective Java", "Joshua Bloch", "9780134685991", "https://images.unsplash.com/photo-1544716278-ca5e3f4abd8c"
        );
        assertEquals("https://covers.openlibrary.org/b/isbn/9780134685991-L.jpg", effJavaWithUnsplash);
    }

    @Test
    @DisplayName("Should return null for unknown book without cover so frontend can render neutral placeholder")
    void testUnknownBookFallback() {
        String unknown = BookCoverResolver.resolveCoverUrl("Obscure Local Campus Notes Vol 4", "Unknown Student 123", null, null);
        assertNull(unknown);
    }
}
