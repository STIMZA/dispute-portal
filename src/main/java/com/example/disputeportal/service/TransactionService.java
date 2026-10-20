package com.example.disputeportal.service;

import com.example.disputeportal.dto.DisputeCounts;
import com.example.disputeportal.dto.TransactionRow;
import com.example.disputeportal.entity.BankTransaction;
import com.example.disputeportal.exception.ResourceNotFoundException;
import com.example.disputeportal.repository.BankTransactionRepository;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reads a customer's transactions for the transactions page, enriched with dispute information: whether a
 * dispute is open, whether the transaction can be disputed, and how many disputes were logged and closed.
 *
 * The dispute lookups are delegated to {@link DisputeService}.
 */
@Service
public class TransactionService {

    private final BankTransactionRepository transactionRepository;
    private final DisputeService disputeService;

    public TransactionService(BankTransactionRepository transactionRepository, DisputeService disputeService) {
        this.transactionRepository = transactionRepository;
        this.disputeService = disputeService;
    }

    /**
     * One page of the customer's transactions.
     *
     * @param username the signed-in customer; only their own transactions are returned
     * @param pageable page number, size and sort order, chosen by the controller
     */
    @Transactional(readOnly = true)
    public Page<TransactionRow> list(String username, Pageable pageable) {
        Page<BankTransaction> page = transactionRepository.findByCustomerUsername(username, pageable);
        List<Long> ids = page.getContent().stream().map(BankTransaction::getId).toList();
        // One query for the whole page rather than one per row.
        Set<Long> withOpenDispute = disputeService.findOpenDisputeTransactionIds(ids);
        // Same idea for the logged/closed counts: one grouped query for the whole page.
        Map<Long, DisputeCounts> counts = disputeService.countByTransaction(ids);
        return page.map(t -> toRow(t, withOpenDispute.contains(t.getId()),
                counts.getOrDefault(t.getId(), DisputeCounts.NONE)));
    }

    /**
     * A single transaction of the customer, used by the dispute form.
     * Another customer's transaction id is reported as "not found".
     */
    @Transactional(readOnly = true)
    public TransactionRow get(Long id, String username) {
        BankTransaction transaction = transactionRepository.findByIdAndCustomerUsername(id, username)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction " + id + " not found"));
        boolean open = !disputeService.findOpenDisputeTransactionIds(List.of(id)).isEmpty();
        DisputeCounts counts = disputeService.countByTransaction(List.of(id)).getOrDefault(id, DisputeCounts.NONE);
        return toRow(transaction, open, counts);
    }

    /**
     * Builds the row shown on the page. A transaction can be disputed only when it has no open dispute and is
     * still inside the dispute window.
     */
    private TransactionRow toRow(BankTransaction t, boolean disputeOpen, DisputeCounts counts) {
        boolean disputable = !disputeOpen && disputeService.isWithinWindow(t.getTransactionDate());
        return new TransactionRow(t.getId(), t.getReference(), t.getMerchant(), t.getDescription(),
                t.getAmount(), t.getCurrency(), t.getTransactionDate(), disputeOpen, disputable,
                counts.logged(), counts.closed());
    }
}
