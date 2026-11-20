# Money stays signed from the CSV to the report

A transaction has no stored income-or-expense marker. Its nature is the sign of its amount:
negative is an expense, positive is income, and zero is rejected everywhere rather than only
on import. The dinar amount carries the same sign, and every report sums signed values.

The alternative was a `type` column alongside an unsigned amount, which would have mirrored
the income-or-expense distinction that categories genuinely do carry. It was rejected because
bank statements are signed. Storing an unsigned amount means stripping the sign on the way in
and reattaching it in every aggregation, and `SUM` over the dinar amount stops being a valid
expression for a balance. Storing both a sign and a type means storing the same fact twice,
and an edit that flips one without the other leaves a row that is an expense by its amount
and income by its label.

Keeping one source of truth has consequences worth naming because they look like bugs:

The largest expenses are the *most negative* amounts, so that report orders ascending. Written
the obvious way, with `DESC`, it returns the smallest expenses and looks plausible enough to
survive review.

Reported expenses are negative numbers. A monthly report says `-18790.79`, not `18790.79`, so
that `income + expenses = balance` holds literally rather than after a sign flip the reader has
to know about.

In the month-on-month comparison, change is `current - previous` over both income and expense
categories, which gives the whole report a single consistent reading: **a positive change always
means the position improved.** Food moving from `-23450.00` to `-2450.80` is `+20999.20`, the
same shape of good news as a salary rising by the same amount, and no part of the calculation
has to branch on whether a category is income or expense — which matters, because the
uncategorized bucket has no category and therefore no type to branch on. The cost is one line
of explanation in the README, since "Food, +89.6%" naively reads as spending more.

## Consequences

Zero amounts have no sign and therefore no nature, so they cannot be allowed anywhere. The
specification only rejected them during import; here the rule is enforced both on the request
and as a `CHECK (amount <> 0)` in the schema, so the manual-entry endpoint cannot open the hole
the importer closes.

Currency conversion must multiply and never take an absolute value. A converter that returns
magnitudes would turn every foreign expense into income, and the only visible symptom is a
balance that is wrong by twice the foreign spend.
