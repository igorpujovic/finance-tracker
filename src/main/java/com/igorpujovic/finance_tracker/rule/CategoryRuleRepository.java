package com.igorpujovic.finance_tracker.rule;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface CategoryRuleRepository extends JpaRepository<CategoryRule, Long> {

    boolean existsByCategoryId(Long categoryId);

    /**
     * The whole rule set in one query, categories included. Both callers need the
     * category of every rule — the list endpoint to name it, matching to read its type
     * — so the fetch join is what keeps either from being one extra query per rule.
     * <p>
     * The order is the one rules are consulted in, matching
     * {@code Categorization.STRONGEST_FIRST} key for key, so that the list a person
     * reads puts the rule that would win first. Matching re-imposes it rather than
     * trusting it, because {@link Categorization} can be handed rules from anywhere.
     */
    @Query("select r from CategoryRule r join fetch r.category "
            + "order by r.priority desc, r.keyword asc, r.id asc")
    List<CategoryRule> findAllWithCategory();
}
