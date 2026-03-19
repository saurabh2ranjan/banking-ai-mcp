import { apiClient, unwrap, ApiResponse, PagedResponse } from "./client";
import type {
  PaymentResponse,
  PaymentSummary,
  DailySpendingSummary
} from "./types";

export interface InitiatePaymentRequest {
  customerId: string;
  sourceAccountId: string;
  destinationAccountId: string;
  amount: number;
  currency: string;
  paymentType: string;
  description?: string;
}

export async function initiatePayment(
  request: InitiatePaymentRequest
): Promise<PaymentResponse> {
  const { data } = await apiClient.post<ApiResponse<PaymentResponse>>(
    "/v1/payments",
    request
  );
  return unwrap(data);
}

export async function processPayment(
  paymentId: string
): Promise<PaymentResponse> {
  const { data } = await apiClient.post<ApiResponse<PaymentResponse>>(
    `/v1/payments/${encodeURIComponent(paymentId)}/process`
  );
  return unwrap(data);
}

export async function reversePayment(params: {
  paymentId: string;
  reason: string;
}): Promise<PaymentResponse> {
  const { data } = await apiClient.post<ApiResponse<PaymentResponse>>(
    `/v1/payments/${encodeURIComponent(params.paymentId)}/reverse`,
    null,
    { params: { reason: params.reason } }
  );
  return unwrap(data);
}

export async function getPayment(
  paymentId: string
): Promise<PaymentResponse> {
  const { data } = await apiClient.get<ApiResponse<PaymentResponse>>(
    `/v1/payments/${encodeURIComponent(paymentId)}`
  );
  return unwrap(data);
}

export async function getPayments(params: {
  accountId: string;
  page?: number;
  size?: number;
}): Promise<PagedResponse<PaymentSummary>> {
  const { data } = await apiClient.get<PagedResponse<PaymentSummary>>(
    "/v1/payments",
    {
      params: {
        accountId: params.accountId,
        page: params.page ?? 0,
        size: params.size ?? 20
      }
    }
  );
  return data;
}

export async function getDailySummary(
  accountId: string
): Promise<DailySpendingSummary> {
  const { data } = await apiClient.get<ApiResponse<DailySpendingSummary>>(
    `/v1/payments/accounts/${encodeURIComponent(accountId)}/daily-summary`
  );
  return unwrap(data);
}

