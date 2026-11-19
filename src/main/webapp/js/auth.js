/**
 * ============================================================================
 * auth.js - Client-Side Authentication & Profile Management Controller
 * ============================================================================
 * University Academic Project: Student Peer-to-Peer Book Exchange System (Book4u)
 * Technology: Vanilla JavaScript (ES6+ Fetch API, DOM API)
 * ============================================================================
 */

// Helper to determine the application context path dynamically
function getContextPath() {
    const path = window.location.pathname;
    const segments = path.split('/');
    if (segments.length > 1 && segments[1] !== '' && !segments[1].endsWith('.html') && segments[1] !== 'api') {
        return '/' + segments[1];
    }
    return '';
}

const CONTEXT_PATH = getContextPath();

/**
 * Toggles visibility of a password input field.
 */
function togglePasswordVisibility(inputId, button) {
    const input = document.getElementById(inputId);
    if (!input) return;

    if (input.type === 'password') {
        input.type = 'text';
        button.innerText = '🙈';
    } else {
        input.type = 'password';
        button.innerText = '👁️';
    }
}

/**
 * Displays an alert message box.
 * @param {string} elementId DOM element ID of the alert container
 * @param {string} message Text message to display
 * @param {'danger' | 'success' | 'info'} type Alert severity
 */
function showAlert(elementId, message, type = 'danger') {
    const el = document.getElementById(elementId);
    if (!el) return;

    el.className = `alert alert-${type}`;
    el.innerHTML = message;
    el.style.display = 'flex';
    el.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
}

function hideAlert(elementId) {
    const el = document.getElementById(elementId);
    if (el) el.style.display = 'none';
}

/**
 * Computes initials for the circular student avatar badge.
 */
function getInitials(name) {
    if (!name) return '--';
    const parts = name.trim().split(/\s+/);
    if (parts.length === 1) {
        return parts[0].substring(0, 2).toUpperCase();
    }
    return (parts[0][0] + parts[parts.length - 1][0]).toUpperCase();
}

/**
 * Formats a SQL timestamp string to a friendly campus date format.
 */
function formatDate(timestampStr) {
    if (!timestampStr) return '--';
    try {
        const date = new Date(timestampStr);
        return date.toLocaleDateString('en-US', {
            year: 'numeric',
            month: 'short',
            day: 'numeric'
        });
    } catch (e) {
        return timestampStr;
    }
}

// ============================================================================
// REGISTRATION CONTROLLER
// ============================================================================
function initRegisterPage() {
    const form = document.getElementById('register-form');
    if (!form) return;

    form.addEventListener('submit', async function (e) {
        e.preventDefault();
        hideAlert('register-alert');

        const fullName = document.getElementById('reg-fullname').value.trim();
        const universityId = document.getElementById('reg-univ-id').value.trim();
        const email = document.getElementById('reg-email').value.trim();
        const phone = document.getElementById('reg-phone').value.trim();
        const campusName = document.getElementById('reg-campus').value;
        const department = document.getElementById('reg-dept').value;
        const semester = parseInt(document.getElementById('reg-semester').value, 10);
        const password = document.getElementById('reg-password').value;
        const confirmPassword = document.getElementById('reg-confirm-password').value;

        // Client-side validations
        if (!fullName || !universityId || !email || !department || !password || !confirmPassword) {
            showAlert('register-alert', 'Please complete all required fields (*).', 'danger');
            return;
        }

        const emailRegex = /^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,6}$/;
        if (!emailRegex.test(email)) {
            showAlert('register-alert', 'Please enter a valid college email address format.', 'danger');
            return;
        }

        if (password.length < 6) {
            showAlert('register-alert', 'Password must be at least 6 characters long.', 'danger');
            return;
        }

        if (password !== confirmPassword) {
            showAlert('register-alert', 'Passwords do not match. Please verify and re-type.', 'danger');
            return;
        }

        const btn = document.getElementById('btn-submit-register');
        btn.disabled = true;
        btn.innerText = 'Creating Account...';

        try {
            const response = await fetch(`${CONTEXT_PATH}/api/auth/register`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({
                    fullName,
                    universityId,
                    email,
                    phone,
                    campusName,
                    department,
                    semester,
                    password,
                    confirmPassword
                })
            });

            const data = await response.json();

            if (response.ok && data.success) {
                showAlert('register-alert', '✅ Registration successful! Redirecting to login...', 'success');
                setTimeout(() => {
                    window.location.href = `${CONTEXT_PATH}/login.html?registered=true`;
                }, 1200);
            } else {
                showAlert('register-alert', `⚠️ ${data.error || 'Registration failed. Please check inputs.'}`, 'danger');
                btn.disabled = false;
                btn.innerText = 'Create Student Account';
            }
        } catch (err) {
            console.error('Registration error:', err);
            showAlert('register-alert', 'Network or server communication error. Please try again later.', 'danger');
            btn.disabled = false;
            btn.innerText = 'Create Student Account';
        }
    });
}

