/**
 * ============================================================================
 * Student Peer-to-Peer Book Exchange System - Frontend Client Application
 * Technology: Vanilla JavaScript (ES6+), Fetch API, DOM Manipulation
 * Integrates directly with Java Servlets (/api/health, /api/books)
 * ============================================================================
 */

document.addEventListener('DOMContentLoaded', () => {
    // Initialize components
    checkSystemHealth();
    loadAvailableBooks();
    setupEventListeners();
});

/**
 * Pings the Java Servlet health check endpoint: GET /api/health
 * Tests Tomcat container status and live MySQL JDBC connectivity.
 */
async function checkSystemHealth() {
    const healthDot = document.getElementById('health-dot');
    const healthText = document.getElementById('health-text');
    if (!healthDot || !healthText) return;

    try {
        const response = await fetch('api/health');
        if (response.ok) {
            const data = await response.json();
            if (data.databaseStatus === 'CONNECTED') {
                healthDot.className = 'health-dot dot-green';
                healthText.textContent = 'Tomcat & MySQL: Connected';
            } else {
                healthDot.className = 'health-dot dot-amber';
                healthText.textContent = 'Server: Up | DB: Awaiting Connection';
            }
        } else {
            healthDot.className = 'health-dot dot-amber';
            healthText.textContent = 'Server: HTTP ' + response.status;
        }
    } catch (err) {
        healthDot.className = 'health-dot dot-amber';
        healthText.textContent = 'Server Offline (Deploy to Tomcat)';
    }
}

/**
 * Fetches available books from GET /api/books and renders them on the page.
 */
