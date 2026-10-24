package com.example.disputeportal.service;

import com.example.disputeportal.config.EvidenceProperties;
import com.example.disputeportal.dto.EvidenceFile;
import com.example.disputeportal.dto.EvidenceLimits;
import com.example.disputeportal.dto.EvidenceRow;
import com.example.disputeportal.entity.Dispute;
import com.example.disputeportal.entity.DisputeEvidence;
import com.example.disputeportal.exception.BusinessRuleException;
import com.example.disputeportal.exception.ResourceNotFoundException;
import com.example.disputeportal.repository.DisputeEvidenceRepository;
import com.example.disputeportal.repository.DisputeRepository;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

/**
 * Evidence uploads: customers attach supporting files (receipts, screenshots, correspondence) to their own open
 * disputes, and admins read them while reviewing.
 *
 * What is checked on every upload, and why:
 * <ul>
 *   <li>Ownership: the dispute must be the signed-in customer's own (someone else's id is "not found").</li>
 *   <li>The dispute must still be open. Closed disputes are final, so their file set is too.</li>
 *   <li>Size, and a per-dispute file count, so storage can't be filled by one account.</li>
 *   <li>The real file type: the first bytes must say PDF, PNG or JPEG, and the extension must agree. The type the
 *       browser claims is ignored, because the client controls it.</li>
 *   <li>The same content can't be attached twice to one dispute.</li>
 * </ul>
 * Files are stored under a random key and sent back only as attachments (never displayed inside the site), with the
 * type we detected. The dispute row is locked while an upload is processed, so concurrent uploads can't exceed the
 * limit and an upload can't slip in while an admin closes the dispute.
 */
@Service
public class EvidenceService {

    private static final Logger log = LoggerFactory.getLogger(EvidenceService.class);

    static final String PDF = "application/pdf";
    static final String PNG = "image/png";
    static final String JPEG = "image/jpeg";

    /** Which file extensions are acceptable for each detected type. */
    private static final Map<String, Set<String>> EXTENSIONS = Map.of(
            PDF, Set.of("pdf"),
            PNG, Set.of("png"),
            JPEG, Set.of("jpg", "jpeg"));

