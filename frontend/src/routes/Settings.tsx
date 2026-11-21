import { useRefreshRates } from '../api/hooks';
import { date as formatDate } from '../format';
import { ErrorNotice, SuccessNotice } from '../components/Notices';

/**
 * The one thing on this API that is neither money nor categorization: warming
 * today's exchange rates.
 *
 * Pressing this is never *required*. A scheduled job does it on working
 * mornings, and an import resolves whatever dates it is actually missing before
 * it opens a database transaction, so a statement from last year converts
 * correctly whether or not this was ever clicked (ADR-0005). The button exists
 * for the case where the bank was unreachable when it mattered.
 */
export function Settings() {
  const refresh = useRefreshRates();

  return (
    <>
      <div className="page-head">
        <div>
          <h1>Settings</h1>
          <p>One user, one ledger, no accounts — there is nothing here to configure but the rates.</p>
        </div>
      </div>

      <div className="card" style={{ marginBottom: '1.25rem' }}>
        <h2>Exchange rates</h2>
        <div className="card-body">
          <p style={{ marginTop: 0, color: 'var(--text-muted)', fontSize: '0.9rem' }}>
            Foreign amounts are converted to dinars at the official National Bank of Serbia rate for
            the transaction's own date, read from the Kurs API. Rates exist only for working days;
            the rate in force on a weekend or a holiday is the most recent earlier one, which is why
            there is no holiday calendar anywhere in this project.
          </p>

          <div className="btn-row">
            <button
              type="button"
              className="primary"
              disabled={refresh.isPending}
              onClick={() => {
                refresh.reset();
                refresh.mutate();
              }}
            >
              {refresh.isPending ? 'Asking the bank…' : "Download today's rates"}
            </button>
          </div>

          {refresh.data && (
            <div style={{ marginTop: '1rem' }}>
              <SuccessNotice>
                <strong>
                  {formatDate(refresh.data.asOf)}: the bank published {refresh.data.published}{' '}
                  currencies, {refresh.data.stored} of them new.
                </strong>{' '}
                {refresh.data.stored === 0 && (
                  <>
                    Nothing was stored because nothing was missing — the ordinary answer to a second
                    refresh on one day, and not a failure.
                  </>
                )}
              </SuccessNotice>
            </div>
          )}

          {refresh.error && (
            <div style={{ marginTop: '1rem' }}>
              <ErrorNotice error={refresh.error} />
            </div>
          )}

          <p className="hint">
            A scheduled job already does this on working mornings, and an import fetches any date it
            is missing on its own. This is here for when the bank was down at the time.
          </p>
        </div>
      </div>

      <div className="card">
        <h2>About this ledger</h2>
        <div className="card-body">
          <ul style={{ margin: 0, paddingLeft: '1.2rem', color: 'var(--text-muted)', fontSize: '0.9rem' }}>
            <li>
              Everything is reported in Serbian dinars, whatever currency it was spent in.
            </li>
            <li>
              Expenses are negative numbers. There is no type column: the sign of the amount is the
              only record of whether money came in or went out.
            </li>
            <li>
              Two identical payments on the same day count as one — a source line is recognised by
              its date, description, amount and currency, the only four things a bank export gives.
              A genuine second payment can be added by hand on the Transactions page.
            </li>
            <li>
              There is no authentication. This API is a single person's ledger and is not exposed to
              the internet by its compose file.
            </li>
          </ul>
          <p className="hint">
            The full reasoning for each of these is in the project's ADRs, and the API itself is
            documented at <a href="/scalar">/scalar</a> and{' '}
            <a href="/swagger-ui.html">/swagger-ui.html</a>.
          </p>
        </div>
      </div>
    </>
  );
}
