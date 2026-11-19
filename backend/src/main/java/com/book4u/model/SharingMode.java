package com.book4u.model;

/**
 * Supported book sharing modes across the Book4u platform.
 * Notice: Strictly peer-to-peer sharing. Commercial sale/purchase is excluded.
 */
public enum SharingMode {
    /**
     * Direct bilateral or circular book exchange.
     */
    SWAP,

    /**
     * Altruistic gifting of books with zero return obligation.
     */
    GIVEAWAY,

    /**
     * Temporary lending with an agreed return calendar date.
     */
    LENDING
}