async function loadAvailableBooks(subject = '', mode = '') {
    const grid = document.getElementById('book-grid') || document.getElementById('recent-book-grid');
    if (!grid) return;

    grid.innerHTML = '<p style="grid-column: 1/-1; text-align: center; color: var(--color-muted); padding: 2rem;">Loading campus textbooks...</p>';

    let url = 'api/books';
    const params = new URLSearchParams();
    if (subject && subject !== 'ALL') params.append('subject', subject);
    if (mode && mode !== 'ALL') params.append('mode', mode);
    if (params.toString()) url += '?' + params.toString();

    try {
        const response = await fetch(url);
        if (!response.ok) {
            throw new Error('Server returned HTTP ' + response.status);
        }

        const res = await response.json();
        const books = Array.isArray(res) ? res : (res.books || []);
        renderBookCards(books);

    } catch (err) {
        console.warn('API unreachable, displaying campus catalog preview:', err);
        // Fallback sample academic listings for clean preview if API is not deployed locally
        const sampleBooks = [
            {
                id: 1,
                title: "DSA: Data Structures & Algorithms in C++",
                author: "Shreyash Tripathi",
                isbn: "9781234567890",
                subject: "DSA",
                courseCode: "CS210",
                conditionGrade: "LIKE_NEW",
                sharingMode: "SWAP",
                swapPreferences: "Looking for Database Systems or Operating Systems",
                ownerName: "Aarav Sharma",
                ownerCampus: "North Campus Library",
                imageUrl: "images/covers/dsa-cpp-tripathi.webp"
            },
            {
                id: 2,
                title: "Database System Concepts (7th Edition)",
                author: "Abraham Silberschatz, Henry F. Korth",
                isbn: "9780078022159",
                subject: "DBMS",
                courseCode: "CS304",
                conditionGrade: "VERY_GOOD",
                sharingMode: "LENDING",
                lendingMaxDurationDays: 30,
                ownerName: "Priya Patel",
                ownerCampus: "Central Campus",
                imageUrl: "images/covers/database-system-concepts-silberschatz-7th.jpg"
            },
            {
                id: 3,
                title: "Computer Networks",
                author: "Sarika Gupta, Gaurav Gupta",
                isbn: "9789386173263",
                subject: "Networks",
                courseCode: "EC501",
                conditionGrade: "GOOD",
                sharingMode: "SWAP",
                swapPreferences: "Looking for Database Management or Operating Systems",
                ownerName: "Devendra Rao",
                ownerCampus: "Engineering Quad",
                imageUrl: "images/covers/computer-networks-gupta.jpg"
            },
            {
                id: 4,
                title: "Java: The Complete Reference (12th Edition)",
                author: "Herbert Schildt",
                isbn: "9781260440232",
                subject: "Java",
                courseCode: "CS201",
                conditionGrade: "GOOD",
                sharingMode: "GIVEAWAY",
                ownerName: "Rohan Verma",
                ownerCampus: "Engineering Quad",
                imageUrl: "https://covers.openlibrary.org/b/isbn/9781260440232-L.jpg"
            },
            {
                id: 5,
                title: "Operating System Concepts (10th Edition)",
                author: "Abraham Silberschatz, Peter B. Galvin",
                isbn: "9781118063330",
                subject: "Operating Systems",
                courseCode: "CS204",
                conditionGrade: "LIKE_NEW",
                sharingMode: "SWAP",
                swapPreferences: "Looking for Computer Networks or Compiler Design",
                ownerName: "Ananya Iyer",
                ownerCampus: "Central Library Block B",
                imageUrl: "https://covers.openlibrary.org/b/isbn/9781118063330-L.jpg"
            },
            {
                id: 6,
                title: "Computer Networking: A Top-Down Approach",
                author: "James F. Kurose, Keith W. Ross",
                isbn: "9780132856201",
                subject: "Networks",
                courseCode: "EC501",
                conditionGrade: "GOOD",
                sharingMode: "SWAP",
                swapPreferences: "Interested in Database Management or Web Technologies",
                ownerName: "Devendra Rao",
                ownerCampus: "Main Campus Library",
                imageUrl: "https://covers.openlibrary.org/b/isbn/9780132856201-L.jpg"
            },
            {
                id: 7,
                title: "Effective Java (3rd Edition)",
                author: "Joshua Bloch",
                isbn: "9780134685991",
                subject: "Java",
                courseCode: "CS301",
                conditionGrade: "LIKE_NEW",
                sharingMode: "LENDING",
                lendingMaxDurationDays: 21,
                ownerName: "Aarav Sharma",
                ownerCampus: "North Campus Library",
                imageUrl: "https://covers.openlibrary.org/b/isbn/9780134685991-L.jpg"
            },
            {
                id: 8,
                title: "Fundamentals of Database Systems",
                author: "Ramez Elmasri, Shamkant Navathe",
                isbn: "9780133970777",
                subject: "DBMS",
                courseCode: "CS401",
                conditionGrade: "FAIR",
                sharingMode: "GIVEAWAY",
                ownerName: "Priya Patel",
                ownerCampus: "Central Campus",
                imageUrl: "https://covers.openlibrary.org/b/isbn/9780133970777-L.jpg"
            },
            {
                id: 9,
                title: "Discrete Mathematics and Its Applications",
                author: "Kenneth H. Rosen",
                isbn: "9780073383095",
                subject: "Mathematics",
                courseCode: "MTH102",
                conditionGrade: "GOOD",
                sharingMode: "LENDING",
                lendingMaxDurationDays: 14,
                ownerName: "Rohan Verma",
                ownerCampus: "Main Campus",
                imageUrl: "https://covers.openlibrary.org/b/isbn/9780073383095-L.jpg"
            },
            {
                id: 10,
                title: "Core Java Volume I: Fundamentals",
                author: "Cay S. Horstmann",
                isbn: "9780135166307",
                subject: "Java",
                courseCode: "CS201",
                conditionGrade: "VERY_GOOD",
                sharingMode: "GIVEAWAY",
                ownerName: "Sneha Nair",
                ownerCampus: "Science Block Lobby",
                imageUrl: "https://covers.openlibrary.org/b/isbn/9780135166307-L.jpg"
            },
            {
                id: 11,
                title: "Artificial Intelligence: A Modern Approach",
                author: "Stuart Russell, Peter Norvig",
                isbn: "9780134610993",
                subject: "Artificial Intelligence",
                courseCode: "CS480",
                conditionGrade: "LIKE_NEW",
                sharingMode: "SWAP",
                swapPreferences: "Looking for Deep Learning (Goodfellow)",
                ownerName: "Ananya Iyer",
                ownerCampus: "Technology Hub",
                imageUrl: "https://covers.openlibrary.org/b/isbn/9780134610993-L.jpg"
            },
            {
                id: 12,
                title: "Cracking the Coding Interview (6th Edition)",
                author: "Gayle Laakmann McDowell",
                isbn: "9780984782857",
                subject: "Interview Prep",
                courseCode: "CS390",
                conditionGrade: "GOOD",
                sharingMode: "LENDING",
                lendingMaxDurationDays: 30,
                ownerName: "Devendra Rao",
                ownerCampus: "Career Services Lobby",
                imageUrl: "https://covers.openlibrary.org/b/isbn/9780984782857-L.jpg"
            }
        ];
        renderBookCards(sampleBooks);
    }
}

