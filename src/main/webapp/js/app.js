/**
 * ============================================================================
 * Book4u - Frontend Client Application & Global Controller
 * Technology: Vanilla JavaScript (ES6+), Fetch API, DOM Manipulation
 * Integrates directly with Java Servlets (/api/health, /api/books)
 * ============================================================================
 */

function getContextPath() {
    const path = window.location.pathname;
    const segments = path.split('/');
    if (segments.length > 1 && segments[1] !== '' && !segments[1].endsWith('.html') && segments[1] !== 'api') {
        return '/' + segments[1];
    }
    return '';
}

const CONTEXT_PATH = getContextPath();

// Wishlist Management via localStorage
const WishlistManager = {
    STORAGE_KEY: 'book4u_wishlist',

    getAll() {
        try {
            return JSON.parse(localStorage.getItem(this.STORAGE_KEY)) || [];
        } catch (e) {
            return [];
        }
    },

    has(bookId) {
        const list = this.getAll();
        return list.some(item => String(item.id) === String(bookId));
    },

    toggle(book) {
        let list = this.getAll();
        const index = list.findIndex(item => String(item.id) === String(book.id));
        let added = false;

        if (index > -1) {
            list.splice(index, 1);
        } else {
            list.push({
                id: book.id,
                title: book.title,
                author: book.author,
                subject: book.subject,
                sharingMode: book.sharingMode,
                imageUrl: book.imageUrl
            });
            added = true;
        }

        localStorage.setItem(this.STORAGE_KEY, JSON.stringify(list));
        this.updateBadge();
        return added;
    },

    updateBadge() {
        const count = this.getAll().length;
        const badges = document.querySelectorAll('.wishlist-counter-badge');
        badges.forEach(b => {
            b.innerText = count;
            b.style.display = count > 0 ? 'flex' : 'none';
        });
    }
};

document.addEventListener('DOMContentLoaded', () => {
    checkSystemHealth();
    WishlistManager.updateBadge();
    initGlobalSearch();
    initWishlistModal();

    // If on homepage, load the multiple shelf sections
    if (document.getElementById('recent-books-row') || document.getElementById('popular-books-row') || document.getElementById('book-grid')) {
        loadHomepageSections();
    }
});

/**
 * Pings the Java Servlet health check endpoint: GET /api/health/live and /api/health/ready
 */
async function checkSystemHealth() {
    const healthDot = document.getElementById('health-dot');
    const healthText = document.getElementById('health-text');
    if (!healthDot || !healthText) return;

    try {
        const response = await fetch(`${CONTEXT_PATH}/api/health/ready`);
        if (response.ok) {
            const data = await response.json();
            if (data.databaseStatus === 'CONNECTED') {
                healthDot.className = 'health-dot dot-green';
                healthText.textContent = 'Tomcat & MySQL: Online';
            } else {
                healthDot.className = 'health-dot dot-amber';
                healthText.textContent = 'Server: Up | DB: Standby';
            }
        } else {
            healthDot.className = 'health-dot dot-amber';
            healthText.textContent = 'Server: HTTP ' + response.status;
        }
    } catch (err) {
        healthDot.className = 'health-dot dot-amber';
        healthText.textContent = 'Connecting to Server...';
    }
}

/**
 * Global Search in Navigation Bar
 */
function initGlobalSearch() {
    const searchForm = document.getElementById('nav-search-form');
    if (!searchForm) return;

    searchForm.addEventListener('submit', (e) => {
        e.preventDefault();
        const input = document.getElementById('nav-search-input');
        if (input && input.value.trim()) {
            window.location.href = `${CONTEXT_PATH}/explore.html?query=${encodeURIComponent(input.value.trim())}`;
        }
    });
}

/**
 * Loads and partitions books across homepage bookstore shelves
 */
async function loadHomepageSections() {
    const recentRow = document.getElementById('recent-books-row');
    const popularRow = document.getElementById('popular-books-row');
    const defaultGrid = document.getElementById('book-grid');

    try {
        const response = await fetch(`${CONTEXT_PATH}/api/books`);
        if (!response.ok) throw new Error('HTTP ' + response.status);

        const data = await response.json();
        const books = Array.isArray(data) ? data : (data.books || []);

        if (recentRow) {
            renderBookShelf(recentRow, books.slice(0, 5));
        }
        if (popularRow) {
            // Pick available SWAP and LENDING books for popular shelf
            const popularBooks = books.filter(b => b.sharingMode === 'SWAP' || b.conditionGrade === 'LIKE_NEW');
            renderBookShelf(popularRow, (popularBooks.length >= 3 ? popularBooks : books).slice(0, 5));
        }
        if (defaultGrid && !recentRow) {
            renderBookShelf(defaultGrid, books);
        }

    } catch (err) {
        console.error('Failed to load homepage book sections:', err);
        const errorHtml = `
            <div class="empty-state-box">
                <span class="empty-state-icon">⚠️</span>
                <h3>Welcome to Book4u</h3>
                <p>Academic book exchange system is active. Start exploring catalog listings or sign in to list textbooks.</p>
                <a href="${CONTEXT_PATH}/explore.html" class="btn btn-primary">Explore All Books</a>
            </div>
        `;
        if (recentRow) recentRow.innerHTML = errorHtml;
        if (popularRow) popularRow.innerHTML = '';
        if (defaultGrid) defaultGrid.innerHTML = errorHtml;
    }
}

/**
 * Generates commercial bookstore product cards (Screenshot 1 Layout)
 */
