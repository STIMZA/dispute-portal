package com.example.disputeportal.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.disputeportal.config.EvidenceProperties;
import com.example.disputeportal.dto.EvidenceRow;
import com.example.disputeportal.entity.BankTransaction;
import com.example.disputeportal.entity.Customer;
import com.example.disputeportal.entity.Dispute;
import com.example.disputeportal.entity.DisputeEvidence;
import com.example.disputeportal.entity.DisputeReason;
import com.example.disputeportal.entity.DisputeStatus;
import com.example.disputeportal.exception.BusinessRuleException;
import com.example.disputeportal.exception.ResourceNotFoundException;
import com.example.disputeportal.repository.DisputeEvidenceRepository;
import com.example.disputeportal.repository.DisputeRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for the evidence rules: ownership, open disputes only, size and count limits, real file type,
 * duplicates, and that admins are told. Repositories and storage are mocked, and the clock is fixed.
 */
@ExtendWith(MockitoExtension.class)
class EvidenceServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-13T10:00:00Z"), ZoneOffset.UTC);

    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3, 4};
    private static final byte[] PDF = "%PDF-1.7 fake body".getBytes();
    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 1, 2};

    @Mock
    private DisputeRepository disputeRepository;
    @Mock
    private DisputeEvidenceRepository evidenceRepository;
    @Mock
    private EvidenceStorage storage;
    @Mock
    private NotificationService notificationService;

    private EvidenceService service;
    private Dispute dispute;

    @BeforeEach
    void setUp() {
        service = new EvidenceService(disputeRepository, evidenceRepository, storage, notificationService,
                new EvidenceProperties("unused", 1000, 2), CLOCK);
        Customer customer = new Customer("thandi", "hash", "Thandi Nkosi");
        BankTransaction tx = new BankTransaction(customer, "TXN-1", "Takealot.com", "Card purchase",
                new BigDecimal("1299.00"), "ZAR", LocalDateTime.of(2026, 10, 1, 12, 0));
        dispute = new Dispute(tx, DisputeReason.GOODS_NOT_RECEIVED, "The parcel never arrived", "thandi",
                LocalDateTime.of(2026, 10, 2, 9, 0));
    }

    private void givenOwnOpenDispute() {
        when(disputeRepository.findWithLockById(1L)).thenReturn(Optional.of(dispute));
    }

    private void givenRoomForOneMoreFile() {
        when(evidenceRepository.countByDisputeId(any())).thenReturn(0L);
        when(evidenceRepository.existsByDisputeIdAndSha256(any(), anyString())).thenReturn(false);
        when(evidenceRepository.save(any(DisputeEvidence.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void storesAValidPngAndNotifiesAdmins() {
        givenOwnOpenDispute();
        givenRoomForOneMoreFile();

        EvidenceRow row = service.upload(1L, "thandi", "receipt.png", PNG);

        assertThat(row.filename()).isEqualTo("receipt.png");
        assertThat(row.contentType()).isEqualTo("image/png");
        assertThat(row.sizeBytes()).isEqualTo(PNG.length);
        assertThat(row.uploadedBy()).isEqualTo("You");
        ArgumentCaptor<DisputeEvidence> saved = ArgumentCaptor.forClass(DisputeEvidence.class);
        verify(evidenceRepository).save(saved.capture());
        assertThat(saved.getValue().getStorageKey()).matches("[0-9a-f]{32}");
        assertThat(saved.getValue().getSha256()).hasSize(64);
        assertThat(saved.getValue().getUploadedAt()).isEqualTo(LocalDateTime.of(2026, 10, 13, 10, 0));
        verify(storage).store(saved.getValue().getStorageKey(), PNG);
        verify(notificationService).evidenceAdded(dispute);
    }

    @Test
    void acceptsPdfAndJpegWithMatchingExtensions() {
        givenOwnOpenDispute();
        givenRoomForOneMoreFile();

        assertThat(service.upload(1L, "thandi", "slip.PDF", PDF).contentType()).isEqualTo("application/pdf");
        assertThat(service.upload(1L, "thandi", "photo.jpeg", JPEG).contentType()).isEqualTo("image/jpeg");
        assertThat(service.upload(1L, "thandi", "photo.jpg", JPEG).contentType()).isEqualTo("image/jpeg");
    }

    @Test
    void someoneElsesDisputeIsNotFound() {
        givenOwnOpenDispute();

        assertThatThrownBy(() -> service.upload(1L, "sipho", "receipt.png", PNG))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(storage, never()).store(anyString(), any());
    }

    @Test
    void unknownDisputeIsNotFound() {
        when(disputeRepository.findWithLockById(9L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.upload(9L, "thandi", "receipt.png", PNG))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void closedDisputesTakeNoMoreEvidence() {
        dispute.changeStatus(DisputeStatus.REJECTED, "Not upheld", LocalDateTime.of(2026, 10, 5, 9, 0), "agent1");
        givenOwnOpenDispute();

        assertThatThrownBy(() -> service.upload(1L, "thandi", "receipt.png", PNG))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("closed");
        verify(storage, never()).store(anyString(), any());
    }

    @Test
    void rejectsEmptyFiles() {
        givenOwnOpenDispute();

        assertThatThrownBy(() -> service.upload(1L, "thandi", "receipt.png", new byte[0]))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("empty");
    }

    @Test
    void rejectsFilesOverTheSizeLimit() {
        givenOwnOpenDispute();
        byte[] big = Arrays.copyOf(PNG, 1001);

        assertThatThrownBy(() -> service.upload(1L, "thandi", "big.png", big))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("too large");
        verify(storage, never()).store(anyString(), any());
    }

    @Test
    void rejectsAnExecutableDisguisedAsAPdf() {
        givenOwnOpenDispute();
        byte[] exe = {'M', 'Z', (byte) 0x90, 0x00, 3, 0};

        assertThatThrownBy(() -> service.upload(1L, "thandi", "invoice.pdf", exe))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("PDF, PNG and JPG");
        verify(storage, never()).store(anyString(), any());
    }

    @Test
    void rejectsAFileWhoseNameDoesNotMatchItsContents() {
        givenOwnOpenDispute();

        assertThatThrownBy(() -> service.upload(1L, "thandi", "photo.png", PDF))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("doesn't match");
        verify(storage, never()).store(anyString(), any());
    }

    @Test
    void rejectsWhenTheDisputeIsFull() {
        givenOwnOpenDispute();
        when(evidenceRepository.countByDisputeId(any())).thenReturn(2L);

        assertThatThrownBy(() -> service.upload(1L, "thandi", "receipt.png", PNG))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("maximum of 2");
        verify(storage, never()).store(anyString(), any());
    }

    @Test
    void rejectsTheSameFileTwice() {
        givenOwnOpenDispute();
        when(evidenceRepository.countByDisputeId(any())).thenReturn(1L);
        when(evidenceRepository.existsByDisputeIdAndSha256(any(), anyString())).thenReturn(true);

        assertThatThrownBy(() -> service.upload(1L, "thandi", "receipt.png", PNG))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("already attached");
        verify(storage, never()).store(anyString(), any());
        verify(notificationService, never()).evidenceAdded(any());
    }

    @Test
    void cleansFileNames() {
        assertThat(EvidenceService.cleanFilename("C:\\Users\\me\\scan.pdf")).isEqualTo("scan.pdf");
        assertThat(EvidenceService.cleanFilename("../../etc/passwd.png")).isEqualTo("passwd.png");
        assertThat(EvidenceService.cleanFilename("bad\r\nname.pdf")).isEqualTo("badname.pdf");
        assertThat(EvidenceService.cleanFilename("   ")).isEqualTo("evidence");
        assertThat(EvidenceService.cleanFilename(null)).isEqualTo("evidence");
        String longName = EvidenceService.cleanFilename("a".repeat(300) + ".pdf");
        assertThat(longName).hasSize(100).endsWith(".pdf");
    }
}
