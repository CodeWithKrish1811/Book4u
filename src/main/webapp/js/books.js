/**
 * ============================================================================
 * books.js - Client-Side Book Management & Discovery Controller
 * ============================================================================
 * University Academic Project: Student Peer-to-Peer Book Exchange System (Book4u)
 * Technology: Vanilla JavaScript (ES6+ Fetch API, DOM API)
 * Design Reference: Commercial Bookstore Interface (Screenshot 1 & 2 Layouts)
 * ============================================================================
 */

function getAppPath() {
    const path = window.location.pathname;
    const segments = path.split('/');
    if (segments.length > 1 && segments[1] !== '' && !segments[1].endsWith('.html') && segments[1] !== 'api') {
        return '/' + segments[1];
    }
    return '';
}

const APP_CONTEXT = getAppPath();

/**
 * Escapes HTML characters for safe DOM rendering.
 */
function escapeHtml(text) {
    if (!text) return '';
    const div = document.createElement('div');
    div.textContent = text;
    return div.innerHTML;
}

/**
 * Displays an alert box.
 */
function showBookAlert(elementId, message, type = 'danger') {
    const el = document.getElementById(elementId);
    if (!el) return;
    el.className = `alert alert-${type}`;
    el.innerHTML = message;
    el.style.display = 'flex';
    el.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
}

function hideBookAlert(elementId) {
    const el = document.getElementById(elementId);
    if (el) el.style.display = 'none';
}

function getModeBadgeClass(mode) {
    if (mode === 'GIVEAWAY') return 'badge-giveaway';
    if (mode === 'LENDING') return 'badge-lending';
    return 'badge-swap';
}

function getStatusBadgeClass(status) {
    if (status === 'RESERVED') return 'status-reserved';
    if (status === 'ON_LOAN') return 'status-on-loan';
    if (status === 'UNAVAILABLE') return 'status-unavailable';
    return 'status-available';
}

// ============================================================================
// 1. EXPLORE BOOKS CATALOG CONTROLLER
// ============================================================================
async function loadExploreCatalog() {
    const grid = document.getElementById('explore-book-grid');
    if (!grid) return;

    grid.innerHTML = '<p style="grid-column: 1/-1; text-align: center; color: var(--text-muted); padding: 3rem 1rem;">Loading academic book catalog...</p>';

    const query = document.getElementById('search-query') ? document.getElementById('search-query').value.trim() : '';
    const subject = document.getElementById('filter-subject') ? document.getElementById('filter-subject').value : '';
    const condition = document.getElementById('filter-condition') ? document.getElementById('filter-condition').value : '';
    const mode = document.getElementById('filter-mode') ? document.getElementById('filter-mode').value : '';

    const params = new URLSearchParams();
    params.append('excludeSelf', 'true');
    if (query) params.append('query', query);
    if (subject && subject !== 'ALL') params.append('subject', subject);
    if (condition && condition !== 'ALL') params.append('condition', condition);
    if (mode && mode !== 'ALL') params.append('mode', mode);

    try {
        const response = await fetch(`${APP_CONTEXT}/api/books?${params.toString()}`);
        if (!response.ok) throw new Error('HTTP ' + response.status);

        const data = await response.json();
        const countEl = document.getElementById('catalog-count');
        const books = Array.isArray(data) ? data : (data.books || []);
        if (countEl) countEl.innerText = `${books.length} books available`;

        renderExploreCards(books);

    } catch (err) {
        console.error('Failed to load explore books:', err);
        grid.innerHTML = `
            <div class="empty-state-box" style="grid-column: 1/-1;">
                <span class="empty-state-icon">⚠️</span>
                <h3>Unable to Connect to Server</h3>
                <p>Ensure Tomcat is running and MySQL database is connected.</p>
            </div>
        `;
    }
}

/**
 * Renders Explore Catalog Cards matching Screenshot 1 Commercial Bookstore Card
 */
