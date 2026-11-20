# Personal Finance Tracker

One person's bank statements, imported from files the bank exports, cleaned up and sorted
into categories, then summed into monthly reports. Everything is reported in Serbian dinars,
whatever currency it was spent in.

## Language

### Money

**Transaction**:
A single movement of money into or out of the account, on a date, with a description the
bank wrote.
_Avoid_: Entry, Record, Line item, Payment

**Expense**:
A transaction that takes money out. An expense is always a negative amount — nothing else
marks it as one.
_Avoid_: Debit, Outgoing, Spending, Cost

**Income**:
A transaction that brings money in, and always a positive amount.
_Avoid_: Credit, Incoming, Earning, Revenue

**Refund**:
Income that reverses an earlier expense, and the reason income and expenses cannot be told
apart by description alone: a refund usually carries the same merchant name as the expense
it undoes.
_Avoid_: Reversal, Chargeback, Return, Credit note

**Dinar amount**:
What a transaction is worth in RSD, using the rate that applied on its own date. Foreign
transactions keep their original amount and currency as well; reports only ever use the
dinar amount.
_Avoid_: Converted amount, Base amount, Normalized amount, Local amount

**Exchange rate**:
The official National Bank of Serbia value of one unit of a currency in dinars on a given
date. Rates exist only for working days; the rate in force on a weekend or holiday is the
most recent earlier one.
_Avoid_: FX rate, Conversion rate, Currency rate

**Balance**:
Income plus expenses over a period. Because expenses are negative, a balance is simply the
sum of the dinar amounts.
_Avoid_: Net, Total, Profit, Surplus

### Categorization

**Category**:
A named kind of spending or earning that transactions are sorted into, such as Food or
Salary. A category is either an income category or an expense category, never both.
_Avoid_: Tag, Label, Group, Bucket, Class

**Category rule**:
A keyword that assigns a category to any transaction whose description contains it. Rules
are the automatic half of categorization; assigning a category by hand is the other half.
_Avoid_: Mapping, Matcher, Pattern, Classifier, Filter

**Priority**:
Which category rule wins when more than one matches. Only rules whose category can legally
take the transaction are in the running at all.
_Avoid_: Weight, Rank, Order, Precedence

**Uncategorized**:
A transaction no rule claimed and nobody has assigned by hand. Uncategorized is a real state
with real money in it, not an absence — it appears in reports and has its own filter.
_Avoid_: Unassigned, Untagged, Unknown, Other, Misc

### Importing

**Statement**:
The file a bank exports describing account activity over a period. The thing a person
uploads.
_Avoid_: Export, Extract, Feed, Document

**Source line**:
One data row of an uploaded statement — the origin of a transaction, and the thing a
fingerprint identifies.
_Avoid_: Row, Record, Entry

**Import batch**:
One upload, and the standing record of what became of it: how many source lines arrived,
how many became transactions, how many were duplicates, how many were rejected. Kept so an
import can be explained months later.
_Avoid_: Upload, Job, Run, Session, Ingestion

**Fingerprint**:
What makes a source line recognisable if the same statement is uploaded again. It identifies
the line, not the transaction: correcting a transaction afterwards does not change it, and a
transaction entered by hand has none.
_Avoid_: Hash, Checksum, Digest, Signature, Key

**Duplicate**:
A source line already imported, whether by an earlier upload or earlier in the same file.
Duplicates are counted and skipped, never rejected — nothing was wrong with them.
_Avoid_: Repeat, Copy, Collision

**Rejected line**:
A source line that could not become a transaction, kept verbatim alongside the reason. One
rejected line never stops an import.
_Avoid_: Failure, Error row, Bad row, Invalid row, Discard

### Reporting

**Monthly report**:
One month's income, expenses and balance, broken down by category. The breakdown always
accounts for every transaction in the month, uncategorized ones included.
_Avoid_: Summary, Statement, Overview, Breakdown

**Comparison**:
A month set against the one before it, category by category, in dinars and percent. Covers
every category active in either month, so a category that stopped is as visible as one that
started.
_Avoid_: Delta, Trend, Variance, Change report
