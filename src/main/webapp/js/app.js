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
                title: "Core Java: Fundamentals, Volume 1 (12th Edition)",
                author: "Cay S. Horstmann",
                subject: "Computer Science",
                courseCode: "CS201",
                sharingMode: "SWAP",
                conditionGrade: "LIKE_NEW",
                swapPreferences: "Looking for DBMS Korth or OS Silberschatz",
                ownerName: "Aarav Sharma",
                imageUrl: "https://images.unsplash.com/photo-1517694712202-14dd9538aa97?w=500&q=80"
            },
            {
                id: 2,
                title: "Database System Concepts (7th Edition)",
                author: "Silberschatz, Korth, Sudarshan",
                subject: "Database Systems",
                courseCode: "CS304",
                sharingMode: "LENDING",
                lendingMaxDurationDays: 30,
                conditionGrade: "VERY_GOOD",
                ownerName: "Priya Patel",
                imageUrl: "https://images.unsplash.com/photo-1544716278-ca5e3f4abd8c?w=500&q=80"
            },
            {
                id: 3,
                title: "Introduction to Algorithms (CLRS 4th Edition)",
                author: "Cormen, Leiserson, Rivest, Stein",
                subject: "Algorithms",
                courseCode: "CS210",
                sharingMode: "SWAP",
                conditionGrade: "GOOD",
                swapPreferences: "Open to Machine Learning or Networks",
                ownerName: "Rohan Verma",
                imageUrl: "https://images.unsplash.com/photo-1532012164546-f432f2e3777f?w=500&q=80"
            },
            {
                id: 4,
                title: "Computer Networks: A Systems Approach",
                author: "Larry L. Peterson, Bruce S. Davie",
                subject: "Networking",
                courseCode: "CS350",
                sharingMode: "GIVEAWAY",
                conditionGrade: "GOOD",
                ownerName: "Ananya Iyer",
                imageUrl: "https://images.unsplash.com/photo-1456513080510-7bf3a84b82f8?w=500&q=80"
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

    grid.innerHTML = '';
    books.forEach(book => {
        const card = document.createElement('div');
        card.className = 'b4u-book-card';

        const coverSrc = book.imageUrl || 'https://images.unsplash.com/photo-1544716278-ca5e3f4abd8c?auto=format&fit=crop&w=400&q=80';
        
        let modeLabel = 'SWAP';
        let modeClass = 'mode-swap';
        let modeActionText = 'Request Swap';
        let modeDetail = book.swapPreferences ? `Wants: ${escapeHtml(book.swapPreferences)}` : 'Peer Exchange';

        if (book.sharingMode === 'GIVEAWAY') {
            modeLabel = 'GIVEAWAY';
            modeClass = 'mode-giveaway';
            modeActionText = 'Claim Free';
            modeDetail = 'Free Student Gift';
        } else if (book.sharingMode === 'LENDING') {
            modeLabel = 'LENDING';
            modeClass = 'mode-lending';
            const days = book.lendingMaxDurationDays || 14;
            modeActionText = `Borrow (${days}d)`;
            modeDetail = `Loan period: ${days} days`;
        }

        const isSaved = typeof Store !== 'undefined' && Store.isSaved(book.id);

        const bookPayload = JSON.stringify({
            id: book.id,
            title: book.title,
            author: book.author,
            coverImage: coverSrc,
            sharingMode: book.sharingMode,
            subject: book.subject || 'Academic',
            conditionGrade: book.conditionGrade || 'Good',
            courseCode: book.courseCode || ''
        }).replace(/"/g, '&quot;');

        const conditionLabel = (book.conditionGrade || 'GOOD').replace('_', ' ');

        card.innerHTML = `
            <div class="b4u-card-media">
                <a href="book-details.html?id=${book.id}">
                    <img src="${escapeHtml(coverSrc)}" alt="${escapeHtml(book.title)}" class="b4u-card-img" loading="lazy" onerror="this.src='https://images.unsplash.com/photo-1544716278-ca5e3f4abd8c?auto=format&fit=crop&w=400&q=80';">
                </a>
                <span class="b4u-mode-badge ${modeClass}">${modeLabel}</span>
                <button class="b4u-save-btn ${isSaved ? 'active' : ''}" type="button" aria-label="Save Book" onclick='Store.toggleSave(${bookPayload}, this)' title="Save to Reading List">
                    <svg width="18" height="18" viewBox="0 0 24 24" fill="${isSaved ? 'currentColor' : 'none'}" stroke="currentColor" stroke-width="2">
                        <path d="M19 21l-7-5-7 5V5a2 2 0 0 1 2-2h10a2 2 0 0 1 2 2z"></path>
                    </svg>
                </button>
            </div>
            <div class="b4u-card-body">
                <div class="b4u-card-meta">
                    <span class="b4u-subject-tag">${escapeHtml(book.subject || 'Academic')}</span>
                    ${book.courseCode ? `<span class="b4u-course-tag">${escapeHtml(book.courseCode)}</span>` : ''}
                    <span class="b4u-condition-tag">${escapeHtml(conditionLabel)}</span>
                </div>
                <h3 class="b4u-card-title">
                    <a href="book-details.html?id=${book.id}" title="${escapeHtml(book.title)}">${escapeHtml(book.title)}</a>
                </h3>
                <p class="b4u-card-author">By ${escapeHtml(book.author || 'Author')}</p>
                <div class="b4u-card-owner">
                    <span class="owner-icon">👤</span>
                    <span>${escapeHtml(book.ownerName || 'Verified Student')}</span>
                </div>
                <div class="b4u-card-mode-detail">
                    <span>${modeDetail}</span>
                </div>
                <div class="b4u-card-actions">
                    <a href="book-details.html?id=${book.id}" class="btn btn-primary btn-sm" style="flex: 1; text-align: center;">
                        ${modeActionText}
                    </a>
                    <a href="book-details.html?id=${book.id}" class="btn btn-outline btn-sm" title="View Specifications">
                        Details
                    </a>
                </div>
            </div>
        `;
        grid.appendChild(card);
    });
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