function renderExploreCards(books) {
    const grid = document.getElementById('explore-book-grid');
    if (!grid) return;

    if (!books || books.length === 0) {
        grid.innerHTML = `
            <div class="empty-state-box" style="grid-column: 1/-1;">
                <span class="empty-state-icon">🔍</span>
                <h3>No Textbooks Found</h3>
                <p>No books match your current search query or filter criteria. Try adjusting keywords or clearing filters.</p>
                <button type="button" class="btn btn-outline" onclick="resetExploreFilters()">Reset All Filters</button>
            </div>
        `;
        return;
    }

    grid.innerHTML = '';
    books.forEach(book => {
        const card = document.createElement('div');
        card.className = 'book-card';

        const isWishlisted = (typeof WishlistManager !== 'undefined') ? WishlistManager.has(book.id) : false;
        const wishlistClass = isWishlisted ? 'card-wishlist-btn active' : 'card-wishlist-btn';
        const wishlistHeart = isWishlisted ? '♥' : '♡';

        // 2:3 Cover container with fallback
        const coverHtml = book.imageUrl
            ? `<div class="book-cover-container">
                 <img src="${escapeHtml(book.imageUrl)}" alt="${escapeHtml(book.title)}" class="book-cover-img" 
                      onerror="this.onerror=null; this.parentElement.innerHTML='<div class=\\'book-cover-placeholder\\'><span class=\\'subject-pill\\'>${escapeHtml(book.subject || 'Academics')}</span><p class=\\'placeholder-title\\'>${escapeHtml(book.title)}</p><span class=\\'placeholder-icon\\'>📖</span></div>';">
               </div>`
            : `<div class="book-cover-container">
                 <div class="book-cover-placeholder">
                   <span class="subject-pill">${escapeHtml(book.subject || 'Academics')}</span>
                   <p class="placeholder-title">${escapeHtml(book.title)}</p>
                   <span class="placeholder-icon">📖</span>
                 </div>
               </div>`;

        // Pricing line (strikethrough estimate + mode + savings pill)
        let priceTag = 'FREE';
        let originalEstimate = '₹ 650';
        let discountPill = '100% Free';

        if (book.sharingMode === 'SWAP') {
            priceTag = 'SWAP';
            originalEstimate = '₹ 750';
            discountPill = 'Trade Ready';
        } else if (book.sharingMode === 'LENDING') {
            priceTag = book.lendingMaxDurationDays ? `${book.lendingMaxDurationDays}d Loan` : 'Loan';
            originalEstimate = '₹ 500';
            discountPill = 'Zero Fee';
        }

        const badgeModeClass = getModeBadgeClass(book.sharingMode);

        card.innerHTML = `
            <button type="button" class="${wishlistClass}" title="Save to Wishlist" 
                    onclick="handleWishlistClick(event, ${JSON.stringify(book).replace(/"/g, '&quot;')})">
                ${wishlistHeart}
            </button>

            ${coverHtml}

            <div class="book-card-body">
                <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 0.4rem;">
                    <span class="mode-badge ${badgeModeClass}">${escapeHtml(book.sharingMode)}</span>
                    <span class="status-pill ${getStatusBadgeClass(book.status)}">${escapeHtml(book.status)}</span>
                </div>

                <a href="${APP_CONTEXT}/book-details.html?id=${book.id}" class="book-title" title="${escapeHtml(book.title)}">
                    ${escapeHtml(book.title)}
                </a>
                <p class="book-author">By ${escapeHtml(book.author || 'Author not specified')}</p>

                <div class="book-pricing-line">
                    <span class="price-strikethrough">${originalEstimate}</span>
                    <span class="price-active">${priceTag}</span>
                    <span class="price-discount-badge">${discountPill}</span>
                </div>

                <a href="${APP_CONTEXT}/book-details.html?id=${book.id}" class="card-action-btn">
                    <span>View Details</span> <span>↗</span>
                </a>
            </div>
        `;
        grid.appendChild(card);
    });
}

function resetExploreFilters() {
    const q = document.getElementById('search-query');
    if (q) q.value = '';
    const s = document.getElementById('filter-subject');
    if (s) s.value = 'ALL';
    const c = document.getElementById('filter-condition');
    if (c) c.value = 'ALL';
    const m = document.getElementById('filter-mode');
    if (m) m.value = 'ALL';
    loadExploreCatalog();
}

function initExplorePage() {
    const form = document.getElementById('search-filter-form');
    if (!form) return;

    // Check URL parameters for preset subject or search query
    const urlParams = new URLSearchParams(window.location.search);
    const presetSubject = urlParams.get('subject');
    const presetQuery = urlParams.get('query');

    if (presetSubject) {
        const s = document.getElementById('filter-subject');
        if (s) s.value = presetSubject;
    }
    if (presetQuery) {
        const q = document.getElementById('search-query');
        if (q) q.value = presetQuery;
    }

    form.addEventListener('submit', (e) => {
        e.preventDefault();
        loadExploreCatalog();
    });

    ['filter-subject', 'filter-condition', 'filter-mode'].forEach(id => {
        const el = document.getElementById(id);
        if (el) el.addEventListener('change', () => loadExploreCatalog());
    });

    const clearBtn = document.getElementById('btn-clear-filters');
    if (clearBtn) {
        clearBtn.addEventListener('click', resetExploreFilters);
    }

    loadExploreCatalog();
}

// ============================================================================
// 2. MY BOOKS MANAGEMENT CONTROLLER
// ============================================================================
let activeMyBooksStatus = 'ALL';

async function loadMyBooks(status = 'ALL') {
    const grid = document.getElementById('my-books-grid');
    if (!grid) return;

    activeMyBooksStatus = status;
    grid.innerHTML = '<p style="grid-column: 1/-1; text-align: center; color: var(--text-muted); padding: 3rem 1rem;">Loading your book listings...</p>';

    const params = new URLSearchParams();
    params.append('myBooks', 'true');
    if (status && status !== 'ALL') {
        params.append('status', status);
    }

    try {
        const response = await fetch(`${APP_CONTEXT}/api/books?${params.toString()}`);
        if (response.status === 401) {
            window.location.href = `${APP_CONTEXT}/login.html?authRequired=true`;
            return;
        }

        const data = await response.json();
        const books = Array.isArray(data) ? data : (data.books || []);
        renderMyBooksCards(books);

    } catch (err) {
        console.error('Failed to load My Books:', err);
        grid.innerHTML = `
            <div class="empty-state-box" style="grid-column: 1/-1;">
                <span class="empty-state-icon">⚠️</span>
                <h3>Connection Error</h3>
                <p>Could not fetch your book listings. Please check server status.</p>
            </div>
        `;
    }
}

