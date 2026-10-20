package com.example.disputeportal.security;

/**
 * The things an API key can be allowed to do. Every key is given only the scopes it needs, so a leaked
 * transaction-loading key can't be used to create accounts, and the other way round.
 */
public final class ApiScopes {

    /** May load transactions (POST /api/v1/transactions/batch). */
    public static final String TRANSACTIONS_LOAD = "transactions:load";

    /** May create customer accounts (POST /api/v1/customers/batch). */
    public static final String CUSTOMERS_CREATE = "customers:create";

    private static final String AUTHORITY_PREFIX = "SCOPE_";

    private ApiScopes() {
        // constants only
    }

    /** The Spring Security authority name for a scope, e.g. "SCOPE_transactions:load". */
    public static String authority(String scope) {
        return AUTHORITY_PREFIX + scope;
    }
}
