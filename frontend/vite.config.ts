// `defineConfig` comes from vitest rather than from vite: it is vite's own,
// widened with the `test` block below. Importing it from 'vite' typechecks
// everything here except `test`, which is the one part a stray refactor breaks.
import { defineConfig } from 'vitest/config';
import react from '@vitejs/plugin-react';

/**
 * The production build is copied into the jar's static resources by Maven
 * (see the `frontend-maven-plugin` executions in pom.xml and ADR-0008), so the
 * application serves the client and the API from one origin on one port. That is
 * why there is no CORS configuration anywhere in this project.
 *
 * `npm run dev` is the one case where they are two origins, and the proxy below
 * is what keeps it from being a third configuration to maintain: the client
 * always fetches a relative `/api/...` path, in development as in production.
 */
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      // The docs are proxied too, so the nav's link to Scalar works from the
      // dev server rather than only from the packaged jar.
      '/api': 'http://localhost:8080',
      '/scalar': 'http://localhost:8080',
      '/swagger-ui': 'http://localhost:8080',
      '/swagger-ui.html': 'http://localhost:8080',
      '/v3/api-docs': 'http://localhost:8080',
    },
  },
  build: {
    outDir: 'dist',
    // A build that silently drops assets over a size limit is a build that
    // ships a broken page; nothing here is near it, so the warning is noise.
    chunkSizeWarningLimit: 1500,
  },
  test: {
    environment: 'jsdom',
    globals: true,
    setupFiles: './src/test-setup.ts',
    css: false,
  },
});