function renderMyBooksCards(books) {
    const grid = document.getElementById('my-books-grid');
    if (!grid) return;

    if (!books || books.length === 0) {
        grid.innerHTML = `
            <div class="empty-state-box" style="grid-column: 1/-1;">
                <span class="empty-state-icon">📚</span>
                <h3>No Listings Found</h3>
                <p>You have not uploaded any textbooks under this category yet.</p>
                <a href="${APP_CONTEXT}/add-book.html" class="btn btn-primary">+ List a Textbook</a>
            </div>
        `;
        return;
    }

    grid.innerHTML = '';
    books.forEach(book => {
        const card = document.createElement('div');
        card.className = 'book-card';

        const coverHtml = book.imageUrl
            ? `<div class="book-cover-container">
                 <img src="${escapeHtml(book.imageUrl)}" alt="${escapeHtml(book.title)}" class="book-cover-img" 
                      onerror="this.onerror=null; this.parentElement.innerHTML='<div class=\\'book-cover-placeholder\\'><span class=\\'subject-pill\\'>${escapeHtml(book.subject || 'Academics')}</span><p class=\\'placeholder-title\\'>${escapeHtml(book.title)}</p><span class=\\'placeholder-icon\\'>📖</span></div>';">
               </div>`
            : `<div class="book-cover-container">
                 <div class="book-cover-placeholder">
                   <span class="subject-pill">${escapeHtml(book.subject || 'Academics')}</span>
                   <p class="placeholder-title">${escapeHtml(book.title)}</p>
                   <span class="placeholder-icon">📖</span>
                 </div>
               </div>`;

        card.innerHTML = `
            ${coverHtml}
            <div class="book-card-body">
                <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 0.4rem;">
                    <span class="mode-badge ${getModeBadgeClass(book.sharingMode)}">${escapeHtml(book.sharingMode)}</span>
                    <span class="status-pill ${getStatusBadgeClass(book.status)}">${escapeHtml(book.status)}</span>
                </div>

                <a href="${APP_CONTEXT}/book-details.html?id=${book.id}" class="book-title" title="${escapeHtml(book.title)}">
                    ${escapeHtml(book.title)}
                </a>
                <p class="book-author">By ${escapeHtml(book.author || 'Author not specified')}</p>

                <div style="font-size: 0.8rem; color: var(--text-muted); margin-bottom: 0.75rem;">
                    Subject: <strong>${escapeHtml(book.subject)}</strong> • Condition: <strong>${escapeHtml(book.conditionGrade)}</strong>
                </div>

                <div style="display: flex; gap: 0.45rem; margin-top: auto;">
                    <a href="${APP_CONTEXT}/book-details.html?id=${book.id}" class="btn btn-outline btn-sm" style="flex: 1;">View</a>
                    <a href="${APP_CONTEXT}/edit-book.html?id=${book.id}" class="btn btn-outline btn-sm" style="color: var(--primary); border-color: var(--primary);">✏️ Edit</a>
                    <button type="button" class="btn btn-outline btn-sm" style="color: var(--danger-color); border-color: #F8B4B4;" 
                            onclick="confirmDeleteBook(${book.id}, '${escapeHtml(book.title)}')">🗑️</button>
                </div>
            </div>
        `;
        grid.appendChild(card);
    });
}

function initMyBooksPage() {
    const tabs = document.querySelectorAll('.filter-tabs-bar .tab-btn');
    if (tabs.length === 0) return;

    tabs.forEach(tab => {
        tab.addEventListener('click', function () {
            tabs.forEach(t => t.classList.remove('active'));
            this.classList.add('active');
            const status = this.getAttribute('data-status');
            loadMyBooks(status);
        });
    });

    const params = new URLSearchParams(window.location.search);
    if (params.get('added') === 'true') {
        showBookAlert('my-books-alert', '✅ New textbook listing added successfully!', 'success');
    } else if (params.get('updated') === 'true') {
        showBookAlert('my-books-alert', '✅ Book listing details updated successfully!', 'success');
    }

    loadMyBooks('ALL');
}

async function confirmDeleteBook(bookId, title) {
    if (!confirm(`Are you sure you want to permanently delete your listing for "${title}"?\nThis action cannot be undone.`)) {
        return;
    }

    try {
        const response = await fetch(`${APP_CONTEXT}/api/books/delete?id=${bookId}`, {
            method: 'POST'
        });

        const data = await response.json();

        if (response.ok && data.success) {
            showBookAlert('my-books-alert', '🗑️ Listing deleted successfully.', 'success');
            loadMyBooks(activeMyBooksStatus);
        } else {
            showBookAlert('my-books-alert', `⚠️ ${data.error || 'Failed to delete listing.'}`, 'danger');
        }
    } catch (err) {
        showBookAlert('my-books-alert', 'Server error while deleting book.', 'danger');
    }
}

