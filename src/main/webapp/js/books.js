/**
 * ============================================================================
 * books.js - Client-Side Book Management & Discovery Controller
 * ============================================================================
 * University Academic Project: Student Peer-to-Peer Book Exchange System (Book4u)
 * Technology: Vanilla JavaScript (ES6+ Fetch API, DOM API)
 * 
 * Supports three campus sharing modes:
 * - SWAP (Exchange a book for another)
 * - GIVEAWAY (Free gift to another student)
 * - LENDING (Temporary semester loan)
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

    grid.innerHTML = '<p style="grid-column: 1/-1; text-align: center; color: var(--color-muted); padding: 2.5rem;">Loading campus academic catalog...</p>';

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
        if (countEl) countEl.innerText = `${data.count || 0} books available on campus`;

        renderExploreCards(data.books);

    } catch (err) {
        console.error('Failed to load explore books:', err);
        // Fallback sample catalog for instant campus preview
        const fallbackBooks = [
            {
                id: 101,
                title: "Introduction to Algorithms (CLRS 4th Edition)",
                author: "Thomas H. Cormen, Charles E. Leiserson",
                subject: "DSA",
                courseCode: "CS210",
                conditionGrade: "LIKE_NEW",
                sharingMode: "SWAP",
                swapPreferences: "Looking for Database Systems or Operating Systems",
                ownerName: "Aarav Sharma",
                ownerCampus: "North Campus Library",
                imageUrl: "https://images.unsplash.com/photo-1532012164546-f432f2e3777f?w=500&q=80"
            },
            {
                id: 102,
                title: "Database System Concepts (7th Edition)",
                author: "Abraham Silberschatz, Henry F. Korth",
                subject: "DBMS",
                courseCode: "CS304",
                conditionGrade: "VERY_GOOD",
                sharingMode: "LENDING",
                lendingMaxDurationDays: 30,
                ownerName: "Priya Patel",
                ownerCampus: "Central Campus",
                imageUrl: "https://images.unsplash.com/photo-1544716278-ca5e3f4abd8c?w=500&q=80"
            },
            {
                id: 103,
                title: "Computer Networks: A Systems Approach",
                author: "Larry L. Peterson, Bruce S. Davie",
                subject: "Networks",
                courseCode: "CS350",
                conditionGrade: "GOOD",
                sharingMode: "GIVEAWAY",
                ownerName: "Rohan Verma",
                ownerCampus: "Engineering Block Quad",
                imageUrl: "https://images.unsplash.com/photo-1456513080510-7bf3a84b82f8?w=500&q=80"
            },
            {
                id: 104,
                title: "Operating System Concepts (10th Dinosaur Edition)",
                author: "Abraham Silberschatz, Peter B. Galvin",
                subject: "Operating Systems",
                courseCode: "CS204",
                conditionGrade: "LIKE_NEW",
                sharingMode: "SWAP",
                swapPreferences: "Looking for Computer Networks or Compiler Design",
                ownerName: "Ananya Iyer",
                ownerCampus: "Central Library Block B",
                imageUrl: "https://images.unsplash.com/photo-1517694712202-14dd9538aa97?w=500&q=80"
            },
            {
                id: 105,
                title: "Discrete Mathematics and Its Applications",
                author: "Kenneth H. Rosen",
                subject: "Mathematics",
                courseCode: "MTH102",
                conditionGrade: "GOOD",
                sharingMode: "LENDING",
                lendingMaxDurationDays: 14,
                ownerName: "Devendra Rao",
                ownerCampus: "Main Campus Library",
                imageUrl: "https://images.unsplash.com/photo-1509228468518-180dd4864904?w=500&q=80"
            },
            {
                id: 106,
                title: "Core Java Volume I: Fundamentals",
                author: "Cay S. Horstmann",
                subject: "Java",
                courseCode: "CS201",
                conditionGrade: "VERY_GOOD",
                sharingMode: "GIVEAWAY",
                ownerName: "Sneha Nair",
                ownerCampus: "Science Block Lobby",
                imageUrl: "https://images.unsplash.com/photo-1512820790803-83ca734da794?w=500&q=80"
            }
        ];
        const countEl = document.getElementById('catalog-count');
        if (countEl) countEl.innerText = `${fallbackBooks.length} books available (Offline Preview)`;
        renderExploreCards(fallbackBooks);
    }
}

function renderExploreCards(books) {
    const grid = document.getElementById('explore-book-grid');
    if (!grid) return;

    if (!books || books.length === 0) {
        grid.innerHTML = `
            <div class="empty-state-box" style="grid-column: 1/-1; text-align: center; padding: 4rem 1rem; background: #FFF; border: 1px solid var(--color-border); border-radius: 12px;">
                <span class="empty-state-icon" style="font-size: 3rem;">🔍</span>
                <h3 style="font-size: 1.4rem; margin: 0.5rem 0; color: var(--color-charcoal);">No Textbooks Found</h3>
                <p style="color: var(--color-muted); margin-bottom: 1.5rem;">No books match your current search query or filter criteria. Try adjusting keywords or clearing filters.</p>
                <button type="button" class="btn btn-outline" onclick="resetExploreFilters()">Reset All Filters</button>
            </div>
        `;
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
        let modeDetail = book.swapPreferences ? `Wants: ${escapeHtml(book.swapPreferences)}` : 'Direct Peer Trade';

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
                <a href="${APP_CONTEXT}/book-details.html?id=${book.id}">
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
                    <a href="${APP_CONTEXT}/book-details.html?id=${book.id}" title="${escapeHtml(book.title)}">${escapeHtml(book.title)}</a>
                </h3>
                <p class="b4u-card-author">By ${escapeHtml(book.author || 'Author')}</p>
                <div class="b4u-card-owner">
                    <span class="owner-icon">👤</span>
                    <span>${escapeHtml(book.ownerName || 'Verified Peer')}</span>
                </div>
                <div class="b4u-card-mode-detail">
                    <span>${modeDetail}</span>
                </div>
                <div class="b4u-card-actions">
                    <a href="${APP_CONTEXT}/book-details.html?id=${book.id}" class="btn btn-primary btn-sm" style="flex: 1; text-align: center;">
                        ${modeActionText}
                    </a>
                    <a href="${APP_CONTEXT}/book-details.html?id=${book.id}" class="btn btn-outline btn-sm" title="View Specifications">
                        Details
                    </a>
                </div>
            </div>
        `;
        grid.appendChild(card);
    });
}

function filterExploreBySubject(subj) {
    const subjectSelect = document.getElementById('filter-subject');
    if (subjectSelect) {
        subjectSelect.value = subj;
        loadExploreCatalog();
    }
}

function filterExploreByMode(mode) {
    const modeSelect = document.getElementById('filter-mode');
    if (modeSelect) {
        modeSelect.value = mode;
        loadExploreCatalog();
    }
}

function resetExploreFilters() {
    const searchInput = document.getElementById('search-query');
    const headerInput = document.getElementById('header-search-input');
    const subjectSelect = document.getElementById('filter-subject');
    const conditionSelect = document.getElementById('filter-condition');
    const modeSelect = document.getElementById('filter-mode');

    if (searchInput) searchInput.value = '';
    if (headerInput) headerInput.value = '';
    if (subjectSelect) subjectSelect.value = 'ALL';
    if (conditionSelect) conditionSelect.value = 'ALL';
    if (modeSelect) modeSelect.value = 'ALL';

    loadExploreCatalog();
}

function sortExploreCatalog(criteria) {
    const grid = document.getElementById('explore-book-grid');
    if (!grid) return;

    const cards = Array.from(grid.querySelectorAll('.b4u-book-card'));
    if (cards.length === 0) return;

    cards.sort((a, b) => {
        const titleA = a.querySelector('.b4u-card-title a')?.textContent.trim().toLowerCase() || '';
        const titleB = b.querySelector('.b4u-card-title a')?.textContent.trim().toLowerCase() || '';

        if (criteria === 'title-asc') return titleA.localeCompare(titleB);
        if (criteria === 'title-desc') return titleB.localeCompare(titleA);
        return 0; // Default order
    });

    cards.forEach(card => grid.appendChild(card));
}

function initExplorePage() {
    const searchForm = document.getElementById('search-filter-form');
    if (searchForm) {
        searchForm.addEventListener('submit', function (e) {
            e.preventDefault();
            loadExploreCatalog();
        });
    }

    const clearBtn = document.getElementById('btn-clear-filters');
    if (clearBtn) {
        clearBtn.addEventListener('click', resetExploreFilters);
    }

    // Parse URL query parameters if arriving with pre-set filters
    const params = new URLSearchParams(window.location.search);
    const initialQuery = params.get('query');
    const initialSubject = params.get('subject');
    const initialMode = params.get('mode');

    if (initialQuery && document.getElementById('search-query')) {
        document.getElementById('search-query').value = initialQuery;
    }
    if (initialSubject && document.getElementById('filter-subject')) {
        document.getElementById('filter-subject').value = initialSubject;
    }
    if (initialMode && document.getElementById('filter-mode')) {
        document.getElementById('filter-mode').value = initialMode;
    }

    if (document.getElementById('explore-book-grid')) {
        loadExploreCatalog();
    }
}

// ============================================================================
// 2. MY BOOKS / STUDENT LISTINGS CONTROLLER
// ============================================================================
let activeMyBooksStatus = 'ALL';

async function loadMyBooks(statusFilter = 'ALL') {
    const grid = document.getElementById('my-books-grid');
    if (!grid) return;

    activeMyBooksStatus = statusFilter;
    grid.innerHTML = '<p style="grid-column: 1/-1; text-align: center; color: var(--color-muted); padding: 2.5rem;">Loading your active campus listings...</p>';

    const params = new URLSearchParams();
    if (statusFilter && statusFilter !== 'ALL') {
        params.append('status', statusFilter);
    }

    try {
        const response = await fetch(`${APP_CONTEXT}/api/books/my?${params.toString()}`);
        if (response.status === 401) {
            window.location.href = `${APP_CONTEXT}/login.html?authRequired=true`;
            return;
        }

        if (!response.ok) throw new Error('HTTP ' + response.status);

        const data = await response.json();
        const countEl = document.getElementById('my-books-count');
        if (countEl) countEl.innerText = `${data.count || 0} books listed`;

        renderMyBooks(data.books);

    } catch (err) {
        console.error('Error fetching my books:', err);
        grid.innerHTML = `
            <div class="empty-state-box" style="grid-column: 1/-1; text-align: center; padding: 3rem 1rem;">
                <span class="empty-state-icon" style="font-size: 2.5rem;">⚠️</span>
                <h3>Unable to Load Your Listings</h3>
                <p style="color: var(--color-muted);">Ensure you are signed in and connected to the campus server.</p>
                <a href="${APP_CONTEXT}/login.html" class="btn btn-primary" style="margin-top: 1rem;">Sign In</a>
            </div>
        `;
    }
}

function renderMyBooks(books) {
    const grid = document.getElementById('my-books-grid');
    if (!grid) return;

    if (!books || books.length === 0) {
        grid.innerHTML = `
            <div class="empty-state-box" style="grid-column: 1/-1; text-align: center; padding: 4rem 1rem; background: #FFFFFF; border: 1px solid var(--color-border); border-radius: 12px;">
                <span class="empty-state-icon" style="font-size: 3rem;">📚</span>
                <h3 style="font-size: 1.4rem; margin: 0.5rem 0; color: var(--color-charcoal);">No Books Listed</h3>
                <p style="color: var(--color-muted); margin-bottom: 1.5rem;">You haven't listed any textbooks yet. Share a book with peers today!</p>
                <a href="${APP_CONTEXT}/add-book.html" class="btn btn-primary">+ List a Book Now</a>
            </div>
        `;
        return;
    }

    grid.innerHTML = '';
    books.forEach(book => {
        const card = document.createElement('div');
        card.className = 'b4u-book-card my-book-item';

        const coverSrc = book.imageUrl || 'https://images.unsplash.com/photo-1544716278-ca5e3f4abd8c?auto=format&fit=crop&w=400&q=80';
        const modeClass = book.sharingMode === 'GIVEAWAY' ? 'mode-giveaway' : (book.sharingMode === 'LENDING' ? 'mode-lending' : 'mode-swap');
        const conditionLabel = (book.conditionGrade || 'GOOD').replace('_', ' ');

        card.innerHTML = `
            <div class="b4u-card-media">
                <a href="${APP_CONTEXT}/book-details.html?id=${book.id}">
                    <img src="${escapeHtml(coverSrc)}" alt="${escapeHtml(book.title)}" class="b4u-card-img" loading="lazy">
                </a>
                <span class="b4u-mode-badge ${modeClass}">${escapeHtml(book.sharingMode || 'SWAP')}</span>
            </div>
            <div class="b4u-card-body">
                <div class="b4u-card-meta">
                    <span class="b4u-subject-tag">${escapeHtml(book.subject || 'Academic')}</span>
                    <span class="b4u-condition-tag">${escapeHtml(conditionLabel)}</span>
                </div>
                <h3 class="b4u-card-title">
                    <a href="${APP_CONTEXT}/book-details.html?id=${book.id}">${escapeHtml(book.title)}</a>
                </h3>
                <p class="b4u-card-author">By ${escapeHtml(book.author || 'Author')}</p>
                <div class="b4u-card-actions" style="margin-top: 1rem;">
                    <a href="${APP_CONTEXT}/edit-book.html?id=${book.id}" class="btn btn-outline btn-sm" style="flex: 1; text-align: center;">✏️ Edit</a>
                    <button type="button" class="btn btn-outline btn-sm" style="color: #DC2626; border-color: #FECACA;" onclick="confirmDeleteBook(${book.id}, '${escapeHtml(book.title)}')">🗑️ Delete</button>
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
                    title,
                    author,
                    subject,
                    isbn,
                    edition,
                    courseCode,
                    conditionGrade,
                    sharingMode,
                    description,
                    imageUrl,
                    swapPreferences,
                    lendingMaxDurationDays
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
                btn.innerText = 'Publish Listing';
            }
        } catch (err) {
            showBookAlert('add-book-alert', 'Server error while listing book.', 'danger');
            btn.disabled = false;
            btn.innerText = 'Publish Listing';
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
        showBookAlert('edit-book-alert', 'No book ID provided for editing.', 'danger');
        return;
    }

    try {
        const response = await fetch(`${APP_CONTEXT}/api/books?id=${bookId}`);
        if (!response.ok) throw new Error('HTTP ' + response.status);

        const data = await response.json();
        if (data.success && data.book) {
            populateEditForm(data.book);
        } else {
            showBookAlert('edit-book-alert', data.error || 'Unable to load book details.', 'danger');
        }
    } catch (err) {
        showBookAlert('edit-book-alert', 'Error fetching book data for editing.', 'danger');
    }

    form.addEventListener('submit', async (e) => {
        e.preventDefault();
        hideBookAlert('edit-book-alert');

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
        }

        const btn = document.getElementById('btn-submit-edit');
        btn.disabled = true;
        btn.innerText = 'Saving Changes...';

        try {
            const res = await fetch(`${APP_CONTEXT}/api/books/update?id=${bookId}`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({
                    title,
                    author,
                    subject,
                    isbn,
                    edition,
                    courseCode,
                    conditionGrade,
                    sharingMode,
                    description,
                    imageUrl,
                    swapPreferences,
                    lendingMaxDurationDays
                })
            });

            const result = await res.json();
            if (res.ok && result.success) {
                window.location.href = `${APP_CONTEXT}/my-books.html?updated=true`;
            } else {
                showBookAlert('edit-book-alert', `⚠️ ${result.error || 'Failed to update book.'}`, 'danger');
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

function populateEditForm(book) {
    if (document.getElementById('book-title')) document.getElementById('book-title').value = book.title || '';
    if (document.getElementById('book-author')) document.getElementById('book-author').value = book.author || '';
    if (document.getElementById('book-subject')) document.getElementById('book-subject').value = book.subject || '';
    if (document.getElementById('book-isbn')) document.getElementById('book-isbn').value = book.isbn || '';
    if (document.getElementById('book-edition')) document.getElementById('book-edition').value = book.edition || '';
    if (document.getElementById('book-course')) document.getElementById('book-course').value = book.courseCode || '';
    if (document.getElementById('book-condition')) document.getElementById('book-condition').value = book.conditionGrade || 'GOOD';
    if (document.getElementById('book-mode')) {
        document.getElementById('book-mode').value = book.sharingMode || 'SWAP';
        const lendingGroup = document.getElementById('lending-duration-wrapper');
        if (lendingGroup) {
            lendingGroup.style.display = (book.sharingMode === 'LENDING') ? 'block' : 'none';
        }
    }
    if (document.getElementById('book-lending-days')) document.getElementById('book-lending-days').value = book.lendingMaxDurationDays || 14;
    if (document.getElementById('book-desc')) document.getElementById('book-desc').value = book.description || '';
    if (document.getElementById('book-image')) document.getElementById('book-image').value = book.imageUrl || '';
    if (document.getElementById('book-swap')) document.getElementById('book-swap').value = book.swapPreferences || '';
}

// ============================================================================
// 5. BOOK DETAILS CONTROLLER (ACADEMIC PEER EXCHANGE SPECIFICATIONS)
// ============================================================================
async function initBookDetailsPage() {
    const container = document.getElementById('book-details-view');
    if (!container) return;

    const params = new URLSearchParams(window.location.search);
    const bookId = params.get('id');

    let book = null;
    let isOwner = false;
    let canRequest = true;

    if (bookId) {
        try {
            const response = await fetch(`${APP_CONTEXT}/api/books?id=${bookId}`);
            if (response.ok) {
                const data = await response.json();
                if (data.success && data.book) {
                    book = data.book;
                    isOwner = data.isOwner;
                    canRequest = data.canRequest;
                }
            }
        } catch (e) {
            console.warn('Could not fetch book from server, checking fallback.');
        }
    }

    if (!book) {
        // Fallback default academic book
        book = {
            id: bookId || 101,
            title: "Introduction to Algorithms (CLRS 4th Edition)",
            author: "Thomas H. Cormen, Charles E. Leiserson, Ronald L. Rivest, Clifford Stein",
            subject: "Computer Science",
            edition: "4th Edition (MIT Press)",
            isbn: "9780262046305",
            courseCode: "CS210",
            conditionGrade: "LIKE_NEW",
            sharingMode: "SWAP",
            status: "AVAILABLE",
            swapPreferences: "Open to Database System Concepts (Korth) or Operating Systems (Silberschatz)",
            description: "Comprehensive textbook on algorithms covering sorting, data structures, graph algorithms, and complexity. Standard reference used in CS210 and competitive programming courses across campus.",
            ownerName: "Aarav Sharma",
            ownerDepartment: "Computer Science & Engineering",
            ownerCampus: "Central Campus Library Block B",
            imageUrl: "https://images.unsplash.com/photo-1532012164546-f432f2e3777f?auto=format&fit=crop&w=600&q=80"
        };
    }

    // Set page breadcrumb
    const bcTitle = document.getElementById('breadcrumb-title');
    if (bcTitle) bcTitle.textContent = book.title;

    // Track recently viewed
    if (typeof Store !== 'undefined' && Store.recordRecentlyViewed) {
        Store.recordRecentlyViewed(book);
    }

    const isSaved = typeof Store !== 'undefined' && Store.isSaved(book.id);
    const modeClass = book.sharingMode === 'GIVEAWAY' ? 'mode-giveaway' : (book.sharingMode === 'LENDING' ? 'mode-lending' : 'mode-swap');
    const conditionLabel = (book.conditionGrade || 'GOOD').replace('_', ' ');

    let modeDescriptionBanner = '';
    let primaryButtonText = 'Request Book Exchange';

    if (book.sharingMode === 'SWAP') {
        modeDescriptionBanner = `
            <div class="b4u-mode-panel b4u-panel-swap">
                <div class="panel-icon">🔄</div>
                <div class="panel-text">
                    <strong>Peer Book Swap</strong>
                    <p>${book.swapPreferences ? `Owner's Swap Preference: <em>"${escapeHtml(book.swapPreferences)}"</em>` : 'Propose an academic book from your collection to exchange with this peer.'}</p>
                </div>
            </div>
        `;
        primaryButtonText = 'Propose Book Swap';
    } else if (book.sharingMode === 'GIVEAWAY') {
        modeDescriptionBanner = `
            <div class="b4u-mode-panel b4u-panel-giveaway">
                <div class="panel-icon">🎁</div>
                <div class="panel-text">
                    <strong>Free Student Giveaway</strong>
                    <p>This book is completely free for any university peer in need. Zero payment, zero fees.</p>
                </div>
            </div>
        `;
        primaryButtonText = 'Claim Free Book';
    } else if (book.sharingMode === 'LENDING') {
        const days = book.lendingMaxDurationDays || 14;
        modeDescriptionBanner = `
            <div class="b4u-mode-panel b4u-panel-lending">
                <div class="panel-icon">⏱️</div>
                <div class="panel-text">
                    <strong>Temporary Semester Loan</strong>
                    <p>Available on loan for up to <strong>${days} days</strong>. Return in the same condition on the agreed date.</p>
                </div>
            </div>
        `;
        primaryButtonText = `Request Loan (${days} Days)`;
    }

    const bookPayload = JSON.stringify({
        id: book.id,
        title: book.title,
        author: book.author,
        coverImage: book.imageUrl,
        sharingMode: book.sharingMode,
        subject: book.subject || 'Academic',
        conditionGrade: book.conditionGrade || 'Good',
        courseCode: book.courseCode || ''
    }).replace(/"/g, '&quot;');

    container.innerHTML = `
        <div class="b4u-detail-layout">
            
            <!-- Left Column: Large Book Cover -->
            <div class="b4u-detail-media-col">
                <div class="b4u-detail-frame">
                    <img src="${escapeHtml(book.imageUrl || 'https://images.unsplash.com/photo-1544716278-ca5e3f4abd8c?w=600&q=80')}" alt="${escapeHtml(book.title)}" class="b4u-detail-img" onerror="this.src='https://images.unsplash.com/photo-1544716278-ca5e3f4abd8c?w=600&q=80';">
                </div>

                <div class="b4u-trust-highlights">
                    <div class="highlight-item">
                        <span>🎓</span> Verified Campus Student
                    </div>
                    <div class="highlight-item">
                        <span>🏛️</span> Campus Handover
                    </div>
                    <div class="highlight-item">
                        <span>⚡</span> 100% Free Sharing
                    </div>
                </div>
            </div>

            <!-- Right Column: Book Specifications & Exchange Actions -->
            <div class="b4u-detail-info-col">
                
                <div class="b4u-detail-header-row">
                    <div>
                        <span class="b4u-mode-badge ${modeClass}" style="margin-bottom: 0.75rem; display: inline-block;">
                            ${escapeHtml(book.sharingMode || 'SWAP')}
                        </span>
                        <h1 class="b4u-detail-title">${escapeHtml(book.title)}</h1>
                        <p class="b4u-detail-author">By <strong>${escapeHtml(book.author || 'Author')}</strong></p>
                    </div>

                    <div class="b4u-detail-actions-top">
                        <button class="btn btn-outline btn-icon ${isSaved ? 'active' : ''}" type="button" aria-label="Save Book" onclick='Store.toggleSave(${bookPayload}, this)' title="Save to Reading List">
                            <svg width="20" height="20" viewBox="0 0 24 24" fill="${isSaved ? 'currentColor' : 'none'}" stroke="currentColor" stroke-width="2">
                                <path d="M19 21l-7-5-7 5V5a2 2 0 0 1 2-2h10a2 2 0 0 1 2 2z"></path>
                            </svg>
                        </button>
                        <button class="btn btn-outline btn-icon" type="button" aria-label="Share Link" onclick="navigator.clipboard && navigator.clipboard.writeText(window.location.href); Store.showToast('Book link copied to clipboard!', 'info');" title="Copy Book Link">
                            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                                <circle cx="18" cy="5" r="3"></circle>
                                <circle cx="6" cy="12" r="3"></circle>
                                <circle cx="18" cy="19" r="3"></circle>
                                <line x1="8.59" y1="13.51" x2="15.42" y2="17.49"></line>
                                <line x1="15.41" y1="6.51" x2="8.59" y2="10.49"></line>
                            </svg>
                        </button>
                    </div>
                </div>

                <!-- Sharing Mode Banner -->
                ${modeDescriptionBanner}

                <!-- Owner Profile Box -->
                <div class="b4u-owner-card">
                    <div class="owner-avatar">
                        ${escapeHtml((book.ownerName || 'Student').substring(0, 2).toUpperCase())}
                    </div>
                    <div class="owner-info">
                        <div class="owner-name">${escapeHtml(book.ownerName || 'Campus Student')}</div>
                        <div class="owner-meta">
                            <span>🏛️ ${escapeHtml(book.ownerCampus || 'Main Campus')}</span>
                            ${book.ownerDepartment ? `<span>• 📚 ${escapeHtml(book.ownerDepartment)}</span>` : ''}
                        </div>
                    </div>
                </div>

                <!-- Primary Action Buttons -->
                <div class="b4u-action-cta-row">
                    ${isOwner ? `
                        <a href="${APP_CONTEXT}/edit-book.html?id=${book.id}" class="btn btn-primary" style="flex: 1; text-align: center; padding: 0.9rem;">
                            ✏️ Edit Your Listing
                        </a>
                    ` : `
                        <button type="button" class="btn btn-primary btn-lg" style="flex: 1;" onclick="openRequestBookModal(${book.id}, '${escapeHtml(book.title).replace(/'/g, "\\'")}', '${book.sharingMode || 'SWAP'}', ${book.lendingMaxDurationDays || 14}, '${escapeHtml(book.ownerCampus || '').replace(/'/g, "\\'")}')">
                            🤝 ${primaryButtonText}
                        </button>
                    `}
                    <button type="button" class="btn btn-outline btn-lg" onclick='Store.toggleSave(${bookPayload})'>
                        🔖 Save for Later
                    </button>
                </div>

                <!-- Specifications Grid -->
                <div class="b4u-specs-card">
                    <h3 class="specs-title">Academic Textbook Specifications</h3>
                    <div class="specs-grid">
                        <div class="spec-cell">
                            <span class="spec-label">Subject / Faculty</span>
                            <span class="spec-value">${escapeHtml(book.subject || 'Computer Science')}</span>
                        </div>
                        <div class="spec-cell">
                            <span class="spec-label">Condition Grade</span>
                            <span class="spec-value"><span class="badge-condition">${escapeHtml(conditionLabel)}</span></span>
                        </div>
                        <div class="spec-cell">
                            <span class="spec-label">Course Code</span>
                            <span class="spec-value">${escapeHtml(book.courseCode || 'General Campus')}</span>
                        </div>
                        <div class="spec-cell">
                            <span class="spec-label">Edition</span>
                            <span class="spec-value">${escapeHtml(book.edition || 'Standard Student Edition')}</span>
                        </div>
                        <div class="spec-cell">
                            <span class="spec-label">ISBN</span>
                            <span class="spec-value">${escapeHtml(book.isbn || 'Not specified')}</span>
                        </div>
                        <div class="spec-cell">
                            <span class="spec-label">Campus Handover</span>
                            <span class="spec-value">${escapeHtml(book.ownerCampus || 'Central Library Block B')}</span>
                        </div>
                    </div>
                </div>

                <!-- Description -->
                <div class="b4u-desc-card">
                    <h3 class="specs-title">About this Textbook</h3>
                    <p class="b4u-desc-text">
                        ${escapeHtml(book.description || 'No additional synopsis provided by the owner. Connect with the owner to learn more about the contents or arrange an inspection during campus handover.')}
                    </p>
                </div>

            </div>
        </div>
    `;
}

// ============================================================================
// 6. REQUEST BOOK INTERACTIVE MODAL CONTROLLER
// ============================================================================
async function openRequestBookModal(bookId, title, sharingMode, maxDurationDays, ownerCampus) {
    const modal = document.getElementById('request-book-modal');
    if (!modal) {
        // Fallback alert if on an un-updated page
        alert(`🎓 Connect with Owner for "${title}":\nPlease visit the listing page to submit a verified exchange request.`);
        return;
    }

    const alertBox = document.getElementById('modal-alert-box');
    if (alertBox) alertBox.style.display = 'none';

    document.getElementById('req-book-id').value = bookId;
    document.getElementById('req-exchange-mode').value = sharingMode;
    document.getElementById('request-modal-title').textContent = `Request: ${title}`;
    document.getElementById('req-handover-location').value = ownerCampus || 'Central Library Block B, 1st Floor';
    document.getElementById('req-message').value = '';

    const swapGroup = document.getElementById('swap-offered-group');
    const lendingGroup = document.getElementById('lending-duration-group');
    const banner = document.getElementById('request-modal-mode-banner');

    if (sharingMode === 'SWAP') {
        if (swapGroup) swapGroup.style.display = 'block';
        if (lendingGroup) lendingGroup.style.display = 'none';
        if (banner) {
            banner.innerHTML = `
                <div class="b4u-mode-panel b4u-panel-swap" style="margin: 0;">
                    <div class="panel-icon">🔄</div>
                    <div class="panel-text">
                        <strong>Peer Book Swap</strong>
                        <p style="margin: 0;">Select one of your listed books to trade in exchange for this textbook.</p>
                    </div>
                </div>
            `;
        }

        // Fetch authenticated user's available books
        const select = document.getElementById('req-offered-book-id');
        if (select) {
            select.innerHTML = '<option value="">Loading your available listings...</option>';
            try {
                const res = await fetch(`${APP_CONTEXT}/api/books?myBooks=true&status=AVAILABLE`);
                if (res.status === 401) {
                    window.location.href = `${APP_CONTEXT}/login.html?authRequired=true`;
                    return;
                }
                const data = await res.json();
                if (data.books && data.books.length > 0) {
                    select.innerHTML = '<option value="">-- Choose one of your books to offer --</option>' +
                        data.books.map(b => `<option value="${b.id}">${escapeHtml(b.title)} (${escapeHtml(b.subject)})</option>`).join('');
                } else {
                    select.innerHTML = '<option value="">⚠️ You have no available books to offer</option>';
                }
            } catch (e) {
                select.innerHTML = '<option value="">Error loading your listings</option>';
            }
        }

    } else if (sharingMode === 'LENDING') {
        if (swapGroup) swapGroup.style.display = 'none';
        if (lendingGroup) lendingGroup.style.display = 'block';
        const days = maxDurationDays || 14;
        const durInput = document.getElementById('req-duration-days');
        if (durInput) {
            durInput.max = days;
            durInput.value = Math.min(14, days);
        }
        const durHelp = document.getElementById('lending-duration-help');
        if (durHelp) durHelp.textContent = `Maximum duration permitted by owner: ${days} days`;

        if (banner) {
            banner.innerHTML = `
                <div class="b4u-mode-panel b4u-panel-lending" style="margin: 0;">
                    <div class="panel-icon">⏱️</div>
                    <div class="panel-text">
                        <strong>Temporary Semester Loan</strong>
                        <p style="margin: 0;">Borrow this textbook for your coursework and return on the agreed date.</p>
                    </div>
                </div>
            `;
        }

    } else { // GIVEAWAY
        if (swapGroup) swapGroup.style.display = 'none';
        if (lendingGroup) lendingGroup.style.display = 'none';
        if (banner) {
            banner.innerHTML = `
                <div class="b4u-mode-panel b4u-panel-giveaway" style="margin: 0;">
                    <div class="panel-icon">🎁</div>
                    <div class="panel-text">
                        <strong>Free Student Giveaway</strong>
                        <p style="margin: 0;">This book is offered completely free of charge to any student in need.</p>
                    </div>
                </div>
            `;
        }
    }

    modal.classList.add('active');
}

function closeRequestBookModal() {
    const modal = document.getElementById('request-book-modal');
    if (modal) modal.classList.remove('active');
}

async function submitBookRequest(event) {
    event.preventDefault();

    const bookId = document.getElementById('req-book-id').value;
    const mode = document.getElementById('req-exchange-mode').value;
    const location = document.getElementById('req-handover-location').value.trim();
    const message = document.getElementById('req-message').value.trim();
    const btn = document.getElementById('btn-submit-book-req');
    const alertBox = document.getElementById('modal-alert-box');

    let offeredBookId = null;
    let proposedDays = null;

    if (mode === 'SWAP') {
        const sel = document.getElementById('req-offered-book-id');
        offeredBookId = sel ? sel.value : null;
        if (!offeredBookId) {
            if (alertBox) {
                alertBox.textContent = 'Please select a book to offer in return for a SWAP exchange.';
                alertBox.style.display = 'block';
            }
            return;
        }
    } else if (mode === 'LENDING') {
        const inp = document.getElementById('req-duration-days');
        proposedDays = inp ? parseInt(inp.value, 10) : 14;
        if (!proposedDays || proposedDays <= 0) {
            if (alertBox) {
                alertBox.textContent = 'Please enter a valid positive duration in days.';
                alertBox.style.display = 'block';
            }
            return;
        }
    }

    btn.disabled = true;
    btn.textContent = 'Sending Request...';
    if (alertBox) alertBox.style.display = 'none';

    try {
        const payload = {
            bookId: parseInt(bookId, 10),
            exchangeMode: mode,
            offeredBookId: offeredBookId ? parseInt(offeredBookId, 10) : null,
            proposedDurationDays: proposedDays,
            requestMessage: message,
            handoverLocation: location
        };

        const response = await fetch(`${APP_CONTEXT}/api/exchange-requests`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(payload)
        });

        if (response.status === 401) {
            window.location.href = `${APP_CONTEXT}/login.html?authRequired=true`;
            return;
        }

        const data = await response.json();
        if (response.ok && data.success) {
            closeRequestBookModal();
            if (typeof Store !== 'undefined' && Store.showToast) {
                Store.showToast(data.message || 'Exchange request submitted successfully!', 'success');
            } else {
                alert(data.message || 'Exchange request submitted successfully!');
            }
            setTimeout(() => {
                window.location.href = `${APP_CONTEXT}/my-requests.html`;
            }, 1000);
        } else {
            if (alertBox) {
                alertBox.textContent = data.error || 'Failed to submit exchange request.';
                alertBox.style.display = 'block';
            }
            btn.disabled = false;
            btn.textContent = 'Send Request';
        }

    } catch (err) {
        if (alertBox) {
            alertBox.textContent = 'Network error: ' + err.message;
            alertBox.style.display = 'block';
        }
        btn.disabled = false;
        btn.textContent = 'Send Request';
    }
}

// Auto-initialize based on active DOM elements
document.addEventListener('DOMContentLoaded', () => {
    initExplorePage();
    initMyBooksPage();
    initAddBookPage();
    initEditBookPage();
    initBookDetailsPage();
});
