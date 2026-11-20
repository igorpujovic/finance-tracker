package com.igorpujovic.finance_tracker.rule;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CategoryRuleRepository extends JpaRepository<CategoryRule, Long> {

    boolean existsByCategoryId(Long categoryId);
}