// ============================================================================
// 3. ADD BOOK CONTROLLER
// ============================================================================
function initAddBookPage() {
    const form = document.getElementById('add-book-form');
    if (!form) return;

    const modeSelect = document.getElementById('book-mode');
    const lendingGroup = document.getElementById('lending-duration-wrapper');

    if (modeSelect && lendingGroup) {
        modeSelect.addEventListener('change', () => {
            lendingGroup.style.display = (modeSelect.value === 'LENDING') ? 'block' : 'none';
        });
    }

    form.addEventListener('submit', async (e) => {
        e.preventDefault();
        hideBookAlert('add-book-alert');

        const title = document.getElementById('book-title').value.trim();
        const author = document.getElementById('book-author').value.trim();
        const subject = document.getElementById('book-subject').value.trim();
        const isbn = document.getElementById('book-isbn') ? document.getElementById('book-isbn').value.trim() : null;
        const edition = document.getElementById('book-edition') ? document.getElementById('book-edition').value.trim() : null;
        const courseCode = document.getElementById('book-course') ? document.getElementById('book-course').value.trim() : null;
        const conditionGrade = document.getElementById('book-condition').value;
        const sharingMode = document.getElementById('book-mode').value;
        const description = document.getElementById('book-desc') ? document.getElementById('book-desc').value.trim() : null;
        const imageUrl = document.getElementById('book-image') ? document.getElementById('book-image').value.trim() : null;
        const swapPreferences = document.getElementById('book-swap') ? document.getElementById('book-swap').value.trim() : null;

        let lendingMaxDurationDays = null;
        if (sharingMode === 'LENDING') {
            const daysInput = document.getElementById('book-lending-days');
            lendingMaxDurationDays = daysInput ? parseInt(daysInput.value, 10) : null;
            if (!lendingMaxDurationDays || lendingMaxDurationDays <= 0) {
                showBookAlert('add-book-alert', 'Please enter a valid loan duration in days (> 0).', 'danger');
                return;
            }
        }

        if (!title || !author || !subject) {
            showBookAlert('add-book-alert', 'Please complete all required fields (*).', 'danger');
            return;
        }

        const btn = document.getElementById('btn-submit-book');
        btn.disabled = true;
        btn.innerText = 'Publishing Listing...';

        try {
            const response = await fetch(`${APP_CONTEXT}/api/books`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({
                    title, author, subject, isbn, edition, courseCode,
                    conditionGrade, sharingMode, description, imageUrl,
                    lendingMaxDurationDays, swapPreferences
                })
            });

            const data = await response.json();

            if (response.status === 401) {
                window.location.href = `${APP_CONTEXT}/login.html?authRequired=true`;
                return;
            }

            if (response.ok && data.success) {
                window.location.href = `${APP_CONTEXT}/my-books.html?added=true`;
            } else {
                showBookAlert('add-book-alert', `⚠️ ${data.error || 'Failed to list book.'}`, 'danger');
                btn.disabled = false;
                btn.innerText = 'Publish Book Listing';
            }
        } catch (err) {
            showBookAlert('add-book-alert', 'Server error while publishing listing.', 'danger');
            btn.disabled = false;
            btn.innerText = 'Publish Book Listing';
        }
    });
}

