package com.example.disputeportal.repository;

import com.example.disputeportal.entity.Dispute;
import com.example.disputeportal.entity.DisputeStatus;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.repository.query.Param;

/**
 * Database access for disputes. Most methods are derived from their names; the {@code @Query} ones are written out
 * because they need grouping or a projection. {@code @EntityGraph} loads related rows in the same query, which avoids
 * one extra query per row (the "N+1" problem) on list pages.
 */
public interface DisputeRepository extends JpaRepository<Dispute, Long>, JpaSpecificationExecutor<Dispute> {

    /**
     * Does the transaction have a dispute in any of these statuses? Used to enforce one open dispute at a time.
     */
    boolean existsByTransactionIdAndStatusIn(Long transactionId, Collection<DisputeStatus> statuses);

    /**
     * Ids (out of the given ones) of transactions that have a dispute in one of the statuses. One query for a whole page.
     */
    @Query("select d.transaction.id from Dispute d "
            + "where d.transaction.id in :transactionIds and d.status in :statuses")
    List<Long> findTransactionIdsWithStatusIn(@Param("transactionIds") Collection<Long> transactionIds,
                                              @Param("statuses") Collection<DisputeStatus> statuses);

    // Back-office queue: loads transaction and customer in the same query.
    /**
     * All disputes for the admin queue.
     */
    @EntityGraph(attributePaths = {"transaction", "transaction.customer"})
    Page<Dispute> findAll(Pageable pageable);

    /**
     * Disputes matching a filter built from optional criteria (status, date range), for the admin queue. Overridden
     * here only to attach the entity graph, so the page still loads transaction and customer in one query.
     */
    @Override
    @EntityGraph(attributePaths = {"transaction", "transaction.customer"})
    Page<Dispute> findAll(Specification<Dispute> spec, Pageable pageable);

    /** Locks the dispute so two admins can't decide the same dispute at once. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Dispute> findWithLockById(Long id);

    /** Disputes logged and closed per transaction, for a whole page of transactions in one query. */
    @Query("select d.transaction.id as transactionId, count(d) as logged, "
            + "count(case when d.status in :closedStatuses then 1 end) as closed "
            + "from Dispute d where d.transaction.id in :transactionIds group by d.transaction.id")
    List<TransactionDisputeCount> countByTransactionIds(@Param("transactionIds") Collection<Long> transactionIds,
                                                        @Param("closedStatuses") Collection<DisputeStatus> closedStatuses);

    /**
     * Earlier disputes on one of the customer's transactions, newest first. Ownership is part of the query.
     */
    @EntityGraph(attributePaths = "transaction")
    List<Dispute> findByTransactionIdAndTransactionCustomerUsernameOrderByCreatedAtDesc(Long transactionId,
                                                                                         String username);

    /**
     * One dispute, but only if its transaction belongs to the given customer.
     */
    @EntityGraph(attributePaths = "transaction")
    Optional<Dispute> findByIdAndTransactionCustomerUsername(Long id, String username);

    // The entity graph loads the transaction in the same query, avoiding N+1 selects on the history page.
    @EntityGraph(attributePaths = "transaction")
    Page<Dispute> findByTransactionCustomerUsername(String username, Pageable pageable);
}
