import { useRef, useState } from 'react';
import { Link } from 'react-router-dom';
import { useImports, useUploadStatement } from '../api/hooks';
import type { ImportBatch } from '../api/types';
import { dateTime } from '../format';
import { Pager } from '../components/Controls';
import { Empty, ErrorNotice, Loading } from '../components/Notices';

/**
 * Uploading a statement, and the standing record of every upload.
 *
 * The summary this page shows after an upload is the thing the project is built
 * around: a statement with unusable lines in it is a *successful* import of the
 * rest, not a failed request, and the four counts say exactly what became of the
 * file. Rejected lines are kept verbatim and are one click away.
 */
export function Import() {
  const [page, setPage] = useState(0);
  const [over, setOver] = useState(false);
  const fileInput = useRef<HTMLInputElement>(null);

  const imports = useImports(page);
  const upload = useUploadStatement();

  const send = (file: File | undefined) => {
    if (!file) return;
    upload.reset();
    upload.mutate(file);
  };

  return (
    <>
      <div className="page-head">
        <div>
          <h1>Import</h1>
          <p>
            A bank statement as CSV: UTF-8, a header row, and four columns — date as dd.MM.yyyy,
            description, amount with a dot and a minus for expenses, and a 3-letter currency code.
          </p>
        </div>
      </div>

      <ErrorNotice error={upload.error} />

      <div
        className={over ? 'dropzone over' : 'dropzone'}
        onDragOver={(event) => {
          event.preventDefault();
          setOver(true);
        }}
        onDragLeave={() => setOver(false)}
        onDrop={(event) => {
          event.preventDefault();
          setOver(false);
          send(event.dataTransfer.files[0]);
        }}
      >
        <p>
          <strong>Drop a statement here</strong>, or
        </p>
        <div className="btn-row" style={{ justifyContent: 'center' }}>
          <button
            type="button"
            className="primary"
            disabled={upload.isPending}
            onClick={() => fileInput.current?.click()}
          >
            {upload.isPending ? 'Importing…' : 'Choose a file'}
          </button>
        </div>
        <input
          ref={fileInput}
          type="file"
          accept=".csv,text/csv"
          className="sr-only"
          onChange={(event) => {
            send(event.target.files?.[0]);
            // Cleared so that choosing the same file twice fires a change event
            // the second time — re-uploading a statement is a legitimate thing
            // to do, and its duplicate lines are counted rather than refused.
            event.target.value = '';
          }}
        />
        <p className="hint">Up to 10 MB. Nothing is written until the whole file has been read.</p>
      </div>

      {upload.data && <UploadSummary batch={upload.data} />}

      <h2 style={{ margin: '1.5rem 0 0.75rem' }}>Past imports</h2>

      <ErrorNotice error={imports.error} />

      <div className="card">
        {imports.isPending && <Loading what="Reading the history" />}

        {imports.data && imports.data.content.length === 0 && (
          <Empty>No statement has been imported yet.</Empty>
        )}

        {imports.data && imports.data.content.length > 0 && (
          <>
            <div className="table-wrap">
              <table>
                <thead>
                  <tr>
                    <th scope="col">File</th>
                    <th scope="col">Imported</th>
                    <th scope="col" className="num">
                      Lines
                    </th>
                    <th scope="col" className="num">
                      Imported
                    </th>
                    <th scope="col" className="num">
                      Duplicate
                    </th>
                    <th scope="col" className="num">
                      Rejected
                    </th>
                    <th scope="col" />
                  </tr>
                </thead>
                <tbody>
                  {imports.data.content.map((batch) => (
                    <tr key={batch.id}>
                      <td>
                        <Link to={`/import/${batch.id}`}>{batch.fileName}</Link>
                      </td>
                      <td style={{ whiteSpace: 'nowrap' }}>{dateTime(batch.importedAt)}</td>
                      <td className="num">{batch.totalRows}</td>
                      <td className="num">{batch.importedRows}</td>
                      <td className="num">{batch.duplicateRows || ''}</td>
                      <td className="num">
                        {batch.rejectedRows > 0 ? (
                          <strong style={{ color: 'var(--expense)' }}>{batch.rejectedRows}</strong>
                        ) : (
                          ''
                        )}
                      </td>
                      <td>
                        <Link to={`/import/${batch.id}`}>Details</Link>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
            <Pager
              page={imports.data.page}
              totalPages={imports.data.totalPages}
              totalElements={imports.data.totalElements}
              noun="imports"
              onPage={setPage}
            />
          </>
        )}
      </div>
    </>
  );
}

function UploadSummary({ batch }: { batch: ImportBatch }) {
  const clean = batch.rejectedRows === 0 && batch.duplicateRows === 0;

  return (
    <div className={clean ? 'notice ok' : 'notice unavailable'} style={{ marginTop: '1rem' }}>
      <strong>
        {batch.fileName}: {batch.totalRows} line{batch.totalRows === 1 ? '' : 's'},{' '}
        {batch.importedRows} imported
        {batch.duplicateRows > 0 && `, ${batch.duplicateRows} duplicate`}
        {batch.rejectedRows > 0 && `, ${batch.rejectedRows} rejected`}.
      </strong>{' '}
      {batch.rejectedRows > 0 && (
        <>
          A rejected line never stops an import —{' '}
          <Link to={`/import/${batch.id}`}>see what was refused and why</Link>.
        </>
      )}
      {batch.rejectedRows === 0 && batch.duplicateRows > 0 && (
        <>
          A duplicate is a source line already imported; nothing was wrong with it, so it was
          counted and skipped. <Link to={`/import/${batch.id}`}>Details</Link>.
        </>
      )}
      {clean && (
        <>
          Every line became a transaction. <Link to={`/import/${batch.id}`}>Details</Link>.
        </>
      )}
    </div>
  );
}
