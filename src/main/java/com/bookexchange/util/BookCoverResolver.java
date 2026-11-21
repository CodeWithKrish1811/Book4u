package com.bookexchange.util;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Server-side Book Cover Resolver.
 * Sanitizes URLs (removes generic/random Unsplash images), validates ISBNs,
 * and resolves authoritative textbook covers with strict lookup priority:
 * 1. User-supplied real physical textbook covers (Database System Concepts, Computer Networks, DSA)
 * 2. ISBN-13 (Open Library Covers API)
 * 3. ISBN-10 (Open Library Covers API)
 * 4. Exact Book Title + Author curated university textbook database
 * 5. Normalized Title keyword search
 */
public final class BookCoverResolver {

    private BookCoverResolver() {}

    /**
     * User-Supplied Real Book Covers registry.
     * Maps canonical book keys to the local relative cover paths.
     */
    public static final String COVER_DBMS_SILBERSCHATZ = "images/covers/database-system-concepts-silberschatz-7th.jpg";
    public static final String COVER_COMPUTER_NETWORKS_GUPTA = "images/covers/computer-networks-gupta.jpg";
    public static final String COVER_DSA_TRIPATHI = "images/covers/dsa-cpp-tripathi.webp";

    /**
     * Curated catalog of standard academic computer science textbooks.
     * Maps title keywords to authoritative Open Library Covers ISBNs.
     */
    private static final Map<String, String> CURATED_TITLES;

    static {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("clean architecture", "9780134494166");
        map.put("clean code", "9780132350884");
        map.put("effective java", "9780134685991");
        map.put("introduction to algorithms", "9780262033848");
        map.put("clrs", "9780262033848");
        map.put("computer networking: a top-down approach", "9780132856201");
        map.put("operating system concepts", "9781118063330");
        map.put("fundamentals of database systems", "9780133970777");
        map.put("java: the complete reference", "9781260440232");
        map.put("core java", "9780135166307");
        map.put("discrete mathematics", "9780073383095");
        map.put("artificial intelligence: a modern approach", "9780134610993");
        map.put("cracking the coding interview", "9780984782857");
        map.put("design patterns", "9780201633610");
        map.put("refactoring", "9780134757599");
        CURATED_TITLES = Collections.unmodifiableMap(map);
    }

    /**
     * Checks if a URL points to a random/generic Unsplash image.
     */
    public static boolean isUnsplashOrGenericImage(String url) {
        if (url == null || url.isBlank()) {
            return false;
        }
        String lower = url.toLowerCase(Locale.ROOT);
        return lower.contains("unsplash.com") || lower.contains("photo-1544716278");
    }

    /**
     * Strips hyphens, spaces, and non-alphanumeric chars from ISBN.
     * Returns 10 or 13 char clean ISBN, or null if invalid.
     */
    public static String cleanIsbn(String rawIsbn) {
        if (rawIsbn == null || rawIsbn.isBlank()) {
            return null;
        }
        String cleaned = rawIsbn.replaceAll("[^0-9Xx]", "").toUpperCase(Locale.ROOT);
        return (cleaned.length() == 10 || cleaned.length() == 13) ? cleaned : null;
    }

    /**
     * Builds the Open Library Covers API URL for a clean ISBN.
     */
    public static String buildOpenLibraryCoverUrl(String cleanIsbn) {
        if (cleanIsbn == null || (cleanIsbn.length() != 10 && cleanIsbn.length() != 13)) {
            return null;
        }
        return "https://covers.openlibrary.org/b/isbn/" + cleanIsbn + "-L.jpg";
    }

    /**
     * Resolves the canonical cover image URL according to strict priority:
     * 1. User-supplied real physical textbook covers
     * 2. ISBN-13
     * 3. ISBN-10
     * 4. Curated title + author match
     * 5. Normalized title search fallback
     *
     * @param title Book title
     * @param author Book author
     * @param rawIsbn Raw ISBN string
     * @param rawImageUrl Existing or user-provided image URL
     * @return Resolved cover URL, or null if no authentic cover found
     */
    public static String resolveCoverUrl(String title, String author, String rawIsbn, String rawImageUrl) {
        // If an existing valid image URL is provided (and not Unsplash), preserve it
        if (rawImageUrl != null && !rawImageUrl.isBlank() && !isUnsplashOrGenericImage(rawImageUrl)) {
            String trimmed = rawImageUrl.trim();
            if (trimmed.startsWith("images/covers/") || trimmed.startsWith("/images/covers/") ||
                trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
                return trimmed;
            }
        }

        String normTitle = (title != null) ? title.toLowerCase(Locale.ROOT).trim() : "";
        String normAuthor = (author != null) ? author.toLowerCase(Locale.ROOT).trim() : "";

        // Priority 1: User-Supplied Real Book Covers
        // Database System Concepts (Silberschatz)
        if (normTitle.contains("database system concepts") ||
            (normTitle.contains("database") && (normAuthor.contains("silberschatz") || normAuthor.contains("korth")))) {
            if (!normTitle.contains("fundamentals")) {
                return COVER_DBMS_SILBERSCHATZ;
            }
        }

        // Computer Networks (Gupta)
        if (normTitle.contains("computer networks") && (normAuthor.contains("gupta") || normAuthor.isBlank())) {
            return COVER_COMPUTER_NETWORKS_GUPTA;
        }

        // DSA (Tripathi)
        if (normTitle.contains("dsa") && (normAuthor.contains("tripathi") || normTitle.contains("shreyash"))) {
            return COVER_DSA_TRIPATHI;
        }

        // Priority 2 & 3: Clean ISBN (ISBN-13 then ISBN-10)
        String cleaned = cleanIsbn(rawIsbn);
        if (cleaned != null) {
            return buildOpenLibraryCoverUrl(cleaned);
        }

        // Priority 4 & 5: Curated title / author catalog matching
        for (Map.Entry<String, String> entry : CURATED_TITLES.entrySet()) {
            if (normTitle.contains(entry.getKey())) {
                return buildOpenLibraryCoverUrl(entry.getValue());
            }
        }

        // Author-specific textbook matches
        if (normAuthor.contains("bloch") && normTitle.contains("java")) {
            return buildOpenLibraryCoverUrl("9780134685991"); // Effective Java
        }
        if (normAuthor.contains("cormen") || normAuthor.contains("leiserson") || normTitle.contains("clrs")) {
            return buildOpenLibraryCoverUrl("9780262033848"); // CLRS
        }
        if (normAuthor.contains("kurose") || normAuthor.contains("ross")) {
            return buildOpenLibraryCoverUrl("9780132856201"); // Kurose & Ross
        }
        if (normAuthor.contains("elmasri") || normAuthor.contains("navathe")) {
            return buildOpenLibraryCoverUrl("9780133970777"); // Fundamentals of Database Systems
        }
        if (normAuthor.contains("galvin") || (normAuthor.contains("silberschatz") && normTitle.contains("operating"))) {
            return buildOpenLibraryCoverUrl("9781118063330"); // Operating System Concepts
        }
        if (normAuthor.contains("robert c. martin") || normAuthor.contains("uncle bob")) {
            if (normTitle.contains("architecture")) {
                return buildOpenLibraryCoverUrl("9780134494166");
            }
            if (normTitle.contains("code")) {
                return buildOpenLibraryCoverUrl("9780132350884");
            }
        }

        return null;
    }
}
