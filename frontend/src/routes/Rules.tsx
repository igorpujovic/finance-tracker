import { useState } from 'react';
import {
  useApplyRules,
  useCategories,
  useCreateRule,
  useDeleteRule,
  useRules,
  useUpdateRule,
} from '../api/hooks';
import type { Rule } from '../api/types';
import { ConfirmButton, TypeBadge } from '../components/Controls';
import {
  Empty,
  ErrorNotice,
  FieldError,
  Loading,
  SuccessNotice,
  isInvalid,
} from '../components/Notices';

/**
 * Category rules: the automatic half of categorization.
 *
 * A rule is a keyword that claims any transaction whose description contains it.
 * Two things decide which rule wins, and in this order (ADR-0004): the
 * category's type has to be able to take the transaction at all, and only then
 * does priority rank the survivors. That is why each row shows the type of the
 * category it points at — a rule aimed at the wrong side of the ledger never
 * fires, and the table is where that is meant to become obvious.
 */
export function Rules() {
  const rules = useRules();
  const categories = useCategories();
  const create = useCreateRule();
  const update = useUpdateRule();
  const remove = useDeleteRule();
  const apply = useApplyRules();

  const [keyword, setKeyword] = useState('');
  const [categoryId, setCategoryId] = useState<number | ''>('');
  const [priority, setPriority] = useState('');
  const [editing, setEditing] = useState<Rule | null>(null);

  const submit = (event: React.FormEvent) => {
    event.preventDefault();
    if (categoryId === '') return;
    create.mutate(
      {
        keyword,
        categoryId,
        ...(priority === '' ? {} : { priority: Number(priority) }),
      },
      {
        onSuccess: () => {
          setKeyword('');
          setPriority('');
        },
      },
    );
  };

  return (
    <>
      <div className="page-head">
        <div>
          <h1>Rules</h1>
          <p>
            A keyword claims any transaction whose description contains it. Higher priority wins,
            but only among rules whose category can legally take the transaction.
          </p>
        </div>
        <button type="button" className="primary" disabled={apply.isPending} onClick={() => apply.mutate()}>
          {apply.isPending ? 'Applying…' : 'Apply to uncategorized'}
        </button>
      </div>

      {apply.data && (
        <SuccessNotice>
          <strong>
            {apply.data.categorized} of {apply.data.examined} uncategorized transaction
            {apply.data.examined === 1 ? '' : 's'} claimed by a rule.
          </strong>{' '}
          {apply.data.examined - apply.data.categorized > 0 && (
            <>
              The remaining {apply.data.examined - apply.data.categorized} still need a decision —
              a refund carries the merchant's name and no keyword can safely tell it from the
              expense it undoes.
            </>
          )}
        </SuccessNotice>
      )}

      <ErrorNotice error={apply.error} />

      <div className="card" style={{ marginBottom: '1rem' }}>
        <h2>Add a rule</h2>
        <form className="card-body" onSubmit={submit}>
          <ErrorNotice error={create.error} />
          <div className="form-row">
            <div className="field" style={{ flex: '2 1 180px' }}>
              <label htmlFor="r-keyword">Keyword</label>
              <input
                id="r-keyword"
                type="text"
                required
                maxLength={100}
                placeholder="MAXI"
                value={keyword}
                aria-invalid={isInvalid(create.error, 'keyword') || undefined}
                onChange={(event) => setKeyword(event.target.value)}
              />
              <FieldError error={create.error} field="keyword" />
            </div>
            <div className="field" style={{ flex: '2 1 180px' }}>
              <label htmlFor="r-category">Category</label>
              <select
                id="r-category"
                required
                value={categoryId}
                aria-invalid={isInvalid(create.error, 'categoryId') || undefined}
                onChange={(event) =>
                  setCategoryId(event.target.value === '' ? '' : Number(event.target.value))
                }
              >
                <option value="">Choose a category…</option>
                {(categories.data ?? []).map((category) => (
                  <option key={category.id} value={category.id}>
                    {category.name} ({category.type === 'INCOME' ? 'income' : 'expense'})
                  </option>
                ))}
              </select>
              <FieldError error={create.error} field="categoryId" />
            </div>
            <div className="field" style={{ flex: '0 1 120px' }}>
              <label htmlFor="r-priority">Priority</label>
              <input
                id="r-priority"
                type="number"
                placeholder="0"
                value={priority}
                aria-invalid={isInvalid(create.error, 'priority') || undefined}
                onChange={(event) => setPriority(event.target.value)}
              />
              <FieldError error={create.error} field="priority" />
            </div>
            <div className="field" style={{ flex: '0 0 auto' }}>
              <button type="submit" className="primary" disabled={create.isPending}>
                {create.isPending ? 'Adding…' : 'Add'}
              </button>
            </div>
          </div>
          <p className="hint">
            Priority is optional; a rule with none given is the weakest kind. Creating a rule does
            not categorize anything by itself — use "Apply to uncategorized" for that.
          </p>
        </form>
      </div>

      <ErrorNotice error={rules.error ?? update.error ?? remove.error} />

      <div className="card">
        {rules.isPending && <Loading what="Reading the rules" />}

        {rules.data?.length === 0 && <Empty>No rules yet.</Empty>}

        {rules.data && rules.data.length > 0 && (
          <div className="table-wrap">
            <table>
              <thead>
                <tr>
                  <th scope="col">Keyword</th>
                  <th scope="col">Category</th>
                  <th scope="col">Takes</th>
                  <th scope="col" className="num">
                    Priority
                  </th>
                  <th scope="col" />
                </tr>
              </thead>
              <tbody>
                {rules.data.map((rule) =>
                  editing?.id === rule.id ? (
                    <tr key={rule.id}>
                      <td>
                        <input
                          type="text"
                          value={editing.keyword}
                          maxLength={100}
                          aria-label="Keyword"
                          onChange={(event) =>
                            setEditing({ ...editing, keyword: event.target.value })
                          }
                        />
                      </td>
                      <td>
                        <select
                          value={editing.categoryId}
                          aria-label="Category"
                          onChange={(event) =>
                            setEditing({ ...editing, categoryId: Number(event.target.value) })
                          }
                        >
                          {(categories.data ?? []).map((category) => (
                            <option key={category.id} value={category.id}>
                              {category.name}
                            </option>
                          ))}
                        </select>
                      </td>
                      <td />
                      <td className="num">
                        <input
                          type="number"
                          value={editing.priority}
                          aria-label="Priority"
                          onChange={(event) =>
                            setEditing({ ...editing, priority: Number(event.target.value) })
                          }
                        />
                      </td>
                      <td>
                        <span className="btn-row">
                          <button
                            type="button"
                            className="primary"
                            disabled={update.isPending}
                            onClick={() =>
                              update.mutate(
                                {
                                  id: editing.id,
                                  body: {
                                    keyword: editing.keyword,
                                    categoryId: editing.categoryId,
                                    priority: editing.priority,
                                  },
                                },
                                { onSuccess: () => setEditing(null) },
                              )
                            }
                          >
                            {update.isPending ? 'Saving…' : 'Save'}
                          </button>
                          <button type="button" className="link" onClick={() => setEditing(null)}>
                            Cancel
                          </button>
                        </span>
                      </td>
                    </tr>
                  ) : (
                    <tr key={rule.id}>
                      <td className="mono">{rule.keyword}</td>
                      <td>{rule.categoryName}</td>
                      <td>
                        <TypeBadge type={rule.categoryType} />
                      </td>
                      <td className="num">{rule.priority}</td>
                      <td>
                        <span className="btn-row">
                          <button type="button" className="link" onClick={() => setEditing(rule)}>
                            Edit
                          </button>
                          <ConfirmButton
                            disabled={remove.isPending}
                            onConfirm={() => remove.mutate(rule.id)}
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
        Matching is case-insensitive on the description. A rule pointing at an expense category will
        never claim income, however high its priority — which is what keeps a refund out of the Food
        total, and also why a refund stays uncategorized until someone files it by hand.
      </p>
    </>
  );
}
