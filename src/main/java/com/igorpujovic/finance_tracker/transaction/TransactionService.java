package com.igorpujovic.finance_tracker.transaction;

import com.igorpujovic.finance_tracker.category.Category;
import com.igorpujovic.finance_tracker.category.CategoryRepository;
import com.igorpujovic.finance_tracker.common.ConflictException;
import com.igorpujovic.finance_tracker.common.NotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;

@Service
@Transactional
public class TransactionService {

    private static final String DINAR = "RSD";

    private final TransactionRepository transactions;
    private final CategoryRepository categories;

    public TransactionService(TransactionRepository transactions, CategoryRepository categories) {
        this.transactions = transactions;
        this.categories = categories;
    }

    @Transactional(readOnly = true)
    public Page<Transaction> search(LocalDate from, LocalDate to, Long categoryId,
                                    TransactionType type, boolean uncategorizedOnly,
                                    Pageable pageable) {
        Specification<Transaction> spec = TransactionSpecifications.all();
        if (from != null) {
            spec = spec.and(TransactionSpecifications.onOrAfter(from));
        }
        if (to != null) {
            spec = spec.and(TransactionSpecifications.onOrBefore(to));
        }
        if (categoryId != null) {
            spec = spec.and(TransactionSpecifications.inCategory(categoryId));
        }
        if (uncategorizedOnly) {
            spec = spec.and(TransactionSpecifications.uncategorized());
        }
        if (type != null) {
            spec = spec.and(TransactionSpecifications.ofType(type));
        }
        return transactions.findAll(spec, pageable);
    }

    @Transactional(readOnly = true)
    public Transaction get(Long id) {
        return transactions.findById(id)
                .orElseThrow(() -> new NotFoundException("Transaction", id));
    }

    public Transaction create(TransactionRequest request) {
        String currency = requireDinar(request.normalisedCurrency());
        Category category = resolveCategory(request.categoryId(), request.amount());
        Transaction transaction = Transaction.manual(
                request.date(),
                request.description(),
                request.amount(),
                currency,
                dinarAmountOf(request.amount(), currency),
                category);
        return transactions.save(transaction);
    }

    public Transaction update(Long id, TransactionRequest request) {
        Transaction transaction = get(id);
        String currency = requireDinar(request.normalisedCurrency());
        Category category = resolveCategory(request.categoryId(), request.amount());
        transaction.amend(
                request.date(),
                request.description(),
                request.amount(),
                currency,
                dinarAmountOf(request.amount(), currency),
                category);
        return transaction;
    }

    public Transaction changeCategory(Long id, Long categoryId) {
        Transaction transaction = get(id);
        transaction.assignCategory(resolveCategory(categoryId, transaction.getAmount()));
        return transaction;
    }

    public void delete(Long id) {
        transactions.delete(get(id));
    }

    /**
     * ADR-0004, applied to a category chosen by hand rather than by a rule: an expense
     * takes only an expense category. A rule that cannot apply is silently skipped; a
     * person who asks for it directly is told why not.
     */
    private Category resolveCategory(Long categoryId, BigDecimal amount) {
        if (categoryId == null) {
            return null;
        }
        Category category = categories.findById(categoryId)
                .orElseThrow(() -> new NotFoundException("Category", categoryId));
        TransactionType type = TransactionType.of(amount);
        if (!type.canTake(category.getType())) {
            throw new ConflictException("A transaction of type " + type + " cannot take category '"
                    + category.getName() + "', which is " + category.getType());
        }
        return category;
    }

    /**
     * Step 1 handles dinars only. Converting a foreign amount needs a rate for the
     * transaction's own date, and resolving those is step 4 — so rather than guess at
     * a rate or store an unconverted row, the request is refused with a reason.
     */
    private String requireDinar(String currency) {
        if (!DINAR.equals(currency)) {
            throw new IllegalArgumentException(
                    "Manual transactions are limited to RSD until exchange rates arrive; got " + currency);
        }
        return currency;
    }

    private BigDecimal dinarAmountOf(BigDecimal amount, String currency) {
        return DINAR.equals(currency) ? amount : null;
    }
}
