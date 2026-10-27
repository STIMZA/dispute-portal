package com.example.disputeportal.entity;

/**
 * What an account may do. Spring Security turns each into an authority named ROLE_CUSTOMER or ROLE_ADMIN.
 */
public enum Role {
    /**
     * Sees only their own transactions and disputes, and can raise disputes.
     */
    CUSTOMER,
    /**
     * Works the dispute queue. Has no transactions of their own and can't use the customer pages.
     */
    ADMIN
}
