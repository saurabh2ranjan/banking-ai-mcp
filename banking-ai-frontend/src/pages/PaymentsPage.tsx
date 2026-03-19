import { FormEvent, useState } from "react";
import {
  initiatePayment,
  getPayments,
  getDailySummary,
  processPayment,
  reversePayment
} from "../api/payments";
import type { PaymentSummary, PaymentResponse, DailySpendingSummary } from "../api/types";

export function PaymentsPage() {
  const [accountId, setAccountId] = useState("");
  const [payments, setPayments] = useState<PaymentSummary[]>([]);
  const [dailySummary, setDailySummary] = useState<DailySpendingSummary | null>(null);
  const [selectedPayment, setSelectedPayment] = useState<PaymentResponse | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [form, setForm] = useState({
    customerId: "",
    sourceAccountId: "",
    destinationAccountId: "",
    amount: "0.00",
    currency: "INR",
    paymentType: "IMPS",
    description: ""
  });

  async function loadHistory(e: FormEvent) {
    e.preventDefault();
    if (!accountId) return;
    setError(null);
    try {
      const [page, summary] = await Promise.all([
        getPayments({ accountId, page: 0, size: 20 }),
        getDailySummary(accountId)
      ]);
      setPayments(page.content);
      setDailySummary(summary);
    } catch (e) {
      setError(
        e instanceof Error ? e.message : "Failed to load payments"
      );
    }
  }

  async function handleInitiate(e: FormEvent) {
    e.preventDefault();
    setError(null);
    try {
      const payment = await initiatePayment({
        customerId: form.customerId,
        sourceAccountId: form.sourceAccountId,
        destinationAccountId: form.destinationAccountId,
        amount: parseFloat(form.amount || "0"),
        currency: form.currency,
        paymentType: form.paymentType,
        description: form.description || undefined
      });
      setSelectedPayment(payment);
      if (accountId) {
        const page = await getPayments({ accountId, page: 0, size: 20 });
        setPayments(page.content);
      }
    } catch (e) {
      setError(
        e instanceof Error ? e.message : "Failed to initiate payment"
      );
    }
  }

  async function handleProcess(paymentId: string) {
    setError(null);
    try {
      const updated = await processPayment(paymentId);
      setSelectedPayment(updated);
      if (accountId) {
        const page = await getPayments({ accountId, page: 0, size: 20 });
        setPayments(page.content);
      }
    } catch (e) {
      setError(
        e instanceof Error ? e.message : "Failed to process payment"
      );
    }
  }

  async function handleReverse(paymentId: string) {
    const reason = window.prompt("Enter reason for reversal");
    if (!reason) return;
    setError(null);
    try {
      const updated = await reversePayment({ paymentId, reason });
      setSelectedPayment(updated);
      if (accountId) {
        const page = await getPayments({ accountId, page: 0, size: 20 });
        setPayments(page.content);
      }
    } catch (e) {
      setError(
        e instanceof Error ? e.message : "Failed to reverse payment"
      );
    }
  }

  return (
    <div className="grid grid-2">
      <div className="card">
        <div className="card-header">
          <div>
            <div className="card-title">Payments</div>
            <div className="card-subtitle">
              Initiate and manage account-level transactions
            </div>
          </div>
        </div>
        <form onSubmit={loadHistory} className="btn-row">
          <div style={{ flex: 1 }}>
            <label>Account ID for history</label>
            <input
              value={accountId}
              onChange={(e) => setAccountId(e.target.value)}
              placeholder="e.g. ACC-001"
            />
          </div>
          <div style={{ alignSelf: "flex-end" }}>
            <button className="btn-secondary" type="submit">
              Load history
            </button>
          </div>
        </form>
        {dailySummary && (
          <div className="pill-row" style={{ marginTop: "0.5rem" }}>
            <span className="chip">
              Today: {dailySummary.totalSpentToday.toString()}{" "}
              {dailySummary.currency}
            </span>
            <span className="chip">
              Tx count: {dailySummary.transactionCount.toString()}
            </span>
            <span className="chip">
              Largest: {dailySummary.largestTransaction.toString()}{" "}
              {dailySummary.currency}
            </span>
          </div>
        )}
        {payments.length > 0 && (
          <table className="table" style={{ marginTop: "0.75rem" }}>
            <thead>
              <tr>
                <th>Ref</th>
                <th>Amount</th>
                <th>Type</th>
                <th>Status</th>
              </tr>
            </thead>
            <tbody>
              {payments.map((p) => (
                <tr
                  key={p.paymentId}
                  onClick={() =>
                    setSelectedPayment({
                      // minimal mapping for quick detail; normally fetch full details
                      paymentId: p.paymentId,
                      referenceNumber: p.referenceNumber,
                      customerId: "",
                      sourceAccountId: "",
                      destinationAccountId: "",
                      amount: p.amount,
                      currency: p.currency,
                      paymentType: p.paymentType,
                      status: p.status,
                      description: null,
                      initiatedAt: p.initiatedAt,
                      completedAt: null,
                      failureReason: null,
                      fraudScore: null,
                      fraudRiskLevel: null
                    })
                  }
                  style={{ cursor: "pointer" }}
                >
                  <td>{p.referenceNumber}</td>
                  <td>
                    {p.amount.toString()} {p.currency}
                  </td>
                  <td>{p.paymentType}</td>
                  <td>{p.status}</td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
        {error && <div className="error-text">{error}</div>}
      </div>
      <div className="card">
        <div className="card-header">
          <div>
            <div className="card-title">Initiate Payment</div>
            <div className="card-subtitle">
              Funds are put on hold immediately; fraud rules apply
            </div>
          </div>
        </div>
        <form
          onSubmit={handleInitiate}
          className="grid"
          style={{ gap: "0.75rem" }}
        >
          <div>
            <label>Customer ID</label>
            <input
              value={form.customerId}
              onChange={(e) =>
                setForm((f) => ({ ...f, customerId: e.target.value }))
              }
              required
            />
          </div>
          <div className="grid" style={{ gridTemplateColumns: "1fr 1fr", gap: "0.75rem" }}>
            <div>
              <label>Source account ID</label>
              <input
                value={form.sourceAccountId}
                onChange={(e) =>
                  setForm((f) => ({ ...f, sourceAccountId: e.target.value }))
                }
                required
              />
            </div>
            <div>
              <label>Destination account ID</label>
              <input
                value={form.destinationAccountId}
                onChange={(e) =>
                  setForm((f) => ({
                    ...f,
                    destinationAccountId: e.target.value
                  }))
                }
                required
              />
            </div>
          </div>
          <div className="grid" style={{ gridTemplateColumns: "1fr 1fr 1fr", gap: "0.75rem" }}>
            <div>
              <label>Amount</label>
              <input
                type="number"
                min="0.01"
                step="0.01"
                value={form.amount}
                onChange={(e) =>
                  setForm((f) => ({ ...f, amount: e.target.value }))
                }
                required
              />
            </div>
            <div>
              <label>Currency</label>
              <input
                value={form.currency}
                onChange={(e) =>
                  setForm((f) => ({ ...f, currency: e.target.value }))
                }
              />
            </div>
            <div>
              <label>Payment type</label>
              <select
                value={form.paymentType}
                onChange={(e) =>
                  setForm((f) => ({ ...f, paymentType: e.target.value }))
                }
              >
                <option value="IMPS">IMPS</option>
                <option value="NEFT">NEFT</option>
                <option value="RTGS">RTGS</option>
              </select>
            </div>
          </div>
          <div>
            <label>Description</label>
            <textarea
              value={form.description}
              onChange={(e) =>
                setForm((f) => ({ ...f, description: e.target.value }))
              }
              placeholder="Purpose, notes for compliance, etc."
            />
          </div>
          <div className="btn-row">
            <button className="btn-primary" type="submit">
              Initiate payment
            </button>
          </div>
        </form>
        {selectedPayment && (
          <div style={{ marginTop: "1rem" }}>
            <div className="card-subtitle">Selected payment</div>
            <div className="pill-row">
              <span className="chip">
                Ref: {selectedPayment.referenceNumber}
              </span>
              <span className="chip">
                {selectedPayment.amount.toString()} {selectedPayment.currency}
              </span>
              <span className="chip">{selectedPayment.status}</span>
              {selectedPayment.fraudRiskLevel && (
                <span className="chip">
                  Fraud: {selectedPayment.fraudRiskLevel} (
                  {selectedPayment.fraudScore?.toFixed(2) ?? "—"})
                </span>
              )}
            </div>
            <div className="btn-row" style={{ marginTop: "0.75rem" }}>
              <button
                className="btn-secondary"
                type="button"
                onClick={() => void handleProcess(selectedPayment.paymentId)}
              >
                Process payment
              </button>
              <button
                className="btn-ghost"
                type="button"
                onClick={() => void handleReverse(selectedPayment.paymentId)}
              >
                Reverse
              </button>
            </div>
          </div>
        )}
      </div>
    </div>
  );
}

