import { amount as formatAmount, money as formatMoney, signedAmount } from '../format';

/**
 * A dinar amount, coloured by its sign.
 *
 * The sign is *printed*, always, and the colour only reinforces it — an expense
 * reads `-2.450,80` whether or not the reader can see the red. Colour is never
 * the only carrier of meaning here.
 *
 * There is no `type` prop and no way to ask for an unsigned rendering, because
 * the sign is the only thing that distinguishes income from an expense
 * (ADR-0002). A component that could hide it would be a component that could
 * turn a 95.000 dinar salary and an 95.000 dinar outgoing into the same string.
 */
export function Money({
  value,
  withCurrency = false,
  signed = false,
}: {
  value: number;
  /** Append "RSD". Off in table columns, where the header says it once. */
  withCurrency?: boolean;
  /** Force a `+` on positive values — for changes, where direction is the point. */
  signed?: boolean;
}) {
  const className = value >= 0 ? 'money income' : 'money expense';
  const text = signed
    ? signedAmount(value)
    : withCurrency
      ? formatMoney(value)
      : formatAmount(value);
  return <span className={className}>{text}</span>;
}
