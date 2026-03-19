import { ReactNode } from "react";
import { NavLink } from "react-router-dom";

interface LayoutProps {
  children: ReactNode;
}

export function Layout({ children }: LayoutProps) {
  return (
    <div className="app-shell">
      <aside className="sidebar">
        <div className="brand">Banking AI Console</div>
        <nav className="nav">
          <NavLink to="/dashboard" className="nav-link">
            Dashboard
          </NavLink>
          <NavLink to="/ai-assistant" className="nav-link">
            AI Assistant
          </NavLink>
          <NavLink to="/onboarding" className="nav-link">
            Onboarding
          </NavLink>
          <NavLink to="/accounts" className="nav-link">
            Accounts
          </NavLink>
          <NavLink to="/payments" className="nav-link">
            Payments
          </NavLink>
        </nav>
      </aside>
      <main className="main">
        <header className="topbar">
          <h1>Banking Operations</h1>
        </header>
        <section className="content">{children}</section>
      </main>
    </div>
  );
}

