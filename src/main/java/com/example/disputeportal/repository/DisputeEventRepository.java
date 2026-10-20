package com.example.disputeportal.repository;

import com.example.disputeportal.entity.DisputeEvent;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Database access for the dispute history entries. Entries are only ever inserted, never changed.
 */
public interface DisputeEventRepository extends JpaRepository<DisputeEvent, Long> {

    /**
     * A dispute's history, oldest first (id breaks ties between entries created at the same moment).
     */
    List<DisputeEvent> findByDisputeIdOrderByCreatedAtAscIdAsc(Long disputeId);
}
