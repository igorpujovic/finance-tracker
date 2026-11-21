import { useState } from 'react';
import { Link } from 'react-router-dom';
import {
  useCategories,
  useChangeCategory,
  useCreateTransaction,
  useDeleteTransaction,
  useTransactions,
  useUpdateTransaction,
} from '../api/hooks';
import type { Transaction, TransactionFilters, TransactionRequest } from '../api/types';
import { date as formatDate, foreignAmount, today } from '../format';
import { Money } from '../components/Money';
import { CategoryPicker, ConfirmButton, Pager } from '../components/Controls';
import { Empty, ErrorNotice, FieldError, Loading, isInvalid } from '../components/Notices';

const EMPTY_FORM: TransactionRequest = {
  date: today(),
  description: '',
  amount: 0,
  currency: 'RSD',
  categoryId: null,
};

/**
 * The ledger: every transaction, filtered, with the ways of correcting one.
 *
 * Two ways of changing a category exist here and they are not redundant. The
 * dropdown in each row is `PATCH /{id}/category`, which can change nothing else
 * — and that matters for an imported foreign row, because the full `PUT` has to
 * restate the amount and currency, and a `PUT` that got the currency wrong would
 * silently restate a −9.99 EUR line as −9.99 RSD. Re-filing a transaction is the
 * common operation, so it is the one that cannot do collateral damage.
 */
