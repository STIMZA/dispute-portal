package com.example.disputeportal.security;

import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import com.example.disputeportal.repository.CustomerRepository;

/**
 * Tells Spring Security how to find an account at login: by username, returning
 * the stored password hash and role.
 */
@Service
public class CustomerUserDetailsService implements UserDetailsService {

    private final CustomerRepository customerRepository;

    public CustomerUserDetailsService(CustomerRepository customerRepository) {
        this.customerRepository = customerRepository;
    }

    /**
     * Called by Spring Security during login. Spring compares the typed
     * password with the returned hash. An unknown username raises the same
     * exception as a wrong password, so the login page can't be used to
     * discover which usernames exist.
     */
    @Override
    public UserDetails loadUserByUsername(String username) {
        return customerRepository.findByUsername(username)
                .map(c -> User.withUsername(c.getUsername())
                        .password(c.getPasswordHash())
                        // Spring Security stores this as the authority ROLE_<name>, which is what hasRole(...) checks.
                        .roles(c.getRole().name())
                        .build())
                .orElseThrow(() -> new UsernameNotFoundException("Unknown user"));
    }
}
