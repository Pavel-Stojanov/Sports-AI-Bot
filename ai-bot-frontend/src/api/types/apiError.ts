/**
 * The error body every backend exception handler returns.
 */
export type ApiError = {
  status: number;
  message: string;
  timestamp: string;
};
