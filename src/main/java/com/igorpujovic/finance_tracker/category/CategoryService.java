package com.igorpujovic.finance_tracker.category;

import com.igorpujovic.finance_tracker.common.ConflictException;
import com.igorpujovic.finance_tracker.common.NotFoundException;
import com.igorpujovic.finance_tracker.rule.CategoryRuleRepository;
import com.igorpujovic.finance_tracker.transaction.TransactionRepository;
import org.springframework.data.core.TypedPropertyPath;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
public class CategoryService {

    private final CategoryRepository categories;
    private final TransactionRepository transactions;
    private final CategoryRuleRepository rules;

    public CategoryService(CategoryRepository categories,
                           TransactionRepository transactions,
                           CategoryRuleRepository rules) {
        this.categories = categories;
        this.transactions = transactions;
        this.rules = rules;
    }

    @Transactional(readOnly = true)
    public List<Category> list() {
        // Method references rather than Sort.by("type", "name"): the compiler checks
        // the properties exist, so a rename is caught at build time instead of on the
        // first request. Sort.sort(Class) and Sort.TypedSort did this before Spring
        // Data 4.1 and are deprecated in favour of these typed paths.
        return categories.findAll(Sort.by(
                TypedPropertyPath.path(Category::getType),
                TypedPropertyPath.path(Category::getName)));
    }

    @Transactional(readOnly = true)
    public Category get(Long id) {
        return categories.findById(id)
                .orElseThrow(() -> new NotFoundException("Category", id));
    }

    public Category create(CategoryRequest request) {
        if (categories.existsByNameIgnoreCase(request.name())) {
            throw new ConflictException("A category named '" + request.name() + "' already exists");
        }
        return categories.save(new Category(request.name(), request.type()));
    }

    public Category update(Long id, CategoryRequest request) {
        Category category = get(id);
        if (categories.existsByNameIgnoreCaseAndIdNot(request.name(), id)) {
            throw new ConflictException("A category named '" + request.name() + "' already exists");
        }
        category.rename(request.name());
        category.retype(request.type());
        return category;
    }

    /**
     * A category still holding transactions or still named by a rule is not deleted.
     * Removing one would either orphan money or silently break a rule, and neither is
     * something to do on the strength of a DELETE with no body.
     */
    public void delete(Long id) {
        Category category = get(id);
        if (transactions.existsByCategoryId(id)) {
            throw new ConflictException("Category '" + category.getName()
                    + "' still has transactions and cannot be deleted");
        }
        if (rules.existsByCategoryId(id)) {
            throw new ConflictException("Category '" + category.getName()
                    + "' is still used by a categorization rule and cannot be deleted");
        }
        categories.delete(category);
    }
}
