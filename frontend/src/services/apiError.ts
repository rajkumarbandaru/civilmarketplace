import { AxiosError } from 'axios';

/** Shape the backend services use for error bodies. */
interface ApiErrorBody {
  message?: string;
  error?: string;
  errors?: Array<{ field?: string; defaultMessage?: string; message?: string }>;
}

export interface NormalizedApiError {
  /** Message safe to show the user. */
  message: string;
  /** HTTP status, when the request reached a server at all. */
  status?: number;
  /** True when retrying could plausibly succeed (timeout, network, 5xx). */
  retryable: boolean;
  /** Per-field validation messages, keyed by field name. */
  fieldErrors?: Record<string, string>;
}

const GENERIC = 'Something went wrong. Please try again.';

/**
 * Turns any axios failure into a message worth showing a user.
 *
 * Without this, a dropped connection, a gateway timeout and a rejected password all
 * surface as the same opaque fallback string, which is what made a stale
 * "Login failed" impossible to tell apart from a real outage.
 *
 * @param fallback message for the case where the server sent no usable detail
 */
export const normalizeApiError = (
  error: unknown,
  fallback: string = GENERIC
): NormalizedApiError => {
  const axiosError = error as AxiosError<ApiErrorBody>;

  if (!axiosError?.isAxiosError) {
    return { message: fallback, retryable: false };
  }

  // Request was aborted client-side before any response arrived.
  if (axiosError.code === 'ECONNABORTED' || /timeout/i.test(axiosError.message || '')) {
    return {
      message: 'The server took too long to respond. Please try again.',
      retryable: true,
    };
  }

  if (axiosError.code === 'ERR_CANCELED') {
    return { message: 'Request cancelled.', retryable: false };
  }

  // No response at all — server unreachable, DNS failure, CORS block, offline.
  if (!axiosError.response) {
    const offline = typeof navigator !== 'undefined' && navigator.onLine === false;
    return {
      message: offline
        ? 'You appear to be offline. Check your connection and try again.'
        : 'Cannot reach the server. Check your connection and try again.',
      retryable: true,
    };
  }

  const { status, data } = axiosError.response;
  const serverMessage = data?.message || data?.error;

  // Bean-validation failures come back as a list of field errors.
  const fieldErrors = data?.errors?.reduce<Record<string, string>>((acc, e) => {
    if (e.field) acc[e.field] = e.defaultMessage || e.message || 'Invalid value';
    return acc;
  }, {});

  const message = (() => {
    switch (status) {
      case 400:
      case 422:
        return serverMessage || 'Please check the details you entered and try again.';
      case 401:
        return serverMessage || 'Invalid credentials. Please check your details and try again.';
      case 403:
        return serverMessage || 'You do not have permission to do that.';
      case 404:
        return serverMessage || 'We could not find what you were looking for.';
      case 409:
        return serverMessage || 'That conflicts with something that already exists.';
      case 429:
        return serverMessage || 'Too many attempts. Please wait a moment and try again.';
      case 502:
      case 503:
        return 'The service is temporarily unavailable. Please try again shortly.';
      case 504:
        return 'The request timed out at the gateway. Please try again.';
      default:
        if (status >= 500) return GENERIC;
        return serverMessage || fallback;
    }
  })();

  return {
    message,
    status,
    retryable: status >= 500 || status === 429,
    fieldErrors: fieldErrors && Object.keys(fieldErrors).length ? fieldErrors : undefined,
  };
};

/** Convenience for thunks that only need the message. */
export const apiErrorMessage = (error: unknown, fallback?: string): string =>
  normalizeApiError(error, fallback).message;