// ============================================================================
// LOGIN CONTROLLER
// ============================================================================
function initLoginPage() {
    const form = document.getElementById('login-form');
    if (!form) return;

    // Check URL parameters for status banners
    const params = new URLSearchParams(window.location.search);
    if (params.get('authRequired') === 'true') {
        showAlert('login-alert', '🔒 Please sign in with your student account to access that page.', 'info');
    } else if (params.get('registered') === 'true') {
        showAlert('login-alert', '🎉 Registration successful! Please sign in with your credentials.', 'success');
    } else if (params.get('loggedOut') === 'true') {
        showAlert('login-alert', '👋 You have signed out successfully.', 'info');
    }

    form.addEventListener('submit', async function (e) {
        e.preventDefault();
        hideAlert('login-alert');

        const email = document.getElementById('login-email').value.trim();
        const password = document.getElementById('login-password').value;

        if (!email || !password) {
            showAlert('login-alert', 'Please enter both your college email and password.', 'danger');
            return;
        }

        const btn = document.getElementById('btn-submit-login');
        btn.disabled = true;
        btn.innerText = 'Verifying Credentials...';

        try {
            const response = await fetch(`${CONTEXT_PATH}/api/auth/login`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ email, password })
            });

            const data = await response.json();

            if (response.ok && data.success) {
                showAlert('login-alert', '✅ Login successful! Redirecting...', 'success');
                setTimeout(() => {
                    window.location.href = `${CONTEXT_PATH}/profile.html`;
                }, 800);
            } else {
                showAlert('login-alert', `⚠️ ${data.error || 'Invalid credentials.'}`, 'danger');
                btn.disabled = false;
                btn.innerText = 'Sign In to Book4u';
            }
        } catch (err) {
            console.error('Login error:', err);
            showAlert('login-alert', 'Network connection failed. Ensure backend server is running.', 'danger');
            btn.disabled = false;
            btn.innerText = 'Sign In to Book4u';
        }
    });
}

// ============================================================================
// PROFILE & SETTINGS CONTROLLER
// ============================================================================
async function loadStudentProfile() {
    const profileForm = document.getElementById('profile-form');
    if (!profileForm) return;

    try {
        const response = await fetch(`${CONTEXT_PATH}/api/profile`, {
            method: 'GET',
            headers: { 'Accept': 'application/json' }
        });

        if (response.status === 401) {
            // Unauthenticated: redirect to login
            window.location.href = `${CONTEXT_PATH}/login.html?authRequired=true`;
            return;
        }

        const data = await response.json();
        if (response.ok && data.success && data.student) {
            populateProfileUI(data.student);
        } else {
            showAlert('profile-alert', data.error || 'Failed to load profile record.', 'danger');
        }
    } catch (err) {
        console.error('Error fetching profile:', err);
        showAlert('profile-alert', 'Server connection failure while retrieving profile.', 'danger');
    }
}

function populateProfileUI(student) {
    // Populate Sidebar Identity Card
    const initials = getInitials(student.fullName);
    const avatarEl = document.getElementById('avatar-initials');
    if (avatarEl) avatarEl.innerText = initials;

    const nameEl = document.getElementById('card-fullname');
    if (nameEl) nameEl.innerText = student.fullName || '--';

    const emailEl = document.getElementById('card-email');
    if (emailEl) emailEl.innerText = student.email || '--';

    const univIdEl = document.getElementById('card-univ-id');
    if (univIdEl) univIdEl.innerText = `Roll No: ${student.universityId || '--'}`;

    const deptEl = document.getElementById('card-dept');
    if (deptEl) deptEl.innerText = student.department || '--';

    const semEl = document.getElementById('card-semester');
    if (semEl) semEl.innerText = `Semester ${student.semester || 1}`;

    const campusEl = document.getElementById('card-campus');
    if (campusEl) campusEl.innerText = student.campusName || 'Main Campus';

    const phoneEl = document.getElementById('card-phone');
    if (phoneEl) phoneEl.innerText = student.phone || 'Not specified';

    const dateEl = document.getElementById('card-created-at');
    if (dateEl) dateEl.innerText = formatDate(student.createdAt);

    // Populate Edit Profile Form
    const inputUniv = document.getElementById('prof-univ-id');
    if (inputUniv) inputUniv.value = student.universityId || '';

    const inputEmail = document.getElementById('prof-email');
    if (inputEmail) inputEmail.value = student.email || '';

    const inputName = document.getElementById('prof-fullname');
    if (inputName) inputName.value = student.fullName || '';

    const inputPhone = document.getElementById('prof-phone');
    if (inputPhone) inputPhone.value = student.phone || '';

    const inputDept = document.getElementById('prof-dept');
    if (inputDept) inputDept.value = student.department || 'Computer Science';

    const inputSem = document.getElementById('prof-semester');
    if (inputSem) inputSem.value = student.semester || 1;

    const inputCampus = document.getElementById('prof-campus');
    if (inputCampus) inputCampus.value = student.campusName || 'Main Campus';
}

