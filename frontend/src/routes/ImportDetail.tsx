import { Link, useParams } from 'react-router-dom';
import { useImportBatch } from '../api/hooks';
import { dateTime } from '../format';
import { ErrorNotice, Loading } from '../components/Notices';

/**
 * One import batch, months later.
 *
 * This page is the reason import batches are stored at all: an upload keeps its
 * summary and every line it refused, verbatim, with the reason each one was
 * refused — so a statement can still be explained long after the file itself has
 * been lost.
 */
export function ImportDetail() {
  const params = useParams();
  const id = Number(params.id);
  const batch = useImportBatch(Number.isFinite(id) ? id : null);

  return (
    <>
      <div className="page-head">
        <div>
          <h1>Import #{params.id}</h1>
          <p>
            <Link to="/import">← All imports</Link>
          </p>
        </div>
      </div>

      <ErrorNotice error={batch.error} />
      {batch.isPending && <Loading what="Reading the batch" />}

      {batch.data && (
        <>
          <div className="grid grid-3" style={{ marginBottom: '1rem' }}>
            <div className="card stat">
              <div className="label">Source lines</div>
              <div className="value">{batch.data.totalRows}</div>
              <div className="note">{batch.data.fileName}</div>
            </div>
            <div className="card stat">
              <div className="label">Imported</div>
              <div className="value">{batch.data.importedRows}</div>
              <div className="note">became transactions</div>
            </div>
            <div className="card stat">
              <div className="label">Duplicate</div>
              <div className="value">{batch.data.duplicateRows}</div>
              <div className="note">already imported, skipped</div>
            </div>
            <div className="card stat">
              <div className="label">Rejected</div>
              <div className="value">{batch.data.rejectedRows}</div>
              <div className="note">could not be read</div>
            </div>
            <div className="card stat">
              <div className="label">Imported at</div>
              <div className="value" style={{ fontSize: '1.05rem' }}>
                {dateTime(batch.data.importedAt)}
              </div>
            </div>
          </div>

          {/*
            An empty `errors` array and an absent one are different answers, and
            the API distinguishes them: the detail endpoint always sends the
            array, so an empty one here really does mean nothing was refused.
          */}
          {batch.data.rejectedRows > 0 && (
            <div className="card">
              <h2>Rejected lines</h2>
              <div className="table-wrap">
                <table>
                  <thead>
                    <tr>
                      <th scope="col" className="num">
                        Line
                      </th>
                      <th scope="col">As the file wrote it</th>
                      <th scope="col">Why it was refused</th>
                    </tr>
                  </thead>
                  <tbody>
                    {(batch.data.errors ?? []).map((error) => (
                      <tr key={error.rowNumber}>
                        <td className="num">{error.rowNumber}</td>
                        <td>
                          {/* Verbatim: not re-quoted, not trimmed, not
                              prettified. Whatever is wrong with the line is
                              often only visible in the original text. */}
                          <code className="raw-line">{error.rawLine}</code>
                        </td>
                        <td>{error.reason}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
              <p className="hint" style={{ padding: '0 1rem 0.8rem' }}>
                Line numbers count the header as line 1. Correct the file and upload it again — the
                lines that already imported will be counted as duplicates rather than doubled.
              </p>
            </div>
          )}

          {batch.data.rejectedRows === 0 && (
            <div className="notice ok">
              Every line in this file could be read. {batch.data.duplicateRows > 0 &&
                `${batch.data.duplicateRows} of them had already been imported and were skipped.`}
            </div>
          )}

          <p className="hint">
            <Link to={`/transactions`}>Browse the transactions</Link> this import produced.
          </p>
        </>
      )}
    </>
  );
}
