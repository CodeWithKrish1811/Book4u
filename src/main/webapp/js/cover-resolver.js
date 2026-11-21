/**
 * ============================================================================
 * cover-resolver.js - Book Cover Resolution, Caching & Card Generator
 * ============================================================================
 * Book4u: Student Peer-to-Peer Academic Book Exchange Platform
 *
 * Strict Lookup Priority:
 * 1. User-Supplied Real Book Covers (Computer Networks, Database Systems, DSA)
 * 1b. Existing authentic cover in book payload (verified local or remote URL)
 * 2. ISBN-13 (13 digits -> Open Library Covers API)
 * 3. ISBN-10 (10 digits -> Open Library Covers API)
 * 4. Exact Book Title + Author Curated Database (Clean Architecture, Effective Java, CLRS, etc.)
 * 5. Normalized Title Search fallback
 * 6. Clean neutral vector placeholder ("COVER PENDING") when cover is genuinely missing
 *
 * Features:
 * - Persistent caching in localStorage (avoids repeated lookups on refresh)
 * - Safe error handling (switches failed remote covers to neutral SVG without loops)
 * - Explicit console logging of missing covers for admin review
 * - 1:1 Square Card generator with circular wishlist heart button and mode badges
 * ============================================================================
 */

(function (window) {
    'use strict';

    /**
     * In-memory cache for fast synchronous lookup during DOM rendering.
     */
    const _memoryCache = Object.create(null);

    /**
     * ============================================================================
     * 1. AUTHORITATIVE USER-SUPPLIED REAL BOOK COVERS REGISTRY
     * ============================================================================
     * Specifically for physical textbooks photographed and supplied by campus students.
     * ============================================================================
     */
    const REAL_LOCAL_COVERS = [
        {
            id: 'database-system-concepts',
            title: 'Database System Concepts',
            edition: 'Seventh Edition (Indian Edition, McGraw Hill)',
            authors: ['abraham silberschatz', 'henry f. korth', 's. sudarshan', 'silberschatz', 'korth'],
            keywords: ['database system concepts', 'silberschatz database', 'korth database'],
            url: 'images/covers/database-system-concepts-silberschatz-7th.jpg'
        },
        {
            id: 'computer-networks',
            title: 'Computer Networks',
            edition: 'Khanna Book Publishing Edition',
            authors: ['sarika gupta', 'gaurav gupta', 'gupta'],
            keywords: ['computer networks', 'computer networking'],
            url: 'images/covers/computer-networks-gupta.jpg'
        },
        {
            id: 'dsa-cpp',
            title: 'DSA: Data Structures & Algorithms in C++',
            edition: 'Job-Ready Series C++ Edition 2026',
            authors: ['shreyash tripathi', 'tripathi'],
            keywords: ['dsa', 'data structures & algorithms in c++', 'data structures and algorithms in c++', 'dsa in c++'],
            url: 'images/covers/dsa-cpp-tripathi.webp'
        }
    ];

    /**
     * ============================================================================
     * 2. CURATED ACADEMIC TEXTBOOK DATABASE
     * ============================================================================
     * Standard university computer science and engineering textbooks.
     * Authoritative ISBNs mapped to Open Library Covers CDN.
     * ============================================================================
     */
    const KNOWN_CURATED_COVERS = [
        {
            key: 'clean-architecture',
            title: 'Clean Architecture',
            authors: ['robert c. martin', 'uncle bob', 'martin'],
            isbn: '9780134494166',
            url: 'https://covers.openlibrary.org/b/isbn/9780134494166-L.jpg'
        },
        {
            key: 'clean-code',
            title: 'Clean Code',
            authors: ['robert c. martin', 'uncle bob', 'martin'],
            isbn: '9780132350884',
            url: 'https://covers.openlibrary.org/b/isbn/9780132350884-L.jpg'
        },
        {
            key: 'effective-java',
            title: 'Effective Java',
            authors: ['joshua bloch', 'bloch'],
            isbn: '9780134685991',
            url: 'https://covers.openlibrary.org/b/isbn/9780134685991-L.jpg'
        },
        {
            key: 'introduction-to-algorithms',
            title: 'Introduction to Algorithms',
            authors: ['thomas h. cormen', 'charles e. leiserson', 'ronald l. rivest', 'clifford stein', 'cormen', 'clrs'],
            isbn: '9780262033848',
            url: 'https://covers.openlibrary.org/b/isbn/9780262033848-L.jpg'
        },
        {
            key: 'computer-networking-top-down',
            title: 'Computer Networking: A Top-Down Approach',
            authors: ['james f. kurose', 'keith w. ross', 'kurose', 'ross'],
            isbn: '9780132856201',
            url: 'https://covers.openlibrary.org/b/isbn/9780132856201-L.jpg'
        },
        {
            key: 'fundamentals-of-database-systems',
            title: 'Fundamentals of Database Systems',
            authors: ['ramez elmasri', 'shamkant b. navathe', 'elmasri', 'navathe'],
            isbn: '9780133970777',
            url: 'https://covers.openlibrary.org/b/isbn/9780133970777-L.jpg'
        },
        {
            key: 'operating-system-concepts',
            title: 'Operating System Concepts',
            authors: ['abraham silberschatz', 'peter b. galvin', 'greg gagne', 'silberschatz', 'galvin'],
            isbn: '9781118063330',
            url: 'https://covers.openlibrary.org/b/isbn/9781118063330-L.jpg'
        },
        {
            key: 'java-complete-reference',
            title: 'Java: The Complete Reference',
            authors: ['herbert schildt', 'schildt'],
            isbn: '9781260440232',
            url: 'https://covers.openlibrary.org/b/isbn/9781260440232-L.jpg'
        },
        {
            key: 'core-java-vol-1',
            title: 'Core Java Volume I: Fundamentals',
            authors: ['cay s. horstmann', 'horstmann'],
            isbn: '9780135166307',
            url: 'https://covers.openlibrary.org/b/isbn/9780135166307-L.jpg'
        },
        {
            key: 'discrete-mathematics',
            title: 'Discrete Mathematics and Its Applications',
            authors: ['kenneth h. rosen', 'rosen'],
            isbn: '9780073383095',
            url: 'https://covers.openlibrary.org/b/isbn/9780073383095-L.jpg'
        },
        {
            key: 'artificial-intelligence-modern-approach',
            title: 'Artificial Intelligence: A Modern Approach',
            authors: ['stuart russell', 'peter norvig', 'russell', 'norvig'],
            isbn: '9780134610993',
            url: 'https://covers.openlibrary.org/b/isbn/9780134610993-L.jpg'
        },
        {
            key: 'cracking-the-coding-interview',
            title: 'Cracking the Coding Interview',
            authors: ['gayle laakmann mcdowell', 'mcdowell'],
            isbn: '9780984782857',
            url: 'https://covers.openlibrary.org/b/isbn/9780984782857-L.jpg'
        },
        {
            key: 'design-patterns-gof',
            title: 'Design Patterns',
            authors: ['erich gamma', 'richard helm', 'ralph johnson', 'john vlissides', 'gang of four', 'gof'],
            isbn: '9780201633610',
            url: 'https://covers.openlibrary.org/b/isbn/9780201633610-L.jpg'
        },
        {
            key: 'refactoring-fowler',
            title: 'Refactoring: Improving the Design of Existing Code',
            authors: ['martin fowler', 'fowler'],
            isbn: '9780134757599',
            url: 'https://covers.openlibrary.org/b/isbn/9780134757599-L.jpg'
        }
    ];

    /**
     * Checks if a URL is an irrelevant generic Unsplash or placeholder image.
     */
    function isRandomOrUnsplashImage(url) {
        if (!url || typeof url !== 'string') return true;
        const lower = url.toLowerCase();
        return lower.includes('unsplash.com') ||
               lower.includes('photo-1544716278') ||
               lower.includes('photo-1532012164546') ||
               lower.includes('photo-1512820790803');
    }

    /**
     * Cleans an ISBN string into 10 or 13 alphanumeric chars.
     */
    function cleanIsbn(isbn) {
        if (!isbn) return null;
        const cleaned = String(isbn).replace(/[^0-9Xx]/g, '').toUpperCase();
        return (cleaned.length === 10 || cleaned.length === 13) ? cleaned : null;
    }

    /**
     * Escapes HTML strings for security.
     */
    function escapeHtml(str) {
        if (str === null || str === undefined) return '';
        return String(str)
            .replace(/&/g, '&amp;')
            .replace(/</g, '&lt;')
            .replace(/>/g, '&gt;')
            .replace(/"/g, '&quot;')
            .replace(/'/g, '&#039;');
    }

    /**
     * Stores a resolved cover in in-memory cache and localStorage.
     */
    function persistCache(bookId, cleanIsbn, coverUrl) {
        if (!coverUrl || coverUrl.startsWith('data:image/svg+xml')) return;
        if (bookId) {
            _memoryCache['id_' + bookId] = coverUrl;
        }
        if (cleanIsbn) {
            _memoryCache['isbn_' + cleanIsbn] = coverUrl;
        }
        try {
            if (typeof window.localStorage !== 'undefined') {
                if (bookId) window.localStorage.setItem('b4u_cover_id_' + bookId, coverUrl);
                if (cleanIsbn) window.localStorage.setItem('b4u_cover_isbn_' + cleanIsbn, coverUrl);
            }
        } catch (e) {
            // Storage quota or privacy mode - ignore
        }
    }

    /**
     * Retrieves a cached cover URL if available.
     */
    function getFromCache(bookId, cleanIsbn) {
        if (bookId && _memoryCache['id_' + bookId]) {
            return _memoryCache['id_' + bookId];
        }
        if (cleanIsbn && _memoryCache['isbn_' + cleanIsbn]) {
            return _memoryCache['isbn_' + cleanIsbn];
        }
        try {
            if (typeof window.localStorage !== 'undefined') {
                if (bookId) {
                    const cached = window.localStorage.getItem('b4u_cover_id_' + bookId);
                    if (cached && !isRandomOrUnsplashImage(cached)) {
                        _memoryCache['id_' + bookId] = cached;
                        return cached;
                    }
                }
                if (cleanIsbn) {
                    const cached = window.localStorage.getItem('b4u_cover_isbn_' + cleanIsbn);
                    if (cached && !isRandomOrUnsplashImage(cached)) {
                        _memoryCache['isbn_' + cleanIsbn] = cached;
                        return cached;
                    }
                }
            }
        } catch (e) {
            // Ignore
        }
        return null;
    }

    /**
     * Priority 1: Match against User-Supplied Real Book Covers.
     */
    function matchLocalCover(normTitle, normAuthor) {
        for (const entry of REAL_LOCAL_COVERS) {
            // Guard: ensure "Fundamentals of Database Systems" (Elmasri) does NOT hijack "Database System Concepts" (Silberschatz)
            if (entry.id === 'database-system-concepts' && normTitle.includes('fundamentals')) {
                continue;
            }

            const matchesKeyword = entry.keywords && entry.keywords.some(kw =>
                normTitle.includes(kw) || kw.includes(normTitle)
            );
            const matchesAuthor = entry.authors && entry.authors.some(a =>
                normAuthor.includes(a.toLowerCase())
            );

            if (matchesKeyword || matchesAuthor) {
                return entry.url;
            }
        }
        return null;
    }

    /**
     * Priority 4: Match against Curated CS Textbook Catalog.
     */
    function matchCuratedCover(normTitle, normAuthor) {
        for (const entry of KNOWN_CURATED_COVERS) {
            const entryTitleLower = entry.title.toLowerCase();
            const titleMatches = normTitle.includes(entryTitleLower) || entryTitleLower.includes(normTitle);
            const authorMatches = entry.authors && entry.authors.some(a =>
                normAuthor.includes(a.toLowerCase())
            );

            // Require either title match or author match with relevant topic
            if (titleMatches || (authorMatches && normTitle.length > 3)) {
                // Guard: Fundamentals of Database Systems vs Database System Concepts
                if (entry.key === 'fundamentals-of-database-systems' && !normTitle.includes('fundamentals')) {
                    continue;
                }
                return entry.url;
            }
        }
        return null;
    }

    /**
     * Priority 5: Normalized Title Search fallback.
     */
    function matchNormalizedTitle(normTitle, normAuthor) {
        if (!normTitle || normTitle.length < 4) return null;

        // Clean Architecture
        if (normTitle.includes('clean architecture')) {
            return 'https://covers.openlibrary.org/b/isbn/9780134494166-L.jpg';
        }
        // Clean Code
        if (normTitle.includes('clean code')) {
            return 'https://covers.openlibrary.org/b/isbn/9780132350884-L.jpg';
        }
        // Effective Java
        if (normTitle.includes('effective java') || (normTitle.includes('java') && normAuthor.includes('bloch'))) {
            return 'https://covers.openlibrary.org/b/isbn/9780134685991-L.jpg';
        }
        // Introduction to Algorithms (CLRS)
        if (normTitle.includes('algorithms') && (normTitle.includes('clrs') || normAuthor.includes('cormen') || normAuthor.includes('leiserson'))) {
            return 'https://covers.openlibrary.org/b/isbn/9780262033848-L.jpg';
        }
        // Computer Networking Kurose & Ross
        if (normTitle.includes('computer networking') && (normAuthor.includes('kurose') || normAuthor.includes('ross') || normTitle.includes('top-down'))) {
            return 'https://covers.openlibrary.org/b/isbn/9780132856201-L.jpg';
        }
        // Operating Systems Dinosaur
        if (normTitle.includes('operating system') && (normAuthor.includes('silberschatz') || normAuthor.includes('galvin'))) {
            return 'https://covers.openlibrary.org/b/isbn/9781118063330-L.jpg';
        }
        // Fundamentals of Database Systems Elmasri
        if (normTitle.includes('fundamentals of database') || (normTitle.includes('database') && normAuthor.includes('elmasri'))) {
            return 'https://covers.openlibrary.org/b/isbn/9780133970777-L.jpg';
        }
        // Java Complete Reference Schildt
        if (normTitle.includes('complete reference') && normTitle.includes('java')) {
            return 'https://covers.openlibrary.org/b/isbn/9781260440232-L.jpg';
        }

        return null;
    }

    /**
     * ============================================================================
     * MAIN RESOLVER FUNCTION
     * ============================================================================
     * Strict Lookup Cascade:
     * 1. Cache
     * 2. Priority 1: User-Supplied Real Local Book Covers
     * 3. Priority 1b: Authentic cover already stored in book payload
     * 4. Priority 2: ISBN-13
     * 5. Priority 3: ISBN-10
     * 6. Priority 4: Curated Textbook Database (Exact Title + Author)
     * 7. Priority 5: Normalized Title Search
     * 8. Priority 6: Clean Neutral SVG Placeholder (COVER PENDING)
     * ============================================================================
     */
    function resolveCoverUrl(book) {
        if (!book) {
            return getNeutralPlaceholder('Academic Textbook', 'Campus Peer');
        }

        const title = (book.title || '').trim();
        const author = (book.author || '').trim();
        const rawIsbn = book.isbn || '';
        const clean = cleanIsbn(rawIsbn);
        const bookId = book.id || null;

        // 1. Check persistent cache
        const cached = getFromCache(bookId, clean);
        if (cached) {
            return cached;
        }

        const normTitle = title.toLowerCase();
        const normAuthor = author.toLowerCase();

        // 2. Priority 1: User-Supplied Real Local Book Covers
        const localMatch = matchLocalCover(normTitle, normAuthor);
        if (localMatch) {
            persistCache(bookId, clean, localMatch);
            return localMatch;
        }

        // 3. Priority 1b: Check if book already has an authentic URL in payload
        if (book.imageUrl && !isRandomOrUnsplashImage(book.imageUrl)) {
            const rawUrl = String(book.imageUrl).trim();
            if (rawUrl.startsWith('images/covers/') ||
                rawUrl.startsWith('/images/covers/') ||
                rawUrl.startsWith('http://') ||
                rawUrl.startsWith('https://')) {
                persistCache(bookId, clean, rawUrl);
                return rawUrl;
            }
        }

        // 4. Priority 2: ISBN-13
        if (clean && clean.length === 13) {
            const isbnUrl = `https://covers.openlibrary.org/b/isbn/${clean}-L.jpg`;
            persistCache(bookId, clean, isbnUrl);
            return isbnUrl;
        }

        // 5. Priority 3: ISBN-10
        if (clean && clean.length === 10) {
            const isbnUrl = `https://covers.openlibrary.org/b/isbn/${clean}-L.jpg`;
            persistCache(bookId, clean, isbnUrl);
            return isbnUrl;
        }

        // 6. Priority 4: Exact Title + Author Curated Database
        const curatedMatch = matchCuratedCover(normTitle, normAuthor);
        if (curatedMatch) {
            persistCache(bookId, clean, curatedMatch);
            return curatedMatch;
        }

        // 7. Priority 5: Normalized Title Search
        const normalizedMatch = matchNormalizedTitle(normTitle, normAuthor);
        if (normalizedMatch) {
            persistCache(bookId, clean, normalizedMatch);
            return normalizedMatch;
        }

        // 8. Priority 6: Clean Neutral Fallback
        // Log missing covers for administrative review and future photograph uploads
        console.warn(`[CoverResolver] Cover not found for book: "${title}" by "${author}" (ISBN: ${rawIsbn || 'none'}). Displaying neutral placeholder.`);
        return getNeutralPlaceholder(title, author);
    }

    /**
     * Generates a neutral, attractive placeholder SVG data URI.
     * Features: neutral slate background (#F8FAFC), crisp typography with the actual
     * book title and author, clean outline book icon, and "COVER PENDING" badge.
     */
    function getNeutralPlaceholder(title, author) {
        const cleanTitle = (title || 'Academic Textbook').trim();
        const cleanAuthor = (author || 'Campus Student').trim();

        const svg = `
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 300 380" width="300" height="380">
    <defs>
        <linearGradient id="neutral-bg" x1="0" y1="0" x2="0" y2="1">
            <stop offset="0%" stop-color="#F8FAFC" />
            <stop offset="100%" stop-color="#F1F5F9" />
        </linearGradient>
    </defs>
    <!-- Background Frame -->
    <rect width="300" height="380" rx="6" fill="url(#neutral-bg)" stroke="#E2E8F0" stroke-width="1.5" />
    
    <!-- Spine Accent -->
    <rect x="0" y="0" width="10" height="380" fill="#CBD5E1" opacity="0.6" />
    <line x1="10" y1="0" x2="10" y2="380" stroke="#E2E8F0" stroke-width="1" />
    
    <!-- Minimalist Book Graphic -->
    <g transform="translate(150, 125)" stroke="#64748B" stroke-width="2" fill="none">
        <path d="M-26,-16 C-13,-22 0,-16 0,-16 C0,-16 13,-22 26,-16 L26,20 C13,14 0,20 0,20 C0,20 -13,14 -26,20 Z" />
        <line x1="0" y1="-16" x2="0" y2="20" />
    </g>
    
    <!-- Book Title -->
    <text x="150" y="205" text-anchor="middle" fill="#0F172A" font-family="-apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif" font-size="14.5" font-weight="700">
        ${escapeHtml(cleanTitle.length > 25 ? cleanTitle.slice(0, 25) + '...' : cleanTitle)}
    </text>
    
    <!-- Author -->
    <text x="150" y="232" text-anchor="middle" fill="#64748B" font-family="-apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif" font-size="12" font-style="italic">
        by ${escapeHtml(cleanAuthor.length > 28 ? cleanAuthor.slice(0, 28) + '...' : cleanAuthor)}
    </text>
    
    <!-- Neutral Placeholder Status Tag -->
    <g transform="translate(150, 305)">
        <rect x="-65" y="-12" width="130" height="24" rx="12" fill="#FFFFFF" stroke="#CBD5E1" stroke-width="1" />
        <text x="0" y="4" text-anchor="middle" fill="#475569" font-family="-apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif" font-size="9.5" font-weight="700" letter-spacing="0.5">COVER PENDING</text>
    </g>
</svg>`.trim();

        return 'data:image/svg+xml;charset=utf-8,' + encodeURIComponent(svg);
    }

    /**
     * Image error event handler: seamlessly replaces failed remote covers with
     * the neutral SVG placeholder without infinite loops.
     */
    function handleImageError(imgEl, title, author, subject) {
        if (!imgEl) return;
        imgEl.onerror = null; // Prevent recursion

        console.warn(`[CoverResolver] Image failed to load for "${title || 'textbook'}" (src: ${imgEl.src}). Swapped to neutral placeholder.`);
        imgEl.src = getNeutralPlaceholder(title, author);
        imgEl.classList.add('b4u-img-fallback');
    }

    /**
     * Generates a compact 1:1 square product card matching all user requirements:
     * - Exactly 1:1 square aspect ratio
     * - Contain-fitted cover with neutral background and soft shadow
     * - Circular wishlist heart button in top-right corner
     * - Exchange mode badge (SWAP / GIVEAWAY / LENDING) in top-left corner
     * - Compact typography: meta tags, title, author, owner avatar & name
     * - View Details action link
     */
    function createCardHtml(book, options = {}) {
        const id = book.id || 0;
        const title = book.title || 'Academic Textbook';
        const author = book.author || 'Academic Author';
        const subject = book.subject || 'Academic';
        const courseCode = book.courseCode || '';
        const condition = (book.conditionGrade || 'GOOD').replace(/_/g, ' ');
        const mode = book.sharingMode || 'SWAP';
        const ownerName = book.ownerName || 'Campus Peer';
        const ownerCampus = book.ownerCampus || '';

        const coverSrc = resolveCoverUrl(book);

        let modeBadgeClass = 'mode-swap';
        let modeIcon = '🔄';
        let modeLabel = 'SWAP';

        if (mode === 'GIVEAWAY') {
            modeBadgeClass = 'mode-giveaway';
            modeIcon = '🎁';
            modeLabel = 'GIVEAWAY';
        } else if (mode === 'LENDING') {
            modeBadgeClass = 'mode-lending';
            modeIcon = '⏱️';
            modeLabel = 'LENDING';
        }

        const isSaved = typeof window.Store !== 'undefined' && window.Store.isSaved(id);

        const bookPayload = JSON.stringify({
            id: id,
            title: title,
            author: author,
            coverImage: coverSrc,
            sharingMode: mode,
            subject: subject,
            conditionGrade: book.conditionGrade || 'GOOD',
            courseCode: courseCode
        }).replace(/"/g, '&quot;');

        const ownerInitial = ownerName.trim().charAt(0).toUpperCase() || 'S';
        const ctx = typeof window.APP_CONTEXT !== 'undefined' ? window.APP_CONTEXT : '';

        return `
        <div class="b4u-book-card" data-book-id="${id}">
            <!-- 1. Book Cover Section (top 58%) -->
            <div class="b4u-card-media">
                <a href="${ctx}/book-details.html?id=${id}" class="b4u-card-cover-link" aria-label="View details of ${escapeHtml(title)}">
                    <img src="${escapeHtml(coverSrc)}"
                         alt="${escapeHtml(title)}"
                         class="b4u-card-img"
                         loading="lazy"
                         onerror="CoverResolver.handleImageError(this, '${escapeHtml(title)}', '${escapeHtml(author)}', '${escapeHtml(subject)}')">
                </a>
                
                <!-- Exchange Mode Badge (top-left) -->
                <span class="b4u-mode-badge ${modeBadgeClass}">
                    <span class="mode-badge-icon">${modeIcon}</span>
                    <span class="mode-badge-label">${modeLabel}</span>
                </span>

                <!-- 2. Wishlist Heart Button (top-right circular button) -->
                <button class="b4u-save-btn b4u-wishlist-btn ${isSaved ? 'active' : ''}"
                        type="button"
                        aria-label="Save Book"
                        title="${isSaved ? 'Remove from Saved Books' : 'Save to Reading List'}"
                        onclick='Store.toggleSave(${bookPayload}, this); event.stopPropagation();'>
                    <svg class="b4u-heart-icon" width="16" height="16" viewBox="0 0 24 24" fill="${isSaved ? '#DC2626' : 'none'}" stroke="${isSaved ? '#DC2626' : 'currentColor'}" stroke-width="2">
                        <path d="M20.84 4.61a5.5 5.5 0 0 0-7.78 0L12 5.67l-1.06-1.06a5.5 5.5 0 0 0-7.78 7.78l1.06 1.06L12 21.23l7.78-7.78 1.06-1.06a5.5 5.5 0 0 0 0-7.78z"></path>
                    </svg>
                </button>
            </div>

            <!-- 3. Compact Book Information Section (bottom 42%) -->
            <div class="b4u-card-body">
                <div class="b4u-card-meta">
                    <span class="b4u-subject-tag">${escapeHtml(subject)}</span>
                    ${courseCode ? `<span class="b4u-course-tag">${escapeHtml(courseCode)}</span>` : ''}
                    <span class="b4u-condition-tag">${escapeHtml(condition)}</span>
                </div>

                <h3 class="b4u-card-title">
                    <a href="${ctx}/book-details.html?id=${id}" title="${escapeHtml(title)}">
                        ${escapeHtml(title)}
                    </a>
                </h3>

                <p class="b4u-card-author">by ${escapeHtml(author)}</p>

                <!-- 4. Owner & Details Action -->
                <div class="b4u-card-footer-row">
                    <div class="b4u-card-owner" title="Owner: ${escapeHtml(ownerName)}${ownerCampus ? ` &bull; ${escapeHtml(ownerCampus)}` : ''}">
                        <span class="b4u-owner-avatar">${ownerInitial}</span>
                        <span class="b4u-owner-name">${escapeHtml(ownerName)}</span>
                    </div>
                    <a href="${ctx}/book-details.html?id=${id}" class="b4u-btn-view-compact" aria-label="View details of ${escapeHtml(title)}">
                        View &rarr;
                    </a>
                </div>
            </div>
        </div>
        `.trim();
    }

    // Export to window
    window.CoverResolver = {
        resolve: resolveCoverUrl,
        getPlaceholderSvg: getNeutralPlaceholder,
        getNeutralPlaceholder: getNeutralPlaceholder,
        handleImageError: handleImageError,
        createCardHtml: createCardHtml,
        isRandomOrUnsplashImage: isRandomOrUnsplashImage,
        isGenericCoffeeImage: isRandomOrUnsplashImage,
        cleanIsbn: cleanIsbn,
        REAL_LOCAL_COVERS: REAL_LOCAL_COVERS,
        KNOWN_CURATED_COVERS: KNOWN_CURATED_COVERS
    };

})(window);
