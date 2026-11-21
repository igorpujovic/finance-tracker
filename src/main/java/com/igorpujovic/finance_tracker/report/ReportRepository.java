package com.igorpujovic.finance_tracker.report;

import com.igorpujovic.finance_tracker.transaction.Transaction;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

/**
 * Every report is one aggregation query, written in SQL and returned as a projection.
 * <p>
 * Native rather than JPQL, and not by preference: the comparison is a full outer join,
 * which JPQL has no syntax for, and the monthly breakdown groups by a nullable
 * association so that the uncategorized bucket survives the grouping. Writing all of
 * them the same way keeps one dialect in one file instead of two half-expressible ones.
 * ADR-0006 is what makes that safe — these run against a real PostgreSQL in the suite,
 * so a query that does not parse fails the build.
 * <p>
 * ADR-0007 asks that queries name entity properties through the generated metamodel, so
 * that renaming a field breaks the build instead of a request, and these do not. Worth
 * being precise about what that costs: the protection comes from a compile-checked path
 * such as {@code root.get(Transaction_.date)}, and a {@code @Query} forfeits it in
 * either dialect — a JPQL {@code sum(t.amountRsd)} is a string literal exactly as
 * {@code sum(t.amount_rsd)} is. Criteria aggregation would keep it, and cannot express a
 * full outer join at all. So the choice here is not between checked and unchecked but
 * between two unchecked dialects, and the one that can state the query wins. What
 * narrows the gap is ADR-0006: a column renamed out from under these fails every report
 * test against the real schema, which is a louder failure than a stale metamodel
 * attribute.
 * <p>
 * It extends the bare {@link Repository} marker rather than {@code JpaRepository}: a
 * report never saves, deletes or finds by id, and inheriting thirty methods it must not
 * use would invite a caller to write through it.
 * <p>
 * Column aliases are double-quoted. PostgreSQL folds an unquoted identifier to lower
 * case, so {@code as totalRsd} arrives as {@code totalrsd} and matches no getter on the
 * projection; quoting makes the result label exactly the property name.
 */
interface ReportRepository extends Repository<Transaction, Long> {

    /**
     * The monthly breakdown. A {@code left join} rather than an inner one, and grouping
     * by {@code t.category_id} rather than {@code c.id}, so that the transactions no
     * rule claimed group together under a null instead of being joined away: the rows
     * this returns are a complete partition of the month.
     * <p>
     * Ordered by the signed total ascending, which puts the heaviest spending first and
     * income last (ADR-0002), with the category name breaking ties so that two equal
     * totals do not reorder between calls. An export has to be reproducible.
     */
    @Query(value = """
            select t.category_id                 as "categoryId",
                   c.name                        as "categoryName",
                   c.type                        as "categoryType",
                   round(sum(t.amount_rsd), 2)   as "totalRsd"
            from transactions t
            left join categories c on c.id = t.category_id
            where t.date >= :start and t.date < :endExclusive
            group by t.category_id, c.name, c.type
            order by sum(t.amount_rsd) asc, c.name asc nulls last
            """, nativeQuery = true)
    List<CategoryTotal> totalsPerCategory(@Param("start") LocalDate start,
                                          @Param("endExclusive") LocalDate endExclusive);

    /**
     * The month's balance and its two halves. No {@code group by}, so one row comes back
     * for an empty month too, and {@code coalesce} turns its nulls into zeros: a month
     * with nothing in it is a report of zeros rather than an absent one.
     * <p>
     * {@code case ... end} with no {@code else} yields null for the rows on the other
     * side of zero, and {@code sum} ignores nulls, so income and expenses are each a sum
     * over their own half without a second pass over the table.
     */
    @Query(value = """
            select round(coalesce(sum(case when t.amount_rsd > 0 then t.amount_rsd end), 0), 2) as "income",
                   round(coalesce(sum(case when t.amount_rsd < 0 then t.amount_rsd end), 0), 2) as "expenses",
                   round(coalesce(sum(t.amount_rsd), 0), 2)                                     as "balance"
            from transactions t
            where t.date >= :start and t.date < :endExclusive
            """, nativeQuery = true)
    MonthlyBalance balanceOf(@Param("start") LocalDate start,
                             @Param("endExclusive") LocalDate endExclusive);

