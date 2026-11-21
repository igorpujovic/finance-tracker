package com.igorpujovic.finance_tracker.transaction;

import com.igorpujovic.finance_tracker.category.Category;
import com.igorpujovic.finance_tracker.category.CategoryRepository;
import com.igorpujovic.finance_tracker.common.ConflictException;
import com.igorpujovic.finance_tracker.common.NotFoundException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * Everything the transaction endpoints write, and the transaction boundary they write
 * inside.
 * <p>
 * It is a separate bean from {@link TransactionService} rather than a method on it for
 * the reason {@code ImportWriter} is: ADR-0005 requires the rate a line needs to be
 * resolved before a transaction opens, and {@code @Transactional} on one bean calling
 * itself would have opened nothing here and resolved the rate inside the transaction it
 * is meant to stay outside of. The dinar amount therefore arrives as an argument — this
 * class cannot reach a rate, which is the guarantee rather than an oversight.
 */
@Component
class TransactionWriter {

    private final TransactionRepository transactions;
    private final CategoryRepository categories;

    TransactionWriter(TransactionRepository transactions, CategoryRepository categories) {
        this.transactions = transactions;
        this.categories = categories;
    }

    /**
     * Public although the class is not, because that is the visibility Spring documents
     * for {@code @Transactional} — the same note as on {@code ImportWriter.write}.
     */
    @Transactional
    public Transaction create(TransactionRequest request, BigDecimal amountRsd) {
        return transactions.save(Transaction.manual(
                request.date(),
                request.description(),
                request.amount(),
                request.normalisedCurrency(),
                amountRsd,
                resolveCategory(request.categoryId(), request.amount())));
    }

    @Transactional
    public Transaction update(Long id, TransactionRequest request, BigDecimal amountRsd) {
        Transaction transaction = find(id);
        transaction.amend(
                request.date(),
                request.description(),
                request.amount(),
                request.normalisedCurrency(),
                amountRsd,
                resolveCategory(request.categoryId(), request.amount()));
        return transaction;
    }

    @Transactional
    public Transaction changeCategory(Long id, Long categoryId) {
        Transaction transaction = find(id);
        transaction.assignCategory(resolveCategory(categoryId, transaction.getAmount()));
        return transaction;
    }

    @Transactional
    public void delete(Long id) {
        transactions.delete(find(id));
    }

    private Transaction find(Long id) {
        return transactions.findById(id)
                .orElseThrow(() -> new NotFoundException("Transaction", id));
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
}
