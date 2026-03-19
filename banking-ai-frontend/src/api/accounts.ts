import { apiClient, unwrap, ApiResponse } from "./client";
import type {
  AccountSummary,
  AccountResponse,
  BalanceResponse
} from "./types";

export interface OpenAccountRequest {
  customerId: string;
  accountType: string;
  currency: string;
  displayName?: string;
  initialDeposit?: number;
  dailyDebitLimit?: number;
  singleTransactionLimit?: number;
}

export async function openAccount(
  request: OpenAccountRequest
): Promise<AccountResponse> {
  const { data } = await apiClient.post<ApiResponse<AccountResponse>>(
    "/v1/accounts",
    request
  );
  return unwrap(data);
}

export async function getAccount(
  accountId: string
): Promise<AccountResponse> {
  const { data } = await apiClient.get<ApiResponse<AccountResponse>>(
    `/v1/accounts/${encodeURIComponent(accountId)}`
  );
  return unwrap(data);
}

export async function getBalance(
  accountId: string
): Promise<BalanceResponse> {
  const { data } = await apiClient.get<ApiResponse<BalanceResponse>>(
    `/v1/accounts/${encodeURIComponent(accountId)}/balance`
  );
  return unwrap(data);
}

export async function getCustomerAccounts(
  customerId: string
): Promise<AccountSummary[]> {
  const { data } = await apiClient.get<ApiResponse<AccountSummary[]>>(
    "/v1/accounts",
    { params: { customerId } }
  );
  return unwrap(data);
}

export async function blockAccount(params: {
  accountId: string;
  reason: string;
}): Promise<AccountResponse> {
  const { data } = await apiClient.post<ApiResponse<AccountResponse>>(
    `/v1/accounts/${encodeURIComponent(params.accountId)}/block`,
    null,
    { params: { reason: params.reason } }
  );
  return unwrap(data);
}

export async function unblockAccount(
  accountId: string
): Promise<AccountResponse> {
  const { data } = await apiClient.post<ApiResponse<AccountResponse>>(
    `/v1/accounts/${encodeURIComponent(accountId)}/unblock`
  );
  return unwrap(data);
}

