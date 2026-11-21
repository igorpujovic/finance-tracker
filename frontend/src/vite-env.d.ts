/// <reference types="vite/client" />

interface ImportMetaEnv {
  /**
   * Set only by the GitHub Pages build (`VITE_DEMO=true`). When it is absent
   * the service worker is never started and the demo's fixtures are not in the
   * bundle at all — the branch that imports them is dropped by the bundler.
   */
  readonly VITE_DEMO?: string;
}

interface ImportMeta {
  readonly env: ImportMetaEnv;
}
