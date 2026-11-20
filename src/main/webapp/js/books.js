/**
 * ============================================================================
 * books.js - Client-Side Book Management & Discovery Controller
 * ============================================================================
 * University Academic Project: Student Peer-to-Peer Book Exchange System (Book4u)
 * Technology: Vanilla JavaScript (ES6+ Fetch API, DOM API)
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

    grid.innerHTML = '<p style="grid-column: 1/-1; text-align: center; color: var(--text-muted); padding: 2rem;">Loading academic book catalog...</p>';

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
        if (countEl) countEl.innerText = `${data.count || 0} books available`;

        renderExploreCards(data.books);

    } catch (err) {
        console.error('Failed to load explore books:', err);
        grid.innerHTML = `
            <div class="empty-state-box">
                <span class="empty-state-icon">⚠️</span>
                <h3>Unable to Connect to Server</h3>
                <p>Ensure Tomcat is running and MySQL database is connected.</p>
            </div>
        `;
    }
}

function renderExploreCards(books) {
    const grid = document.getElementById('explore-book-grid');
    if (!grid) return;

    if (!books || books.length === 0) {
        grid.innerHTML = `
            <div class="empty-state-box" style="grid-column: 1/-1; text-align: center; padding: 4rem 1rem; background: #FFF; border: 1px solid var(--cw-border); border-radius: 12px;">
                <span class="empty-state-icon" style="font-size: 3rem;">🔍</span>
                <h3 style="font-family: var(--font-editorial); font-size: 1.5rem; margin: 0.5rem 0;">No Textbooks Found</h3>
                <p style="color: #64748B; margin-bottom: 1.5rem;">No books match your current search query or filter criteria. Try adjusting keywords or clearing filters.</p>
                <button type="button" class="cw-btn cw-btn-outline" onclick="resetExploreFilters()">Reset All Filters</button>
            </div>
        `;
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
                <h3 class="cw-card-title"><a href="${APP_CONTEXT}/book-details.html?id=${book.id}" title="${escapeHtml(book.title)}">${escapeHtml(book.title)}</a></h3>
                <p class="cw-card-author">By ${escapeHtml(book.author)} • <span style="color: var(--cw-burgundy); font-weight: 600;">${escapeHtml(book.subject || 'Academic')}</span></p>
                <div class="cw-card-price-row">
                    <span class="cw-mrp-strike">₹ 699</span>
                    <span class="cw-deal-price">${modePrice}</span>
                    <span class="cw-discount-pill">${discountPill}</span>
                </div>
                <div style="font-size: 0.78rem; color: #64748B; margin-bottom: 0.75rem;">
                    Condition: <span class="badge-condition">${escapeHtml(book.conditionGrade || 'Good')}</span> • Owner: <strong>${escapeHtml(book.ownerName || 'Student')}</strong>
                </div>
                <div style="display: flex; gap: 0.5rem;">
                    <button class="cw-add-bag-btn" type="button" style="flex: 1;" onclick='Store.addToCart(${bookPayload})'>
                        <span>🛍️</span> Add to Bag
                    </button>
                    <a href="${APP_CONTEXT}/book-details.html?id=${book.id}" class="cw-btn cw-btn-outline" style="padding: 0.5rem 0.75rem; font-size: 0.85rem;" title="View Details">
                        Details
                    </a>
                </div>
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
    grid.innerHTML = '<p style="grid-column: 1/-1; text-align: center; color: var(--text-muted); padding: 2rem;">Loading your book listings...</p>';

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
        renderMyBooksCards(data.books);

    } catch (err) {
        console.error('Failed to load My Books:', err);
        grid.innerHTML = `
            <div class="empty-state-box">
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
            <div class="empty-state-box">
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
            ? `<div class="book-cover-container"><img src="${escapeHtml(book.imageUrl)}" alt="${escapeHtml(book.title)}" class="book-cover-img" onerror="this.parentElement.innerHTML='<div class=\\'book-cover-placeholder\\'>📚 <span>${escapeHtml(book.subject)}</span></div>';"></div>`
            : `<div class="book-cover-container"><div class="book-cover-placeholder">📚 <span>${escapeHtml(book.subject)}</span></div></div>`;

        card.innerHTML = `
            ${coverHtml}
            <div class="book-card-body">
                <div class="book-header-line">
                    <span class="mode-badge ${getModeBadgeClass(book.sharingMode)}">${escapeHtml(book.sharingMode)}</span>
                    <span class="status-pill ${getStatusBadgeClass(book.status)}">${escapeHtml(book.status)}</span>
                </div>

                <h3 class="book-title" title="${escapeHtml(book.title)}">${escapeHtml(book.title)}</h3>
                <p class="book-author">By ${escapeHtml(book.author)}</p>

                <div class="book-meta-grid">
                    <div><strong>Subject:</strong> ${escapeHtml(book.subject)}</div>
                    <div><strong>Condition:</strong> <span class="badge-condition">${escapeHtml(book.conditionGrade)}</span></div>
                    <div><strong>Listed:</strong> ${book.createdAt ? new Date(book.createdAt).toLocaleDateString() : '--'}</div>
                </div>

                <div class="book-card-actions">
                    <a href="${APP_CONTEXT}/book-details.html?id=${book.id}" class="btn btn-outline btn-sm">View</a>
                    <a href="${APP_CONTEXT}/edit-book.html?id=${book.id}" class="btn btn-outline btn-sm" style="color: var(--primary-color);">✏️ Edit</a>
                    <button type="button" class="btn btn-outline btn-sm" style="color: var(--danger-color); border-color: #fca5a5;" onclick="confirmDeleteBook(${book.id}, '${escapeHtml(book.title)}')">🗑️ Delete</button>
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

    // Check status banners
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
        showBookAlert('edit-book-alert', 'Missing book ID parameter.', 'danger');
        return;
    }

    try {
        const response = await fetch(`${APP_CONTEXT}/api/books?id=${bookId}`);
        if (!response.ok) throw new Error('Failed to fetch book');

        const data = await response.json();
        if (!data.success || !data.book) {
            showBookAlert('edit-book-alert', 'Book listing not found.', 'danger');
            return;
        }

        if (!data.isOwner) {
            showBookAlert('edit-book-alert', '🔒 Unauthorized: You do not own this book listing.', 'danger');
            document.getElementById('btn-save-edit').disabled = true;
            return;
        }

        const b = data.book;
        document.getElementById('edit-book-id').value = b.id;
        document.getElementById('edit-title').value = b.title || '';
        document.getElementById('edit-author').value = b.author || '';
        document.getElementById('edit-subject').value = b.subject || '';
        if (document.getElementById('edit-isbn')) document.getElementById('edit-isbn').value = b.isbn || '';
        if (document.getElementById('edit-edition')) document.getElementById('edit-edition').value = b.edition || '';
        if (document.getElementById('edit-course')) document.getElementById('edit-course').value = b.courseCode || '';
        document.getElementById('edit-condition').value = b.conditionGrade || 'GOOD';
        document.getElementById('edit-mode').value = b.sharingMode || 'SWAP';
        document.getElementById('edit-status').value = b.status || 'AVAILABLE';
        if (document.getElementById('edit-desc')) document.getElementById('edit-desc').value = b.description || '';
        if (document.getElementById('edit-image')) document.getElementById('edit-image').value = b.imageUrl || '';
        if (document.getElementById('edit-swap')) document.getElementById('edit-swap').value = b.swapPreferences || '';

        const lendingGroup = document.getElementById('edit-lending-duration-wrapper');
        const modeSelect = document.getElementById('edit-mode');
        if (lendingGroup && modeSelect) {
            lendingGroup.style.display = (b.sharingMode === 'LENDING') ? 'block' : 'none';
            if (b.lendingMaxDurationDays) {
                document.getElementById('edit-lending-days').value = b.lendingMaxDurationDays;
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
// 5. BOOK DETAILS CONTROLLER (Matching Crossword.in Screenshot 2)
// ============================================================================
async function initBookDetailsPage() {
    const container = document.getElementById('book-details-view');
    if (!container) return;

    const params = new URLSearchParams(window.location.search);
    const bookId = params.get('id');
    const demoKey = params.get('demo');

    // Demo Catalog for instant merchandising display
    const demoCatalog = {
        'harry-potter': {
            id: 'demo-hp1',
            title: "Harry Potter And The Philosopher's Stone",
            author: "J.K Rowling",
            subject: "Fiction & Fantasy",
            edition: "Bloomsbury Special Campus Edition",
            isbn: "9780747532743",
            courseCode: "LIT101",
            conditionGrade: "LIKE_NEW",
            sharingMode: "SWAP",
            status: "AVAILABLE",
            mrp: 599,
            price: 450,
            discount: "₹ 149 Off",
            rating: 5,
            reviewsCount: 10,
            tags: ["Magical Adventure", "Wizarding World", "Friendship And Bravery", "Childhood Imagination", "Epic Journey"],
            imageUrl: "https://upload.wikimedia.org/wikipedia/en/6/6b/Harry_Potter_and_the_Philosopher%27s_Stone_Book_Cover.jpg",
            description: "Harry Potter has never even heard of Hogwarts when the letters start dropping on the doormat at number four, Privet Drive. Addressed in green ink on yellowish parchment with a purple seal, they are swiftly confiscated by his grisly aunt and uncle. Then, on Harry's eleventh birthday, a great beetle-eyed giant of a man called Rubeus Hagrid bursts in with some astonishing news: Harry Potter is a wizard, and he has a place at Hogwarts School of Witchcraft and Wizardry.",
            ownerName: "Campus Literature Society",
            ownerDepartment: "English & Humanities",
            ownerCampus: "Central Campus",
            series: [
                { part: "Part 2", title: "Chamber of Secrets", price: 450, mrp: 599, img: "https://upload.wikimedia.org/wikipedia/en/5/5c/Harry_Potter_and_the_Chamber_of_Secrets.jpg" },
                { part: "Part 3", title: "Prisoner of Azkaban", price: 450, mrp: 599, img: "https://upload.wikimedia.org/wikipedia/en/a/a0/Harry_Potter_and_the_Prisoner_of_Azkaban.jpg" },
                { part: "Part 4", title: "Goblet of Fire", price: 640, mrp: 799, img: "https://upload.wikimedia.org/wikipedia/en/b/b6/Harry_Potter_and_the_Goblet_of_Fire_cover.png" },
                { part: "Part 5", title: "Order of the Phoenix", price: 720, mrp: 899, img: "https://upload.wikimedia.org/wikipedia/en/7/70/Harry_Potter_and_the_Order_of_the_Phoenix.jpg" },
                { part: "Part 6", title: "Half-Blood Prince", price: 640, mrp: 799, img: "https://upload.wikimedia.org/wikipedia/en/b/b5/Harry_Potter_and_the_Half-Blood_Prince_cover.png" },
                { part: "Part 7", title: "Deathly Hallows", price: 640, mrp: 799, img: "https://upload.wikimedia.org/wikipedia/en/a/a9/Harry_Potter_and_the_Deathly_Hallows.jpg" }
            ]
        },
        'belonging': {
            id: 'bs-1',
            title: "Belonging: A Journey of Love",
            author: "Sonia Gandhi",
            subject: "Biography & Memoir",
            edition: "1st Hardcover Edition",
            isbn: "9788193856214",
            courseCode: "POL201",
            conditionGrade: "LIKE_NEW",
            sharingMode: "SWAP",
            status: "AVAILABLE",
            mrp: 1299,
            price: 998,
            discount: "₹ 301 Off",
            rating: 5,
            reviewsCount: 18,
            tags: ["Memoir", "History", "Indian Politics", "Biography", "Literary Non-Fiction"],
            imageUrl: "https://images.unsplash.com/photo-1544716278-ca5e3f4abd8c?auto=format&fit=crop&w=600&q=80",
            description: "A poignant, intimate autobiographical reflection weaving through decades of personal memories, leadership challenges, and private family devotion.",
            ownerName: "Pooja Sharma",
            ownerDepartment: "Political Science",
            ownerCampus: "North Campus"
        },
        'ward-d': {
            id: 'bs-3',
            title: "Ward D",
            author: "Freida McFadden",
            subject: "Psychological Thriller",
            edition: "Paperback Edition",
            isbn: "9781464221088",
            courseCode: "GEN",
            conditionGrade: "VERY_GOOD",
            sharingMode: "SWAP",
            status: "AVAILABLE",
            mrp: 550,
            price: 413,
            discount: "₹ 137 Off",
            rating: 5,
            reviewsCount: 34,
            tags: ["Psychological Thriller", "Medical Suspense", "Locked Room Mystery", "Twisted Plot"],
            imageUrl: "https://images.unsplash.com/photo-1518373714866-3f1478910cc0?auto=format&fit=crop&w=600&q=80",
            description: "Amy Brenner is spending the night on a locked psychiatric ward. Amy has been dreading her night shift on Ward D, the hospital’s inpatient unit. But as the night wears on, Amy becomes convinced something terrible is happening within these locked doors.",
            ownerName: "Rahul Mehta",
            ownerDepartment: "Biotechnology",
            ownerCampus: "Main Campus"
        },
        'the-teacher': {
            id: 'bs-4',
            title: "The Teacher",
            author: "Freida McFadden",
            subject: "Thriller & Suspense",
            edition: "1st Edition",
            isbn: "9781464221194",
            courseCode: "GEN",
            conditionGrade: "LIKE_NEW",
            sharingMode: "SWAP",
            status: "AVAILABLE",
            mrp: 550,
            price: 413,
            discount: "₹ 137 Off",
            rating: 5,
            reviewsCount: 29,
            tags: ["Thriller", "High School Drama", "Dark Secrets", "Page Turner"],
            imageUrl: "https://images.unsplash.com/photo-1495640388908-05fa85288e61?auto=format&fit=crop&w=600&q=80",
            description: "Eve has a good life as a high school math teacher. But a scandal with an ex-student rocked the school last year, and now Addie is in Eve's class. No one trusts Addie. But nobody knows what really happened either.",
            ownerName: "Ananya Roy",
            ownerDepartment: "Psychology",
            ownerCampus: "South Campus"
        },
        'the-boyfriend': {
            id: 'bs-5',
            title: "The Boyfriend",
            author: "Freida McFadden",
            subject: "Thriller & Mystery",
            edition: "1st Paperback",
            isbn: "9781464221255",
            courseCode: "GEN",
            conditionGrade: "NEW",
            sharingMode: "SWAP",
            status: "AVAILABLE",
            mrp: 550,
            price: 413,
            discount: "₹ 137 Off",
            rating: 5,
            reviewsCount: 42,
            tags: ["Romantic Suspense", "Bestselling Mystery", "Deadly Secret"],
            imageUrl: "https://images.unsplash.com/photo-1543002588-bfa74002ed7e?auto=format&fit=crop&w=600&q=80",
            description: "Sydney Shaw is unlucky in love. But when handsome doctor Tom enters her life, she feels like she has finally found the one. Until a series of brutal murders points straight to someone close.",
            ownerName: "Karan Johar",
            ownerDepartment: "Mass Communication",
            ownerCampus: "Main Campus"
        }
    };

    let book = null;
    let isOwner = false;
    let canRequest = true;

    if (demoKey && demoCatalog[demoKey]) {
        book = demoCatalog[demoKey];
    } else if (bookId) {
        try {
            const response = await fetch(`${APP_CONTEXT}/api/books?id=${bookId}`);
            if (response.ok) {
                const data = await response.json();
                if (data.success && data.book) {
                    book = data.book;
                    isOwner = data.isOwner;
                    canRequest = data.canRequest;
                    book.mrp = 699;
                    book.price = book.sharingMode === 'SWAP' ? 0 : 0;
                    book.discount = book.sharingMode === 'GIVEAWAY' ? 'Free Gift' : (book.sharingMode === 'LENDING' ? `${book.lendingMaxDurationDays || 14}d Loan` : 'Free Trade');
                    book.rating = 5;
                    book.reviewsCount = 12;
                    book.tags = [book.subject || 'Academic', book.courseCode || 'University', book.sharingMode, 'Campus Verified'];
                    if (!book.imageUrl) {
                        book.imageUrl = 'https://images.unsplash.com/photo-1544716278-ca5e3f4abd8c?auto=format&fit=crop&w=600&q=80';
                    }
                }
            }
        } catch (e) {
            console.warn('Could not fetch book from server, checking fallback.');
        }
    }

    if (!book) {
        // Fallback default book (Harry Potter)
        book = demoCatalog['harry-potter'];
    }

    // Set page breadcrumb
    const bcTitle = document.getElementById('breadcrumb-title');
    if (bcTitle) bcTitle.textContent = book.title;

    // Build Payload for Cart & Wishlist
    const payloadJson = JSON.stringify({
        id: book.id,
        title: book.title,
        author: book.author,
        price: book.price !== undefined ? book.price : 450,
        mrp: book.mrp || 599,
        discount: book.discount || 'Special Offer',
        image: book.imageUrl
    }).replace(/"/g, '&quot;');

    // Build Tags HTML
    const tagsHtml = (book.tags || ['Academic Excellence', 'Campus Verified', 'Semester Special'])
        .map(tag => `<span class="cw-detail-tag">${escapeHtml(tag)}</span>`)
        .join('');

    // Build Books in This Series (Screenshot 2)
    let seriesHtml = '';
    if (book.series && book.series.length > 0) {
        seriesHtml = `
            <div class="cw-series-section" style="margin-top: 2.5rem; padding-top: 2rem; border-top: 1px solid var(--cw-border);">
                <h3 style="font-family: var(--font-sans); font-size: 1.25rem; font-weight: 800; color: #1E293B; margin-bottom: 1.25rem;">
                    Books in This Series
                </h3>
                <div class="cw-series-row" style="display: flex; gap: 1rem; overflow-x: auto; padding-bottom: 1rem;">
                    ${book.series.map(item => `
                        <div class="cw-series-item" style="min-width: 130px; max-width: 130px; text-align: center; border: 1px solid var(--cw-border); border-radius: 8px; padding: 0.75rem 0.5rem; background: #FFFFFF;">
                            <img src="${item.img}" alt="${item.title}" style="width: 90px; height: 130px; object-fit: cover; border-radius: 4px; margin: 0 auto 0.5rem auto;">
                            <div style="font-size: 0.8rem; font-weight: 700; color: #1E293B; margin-bottom: 0.25rem;">${item.part}</div>
                            <div style="font-size: 0.85rem; font-weight: 800; color: #1E293B;">₹ ${item.price}</div>
                            <div style="font-size: 0.75rem; color: #94A3B8; text-decoration: line-through;">₹ ${item.mrp}</div>
                        </div>
                    `).join('')}
                </div>
            </div>
        `;
    }

    // Two-Column Layout HTML (Exact match to Screenshot 2)
    container.innerHTML = `
        <div class="cw-product-detail-layout" style="display: grid; grid-template-columns: 460px 1fr; gap: 3.5rem; align-items: start;">
            
            <!-- Left Column: Large Book Cover Gallery -->
            <div class="cw-detail-gallery-col">
                <div class="cw-detail-main-frame" style="background: #FFFFFF; border: 1px solid var(--cw-border); border-radius: 12px; padding: 2.5rem; text-align: center; box-shadow: 0 4px 20px rgba(0,0,0,0.04);">
                    <img src="${escapeHtml(book.imageUrl)}" alt="${escapeHtml(book.title)}" class="cw-detail-big-img" style="max-height: 480px; width: auto; max-width: 100%; object-fit: contain; border-radius: 4px; margin: 0 auto; box-shadow: 0 10px 25px rgba(0,0,0,0.15);">
                </div>

                <div class="cw-trust-badge-row" style="display: flex; justify-content: center; gap: 1.5rem; margin-top: 1.5rem; font-size: 0.85rem; color: #475569;">
                    <span>🛡️ 100% Student Verified</span>
                    <span>📦 Free Campus Pickup</span>
                    <span>🤝 Safe Handover</span>
                </div>
            </div>

            <!-- Right Column: Product Info & Actions -->
            <div class="cw-detail-info-col">
                
                <!-- Title & Wishlist/Share Row (Screenshot 2 Top Right) -->
                <div style="display: flex; justify-content: space-between; align-items: flex-start; gap: 1.5rem;">
                    <h1 class="cw-detail-title" style="font-family: var(--font-sans); font-size: 2.2rem; font-weight: 800; line-height: 1.25; color: #1E293B; margin: 0;">
                        ${escapeHtml(book.title)}
                    </h1>

                    <div class="cw-detail-top-actions" style="display: flex; gap: 0.5rem; flex-shrink: 0;">
                        <button class="cw-circle-icon-btn" type="button" aria-label="Add to Wishlist" onclick='Store.toggleWishlist(${payloadJson})' style="width: 44px; height: 44px; border-radius: 50%; border: 1px solid var(--cw-border); background: #FFFFFF; display: flex; align-items: center; justify-content: center; cursor: pointer; transition: background 0.2s;">
                            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                                <path d="M20.84 4.61a5.5 5.5 0 0 0-7.78 0L12 5.67l-1.06-1.06a5.5 5.5 0 0 0-7.78 7.78l1.06 1.06L12 21.23l7.78-7.78 1.06-1.06a5.5 5.5 0 0 0 0-7.78z"></path>
                            </svg>
                        </button>
                        <button class="cw-circle-icon-btn" type="button" aria-label="Share" onclick="navigator.clipboard && navigator.clipboard.writeText(window.location.href); Store.showToast('Book link copied to clipboard!', 'info');" style="width: 44px; height: 44px; border-radius: 50%; border: 1px solid var(--cw-border); background: #FFFFFF; display: flex; align-items: center; justify-content: center; cursor: pointer;">
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

                <!-- Author Link -->
                <div style="margin-top: 0.5rem;">
                    <a href="explore.html?query=${encodeURIComponent(book.author)}" style="color: #2563EB; font-size: 1.05rem; font-weight: 600; text-decoration: none;">
                        ${escapeHtml(book.author)}
                    </a>
                </div>

                <!-- Star Ratings & Review Count (Screenshot 2) -->
                <div class="cw-ratings-row" style="display: flex; align-items: center; gap: 0.75rem; margin-top: 0.75rem; font-size: 0.9rem;">
                    <span style="color: #F59E0B; letter-spacing: 2px;">★★★★★</span>
                    <span style="font-weight: 600; color: #1E293B;">${book.reviewsCount || 10} reviews</span>
                    <span style="color: #CBD5E1;">|</span>
                    <span style="color: #64748B;">🗨️ No questions</span>
                </div>

                <!-- Feature / Genre Tags Pills (Screenshot 2) -->
                <div class="cw-tags-wrap" style="display: flex; flex-wrap: wrap; gap: 0.5rem; margin-top: 1.25rem;">
                    ${tagsHtml}
                </div>

                <!-- Price Row Box (Screenshot 2) -->
                <div class="cw-price-box" style="margin-top: 1.5rem; padding: 1.25rem 0; border-top: 1px solid var(--cw-border); border-bottom: 1px solid var(--cw-border);">
                    <div style="display: flex; align-items: baseline; gap: 0.75rem;">
                        <span class="cw-mrp-label" style="font-size: 1.15rem; color: #64748B; text-decoration: line-through;">
                            MRP: ₹ ${book.mrp || 599}
                        </span>
                        <span class="cw-main-price" style="font-size: 2.2rem; font-weight: 800; color: #1E293B;">
                            ₹ ${book.price !== undefined ? book.price : 450}
                        </span>
                        <span class="cw-discount-badge" style="background: #DCFCE7; color: #15803D; font-size: 0.85rem; font-weight: 700; padding: 0.3rem 0.6rem; border-radius: 4px;">
                            ${escapeHtml(book.discount || '₹ 149 Off')}
                        </span>
                    </div>
                    <div style="font-size: 0.85rem; color: #64748B; margin-top: 0.25rem;">
                        (Incl. of all taxes • Zero Campus Shipping)
                    </div>
                </div>

                <!-- Action Buttons: Add to Bag & Buy Now (Screenshot 2) -->
                <div class="cw-detail-buttons-row" style="display: grid; grid-template-columns: 1fr 1fr; gap: 1rem; margin-top: 1.75rem;">
                    <button type="button" class="cw-btn-yellow-bag" onclick='Store.addToCart(${payloadJson})' style="background: var(--cw-yellow); color: #1E293B; border: none; border-radius: 8px; padding: 1rem; font-size: 1.05rem; font-weight: 700; display: flex; align-items: center; justify-content: center; gap: 0.5rem; cursor: pointer; transition: background 0.2s;">
                        <span>Add to Bag</span>
                        <span>🛍️</span>
                    </button>

                    <button type="button" class="cw-btn-yellow-buy" onclick='Store.addToCart(${payloadJson}); window.location.href="checkout.html";' style="background: var(--cw-yellow); color: #1E293B; border: none; border-radius: 8px; padding: 1rem; font-size: 1.05rem; font-weight: 700; display: flex; align-items: center; justify-content: center; gap: 0.5rem; cursor: pointer; transition: background 0.2s;">
                        <span>Buy Now</span>
                        <span>↗</span>
                    </button>
                </div>

                <!-- Books In This Series Section (Screenshot 2) -->
                ${seriesHtml}

                <!-- Specifications Table -->
                <div class="cw-specs-container" style="margin-top: 2.5rem; background: #FFFFFF; border: 1px solid var(--cw-border); border-radius: 12px; padding: 1.5rem;">
                    <h3 style="font-family: var(--font-sans); font-size: 1.15rem; font-weight: 700; color: #1E293B; margin-bottom: 1rem; padding-bottom: 0.5rem; border-bottom: 1px solid var(--cw-border);">
                        Book Specifications & Campus Details
                    </h3>
                    <div style="display: grid; grid-template-columns: 1fr 1fr; gap: 1rem; font-size: 0.9rem;">
                        <div>
                            <span style="color: #64748B; display: block; font-size: 0.78rem; text-transform: uppercase; font-weight: 700;">Subject</span>
                            <strong style="color: #1E293B;">${escapeHtml(book.subject || 'Academic Literature')}</strong>
                        </div>
                        <div>
                            <span style="color: #64748B; display: block; font-size: 0.78rem; text-transform: uppercase; font-weight: 700;">Condition Grade</span>
                            <span class="badge-condition">${escapeHtml(book.conditionGrade || 'Like New')}</span>
                        </div>
                        <div>
                            <span style="color: #64748B; display: block; font-size: 0.78rem; text-transform: uppercase; font-weight: 700;">Edition</span>
                            <strong style="color: #1E293B;">${escapeHtml(book.edition || 'Current Standard')}</strong>
                        </div>
                        <div>
                            <span style="color: #64748B; display: block; font-size: 0.78rem; text-transform: uppercase; font-weight: 700;">Course Code</span>
                            <strong style="color: #1E293B;">${escapeHtml(book.courseCode || 'All Departments')}</strong>
                        </div>
                        <div>
                            <span style="color: #64748B; display: block; font-size: 0.78rem; text-transform: uppercase; font-weight: 700;">ISBN</span>
                            <strong style="color: #1E293B;">${escapeHtml(book.isbn || '978-0-7475-3274-3')}</strong>
                        </div>
                        <div>
                            <span style="color: #64748B; display: block; font-size: 0.78rem; text-transform: uppercase; font-weight: 700;">Sharing Mode</span>
                            <span class="mode-badge ${getModeBadgeClass(book.sharingMode)}">${escapeHtml(book.sharingMode || 'SWAP')}</span>
                        </div>
                        <div>
                            <span style="color: #64748B; display: block; font-size: 0.78rem; text-transform: uppercase; font-weight: 700;">Listed By</span>
                            <strong style="color: #1E293B;">${escapeHtml(book.ownerName || 'Verified Peer Student')}</strong>
                        </div>
                        <div>
                            <span style="color: #64748B; display: block; font-size: 0.78rem; text-transform: uppercase; font-weight: 700;">Campus Handover Station</span>
                            <strong style="color: #1E293B;">${escapeHtml(book.ownerCampus || 'Central Library Block B')}</strong>
                        </div>
                    </div>
                </div>

                <!-- Description & Overview -->
                <div class="cw-detail-desc" style="margin-top: 2rem;">
                    <h3 style="font-family: var(--font-sans); font-size: 1.15rem; font-weight: 700; color: #1E293B; margin-bottom: 0.75rem;">
                        Description & Synopsis
                    </h3>
                    <p style="font-size: 0.95rem; color: #334155; line-height: 1.7;">
                        ${escapeHtml(book.description || 'No additional synopsis provided.')}
                    </p>
                </div>

            </div>
        </div>
    `;
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
