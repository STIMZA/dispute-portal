package com.example.disputeportal.repository;

import com.example.disputeportal.entity.DisputeEvidence;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Database access for the evidence records of disputes.
 */
public interface DisputeEvidenceRepository extends JpaRepository<DisputeEvidence, Long> {

    /** A dispute's evidence, oldest first (id breaks ties). */
    List<DisputeEvidence> findByDisputeIdOrderByUploadedAtAscIdAsc(Long disputeId);

    /** How many files the dispute already has. Used for the per-dispute limit. */
    long countByDisputeId(Long disputeId);

    /** Is a file with this content (SHA-256) already attached to the dispute? */
    boolean existsByDisputeIdAndSha256(Long disputeId, String sha256);

    /** One file of a dispute. Asking with the wrong dispute id finds nothing, so ids can't be mixed up. */
    Optional<DisputeEvidence> findByIdAndDisputeId(Long id, Long disputeId);
}
