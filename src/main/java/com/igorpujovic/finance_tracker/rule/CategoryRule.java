package com.igorpujovic.finance_tracker.rule;

import com.igorpujovic.finance_tracker.category.Category;
import com.igorpujovic.finance_tracker.transaction.TransactionType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.util.Locale;

/**
 * A keyword that assigns a category to any transaction whose description contains it.
 * Whether it applies to a transaction at all is this class's question; which of several
 * applicable rules wins is {@link Categorization}'s.
 */
@Entity
@Table(name = "category_rules")
public class CategoryRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String keyword;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    @Column(nullable = false)
    private int priority;

    protected CategoryRule() {
        // for JPA
    }

    public CategoryRule(String keyword, Category category, int priority) {
        this.keyword = stripped(keyword);
        this.category = category;
        this.priority = priority;
    }

    /**
     * A rule's three fields are changed together, as {@code Category} is renamed and
     * retyped together: there is no request that changes one of them alone, and nothing
     * that reads a rule half updated.
     */
    public void amend(String keyword, Category category, int priority) {
        this.keyword = stripped(keyword);
        this.category = category;
        this.priority = priority;
    }

    /**
     * Stored stripped, here rather than in the request that asked for it, so that the
     * one keyword a rule has is the one it matches on. A keyword kept with a stray
     * space would match nothing, and the mistake is invisible in a list of keywords.
     */
    private static String stripped(String keyword) {
        return keyword == null ? null : keyword.strip();
    }

    public Long getId() {
        return id;
    }

    public String getKeyword() {
        return keyword;
    }

    public Category getCategory() {
        return category;
    }

    public int getPriority() {
        return priority;
    }

    /**
     * Whether this rule is a candidate for a transaction at all — both halves of
     * ADR-0004's filter, in the order the ADR puts them: a rule whose category cannot
     * legally take the transaction is never in the running, however well its keyword
     * fits. What happens when several rules apply is {@link Categorization}'s question.
     */
    public boolean appliesTo(String description, TransactionType type) {
        return type.canTake(category.getType()) && matches(description);
    }

    /**
     * Case-insensitive containment, in {@link Locale#ROOT} so that the comparison does
     * not depend on the locale the server happens to run in — a Turkish default locale
     * uppercases {@code i} to {@code İ}, and a keyword would stop matching its own
     * merchant.
     */
    private boolean matches(String description) {
        return description != null
                && description.toUpperCase(Locale.ROOT).contains(keyword.toUpperCase(Locale.ROOT));
    }
}