// ============================================================================
// 4. EDIT BOOK CONTROLLER
// ============================================================================
async function initEditBookPage() {
    const form = document.getElementById('edit-book-form');
    if (!form) return;

    const params = new URLSearchParams(window.location.search);
    const bookId = params.get('id');

    if (!bookId) {
        showBookAlert('edit-book-alert', 'No book ID specified to edit.', 'danger');
        return;
    }

    try {
        const response = await fetch(`${APP_CONTEXT}/api/books?id=${bookId}`);
        if (!response.ok) throw new Error('Book not found');

        const data = await response.json();
        if (!data.success || !data.book) throw new Error('Book record missing');

        const book = data.book;
        if (!data.isOwner) {
            showBookAlert('edit-book-alert', 'You can only edit books listed by your account.', 'danger');
            return;
        }

        document.getElementById('edit-book-id').value = book.id;
        document.getElementById('edit-title').value = book.title || '';
        document.getElementById('edit-author').value = book.author || '';
        document.getElementById('edit-subject').value = book.subject || '';
        if (document.getElementById('edit-isbn')) document.getElementById('edit-isbn').value = book.isbn || '';
        if (document.getElementById('edit-edition')) document.getElementById('edit-edition').value = book.edition || '';
        if (document.getElementById('edit-course')) document.getElementById('edit-course').value = book.courseCode || '';
        if (document.getElementById('edit-condition')) document.getElementById('edit-condition').value = book.conditionGrade || 'GOOD';
        if (document.getElementById('edit-mode')) document.getElementById('edit-mode').value = book.sharingMode || 'SWAP';
        if (document.getElementById('edit-status')) document.getElementById('edit-status').value = book.status || 'AVAILABLE';
        if (document.getElementById('edit-desc')) document.getElementById('edit-desc').value = book.description || '';
        if (document.getElementById('edit-image')) document.getElementById('edit-image').value = book.imageUrl || '';
        if (document.getElementById('edit-swap')) document.getElementById('edit-swap').value = book.swapPreferences || '';

        const modeSelect = document.getElementById('edit-mode');
        const lendingGroup = document.getElementById('edit-lending-duration-wrapper');
        if (modeSelect && lendingGroup) {
            lendingGroup.style.display = (book.sharingMode === 'LENDING') ? 'block' : 'none';
            if (book.lendingMaxDurationDays && document.getElementById('edit-lending-days')) {
                document.getElementById('edit-lending-days').value = book.lendingMaxDurationDays;
            }
            modeSelect.addEventListener('change', () => {
                lendingGroup.style.display = (modeSelect.value === 'LENDING') ? 'block' : 'none';
            });
        }

    } catch (err) {
        showBookAlert('edit-book-alert', 'Failed to load existing book details.', 'danger');
    }

    form.addEventListener('submit', async (e) => {
        e.preventDefault();
        hideBookAlert('edit-book-alert');

        const id = parseInt(document.getElementById('edit-book-id').value, 10);
        const title = document.getElementById('edit-title').value.trim();
        const author = document.getElementById('edit-author').value.trim();
        const subject = document.getElementById('edit-subject').value.trim();
        const isbn = document.getElementById('edit-isbn') ? document.getElementById('edit-isbn').value.trim() : null;
        const edition = document.getElementById('edit-edition') ? document.getElementById('edit-edition').value.trim() : null;
        const courseCode = document.getElementById('edit-course') ? document.getElementById('edit-course').value.trim() : null;
        const conditionGrade = document.getElementById('edit-condition').value;
        const sharingMode = document.getElementById('edit-mode').value;
        const status = document.getElementById('edit-status').value;
        const description = document.getElementById('edit-desc') ? document.getElementById('edit-desc').value.trim() : null;
        const imageUrl = document.getElementById('edit-image') ? document.getElementById('edit-image').value.trim() : null;
        const swapPreferences = document.getElementById('edit-swap') ? document.getElementById('edit-swap').value.trim() : null;

        let lendingMaxDurationDays = null;
        if (sharingMode === 'LENDING') {
            const daysInput = document.getElementById('edit-lending-days');
            lendingMaxDurationDays = daysInput ? parseInt(daysInput.value, 10) : null;
        }

        const btn = document.getElementById('btn-save-edit');
        btn.disabled = true;
        btn.innerText = 'Saving Changes...';

        try {
            const response = await fetch(`${APP_CONTEXT}/api/books/update`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({
                    id, title, author, subject, isbn, edition, courseCode,
                    conditionGrade, sharingMode, status, description, imageUrl,
                    lendingMaxDurationDays, swapPreferences
                })
            });

            const data = await response.json();

            if (response.ok && data.success) {
                window.location.href = `${APP_CONTEXT}/my-books.html?updated=true`;
            } else {
                showBookAlert('edit-book-alert', `⚠️ ${data.error || 'Failed to update book.'}`, 'danger');
                btn.disabled = false;
                btn.innerText = 'Save Changes';
            }
        } catch (err) {
            showBookAlert('edit-book-alert', 'Server error while updating book.', 'danger');
            btn.disabled = false;
            btn.innerText = 'Save Changes';
        }
    });
}

