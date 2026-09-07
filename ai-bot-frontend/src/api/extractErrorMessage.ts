import axios from 'axios';
import type { ApiError } from './types/apiError.ts';

/**
 * The message to show the user for a failed request.
 *
 * An axios failure is itself an Error whose message is only the status line
 * ("Request failed with status code 502"), so the backend's own message has to
 * be read out of the response body.
 */
const extractErrorMessage = (error: unknown, fallback: string): string => {
  if (axios.isAxiosError<ApiError>(error)) {
    return error.response?.data?.message ?? fallback;
  }
  return fallback;
};

export default extractErrorMessage;
