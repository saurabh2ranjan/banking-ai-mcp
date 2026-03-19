import { apiClient, unwrap, ApiResponse, PagedResponse } from "./client";
import type {
  OnboardingRequest,
  CustomerSummary,
  CustomerResponse
} from "./types";

export async function initiateOnboarding(
  request: OnboardingRequest
): Promise<CustomerResponse> {
  const { data } = await apiClient.post<ApiResponse<CustomerResponse>>(
    "/v1/onboarding/customers",
    request
  );
  return unwrap(data);
}

export async function getCustomer(
  customerId: string
): Promise<CustomerResponse> {
  const { data } = await apiClient.get<ApiResponse<CustomerResponse>>(
    `/v1/onboarding/customers/${encodeURIComponent(customerId)}`
  );
  return unwrap(data);
}

export async function getCustomersByStatus(
  status: string,
  page = 0,
  size = 20
): Promise<PagedResponse<CustomerSummary>> {
  const { data } = await apiClient.get<
    ApiResponse<PagedResponse<CustomerSummary>>
  >("/v1/onboarding/customers", { params: { status, page, size } });
  return unwrap(data);
}

export async function getPendingKyc(
  page = 0,
  size = 20
): Promise<PagedResponse<CustomerSummary>> {
  const { data } = await apiClient.get<
    ApiResponse<PagedResponse<CustomerSummary>>
  >("/v1/onboarding/kyc/pending", { params: { page, size } });
  return unwrap(data);
}

export async function updateKycStatus(params: {
  customerId: string;
  kycStatus: string;
  rejectionReason?: string;
}): Promise<CustomerResponse> {
  const { data } = await apiClient.patch<ApiResponse<CustomerResponse>>(
    `/v1/onboarding/customers/${encodeURIComponent(params.customerId)}/kyc`,
    {
      kycStatus: params.kycStatus,
      rejectionReason: params.rejectionReason
    }
  );
  return unwrap(data);
}

export async function completeOnboarding(
  customerId: string
): Promise<CustomerResponse> {
  const { data } = await apiClient.post<ApiResponse<CustomerResponse>>(
    `/v1/onboarding/customers/${encodeURIComponent(customerId)}/complete`
  );
  return unwrap(data);
}

