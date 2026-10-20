package com.example.disputeportal.repository;

import com.example.disputeportal.entity.BankTransaction;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

/**
 * Database access for transactions. The SQL is derived from the method names.
 */
public interface BankTransactionRepository extends JpaRepository<BankTransaction, Long> {

    /**
     * One page of a customer's transactions. Paging and sorting come from the Pageable argument.
     */
    Page<BankTransaction> findByCustomerUsername(String username, Pageable pageable);

    /** Ownership is enforced in the query so a customer can never load someone else's transaction. */
    Optional<BankTransaction> findByIdAndCustomerUsername(Long id, String username);

    /**
     * Existing transactions with any of the given references. The loading API uses this to recognise retries and
     * conflicts. The entity graph fetches each customer in the same query.
     */
    @EntityGraph(attributePaths = "customer")
    List<BankTransaction> findByReferenceIn(Collection<String> references);

    /**
     * Same lookup, but locks the row until the transaction ends. Raising a dispute uses this so two
     * simultaneous submissions for one transaction are serialised and the second sees the first.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<BankTransaction> findWithLockByIdAndCustomerUsername(Long id, String username);
}
