/**
 * ============================================================================
 * requests.js - Client Controller for Student Exchange Requests & Handovers
 * ============================================================================
 * University Academic Project: Book4u (Peer-to-Peer Book Exchange)
 * Technology: Vanilla JavaScript (Fetch API, DOM API)
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

function escapeHtml(text) {
    if (!text) return '';
    const div = document.createElement('div');
    div.textContent = text;
    return div.innerHTML;
}

function showRequestAlert(msg, type = 'info') {
    const alertEl = document.getElementById('requests-alert') || document.getElementById('exchange-alert');
    if (!alertEl) return;
    alertEl.className = `alert alert-${type}`;
    alertEl.innerHTML = msg;
    alertEl.style.display = 'flex';
    alertEl.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
}

function hideRequestAlert() {
    const alertEl = document.getElementById('requests-alert') || document.getElementById('exchange-alert');
    if (alertEl) alertEl.style.display = 'none';
}

function getStatusBadge(status) {
    const s = (status || 'PENDING').toUpperCase();
    if (s === 'PENDING') return '<span class="badge-status-pending">⏳ Pending Review</span>';
    if (s === 'ACCEPTED') return '<span class="badge-status-accepted">✓ Accepted</span>';
    if (s === 'REJECTED') return '<span class="badge-status-rejected">✕ Declined</span>';
    if (s === 'CANCELLED') return '<span class="badge-status-cancelled">⊘ Cancelled</span>';
    if (s === 'AWAITING_HANDOVER') return '<span class="badge-status-awaiting">📍 Awaiting Handover</span>';
    if (s === 'ACTIVE') return '<span class="badge-status-active">⏱️ Active Loan</span>';
    if (s === 'COMPLETED') return '<span class="badge-status-completed">✓ Handover Completed</span>';
    if (s === 'RETURNED') return '<span class="badge-status-returned">✓ Book Returned</span>';
    return `<span class="badge-status-awaiting">${escapeHtml(s)}</span>`;
}

function getModeBadge(mode) {
    const m = (mode || 'SWAP').toUpperCase();
    if (m === 'GIVEAWAY') return '<span class="b4u-mode-badge mode-giveaway">🎁 Giveaway</span>';
    if (m === 'LENDING') return '<span class="b4u-mode-badge mode-lending">⏱️ Lending</span>';
    return '<span class="b4u-mode-badge mode-swap">🔄 Swap</span>';
}

// ============================================================================
// 1. MY-REQUESTS PAGE CONTROLLER
// ============================================================================
let sentRequestsCache = [];
let receivedRequestsCache = [];
let exchangesCache = [];

async function loadRequestsDashboard() {
    const paneReceived = document.getElementById('tab-pane-received');
    const paneSent = document.getElementById('tab-pane-sent');
    const paneExchanges = document.getElementById('tab-pane-exchanges');
    if (!paneReceived && !paneSent && !paneExchanges) return;

    try {
        // Fetch sent requests, received requests, and active exchanges in parallel
        const [resSent, resReceived, resExchanges] = await Promise.all([
            fetch(`${APP_CONTEXT}/api/exchange-requests/sent`),
            fetch(`${APP_CONTEXT}/api/exchange-requests/received`),
            fetch(`${APP_CONTEXT}/api/exchanges`)
        ]);

        if (resSent.status === 401 || resReceived.status === 401) {
            window.location.href = `${APP_CONTEXT}/login.html?authRequired=true`;
            return;
        }

        const dataSent = resSent.ok ? await resSent.json() : { requests: [] };
        const dataReceived = resReceived.ok ? await resReceived.json() : { requests: [] };
        const dataExchanges = resExchanges.ok ? await resExchanges.json() : { exchanges: [] };

        sentRequestsCache = dataSent.requests || [];
        receivedRequestsCache = dataReceived.requests || [];
        exchangesCache = dataExchanges.exchanges || [];

        // Update Stat Counters
        const pendingCount = receivedRequestsCache.filter(r => r.status === 'PENDING').length;
        const activeExchangesCount = exchangesCache.filter(e => e.status === 'AWAITING_HANDOVER' || e.status === 'ACTIVE').length;

        if (document.getElementById('stat-sent-count')) document.getElementById('stat-sent-count').textContent = sentRequestsCache.length;
        if (document.getElementById('stat-received-count')) document.getElementById('stat-received-count').textContent = receivedRequestsCache.length;
        if (document.getElementById('stat-pending-count')) document.getElementById('stat-pending-count').textContent = pendingCount;
        if (document.getElementById('stat-exchanges-count')) document.getElementById('stat-exchanges-count').textContent = activeExchangesCount;

        if (document.getElementById('badge-received-count')) document.getElementById('badge-received-count').textContent = receivedRequestsCache.length;
        if (document.getElementById('badge-sent-count')) document.getElementById('badge-sent-count').textContent = sentRequestsCache.length;
        if (document.getElementById('badge-exchanges-count')) document.getElementById('badge-exchanges-count').textContent = exchangesCache.length;

        renderReceivedRequests();
        renderSentRequests();
        renderExchangesList();

    } catch (err) {
        console.error('Failed to load exchange requests:', err);
        showRequestAlert('Could not load your requests. Please ensure you are logged in.', 'danger');
    }
}

function switchRequestTab(tabName) {
    const tabs = ['received', 'sent', 'exchanges'];
    tabs.forEach(t => {
        const btn = document.getElementById(`tab-btn-${t}`);
        const pane = document.getElementById(`tab-pane-${t}`);
        if (btn) btn.classList.toggle('active', t === tabName);
        if (pane) pane.style.display = (t === tabName) ? 'flex' : 'none';
    });
}

function renderReceivedRequests() {
    const container = document.getElementById('tab-pane-received');
    if (!container) return;

    if (!receivedRequestsCache.length) {
        container.innerHTML = `
            <div style="text-align: center; padding: 3rem 1rem; background: #FFF; border-radius: 8px; border: 1px solid var(--color-border);">
                <div style="font-size: 2.5rem; margin-bottom: 0.75rem;">📥</div>
                <h3 style="font-size: 1.15rem; color: var(--color-navy); margin-bottom: 0.5rem;">No Incoming Requests Yet</h3>
                <p style="color: var(--color-slate); font-size: 0.9rem; max-width: 450px; margin: 0 auto 1.25rem;">
                    When campus peers submit an exchange request for your listed textbooks, they will appear here for your review and approval.
                </p>
                <a href="${APP_CONTEXT}/add-book.html" class="btn btn-primary">+ List Another Textbook</a>
            </div>
        `;
        return;
    }

    container.innerHTML = receivedRequestsCache.map(req => {
        const isPending = req.status === 'PENDING';
        const isAccepted = req.status === 'ACCEPTED';
        const coverImg = req.bookImageUrl || 'https://images.unsplash.com/photo-1544716278-ca5e3f4abd8c?w=300&q=80';

        let swapHtml = '';
        if (req.exchangeMode === 'SWAP' && req.offeredBookTitle) {
            swapHtml = `
                <div style="background: #F8FAFC; border: 1px dashed #CBD5E1; border-radius: 6px; padding: 0.5rem 0.75rem; margin-top: 0.5rem; font-size: 0.85rem;">
                    <strong>Offered Book in Return:</strong> ${escapeHtml(req.offeredBookTitle)} ${req.offeredBookAuthor ? `by <em>${escapeHtml(req.offeredBookAuthor)}</em>` : ''}
                </div>
            `;
        }

        let lendingHtml = '';
        if (req.exchangeMode === 'LENDING' && req.proposedDurationDays) {
            lendingHtml = `<span style="font-size: 0.85rem; color: var(--color-slate-dark);">⏱️ Proposed Loan: <strong>${req.proposedDurationDays} Days</strong></span>`;
        }

        return `
            <div class="request-card">
                <img src="${escapeHtml(coverImg)}" alt="${escapeHtml(req.bookTitle)}" class="request-card-thumb" onerror="this.src='https://images.unsplash.com/photo-1544716278-ca5e3f4abd8c?w=300&q=80';">
                
                <div class="request-card-body">
                    <div style="display: flex; gap: 0.5rem; align-items: center; flex-wrap: wrap;">
                        ${getModeBadge(req.exchangeMode)}
                        ${getStatusBadge(req.status)}
                    </div>

                    <a href="${APP_CONTEXT}/book-details.html?id=${req.bookId}" class="request-card-title">${escapeHtml(req.bookTitle)}</a>
                    <div class="request-card-author">By ${escapeHtml(req.bookAuthor || 'Author')}</div>

                    <div class="request-meta-row">
                        <span class="request-party-info">
                            👤 Requested by: <strong>${escapeHtml(req.requesterName || 'Student')}</strong> (${escapeHtml(req.requesterDepartment || 'Campus')})
                        </span>
                        ${req.handoverLocation ? `<span class="request-party-info">📍 Handover Spot: <strong>${escapeHtml(req.handoverLocation)}</strong></span>` : ''}
                        ${lendingHtml}
                    </div>

                    ${req.requestMessage ? `<div class="request-message-bubble">"${escapeHtml(req.requestMessage)}"</div>` : ''}
                    ${swapHtml}
                </div>

                <div class="request-card-actions">
                    ${isPending ? `
                        <button type="button" class="btn btn-primary" onclick="openAcceptModal(${req.id}, '${escapeHtml(req.handoverLocation || '')}')">
                            ✓ Accept Request
                        </button>
                        <button type="button" class="btn btn-outline" style="color: #DC2626; border-color: #FECACA;" onclick="handleRejectRequest(${req.id})">
                            ✕ Decline
                        </button>
                    ` : ''}
                    ${isAccepted && req.exchangeId ? `
                        <a href="${APP_CONTEXT}/exchange-details.html?id=${req.exchangeId}" class="btn btn-primary" style="background: var(--color-gold); border-color: var(--color-gold);">
                            🤝 Coordinate Handover &rarr;
                        </a>
                    ` : ''}
                    <a href="${APP_CONTEXT}/book-details.html?id=${req.bookId}" class="btn btn-outline" style="font-size: 0.8rem; padding: 0.4rem 0.6rem;">
                        View Listing
                    </a>
                </div>
            </div>
        `;
    }).join('');
}

function renderSentRequests() {
    const container = document.getElementById('tab-pane-sent');
    if (!container) return;

    if (!sentRequestsCache.length) {
        container.innerHTML = `
            <div style="text-align: center; padding: 3rem 1rem; background: #FFF; border-radius: 8px; border: 1px solid var(--color-border);">
                <div style="font-size: 2.5rem; margin-bottom: 0.75rem;">📤</div>
                <h3 style="font-size: 1.15rem; color: var(--color-navy); margin-bottom: 0.5rem;">No Sent Requests</h3>
                <p style="color: var(--color-slate); font-size: 0.9rem; max-width: 450px; margin: 0 auto 1.25rem;">
                    You have not submitted any book exchange requests. Browse available textbooks listed by peers across your campus.
                </p>
                <a href="${APP_CONTEXT}/explore.html" class="btn btn-primary">Explore Academic Catalog</a>
            </div>
        `;
        return;
    }

    container.innerHTML = sentRequestsCache.map(req => {
        const isPending = req.status === 'PENDING';
        const isAccepted = req.status === 'ACCEPTED';
        const coverImg = req.bookImageUrl || 'https://images.unsplash.com/photo-1544716278-ca5e3f4abd8c?w=300&q=80';

        let swapHtml = '';
        if (req.exchangeMode === 'SWAP' && req.offeredBookTitle) {
            swapHtml = `
                <div style="background: #F8FAFC; border: 1px dashed #CBD5E1; border-radius: 6px; padding: 0.5rem 0.75rem; margin-top: 0.5rem; font-size: 0.85rem;">
                    <strong>Your Offered Book:</strong> ${escapeHtml(req.offeredBookTitle)}
                </div>
            `;
        }

        let lendingHtml = '';
        if (req.exchangeMode === 'LENDING' && req.proposedDurationDays) {
            lendingHtml = `<span style="font-size: 0.85rem; color: var(--color-slate-dark);">⏱️ Requested Duration: <strong>${req.proposedDurationDays} Days</strong></span>`;
        }

        return `
            <div class="request-card">
                <img src="${escapeHtml(coverImg)}" alt="${escapeHtml(req.bookTitle)}" class="request-card-thumb" onerror="this.src='https://images.unsplash.com/photo-1544716278-ca5e3f4abd8c?w=300&q=80';">
                
                <div class="request-card-body">
                    <div style="display: flex; gap: 0.5rem; align-items: center; flex-wrap: wrap;">
                        ${getModeBadge(req.exchangeMode)}
                        ${getStatusBadge(req.status)}
                    </div>

                    <a href="${APP_CONTEXT}/book-details.html?id=${req.bookId}" class="request-card-title">${escapeHtml(req.bookTitle)}</a>
                    <div class="request-card-author">By ${escapeHtml(req.bookAuthor || 'Author')}</div>

                    <div class="request-meta-row">
                        <span class="request-party-info">
                            🏛️ Book Owner: <strong>${escapeHtml(req.ownerName || 'Student')}</strong> (${escapeHtml(req.ownerCampus || 'Main Campus')})
                        </span>
                        ${req.handoverLocation ? `<span class="request-party-info">📍 Meeting Spot: <strong>${escapeHtml(req.handoverLocation)}</strong></span>` : ''}
                        ${lendingHtml}
                    </div>

                    ${req.requestMessage ? `<div class="request-message-bubble">"${escapeHtml(req.requestMessage)}"</div>` : ''}
                    ${swapHtml}
                </div>

                <div class="request-card-actions">
                    ${isPending ? `
                        <button type="button" class="btn btn-outline" onclick="handleCancelRequest(${req.id})">
                            ⊘ Cancel Request
                        </button>
                    ` : ''}
                    ${isAccepted && req.exchangeId ? `
                        <a href="${APP_CONTEXT}/exchange-details.html?id=${req.exchangeId}" class="btn btn-primary" style="background: var(--color-gold); border-color: var(--color-gold);">
                            🤝 View Handover &rarr;
                        </a>
                    ` : ''}
                    <a href="${APP_CONTEXT}/book-details.html?id=${req.bookId}" class="btn btn-outline" style="font-size: 0.8rem; padding: 0.4rem 0.6rem;">
                        View Listing
                    </a>
                </div>
            </div>
        `;
    }).join('');
}

function renderExchangesList() {
    const container = document.getElementById('tab-pane-exchanges');
    if (!container) return;

    if (!exchangesCache.length) {
        container.innerHTML = `
            <div style="text-align: center; padding: 3rem 1rem; background: #FFF; border-radius: 8px; border: 1px solid var(--color-border);">
                <div style="font-size: 2.5rem; margin-bottom: 0.75rem;">🤝</div>
                <h3 style="font-size: 1.15rem; color: var(--color-navy); margin-bottom: 0.5rem;">No Active or Completed Handovers</h3>
                <p style="color: var(--color-slate); font-size: 0.9rem; max-width: 450px; margin: 0 auto 1.25rem;">
                    Once an exchange request is accepted, your physical campus handover and loan return tracking will be recorded here.
                </p>
                <a href="${APP_CONTEXT}/explore.html" class="btn btn-primary">Find Textbooks to Exchange</a>
            </div>
        `;
        return;
    }

    container.innerHTML = exchangesCache.map(ex => {
        const coverImg = ex.bookImageUrl || 'https://images.unsplash.com/photo-1544716278-ca5e3f4abd8c?w=300&q=80';

        let loanDates = '';
        if (ex.exchangeMode === 'LENDING' && ex.dueDate) {
            loanDates = `<span style="font-size: 0.85rem; color: #B45309; font-weight: 600;">📅 Due Date: ${ex.dueDate}</span>`;
        }

        return `
            <div class="request-card">
                <img src="${escapeHtml(coverImg)}" alt="${escapeHtml(ex.bookTitle)}" class="request-card-thumb" onerror="this.src='https://images.unsplash.com/photo-1544716278-ca5e3f4abd8c?w=300&q=80';">
                
                <div class="request-card-body">
                    <div style="display: flex; gap: 0.5rem; align-items: center; flex-wrap: wrap;">
                        ${getModeBadge(ex.exchangeMode)}
                        ${getStatusBadge(ex.status)}
                    </div>

                    <a href="${APP_CONTEXT}/exchange-details.html?id=${ex.id}" class="request-card-title">${escapeHtml(ex.bookTitle)}</a>
                    <div class="request-card-author">By ${escapeHtml(ex.bookAuthor || 'Author')}</div>

                    <div class="request-meta-row">
                        <span class="request-party-info">Owner: <strong>${escapeHtml(ex.ownerName)}</strong></span>
                        <span class="request-party-info">• Requester: <strong>${escapeHtml(ex.requesterName)}</strong></span>
                        ${ex.handoverLocation ? `<span class="request-party-info">• 📍 Location: <strong>${escapeHtml(ex.handoverLocation)}</strong></span>` : ''}
                        ${loanDates}
                    </div>

                    <div style="display: flex; gap: 1rem; margin-top: 0.5rem; font-size: 0.8rem;">
                        <span style="color: ${ex.ownerHandoverConfirmed ? '#047857' : '#B45309'};">
                            ${ex.ownerHandoverConfirmed ? '✓ Owner Handover Confirmed' : '⏳ Waiting Owner Handover'}
                        </span>
                        <span style="color: ${ex.requesterHandoverConfirmed ? '#047857' : '#B45309'};">
                            ${ex.requesterHandoverConfirmed ? '✓ Requester Handover Confirmed' : '⏳ Waiting Requester Handover'}
                        </span>
                    </div>
                </div>

                <div class="request-card-actions">
                    <a href="${APP_CONTEXT}/exchange-details.html?id=${ex.id}" class="btn btn-primary" style="background: var(--color-navy);">
                        🤝 Handover Details &rarr;
                    </a>
                </div>
            </div>
        `;
    }).join('');
}

// Modal Handlers for Accept Request
function openAcceptModal(requestId, suggestedLocation) {
    document.getElementById('accept-request-id').value = requestId;
    document.getElementById('accept-location').value = suggestedLocation || 'Central Library Block B, Campus Circulation Desk';
    document.getElementById('accept-notes').value = '';
    const modal = document.getElementById('accept-modal');
    if (modal) modal.classList.add('active');
}

function closeAcceptModal() {
    const modal = document.getElementById('accept-modal');
    if (modal) modal.classList.remove('active');
}

async function submitAcceptRequest(event) {
    event.preventDefault();
    const requestId = document.getElementById('accept-request-id').value;
    const location = document.getElementById('accept-location').value.trim();
    const notes = document.getElementById('accept-notes').value.trim();
    const btn = document.getElementById('btn-submit-accept');

    btn.disabled = true;
    btn.textContent = 'Accepting...';

    try {
        const response = await fetch(`${APP_CONTEXT}/api/exchange-requests/${requestId}/accept`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ handoverLocation: location, handoverNotes: notes })
        });

        const data = await response.json();
        if (response.ok && data.success) {
            closeAcceptModal();
            showRequestAlert(data.message || 'Request accepted successfully! Campus handover is initialized.', 'success');
            await loadRequestsDashboard();
        } else {
            showRequestAlert(data.error || 'Failed to accept request.', 'danger');
        }
    } catch (err) {
        showRequestAlert('Network error while accepting request: ' + err.message, 'danger');
    } finally {
        btn.disabled = false;
        btn.textContent = 'Confirm & Accept';
    }
}

async function handleRejectRequest(requestId) {
    if (!confirm('Are you sure you want to decline this exchange request?')) return;

    try {
        const response = await fetch(`${APP_CONTEXT}/api/exchange-requests/${requestId}/reject`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ reason: 'Declined by owner' })
        });

        const data = await response.json();
        if (response.ok && data.success) {
            showRequestAlert('Request declined.', 'info');
            await loadRequestsDashboard();
        } else {
            showRequestAlert(data.error || 'Failed to decline request.', 'danger');
        }
    } catch (err) {
        showRequestAlert('Network error: ' + err.message, 'danger');
    }
}

async function handleCancelRequest(requestId) {
    if (!confirm('Are you sure you want to cancel this pending exchange request?')) return;

    try {
        const response = await fetch(`${APP_CONTEXT}/api/exchange-requests/${requestId}/cancel`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' }
        });

        const data = await response.json();
        if (response.ok && data.success) {
            showRequestAlert('Exchange request cancelled.', 'info');
            await loadRequestsDashboard();
        } else {
            showRequestAlert(data.error || 'Failed to cancel request.', 'danger');
        }
    } catch (err) {
        showRequestAlert('Network error: ' + err.message, 'danger');
    }
}

// ============================================================================
// 2. EXCHANGE-DETAILS PAGE CONTROLLER
// ============================================================================
async function loadExchangeDetailsPage() {
    const container = document.getElementById('exchange-details-container');
    if (!container) return;

    const params = new URLSearchParams(window.location.search);
    const exchangeId = params.get('id');

    if (!exchangeId) {
        container.innerHTML = `
            <div style="text-align: center; padding: 3rem; background: #FFF; border-radius: 8px;">
                <p style="color: #DC2626;">Missing exchange transaction ID.</p>
                <a href="${APP_CONTEXT}/my-requests.html" class="btn btn-outline">&larr; Return to Requests</a>
            </div>
        `;
        return;
    }

    try {
        const response = await fetch(`${APP_CONTEXT}/api/exchanges/${exchangeId}`);
        if (response.status === 401) {
            window.location.href = `${APP_CONTEXT}/login.html?authRequired=true`;
            return;
        }

        const data = await response.json();
        if (!response.ok || !data.success) {
            container.innerHTML = `
                <div style="text-align: center; padding: 3rem; background: #FFF; border-radius: 8px;">
                    <p style="color: #DC2626;">${escapeHtml(data.error || 'Exchange not found or access denied.')}</p>
                    <a href="${APP_CONTEXT}/my-requests.html" class="btn btn-outline">&larr; Return to Requests</a>
                </div>
            `;
            return;
        }

        renderExchangeDetails(data.exchange, data.isOwner, data.isRequester);

    } catch (err) {
        console.error('Failed to load exchange details:', err);
        container.innerHTML = `
            <div style="text-align: center; padding: 3rem; background: #FFF; border-radius: 8px;">
                <p style="color: #DC2626;">Network error loading exchange details: ${err.message}</p>
            </div>
        `;
    }
}

function renderExchangeDetails(ex, isOwner, isRequester) {
    const container = document.getElementById('exchange-details-container');
    if (!container) return;

    const isAwaiting = ex.status === 'AWAITING_HANDOVER';
    const isActive = ex.status === 'ACTIVE';
    const isCompleted = ex.status === 'COMPLETED';
    const isReturned = ex.status === 'RETURNED';

    const userHandoverDone = isOwner ? ex.ownerHandoverConfirmed : ex.requesterHandoverConfirmed;
    const peerHandoverDone = isOwner ? ex.requesterHandoverConfirmed : ex.ownerHandoverConfirmed;
    const bothHandoverDone = ex.ownerHandoverConfirmed && ex.requesterHandoverConfirmed;

    const userReturnDone = isOwner ? ex.ownerReturnConfirmed : ex.requesterReturnConfirmed;
    const peerReturnDone = isOwner ? ex.requesterReturnConfirmed : ex.ownerReturnConfirmed;
    const bothReturnDone = ex.ownerReturnConfirmed && ex.requesterReturnConfirmed;

    // Stepper determination
    const s1 = 'completed';
    const s2 = 'completed';
    const s3 = bothHandoverDone ? 'completed' : 'active';
    const s4 = bothHandoverDone ? (isReturned ? 'completed' : 'active') : '';
    const s5 = isReturned ? 'completed' : '';

    const peerName = isOwner ? ex.requesterName : ex.ownerName;
    const peerRole = isOwner ? 'Requester / Borrower' : 'Textbook Owner';
    const peerDept = isOwner ? ex.requesterDepartment : ex.ownerDepartment;
    const peerCampus = isOwner ? ex.requesterCampus : ex.ownerCampus;
    const peerEmail = isOwner ? ex.requesterEmail : ex.ownerEmail;

    container.innerHTML = `
        <div style="background: #FFF; border: 1px solid var(--color-border); border-radius: var(--radius-lg); padding: 2rem; box-shadow: var(--shadow-sm);">
            
            <!-- Top Status Banner -->
            <div style="display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 1rem; border-bottom: 1px solid var(--color-border); padding-bottom: 1.5rem;">
                <div>
                    <div style="display: flex; gap: 0.5rem; align-items: center; margin-bottom: 0.5rem;">
                        ${getModeBadge(ex.exchangeMode)}
                        ${getStatusBadge(ex.status)}
                    </div>
                    <h1 style="font-size: 1.6rem; font-weight: 800; color: var(--color-navy); margin: 0;">
                        Campus Handover Coordination
                    </h1>
                </div>

                <div>
                    <a href="${APP_CONTEXT}/my-requests.html" class="btn btn-outline" style="font-size: 0.85rem;">
                        &larr; All Requests
                    </a>
                </div>
            </div>

            <!-- Stepper Progress Bar -->
            <div class="exchange-stepper">
                <div class="stepper-step ${s1}">
                    <div class="stepper-circle">1</div>
                    <div class="stepper-label">Request Sent</div>
                </div>
                <div class="stepper-step ${s2}">
                    <div class="stepper-circle">2</div>
                    <div class="stepper-label">Accepted</div>
                </div>
                <div class="stepper-step ${s3}">
                    <div class="stepper-circle">3</div>
                    <div class="stepper-label">Campus Handover</div>
                </div>
                <div class="stepper-step ${s4}">
                    <div class="stepper-circle">4</div>
                    <div class="stepper-label">${ex.exchangeMode === 'LENDING' ? 'Active Loan' : 'Ownership Finalized'}</div>
                </div>
                ${ex.exchangeMode === 'LENDING' ? `
                    <div class="stepper-step ${s5}">
                        <div class="stepper-circle">5</div>
                        <div class="stepper-label">Returned</div>
                    </div>
                ` : ''}
            </div>

            <!-- Handover Verification Card (Dual Confirmation) -->
            <div class="dual-confirmation-card">
                <div style="display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 1rem;">
                    <div>
                        <h3 style="font-size: 1.15rem; font-weight: 700; color: var(--color-navy); margin: 0 0 0.25rem 0;">
                            Physical Handover Dual Confirmation
                        </h3>
                        <p style="font-size: 0.85rem; color: var(--color-slate); margin: 0;">
                            Both students must meet in-person and confirm handover on their respective devices.
                        </p>
                    </div>

                    <!-- Handover Button -->
                    <div>
                        ${isAwaiting ? `
                            ${!userHandoverDone ? `
                                <button type="button" class="btn btn-primary" id="btn-confirm-handover" onclick="triggerConfirmHandover(${ex.id})" style="padding: 0.75rem 1.5rem; font-weight: 700;">
                                    🤝 Confirm I Received / Handed Over Book
                                </button>
                            ` : `
                                <button type="button" class="btn btn-outline" disabled style="background: #ECFDF5; color: #047857; border-color: #A7F3D0; font-weight: 700;">
                                    ✓ You Confirmed (Waiting for Peer)
                                </button>
                            `}
                        ` : `
                            <span style="display: inline-flex; align-items: center; gap: 0.4rem; color: #047857; font-weight: 700; background: #ECFDF5; padding: 0.5rem 1rem; border-radius: 6px; border: 1px solid #A7F3D0;">
                                ✓ Handover Fully Confirmed
                            </span>
                        `}
                    </div>
                </div>

                <div class="dual-confirmation-grid">
                    <div class="party-confirm-box ${ex.ownerHandoverConfirmed ? 'confirmed' : 'pending'}">
                        <div style="font-size: 1.5rem;">${ex.ownerHandoverConfirmed ? '✅' : '⏳'}</div>
                        <div>
                            <div style="font-weight: 700; font-size: 0.95rem; color: var(--color-navy);">
                                Book Owner (${escapeHtml(ex.ownerName)})
                            </div>
                            <div style="font-size: 0.8rem; color: var(--color-slate);">
                                ${ex.ownerHandoverConfirmed ? 'Confirmed physical handover' : 'Awaiting confirmation'}
                            </div>
                        </div>
                    </div>

                    <div class="party-confirm-box ${ex.requesterHandoverConfirmed ? 'confirmed' : 'pending'}">
                        <div style="font-size: 1.5rem;">${ex.requesterHandoverConfirmed ? '✅' : '⏳'}</div>
                        <div>
                            <div style="font-weight: 700; font-size: 0.95rem; color: var(--color-navy);">
                                Requester (${escapeHtml(ex.requesterName)})
                            </div>
                            <div style="font-size: 0.8rem; color: var(--color-slate);">
                                ${ex.requesterHandoverConfirmed ? 'Confirmed physical handover' : 'Awaiting confirmation'}
                            </div>
                        </div>
                    </div>
                </div>
            </div>

            <!-- Lending Return Verification Card (If LENDING) -->
            ${ex.exchangeMode === 'LENDING' ? `
                <div class="dual-confirmation-card" style="border-color: #FEF3C7; background: #FFFDF7;">
                    <div style="display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 1rem;">
                        <div>
                            <h3 style="font-size: 1.15rem; font-weight: 700; color: #92400E; margin: 0 0 0.25rem 0;">
                                Book Return Confirmation (Semester Lending)
                            </h3>
                            <p style="font-size: 0.85rem; color: var(--color-slate); margin: 0;">
                                Due Date: <strong>${ex.dueDate || 'Agreed date'}</strong>. When the borrower returns the book to the owner, both parties confirm below.
                            </p>
                        </div>

                        <div>
                            ${isActive ? `
                                ${!userReturnDone ? `
                                    <button type="button" class="btn btn-primary" id="btn-confirm-return" onclick="triggerConfirmReturn(${ex.id})" style="background: #D97706; border-color: #D97706; padding: 0.75rem 1.5rem; font-weight: 700;">
                                        🔄 Confirm Book Returned
                                    </button>
                                ` : `
                                    <button type="button" class="btn btn-outline" disabled style="background: #FEF3C7; color: #92400E; border-color: #FDE68A; font-weight: 700;">
                                        ✓ You Confirmed Return (Waiting for Peer)
                                    </button>
                                `}
                            ` : (isReturned ? `
                                <span style="display: inline-flex; align-items: center; gap: 0.4rem; color: #047857; font-weight: 700; background: #ECFDF5; padding: 0.5rem 1rem; border-radius: 6px; border: 1px solid #A7F3D0;">
                                    ✓ Book Returned Successfully
                                </span>
                            ` : `
                                <span style="color: var(--color-slate); font-size: 0.85rem; font-style: italic;">
                                    Enabled after physical handover
                                </span>
                            `)}
                        </div>
                    </div>

                    <div class="dual-confirmation-grid">
                        <div class="party-confirm-box ${ex.ownerReturnConfirmed ? 'confirmed' : 'pending'}">
                            <div style="font-size: 1.5rem;">${ex.ownerReturnConfirmed ? '✅' : '⏳'}</div>
                            <div>
                                <div style="font-weight: 700; font-size: 0.95rem; color: var(--color-navy);">
                                    Owner Return Confirmation (${escapeHtml(ex.ownerName)})
                                </div>
                                <div style="font-size: 0.8rem; color: var(--color-slate);">
                                    ${ex.ownerReturnConfirmed ? 'Confirmed book received back' : 'Awaiting return confirmation'}
                                </div>
                            </div>
                        </div>

                        <div class="party-confirm-box ${ex.requesterReturnConfirmed ? 'confirmed' : 'pending'}">
                            <div style="font-size: 1.5rem;">${ex.requesterReturnConfirmed ? '✅' : '⏳'}</div>
                            <div>
                                <div style="font-weight: 700; font-size: 0.95rem; color: var(--color-navy);">
                                    Borrower Return Confirmation (${escapeHtml(ex.requesterName)})
                                </div>
                                <div style="font-size: 0.8rem; color: var(--color-slate);">
                                    ${ex.requesterReturnConfirmed ? 'Confirmed book returned' : 'Awaiting return confirmation'}
                                </div>
                            </div>
                        </div>
                    </div>
                </div>
            ` : ''}

            <!-- Handover Location & Instructions -->
            <div style="display: grid; grid-template-columns: 1fr 1fr; gap: 1.5rem; margin-top: 2rem;">
                
                <!-- Campus Spot Card -->
                <div style="background: var(--color-surface); border: 1px solid var(--color-border); border-radius: var(--radius-md); padding: 1.5rem;">
                    <h4 style="font-size: 1rem; font-weight: 700; color: var(--color-navy); margin-bottom: 0.75rem; display: flex; align-items: center; gap: 0.5rem;">
                        <span>📍</span> Campus Meeting Spot
                    </h4>
                    <p style="font-size: 0.95rem; font-weight: 600; color: var(--color-slate-dark); margin-bottom: 0.5rem;">
                        ${escapeHtml(ex.handoverLocation || 'Central Library Block B')}
                    </p>
                    ${ex.handoverNotes ? `
                        <div style="background: #FFF; border: 1px solid #E2E8F0; border-radius: 6px; padding: 0.75rem; font-size: 0.85rem; color: var(--color-slate);">
                            <strong>Instructions / Notes:</strong> "${escapeHtml(ex.handoverNotes)}"
                        </div>
                    ` : ''}
                </div>

                <!-- Peer Contact Card -->
                <div style="background: var(--color-surface); border: 1px solid var(--color-border); border-radius: var(--radius-md); padding: 1.5rem;">
                    <h4 style="font-size: 1rem; font-weight: 700; color: var(--color-navy); margin-bottom: 0.75rem; display: flex; align-items: center; gap: 0.5rem;">
                        <span>🎓</span> Connect with ${escapeHtml(peerRole)}
                    </h4>
                    <p style="font-size: 0.95rem; font-weight: 700; color: var(--color-navy); margin-bottom: 0.25rem;">
                        ${escapeHtml(peerName)}
                    </p>
                    <p style="font-size: 0.85rem; color: var(--color-slate); margin-bottom: 0.25rem;">
                        🏛️ ${escapeHtml(peerCampus || 'Main Campus')} • 📚 ${escapeHtml(peerDept || 'Department')}
                    </p>
                    <p style="font-size: 0.85rem; color: var(--color-slate);">
                        📧 ${escapeHtml(peerEmail || 'Email verified')}
                    </p>
                </div>
            </div>

            <!-- Books Involved -->
            <div style="margin-top: 2.5rem;">
                <h3 style="font-size: 1.15rem; font-weight: 700; color: var(--color-navy); margin-bottom: 1rem;">
                    Textbooks in this Exchange
                </h3>

                <div style="display: grid; grid-template-columns: repeat(auto-fit, minmax(280px, 1fr)); gap: 1.5rem;">
                    <!-- Requested Book -->
                    <div style="display: flex; gap: 1rem; background: #FFF; border: 1px solid var(--color-border); border-radius: 8px; padding: 1rem;">
                        <img src="${escapeHtml(ex.bookImageUrl || 'https://images.unsplash.com/photo-1544716278-ca5e3f4abd8c?w=300&q=80')}" alt="${escapeHtml(ex.bookTitle)}" style="width: 70px; height: 100px; object-fit: cover; border-radius: 4px;" onerror="this.src='https://images.unsplash.com/photo-1544716278-ca5e3f4abd8c?w=300&q=80';">
                        <div>
                            <span class="announcement-pill" style="font-size: 0.65rem;">PRIMARY TEXTBOOK</span>
                            <div style="font-weight: 700; font-size: 0.95rem; color: var(--color-navy); margin-top: 0.25rem;">
                                ${escapeHtml(ex.bookTitle)}
                            </div>
                            <div style="font-size: 0.8rem; color: var(--color-slate);">
                                By ${escapeHtml(ex.bookAuthor || 'Author')}
                            </div>
                            <div style="font-size: 0.75rem; color: var(--color-slate-light); margin-top: 0.25rem;">
                                Subject: ${escapeHtml(ex.bookSubject || 'Academic')}
                            </div>
                        </div>
                    </div>

                    <!-- Offered Book for SWAP -->
                    ${ex.offeredBookTitle ? `
                        <div style="display: flex; gap: 1rem; background: #FFF; border: 1px solid var(--color-border); border-radius: 8px; padding: 1rem;">
                            <img src="${escapeHtml(ex.offeredBookImageUrl || 'https://images.unsplash.com/photo-1532012164546-f432f2e3777f?w=300&q=80')}" alt="${escapeHtml(ex.offeredBookTitle)}" style="width: 70px; height: 100px; object-fit: cover; border-radius: 4px;" onerror="this.src='https://images.unsplash.com/photo-1532012164546-f432f2e3777f?w=300&q=80';">
                            <div>
                                <span class="announcement-pill" style="font-size: 0.65rem; background: #FDE68A; color: #92400E;">SWAPPED IN RETURN</span>
                                <div style="font-weight: 700; font-size: 0.95rem; color: var(--color-navy); margin-top: 0.25rem;">
                                    ${escapeHtml(ex.offeredBookTitle)}
                                </div>
                                <div style="font-size: 0.8rem; color: var(--color-slate);">
                                    By ${escapeHtml(ex.offeredBookAuthor || 'Author')}
                                </div>
                            </div>
                        </div>
                    ` : ''}
                </div>
            </div>

        </div>
    `;
}

async function triggerConfirmHandover(exchangeId) {
    if (!confirm('Have you physically met your peer on campus and verified the textbook condition?')) return;

    const btn = document.getElementById('btn-confirm-handover');
    if (btn) {
        btn.disabled = true;
        btn.textContent = 'Recording confirmation...';
    }

    try {
        const response = await fetch(`${APP_CONTEXT}/api/exchanges/${exchangeId}/confirm-handover`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' }
        });

        const data = await response.json();
        if (response.ok && data.success) {
            showRequestAlert(data.message, 'success');
            await loadExchangeDetailsPage();
        } else {
            showRequestAlert(data.error || 'Failed to record handover confirmation.', 'danger');
            if (btn) {
                btn.disabled = false;
                btn.textContent = '🤝 Confirm I Received / Handed Over Book';
            }
        }
    } catch (err) {
        showRequestAlert('Network error: ' + err.message, 'danger');
        if (btn) {
            btn.disabled = false;
            btn.textContent = '🤝 Confirm I Received / Handed Over Book';
        }
    }
}

async function triggerConfirmReturn(exchangeId) {
    if (!confirm('Have you physically returned or received back the borrowed textbook?')) return;

    const btn = document.getElementById('btn-confirm-return');
    if (btn) {
        btn.disabled = true;
        btn.textContent = 'Recording return...';
    }

    try {
        const response = await fetch(`${APP_CONTEXT}/api/exchanges/${exchangeId}/confirm-return`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' }
        });

        const data = await response.json();
        if (response.ok && data.success) {
            showRequestAlert(data.message, 'success');
            await loadExchangeDetailsPage();
        } else {
            showRequestAlert(data.error || 'Failed to record return confirmation.', 'danger');
            if (btn) {
                btn.disabled = false;
                btn.textContent = '🔄 Confirm Book Returned';
            }
        }
    } catch (err) {
        showRequestAlert('Network error: ' + err.message, 'danger');
        if (btn) {
            btn.disabled = false;
            btn.textContent = '🔄 Confirm Book Returned';
        }
    }
}

// Auto-initialize based on active page
document.addEventListener('DOMContentLoaded', () => {
    if (document.getElementById('tab-pane-received') || document.getElementById('stat-sent-count')) {
        loadRequestsDashboard();
    }
    if (document.getElementById('exchange-details-container')) {
        loadExchangeDetailsPage();
    }
});
