import { describe, expect, it } from 'vitest';
import {
  canRestoreRemembered,
  clearSession,
  forgetSession,
  persistSession,
  readRememberedToken,
  readSession,
  rememberSession,
  stashRememberIntent,
  takeRememberIntent,
} from './authStorage';

describe('authStorage', () => {
  it('round-trips a session through sessionStorage only', () => {
    persistSession({ id: 1 }, 'access', 'refresh');
    expect(readSession()).toEqual({ user: { id: 1 }, accessToken: 'access', refreshToken: 'refresh' });
    expect(localStorage.getItem('accessToken')).toBeNull();
    clearSession();
    expect(readSession()).toEqual({ user: null, accessToken: null, refreshToken: null });
  });

  it('starts signed out when the stored user is corrupt', () => {
    sessionStorage.setItem('user', '{not json');
    expect(readSession().user).toBeNull();
  });

  it('remembers only a refresh token, and restores only into an empty tab', () => {
    rememberSession('keep-me');
    expect(readRememberedToken()).toBe('keep-me');
    expect(canRestoreRemembered()).toBe(true);
    persistSession({}, 'a', 'r');
    expect(canRestoreRemembered()).toBe(false);
    forgetSession();
    expect(readRememberedToken()).toBeNull();
  });

  it('remember intent is read once', () => {
    stashRememberIntent(true);
    expect(takeRememberIntent()).toBe(true);
    expect(takeRememberIntent()).toBe(false);
  });
});