/**
 * Generates DOM cards for books in modern academic peer-exchange style.
 */
function renderBookCards(books) {
    const grid = document.getElementById('book-grid') || document.getElementById('recent-book-grid');
    if (!grid) return;

    if (!books || books.length === 0) {
        grid.innerHTML = '<p style="grid-column: 1/-1; text-align: center; color: var(--color-muted); padding: 3rem; font-size: 1rem;">No academic textbooks currently match this filter. Try selecting "All Subjects" or "All Modes".</p>';
        return;
    }

    grid.innerHTML = books.map(book => {
        if (typeof CoverResolver !== 'undefined') {
            return CoverResolver.createCardHtml(book);
        }
        const coverSrc = book.imageUrl || 'https://covers.openlibrary.org/b/isbn/' + (book.isbn || '9780262033848') + '-L.jpg';
        return `
            <div class="b4u-book-card">
                <div class="b4u-card-media">
                    <a href="book-details.html?id=${book.id}">
                        <img src="${coverSrc}" alt="${escapeHtml(book.title)}" class="b4u-card-img" loading="lazy">
                    </a>
                </div>
                <div class="b4u-card-body">
                    <h3 class="b4u-card-title"><a href="book-details.html?id=${book.id}">${escapeHtml(book.title)}</a></h3>
                    <p class="b4u-card-author">By ${escapeHtml(book.author || 'Author')}</p>
                </div>
            </div>
        `;
    }).join('');
}

function setupEventListeners() {
    // Filter by subject & mode
    const subjectFilter = document.getElementById('filter-subject');
    const modeFilter = document.getElementById('filter-mode');

    const applyFilters = () => {
        const subject = subjectFilter ? subjectFilter.value : '';
        const mode = modeFilter ? modeFilter.value : '';
        loadAvailableBooks(subject, mode);
    };

    if (subjectFilter) subjectFilter.addEventListener('change', applyFilters);
    if (modeFilter) modeFilter.addEventListener('change', applyFilters);

    // Modal controls for listing a book
    const listModal = document.getElementById('list-modal');
    const openModalBtn = document.getElementById('btn-open-list-modal');
    const closeModalBtn = document.getElementById('btn-close-modal');
    const newBookForm = document.getElementById('new-book-form');

    if (openModalBtn && listModal) {
        openModalBtn.addEventListener('click', () => {
            listModal.classList.add('active');
        });
    }

    if (closeModalBtn && listModal) {
        closeModalBtn.addEventListener('click', () => {
            listModal.classList.remove('active');
        });
    }

    // Toggle conditional lending days input
    const modeSelect = document.getElementById('book-mode-select');
    const lendingGroup = document.getElementById('lending-duration-group');
    if (modeSelect && lendingGroup) {
        modeSelect.addEventListener('change', () => {
            if (modeSelect.value === 'LENDING') {
                lendingGroup.style.display = 'block';
            } else {
                lendingGroup.style.display = 'none';
            }
        });
    }

    // Submit new book form to POST /api/books
    if (newBookForm) {
        newBookForm.addEventListener('submit', async (e) => {
            e.preventDefault();

            const payload = {
                title: document.getElementById('book-title-input').value,
                author: document.getElementById('book-author-input').value,
                subject: document.getElementById('book-subject-input').value,
                courseCode: document.getElementById('book-course-input').value,
                sharingMode: document.getElementById('book-mode-select').value,
                conditionGrade: document.getElementById('book-condition-select').value,
                lendingMaxDurationDays: modeSelect.value === 'LENDING' ? parseInt(document.getElementById('book-days-input').value, 10) : null,
                description: document.getElementById('book-desc-input').value,
                swapPreferences: document.getElementById('book-swap-input').value
            };

            try {
                const response = await fetch('api/books', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify(payload)
                });

                const result = await response.json();
                if (response.status === 401) {
                    alert('Authentication required! Please sign in with your student account to list a book.');
                    window.location.href = 'login.html?authRequired=true';
                    return;
                }
                if (response.ok) {
                    alert('Success! Your book listing has been added to the campus catalog.');
                    newBookForm.reset();
                    if (listModal) listModal.classList.remove('active');
                    loadAvailableBooks();
                } else {
                    alert('Error: ' + (result.error || 'Failed to list book'));
                }
            } catch (err) {
                alert('Connection error: ' + err.message);
            }
        });
    }
}
