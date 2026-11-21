package com.igorpujovic.finance_tracker.transaction;

import com.igorpujovic.finance_tracker.common.NotFoundException;
import com.igorpujovic.finance_tracker.rates.Conversion;
import com.igorpujovic.finance_tracker.rates.CurrencyOnDate;
import com.igorpujovic.finance_tracker.rates.ExchangeRateService;
import com.igorpujovic.finance_tracker.rates.ResolvedRates;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * A transaction entered or corrected by hand, in the two movements of ADR-0005: the rate
 * the line needs is resolved, and only then does a transaction open and write. The write
 * is {@link TransactionWriter}, and this class deliberately carries no
 * {@code @Transactional} on the paths that reach it — a statement resolves the pairs a
 * fileful of lines needs before opening one, and a single line is owed the same ordering
 * for the same reason, that a request may have to reach the National Bank for a date
 * nobody has fetched yet.
 */
@Service
public class TransactionService {

    private final TransactionRepository transactions;
    private final ExchangeRateService rates;
    private final TransactionWriter writer;

    public TransactionService(TransactionRepository transactions, ExchangeRateService rates,
                              TransactionWriter writer) {
        this.transactions = transactions;
        this.rates = rates;
        this.writer = writer;
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
        return writer.create(request, dinarAmountOf(request));
    }

    /**
     * The same conversion as {@link #create}, and not only for symmetry: an imported
     * foreign row is corrected through here, so a check that admitted dinars alone would
     * leave the euro line a statement wrote amendable only by a claim that it was in
     * dinars — which converts at 1 and restates a 9.99 EUR expense as 9.99 RSD.
     */
    public Transaction update(Long id, TransactionRequest request) {
        requireExists(id);
        return writer.update(id, request, dinarAmountOf(request));
    }

    public Transaction changeCategory(Long id, Long categoryId) {
        return writer.changeCategory(id, categoryId);
    }

    public void delete(Long id) {
        writer.delete(id);
    }

    /**
     * Asked before the rate is, so that a {@code PUT} naming a transaction that is not
     * there answers 404 rather than reaching for the National Bank on behalf of a row
     * that does not exist and then arguing about its currency. The writer looks the
     * transaction up again inside its own transaction, which is the lookup that decides
     * anything: this one cannot promise the row survives the moment after it returns.
     */
    private void requireExists(Long id) {
        if (!transactions.existsById(id)) {
            throw new NotFoundException("Transaction", id);
        }
    }

    /**
     * What this request's amount is worth in dinars, resolved before the write opens.
     * <p>
     * The three outcomes of ADR-0005 answered for one transaction rather than for a
     * statement. A currency the National Bank does not publish and a conversion the
     * column cannot hold are the fault of the amount that needed the rate: on the import
     * path that costs one source line and the rest of the file imports, and here there is
     * nothing else in the request, so it is a 400 carrying the same reason. The bank being
     * unreachable is nobody's fault on either path — it leaves {@link ExchangeRateService}
     * as an exception the API answers 503 to, and it gets here before anything is written.
     */
    private BigDecimal dinarAmountOf(TransactionRequest request) {
        CurrencyOnDate spent = new CurrencyOnDate(request.normalisedCurrency(), request.date());
        ResolvedRates resolved = rates.resolve(List.of(spent));
        return switch (resolved.conversionOf(request.amount(), spent)) {
            case Conversion.DinarAmount(BigDecimal dinars) -> dinars;
            case Conversion.Unavailable(String reason) -> throw new IllegalArgumentException(reason);
        };
    }
}
