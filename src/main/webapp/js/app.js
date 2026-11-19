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
    const healthPill = document.getElementById('system-health-pill');
    const healthText = document.getElementById('health-text');
    const healthDot = document.getElementById('health-dot');

    try {
        const response = await fetch('api/health');
        if (response.ok) {
            const data = await response.json();
            if (data.databaseStatus === 'CONNECTED') {
                healthDot.className = 'health-dot dot-green';
                healthText.textContent = 'Tomcat & MySQL: Online';
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
    const grid = document.getElementById('book-grid');
    grid.innerHTML = '<p style="grid-column: 1/-1; text-align: center; color: var(--text-muted);">Loading campus books...</p>';

    let url = 'api/books';
    const params = new URLSearchParams();
    if (subject) params.append('subject', subject);
    if (mode) params.append('mode', mode);
    if (params.toString()) url += '?' + params.toString();

    try {
        const response = await fetch(url);
        if (!response.ok) {
            throw new Error('Server returned HTTP ' + response.status);
        }

        const books = await response.json();
        renderBookCards(books);

    } catch (err) {
        grid.innerHTML = `
            <div style="grid-column: 1/-1; background-color: #fffbeb; border: 1px solid #fde68a; padding: 1.5rem; border-radius: 12px; text-align: center;">
                <p style="color: #92400e; font-weight: 600; margin-bottom: 0.5rem;">Preview Mode (API endpoint unreachable)</p>
                <p style="color: #78350f; font-size: 0.9rem;">To connect live to Servlets and MySQL, package the project with <code>mvn package</code> and deploy <code>target/Book4u.war</code> into Tomcat's <code>webapps/</code> folder.</p>
            </div>
        `;
    }
}

/**
 * Generates DOM cards for books.
 */
function renderBookCards(books) {
    const grid = document.getElementById('book-grid');
    if (!books || books.length === 0) {
        grid.innerHTML = '<p style="grid-column: 1/-1; text-align: center; color: var(--text-muted); padding: 2rem;">No books currently match this filter.</p>';
        return;
    }

    grid.innerHTML = '';
    books.forEach(book => {
        const card = document.createElement('div');
        card.className = 'book-card';

        let badgeClass = 'badge-swap';
        if (book.sharingMode === 'GIVEAWAY') badgeClass = 'badge-giveaway';
        if (book.sharingMode === 'LENDING') badgeClass = 'badge-lending';

        let lendingInfo = '';
        if (book.sharingMode === 'LENDING' && book.lendingMaxDurationDays) {
            lendingInfo = `<div><strong>Max Duration:</strong> ${book.lendingMaxDurationDays} Days</div>`;
        }

        let swapPref = '';
        if (book.sharingMode === 'SWAP' && book.swapPreferences) {
            swapPref = `<div><strong>Looking for:</strong> ${escapeHtml(book.swapPreferences)}</div>`;
        }

        card.innerHTML = `
            <div class="book-card-top">
                <span class="mode-badge ${badgeClass}">${book.sharingMode}</span>
                <span class="book-subject-pill">${escapeHtml(book.subject || 'General')}</span>
            </div>
            <h4 class="book-title">${escapeHtml(book.title)}</h4>
            <p class="book-author">by ${escapeHtml(book.author)}</p>
            <div class="book-meta">
                <div><strong>Course:</strong> ${escapeHtml(book.courseCode || 'N/A')}</div>
                ${lendingInfo}
                ${swapPref}
            </div>
            <p class="book-desc">${escapeHtml(book.description || 'No description provided.')}</p>
            <div class="book-footer">
                <span class="owner-info">Owner: <strong>${escapeHtml(book.ownerName || 'Campus Student')}</strong></span>
                <button class="btn btn-outline btn-sm" onclick="handleRequest(${book.id}, '${book.sharingMode}')">Request</button>
            </div>
        `;
        grid.appendChild(card);
    });
}

function handleRequest(bookId, mode) {
    alert(`Requesting book #${bookId} in ${mode} mode.\nThis triggers exchange negotiation between students on campus.`);
}

function setupEventListeners() {
    // Filter by subject
    const subjectFilter = document.getElementById('filter-subject');
    const modeFilter = document.getElementById('filter-mode');

    const applyFilters = () => {
        const subject = subjectFilter.value;
        const mode = modeFilter.value;
        loadAvailableBooks(subject, mode);
    };

    if (subjectFilter) subjectFilter.addEventListener('change', applyFilters);
    if (modeFilter) modeFilter.addEventListener('change', applyFilters);

    // Modal controls
    const listModal = document.getElementById('list-modal');
    const openModalBtn = document.getElementById('btn-open-list-modal');
    const closeModalBtn = document.getElementById('btn-close-modal');
    const newBookForm = document.getElementById('new-book-form');

    if (openModalBtn) {
        openModalBtn.addEventListener('click', () => {
            listModal.classList.add('active');
        });
    }

    if (closeModalBtn) {
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
                    listModal.classList.remove('active');
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

function escapeHtml(text) {
    if (!text) return '';
    const div = document.createElement('div');
    div.textContent = text;
    return div.innerHTML;
}
