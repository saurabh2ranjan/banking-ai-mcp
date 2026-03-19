import { FormEvent, useState } from "react";
import {
  getCustomerAccounts,
  openAccount,
  getBalance,
  blockAccount,
  unblockAccount,
  getAccount
} from "../api/accounts";
import type { AccountSummary, BalanceResponse, AccountResponse } from "../api/types";

export function AccountsPage() {
  const [customerId, setCustomerId] = useState("");
  const [accounts, setAccounts] = useState<AccountSummary[]>([]);
  const [selectedAccount, setSelectedAccount] = useState<AccountResponse | null>(null);
  const [balance, setBalance] = useState<BalanceResponse | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [openForm, setOpenForm] = useState({
    accountType: "SAVINGS",
    currency: "INR",
    displayName: "",
    initialDeposit: "0.00"
  });

  async function handleSearch(e: FormEvent) {
    e.preventDefault();
    if (!customerId) return;
    setLoading(true);
    setError(null);
    try {
      const list = await getCustomerAccounts(customerId);
      setAccounts(list);
      setSelectedAccount(null);
      setBalance(null);
    } catch (e) {
      setError(
        e instanceof Error ? e.message : "Failed to load customer accounts"
      );
    } finally {
      setLoading(false);
    }
  }

  async function selectAccount(account: AccountSummary) {
    setError(null);
    try {
      const [bal, acc] = await Promise.all([
        getBalance(account.accountId),
        getAccount(account.accountId)
      ]);
      setBalance(bal);
      setSelectedAccount(acc);
    } catch (e) {
      setError(
        e instanceof Error ? e.message : "Failed to load account details"
      );
    }
  }

  async function handleOpenAccount(e: FormEvent) {
    e.preventDefault();
    if (!customerId) {
      setError("Enter a customer ID before opening an account.");
      return;
    }
    setError(null);
    try {
      const created = await openAccount({
        customerId,
        accountType: openForm.accountType,
        currency: openForm.currency,
        displayName: openForm.displayName || undefined,
        initialDeposit: parseFloat(openForm.initialDeposit || "0")
      });
      setSelectedAccount(created);
      const list = await getCustomerAccounts(customerId);
      setAccounts(list);
    } catch (e) {
      setError(
        e instanceof Error ? e.message : "Failed to open account"
      );
    }
  }

  async function handleBlock() {
    if (!selectedAccount) return;
    const reason = window.prompt("Enter reason for blocking this account");
    if (!reason) return;
    setError(null);
    try {
      const updated = await blockAccount({
        accountId: selectedAccount.accountId,
        reason
      });
      setSelectedAccount(updated);
      const list = await getCustomerAccounts(updated.customerId);
      setAccounts(list);
    } catch (e) {
      setError(
        e instanceof Error ? e.message : "Failed to block account"
      );
    }
  }

  async function handleUnblock() {
    if (!selectedAccount) return;
    setError(null);
    try {
      const updated = await unblockAccount(selectedAccount.accountId);
      setSelectedAccount(updated);
      const list = await getCustomerAccounts(updated.customerId);
      setAccounts(list);
    } catch (e) {
      setError(
        e instanceof Error ? e.message : "Failed to unblock account"
      );
    }
  }

  return (
    <div className="grid grid-2">
      <div className="card">
        <div className="card-header">
          <div>
            <div className="card-title">Customer Accounts</div>
            <div className="card-subtitle">
              Search by customer ID and view accounts
            </div>
          </div>
        </div>
        <form onSubmit={handleSearch} className="btn-row">
          <div style={{ flex: 1 }}>
            <label>Customer ID</label>
            <input
              value={customerId}
              onChange={(e) => setCustomerId(e.target.value)}
              placeholder="e.g. CUST-001"
            />
          </div>
          <div style={{ alignSelf: "flex-end" }}>
            <button className="btn-primary" type="submit" disabled={loading}>
              {loading ? "Searching..." : "Search"}
            </button>
          </div>
        </form>
        {accounts.length > 0 && (
          <table className="table" style={{ marginTop: "0.75rem" }}>
            <thead>
              <tr>
                <th>Account</th>
                <th>Type</th>
                <th>Status</th>
                <th>Balance</th>
              </tr>
            </thead>
            <tbody>
              {accounts.map((a) => (
                <tr
                  key={a.accountId}
                  onClick={() => void selectAccount(a)}
                  style={{ cursor: "pointer" }}
                >
                  <td>{a.accountNumber}</td>
                  <td>{a.accountType}</td>
                  <td>{a.status}</td>
                  <td>
                    {a.balance.toString()} {a.currency}
                  </td>
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
            <div className="card-title">Open / Manage Account</div>
            <div className="card-subtitle">
              Create and control account lifecycle
            </div>
          </div>
        </div>
        <form
          onSubmit={handleOpenAccount}
          className="grid"
          style={{ gap: "0.75rem" }}
        >
          <div className="grid" style={{ gridTemplateColumns: "1fr 1fr", gap: "0.75rem" }}>
            <div>
              <label>Account type</label>
              <select
                value={openForm.accountType}
                onChange={(e) =>
                  setOpenForm((f) => ({ ...f, accountType: e.target.value }))
                }
              >
                <option value="SAVINGS">Savings</option>
                <option value="CURRENT">Current</option>
                <option value="FIXED_DEPOSIT">Fixed deposit</option>
              </select>
            </div>
            <div>
              <label>Currency</label>
              <input
                value={openForm.currency}
                onChange={(e) =>
                  setOpenForm((f) => ({ ...f, currency: e.target.value }))
                }
              />
            </div>
          </div>
          <div>
            <label>Display name</label>
            <input
              value={openForm.displayName}
              onChange={(e) =>
                setOpenForm((f) => ({ ...f, displayName: e.target.value }))
              }
              placeholder="e.g. Salary account"
            />
          </div>
          <div>
            <label>Initial deposit</label>
            <input
              type="number"
              value={openForm.initialDeposit}
              onChange={(e) =>
                setOpenForm((f) => ({ ...f, initialDeposit: e.target.value }))
              }
              min="0"
              step="0.01"
            />
          </div>
          <div className="btn-row">
            <button className="btn-primary" type="submit">
              Open account
            </button>
          </div>
        </form>
        {selectedAccount && (
          <div style={{ marginTop: "1rem" }}>
            <div className="card-subtitle">Selected account</div>
            <div className="pill-row">
              <span className="chip">
                {selectedAccount.accountNumber} ({selectedAccount.accountType})
              </span>
              <span className="chip">
                Status: {selectedAccount.status}
              </span>
              <span className="chip">
                Balance: {selectedAccount.balance.toString()}{" "}
                {selectedAccount.currency}
              </span>
            </div>
            {balance && (
              <div className="pill-row" style={{ marginTop: "0.5rem" }}>
                <span className="chip">
                  Available: {balance.availableBalance.toString()}{" "}
                  {balance.currency}
                </span>
                <span className="chip">
                  On hold: {balance.holdAmount.toString()} {balance.currency}
                </span>
              </div>
            )}
            <div className="btn-row" style={{ marginTop: "0.75rem" }}>
              <button
                className="btn-secondary"
                type="button"
                onClick={() => void handleBlock()}
              >
                Block account
              </button>
              <button
                className="btn-ghost"
                type="button"
                onClick={() => void handleUnblock()}
              >
                Unblock
              </button>
            </div>
          </div>
        )}
      </div>
    </div>
  );
}

