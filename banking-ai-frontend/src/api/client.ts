import axios from "axios";

const API_KEY = import.meta.env.VITE_API_KEY ?? "banking-demo-key-2024";
const CLIENT_ID = import.meta.env.VITE_CLIENT_ID ?? "web-console";

export const apiClient = axios.create({
  baseURL: "/api",
  headers: {
    "X-API-Key": API_KEY,
    "X-Client-ID": CLIENT_ID,
    "Content-Type": "application/json"
  }
});

export interface ApiResponse<T> {
  success: boolean;
  message: string;
  data: T | null;
  errorCode?: string;
  timestamp: string;
}

export interface PagedResponse<T> {
  success: boolean;
  message: string;
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  last: boolean;
  errorCode?: string;
  timestamp: string;
}

export function unwrap<T>(response: ApiResponse<T>): T {
  if (!response.success) {
    throw new Error(response.message || "Request failed");
  }
  if (response.data == null) {
    throw new Error("Empty response payload");
  }
  return response.data;
}

