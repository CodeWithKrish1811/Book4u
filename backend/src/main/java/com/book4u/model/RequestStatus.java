package com.book4u.model;

/**
 * State machine status transitions for exchange requests.
 */
public enum RequestStatus {
    PENDING,
    ACCEPTED,
    REJECTED,
    CANCELLED,
    HANDOVER_SCHEDULED,
    COMPLETED,
    RETURNED
}
