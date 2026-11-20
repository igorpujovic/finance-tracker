package com.igorpujovic.finance_tracker.category;

public record CategoryResponse(Long id, String name, CategoryType type) {

    public static CategoryResponse of(Category category) {
        return new CategoryResponse(category.getId(), category.getName(), category.getType());
    }
}
