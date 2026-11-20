# Exchange rates are resolved before the import transaction opens

Importing a statement happens in three movements. The file is parsed and validated. Then the
distinct currency-and-date pairs the valid lines need are resolved — from the stored rates
where possible, from the Kurs API where there is a genuine gap. Only then does a database
transaction open, convert and write. No HTTP call is ever made while a transaction is held.

As specified, rates reached the database only through a scheduled job that saves the day's
rates and a manual endpoint that downloads today's. Nothing ever filled in history. A bank
statement is always historical — that is the entire point of importing one — so a foreign
line dated any day but today would find no rate and be rejected. The specification's own
sample file contains a euro line, and on all but one day of the year that file could not
import the euro line. Fetching a missing date on demand, which the Kurs API supports directly,
turns the scheduled job into a warm-up rather than the only source.

Doing that lazily, inside the transaction, was the obvious shape and is wrong at statement
scale. A month of card spending abroad spans around twenty-two working days, so a lazy
converter opens a database transaction and then makes up to twenty-two sequential HTTP calls
while holding it, with a connection-pool thread and a PostgreSQL transaction both waiting on
a third party. Collecting the distinct pairs first reduces that to the number of genuinely
missing dates, usually zero, and moves all of it outside the transaction.

The lookup is the most recent rate on or before the transaction's date, which implements the
weekend-and-holiday rule with no special case: Friday's rate simply is the most recent one on
or before Sunday. Rates fetched for a date the bank does not publish are stored under the date
the API attributes them to, never under the date that was asked for, so the table never claims
an official rate existed on a Sunday.

A failure to resolve a rate is then read in two ways rather than one. A currency the National
Bank does not publish is the line's own problem: that line is rejected with a reason naming the
currency and date, and the rest of the file imports. A transport failure — a timeout, a refused
connection, a 5xx — is not the line's problem, and treating it as one would write a permanent
audit record accusing fifty perfectly valid rows of being malformed because the network
blinked, in the one table whose whole purpose is to be trusted later. A transport failure
aborts the upload with `503` and a message saying rates are unavailable.

## Consequences

Aborting costs nothing and compensates for nothing, because the rate pre-pass runs before the
transaction opens: there is no partial write to undo. It also means a `503` import leaves no
import batch at all, so there is no half-finished upload in the history for someone to puzzle
over. The person re-uploads the same file when the network returns and gets a clean import,
with every line counted once.