export function Transactions() {
  const [filters, setFilters] = useState<TransactionFilters>({ page: 0, size: 20 });
  const [editing, setEditing] = useState<Transaction | null>(null);
  const [adding, setAdding] = useState(false);

  const categories = useCategories();
  const transactions = useTransactions(filters);
  const changeCategory = useChangeCategory();
  const remove = useDeleteTransaction();

  const patch = (next: Partial<TransactionFilters>) =>
    // Any filter change resets to the first page: staying on page 4 of a
    // narrower result set is how a reader ends up looking at an empty table and
    // concluding there is nothing there.
    setFilters((current) => ({ ...current, ...next, page: 0 }));

  return (
    <>
      <div className="page-head">
        <div>
          <h1>Transactions</h1>
          <p>
            Newest first. Expenses are negative. The category dropdown in a row changes only the
            category, so an imported foreign amount cannot be restated by accident.
          </p>
        </div>
        <button
          type="button"
          className="primary"
          onClick={() => {
            setAdding((open) => !open);
            setEditing(null);
          }}
        >
          {adding ? 'Cancel' : 'Add transaction'}
        </button>
      </div>

      {adding && (
        <TransactionForm
          title="A transaction entered by hand"
          initial={EMPTY_FORM}
          categories={categories.data ?? []}
          onDone={() => setAdding(false)}
        />
      )}

      {editing && (
        <TransactionForm
          title={`Correcting #${editing.id}`}
          initial={{
            date: editing.date,
            description: editing.description,
            amount: editing.amount,
            currency: editing.currency,
            categoryId: editing.categoryId,
          }}
          editingId={editing.id}
          categories={categories.data ?? []}
          onDone={() => setEditing(null)}
        />
      )}

      <div className="card" style={{ marginBottom: '1rem' }}>
        <div className="card-body filters">
          <div>
            <label htmlFor="from">From</label>
            <input
              id="from"
              type="date"
              value={filters.from ?? ''}
              onChange={(e) => patch({ from: e.target.value || undefined })}
            />
          </div>
          <div>
            <label htmlFor="to">To</label>
            <input
              id="to"
              type="date"
              value={filters.to ?? ''}
              onChange={(e) => patch({ to: e.target.value || undefined })}
            />
          </div>
          <div>
            <label htmlFor="filter-category">Category</label>
            <select
              id="filter-category"
              value={filters.categoryId ?? ''}
              disabled={filters.uncategorized}
              onChange={(e) =>
                patch({ categoryId: e.target.value ? Number(e.target.value) : undefined })
              }
            >
              <option value="">Any</option>
              {(categories.data ?? []).map((category) => (
                <option key={category.id} value={category.id}>
                  {category.name}
                </option>
              ))}
            </select>
          </div>
          <div>
            <label htmlFor="filter-type">Type</label>
            <select
              id="filter-type"
              value={filters.type ?? ''}
              onChange={(e) =>
                patch({
                  type: e.target.value ? (e.target.value as 'INCOME' | 'EXPENSE') : undefined,
                })
              }
            >
              <option value="">Any</option>
              <option value="INCOME">Income</option>
              <option value="EXPENSE">Expense</option>
            </select>
          </div>
          <div className="checkbox" style={{ flex: '0 0 auto', paddingBottom: '0.4rem' }}>
            <input
              id="filter-uncategorized"
              type="checkbox"
              checked={Boolean(filters.uncategorized)}
              onChange={(e) =>
                // Asking for uncategorized rows and for a particular category at
                // once is a contradiction, so choosing one clears the other.
                patch({
                  uncategorized: e.target.checked || undefined,
                  categoryId: e.target.checked ? undefined : filters.categoryId,
                })
              }
            />
            <label htmlFor="filter-uncategorized">Uncategorized only</label>
          </div>
          <div style={{ flex: '0 0 auto', paddingBottom: '0.15rem' }}>
            <button type="button" onClick={() => setFilters({ page: 0, size: 20 })}>
              Clear
            </button>
          </div>
        </div>
      </div>

      <ErrorNotice error={transactions.error ?? changeCategory.error ?? remove.error} />

      <div className="card">
        {transactions.isPending && <Loading what="Reading the ledger" />}

        {transactions.data && transactions.data.content.length === 0 && (
          <Empty>No transaction matches these filters.</Empty>
        )}

        {transactions.data && transactions.data.content.length > 0 && (
          <>
            <div className="table-wrap">
              <table>
                <thead>
                  <tr>
                    <th scope="col">Date</th>
                    <th scope="col">Description</th>
                    <th scope="col" className="num">
                      Amount (RSD)
                    </th>
                    <th scope="col">Original</th>
                    <th scope="col">Category</th>
                    <th scope="col">Source</th>
                    <th scope="col" />
                  </tr>
                </thead>
                <tbody>
                  {transactions.data.content.map((transaction) => (
                    <tr key={transaction.id}>
                      <td style={{ whiteSpace: 'nowrap' }}>{formatDate(transaction.date)}</td>
                      <td>{transaction.description}</td>
                      <td className="num">
                        <Money value={transaction.amountRsd} />
                      </td>
                      <td className="mono" style={{ whiteSpace: 'nowrap' }}>
                        {/* Shown only when it says something the dinar column
                            does not — a dinar row would just repeat itself. */}
                        {transaction.currency === 'RSD'
                          ? ''
                          : foreignAmount(transaction.amount, transaction.currency)}
                      </td>
                      <td>
                        <CategoryPicker
                          categories={categories.data ?? []}
                          value={transaction.categoryId}
                          restrictTo={transaction.type}
                          disabled={changeCategory.isPending}
                          onChange={(categoryId) =>
                            changeCategory.mutate({ id: transaction.id, categoryId })
                          }
                        />
                      </td>
                      <td>
                        {transaction.source === 'IMPORT' && transaction.importBatchId !== null ? (
                          <Link to={`/import/${transaction.importBatchId}`} className="badge muted">
                            import #{transaction.importBatchId}
                          </Link>
                        ) : (
                          <span className="badge muted">manual</span>
                        )}
                      </td>
                      <td>
                        <span className="btn-row">
                          <button
                            type="button"
                            className="link"
                            onClick={() => {
                              setEditing(transaction);
                              setAdding(false);
                            }}
                          >
                            Edit
                          </button>
                          <ConfirmButton
                            disabled={remove.isPending}
                            onConfirm={() => remove.mutate(transaction.id)}
                          >
                            Delete
                          </ConfirmButton>
                        </span>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
            <Pager
              page={transactions.data.page}
              totalPages={transactions.data.totalPages}
              totalElements={transactions.data.totalElements}
              noun="transactions"
              onPage={(page) => setFilters((current) => ({ ...current, page }))}
            />
          </>
        )}
      </div>

      <p className="hint">
        Deleting a transaction is a correction of the database, not of the statement: the
        fingerprint goes with the row, so re-importing the same file brings it back.
      </p>
    </>
  );
}

/**
 * One form for both creating and correcting, because the request body is the
 * same shape either way — `TransactionRequest`, a full replacement.
 *
 * The category list offered narrows to the side of the ledger the amount's sign
 * puts it on, and the amount's sign is watched as it is typed: a category is
 * either an income or an expense category and can only take its own kind
 * (ADR-0004), so typing a minus in front of a salary has to drop a Salary
 * selection rather than send a request the server will refuse.
 */
function TransactionForm({
  title,
  initial,
  editingId,
  categories,
  onDone,
}: {
  title: string;
  initial: TransactionRequest;
  editingId?: number;
  categories: { id: number; name: string; type: 'INCOME' | 'EXPENSE' }[];
  onDone: () => void;
}) {
  const [form, setForm] = useState(initial);
  const create = useCreateTransaction();
  const update = useUpdateTransaction();
  const pending = create.isPending || update.isPending;
  const error = create.error ?? update.error;

  const side = form.amount === 0 ? undefined : form.amount > 0 ? 'INCOME' : 'EXPENSE';

  const submit = (event: React.FormEvent) => {
    event.preventDefault();
    const body: TransactionRequest = { ...form, currency: form.currency.toUpperCase() };
    if (editingId === undefined) {
      create.mutate(body, {
        onSuccess: () => {
          setForm(initial);
          onDone();
        },
      });
    } else {
      update.mutate({ id: editingId, body }, { onSuccess: onDone });
    }
  };

  return (
    <div className="card" style={{ marginBottom: '1rem' }}>
      <h2>{title}</h2>
      <form className="card-body" onSubmit={submit}>
        <ErrorNotice error={error} />
        <div className="form-row">
          <div className="field">
            <label htmlFor="t-date">Date</label>
            <input
              id="t-date"
              type="date"
              required
              value={form.date}
              aria-invalid={isInvalid(error, 'date') || undefined}
              onChange={(e) => setForm({ ...form, date: e.target.value })}
            />
            <FieldError error={error} field="date" />
          </div>
          <div className="field" style={{ flex: '3 1 240px' }}>
            <label htmlFor="t-description">Description</label>
            <input
              id="t-description"
              type="text"
              required
              maxLength={255}
              value={form.description}
              aria-invalid={isInvalid(error, 'description') || undefined}
              onChange={(e) => setForm({ ...form, description: e.target.value })}
            />
            <FieldError error={error} field="description" />
          </div>
          <div className="field">
            <label htmlFor="t-amount">Amount</label>
            <input
              id="t-amount"
              type="number"
              step="0.01"
              required
              value={form.amount}
              aria-invalid={isInvalid(error, 'amount') || undefined}
              onChange={(e) => {
                const next = Number(e.target.value);
                const flipped =
                  (next > 0 ? 'INCOME' : next < 0 ? 'EXPENSE' : undefined) !== side;
                setForm((current) => ({
                  ...current,
                  amount: next,
                  // The sign just moved the transaction to the other side of the
                  // ledger, so a category chosen for the old side is no longer
                  // legal and is dropped rather than sent.
                  categoryId: flipped ? null : current.categoryId,
                }));
              }}
            />
            <FieldError error={error} field="amount" />
          </div>
          <div className="field" style={{ flex: '0 1 90px' }}>
            <label htmlFor="t-currency">Currency</label>
            <input
              id="t-currency"
              type="text"
              required
              maxLength={3}
              style={{ textTransform: 'uppercase' }}
              value={form.currency}
              aria-invalid={isInvalid(error, 'currency') || undefined}
              onChange={(e) => setForm({ ...form, currency: e.target.value })}
            />
            <FieldError error={error} field="currency" />
          </div>
          <div className="field">
            <label htmlFor="t-category">Category</label>
            <CategoryPicker
              id="t-category"
              categories={categories}
              value={form.categoryId}
              restrictTo={side}
              invalid={isInvalid(error, 'categoryId')}
              onChange={(categoryId) => setForm({ ...form, categoryId })}
            />
            <FieldError error={error} field="categoryId" />
          </div>
        </div>
        <div className="btn-row" style={{ marginTop: '0.75rem' }}>
          <button type="submit" className="primary" disabled={pending}>
            {pending ? 'Saving…' : editingId === undefined ? 'Add' : 'Save'}
          </button>
          <button type="button" onClick={onDone} disabled={pending}>
            Cancel
          </button>
        </div>
        <p className="hint">
          A negative amount is an expense and a positive one is income; zero has no sign and is
          refused. A currency other than RSD is converted at the National Bank's rate for this
          date, which may need the bank to be reachable.
        </p>
      </form>
    </div>
  );
}
