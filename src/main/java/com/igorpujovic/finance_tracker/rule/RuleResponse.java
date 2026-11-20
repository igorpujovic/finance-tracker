package com.igorpujovic.finance_tracker.rule;

import com.igorpujovic.finance_tracker.category.Category;
import com.igorpujovic.finance_tracker.category.CategoryType;

/**
 * The category's type is part of the rule's response because it is half of what
 * decides whether the rule ever applies (ADR-0004): a reader looking at a rule that
 * never fires needs to see that it points at the wrong side of the ledger.
 */
public record RuleResponse(
        Long id,
        String keyword,
        Long categoryId,
        String categoryName,
        CategoryType categoryType,
        int priority) {

    public static RuleResponse of(CategoryRule rule) {
        Category category = rule.getCategory();
        return new RuleResponse(
                rule.getId(),
                rule.getKeyword(),
                category.getId(),
                category.getName(),
                category.getType(),
                rule.getPriority());
    }
}
