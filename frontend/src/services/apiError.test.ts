import { describe, expect, it } from 'vitest';
import { AxiosError, AxiosHeaders } from 'axios';
import { apiErrorMessage, normalizeApiError } from './apiError';

const withResponse = (status: number, data: unknown = {}) => {
  const error = new AxiosError('Request failed', 'ERR_BAD_RESPONSE');
  error.response = { status, data, statusText: '', headers: {}, config: { headers: new AxiosHeaders() } };
  return error;
};

describe('normalizeApiError', () => {
  it('uses the fallback for non-axios errors', () => {
    expect(normalizeApiError(new Error('x'), 'Login failed')).toEqual({ message: 'Login failed', retryable: false });
  });

  it('flags timeouts and network failures as retryable', () => {
    expect(normalizeApiError(new AxiosError('timeout of 30000ms exceeded', 'ECONNABORTED')).retryable).toBe(true);
    const network = normalizeApiError(new AxiosError('Network Error', 'ERR_NETWORK'));
    expect(network.message).toMatch(/Cannot reach the server/);
    expect(network.retryable).toBe(true);
  });

  it('prefers the server message for client errors', () => {
    expect(normalizeApiError(withResponse(401, { message: 'Bad password' })).message).toBe('Bad password');
    expect(normalizeApiError(withResponse(401)).message).toMatch(/Invalid credentials/);
  });

  it('hides server detail on 5xx and marks it retryable', () => {
    const result = normalizeApiError(withResponse(503, { message: 'stack trace' }));
    expect(result.message).toMatch(/temporarily unavailable/);
    expect(result).toMatchObject({ status: 503, retryable: true });
  });

  it('collects field validation errors', () => {
    const result = normalizeApiError(withResponse(400, {
      errors: [{ field: 'email', defaultMessage: 'must be valid' }, { field: 'name' }],
    }));
    expect(result.fieldErrors).toEqual({ email: 'must be valid', name: 'Invalid value' });
  });

  it('apiErrorMessage returns just the message', () => {
    expect(apiErrorMessage(withResponse(404))).toMatch(/could not find/);
  });
});
