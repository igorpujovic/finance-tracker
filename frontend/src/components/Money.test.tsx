import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { Money } from './Money';

describe('Money', () => {
  it('prints the minus on an expense, so colour is never the only signal', () => {
    // About one man in twelve cannot reliably tell the income green from the
    // expense red. The sign is what they read instead, so it has to be in the
    // text and not only in the class.
    render(<Money value={-2450.8} />);
    expect(screen.getByText(/-/)).toBeInTheDocument();
  });

  it('marks an expense and income with different classes', () => {
    const { container: expense } = render(<Money value={-1} />);
    const { container: income } = render(<Money value={1} />);
    expect(expense.querySelector('.money')?.className).toContain('expense');
    expect(income.querySelector('.money')?.className).toContain('income');
  });

  it('treats zero as income rather than colouring it as a loss', () => {
    // Zero has no sign and the API refuses to store it, but a report total can
    // still be 0.00 for a category that stopped. It must not read as spending.
    const { container } = render(<Money value={0} />);
    expect(container.querySelector('.money')?.className).toContain('income');
  });

  it('adds the currency only when asked', () => {
    const { container: bare } = render(<Money value={95000} />);
    const { container: withCurrency } = render(<Money value={95000} withCurrency />);
    expect(bare.textContent).not.toContain('RSD');
    expect(withCurrency.textContent).toContain('RSD');
  });

  it('writes an explicit plus when rendering a change', () => {
    const { container } = render(<Money value={2549.2} signed />);
    expect(container.textContent?.startsWith('+')).toBe(true);
  });
});
