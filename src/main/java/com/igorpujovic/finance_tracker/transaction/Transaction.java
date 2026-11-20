package com.igorpujovic.finance_tracker.transaction;

import com.igorpujovic.finance_tracker.category.Category;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "transactions")
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "date", nullable = false)
    private LocalDate date;

    @Column(nullable = false, length = 255)
    private String description;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "amount_rsd", nullable = false, precision = 12, scale = 2)
    private BigDecimal amountRsd;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private Category category;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private TransactionSource source;

    /**
     * Held as a plain identifier rather than an association: nothing navigates from a
     * transaction to its batch, and the batch detail endpoint reads errors, not rows.
     */
    @Column(name = "import_batch_id")
    private Long importBatchId;

    /**
     * ADR-0003: set once, at import, from the source line. Null for manual rows, and
     * deliberately not recomputed when the transaction is later edited — it identifies
     * the line that was imported, not the values the row now holds.
     */
    @Column(length = 64, updatable = false)
    private String fingerprint;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Transaction() {
        // for JPA
    }

    private Transaction(LocalDate date, String description, BigDecimal amount, String currency,
                        BigDecimal amountRsd, Category category, TransactionSource source,
                        Long importBatchId, String fingerprint) {
        this.date = date;
        this.description = description;
        this.amount = amount;
        this.currency = currency;
        this.amountRsd = amountRsd;
        this.category = category;
        this.source = source;
        this.importBatchId = importBatchId;
        this.fingerprint = fingerprint;
    }

    public static Transaction manual(LocalDate date, String description, BigDecimal amount,
                                     String currency, BigDecimal amountRsd, Category category) {
        return new Transaction(date, description, amount, currency, amountRsd, category,
                TransactionSource.MANUAL, null, null);
    }

    public static Transaction imported(LocalDate date, String description, BigDecimal amount,
                                       String currency, BigDecimal amountRsd, Category category,
                                       Long importBatchId, String fingerprint) {
        return new Transaction(date, description, amount, currency, amountRsd, category,
                TransactionSource.IMPORT, importBatchId, fingerprint);
    }

    @PrePersist
    void stampCreatedAt() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public TransactionType getType() {
        return TransactionType.of(amount);
    }

    public Long getId() {
        return id;
    }

    public LocalDate getDate() {
        return date;
    }

    public String getDescription() {
        return description;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getCurrency() {
        return currency;
    }

    public BigDecimal getAmountRsd() {
        return amountRsd;
    }

    public Category getCategory() {
        return category;
    }

    public TransactionSource getSource() {
        return source;
    }

    public Long getImportBatchId() {
        return importBatchId;
    }

    public String getFingerprint() {
        return fingerprint;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void assignCategory(Category category) {
        this.category = category;
    }

    public void amend(LocalDate date, String description, BigDecimal amount,
                      String currency, BigDecimal amountRsd, Category category) {
        this.date = date;
        this.description = description;
        this.amount = amount;
        this.currency = currency;
        this.amountRsd = amountRsd;
        this.category = category;
        // fingerprint intentionally untouched — see ADR-0003
    }
}
