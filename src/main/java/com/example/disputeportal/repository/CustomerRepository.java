package com.example.disputeportal.repository;

import com.example.disputeportal.entity.Customer;
import com.example.disputeportal.entity.Role;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Database access for customers and admins (both are rows in the customers table, told apart by role).
 *
 * Spring Data generates the implementation: the SQL is derived from each method's name.
 */
public interface CustomerRepository extends JpaRepository<Customer, Long> {

    /**
     * Finds an account by login name. Used at sign-in and by the startup seeders.
     */
    Optional<Customer> findByUsername(String username);

    /**
     * Finds several accounts at once. Used by the loading API to resolve a whole batch with one query.
     */
    List<Customer> findByUsernameIn(Collection<String> usernames);

    /**
     * Login names of every account with the given role. Used to tell all admins about new disputes and evidence.
     */
    @Query("select c.username from Customer c where c.role = :role")
    List<String> findUsernamesByRole(@Param("role") Role role);
}