function renderBookShelf(container, books) {
    if (!container) return;

    if (!books || books.length === 0) {
        container.innerHTML = `
            <div class="empty-state-box">
                <span class="empty-state-icon">📚</span>
                <h3>No Books Currently Listed</h3>
                <p>Be the first student to list a textbook for exchange this semester!</p>
                <a href="${CONTEXT_PATH}/add-book.html" class="btn btn-primary">+ List a Book</a>
            </div>
        `;
        return;
    }

    container.innerHTML = '';
    books.forEach(book => {
        const card = document.createElement('div');
        card.className = 'book-card';

        const isWishlisted = WishlistManager.has(book.id);
        const wishlistClass = isWishlisted ? 'card-wishlist-btn active' : 'card-wishlist-btn';
        const wishlistHeart = isWishlisted ? '♥' : '♡';

        // Aspect ratio 2:3 Cover presentation with fallback
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

        // Pricing line matching reference screenshot (Strikethrough estimate + Mode/Free + Savings pill)
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

        let badgeModeClass = 'badge-swap';
        if (book.sharingMode === 'GIVEAWAY') badgeModeClass = 'badge-giveaway';
        if (book.sharingMode === 'LENDING') badgeModeClass = 'badge-lending';

        card.innerHTML = `
            <button type="button" class="${wishlistClass}" title="Save to Wishlist" 
                    onclick="handleWishlistClick(event, ${JSON.stringify(book).replace(/"/g, '&quot;')})">
                ${wishlistHeart}
            </button>

            ${coverHtml}

            <div class="book-card-body">
                <span class="mode-badge ${badgeModeClass}">${escapeHtml(book.sharingMode)}</span>
                <a href="${CONTEXT_PATH}/book-details.html?id=${book.id}" class="book-title" title="${escapeHtml(book.title)}">
                    ${escapeHtml(book.title)}
                </a>
                <p class="book-author">By ${escapeHtml(book.author || 'Author not specified')}</p>

                <div class="book-pricing-line">
                    <span class="price-strikethrough">${originalEstimate}</span>
                    <span class="price-active">${priceTag}</span>
                    <span class="price-discount-badge">${discountPill}</span>
                </div>

                <a href="${CONTEXT_PATH}/book-details.html?id=${book.id}" class="card-action-btn">
                    <span>View Details</span> <span>↗</span>
                </a>
            </div>
        `;

        container.appendChild(card);
    });
}

function handleWishlistClick(event, book) {
    event.preventDefault();
    event.stopPropagation();
    const btn = event.currentTarget;
    const added = WishlistManager.toggle(book);

    if (added) {
        btn.classList.add('active');
        btn.innerText = '♥';
    } else {
        btn.classList.remove('active');
        btn.innerText = '♡';
    }
}

/**
 * Wishlist Modal Drawer Management
 */
function initWishlistModal() {
    const openBtn = document.getElementById('nav-wishlist-btn');
    const modal = document.getElementById('wishlist-modal');
    const closeBtn = document.getElementById('wishlist-close-btn');

    if (openBtn && modal) {
        openBtn.addEventListener('click', (e) => {
            e.preventDefault();
            renderWishlistModalItems();
            modal.style.display = 'flex';
        });
    }

    if (closeBtn && modal) {
        closeBtn.addEventListener('click', () => {
            modal.style.display = 'none';
        });
    }

    if (modal) {
        modal.addEventListener('click', (e) => {
            if (e.target === modal) {
                modal.style.display = 'none';
            }
        });
    }
}

function renderWishlistModalItems() {
    const listContainer = document.getElementById('wishlist-items-container');
    if (!listContainer) return;

    const items = WishlistManager.getAll();
    if (items.length === 0) {
        listContainer.innerHTML = `
            <div style="text-align: center; padding: 2rem; color: var(--text-muted);">
                <p style="font-size: 2rem; margin-bottom: 0.5rem;">♡</p>
                <p>Your wishlist is empty.</p>
                <p style="font-size: 0.85rem; margin-top: 0.25rem;">Click the heart icon on any book card to save it here.</p>
            </div>
        `;
        return;
    }

    listContainer.innerHTML = '';
    items.forEach(item => {
        const row = document.createElement('div');
        row.className = 'wishlist-item';
        row.innerHTML = `
            <div class="wishlist-item-info">
                <h4><a href="${CONTEXT_PATH}/book-details.html?id=${item.id}" style="color: var(--text-main); text-decoration: none;">${escapeHtml(item.title)}</a></h4>
                <p>By ${escapeHtml(item.author || '')} • <strong>${escapeHtml(item.sharingMode || 'SWAP')}</strong></p>
            </div>
            <div style="display: flex; gap: 0.5rem; align-items: center;">
                <a href="${CONTEXT_PATH}/book-details.html?id=${item.id}" class="btn btn-outline btn-sm">View</a>
                <button type="button" class="btn btn-sm" style="color: var(--danger-color); border: none; background: transparent; cursor: pointer;" 
                        onclick="removeWishlistItem(${item.id})">✕</button>
            </div>
        `;
        listContainer.appendChild(row);
    });
}

function removeWishlistItem(bookId) {
    WishlistManager.toggle({ id: bookId });
    renderWishlistModalItems();
    // Update all matching cards on page
    document.querySelectorAll('.card-wishlist-btn').forEach(btn => {
        btn.classList.remove('active');
        btn.innerText = '♡';
    });
}

function escapeHtml(text) {
    if (!text) return '';
    const div = document.createElement('div');
    div.textContent = text;
    return div.innerHTML;
}
