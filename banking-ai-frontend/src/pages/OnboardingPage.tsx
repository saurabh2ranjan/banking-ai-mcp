import { FormEvent, useState } from "react";
import {
  initiateOnboarding,
  getPendingKyc,
  updateKycStatus,
  completeOnboarding
} from "../api/onboarding";
import type { CustomerSummary, CustomerResponse } from "../api/types";

export function OnboardingPage() {
  const [form, setForm] = useState({
    firstName: "",
    lastName: "",
    dateOfBirth: "",
    gender: "MALE",
    email: "",
    mobile: "",
    nationality: "",
    idType: "PASSPORT",
    idExpiryDate: "",
    line1: "",
    city: "",
    state: "",
    postalCode: "",
    country: "IND",
    preferredAccountType: "SAVINGS"
  });
  const [pendingKyc, setPendingKyc] = useState<CustomerSummary[]>([]);
  const [selectedCustomer, setSelectedCustomer] = useState<CustomerResponse | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [message, setMessage] = useState<string | null>(null);

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    setLoading(true);
    setError(null);
    setMessage(null);
    try {
      const created = await initiateOnboarding({
        firstName: form.firstName,
        lastName: form.lastName,
        dateOfBirth: form.dateOfBirth,
        gender: form.gender,
        email: form.email,
        mobile: form.mobile,
        nationality: form.nationality,
        panNumber: undefined,
        passportNumber: undefined,
        nationalId: undefined,
        idType: form.idType,
        idExpiryDate: form.idExpiryDate,
        address: {
          line1: form.line1,
          line2: "",
          city: form.city,
          state: form.state,
          postalCode: form.postalCode,
          country: form.country
        },
        employmentType: undefined,
        employerName: undefined,
        annualIncome: undefined,
        incomeCurrency: undefined,
        preferredAccountType: form.preferredAccountType
      });
      setSelectedCustomer(created);
      setMessage(`Onboarding initiated for ${created.fullName}`);
    } catch (e) {
      setError(
        e instanceof Error ? e.message : "Failed to initiate onboarding"
      );
    } finally {
      setLoading(false);
    }
  }

  async function refreshKycQueue() {
    setError(null);
    try {
      const page = await getPendingKyc(0, 20);
      setPendingKyc(page.content);
    } catch (e) {
      setError(
        e instanceof Error ? e.message : "Failed to load pending KYC queue"
      );
    }
  }

  async function handleApprove(customerId: string) {
    setError(null);
    try {
      const updated = await updateKycStatus({
        customerId,
        kycStatus: "VERIFIED"
      });
      setSelectedCustomer(updated);
      await refreshKycQueue();
    } catch (e) {
      setError(
        e instanceof Error ? e.message : "Failed to update KYC status"
      );
    }
  }

  async function handleReject(customerId: string) {
    const reason = window.prompt("Enter rejection reason");
    if (!reason) return;
    setError(null);
    try {
      const updated = await updateKycStatus({
        customerId,
        kycStatus: "REJECTED",
        rejectionReason: reason
      });
      setSelectedCustomer(updated);
      await refreshKycQueue();
    } catch (e) {
      setError(
        e instanceof Error ? e.message : "Failed to reject KYC"
      );
    }
  }

  async function handleComplete(customerId: string) {
    setError(null);
    try {
      const updated = await completeOnboarding(customerId);
      setSelectedCustomer(updated);
      setMessage(`Onboarding completed for ${updated.fullName}`);
    } catch (e) {
      setError(
        e instanceof Error ? e.message : "Failed to complete onboarding"
      );
    }
  }

  return (
    <div className="grid grid-2">
      <div className="card">
        <div className="card-header">
          <div>
            <div className="card-title">New Customer Onboarding</div>
            <div className="card-subtitle">
              Capture minimal details to start the journey
            </div>
          </div>
        </div>
        <form onSubmit={handleSubmit} className="grid" style={{ gap: "0.75rem" }}>
          <div className="grid" style={{ gridTemplateColumns: "1fr 1fr", gap: "0.75rem" }}>
            <div>
              <label>First name</label>
              <input
                value={form.firstName}
                onChange={(e) =>
                  setForm((f) => ({ ...f, firstName: e.target.value }))
                }
                required
              />
            </div>
            <div>
              <label>Last name</label>
              <input
                value={form.lastName}
                onChange={(e) =>
                  setForm((f) => ({ ...f, lastName: e.target.value }))
                }
                required
              />
            </div>
          </div>
          <div className="grid" style={{ gridTemplateColumns: "1fr 1fr", gap: "0.75rem" }}>
            <div>
              <label>Date of birth</label>
              <input
                type="date"
                value={form.dateOfBirth}
                onChange={(e) =>
                  setForm((f) => ({ ...f, dateOfBirth: e.target.value }))
                }
                required
              />
            </div>
            <div>
              <label>Gender</label>
              <select
                value={form.gender}
                onChange={(e) =>
                  setForm((f) => ({ ...f, gender: e.target.value }))
                }
              >
                <option value="MALE">Male</option>
                <option value="FEMALE">Female</option>
                <option value="OTHER">Other</option>
              </select>
            </div>
          </div>
          <div>
            <label>Email</label>
            <input
              type="email"
              value={form.email}
              onChange={(e) =>
                setForm((f) => ({ ...f, email: e.target.value }))
              }
              required
            />
          </div>
          <div>
            <label>Mobile</label>
            <input
              value={form.mobile}
              onChange={(e) =>
                setForm((f) => ({ ...f, mobile: e.target.value }))
              }
              required
            />
          </div>
          <div className="grid" style={{ gridTemplateColumns: "1fr 1fr", gap: "0.75rem" }}>
            <div>
              <label>Nationality</label>
              <input
                value={form.nationality}
                onChange={(e) =>
                  setForm((f) => ({ ...f, nationality: e.target.value }))
                }
                required
              />
            </div>
            <div>
              <label>Preferred account type</label>
              <select
                value={form.preferredAccountType}
                onChange={(e) =>
                  setForm((f) => ({
                    ...f,
                    preferredAccountType: e.target.value
                  }))
                }
              >
                <option value="SAVINGS">Savings</option>
                <option value="CURRENT">Current</option>
              </select>
            </div>
          </div>
          <div className="grid" style={{ gridTemplateColumns: "1.2fr 1fr", gap: "0.75rem" }}>
            <div>
              <label>Address line 1</label>
              <input
                value={form.line1}
                onChange={(e) =>
                  setForm((f) => ({ ...f, line1: e.target.value }))
                }
                required
              />
            </div>
            <div>
              <label>City</label>
              <input
                value={form.city}
                onChange={(e) =>
                  setForm((f) => ({ ...f, city: e.target.value }))
                }
                required
              />
            </div>
          </div>
          <div className="grid" style={{ gridTemplateColumns: "1fr 1fr 1fr", gap: "0.75rem" }}>
            <div>
              <label>State</label>
              <input
                value={form.state}
                onChange={(e) =>
                  setForm((f) => ({ ...f, state: e.target.value }))
                }
                required
              />
            </div>
            <div>
              <label>Postal code</label>
              <input
                value={form.postalCode}
                onChange={(e) =>
                  setForm((f) => ({ ...f, postalCode: e.target.value }))
                }
                required
              />
            </div>
            <div>
              <label>Country (ISO)</label>
              <input
                value={form.country}
                onChange={(e) =>
                  setForm((f) => ({ ...f, country: e.target.value }))
                }
                required
              />
            </div>
          </div>
          <div className="grid" style={{ gridTemplateColumns: "1fr 1fr", gap: "0.75rem" }}>
            <div>
              <label>ID type</label>
              <select
                value={form.idType}
                onChange={(e) =>
                  setForm((f) => ({ ...f, idType: e.target.value }))
                }
              >
                <option value="PASSPORT">Passport</option>
                <option value="NATIONAL_ID">National ID</option>
                <option value="DRIVING_LICENSE">Driving License</option>
              </select>
            </div>
            <div>
              <label>ID expiry date</label>
              <input
                type="date"
                value={form.idExpiryDate}
                onChange={(e) =>
                  setForm((f) => ({ ...f, idExpiryDate: e.target.value }))
                }
                required
              />
            </div>
          </div>
          <div className="btn-row">
            <button className="btn-primary" type="submit" disabled={loading}>
              {loading ? "Submitting..." : "Initiate onboarding"}
            </button>
            <button
              type="button"
              className="btn-secondary"
              onClick={() => void refreshKycQueue()}
            >
              Refresh KYC queue
            </button>
          </div>
          {message && <div className="muted">{message}</div>}
          {error && <div className="error-text">{error}</div>}
        </form>
      </div>
      <div className="card">
        <div className="card-header">
          <div>
            <div className="card-title">KYC Review Queue</div>
            <div className="card-subtitle">
              Approve, reject, and complete onboarding
            </div>
          </div>
        </div>
        {pendingKyc.length === 0 ? (
          <div className="muted">
            No pending KYC customers. Click &quot;Refresh KYC queue&quot; to
            fetch latest data.
          </div>
        ) : (
          <table className="table">
            <thead>
              <tr>
                <th>Customer</th>
                <th>Email</th>
                <th>Mobile</th>
                <th>Actions</th>
              </tr>
            </thead>
            <tbody>
              {pendingKyc.map((c) => (
                <tr key={c.customerId}>
                  <td>{c.fullName}</td>
                  <td>{c.email}</td>
                  <td>{c.mobile}</td>
                  <td>
                    <div className="btn-row">
                      <button
                        className="btn-primary"
                        type="button"
                        onClick={() => void handleApprove(c.customerId)}
                      >
                        Approve
                      </button>
                      <button
                        className="btn-secondary"
                        type="button"
                        onClick={() => void handleReject(c.customerId)}
                      >
                        Reject
                      </button>
                      <button
                        className="btn-ghost"
                        type="button"
                        onClick={() => void handleComplete(c.customerId)}
                      >
                        Complete onboarding
                      </button>
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
        {selectedCustomer && (
          <div style={{ marginTop: "0.75rem" }}>
            <div className="card-subtitle">Selected customer</div>
            <div className="pill-row">
              <span className="chip">{selectedCustomer.fullName}</span>
              <span className="chip">
                KYC: {selectedCustomer.kycStatus}
              </span>
              <span className="chip">
                Onboarding: {selectedCustomer.onboardingStatus}
              </span>
            </div>
          </div>
        )}
      </div>
    </div>
  );
}

