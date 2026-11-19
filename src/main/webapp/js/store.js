/**
 * ============================================================================
 * store.js - Client-Side Bookstore Commerce & Wishlist Manager
 * ============================================================================
 * Inspired by Crossword.in: Manages Cart (Shopping Bag), Wishlist, Recently Viewed,
 * and Header Badge Synchronization with local persistence (localStorage).
 * ============================================================================
 */

const Store = (function () {
    const CART_KEY = 'book4u_cart';
    const WISHLIST_KEY = 'book4u_wishlist';
    const RECENT_KEY = 'book4u_recently_viewed';

    // Retrieve parsed array from localStorage
    function getItem(key) {
        try {
            const data = localStorage.getItem(key);
            return data ? JSON.parse(data) : [];
        } catch (e) {
            console.error('Error reading localStorage key: ' + key, e);
            return [];
        }
    }

    // Save array to localStorage
    function setItem(key, val) {
        try {
            localStorage.setItem(key, JSON.stringify(val));
        } catch (e) {
            console.error('Error saving to localStorage key: ' + key, e);
        }
    }

    // Update badge counters across the page
    function updateBadges() {
        const cartItems = getItem(CART_KEY);
        const totalCount = cartItems.reduce((sum, item) => sum + (item.quantity || 1), 0);
        const wishlistItems = getItem(WISHLIST_KEY);

        const cartBadges = document.querySelectorAll('.cart-count-badge');
        cartBadges.forEach(badge => {
            badge.textContent = totalCount;
            badge.style.display = totalCount > 0 ? 'inline-flex' : 'none';
        });

        const wishlistBadges = document.querySelectorAll('.wishlist-count-badge');
        wishlistBadges.forEach(badge => {
            badge.textContent = wishlistItems.length;
            badge.style.display = wishlistItems.length > 0 ? 'inline-flex' : 'none';
        });
    }

    // Floating Toast Notification (Crossword Bookstore Style)
    function showToast(message, type = 'success') {
        let toastContainer = document.getElementById('cw-toast-container');
        if (!toastContainer) {
            toastContainer = document.createElement('div');
            toastContainer.id = 'cw-toast-container';
            toastContainer.className = 'cw-toast-container';
            document.body.appendChild(toastContainer);
        }

        const toast = document.createElement('div');
        toast.className = `cw-toast cw-toast-${type}`;
        const icon = type === 'success' ? '🛍️' : (type === 'wishlist' ? '❤️' : 'ℹ️');
        toast.innerHTML = `
            <span class="cw-toast-icon">${icon}</span>
            <div class="cw-toast-content">${message}</div>
            <button class="cw-toast-close" aria-label="Close">&times;</button>
        `;

        toast.querySelector('.cw-toast-close').addEventListener('click', () => {
            toast.remove();
        });

        toastContainer.appendChild(toast);

        setTimeout(() => {
            toast.classList.add('cw-toast-show');
        }, 10);

        setTimeout(() => {
            toast.classList.remove('cw-toast-show');
            setTimeout(() => toast.remove(), 300);
        }, 3500);
    }

    return {
        // --- CART / BAG METHODS ---
        getCart() {
            return getItem(CART_KEY);
        },
        addToCart(book) {
            if (!book || !book.id) return;
            const items = getItem(CART_KEY);
            const existing = items.find(item => String(item.id) === String(book.id));
            if (existing) {
                existing.quantity = (existing.quantity || 1) + 1;
            } else {
                items.push({
                    id: book.id,
                    title: book.title || 'Academic Book',
                    author: book.author || 'Author',
                    coverImage: book.coverImage || 'https://images.unsplash.com/photo-1544716278-ca5e3f4abd8c?w=400&q=80',
                    price: book.price !== undefined ? book.price : 450,
                    originalPrice: book.originalPrice || 599,
                    sharingMode: book.sharingMode || 'SWAP',
                    subject: book.subject || 'General',
                    quantity: 1
                });
            }
            setItem(CART_KEY, items);
            updateBadges();
            showToast(`<strong>${escapeHtml(book.title)}</strong> added to your shopping bag!`, 'success');
        },
        updateQuantity(bookId, delta) {
            let items = getItem(CART_KEY);
            const item = items.find(i => String(i.id) === String(bookId));
            if (item) {
                item.quantity = (item.quantity || 1) + delta;
                if (item.quantity <= 0) {
                    items = items.filter(i => String(i.id) !== String(bookId));
                }
                setItem(CART_KEY, items);
                updateBadges();
            }
            return items;
        },
        removeFromCart(bookId) {
            let items = getItem(CART_KEY);
            items = items.filter(i => String(i.id) !== String(bookId));
            setItem(CART_KEY, items);
            updateBadges();
            return items;
        },
        clearCart() {
            setItem(CART_KEY, []);
            updateBadges();
        },
        getCartTotal() {
            const items = getItem(CART_KEY);
            return items.reduce((total, item) => total + ((item.price || 0) * (item.quantity || 1)), 0);
        },

        // --- WISHLIST METHODS ---
        getWishlist() {
            return getItem(WISHLIST_KEY);
        },
        isInWishlist(bookId) {
            const items = getItem(WISHLIST_KEY);
            return items.some(item => String(item.id) === String(bookId));
        },
        toggleWishlist(book, buttonElement) {
            if (!book || !book.id) return false;
            let items = getItem(WISHLIST_KEY);
            const exists = items.some(item => String(item.id) === String(book.id));
            if (exists) {
                items = items.filter(item => String(item.id) !== String(book.id));
                setItem(WISHLIST_KEY, items);
                updateBadges();
                if (buttonElement) buttonElement.classList.remove('active');
                showToast(`Removed <strong>${escapeHtml(book.title)}</strong> from your wishlist.`, 'info');
                return false;
            } else {
                items.push({
                    id: book.id,
                    title: book.title || 'Academic Book',
                    author: book.author || 'Author',
                    coverImage: book.coverImage || 'https://images.unsplash.com/photo-1544716278-ca5e3f4abd8c?w=400&q=80',
                    price: book.price !== undefined ? book.price : 450,
                    originalPrice: book.originalPrice || 599,
                    sharingMode: book.sharingMode || 'SWAP',
                    subject: book.subject || 'General'
                });
                setItem(WISHLIST_KEY, items);
                updateBadges();
                if (buttonElement) buttonElement.classList.add('active');
                showToast(`Added <strong>${escapeHtml(book.title)}</strong> to your wishlist!`, 'wishlist');
                return true;
            }
        },
        removeFromWishlist(bookId) {
            let items = getItem(WISHLIST_KEY);
            items = items.filter(item => String(item.id) !== String(bookId));
            setItem(WISHLIST_KEY, items);
            updateBadges();
            return items;
        },

        // --- RECENTLY VIEWED ---
        recordRecentlyViewed(book) {
            if (!book || !book.id) return;
            let items = getItem(RECENT_KEY);
            items = items.filter(item => String(item.id) !== String(book.id));
            items.unshift({
                id: book.id,
                title: book.title,
                author: book.author,
                coverImage: book.coverImage || 'https://images.unsplash.com/photo-1544716278-ca5e3f4abd8c?w=400&q=80',
                price: book.price || 450,
                originalPrice: book.originalPrice || 599,
                sharingMode: book.sharingMode || 'SWAP',
                subject: book.subject || 'General'
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
