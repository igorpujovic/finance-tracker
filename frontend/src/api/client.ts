import type { ApiErrorBody } from './types';

/**
 * The API returns one error shape from one `@RestControllerAdvice`, so this
 * client has one error class. Everything that renders a failure reads these
 * fields and nothing else.
 *
 * The statuses are not interchangeable, and the UI is expected to tell them
 * apart:
 *
 * - **400** is the request's fault, and `fieldErrors` says which field.
 * - **404** is a row that is not there.
 * - **409** is a conflict — deleting a category that still holds money.
 * - **503** is the National Bank being unreachable. It is *not* the user's
 *   mistake, nothing was written, and the cure is to try again later
 *   (ADR-0005). Reporting it as a validation problem would be a lie.
 */
export class ApiError extends Error {
  readonly status: number;
  readonly fieldErrors: Record<string, string>;
  readonly body: ApiErrorBody | null;

  constructor(status: number, message: string, body: ApiErrorBody | null) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
    this.body = body;
    this.fieldErrors = body?.fieldErrors ?? {};
  }

  /** True when the National Bank could not be reached. */
  get isRatesUnavailable(): boolean {
    return this.status === 503;
  }

  get isConflict(): boolean {
    return this.status === 409;
  }

  get isNotFound(): boolean {
    return this.status === 404;
  }

  /** True when the server named at least one offending field. */
  get hasFieldErrors(): boolean {
    return Object.keys(this.fieldErrors).length > 0;
  }
}

/**
 * Relative, always. The client and the API are one origin on one port, in
 * production because Maven puts the built client in the jar, and in development
 * because the Vite dev server proxies this prefix (ADR-0008). Making this
 * absolute would be the first step towards needing CORS, which this project
 * does not have and does not need.
 */
const BASE = '/api';

type Query = Record<string, string | number | boolean | undefined | null>;

/**
 * Drops absent parameters rather than sending them empty. `?categoryId=` is not
 * the same request as one with no `categoryId`: Spring would try to bind the
 * blank and answer 400, so an unset filter must not appear in the URL at all.
 */
export function queryString(params: Query): string {
  const search = new URLSearchParams();
  for (const [key, value] of Object.entries(params)) {
    if (value === undefined || value === null || value === '') continue;
    search.set(key, String(value));
  }
  const rendered = search.toString();
  return rendered ? `?${rendered}` : '';
}

async function errorFrom(response: Response): Promise<ApiError> {
  let body: ApiErrorBody | null = null;
  try {
    body = (await response.json()) as ApiErrorBody;
  } catch {
    // A failure with no JSON body at all — a proxy, or the server being down
    // before it could answer. There is nothing to read, and the status is the
    // only thing we honestly know.
    body = null;
  }
  const message =
    body?.message?.trim() ||
    `The request failed with ${response.status} ${response.statusText}`.trim();
  return new ApiError(response.status, message, body);
}

interface RequestOptions {
  method?: string;
  body?: unknown;
  /** Sent as multipart/form-data; `body` is ignored when this is present. */
  formData?: FormData;
}

/**
 * The one place a request is made. Returns parsed JSON, or `undefined` for the
 * 204s that `DELETE` answers with.
 */
export async function request<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const { method = 'GET', body, formData } = options;

  const init: RequestInit = { method, headers: {} };

  if (formData) {
    // Deliberately no Content-Type: the browser has to set it, because only the
    // browser knows the multipart boundary it generated.
    init.body = formData;
  } else if (body !== undefined) {
    init.headers = { 'Content-Type': 'application/json' };
    init.body = JSON.stringify(body);
  }

  const response = await fetch(`${BASE}${path}`, init);

  if (!response.ok) throw await errorFrom(response);

  if (response.status === 204 || response.headers.get('Content-Length') === '0') {
    return undefined as T;
  }

  return (await response.json()) as T;
}

/**
 * The URL of a file the browser should download itself, rather than one this
 * client fetches and re-serves. Same origin means the browser honours the
 * server's `Content-Disposition` and keeps the byte-order mark the export
 * deliberately carries — a blob round-trip through JavaScript is where both of
 * those get lost.
 */
export function downloadUrl(path: string, params: Query = {}): string {
  return `${BASE}${path}${queryString(params)}`;
}
