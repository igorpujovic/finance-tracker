import { NavLink, Route, Routes } from 'react-router-dom';
import { DemoBanner } from './demo/DemoBanner';
import { Dashboard } from './routes/Dashboard';
import { Transactions } from './routes/Transactions';
import { Import } from './routes/Import';
import { ImportDetail } from './routes/ImportDetail';
import { Categories } from './routes/Categories';
import { Rules } from './routes/Rules';
import { Reports } from './routes/Reports';
import { Settings } from './routes/Settings';

/**
 * The routes here are the same seven paths `SpaRoutes.java` forwards to
 * `index.html`. A route added here and not there works when the reader clicks
 * to it and 404s when they reload the page on it, which is the one failure mode
 * of serving a client-side router from a server — so the two lists have to be
 * changed together, and the Java side says so too.
 */
export default function App() {
  return (
    <div className="app">
      {import.meta.env.VITE_DEMO ? <DemoBanner /> : null}
      <header className="topbar">
        <NavLink to="/" className="brand">
          finance-tracker
        </NavLink>
        <nav className="nav">
          <NavLink to="/" end>
            Dashboard
          </NavLink>
          <NavLink to="/transactions">Transactions</NavLink>
          <NavLink to="/import">Import</NavLink>
          <NavLink to="/reports">Reports</NavLink>
          <NavLink to="/categories">Categories</NavLink>
          <NavLink to="/rules">Rules</NavLink>
          <NavLink to="/settings">Settings</NavLink>
        </nav>
        {/* The OpenAPI document this client is written against, served by the
            same application on the same port. */}
        <a className="docs-link" href="/scalar" target="_blank" rel="noreferrer">
          API docs ↗
        </a>
      </header>

      <main>
        <Routes>
          <Route path="/" element={<Dashboard />} />
          <Route path="/transactions" element={<Transactions />} />
          <Route path="/import" element={<Import />} />
          <Route path="/import/:id" element={<ImportDetail />} />
          <Route path="/categories" element={<Categories />} />
          <Route path="/rules" element={<Rules />} />
          <Route path="/reports" element={<Reports />} />
          <Route path="/settings" element={<Settings />} />
          <Route
            path="*"
            element={
              <div className="empty">
                That page does not exist. <NavLink to="/">Back to the dashboard</NavLink>.
              </div>
            }
          />
        </Routes>
      </main>
    </div>
  );
}
