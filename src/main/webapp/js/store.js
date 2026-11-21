/**
 * ============================================================================
 * store.js - Client-Side Saved Books & Reading List Manager
 * ============================================================================
 * University Academic Project: Student Peer-to-Peer Book Exchange System (Book4u)
 * Technology: Vanilla JavaScript (ES6+), localStorage persistence
 * 
 * Manages Saved Books (reading wishlist), Recently Viewed books, and badge
 * synchronization across campus navigation without any commercial e-commerce logic.
 * ============================================================================
 */

const Store = (function () {
    const SAVED_KEY = 'book4u_saved_books';
    const LEGACY_WISHLIST_KEY = 'book4u_wishlist';
    const RECENT_KEY = 'book4u_recently_viewed';

    // Retrieve parsed array from localStorage (with backwards compatibility)
    function getItem(key) {
        try {
            const data = localStorage.getItem(key);
            if (data) return JSON.parse(data);
            if (key === SAVED_KEY) {
                const legacy = localStorage.getItem(LEGACY_WISHLIST_KEY);
                return legacy ? JSON.parse(legacy) : [];
            }
            return [];
        } catch (e) {
            console.error('Error reading localStorage key: ' + key, e);
            return [];
        }
    }

    // Save array to localStorage
    function setItem(key, val) {
        try {
            localStorage.setItem(key, JSON.stringify(val));
            if (key === SAVED_KEY) {
                localStorage.setItem(LEGACY_WISHLIST_KEY, JSON.stringify(val));
            }
        } catch (e) {
            console.error('Error saving to localStorage key: ' + key, e);
        }
    }

    // Update badge counters across the page (Saved Books badge only)
    function updateBadges() {
        const savedItems = getItem(SAVED_KEY);
        const count = savedItems.length;

        const badges = document.querySelectorAll('.saved-count-badge, .wishlist-count-badge');
        badges.forEach(badge => {
            badge.textContent = count;
            badge.style.display = count > 0 ? 'inline-flex' : 'none';
        });
    }

    // Campus Toast Notification
    function showToast(message, type = 'info') {
        let toastContainer = document.getElementById('b4u-toast-container');
        if (!toastContainer) {
            toastContainer = document.createElement('div');
            toastContainer.id = 'b4u-toast-container';
            toastContainer.className = 'b4u-toast-container';
            document.body.appendChild(toastContainer);
        }

        const toast = document.createElement('div');
        toast.className = `b4u-toast b4u-toast-${type}`;
        const icon = type === 'saved' || type === 'wishlist' ? '🔖' : (type === 'success' ? '✅' : 'ℹ️');
        toast.innerHTML = `
            <span class="b4u-toast-icon">${icon}</span>
            <div class="b4u-toast-content">${message}</div>
            <button class="b4u-toast-close" aria-label="Close">&times;</button>
        `;

        toast.querySelector('.b4u-toast-close').addEventListener('click', () => {
            toast.remove();
        });

        toastContainer.appendChild(toast);

        setTimeout(() => {
            toast.classList.add('b4u-toast-show');
        }, 10);

        setTimeout(() => {
            toast.classList.remove('b4u-toast-show');
            setTimeout(() => toast.remove(), 300);
        }, 3200);
    }

    return {
        // --- SAVED BOOKS (READING WISHLIST) METHODS ---
        getSavedBooks() {
            return getItem(SAVED_KEY);
        },
        getWishlist() {
            return getItem(SAVED_KEY);
        },
        isSaved(bookId) {
            const items = getItem(SAVED_KEY);
            return items.some(item => String(item.id) === String(bookId));
        },
        isInWishlist(bookId) {
            return this.isSaved(bookId);
        },
        toggleSave(book, buttonElement) {
            if (!book || !book.id) return false;
            let items = getItem(SAVED_KEY);
            const exists = items.some(item => String(item.id) === String(book.id));

            if (exists) {
                items = items.filter(item => String(item.id) !== String(book.id));
                setItem(SAVED_KEY, items);
                updateBadges();
                if (buttonElement) {
                    buttonElement.classList.remove('active');
                    const svg = buttonElement.querySelector('svg');
                    if (svg) svg.setAttribute('fill', 'none');
                }
                showToast(`Removed <strong>${escapeHtml(book.title)}</strong> from your Saved Books.`, 'info');
                return false;
            } else {
                const resolvedImg = (typeof CoverResolver !== 'undefined') ? CoverResolver.resolve(book) : (book.coverImage || book.image || book.imageUrl || '');
                items.push({
                    id: book.id,
                    title: book.title || 'Academic Book',
                    author: book.author || 'Author',
                    coverImage: resolvedImg,
                    sharingMode: book.sharingMode || book.mode || 'SWAP',
                    subject: book.subject || 'Academic',
                    conditionGrade: book.conditionGrade || 'Good',
                    courseCode: book.courseCode || ''
                });
                setItem(SAVED_KEY, items);
                updateBadges();
                if (buttonElement) {
                    buttonElement.classList.add('active');
                    const svg = buttonElement.querySelector('svg');
                    if (svg) svg.setAttribute('fill', 'currentColor');
                }
                showToast(`Saved <strong>${escapeHtml(book.title)}</strong> to your Saved Books!`, 'saved');
                return true;
            }
        },
        toggleWishlist(book, buttonElement) {
            return this.toggleSave(book, buttonElement);
        },
        removeSaved(bookId) {
            let items = getItem(SAVED_KEY);
            items = items.filter(item => String(item.id) !== String(bookId));
            setItem(SAVED_KEY, items);
            updateBadges();
            return items;
        },
        removeFromWishlist(bookId) {
            return this.removeSaved(bookId);
        },

        // --- RECENTLY VIEWED (CAMPUS DISCOVERY HISTORY) ---
        recordRecentlyViewed(book) {
            if (!book || !book.id) return;
            let items = getItem(RECENT_KEY);
            items = items.filter(item => String(item.id) !== String(book.id));
            const resolvedImg = (typeof CoverResolver !== 'undefined') ? CoverResolver.resolve(book) : (book.coverImage || book.image || book.imageUrl || '');
            items.unshift({
                id: book.id,
                title: book.title,
                author: book.author,
                coverImage: resolvedImg,
                sharingMode: book.sharingMode || book.mode || 'SWAP',
                subject: book.subject || 'Academic',
                conditionGrade: book.conditionGrade || 'Good',
                courseCode: book.courseCode || ''
            });
            if (items.length > 8) items.pop();
            setItem(RECENT_KEY, items);
        },
        getRecentlyViewed() {
            return getItem(RECENT_KEY);
        },

        // --- INITIALIZER ---
        init() {
            updateBadges();
        },
        updateBadges,
        showToast
    };
})();

// Helper to escape HTML characters
function escapeHtml(text) {
    if (!text) return '';
    const div = document.createElement('div');
    div.textContent = text;
    return div.innerHTML;
}

// Auto-initialize badges when DOM loads
document.addEventListener('DOMContentLoaded', () => {
    Store.init();
});
