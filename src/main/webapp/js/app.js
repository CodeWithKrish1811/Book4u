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

        const res = await response.json();
        const books = Array.isArray(res) ? res : (res.books || []);
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
 * Generates DOM cards for books in Crossword Bookstore style.
 */
function renderBookCards(books) {
    const grid = document.getElementById('book-grid');
    if (!books || books.length === 0) {
        grid.innerHTML = '<p style="grid-column: 1/-1; text-align: center; color: var(--cw-muted); padding: 3rem; font-size: 1.05rem;">No academic textbooks currently match this filter. Try selecting "All Subjects" or "All Modes".</p>';
        return;
    }

    grid.innerHTML = '';
    books.forEach(book => {
        const card = document.createElement('div');
        card.className = 'book-card-cw';

        const coverSrc = book.imageUrl || 'https://images.unsplash.com/photo-1544716278-ca5e3f4abd8c?auto=format&fit=crop&w=400&q=80';
        
        let modePrice = 'Free Swap';
        let discountPill = '100% Free';
        if (book.sharingMode === 'GIVEAWAY') {
            modePrice = 'Free Gift';
            discountPill = 'Giveaway';
        } else if (book.sharingMode === 'LENDING') {
            modePrice = 'Semester Loan';
            discountPill = `${book.lendingMaxDurationDays || 14}d Loan`;
        }

        const bookPayload = JSON.stringify({
            id: book.id,
            title: book.title,
            author: book.author,
            price: 0,
            mrp: 699,
            discount: discountPill,
            image: coverSrc,
            mode: book.sharingMode
        }).replace(/"/g, '&quot;');

        card.innerHTML = `
            <div class="cw-card-image-wrap">
                <img src="${escapeHtml(coverSrc)}" alt="${escapeHtml(book.title)}" class="cw-card-img" loading="lazy" onerror="this.src='https://images.unsplash.com/photo-1544716278-ca5e3f4abd8c?auto=format&fit=crop&w=400&q=80';">
                <button class="cw-heart-btn" type="button" aria-label="Add to Wishlist" onclick='Store.toggleWishlist(${bookPayload})'>
                    <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                        <path d="M20.84 4.61a5.5 5.5 0 0 0-7.78 0L12 5.67l-1.06-1.06a5.5 5.5 0 0 0-7.78 7.78l1.06 1.06L12 21.23l7.78-7.78 1.06-1.06a5.5 5.5 0 0 0 0-7.78z"></path>
                    </svg>
                </button>
            </div>
            <div class="cw-card-info">
                <h3 class="cw-card-title"><a href="book-details.html?id=${book.id}" title="${escapeHtml(book.title)}">${escapeHtml(book.title)}</a></h3>
                <p class="cw-card-author">By ${escapeHtml(book.author)} • <span style="color: var(--cw-burgundy); font-weight: 600;">${escapeHtml(book.subject || 'Academic')}</span></p>
                <div class="cw-card-price-row">
                    <span class="cw-mrp-strike">₹ 699</span>
                    <span class="cw-deal-price">${modePrice}</span>
                    <span class="cw-discount-pill">${discountPill}</span>
                </div>
                <div style="font-size: 0.78rem; color: #64748B; margin-bottom: 0.75rem;">
                    Course: <strong>${escapeHtml(book.courseCode || 'General')}</strong> • Owner: <strong>${escapeHtml(book.ownerName || 'Campus Student')}</strong>
                </div>
                <div style="display: flex; gap: 0.5rem;">
                    <button class="cw-add-bag-btn" type="button" style="flex: 1;" onclick='Store.addToCart(${bookPayload})'>
                        <span>🛍️</span> Add to Bag
                    </button>
                    <a href="book-details.html?id=${book.id}" class="cw-btn cw-btn-outline" style="padding: 0.5rem 0.75rem; font-size: 0.85rem;" title="View Details">
                        Details
                    </a>
                </div>
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