    /**
     * The month-on-month comparison: a full outer join over the categories active in
     * either month, zero-filling whichever side is missing, so that a category which
     * stopped is reported rather than omitted.
     * <p>
     * It is written as the union of both key sets with a left join to each side rather
     * than with the {@code full outer join} keyword, for a reason PostgreSQL enforces.
     * The uncategorized bucket groups under a null {@code category_id}, and
     * {@code null = null} is not true, so a join on equality splits that bucket into two
     * half-rows — one with a zero current, one with a zero previous — which still sums
     * correctly and so looks right. The condition that does join it is
     * {@code is not distinct from}, and a {@code full outer join} on that is refused
     * outright: <em>FULL JOIN is only supported with merge-joinable or hash-joinable
     * join conditions</em>. {@code union} already treats two nulls as one key, which
     * leaves the uncategorized bucket joining to itself with no sentinel id standing in
     * for "no category".
     * <p>
     * {@code change} and {@code percent} are computed here rather than in Java because
     * the zero-filling they depend on happens here (ADR-0002): change is
     * {@code current - previous} over income and expense categories alike, and percent
     * divides by the <em>magnitude</em> of the previous total, so a positive number
     * always means the position improved. A previous total of zero yields no percent at
     * all rather than an infinity.
     * <p>
     * Ordered by change ascending, so the categories that moved against the account come
     * first.
     */
    @Query(value = """
            with current_month as (
                select t.category_id as category_id, sum(t.amount_rsd) as total
                from transactions t
                where t.date >= :currentStart and t.date < :currentEnd
                group by t.category_id
            ),
            previous_month as (
                select t.category_id as category_id, sum(t.amount_rsd) as total
                from transactions t
                where t.date >= :previousStart and t.date < :previousEnd
                group by t.category_id
            ),
            active as (
                select category_id from current_month
                union
                select category_id from previous_month
            ),
            compared as (
                select a.category_id                       as category_id,
                       round(coalesce(cur.total, 0), 2)    as current_total,
                       round(coalesce(prev.total, 0), 2)   as previous_total
                from active a
                left join current_month cur on cur.category_id is not distinct from a.category_id
                left join previous_month prev on prev.category_id is not distinct from a.category_id
            )
            select compared.category_id                                            as "categoryId",
                   c.name                                                          as "categoryName",
                   c.type                                                          as "categoryType",
                   compared.current_total                                          as "currentTotal",
                   compared.previous_total                                         as "previousTotal",
                   compared.current_total - compared.previous_total                as "change",
                   case
                       when compared.previous_total = 0 then null
                       else round((compared.current_total - compared.previous_total)
                                  / abs(compared.previous_total) * 100, 2)
                   end                                                             as "percent"
            from compared
            left join categories c on c.id = compared.category_id
            order by compared.current_total - compared.previous_total asc, c.name asc nulls last
            """, nativeQuery = true)
    List<CategoryComparison> comparison(@Param("currentStart") LocalDate currentStart,
                                        @Param("currentEnd") LocalDate currentEnd,
                                        @Param("previousStart") LocalDate previousStart,
                                        @Param("previousEnd") LocalDate previousEnd);

    /**
     * The largest expenses of a period. This is the query ADR-0002 names as the one that
     * looks plausible when it is wrong: the largest expenses are the <em>most
     * negative</em> dinar amounts, so the order is {@code asc}. Written {@code desc} it
     * returns the period's smallest expenses, in a list that still looks like a list of
     * expenses.
     * <p>
     * {@code amount_rsd < 0} rather than a type column, for the same reason: the sign is
     * the only record of what a transaction is. The id breaks ties so that two identical
     * amounts do not swap places between calls, and the window is inclusive at both ends
     * because {@code from} and {@code to} are dates a person typed.
     */
    @Query(value = """
            select t.id              as "id",
                   t.date            as "date",
                   t.description     as "description",
                   t.amount          as "amount",
                   t.currency        as "currency",
                   t.amount_rsd      as "amountRsd",
                   t.category_id     as "categoryId",
                   c.name            as "categoryName"
            from transactions t
            left join categories c on c.id = t.category_id
            where t.amount_rsd < 0
              and t.date >= :from
              and t.date <= :to
            order by t.amount_rsd asc, t.id asc
            limit :limit
            """, nativeQuery = true)
    List<TopExpense> topExpenses(@Param("from") LocalDate from,
                                 @Param("to") LocalDate to,
                                 @Param("limit") int limit);
}