// ============================================================================
// 5. BOOK DETAILS CONTROLLER (Screenshot 2: Commercial Bookstore Layout)
// ============================================================================
async function initBookDetailsPage() {
    const container = document.getElementById('book-details-view');
    if (!container) return;

    const params = new URLSearchParams(window.location.search);
    const bookId = params.get('id');

    if (!bookId) {
        container.innerHTML = `
            <div class="empty-state-box">
                <span class="empty-state-icon">❌</span>
                <h3>Invalid Book Request</h3>
                <p>No textbook ID was specified.</p>
                <a href="${APP_CONTEXT}/explore.html" class="btn btn-primary">Browse All Books</a>
            </div>
        `;
        return;
    }

    try {
        const response = await fetch(`${APP_CONTEXT}/api/books?id=${bookId}`);
        if (!response.ok) throw new Error('Book not found');

        const data = await response.json();
        if (!data.success || !data.book) {
            throw new Error('Book record missing');
        }

        const book = data.book;
        const isOwner = data.isOwner;
        const canRequest = data.canRequest;

        // Wishlist state
        const isWishlisted = (typeof WishlistManager !== 'undefined') ? WishlistManager.has(book.id) : false;
        const wishlistHeart = isWishlisted ? '♥' : '♡';
        const wishlistClass = isWishlisted ? 'active' : '';

        // Left Container: Large book cover in white container (Screenshot 2)
        const coverHtml = book.imageUrl
            ? `<div class="details-cover-frame">
                 <img src="${escapeHtml(book.imageUrl)}" alt="${escapeHtml(book.title)}" class="details-cover-img" 
                      onerror="this.onerror=null; this.parentElement.innerHTML='<div class=\\'book-cover-placeholder\\'><span class=\\'subject-pill\\'>${escapeHtml(book.subject || 'Academics')}</span><p class=\\'placeholder-title\\'>${escapeHtml(book.title)}</p><span class=\\'placeholder-icon\\'>📖</span></div>';">
               </div>`
            : `<div class="details-cover-frame">
                 <div class="book-cover-placeholder">
                   <span class="subject-pill">${escapeHtml(book.subject || 'Academics')}</span>
                   <p class="placeholder-title" style="font-size: 1.1rem;">${escapeHtml(book.title)}</p>
                   <span class="placeholder-icon" style="font-size: 2.5rem;">📖</span>
                 </div>
               </div>`;

        // Pricing line matching Screenshot 2 (MRP strikethrough + active + savings badge)
        let priceTag = 'FREE';
        let originalEstimate = '₹ 650';
        let discountBadge = '100% Free • Student Exchange';

        if (book.sharingMode === 'SWAP') {
            priceTag = 'SWAP';
            originalEstimate = '₹ 750';
            discountBadge = 'Zero Cost • Peer Trade';
        } else if (book.sharingMode === 'LENDING') {
            priceTag = book.lendingMaxDurationDays ? `${book.lendingMaxDurationDays}d Loan` : 'Loan';
            originalEstimate = '₹ 500';
            discountBadge = 'Zero Platform Fee';
        }

        // Action Buttons (Screenshot 2: Add to Bag / Buy Now style dual buttons)
        let actionButtonsHtml = '';
        if (isOwner) {
            actionButtonsHtml = `
                <div class="details-buttons-row">
                    <a href="${APP_CONTEXT}/edit-book.html?id=${book.id}" class="details-btn-primary">
                        <span>✏️ Edit Listing Details</span>
                    </a>
                    <button type="button" class="details-btn-secondary" style="color: var(--danger-color); border-color: #F8B4B4;" 
                            onclick="confirmDeleteBook(${book.id}, '${escapeHtml(book.title)}')">
                        <span>🗑️ Delete Listing</span>
                    </button>
                </div>
            `;
        } else if (canRequest) {
            actionButtonsHtml = `
                <div class="details-buttons-row">
                    <button type="button" id="btn-request-exchange" class="details-btn-primary" 
                            onclick="handleRequestExchangeNotice(${book.id}, '${escapeHtml(book.title)}')">
                        <span>Request Exchange (${escapeHtml(book.sharingMode)})</span>
                    </button>
                    <button type="button" class="details-btn-secondary" 
                            onclick="toggleDetailsWishlist(event, ${JSON.stringify(book).replace(/"/g, '&quot;')})">
                        <span id="details-wishlist-icon">${wishlistHeart}</span>
                        <span id="details-wishlist-text">${isWishlisted ? 'In Wishlist' : 'Add to Wishlist'}</span>
                    </button>
                </div>
            `;
        } else if (!isOwner && book.status !== 'AVAILABLE') {
            actionButtonsHtml = `
                <div style="padding: 1rem; background-color: var(--status-unavailable-bg); border-radius: var(--radius-sm); text-align: center; color: var(--text-muted); font-size: 0.92rem; margin-top: 1.25rem;">
                    🔒 This textbook is currently <strong>${escapeHtml(book.status)}</strong> and unavailable for new exchange requests.
                </div>
            `;
        }

        // Lending conditions notice
        let lendingTermHtml = '';
        if (book.sharingMode === 'LENDING' && book.lendingMaxDurationDays) {
            lendingTermHtml = `
                <div class="details-terms-box" style="background-color: #F0F6F4; border-color: #C3D9D0;">
                    <strong style="color: var(--primary);">⏱️ Lending Agreement Duration:</strong>
                    <p style="color: var(--secondary); font-size: 0.88rem; margin-top: 0.25rem;">
                        Maximum loan duration is <strong>${book.lendingMaxDurationDays} Days</strong>. Handover and return occur directly between students on campus.
                    </p>
                </div>
            `;
        }

        // Swap preferences notice
        let swapPrefHtml = '';
        if (book.sharingMode === 'SWAP' && book.swapPreferences) {
            swapPrefHtml = `
                <div class="details-terms-box" style="background-color: var(--accent-gold-light); border-color: #E6D2B3;">
                    <strong style="color: #7A5722;">🔄 Student Owner's Swap Preferences:</strong>
                    <p style="color: #593D14; font-size: 0.88rem; margin-top: 0.25rem;">
                        ${escapeHtml(book.swapPreferences)}
                    </p>
                </div>
            `;
        }

        // Render Screenshot 2 layout
        container.innerHTML = `
            <!-- Breadcrumbs -->
            <nav class="book-details-breadcrumb" aria-label="Breadcrumb">
                <a href="${APP_CONTEXT}/index.html">Home</a>
                <span>›</span>
                <a href="${APP_CONTEXT}/explore.html">Explore Books</a>
                <span>›</span>
                <a href="${APP_CONTEXT}/explore.html?subject=${encodeURIComponent(book.subject || '')}">${escapeHtml(book.subject || 'Academics')}</a>
                <span>›</span>
                <span style="color: var(--text-main); font-weight: 600;">${escapeHtml(book.title)}</span>
            </nav>

            <!-- Two-Column Hero (Screenshot 2 Reference) -->
            <div class="book-details-hero">
                <!-- Left: Big Book Cover Frame -->
                <div class="details-left-box">
                    ${coverHtml}
                </div>

                <!-- Right: Book Metadata & Purchase/Exchange Box -->
                <div class="details-right-box">
                    <div class="details-title-row">
                        <h1 class="details-title">${escapeHtml(book.title)}</h1>
                        <div class="details-action-icons">
                            <button type="button" class="details-icon-btn ${wishlistClass}" id="details-heart-btn" title="Save to Wishlist" 
                                    onclick="toggleDetailsWishlist(event, ${JSON.stringify(book).replace(/"/g, '&quot;')})">
                                ${wishlistHeart}
                            </button>
                            <button type="button" class="details-icon-btn" title="Share Book Link" onclick="handleShareBook('${escapeHtml(book.title)}')">
                                ↗
                            </button>
                        </div>
                    </div>

                    <a href="${APP_CONTEXT}/explore.html?query=${encodeURIComponent(book.author || '')}" class="details-author-link">
                        By ${escapeHtml(book.author || 'Author not specified')}
                    </a>

                    <!-- Rating / Verification Line (Crossword reference: ★★★★★ 10 reviews) -->
                    <div class="details-rating-line">
                        <span class="rating-stars">★★★★★</span>
                        <span class="rating-count">5.0 (Student Verified)</span>
                        <span style="color: var(--border-color);">•</span>
                        <span style="color: var(--text-muted); font-size: 0.82rem;">${escapeHtml(book.ownerDepartment || 'Academic Department')}</span>
                    </div>

                    <!-- Category & Pill Tags (Crossword reference: Magical Adventure, Wizarding World, etc.) -->
                    <div class="details-tags-row">
                        <span class="details-tag-pill">${escapeHtml(book.subject || 'Curriculum')}</span>
                        <span class="details-tag-pill">${escapeHtml(book.courseCode || 'General')}</span>
                        <span class="details-tag-pill">${escapeHtml(book.edition || 'Standard Edition')}</span>
                        <span class="details-tag-pill">${escapeHtml(book.conditionGrade || 'Good')}</span>
                        <span class="mode-badge ${getModeBadgeClass(book.sharingMode)}">${escapeHtml(book.sharingMode)}</span>
                    </div>

                    <!-- Price Box (Crossword reference: MRP: ₹ 599 strikethrough, ₹ 450, ₹ 149 Off) -->
                    <div class="details-price-box">
                        <div class="details-price-main">
                            <span class="details-mrp-label">Est. MRP:</span>
                            <span class="details-mrp-val">${originalEstimate}</span>
                            <span class="details-active-price">${priceTag}</span>
                            <span class="details-savings-badge">${discountBadge}</span>
                        </div>
                        <div class="details-tax-note">Zero commercial markups • 100% peer-to-peer campus exchange</div>
                    </div>

                    <!-- Action Buttons Row (Crossword reference: Add to Bag + Buy Now buttons) -->
                    ${actionButtonsHtml}

                    ${lendingTermHtml}
                    ${swapPrefHtml}

                    <!-- Student Owner Profile Card -->
                    <div class="details-owner-card">
                        <div class="owner-avatar-circle">
                            ${book.ownerName ? book.ownerName.charAt(0).toUpperCase() : 'S'}
                        </div>
                        <div>
                            <div style="font-size: 0.72rem; color: var(--primary); font-weight: 700; text-transform: uppercase; letter-spacing: 0.04em;">Listed by Student Peer</div>
                            <div style="font-size: 0.96rem; font-weight: 700; color: var(--text-main);">${escapeHtml(book.ownerName || 'Verified Student')}</div>
                            <div style="font-size: 0.82rem; color: var(--text-muted);">${escapeHtml(book.ownerDepartment || 'Academic Department')} • ${escapeHtml(book.ownerCampus || 'Main Campus')}</div>
                        </div>
                    </div>
                </div>
            </div>

            <!-- "Books in This Series / Subject" (Screenshot 2 Related Books Carousel) -->
            <section class="related-books-section" style="margin-bottom: 2.5rem;">
                <div class="section-header">
                    <div class="section-title-wrap">
                        <h2>Books in This Subject</h2>
                        <p>More textbooks listed by campus students in ${escapeHtml(book.subject || 'Academics')}</p>
                    </div>
                    <div class="section-controls">
                        <a href="${APP_CONTEXT}/explore.html?subject=${encodeURIComponent(book.subject || '')}" class="btn-show-all">
                            Show All ↗
                        </a>
                    </div>
                </div>
                <div id="related-books-row" class="book-cards-row">
                    <p style="grid-column: 1/-1; color: var(--text-muted); padding: 1rem 0;">Finding related course books...</p>
                </div>
            </section>

            <!-- Book Specifications & Details Card -->
            <div class="details-specs-card">
                <h3 style="font-family: var(--font-editorial); font-size: 1.3rem; margin-bottom: 1rem; color: var(--text-main);">
                    Textbook Specifications & Course Notes
                </h3>
                <table class="specs-table">
                    <tbody>
                        <tr>
                            <td>Subject / Field</td>
                            <td><strong>${escapeHtml(book.subject)}</strong></td>
                        </tr>
                        <tr>
                            <td>Condition Grade</td>
                            <td><span class="badge-condition">${escapeHtml(book.conditionGrade)}</span></td>
                        </tr>
                        <tr>
                            <td>Edition</td>
                            <td>${escapeHtml(book.edition || 'Not specified')}</td>
                        </tr>
                        <tr>
                            <td>Course Code</td>
                            <td>${escapeHtml(book.courseCode || 'All Departments')}</td>
                        </tr>
                        <tr>
                            <td>ISBN Reference</td>
                            <td>${escapeHtml(book.isbn || 'N/A')}</td>
                        </tr>
                        <tr>
                            <td>Sharing Protocol</td>
                            <td><span class="mode-badge ${getModeBadgeClass(book.sharingMode)}">${escapeHtml(book.sharingMode)}</span></td>
                        </tr>
                        <tr>
                            <td>Current Availability</td>
                            <td><span class="status-pill ${getStatusBadgeClass(book.status)}">${escapeHtml(book.status)}</span></td>
                        </tr>
                        <tr>
                            <td>Date Cataloged</td>
                            <td>${book.createdAt ? new Date(book.createdAt).toLocaleDateString('en-US', { year: 'numeric', month: 'long', day: 'numeric' }) : '--'}</td>
                        </tr>
                    </tbody>
                </table>

                <div style="margin-top: 1.75rem;">
                    <h4 style="font-size: 1.05rem; color: var(--text-main); margin-bottom: 0.5rem; font-weight: 700;">
                        Description &amp; Syllabus Highlights
                    </h4>
                    <p style="color: var(--text-main); line-height: 1.75; font-size: 0.95rem;">
                        ${escapeHtml(book.description || 'No additional condition notes provided by owner.')}
                    </p>
                </div>
            </div>
        `;

        // Load "Books in This Subject" related shelf (Screenshot 2 reference)
        loadRelatedBooksShelf(book.subject, book.id);

        if (params.get('action') === 'request' && canRequest) {
            handleRequestExchangeNotice(book.id, book.title);
        }

    } catch (err) {
        console.error('Error rendering book details:', err);
        container.innerHTML = `
            <div class="empty-state-box">
                <span class="empty-state-icon">⚠️</span>
                <h3>Book Not Found</h3>
                <p>The requested book listing does not exist or may have been removed.</p>
                <a href="${APP_CONTEXT}/explore.html" class="btn btn-primary">Return to Catalog</a>
            </div>
        `;
    }
}

