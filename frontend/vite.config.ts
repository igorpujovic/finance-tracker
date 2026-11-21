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
export default defineConfig(({ mode }) => ({
  plugins: [react()],
  /**
   * `/` for the real build, which Maven copies into the jar's static
   * resources and Spring serves from the root.
   *
   * The GitHub Pages demo is served from `/<repository>/`, which for this
   * repository is `/finance-tracker/`. Everything that needs to know — the
   * asset URLs, the router's basename, the service worker's scope and the path
   * the client fetches the API on — reads it from `import.meta.env.BASE_URL`
   * rather than repeating it, so this is the only line that states it.
   *
   * Written here rather than passed on the command line so that
   * `npm run build:demo` produces a publishable demo on its own, and the
   * workflow has no argument it can get wrong.
   */
  base: mode === 'demo' ? '/finance-tracker/' : '/',
  /**
   * `public/` holds exactly one file, MSW's generated service worker, and it
   * is of no use to the real application — copying it in would put a worker
   * that never registers inside the jar. It is therefore published only by the
   * demo build. Anything genuinely static and wanted in both (a favicon, say)
   * cannot simply be dropped in here; it needs this made conditional per file.
   */
  publicDir: mode === 'demo' ? 'public' : false,
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
}));