    private static final byte[] PDF_MAGIC = {'%', 'P', 'D', 'F', '-'};
    private static final byte[] PNG_MAGIC = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};
    private static final byte[] JPEG_MAGIC = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};

    /** Matches the length of the original_filename column. */
    private static final int MAX_FILENAME_LENGTH = 100;

    private final DisputeRepository disputeRepository;
    private final DisputeEvidenceRepository evidenceRepository;
    private final EvidenceStorage storage;
    private final NotificationService notificationService;
    private final EvidenceProperties properties;
    private final Clock clock;

    public EvidenceService(DisputeRepository disputeRepository, DisputeEvidenceRepository evidenceRepository,
                           EvidenceStorage storage, NotificationService notificationService,
                           EvidenceProperties properties, Clock clock) {
        this.disputeRepository = disputeRepository;
        this.evidenceRepository = evidenceRepository;
        this.storage = storage;
        this.notificationService = notificationService;
        this.properties = properties;
        this.clock = clock;
    }

    /** The rules the pages show next to the upload form. */
    public EvidenceLimits limits() {
        return new EvidenceLimits(properties.maxFileBytes(), properties.maxFilesPerDispute());
    }

    /**
     * Attaches a file to one of the customer's own open disputes and tells the admins.
     *
     * @param disputeId        the dispute to attach to
     * @param username         the signed-in customer, who must have raised it
     * @param originalFilename the name the browser sent (cleaned before it is stored or shown)
     * @param content          the file's bytes
     * @return the saved file's details
     * @throws ResourceNotFoundException if the dispute doesn't exist or belongs to someone else
     * @throws BusinessRuleException     if the dispute is closed, the file is empty, too big, of a type that isn't
     *                                   accepted, a duplicate, or the dispute already has the maximum number of files
     */
    @Transactional
    public EvidenceRow upload(Long disputeId, String username, String originalFilename, byte[] content) {
        // Looked up first, so a stranger always gets "not found" and never learns anything from our other checks.
        // createdBy is the customer who raised the dispute, which is the same person as the transaction's owner.
        Dispute dispute = disputeRepository.findWithLockById(disputeId)
                .filter(d -> d.getCreatedBy().equals(username))
                .orElseThrow(() -> new ResourceNotFoundException("Dispute " + disputeId + " not found"));

        if (!dispute.getStatus().isOpen()) {
            throw new BusinessRuleException("This dispute is closed, so no more evidence can be added.");
        }
        if (content == null || content.length == 0) {
            throw new BusinessRuleException("Choose a file to upload. The file you selected is empty.");
        }
        if (content.length > properties.maxFileBytes()) {
            throw new BusinessRuleException("That file is too large. The limit is " + limits().maxFileLabel() + ".");
        }

        String filename = cleanFilename(originalFilename);
        String contentType = detectType(content);
        if (contentType == null) {
            throw new BusinessRuleException("Only PDF, PNG and JPG files can be uploaded.");
        }
        if (!EXTENSIONS.get(contentType).contains(extensionOf(filename))) {
            throw new BusinessRuleException("The file name doesn't match its contents. Check that it really is a "
                    + "PDF, PNG or JPG.");
        }
        if (evidenceRepository.countByDisputeId(dispute.getId()) >= properties.maxFilesPerDispute()) {
            throw new BusinessRuleException("This dispute already has the maximum of "
                    + properties.maxFilesPerDispute() + " files.");
        }
        String sha256 = sha256Hex(content);
        if (evidenceRepository.existsByDisputeIdAndSha256(dispute.getId(), sha256)) {
            throw new BusinessRuleException("That file is already attached to this dispute.");
        }

        // The file is written inside the database transaction. If the transaction later fails, the file is removed.
        String key = UUID.randomUUID().toString().replace("-", "");
        storage.store(key, content);
        removeFileIfTransactionFails(key);

        LocalDateTime now = LocalDateTime.now(clock);
        DisputeEvidence saved = evidenceRepository.save(new DisputeEvidence(dispute, filename, contentType,
                content.length, key, sha256, username, now));
        notificationService.evidenceAdded(dispute);
        log.info("Evidence {} ({}, {} bytes) added to dispute {}", saved.getId(), contentType, content.length,
                dispute.getId());
        return toRow(saved, username);
    }

    /** The files on one of the customer's own disputes, oldest first. Someone else's dispute is "not found". */
    @Transactional(readOnly = true)
    public List<EvidenceRow> listForCustomer(Long disputeId, String username) {
        Dispute dispute = ownDispute(disputeId, username);
        return evidenceRepository.findByDisputeIdOrderByUploadedAtAscIdAsc(dispute.getId()).stream()
                .map(e -> toRow(e, username)).toList();
    }

    /** One file of the customer's own dispute, for download. */
    @Transactional(readOnly = true)
    public EvidenceFile downloadForCustomer(Long disputeId, Long evidenceId, String username) {
        Dispute dispute = ownDispute(disputeId, username);
        return toFile(findEvidence(dispute.getId(), evidenceId));
    }

    /** The files on any dispute, for the review page. Admin only. */
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional(readOnly = true)
    public List<EvidenceRow> listForAdmin(Long disputeId) {
        if (!disputeRepository.existsById(disputeId)) {
            throw new ResourceNotFoundException("Dispute " + disputeId + " not found");
        }
        return evidenceRepository.findByDisputeIdOrderByUploadedAtAscIdAsc(disputeId).stream()
                .map(e -> toRow(e, null)).toList();
    }

    /** One file of any dispute, for download. Admin only. */
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional(readOnly = true)
    public EvidenceFile downloadForAdmin(Long disputeId, Long evidenceId) {
        return toFile(findEvidence(disputeId, evidenceId));
    }

    private Dispute ownDispute(Long disputeId, String username) {
        return disputeRepository.findByIdAndTransactionCustomerUsername(disputeId, username)
                .orElseThrow(() -> new ResourceNotFoundException("Dispute " + disputeId + " not found"));
    }

    private DisputeEvidence findEvidence(Long disputeId, Long evidenceId) {
        return evidenceRepository.findByIdAndDisputeId(evidenceId, disputeId)
                .orElseThrow(() -> new ResourceNotFoundException("Evidence " + evidenceId + " not found"));
    }

    private EvidenceFile toFile(DisputeEvidence e) {
        return new EvidenceFile(e.getOriginalFilename(), e.getContentType(), e.getSizeBytes(),
                storage.load(e.getStorageKey()));
    }

    /** Customers see their own uploads as "You"; admins (viewer == null) see the real username. */
    private EvidenceRow toRow(DisputeEvidence e, String viewer) {
        String by = viewer == null ? e.getUploadedBy() : (e.getUploadedBy().equals(viewer) ? "You" : "Dispute team");
        return new EvidenceRow(e.getId(), e.getOriginalFilename(), e.getContentType(), e.getSizeBytes(), by,
                e.getUploadedAt());
    }

    /** If the surrounding database transaction doesn't commit, delete the file that was already written. */
    private void removeFileIfTransactionFails(String key) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status != STATUS_COMMITTED) {
                    storage.delete(key);
                }
            }
        });
    }

    /** The detected type from the first bytes, or null if it isn't one we accept. */
    static String detectType(byte[] content) {
        if (startsWith(content, PDF_MAGIC)) {
            return PDF;
        }
        if (startsWith(content, PNG_MAGIC)) {
            return PNG;
        }
        if (startsWith(content, JPEG_MAGIC)) {
            return JPEG;
        }
        return null;
    }

    private static boolean startsWith(byte[] content, byte[] prefix) {
        if (content.length < prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if (content[i] != prefix[i]) {
                return false;
            }
        }
        return true;
    }

    /**
     * A safe display name: only the last path part, no control characters, at most 100 characters (keeping the
     * extension). Never used to build a path on disk.
     */
    static String cleanFilename(String original) {
        String name = original == null ? "" : original;
        name = name.substring(Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\')) + 1);
        name = name.replaceAll("\\p{Cntrl}", "").trim();
        if (!StringUtils.hasText(name)) {
            return "evidence";
        }
        if (name.length() > MAX_FILENAME_LENGTH) {
            String extension = extensionOf(name);
            String suffix = extension.isEmpty() || extension.length() > 10 ? "" : "." + extension;
            name = name.substring(0, MAX_FILENAME_LENGTH - suffix.length()) + suffix;
        }
        return name;
    }

    /** Lower-case extension without the dot, or an empty string if there is none. */
    static String extensionOf(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot < 0 ? "" : filename.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private static String sha256Hex(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException ex) {
            // Every Java runtime must provide SHA-256, so this can't happen.
            throw new IllegalStateException(ex);
        }
    }
}