/**
 * Loads related books for Screenshot 2 "Books in This Series / Subject" shelf
 */
async function loadRelatedBooksShelf(subject, currentBookId) {
    const row = document.getElementById('related-books-row');
    if (!row) return;

    try {
        const response = await fetch(`${APP_CONTEXT}/api/books?subject=${encodeURIComponent(subject || '')}`);
        if (!response.ok) throw new Error('HTTP ' + response.status);

        const data = await response.json();
        const allBooks = Array.isArray(data) ? data : (data.books || []);
        // Filter out current book
        const related = allBooks.filter(b => String(b.id) !== String(currentBookId)).slice(0, 5);

        if (related.length === 0) {
            row.innerHTML = `<p style="grid-column: 1/-1; color: var(--text-muted); font-size: 0.88rem; padding: 1rem 0;">No other books currently listed under ${escapeHtml(subject)}.</p>`;
            return;
        }

        if (typeof renderBookShelf === 'function') {
            renderBookShelf(row, related);
        } else {
            renderExploreCards(related);
        }

    } catch (e) {
        row.innerHTML = '';
    }
}

function toggleDetailsWishlist(event, book) {
    event.preventDefault();
    if (typeof WishlistManager === 'undefined') return;

    const added = WishlistManager.toggle(book);
    const heartBtn = document.getElementById('details-heart-btn');
    const icon = document.getElementById('details-wishlist-icon');
    const text = document.getElementById('details-wishlist-text');

    if (added) {
        if (heartBtn) {
            heartBtn.classList.add('active');
            heartBtn.innerText = '♥';
        }
        if (icon) icon.innerText = '♥';
        if (text) text.innerText = 'In Wishlist';
    } else {
        if (heartBtn) {
            heartBtn.classList.remove('active');
            heartBtn.innerText = '♡';
        }
        if (icon) icon.innerText = '♡';
        if (text) text.innerText = 'Add to Wishlist';
    }
}

function handleShareBook(title) {
    if (navigator.share) {
        navigator.share({
            title: title + ' — Book4u',
            url: window.location.href
        }).catch(() => {});
    } else if (navigator.clipboard) {
        navigator.clipboard.writeText(window.location.href).then(() => {
            alert('🔗 Book link copied to clipboard!');
        });
    } else {
        alert('Book URL: ' + window.location.href);
    }
}

function handleRequestExchangeNotice(bookId, title) {
    alert(`🎓 Request for "${title}":\n\nYou are requesting to connect with the student who listed this book.\nExchange request workflows, in-person handover coordination, and loan duration agreements will be finalized in Phase 4.`);
}

// Auto-initialize based on active DOM elements
document.addEventListener('DOMContentLoaded', () => {
    initExplorePage();
    initMyBooksPage();
    initAddBookPage();
    initEditBookPage();
    initBookDetailsPage();
});
