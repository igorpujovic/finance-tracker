package com.igorpujovic.finance_tracker.rule;

import com.igorpujovic.finance_tracker.category.Category;
import com.igorpujovic.finance_tracker.category.CategoryRepository;
import com.igorpujovic.finance_tracker.common.NotFoundException;
import com.igorpujovic.finance_tracker.transaction.Transaction;
import com.igorpujovic.finance_tracker.transaction.TransactionRepository;
import com.igorpujovic.finance_tracker.transaction.TransactionSpecifications;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@Transactional
public class RuleService {

    private final CategoryRuleRepository rules;
    private final CategoryRepository categories;
    private final TransactionRepository transactions;

    public RuleService(CategoryRuleRepository rules, CategoryRepository categories,
                       TransactionRepository transactions) {
        this.rules = rules;
        this.categories = categories;
        this.transactions = transactions;
    }

    @Transactional(readOnly = true)
    public List<CategoryRule> list() {
        return rules.findAllWithCategory();
    }

    public CategoryRule create(RuleRequest request) {
        return rules.save(new CategoryRule(
                request.keyword(),
                categoryOf(request.categoryId()),
                request.priorityOrDefault()));
    }

    public CategoryRule update(Long id, RuleRequest request) {
        CategoryRule rule = rules.findById(id)
                .orElseThrow(() -> new NotFoundException("Category rule", id));
        rule.amend(
                request.keyword(),
                categoryOf(request.categoryId()),
                request.priorityOrDefault());
        return rule;
    }

    /**
     * Deleting a rule leaves every transaction it ever categorized exactly as it is.
     * The rule decided a category once; it does not hold it, and un-deciding months of
     * history on the strength of a DELETE is not something to do quietly.
     */
    public void delete(Long id) {
        rules.delete(rules.findById(id)
                .orElseThrow(() -> new NotFoundException("Category rule", id)));
    }

    private Category categoryOf(Long categoryId) {
        return categories.findById(categoryId)
                .orElseThrow(() -> new NotFoundException("Category", categoryId));
    }

    /**
     * The rule set as it stands, ready to be asked about a description. Callers that
     * categorize many transactions at once — an import, a re-apply — read the rules
     * once here rather than once per transaction.
     */
    @Transactional(readOnly = true)
    public Categorization currentCategorization() {
        return Categorization.by(rules.findAllWithCategory());
    }

    /**
     * Runs the rules over the uncategorized transactions, and only those. A transaction
     * that already has a category was either claimed by a rule or assigned by hand, and
     * neither is something a later run of this should overwrite: re-apply exists to
     * empty the uncategorized queue, not to re-decide settled money.
     */
    public RuleApplicationResponse apply() {
        Categorization categorization = currentCategorization();
        // The same predicate the uncategorized filter on the listing endpoint uses, so
        // that the queue re-apply works through is the queue a person can see. Read
        // whole rather than paged: every row has to be offered to the rules for the
        // reported count to mean anything, and the queue is one person's undecided
        // transactions rather than their whole history.
        List<Transaction> uncategorized =
                transactions.findAll(TransactionSpecifications.uncategorized());
        int categorized = 0;
        for (Transaction transaction : uncategorized) {
            Optional<Category> match = categorization
                    .categoryFor(transaction.getDescription(), transaction.getType());
            if (match.isPresent()) {
                transaction.assignCategory(match.get());
                categorized++;
            }
        }
        return new RuleApplicationResponse(uncategorized.size(), categorized);
    }
}
