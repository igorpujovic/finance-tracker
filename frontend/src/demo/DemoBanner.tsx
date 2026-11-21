/**
 * Says what the demo is, on every page, without being dismissible.
 *
 * A demo that looks exactly like the real application and quietly is not is a
 * worse thing to publish than no demo: somebody will import a real statement
 * into it and wonder where the data went. The banner is therefore permanent
 * and explicit about the two things a visitor could otherwise get wrong —
 * nothing is stored, and the exchange rates are fixed rather than live.
 */
export function DemoBanner() {
  return (
    <div className="demo-banner" role="note">
      <strong>Demo.</strong> The real client, with the API faked in your browser and seeded from
      the project's sample statement. Everything works — import a CSV, edit, re-categorize — but
      nothing is saved, and a reload starts over.{' '}
      <a href="https://github.com/igorpujovic/finance-tracker">Source and the real stack →</a>
    </div>
  );
}
