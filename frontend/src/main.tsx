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

createRoot(root).render(
  <StrictMode>
    <QueryClientProvider client={queryClient}>
      <BrowserRouter>
        <App />
      </BrowserRouter>
    </QueryClientProvider>
  </StrictMode>,
);
