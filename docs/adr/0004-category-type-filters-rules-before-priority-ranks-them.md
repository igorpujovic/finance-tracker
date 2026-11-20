# A rule's category type filters the candidates before priority ranks them

Two rules in the specification contradict each other. One says that when several category
rules match a description, the highest priority wins. The other says an expense can only take
an expense category and income only an income category. When the highest-priority match is of
the wrong type, both cannot hold.

Rules whose category cannot legally take the transaction are therefore never candidates at
all. Priority then ranks whatever remains, and a transaction with no compatible match stays
uncategorized. The type check is a filter applied first, not a veto applied last.

The case is not hypothetical, and it is not rare: it is refunds. A rule matching `EPS` to
Utilities is the obvious thing to write, because `EPS SNABDEVANJE` is the electricity bill.
When the power company refunds an overpayment, the line still says `EPS SNABDEVANJE` and the
amount is now positive, so the highest-priority match points at an expense category and the
transaction is income. Every merchant keyword eventually meets a refund, a reversal or a
chargeback, and the description almost always still carries the merchant's name.

Ranking first and vetoing afterwards was rejected because it discards a perfectly good
lower-priority compatible rule for no gain, and produces behaviour nobody can reason about:
deleting an unrelated high-priority rule would cause transactions to become categorized.
Dropping the type restriction for automatic categorization was rejected for a subtler reason.
It makes the per-category arithmetic come out right — a charge and its refund net to zero in
Utilities — but the refund also counts toward the month's income, so the same money is
reflected twice in different parts of one report, and `Category.type` stops meaning anything.

## Consequences

Refunds stay uncategorized until the person creates an income category for them, or an income
rule on a word like `POVRACAJ`. That is a real gap, and it is the right one to have: it makes
the uncategorized filter a genuine queue of things needing a decision rather than a decorative
option, and the money is never silently misfiled.
