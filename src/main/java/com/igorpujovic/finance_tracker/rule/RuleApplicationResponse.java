package com.igorpujovic.finance_tracker.rule;

/**
 * What one run of re-apply did. {@code examined} is reported alongside
 * {@code categorized} because the interesting number is usually the difference: it is
 * the size of the queue that still needs a person's decision (ADR-0004).
 *
 * @param examined   uncategorized transactions the rules were tried against
 * @param categorized how many of them a rule claimed
 */
public record RuleApplicationResponse(int examined, int categorized) {
}
