import { ApiError } from '../api/client';

/**
 * Turns any thrown error into the one notice the UI shows for it.
 *
 * The statuses are kept apart on purpose, because they are not the same news:
 *
 * - **503** is the National Bank being unreachable. Nothing was written, it is
 *   not the reader's mistake, and the cure is to try the same thing again later
 *   (ADR-0005). It gets its own styling so it does not read as a rejection.
 * - **409** is a conflict the reader can act on — a category that still holds
 *   money cannot be deleted until the money moves.
 * - **400** with `fieldErrors` belongs on the fields, not here; this renders the
 *   summary only, and `FieldError` puts the detail where the reader is typing.
 */
export function ErrorNotice({ error }: { error: unknown }) {
  if (!error) return null;

  if (error instanceof ApiError && error.isRatesUnavailable) {
    return (
      <div className="notice unavailable" role="alert">
        <strong>The National Bank could not be reached.</strong> Nothing was saved. Rates are read
        from kurs.resenje.org at the moment a foreign amount is converted — try again once it
        answers.
        <br />
        <span className="mono">{error.message}</span>
      </div>
    );
  }

  const message =
    error instanceof Error ? error.message : 'Something failed, and said nothing about what.';

  const fieldErrors = error instanceof ApiError ? error.fieldErrors : {};
  const hasFields = Object.keys(fieldErrors).length > 0;

  return (
    <div className="notice error" role="alert">
      <strong>{message}</strong>
      {hasFields && (
        <ul>
          {Object.entries(fieldErrors).map(([field, reason]) => (
            <li key={field}>
              <span className="mono">{field}</span> — {reason}
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}

export function SuccessNotice({ children }: { children: React.ReactNode }) {
  return (
    <div className="notice ok" role="status">
      {children}
    </div>
  );
}

/**
 * The server's complaint about one field, shown next to that field.
 *
 * The keys are the Java property names from Bean Validation, so they match the
 * request DTO rather than the input's label.
 */
export function FieldError({ error, field }: { error: unknown; field: string }) {
  if (!(error instanceof ApiError)) return null;
  const reason = error.fieldErrors[field];
  if (!reason) return null;
  return <div className="field-error">{reason}</div>;
}

/** Whether a field should be marked invalid, for `aria-invalid`. */
export function isInvalid(error: unknown, field: string): boolean {
  return error instanceof ApiError && Boolean(error.fieldErrors[field]);
}

export function Loading({ what = 'Loading' }: { what?: string }) {
  return (
    <div className="loading" role="status">
      {what}…
    </div>
  );
}

export function Empty({ children }: { children: React.ReactNode }) {
  return <div className="empty">{children}</div>;
}
