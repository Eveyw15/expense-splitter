import { isAxiosError } from "axios";

export function apiErrorMessage(error: unknown, fallback: string): string {
  if (isAxiosError<{ message?: string }>(error) && error.response?.data?.message) {
    return error.response.data.message;
  }
  return error instanceof Error && !isAxiosError(error) ? error.message : fallback;
}
