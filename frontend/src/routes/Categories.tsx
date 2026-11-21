import { useState } from 'react';
import {
  useCategories,
  useCreateCategory,
  useDeleteCategory,
  useUpdateCategory,
} from '../api/hooks';
import type { Category, CategoryType } from '../api/types';
import { ConfirmButton, TypeBadge } from '../components/Controls';
import { Empty, ErrorNotice, FieldError, Loading, isInvalid } from '../components/Notices';

/**
 * The categories transactions are sorted into.
 *
 * A category is an income category or an expense category and never both
 * (ADR-0004), which is why the type is chosen once here and shown on every row:
 * a rule pointing at the wrong side of the ledger is a rule that never fires,
 * and this is where that becomes visible.
 *
 * Deleting is allowed only for a category holding no money. The server answers
 * 409 otherwise, and that refusal is surfaced as-is rather than pre-empted by
 * hiding the button — the count of transactions in a category is not on this
 * page, and a disabled button with no explanation is worse than a clear refusal.
 */
export function Categories() {
  const categories = useCategories();
  const create = useCreateCategory();
  const update = useUpdateCategory();
  const remove = useDeleteCategory();

  const [name, setName] = useState('');
  const [type, setType] = useState<CategoryType>('EXPENSE');
  const [editing, setEditing] = useState<Category | null>(null);

  const submit = (event: React.FormEvent) => {
    event.preventDefault();
    create.mutate(
      { name, type },
      {
        onSuccess: () => {
          setName('');
        },
      },
    );
  };

  return (
    <>
      <div className="page-head">
        <div>
          <h1>Categories</h1>
          <p>
            Seven are seeded by the migrations, so categorization works on a first run. A category
            is one side of the ledger, never both.
          </p>
        </div>
      </div>

      <div className="card" style={{ marginBottom: '1rem' }}>
        <h2>Add a category</h2>
        <form className="card-body" onSubmit={submit}>
          <ErrorNotice error={create.error} />
          <div className="form-row">
            <div className="field" style={{ flex: '3 1 220px' }}>
              <label htmlFor="c-name">Name</label>
              <input
                id="c-name"
                type="text"
                required
                maxLength={50}
                value={name}
                aria-invalid={isInvalid(create.error, 'name') || undefined}
                onChange={(event) => setName(event.target.value)}
              />
              <FieldError error={create.error} field="name" />
            </div>
            <div className="field">
              <label htmlFor="c-type">Type</label>
              <select
                id="c-type"
                value={type}
                onChange={(event) => setType(event.target.value as CategoryType)}
              >
                <option value="EXPENSE">Expense</option>
                <option value="INCOME">Income</option>
              </select>
              <FieldError error={create.error} field="type" />
            </div>
            <div className="field" style={{ flex: '0 0 auto' }}>
              <button type="submit" className="primary" disabled={create.isPending}>
                {create.isPending ? 'Adding…' : 'Add'}
              </button>
            </div>
          </div>
        </form>
      </div>

      <ErrorNotice error={categories.error ?? update.error ?? remove.error} />

      <div className="card">
        {categories.isPending && <Loading what="Reading the categories" />}

        {categories.data?.length === 0 && <Empty>No categories yet.</Empty>}

        {categories.data && categories.data.length > 0 && (
          <div className="table-wrap">
            <table>
              <thead>
                <tr>
                  <th scope="col">Name</th>
                  <th scope="col">Type</th>
                  <th scope="col" />
                </tr>
              </thead>
              <tbody>
                {categories.data.map((category) =>
                  editing?.id === category.id ? (
                    <EditRow
                      key={category.id}
                      category={editing}
                      pending={update.isPending}
                      onChange={setEditing}
                      onCancel={() => setEditing(null)}
                      onSave={() =>
                        update.mutate(
                          { id: editing.id, body: { name: editing.name, type: editing.type } },
                          { onSuccess: () => setEditing(null) },
                        )
                      }
                    />
                  ) : (
                    <tr key={category.id}>
                      <td>{category.name}</td>
                      <td>
                        <TypeBadge type={category.type} />
                      </td>
                      <td>
                        <span className="btn-row">
                          <button
                            type="button"
                            className="link"
                            onClick={() => setEditing(category)}
                          >
                            Edit
                          </button>
                          <ConfirmButton
                            disabled={remove.isPending}
                            onConfirm={() => remove.mutate(category.id)}
                          >
                            Delete
                          </ConfirmButton>
                        </span>
                      </td>
                    </tr>
                  ),
                )}
              </tbody>
            </table>
          </div>
        )}
      </div>

      <p className="hint">
        A category that still holds transactions cannot be deleted — move the money first, or the
        server will refuse with a conflict. Changing a category's type is refused the same way when
        transactions of the other sign already sit in it.
      </p>
    </>
  );
}

function EditRow({
  category,
  pending,
  onChange,
  onCancel,
  onSave,
}: {
  category: Category;
  pending: boolean;
  onChange: (category: Category) => void;
  onCancel: () => void;
  onSave: () => void;
}) {
  return (
    <tr>
      <td>
        <input
          type="text"
          value={category.name}
          maxLength={50}
          aria-label="Category name"
          onChange={(event) => onChange({ ...category, name: event.target.value })}
        />
      </td>
      <td>
        <select
          value={category.type}
          aria-label="Category type"
          onChange={(event) => onChange({ ...category, type: event.target.value as CategoryType })}
        >
          <option value="EXPENSE">Expense</option>
          <option value="INCOME">Income</option>
        </select>
      </td>
      <td>
        <span className="btn-row">
          <button type="button" className="primary" disabled={pending} onClick={onSave}>
            {pending ? 'Saving…' : 'Save'}
          </button>
          <button type="button" className="link" onClick={onCancel}>
            Cancel
          </button>
        </span>
      </td>
    </tr>
  );
}
