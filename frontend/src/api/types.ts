/**
 * The API's shapes, mirroring the Java records field for field. The source of
 * truth is `/v3/api-docs`; these are hand-written rather than generated so that
 * the nullability the Java side only implies is stated here explicitly, and so
 * that a reader of this client can see what an endpoint returns without running
 * the server.
 *
 * On money: every amount is `numeric(12, 2)` in PostgreSQL and `BigDecimal` in
 * Java, and Jackson writes it as a JSON number — so it arrives here as a double.
 * That is safe for this project's range and not a rounding bug waiting to
 * happen: ten whole digits and two decimals is at most 10^12 when scaled by 100,
 * and doubles hold integers exactly to 2^53 (~9 * 10^15). Do not "fix" this by
 * parsing amounts as strings; do not do arithmetic on them beyond what the
 * server already did.
 *
 * On signs: an expense is a negative number, and that is the only thing marking
 * it as one (ADR-0002). Nothing in this client may take an absolute value.
 */

export type CategoryType = 'INCOME' | 'EXPENSE';
export type TransactionType = 'INCOME' | 'EXPENSE';
export type TransactionSource = 'MANUAL' | 'IMPORT';

/** An ISO date, `yyyy-MM-dd`, as the API sends and receives it. */
export type IsoDate = string;

/** An ISO instant, as the API sends it. */
export type IsoInstant = string;

// --- the one error shape -------------------------------------------------

/**
 * Every failing endpoint returns this, from one `@RestControllerAdvice`.
 * `fieldErrors` is present only on a 400 that came from Bean Validation.
 */
export interface ApiErrorBody {
  timestamp: IsoInstant;
  status: number;
  error: string;
  message: string;
  fieldErrors?: Record<string, string>;
}

// --- paging --------------------------------------------------------------

export interface PageResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

// --- categories ----------------------------------------------------------

export interface Category {
  id: number;
  name: string;
  type: CategoryType;
}

export interface CategoryRequest {
  name: string;
  type: CategoryType;
}

// --- transactions --------------------------------------------------------

export interface Transaction {
  id: number;
  date: IsoDate;
  description: string;
  /** The amount as it was spent, in `currency`. */
  amount: number;
  currency: string;
  /** The same amount in dinars, at the rate in force on `date`. */
  amountRsd: number;
  type: TransactionType;
  /** Null when no rule claimed it and nobody has assigned one by hand. */
  categoryId: number | null;
  categoryName: string | null;
  source: TransactionSource;
  /** Null for a manual transaction, which came from no statement. */
  importBatchId: number | null;
  createdAt: IsoInstant;
}

export interface TransactionRequest {
  date: IsoDate;
  description: string;
  amount: number;
  currency: string;
  categoryId: number | null;
}

export interface TransactionFilters {
  from?: IsoDate;
  to?: IsoDate;
  categoryId?: number;
  type?: TransactionType;
  uncategorized?: boolean;
  page?: number;
  size?: number;
}

// --- rules ---------------------------------------------------------------

export interface Rule {
  id: number;
  keyword: string;
  categoryId: number;
  categoryName: string;
  /** Half of what decides whether a rule ever fires (ADR-0004). */
  categoryType: CategoryType;
  priority: number;
}

export interface RuleRequest {
  keyword: string;
  categoryId: number;
  /** Optional: a rule with none given is the weakest kind. */
  priority?: number;
}

export interface RuleApplication {
  examined: number;
  categorized: number;
}

// --- imports -------------------------------------------------------------

/** A rejected source line, kept verbatim with the reason it was refused. */
export interface ImportError {
  /** The line number in the uploaded file, counting the header as line 1. */
  rowNumber: number;
  rawLine: string;
  reason: string;
}

export interface ImportBatch {
  id: number;
  fileName: string;
  importedAt: IsoInstant;
  totalRows: number;
  importedRows: number;
  duplicateRows: number;
  rejectedRows: number;
  /**
   * Present only on the detail endpoint. Absent on a list — which says "not
   * loaded" more plainly than an empty array, which would claim the import
   * rejected nothing.
   */
  errors?: ImportError[];
}

// --- reports -------------------------------------------------------------

export interface CategoryTotal {
  /** Null on the uncategorized row, which still holds real money. */
  categoryId: number | null;
  categoryName: string | null;
  categoryType: CategoryType | null;
  totalRsd: number;
}

export interface MonthlyReport {
  year: number;
  month: number;
  income: number;
  /** Negative. `income + expenses === balance`, literally. */
  expenses: number;
  balance: number;
  categories: CategoryTotal[];
}

export interface ComparisonRow {
  categoryId: number | null;
  categoryName: string | null;
  categoryType: CategoryType | null;
  current: number;
  previous: number;
  /**
   * `current - previous` on signed amounts, so a positive change always means
   * the position improved — for an expense category as much as an income one.
   */
  change: number;
  /** Null when the previous month was zero, which no percentage describes. */
  percent: number | null;
}

export interface Comparison {
  year: number;
  month: number;
  previousYear: number;
  previousMonth: number;
  categories: ComparisonRow[];
}

export interface TopExpense {
  id: number;
  date: IsoDate;
  description: string;
  amount: number;
  currency: string;
  amountRsd: number;
  categoryId: number | null;
  categoryName: string | null;
}

export interface TopExpenses {
  from: IsoDate;
  to: IsoDate;
  limit: number;
  expenses: TopExpense[];
}

// --- exchange rates ------------------------------------------------------

export interface RateRefresh {
  asOf: IsoDate;
  published: number;
  /** Zero is the ordinary answer to a second refresh on one day. */
  stored: number;
}