function initProfilePage() {
    loadStudentProfile();

    // 1. Profile Details Update Form
    const profileForm = document.getElementById('profile-form');
    if (profileForm) {
        profileForm.addEventListener('submit', async function (e) {
            e.preventDefault();
            hideAlert('profile-alert');

            const fullName = document.getElementById('prof-fullname').value.trim();
            const phone = document.getElementById('prof-phone').value.trim();
            const department = document.getElementById('prof-dept').value;
            const semester = parseInt(document.getElementById('prof-semester').value, 10);
            const campusName = document.getElementById('prof-campus').value;

            if (!fullName) {
                showAlert('profile-alert', 'Full name is required.', 'danger');
                return;
            }

            const btn = document.getElementById('btn-save-profile');
            btn.disabled = true;
            btn.innerText = 'Saving Changes...';

            try {
                const response = await fetch(`${CONTEXT_PATH}/api/profile/update`, {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ fullName, phone, department, semester, campusName })
                });

                const data = await response.json();
                if (response.ok && data.success) {
                    showAlert('profile-alert', '✅ Profile details updated successfully!', 'success');
                    populateProfileUI(data.student);
                } else {
                    showAlert('profile-alert', `⚠️ ${data.error || 'Failed to update profile.'}`, 'danger');
                }
            } catch (err) {
                showAlert('profile-alert', 'Server error while updating profile.', 'danger');
            } finally {
                btn.disabled = false;
                btn.innerText = 'Save Profile Changes';
            }
        });
    }

    // 2. Change Password Form
    const pwdForm = document.getElementById('password-form');
    if (pwdForm) {
        pwdForm.addEventListener('submit', async function (e) {
            e.preventDefault();
            hideAlert('password-alert');

            const currentPassword = document.getElementById('pwd-current').value;
            const newPassword = document.getElementById('pwd-new').value;
            const confirmPassword = document.getElementById('pwd-confirm').value;

            if (!currentPassword || !newPassword || !confirmPassword) {
                showAlert('password-alert', 'Please complete all password fields.', 'danger');
                return;
            }

            if (newPassword.length < 6) {
                showAlert('password-alert', 'New password must be at least 6 characters long.', 'danger');
                return;
            }

            if (newPassword !== confirmPassword) {
                showAlert('password-alert', 'New password and confirm password do not match.', 'danger');
                return;
            }

            const btn = document.getElementById('btn-change-password');
            btn.disabled = true;
            btn.innerText = 'Updating Password...';

            try {
                const response = await fetch(`${CONTEXT_PATH}/api/profile/password`, {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ currentPassword, newPassword, confirmPassword })
                });

                const data = await response.json();
                if (response.ok && data.success) {
                    showAlert('password-alert', '✅ Password changed successfully!', 'success');
                    pwdForm.reset();
                } else {
                    showAlert('password-alert', `⚠️ ${data.error || 'Failed to change password.'}`, 'danger');
                }
            } catch (err) {
                showAlert('password-alert', 'Server error while changing password.', 'danger');
            } finally {
                btn.disabled = false;
                btn.innerText = 'Update Password';
            }
        });
    }
}

// ============================================================================
// LOGOUT CONTROLLER
// ============================================================================
async function logoutStudent() {
    try {
        await fetch(`${CONTEXT_PATH}/api/auth/logout`, {
            method: 'POST'
        });
    } catch (e) {
        console.warn('Logout request completed with warning:', e);
    } finally {
        window.location.href = `${CONTEXT_PATH}/login.html?loggedOut=true`;
    }
}

// ============================================================================
// SHARED NAVBAR AUTH STATUS CONTROLLER
// ============================================================================
async function updateNavbarAuth() {
    const navActions = document.querySelector('.nav-actions');
    if (!navActions) return;

    try {
        const response = await fetch(`${CONTEXT_PATH}/api/auth/status`);
        if (!response.ok) return;

        const data = await response.json();
        if (data.authenticated && data.user) {
            const user = data.user;
            const firstName = user.fullName.split(' ')[0];

            // Render authenticated user menu
            const existingAuthGroup = document.getElementById('navbar-auth-group');
            if (existingAuthGroup) {
                existingAuthGroup.remove();
            }

            const authGroup = document.createElement('div');
            authGroup.id = 'navbar-auth-group';
            authGroup.className = 'nav-auth-group';
            authGroup.innerHTML = `
                <a href="${CONTEXT_PATH}/profile.html" class="nav-user-pill" title="View Profile">
                    <span>👤</span>
                    <span>${firstName}</span>
                </a>
                <button type="button" class="btn btn-outline btn-sm" onclick="logoutStudent()">Sign Out</button>
            `;

            // Insert before modal or at end
            const listBtn = document.getElementById('btn-open-list-modal');
            if (listBtn) {
                navActions.insertBefore(authGroup, listBtn);
            } else {
                navActions.appendChild(authGroup);
            }

            // Remove any guest login/register buttons if present
            const guestButtons = navActions.querySelectorAll('.guest-auth-btn');
            guestButtons.forEach(btn => btn.remove());
        }
    } catch (e) {
        // Ignored for offline or startup phase
    }
}

// Initialize on DOM ready
document.addEventListener('DOMContentLoaded', function () {
    initRegisterPage();
    initLoginPage();
    initProfilePage();
    updateNavbarAuth();
});
