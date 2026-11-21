import { describe, expect, it } from 'vitest';
import {
  amount,
  categoryName,
  date,
  direction,
  foreignAmount,
  money,
  monthBounds,
  monthLabel,
  percent,
  signedAmount,
} from './format';

/**
 * These pin the project's reading of its own numbers, not the output of
 * `Intl`. Where a test asserts on a formatted string it does so through a
 * property — "the minus survives", "the two renderings differ only by a sign" —
 * rather than by pasting a locale's exact spacing, so that a CLDR update does
 * not turn a correct client into a red build.
 */

describe('money and amounts', () => {
  it('keeps the minus sign on an expense', () => {
    // ADR-0002: the sign is the only thing marking an expense as one, so a
    // formatter that drops it has changed what the number means.
    expect(money(-2450.8)).toContain('-');
    expect(amount(-2450.8)).toContain('-');
  });

  it('does not mark income with a minus', () => {
    expect(amount(95000)).not.toContain('-');
  });

  it('renders the same magnitude differently for income and expense', () => {
    expect(amount(2450.8)).not.toEqual(amount(-2450.8));
  });

  it('keeps two decimal places, so 4870 is not shown as 4870', () => {
    expect(amount(-4870)).toMatch(/[.,]00$/);
  });

  it('names the currency of a foreign amount and keeps its sign', () => {
    const rendered = foreignAmount(-9.99, 'EUR');
    expect(rendered).toContain('EUR');
    expect(rendered).toContain('-');
  });
});

describe('signedAmount', () => {
  it('writes an explicit plus on an improvement', () => {
    // The comparison report's `change` is positive when the position improved,
    // on both sides of the ledger. In a column of negatives a bare "2.549,20"
    // reads as a quantity, so the direction is written in.
    expect(signedAmount(2549.2).startsWith('+')).toBe(true);
  });

  it('leaves the minus to speak for itself on a worsening', () => {
    expect(signedAmount(-1174.84).startsWith('+')).toBe(false);
    expect(signedAmount(-1174.84)).toContain('-');
  });

  it('does not put a plus on zero, which is not an improvement', () => {
    expect(signedAmount(0).startsWith('+')).toBe(false);
  });
});

describe('percent', () => {
  it('renders null as an em dash, not as zero and not as infinity', () => {
    // null means the previous month was zero, which no percentage describes.
    // Showing 0% would claim nothing changed; showing ∞ would claim too much.
    expect(percent(null)).toBe('—');
    expect(percent(null)).not.toContain('0');
  });

  it('marks an improvement with a plus', () => {
    expect(percent(50.98)).toContain('+');
    expect(percent(50.98)).toContain('%');
  });

  it('keeps the minus on a worsening', () => {
    expect(percent(-100)).toContain('-');
    expect(percent(-100)).not.toContain('+');
  });

  it('distinguishes income lost from spending that stopped', () => {
    // Both mean "nothing this month"; the sign says which of the two it was.
    expect(percent(-100)).not.toEqual(percent(100));
  });
});

describe('date', () => {
  it('writes an ISO date the way the statements do', () => {
    expect(date('2026-11-04')).toBe('04.11.2026');
  });

  it('does not shift the day across a timezone', () => {
    // `new Date('2026-11-04')` parses as UTC midnight and renders as the 3rd
    // anywhere west of Greenwich. A transaction date is a calendar date with
    // no time in it, so it is reformatted as text and never goes near Date.
    expect(date('2026-01-01')).toBe('01.01.2026');
    expect(date('2026-12-31')).toBe('31.12.2026');
  });

  it('hands back anything it cannot read, rather than inventing a date', () => {
    expect(date('not a date')).toBe('not a date');
  });
});

describe('categoryName', () => {
  it('names the null category rather than leaving a blank', () => {
    // Uncategorized is a real state with real money in it, not an absence.
    expect(categoryName(null)).toBe('Uncategorized');
  });

  it('passes a real name through untouched', () => {
    expect(categoryName('Food')).toBe('Food');
  });
});

describe('monthLabel and monthBounds', () => {
  it('labels a month by name and year', () => {
    expect(monthLabel(2026, 11)).toBe('November 2026');
  });

  it('bounds a 30-day month', () => {
    expect(monthBounds(2026, 11)).toEqual({ from: '2026-11-01', to: '2026-11-30' });
  });

  it('bounds a 31-day month', () => {
    expect(monthBounds(2026, 1)).toEqual({ from: '2026-01-01', to: '2026-01-31' });
  });

  it('knows February in a leap year from February in an ordinary one', () => {
    expect(monthBounds(2024, 2).to).toBe('2024-02-29');
    expect(monthBounds(2026, 2).to).toBe('2026-02-28');
  });
});

describe('direction', () => {
  it('reads a positive number as an improvement and a negative one as not', () => {
    expect(direction(2549.2)).toBe('up');
    expect(direction(-4870)).toBe('down');
    expect(direction(0)).toBe('flat');
  });
});
