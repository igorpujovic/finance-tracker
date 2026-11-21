import {
  useMutation,
  useQuery,
  useQueryClient,
  type UseMutationResult,
  type UseQueryResult,
} from '@tanstack/react-query';
import { queryString, request } from './client';
import type {
  Category,
  CategoryRequest,
  Comparison,
  ImportBatch,
  MonthlyReport,
  PageResponse,
  RateRefresh,
  Rule,
  RuleApplication,
  RuleRequest,
  Transaction,
  TransactionFilters,
  TransactionRequest,
  TopExpenses,
} from './types';

/**
 * Query keys, in one object so that the invalidations below cannot drift from
 * the queries above them.
 */
export const keys = {
  categories: ['categories'] as const,
  transactions: (filters: TransactionFilters) => ['transactions', filters] as const,
  transaction: (id: number) => ['transaction', id] as const,
  rules: ['rules'] as const,
  imports: (page: number) => ['imports', page] as const,
  importBatch: (id: number) => ['import', id] as const,
  monthly: (year: number, month: number) => ['monthly', year, month] as const,
  comparison: (year: number, month: number) => ['comparison', year, month] as const,
  topExpenses: (from: string, to: string, limit: number) =>
    ['top-expenses', from, to, limit] as const,
};

/**
 * Anything that changes money or categorization changes what the reports say,
 * and this is the one list of what has to be re-read when it does.
 *
 * It is deliberately blunt — every writer invalidates all of it. The
 * alternative is each mutation reasoning about which reports its particular
 * change could have moved, which is the kind of case analysis this project
 * avoids in SQL and has no more appetite for here: assigning a category changes
 * two category totals, a comparison's two rows, and possibly a transaction's
 * presence in the uncategorized filter, and a writer that got that list wrong
 * would leave a stale number on screen in exactly the place nobody checks.
 */
const MONEY_QUERIES = ['transactions', 'transaction', 'monthly', 'comparison', 'top-expenses'];

function useInvalidateMoney() {
  const client = useQueryClient();
  return () => {
    for (const key of MONEY_QUERIES) {
      void client.invalidateQueries({ queryKey: [key] });
    }
  };
}

// --- categories ----------------------------------------------------------

export function useCategories(): UseQueryResult<Category[]> {
  return useQuery({
    queryKey: keys.categories,
    queryFn: () => request<Category[]>('/categories'),
    // The category list is read by nearly every page, changes rarely, and is
    // small. Keeping it fresh for a minute stops a page change re-fetching it.
    staleTime: 60_000,
  });
}

export function useCreateCategory(): UseMutationResult<Category, Error, CategoryRequest> {
  const client = useQueryClient();
  const invalidateMoney = useInvalidateMoney();
  return useMutation({
    mutationFn: (body: CategoryRequest) =>
      request<Category>('/categories', { method: 'POST', body }),
    onSuccess: () => {
      void client.invalidateQueries({ queryKey: keys.categories });
      invalidateMoney();
    },
  });
}

export function useUpdateCategory(): UseMutationResult<
  Category,
  Error,
  { id: number; body: CategoryRequest }
> {
  const client = useQueryClient();
  const invalidateMoney = useInvalidateMoney();
  return useMutation({
    mutationFn: ({ id, body }: { id: number; body: CategoryRequest }) =>
      request<Category>(`/categories/${id}`, { method: 'PUT', body }),
    onSuccess: () => {
      void client.invalidateQueries({ queryKey: keys.categories });
      // A renamed category is a renamed row in every report.
      invalidateMoney();
      void client.invalidateQueries({ queryKey: keys.rules });
    },
  });
}

export function useDeleteCategory(): UseMutationResult<void, Error, number> {
  const client = useQueryClient();
  const invalidateMoney = useInvalidateMoney();
  return useMutation({
    mutationFn: (id: number) => request<void>(`/categories/${id}`, { method: 'DELETE' }),
    onSuccess: () => {
      void client.invalidateQueries({ queryKey: keys.categories });
      // Deleting a category can only succeed when it holds no money, but it can
      // have had rules pointing at it.
      void client.invalidateQueries({ queryKey: keys.rules });
      invalidateMoney();
    },
  });
}

// --- transactions --------------------------------------------------------

export function useTransactions(
  filters: TransactionFilters,
): UseQueryResult<PageResponse<Transaction>> {
  return useQuery({
    queryKey: keys.transactions(filters),
    queryFn: () =>
      request<PageResponse<Transaction>>(`/transactions${queryString({ ...filters })}`),
  });
}

export function useTransaction(id: number | null): UseQueryResult<Transaction> {
  return useQuery({
    queryKey: keys.transaction(id ?? 0),
    queryFn: () => request<Transaction>(`/transactions/${id}`),
    enabled: id !== null,
  });
}

export function useCreateTransaction(): UseMutationResult<Transaction, Error, TransactionRequest> {
  const invalidateMoney = useInvalidateMoney();
  return useMutation({
    mutationFn: (body: TransactionRequest) =>
      request<Transaction>('/transactions', { method: 'POST', body }),
    onSuccess: invalidateMoney,
  });
}

export function useUpdateTransaction(): UseMutationResult<
  Transaction,
  Error,
  { id: number; body: TransactionRequest }
> {
  const invalidateMoney = useInvalidateMoney();
  return useMutation({
    mutationFn: ({ id, body }: { id: number; body: TransactionRequest }) =>
      request<Transaction>(`/transactions/${id}`, { method: 'PUT', body }),
    onSuccess: invalidateMoney,
  });
}

