package com.example.disputeportal.config;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.example.disputeportal.entity.BankTransaction;
import com.example.disputeportal.entity.Customer;
import com.example.disputeportal.entity.Dispute;
import com.example.disputeportal.entity.DisputeEvent;
import com.example.disputeportal.entity.DisputeReason;
import com.example.disputeportal.entity.DisputeStatus;
import com.example.disputeportal.entity.Role;
import com.example.disputeportal.repository.BankTransactionRepository;
import com.example.disputeportal.repository.CustomerRepository;
import com.example.disputeportal.repository.DisputeEventRepository;
import com.example.disputeportal.repository.DisputeRepository;

/**
 * This component will load some demo data into the database on startup. This
 * data will be used for demonstration purposes only, in the case that you
 * configure the application for production - this component will not load any
 * demo data.
 */
@Component
@Profile({"dev", "demo"})
public class DemoDataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);

    /**
     * Username of the demo admin account.
     */
    private static final String DEMO_ADMIN = "demo-admin";

    /**
     * Sample merchants and amounts; the transaction at position i uses entry i
     * modulo the array length.
     */
    private static final String[] MERCHANTS = {
        "Checkers Hyper", "Uber Trip", "Takealot.com", "Netflix", "Engen Garage",
        "Woolworths", "Vida e Caffè", "Discovery Insure", "Showmax", "Pick n Pay"};
    private static final String[] AMOUNTS = {
        "842.35", "119.90", "1299.00", "199.00", "650.00", "436.20", "58.50", "1875.00", "99.00", "312.75"};

    private final CustomerRepository customers;
    private final BankTransactionRepository transactions;
    private final DisputeRepository disputes;
    private final DisputeEventRepository events;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;
    private final String demoPassword;
    /**
     * This will help generate unique transaction references (TXN-100000,
     * TXN-100001, ...).
     */
    private int referenceCounter = 100000;

    public DemoDataSeeder(CustomerRepository customers, BankTransactionRepository transactions,
            DisputeRepository disputes, DisputeEventRepository events,
            PasswordEncoder passwordEncoder, Clock clock,
            @Value("${app.demo.password}") String demoPassword) {
        this.customers = customers;
        this.transactions = transactions;
        this.disputes = disputes;
        this.events = events;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
        this.demoPassword = demoPassword;
    }

    /**
     * We check to see if the demo customers already exist, and if it doesnt we
     * add them. This way, if the application is restarted against a persistent
     * database, we don't duplicate the demo data.
     */
    @Override
    @Transactional
    public void run(String... args) {
        if (customers.findByUsername("thandi").isPresent()) {
            return;
        }
        LocalDateTime now = LocalDateTime.now(clock);
        // Assigned the hashed password to all demo users, so they can log in with the same password.
        // For demonstartion purposes only, not for production
        String hash = passwordEncoder.encode(demoPassword);

        Customer thandi = customers.save(new Customer("thandi", hash, "Thandi Nkosi"));
        Customer sipho = customers.save(new Customer("sipho", hash, "Sipho Dlamini"));
        customers.save(new Customer(DEMO_ADMIN, hash, "Demo Administrator", Role.ADMIN));

        // 28 transactions, with a few transactionspast their dispute window.
        List<BankTransaction> thandiTx = new ArrayList<>();
        for (int i = 0; i < 28; i++) {
            LocalDateTime when = now.minusDays(i * 5L + 1).minusHours(i);
            thandiTx.add(transactions.save(newTransaction(thandi, i, when)));
        }
        for (int i = 0; i < 6; i++) {
            transactions.save(newTransaction(sipho, i, now.minusDays(i * 7L + 2)));
        }

        // Disputes in different states so the history page has something to show.
        raise(thandiTx.get(2), DisputeReason.DUPLICATE_CHARGE,
                "Charged twice for the same trip on the same evening.", now, 1, null, null);
        raise(thandiTx.get(5), DisputeReason.INCORRECT_AMOUNT,
                "The slip said R400 but I was charged more.", now, 1, DisputeStatus.UNDER_REVIEW,
                "We have asked the merchant for the till slip.");
        raise(thandiTx.get(9), DisputeReason.UNRECOGNISED_TRANSACTION,
                "I have no subscription with this merchant.", now, 2, DisputeStatus.RESOLVED_IN_FAVOUR,
                "Refunded in full and the merchant has been blocked on your card.");
        raise(thandiTx.get(13), DisputeReason.GOODS_NOT_RECEIVED,
                "The parcel never arrived.", now, 2, DisputeStatus.REJECTED,
                "Courier records show the parcel was delivered and signed for.");
        log.info("Demo data loaded: customers 'thandi' and 'sipho', admin 'demo-admin'");
    }

    /**
     * Builds a sample transaction for the customer at the given point in time.
     */
    private BankTransaction newTransaction(Customer customer, int index, LocalDateTime when) {
        String reference = "TXN-" + (referenceCounter++);
        return new BankTransaction(customer, reference, MERCHANTS[index % MERCHANTS.length],
                "Card purchase", new BigDecimal(AMOUNTS[index % AMOUNTS.length]), "ZAR", when);
    }

    /**
     * Creates a dispute and its history entries on a transaction. If
     * finalStatus is given, the demo admin has already decided it, so the
     * history pages show every state (open, under review, resolved, rejected).
     */
    private void raise(BankTransaction tx, DisputeReason reason, String details, LocalDateTime now,
            int daysAfterTransaction, DisputeStatus finalStatus, String note) {
        LocalDateTime created = tx.getTransactionDate().plusDays(daysAfterTransaction);
        if (created.isAfter(now)) {
            created = now;
        }
        Dispute dispute = new Dispute(tx, reason, details, "thandi", created);
        LocalDateTime updated = null;
        if (finalStatus != null) {
            updated = created.plusDays(5).isAfter(now) ? now : created.plusDays(5);
            dispute.changeStatus(finalStatus, note, updated, DEMO_ADMIN);
        }
        disputes.save(dispute);
        events.save(new DisputeEvent(dispute, null, DisputeStatus.SUBMITTED, null, "thandi", created));
        if (finalStatus != null) {
            events.save(new DisputeEvent(dispute, DisputeStatus.SUBMITTED, finalStatus, note, DEMO_ADMIN, updated));
        }
    }
}
