package com.igorpujovic.finance_tracker.rule;

import com.igorpujovic.finance_tracker.category.Category;
import com.igorpujovic.finance_tracker.transaction.TransactionType;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * The automatic half of categorization: a set of category rules, asked which category
 * a description and a type should take.
 * <p>
 * ADR-0004 is the whole shape of this class. A rule whose category cannot legally take
 * the transaction is not a candidate, so the type check runs before priority is
 * consulted rather than vetoing its answer afterwards.
 */
public final class Categorization {

    /**
     * The order rules are consulted in, which is the order {@code GET /api/rules} lists
     * them in: {@link CategoryRuleRepository#findAllWithCategory()} sorts by the same
     * three keys, so what the list shows first is what wins.
     * <p>
     * Priority decides. A tie goes to the keyword that sorts first, and then to the
     * older rule, because a tie has to resolve the same way on every run — otherwise
     * the same statement imported twice could categorize differently. The last key is
     * what makes the order total; an unsaved rule has no id and sorts last.
     */
    private static final Comparator<CategoryRule> STRONGEST_FIRST =
            Comparator.comparingInt(CategoryRule::getPriority).reversed()
                    .thenComparing(CategoryRule::getKeyword)
                    .thenComparing(CategoryRule::getId, Comparator.nullsLast(Comparator.naturalOrder()));

    private final List<CategoryRule> strongestFirst;

    private Categorization(List<CategoryRule> strongestFirst) {
        this.strongestFirst = strongestFirst;
    }

    public static Categorization by(Collection<CategoryRule> rules) {
        return new Categorization(rules.stream().sorted(STRONGEST_FIRST).toList());
    }

    /**
     * The category a transaction of this description and type should take, or empty if
     * no rule can legally claim it — which leaves it uncategorized, a real state with
     * its own filter rather than a failure.
     */
    public Optional<Category> categoryFor(String description, TransactionType type) {
        return strongestFirst.stream()
                .filter(rule -> rule.appliesTo(description, type))
                .map(CategoryRule::getCategory)
                .findFirst();
    }
}