/**
 * Changing only the category. A separate endpoint from `PUT` because it is a
 * separate intention: `PATCH` cannot restate an amount, so the one thing it
 * cannot do is silently convert a foreign row to dinars.
 */
export function useChangeCategory(): UseMutationResult<
  Transaction,
  Error,
  { id: number; categoryId: number | null }
> {
  const invalidateMoney = useInvalidateMoney();
  return useMutation({
    mutationFn: ({ id, categoryId }: { id: number; categoryId: number | null }) =>
      request<Transaction>(`/transactions/${id}/category`, {
        method: 'PATCH',
        body: { categoryId },
      }),
    onSuccess: invalidateMoney,
  });
}

export function useDeleteTransaction(): UseMutationResult<void, Error, number> {
  const invalidateMoney = useInvalidateMoney();
  return useMutation({
    mutationFn: (id: number) => request<void>(`/transactions/${id}`, { method: 'DELETE' }),
    onSuccess: invalidateMoney,
  });
}

// --- rules ---------------------------------------------------------------

export function useRules(): UseQueryResult<Rule[]> {
  return useQuery({
    queryKey: keys.rules,
    queryFn: () => request<Rule[]>('/rules'),
  });
}

export function useCreateRule(): UseMutationResult<Rule, Error, RuleRequest> {
  const client = useQueryClient();
  return useMutation({
    mutationFn: (body: RuleRequest) => request<Rule>('/rules', { method: 'POST', body }),
    // Creating a rule categorizes nothing by itself — "apply" does that, and it
    // has its own invalidation. So the reports are deliberately left alone.
    onSuccess: () => void client.invalidateQueries({ queryKey: keys.rules }),
  });
}

export function useUpdateRule(): UseMutationResult<Rule, Error, { id: number; body: RuleRequest }> {
  const client = useQueryClient();
  return useMutation({
    mutationFn: ({ id, body }: { id: number; body: RuleRequest }) =>
      request<Rule>(`/rules/${id}`, { method: 'PUT', body }),
    onSuccess: () => void client.invalidateQueries({ queryKey: keys.rules }),
  });
}

export function useDeleteRule(): UseMutationResult<void, Error, number> {
  const client = useQueryClient();
  return useMutation({
    mutationFn: (id: number) => request<void>(`/rules/${id}`, { method: 'DELETE' }),
    onSuccess: () => void client.invalidateQueries({ queryKey: keys.rules }),
  });
}

/** Runs the rules over every uncategorized transaction. This one does move money. */
export function useApplyRules(): UseMutationResult<RuleApplication, Error, void> {
  const invalidateMoney = useInvalidateMoney();
  return useMutation({
    mutationFn: () => request<RuleApplication>('/rules/apply', { method: 'POST' }),
    onSuccess: invalidateMoney,
  });
}

// --- imports -------------------------------------------------------------

export function useImports(page: number): UseQueryResult<PageResponse<ImportBatch>> {
  return useQuery({
    queryKey: keys.imports(page),
    queryFn: () => request<PageResponse<ImportBatch>>(`/imports${queryString({ page })}`),
  });
}

export function useImportBatch(id: number | null): UseQueryResult<ImportBatch> {
  return useQuery({
    queryKey: keys.importBatch(id ?? 0),
    queryFn: () => request<ImportBatch>(`/imports/${id}`),
    enabled: id !== null,
  });
}

export function useUploadStatement(): UseMutationResult<ImportBatch, Error, File> {
  const client = useQueryClient();
  const invalidateMoney = useInvalidateMoney();
  return useMutation({
    mutationFn: (file: File) => {
      const formData = new FormData();
      formData.append('file', file);
      return request<ImportBatch>('/imports', { formData });
    },
    onSuccess: () => {
      void client.invalidateQueries({ queryKey: ['imports'] });
      invalidateMoney();
    },
    // An upload is the one request here that can reasonably take seconds and
    // must not be retried: a retry of a half-answered upload would be a second
    // import of the same statement. Its duplicate detection would catch it, but
    // the batch history would then show an import nobody asked for.
    retry: false,
  });
}

// --- reports -------------------------------------------------------------

export function useMonthlyReport(year: number, month: number): UseQueryResult<MonthlyReport> {
  return useQuery({
    queryKey: keys.monthly(year, month),
    queryFn: () => request<MonthlyReport>(`/reports/monthly${queryString({ year, month })}`),
  });
}

export function useComparison(year: number, month: number): UseQueryResult<Comparison> {
  return useQuery({
    queryKey: keys.comparison(year, month),
    queryFn: () => request<Comparison>(`/reports/comparison${queryString({ year, month })}`),
  });
}

export function useTopExpenses(
  from: string,
  to: string,
  limit: number,
): UseQueryResult<TopExpenses> {
  return useQuery({
    queryKey: keys.topExpenses(from, to, limit),
    queryFn: () => request<TopExpenses>(`/reports/top-expenses${queryString({ from, to, limit })}`),
  });
}

// --- exchange rates ------------------------------------------------------

/**
 * Warms today's rates. Not retried: a 503 here means the National Bank is down,
 * and hammering it is neither polite nor useful (ADR-0005).
 */
export function useRefreshRates(): UseMutationResult<RateRefresh, Error, void> {
  return useMutation({
    mutationFn: () => request<RateRefresh>('/exchange-rates/refresh', { method: 'POST' }),
    retry: false,
  });
}
