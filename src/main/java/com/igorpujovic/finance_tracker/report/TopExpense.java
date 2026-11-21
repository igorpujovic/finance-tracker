package com.igorpujovic.finance_tracker.report;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One of a period's largest expenses. The original amount and currency are carried
 * alongside the dinar amount, because a 200 EUR expense is more recognisable to the
 * person who made it than the dinars it converted to — but the ranking is always on the
 * dinar amount, which is the only figure the whole report shares.
 */
public interface TopExpense {

    Long getId();

    LocalDate getDate();

    String getDescription();

    BigDecimal getAmount();

    String getCurrency();

    BigDecimal getAmountRsd();

    Long getCategoryId();

    String getCategoryName();
}
