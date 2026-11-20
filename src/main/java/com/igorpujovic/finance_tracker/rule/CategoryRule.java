package com.igorpujovic.finance_tracker.rule;

import com.igorpujovic.finance_tracker.category.Category;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * Mapped in step 1 only so that deleting a category can see whether any rule still
 * points at it. Matching, priority and the endpoints arrive in step 3.
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
        this.keyword = keyword;
        this.category = category;
        this.priority = priority;
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
}
