package com.example.disputeportal.service;

import java.util.Set;

/**
 * The result of a successful API key check.
 *
 * @param name   the client's name
 * @param scopes what the client is allowed to do
 */
public record AuthenticatedClient(String name, Set<String> scopes) {
}
