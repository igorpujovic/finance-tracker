package com.igorpujovic.finance_tracker.rule;

import com.igorpujovic.finance_tracker.category.Category;
import com.igorpujovic.finance_tracker.category.CategoryType;
import com.igorpujovic.finance_tracker.transaction.TransactionType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ADR-0004 as pure logic. Rule selection is arithmetic over a list, so there is
 * nothing here a real PostgreSQL would reveal (ADR-0006) — and keeping it out of the
 * container is what makes it cheap enough to state every case the ADR argues about.
 */
class CategorizationTest {

    private static final Category UTILITIES = new Category("Utilities", CategoryType.EXPENSE);
    private static final Category FOOD = new Category("Food", CategoryType.EXPENSE);
    private static final Category HEALTH = new Category("Health", CategoryType.EXPENSE);
    private static final Category REFUNDS = new Category("Refunds", CategoryType.INCOME);

    @Test
    @DisplayName("a refund does not take the expense category its merchant keyword points at")
    void leavesARefundUncategorized() {
        Categorization categorization = Categorization.by(List.of(
                new CategoryRule("EPS", UTILITIES, 50)));

        assertThat(categorization.categoryFor("EPS SNABDEVANJE POVRACAJ", TransactionType.INCOME))
                .isEmpty();
    }

    @Test
    @DisplayName("the highest priority wins among rules that all match and all fit")
    void ranksCompatibleRulesByPriority() {
        Categorization categorization = Categorization.by(List.of(
                new CategoryRule("MAXI", FOOD, 50),
                new CategoryRule("MAXI 245", HEALTH, 90)));

        assertThat(categorization.categoryFor("MAXI 245 BEOGRAD", TransactionType.EXPENSE))
                .contains(HEALTH);
    }

    @Test
    @DisplayName("a compatible rule wins over a higher-priority one that does not fit, rather than losing to it")
    void prefersACompatibleRuleToAHigherPriorityIncompatibleOne() {
        Categorization categorization = Categorization.by(List.of(
                new CategoryRule("EPS", UTILITIES, 100),
                new CategoryRule("POVRACAJ", REFUNDS, 10)));

        // Ranking first and vetoing afterwards would answer empty here: the EPS rule
        // outranks the refund rule and would be the one veto saw. ADR-0004 never lets
        // it into the running, so the refund rule is the only candidate and it wins.
        assertThat(categorization.categoryFor("EPS SNABDEVANJE POVRACAJ", TransactionType.INCOME))
                .contains(REFUNDS);
    }

    @Test
    @DisplayName("a keyword matches anywhere in the description, in any casing")
    void matchesCaseInsensitivelyAnywhere() {
        Categorization categorization = Categorization.by(List.of(new CategoryRule("maxi", FOOD, 50)));

        assertThat(categorization.categoryFor("MAXI 245 BEOGRAD", TransactionType.EXPENSE)).contains(FOOD);
        assertThat(categorization.categoryFor("PLACANJE U Maxi-ju", TransactionType.EXPENSE)).contains(FOOD);
    }

    @Test
    @DisplayName("a description no keyword appears in stays uncategorized")
    void leavesAnUnmatchedDescriptionUncategorized() {
        Categorization categorization = Categorization.by(List.of(new CategoryRule("MAXI", FOOD, 50)));

        assertThat(categorization.categoryFor("NEPOZNATI TRGOVAC", TransactionType.EXPENSE)).isEmpty();
    }

    @Test
    @DisplayName("with no rules at all nothing is categorized, and nothing fails")
    void answersNothingWithoutRules() {
        assertThat(Categorization.by(List.of()).categoryFor("MAXI 245", TransactionType.EXPENSE)).isEmpty();
    }

    @Test
    @DisplayName("two rules of equal priority resolve the same way whichever order they arrive in")
    void breaksATieTheSameWayEveryTime() {
        CategoryRule kiosk = new CategoryRule("KIOSK", FOOD, 50);
        CategoryRule duvan = new CategoryRule("DUVAN", HEALTH, 50);
        String description = "KIOSK DUVAN 12 BEOGRAD";

        // Priority cannot separate these two, and both match. Something still has to,
        // or the same statement imported twice would categorize differently: the
        // keyword that sorts first wins, and it wins from either input order.
        assertThat(Categorization.by(List.of(kiosk, duvan)).categoryFor(description, TransactionType.EXPENSE))
                .contains(HEALTH);
        assertThat(Categorization.by(List.of(duvan, kiosk)).categoryFor(description, TransactionType.EXPENSE))
                .contains(HEALTH);
    }

    @Test
    @DisplayName("a keyword stored with stray spaces still matches the merchant it names")
    void ignoresSurroundingSpaceInAKeyword() {
        Categorization categorization = Categorization.by(List.of(new CategoryRule("  MAXI  ", FOOD, 50)));

        assertThat(categorization.categoryFor("MAXI 245 BEOGRAD", TransactionType.EXPENSE)).contains(FOOD);
    }
}
