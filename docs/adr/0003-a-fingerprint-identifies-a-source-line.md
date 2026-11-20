# A fingerprint identifies an imported source line, not a transaction

The fingerprint is a hash of a source line's date, description, amount and currency, and it
exists so that re-uploading a statement does not import it twice. It is set only on
transactions that came from a file. A transaction entered by hand has none, and the column is
nullable to say so, with `CHECK ((source = 'IMPORT') = (fingerprint IS NOT NULL))` holding the
two facts together. Editing an imported transaction afterwards does not recompute it.

The specification made the column mandatory, which forces the question of what a manual
transaction's fingerprint is. Computing it the same way makes the documented limitation —
"two genuinely identical payments on the same day count as one" — stop being a reasonable
trade and start being a defect. Protecting a person from re-uploading a file they already
uploaded is useful. Refusing to let them type in the second of two identical coffees, after
they have explicitly said "another one", is the system overriding a human about a fact the
human can see and it cannot. PostgreSQL treats nulls as distinct in a unique index, so
leaving manual rows unfingerprinted needs no exemption, no partial index predicate and no
special case in the duplicate query, which only ever matches imported rows anyway.

Freezing the fingerprint at import rather than recomputing it on edit is the other half.
Recomputing means that correcting a miskeyed amount makes the original source line
unrecognisable, so re-uploading that statement imports it again and the person ends up with
their correction *and* a fresh copy of the thing they corrected. Frozen, the fingerprint
keeps answering the only question it was ever asked: has this line of this file already been
seen? That the hash no longer matches the row's current values looks wrong for about as long
as it takes to remember what it identifies.

## Consequences

Deleting an imported transaction frees its fingerprint, so re-uploading that statement brings
the row back. This is the correct behaviour — the deletion removed one person's copy, it did
not make the payment stop having happened — but it surprises people, so it is documented
rather than left to be discovered.

Manual transactions are never deduplicated at all. Nothing stops a person entering the same
thing twice by accident, and the system will not notice.
