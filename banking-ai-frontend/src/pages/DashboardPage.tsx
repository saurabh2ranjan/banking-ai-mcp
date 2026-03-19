import { useEffect, useState } from "react";
import { getCustomersByStatus, getPendingKyc } from "../api/onboarding";
import { getSessionStats } from "../api/ai";
import type { CustomerSummary } from "../api/types";

interface SessionStats {
  activeSessions?: number;
  totalMessages?: number;
  sessions?: unknown;
}

export function DashboardPage() {
  const [pendingKyc, setPendingKyc] = useState<CustomerSummary[]>([]);
  const [recentOnboarded, setRecentOnboarded] = useState<CustomerSummary[]>([]);
  const [sessionStats, setSessionStats] = useState<SessionStats | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    async function load() {
      try {
        setError(null);
        const [kycPage, onboardedPage, stats] = await Promise.all([
          getPendingKyc(0, 5),
          getCustomersByStatus("COMPLETED", 0, 5),
          getSessionStats()
        ]);
        setPendingKyc(kycPage.content);
        setRecentOnboarded(onboardedPage.content);
        setSessionStats(stats as SessionStats);
      } catch (e) {
        setError(
          e instanceof Error ? e.message : "Failed to load dashboard data"
        );
      }
    }
    void load();
  }, []);

  return (
    <>
      <div className="grid grid-2">
        <div className="card">
          <div className="card-header">
            <div>
              <div className="card-title">KYC Queue</div>
              <div className="card-subtitle">
                Customers awaiting KYC review
              </div>
            </div>
            <span className="badge">
              Pending: {pendingKyc.length.toString()}
            </span>
          </div>
          {pendingKyc.length === 0 ? (
            <div className="muted">No customers waiting for KYC.</div>
          ) : (
            <table className="table">
              <thead>
                <tr>
                  <th>Customer</th>
                  <th>Email</th>
                  <th>Mobile</th>
                  <th>Status</th>
                </tr>
              </thead>
              <tbody>
                {pendingKyc.map((c) => (
                  <tr key={c.customerId}>
                    <td>{c.fullName}</td>
                    <td>{c.email}</td>
                    <td>{c.mobile}</td>
                    <td>
                      <span className="status-pill status-warn">
                        {c.kycStatus}
                      </span>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </div>
        <div className="card">
          <div className="card-header">
            <div>
              <div className="card-title">AI Assistant Activity</div>
              <div className="card-subtitle">
                Usage stats from BankingAssist AI
              </div>
            </div>
          </div>
          {sessionStats ? (
            <div className="pill-row">
              <span className="chip">
                Active sessions:{" "}
                {sessionStats.activeSessions?.toString() ?? "—"}
              </span>
              <span className="chip">
                Messages: {sessionStats.totalMessages?.toString() ?? "—"}
              </span>
            </div>
          ) : (
            <div className="muted">No AI session data yet.</div>
          )}
        </div>
      </div>
      <div className="card">
        <div className="card-header">
          <div>
            <div className="card-title">Recently Onboarded</div>
            <div className="card-subtitle">
              Customers who completed onboarding
            </div>
          </div>
        </div>
        {recentOnboarded.length === 0 ? (
          <div className="muted">No completed onboardings yet.</div>
        ) : (
          <table className="table">
            <thead>
              <tr>
                <th>Customer</th>
                <th>Email</th>
                <th>Mobile</th>
                <th>Onboarding</th>
                <th>KYC</th>
              </tr>
            </thead>
            <tbody>
              {recentOnboarded.map((c) => (
                <tr key={c.customerId}>
                  <td>{c.fullName}</td>
                  <td>{c.email}</td>
                  <td>{c.mobile}</td>
                  <td>{c.onboardingStatus}</td>
                  <td>{c.kycStatus}</td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
        {error && <div className="error-text">{error}</div>}
      </div>
    </>
  );
}

