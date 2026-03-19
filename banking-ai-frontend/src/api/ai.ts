import { apiClient, unwrap, ApiResponse } from "./client";

export interface ChatTurn {
  role: "user" | "ai";
  message: string;
  timestamp: string;
}

export async function chat(
  message: string,
  sessionId: string
): Promise<{
  sessionId: string;
  response: string;
}> {
  const { data } = await apiClient.post<
    ApiResponse<{
      sessionId: string;
      response: string;
    }>
  >("/v1/banking-ai/chat", {
    message,
    sessionId
  });
  return unwrap(data);
}

export async function singleQuery(
  message: string
): Promise<string> {
  const { data } = await apiClient.post<ApiResponse<string>>(
    "/v1/banking-ai/query",
    { message }
  );
  return unwrap(data);
}

export async function getHistory(
  sessionId: string
): Promise<Array<{ role: string; message: string }>> {
  const { data } = await apiClient.get<
    ApiResponse<Array<{ role: string; message: string }>>
  >(`/v1/banking-ai/sessions/${encodeURIComponent(sessionId)}/history`);
  return unwrap(data);
}

export async function clearSession(
  sessionId: string
): Promise<string> {
  const { data } = await apiClient.delete<ApiResponse<string>>(
    `/v1/banking-ai/sessions/${encodeURIComponent(sessionId)}`
  );
  return unwrap(data);
}

export async function getSessionStats(): Promise<Record<string, unknown>> {
  const { data } = await apiClient.get<ApiResponse<Record<string, unknown>>>(
    "/v1/banking-ai/sessions"
  );
  return unwrap(data);
}

