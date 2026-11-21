import { useState } from 'react';
import type { Category, CategoryType } from '../api/types';
import { UNCATEGORIZED, monthLabel } from '../format';

/**
 * A category picker.
 *
 * `restrictTo` exists because a category is one side of the ledger or the other
 * and a transaction can only take its own side (ADR-0004). Offering Food for a
 * salary would be offering a choice the server is going to refuse, so when the
 * sign of the amount is known the other side is not listed at all.
 */
export function CategoryPicker({
  categories,
  value,
  onChange,
  restrictTo,
  id,
  includeUncategorized = true,
  disabled = false,
  invalid = false,
}: {
  categories: Category[];
  value: number | null;
  onChange: (categoryId: number | null) => void;
  /** Show only categories of this side. Undefined shows both. */
  restrictTo?: CategoryType | undefined;
  id?: string;
  includeUncategorized?: boolean;
  disabled?: boolean;
  invalid?: boolean;
}) {
  const offered = restrictTo ? categories.filter((c) => c.type === restrictTo) : categories;

  return (
    <select
      id={id}
      value={value ?? ''}
      disabled={disabled}
      aria-invalid={invalid || undefined}
      onChange={(event) => onChange(event.target.value === '' ? null : Number(event.target.value))}
    >
      {includeUncategorized && <option value="">{UNCATEGORIZED}</option>}
      {offered.map((category) => (
        <option key={category.id} value={category.id}>
          {category.name}
        </option>
      ))}
    </select>
  );
}

/** The INCOME / EXPENSE badge, and the word for a null type. */
export function TypeBadge({ type }: { type: CategoryType | null }) {
  if (type === null) return <span className="badge uncategorized">{UNCATEGORIZED}</span>;
  return <span className={`badge ${type.toLowerCase()}`}>{type}</span>;
}

/** A category name, or the marked name of the row that has none. */
export function CategoryLabel({ name }: { name: string | null }) {
  if (name === null) return <span className="badge uncategorized">{UNCATEGORIZED}</span>;
  return <>{name}</>;
}

/** Steps a year/month pair, carrying December to January properly. */
export function MonthPicker({
  year,
  month,
  onChange,
}: {
  year: number;
  month: number;
  onChange: (year: number, month: number) => void;
}) {
  const step = (by: number) => {
    // Through a zero-based month index so that stepping off either end of the
    // year carries into it, rather than asking for month 0 or month 13.
    const index = year * 12 + (month - 1) + by;
    onChange(Math.floor(index / 12), (index % 12) + 1);
  };

  return (
    <div className="month-picker">
      <button type="button" onClick={() => step(-1)} aria-label="Previous month">
        ←
      </button>
      <span className="current">{monthLabel(year, month)}</span>
      <button type="button" onClick={() => step(1)} aria-label="Next month">
        →
      </button>
    </div>
  );
}

export function Pager({
  page,
  totalPages,
  totalElements,
  onPage,
  noun,
}: {
  page: number;
  totalPages: number;
  totalElements: number;
  onPage: (page: number) => void;
  noun: string;
}) {
  if (totalElements === 0) return null;
  return (
    <div className="pager">
      <span>
        {totalElements} {noun}
        {totalPages > 1 && ` · page ${page + 1} of ${totalPages}`}
      </span>
      {totalPages > 1 && (
        <span className="btn-row">
          <button type="button" disabled={page === 0} onClick={() => onPage(page - 1)}>
            ← Newer
          </button>
          <button
            type="button"
            disabled={page >= totalPages - 1}
            onClick={() => onPage(page + 1)}
          >
            Older →
          </button>
        </span>
      )}
    </div>
  );
}

/**
 * A destructive action that asks first, inline.
 *
 * Deliberately not `window.confirm`: a native modal blocks the page, and in this
 * project's own browser-automation notes a native dialog is the thing that
 * wedges a session. Two clicks in the row itself, with the second one saying
 * what it will do.
 */
export function ConfirmButton({
  onConfirm,
  children,
  confirmLabel = 'Really delete',
  disabled = false,
}: {
  onConfirm: () => void;
  children: React.ReactNode;
  confirmLabel?: string;
  disabled?: boolean;
}) {
  const [armed, setArmed] = useState(false);

  if (!armed) {
    return (
      <button type="button" className="danger" disabled={disabled} onClick={() => setArmed(true)}>
        {children}
      </button>
    );
  }

  return (
    <span className="btn-row">
      <button
        type="button"
        className="danger"
        disabled={disabled}
        onClick={() => {
          setArmed(false);
          onConfirm();
        }}
      >
        {confirmLabel}
      </button>
      <button type="button" className="link" onClick={() => setArmed(false)}>
        Cancel
      </button>
    </span>
  );
}
