/**
 * Rendering money, dates and percentages.
 *
 * The rule the whole file exists to keep: **the sign comes from the server and
 * is never removed.** An expense is a negative number, and nothing else marks
 * it as one (ADR-0002). There is no `Math.abs` in this project, and a function
 * that renders "2.450,80 RSD" for a value of `-2450.80` would be describing a
 * different transaction than the one it was given.
 */

const RSD = new Intl.NumberFormat('sr-RS', {
  style: 'currency',
  currency: 'RSD',
  minimumFractionDigits: 2,
  maximumFractionDigits: 2,
});

const PLAIN = new Intl.NumberFormat('sr-RS', {
  minimumFractionDigits: 2,
  maximumFractionDigits: 2,
});

/** A dinar amount, signed, with its currency. */
export function money(amount: number): string {
  return RSD.format(amount);
}

/** A dinar amount, signed, without the currency — for dense table columns. */
export function amount(value: number): string {
  return PLAIN.format(value);
}

/**
 * An amount in the currency it was spent in. Used only to show what a foreign
 * transaction originally said; reports never use it.
 */
export function foreignAmount(value: number, currency: string): string {
  return `${PLAIN.format(value)} ${currency}`;
}

/**
 * A change, with an explicit `+` on the improvements.
 *
 * The sign is already meaningful and needs no interpretation here: `change` is
 * `current - previous` on signed amounts, so a positive number means the
 * position improved whichever side of the ledger it is on — spending less on
 * Food and earning more Salary are both positive. The `+` is written in because
 * a bare "2.549,20" in a column of negatives reads as a quantity rather than as
 * a direction.
 */
export function signedAmount(value: number): string {
  const rendered = PLAIN.format(value);
  return value > 0 ? `+${rendered}` : rendered;
}

/**
 * A percentage change, or an em dash.
 *
 * `null` means the previous month was zero, which no percentage can describe —
 * it is not 0%, and it is not infinity. The report sends null on purpose and
 * this renders it as something a reader will not mistake for a number.
 */
export function percent(value: number | null): string {
  if (value === null || value === undefined) return '—';
  const rendered = PLAIN.format(value);
  return `${value > 0 ? '+' : ''}${rendered}%`;
}

/**
 * An ISO date as the statements write it, `dd.MM.yyyy` — the format the bank
 * exports and the one a Serbian reader expects.
 *
 * Split rather than passed to `new Date`, deliberately: `new Date('2026-11-04')`
 * is parsed as UTC midnight and then rendered in the viewer's zone, which west
 * of Greenwich shows the 3rd. A date here is a calendar date with no time and no
 * zone, so it is reformatted as text.
 */
export function date(iso: string): string {
  const [year, month, day] = iso.split('-');
  if (!year || !month || !day) return iso;
  return `${day}.${month}.${year}`;
}

/** An instant, in the viewer's own zone — this one really is a moment in time. */
export function dateTime(iso: string): string {
  const parsed = new Date(iso);
  if (Number.isNaN(parsed.getTime())) return iso;
  return parsed.toLocaleString('sr-RS', {
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  });
}

/**
 * What to call the row whose category is null.
 *
 * Uncategorized is a real state with real money in it, not an absence: the
 * category totals of a monthly report have to add up to the balance, so this row
 * is always rendered and never dropped.
 */
export const UNCATEGORIZED = 'Uncategorized';

export function categoryName(name: string | null): string {
  return name ?? UNCATEGORIZED;
}

/** `2026-11` → the month name and year, for headings. */
export function monthLabel(year: number, month: number): string {
  const name = new Date(Date.UTC(year, month - 1, 1)).toLocaleString('en-GB', {
    month: 'long',
    timeZone: 'UTC',
  });
  return `${name} ${year}`;
}

/** The first and last day of a month, as ISO dates. */
export function monthBounds(year: number, month: number): { from: string; to: string } {
  const lastDay = new Date(Date.UTC(year, month, 0)).getUTCDate();
  const pad = (n: number) => String(n).padStart(2, '0');
  return {
    from: `${year}-${pad(month)}-01`,
    to: `${year}-${pad(month)}-${pad(lastDay)}`,
  };
}

/** Today, as an ISO date in the viewer's own zone rather than in UTC. */
export function today(): string {
  const now = new Date();
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${now.getFullYear()}-${pad(now.getMonth() + 1)}-${pad(now.getDate())}`;
}

/**
 * Which way a number should read to a person: an improvement, a worsening, or
 * neither. Returned as a word rather than a colour so that the caller decides
 * how to show it, and so that this stays testable.
 */
export function direction(value: number): 'up' | 'down' | 'flat' {
  if (value > 0) return 'up';
  if (value < 0) return 'down';
  return 'flat';
}
