package com.igorpujovic.finance_tracker.rule;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * A rule as asked for. {@code priority} is optional: a rule with no priority given is
 * the weakest kind, which is a sensible thing to want and not worth a field error —
 * the database column has the same default.
 * <p>
 * The keyword is taken as written and stripped by {@link CategoryRule} itself, so that
 * a rule has one keyword rather than one it was asked for and one it matches on.
 */
public record RuleRequest(
        @NotBlank @Size(max = 100) String keyword,
        @NotNull Long categoryId,
        Integer priority) {

    public int priorityOrDefault() {
        return priority == null ? 0 : priority;
    }
}
