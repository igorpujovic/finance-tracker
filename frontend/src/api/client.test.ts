import { afterEach, describe, expect, it, vi } from 'vitest';
import { ApiError, downloadUrl, queryString, request } from './client';

function respondWith(status: number, body: unknown, statusText = '') {
  return vi.fn().mockResolvedValue({
    ok: status >= 200 && status < 300,
    status,
    statusText,
    headers: new Headers(),
    json: async () => body,
  } as unknown as Response);
}

afterEach(() => {
  vi.unstubAllGlobals();
});

/**
 * The ApiError a call is expected to throw.
 *
 * Preferred over `.catch((e) => e)`, which types as `unknown` and — worse —
 * passes silently if the request unexpectedly succeeds, leaving the assertions
 * below to run against a resolved value instead of failing.
 */
async function failureOf(call: Promise<unknown>): Promise<ApiError> {
  try {
    await call;
  } catch (error) {
    if (error instanceof ApiError) return error;
    throw error;
  }
  throw new Error('expected the request to fail, but it resolved');
}

describe('queryString', () => {
  it('drops parameters that are not set', () => {
    // `?categoryId=` is not the same request as one with no categoryId: Spring
    // tries to bind the blank and answers 400, so an unset filter must not
    // appear in the URL at all.
    expect(queryString({ categoryId: undefined, type: null, from: '' })).toBe('');
  });

  it('keeps a zero, which is a real page number', () => {
    expect(queryString({ page: 0 })).toBe('?page=0');
  });

  it('keeps a false, which is a real answer to a boolean filter', () => {
    expect(queryString({ uncategorized: false })).toBe('?uncategorized=false');
  });

  it('renders several parameters', () => {
    expect(queryString({ year: 2026, month: 11 })).toBe('?year=2026&month=11');
  });
});

describe('downloadUrl', () => {
  it('builds a same-origin API path the browser can follow itself', () => {
    // Relative, so the browser honours Content-Disposition and the export's
    // byte-order mark rather than this client re-serving a blob.
    expect(downloadUrl('/reports/monthly/export', { year: 2026, month: 11 })).toBe(
      '/api/reports/monthly/export?year=2026&month=11',
    );
  });
});

describe('request', () => {
  it('returns parsed JSON on success', async () => {
    vi.stubGlobal('fetch', respondWith(200, [{ id: 1, name: 'Food', type: 'EXPENSE' }]));
    await expect(request('/categories')).resolves.toEqual([
      { id: 1, name: 'Food', type: 'EXPENSE' },
    ]);
  });

  it('throws an ApiError carrying the server s message on a 400', async () => {
    vi.stubGlobal(
      'fetch',
      respondWith(400, {
        status: 400,
        error: 'Bad Request',
        message: 'no exchange rate for ZWL on 2026-11-04',
      }),
    );

    const error = await failureOf(request('/transactions', { method: 'POST', body: {} }));

    expect(error).toBeInstanceOf(ApiError);
    expect(error.status).toBe(400);
    // The server's own wording, not a sentence invented here: the API is the
    // only thing that knows which currency and which date.
    expect(error.message).toBe('no exchange rate for ZWL on 2026-11-04');
  });

  it('exposes field errors so a form can put them on the right input', async () => {
    vi.stubGlobal(
      'fetch',
      respondWith(400, {
        status: 400,
        error: 'Bad Request',
        message: 'Validation failed',
        fieldErrors: { amount: 'must not be zero' },
      }),
    );

    const error = await failureOf(request('/transactions', { method: 'POST' }));

    expect(error.hasFieldErrors).toBe(true);
    expect(error.fieldErrors['amount']).toBe('must not be zero');
  });

  it('tells a 503 from the bank apart from the caller s mistake', async () => {
    // ADR-0005: an unreachable National Bank wrote nothing and is not the
    // reader's fault. The UI shows it differently, so the distinction has to
    // survive as far as the error object.
    vi.stubGlobal(
      'fetch',
      respondWith(503, {
        status: 503,
        error: 'Service Unavailable',
        message: 'the National Bank could not be reached',
      }),
    );

    const error = await failureOf(request('/imports'));

    expect(error.isRatesUnavailable).toBe(true);
    expect(error.isConflict).toBe(false);
    expect(error.hasFieldErrors).toBe(false);
  });

  it('recognises the conflict raised by deleting a category that holds money', async () => {
    vi.stubGlobal(
      'fetch',
      respondWith(409, { status: 409, error: 'Conflict', message: 'category still has money' }),
    );

    const error = await failureOf(request('/categories/3', { method: 'DELETE' }));

    expect(error.isConflict).toBe(true);
    expect(error.isRatesUnavailable).toBe(false);
  });

  it('still reports a failure whose body is not JSON at all', async () => {
    // A proxy, or the server dying before it could answer. The status is the
    // only thing honestly known, and it must not surface as a parse crash.
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue({
        ok: false,
        status: 502,
        statusText: 'Bad Gateway',
        headers: new Headers(),
        json: async () => {
          throw new SyntaxError('Unexpected token <');
        },
      } as unknown as Response),
    );

    const error = await failureOf(request('/categories'));

    expect(error).toBeInstanceOf(ApiError);
    expect(error.status).toBe(502);
    expect(error.message).toContain('502');
  });

  it('returns nothing for the 204 a delete answers with', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue({
        ok: true,
        status: 204,
        statusText: 'No Content',
        headers: new Headers(),
        json: async () => {
          throw new SyntaxError('no body');
        },
      } as unknown as Response),
    );

    await expect(request('/transactions/1', { method: 'DELETE' })).resolves.toBeUndefined();
  });

  it('lets the browser set the content type of an upload', async () => {
    // Only the browser knows the multipart boundary it generated, so setting
    // Content-Type by hand produces a body the server cannot parse.
    const fetchMock = respondWith(201, { id: 1 });
    vi.stubGlobal('fetch', fetchMock);

    const formData = new FormData();
    formData.append('file', new Blob(['date,description,amount,currency']), 'sample.csv');
    await request('/imports', { formData });

    const init = fetchMock.mock.calls[0]?.[1] as RequestInit;
    expect(init.headers).toEqual({});
    expect(init.body).toBe(formData);
  });

  it('sends JSON with a content type when given a body', async () => {
    const fetchMock = respondWith(201, { id: 1 });
    vi.stubGlobal('fetch', fetchMock);

    await request('/categories', { method: 'POST', body: { name: 'Food', type: 'EXPENSE' } });

    const [url, init] = fetchMock.mock.calls[0] as [string, RequestInit];
    expect(url).toBe('/api/categories');
    expect(init.headers).toEqual({ 'Content-Type': 'application/json' });
    expect(init.body).toBe('{"name":"Food","type":"EXPENSE"}');
  });
});
