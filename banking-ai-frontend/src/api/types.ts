// Shared DTO types aligned with backend records

export interface OnboardingRequest {
  firstName: string;
  lastName: string;
  dateOfBirth: string;
  gender: string;
  email: string;
  mobile: string;
  nationality: string;
  panNumber?: string;
  passportNumber?: string;
  nationalId?: string;
  idType: string;
  idExpiryDate: string;
  address: {
    line1: string;
    line2?: string;
    city: string;
    state: string;
    postalCode: string;
    country: string;
  };
  employmentType?: string;
  employerName?: string;
  annualIncome?: number;
  incomeCurrency?: string;
  preferredAccountType?: string;
}

export interface CustomerSummary {
  customerId: string;
  fullName: string;
  email: string;
  mobile: string;
  kycStatus: string;
  onboardingStatus: string;
  createdAt: string;
}

export interface CustomerResponse {
  customerId: string;
  firstName: string;
  lastName: string;
  fullName: string;
  dateOfBirth: string;
  gender: string;
  email: string;
  mobile: string;
  nationality: string;
  panNumber: string | null;
  idType: string;
  idExpiryDate: string;
  kycStatus: string;
  onboardingStatus: string;
  riskCategory: string;
  address: {
    line1: string;
    line2: string | null;
    city: string;
    state: string;
    postalCode: string;
    country: string;
    formatted: string;
  };
  employmentType: string | null;
  employerName: string | null;
  annualIncome: number | null;
  incomeCurrency: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface AccountSummary {
  accountId: string;
  accountNumber: string;
  accountType: string;
  status: string;
  balance: number;
  currency: string;
}

export interface AccountResponse {
  accountId: string;
  accountNumber: string;
  customerId: string;
  displayName: string | null;
  accountType: string;
  status: string;
  balance: number;
  availableBalance: number;
  holdAmount: number;
  currency: string;
  dailyDebitLimit: number | null;
  singleTransactionLimit: number | null;
  minimumBalance: number | null;
  interestRate: number | null;
  openedDate: string | null;
  maturityDate: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface BalanceResponse {
  accountId: string;
  accountNumber: string;
  balance: number;
  availableBalance: number;
  holdAmount: number;
  currency: string;
  status: string;
}

export interface PaymentSummary {
  paymentId: string;
  referenceNumber: string;
  amount: number;
  currency: string;
  paymentType: string;
  status: string;
  initiatedAt: string;
}

export interface PaymentResponse {
  paymentId: string;
  referenceNumber: string;
  customerId: string;
  sourceAccountId: string;
  destinationAccountId: string;
  amount: number;
  currency: string;
  paymentType: string;
  status: string;
  description: string | null;
  initiatedAt: string;
  completedAt: string | null;
  failureReason: string | null;
  fraudScore: number | null;
  fraudRiskLevel: string | null;
}

export interface DailySpendingSummary {
  accountId: string;
  totalSpentToday: number;
  transactionCount: number;
  averageTransactionSize: number;
  largestTransaction: number;
  currency: string;
}

