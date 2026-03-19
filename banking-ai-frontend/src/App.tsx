import { Route, Routes, Navigate } from "react-router-dom";
import { Layout } from "./components/Layout";
import { DashboardPage } from "./pages/DashboardPage";
import { OnboardingPage } from "./pages/OnboardingPage";
import { AccountsPage } from "./pages/AccountsPage";
import { PaymentsPage } from "./pages/PaymentsPage";
import { AiAssistantPage } from "./pages/AiAssistantPage";

export function App() {
  return (
    <Layout>
      <Routes>
        <Route path="/" element={<Navigate to="/dashboard" replace />} />
        <Route path="/dashboard" element={<DashboardPage />} />
        <Route path="/onboarding" element={<OnboardingPage />} />
        <Route path="/accounts" element={<AccountsPage />} />
        <Route path="/payments" element={<PaymentsPage />} />
        <Route path="/ai-assistant" element={<AiAssistantPage />} />
        <Route path="*" element={<Navigate to="/dashboard" replace />} />
      </Routes>
    </Layout>
  );
}

