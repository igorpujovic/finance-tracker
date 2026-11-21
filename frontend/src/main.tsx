import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { BrowserRouter } from 'react-router-dom';
import { ApiError } from './api/client';
import App from './App';
import './styles.css';

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      /**
       * A 4xx is an answer, not a transport failure: retrying a 404 or a 400
       * gets the same 404 or 400 three times and delays telling the reader.
       * A 503 is not retried either — it means the National Bank is down
       * (ADR-0005), and this client should say so rather than hammer it.
       */
      retry: (failureCount, error) => {
        if (error instanceof ApiError) return false;
        return failureCount < 2;
      },
      refetchOnWindowFocus: false,
      staleTime: 10_000,
    },
  },
});

const root = document.getElementById('root');
if (!root) throw new Error('index.html is missing its #root element');

/**
 * The GitHub Pages demo has no server, so a service worker answers the API in
 * its place. The import is dynamic and behind the flag so that the demo's
 * fixtures and MSW itself are not in the bundle the application ships — the
 * real build never evaluates this branch, and Rollup drops the whole subtree.
 *
 * It is awaited before the first render: starting the worker is asynchronous,
 * and a component that fetched before it was listening would get a real 404
 * from GitHub Pages instead of the fake answer.
 */
async function startDemoApi() {
  if (!import.meta.env.VITE_DEMO) return;
  const [{ setupWorker }, { handlers }] = await Promise.all([
    import('msw/browser'),
    import('./demo/handlers'),
  ]);
  await setupWorker(...handlers).start({
    serviceWorker: { url: `${import.meta.env.BASE_URL}mockServiceWorker.js` },
    quiet: true,
    // Anything this client does not ask for is not the worker's business.
    onUnhandledRequest: 'bypass',
  });
}

startDemoApi().then(() => {
  createRoot(root).render(
    <StrictMode>
      <QueryClientProvider client={queryClient}>
        {/* The router is told the base path so that the demo, which is served
            from a subdirectory, builds its links correctly. It is `/` for the
            real build, which is what BrowserRouter assumes anyway. */}
        <BrowserRouter basename={import.meta.env.BASE_URL}>
          <App />
        </BrowserRouter>
      </QueryClientProvider>
    </StrictMode>,
  );
});
