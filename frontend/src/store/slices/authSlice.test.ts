import { beforeEach, describe, expect, it, vi } from 'vitest';
import { configureStore } from '@reduxjs/toolkit';

vi.mock('../../services/api', () => ({ default: { post: vi.fn() } }));

import api from '../../services/api';
import reducer, {
  login,
  restoreRememberedSession,
  setCredentials,
  setRememberMe,
  setUser,
  verifyMfa,
  enableMfa,
  cancelMfa,
  beginMfa,
  signOut,
} from './authSlice';

const post = vi.mocked(api.post);
const makeStore = () => configureStore({ reducer: { auth: reducer } });
const user = { id: 1, name: 'Asha', email: 'asha@example.com', role: 'CUSTOMER' };

describe('authSlice', () => {
  beforeEach(() => vi.resetAllMocks());

  it('signs in and persists the session to this tab', async () => {
    post.mockResolvedValue({ data: { user, accessToken: 'a1', refreshToken: 'r1' } });
    const store = makeStore();
    await store.dispatch(login({ email: user.email, password: 'secret' }));
    expect(post).toHaveBeenCalledWith('/auth/login', { email: user.email, password: 'secret' });
    expect(store.getState().auth).toMatchObject({ isAuthenticated: true, user, accessToken: 'a1', error: null });
    expect(sessionStorage.getItem('accessToken')).toBe('a1');
  });

  it('surfaces a readable error on failure', async () => {
    post.mockRejectedValue(new Error('boom'));
    const store = makeStore();
    await store.dispatch(login({ email: 'x@y.z', password: 'bad' }));
    expect(store.getState().auth).toMatchObject({ isAuthenticated: false, loading: false, error: 'Login failed' });
  });

  const signedIn = async () => {
    post.mockResolvedValueOnce({ data: { user, accessToken: 'a1', refreshToken: 'r1' } });
    const store = makeStore();
    await store.dispatch(login({ email: user.email, password: 'secret' }));
    return store;
  };

  it('remember me stores an independent device token, never the tab token', async () => {
    const store = await signedIn();
    post.mockResolvedValueOnce({ data: { refreshToken: 'device-1' } });
    await store.dispatch(setRememberMe(true));
    expect(post).toHaveBeenLastCalledWith('/auth/refresh/device', { refreshToken: 'r1' });
    expect(localStorage.getItem('civeng.rememberedSession')).toBe('device-1');
  });

  it('does not remember anything when the device token is refused', async () => {
    const store = await signedIn();
    post.mockRejectedValueOnce(new Error('401'));
    await store.dispatch(setRememberMe(true));
    expect(localStorage.getItem('civeng.rememberedSession')).toBeNull();
  });

  it('unticking forgets without calling the server', async () => {
    const store = await signedIn();
    localStorage.setItem('civeng.rememberedSession', 'old');
    await store.dispatch(setRememberMe(false));
    expect(localStorage.getItem('civeng.rememberedSession')).toBeNull();
    expect(post).toHaveBeenCalledTimes(1);
  });

  it('setCredentials swaps the tab tokens only', async () => {
    const store = await signedIn();
    localStorage.setItem('civeng.rememberedSession', 'device-1');
    store.dispatch(setCredentials({ accessToken: 'a2', refreshToken: 'r2' }));
    expect(sessionStorage.getItem('refreshToken')).toBe('r2');
    expect(localStorage.getItem('civeng.rememberedSession')).toBe('device-1');
  });

  it('signOut clears locally and revokes both tokens on the server', async () => {
    const store = await signedIn();
    localStorage.setItem('civeng.rememberedSession', 'device-1');
    post.mockResolvedValueOnce({ data: {} });
    await store.dispatch(signOut());
    expect(store.getState().auth).toMatchObject({ isAuthenticated: false, user: null, accessToken: null });
    expect(sessionStorage.getItem('accessToken')).toBeNull();
    expect(localStorage.getItem('civeng.rememberedSession')).toBeNull();
    expect(post).toHaveBeenLastCalledWith(
      '/auth/logout',
      { refreshTokens: ['r1', 'device-1'] },
      { headers: { Authorization: 'Bearer a1' } }
    );
  });

  it('signOut still signs out locally when the server is unreachable', async () => {
    const store = await signedIn();
    post.mockRejectedValueOnce(new Error('offline'));
    await store.dispatch(signOut());
    expect(store.getState().auth.isAuthenticated).toBe(false);
  });

  it('restoring a remembered session keeps tab and device tokens separate', async () => {
    localStorage.setItem('civeng.rememberedSession', 'device-1');
    post
      .mockResolvedValueOnce({ data: { user, accessToken: 'a9', refreshToken: 'tab-9' } })
      .mockResolvedValueOnce({ data: { refreshToken: 'device-2' } });
    const store = makeStore();
    await store.dispatch(restoreRememberedSession());
    expect(post).toHaveBeenNthCalledWith(1, '/auth/refresh', { refreshToken: 'device-1' });
    expect(post).toHaveBeenNthCalledWith(2, '/auth/refresh/device', { refreshToken: 'tab-9' });
    expect(store.getState().auth).toMatchObject({ isAuthenticated: true, refreshToken: 'tab-9' });
    expect(localStorage.getItem('civeng.rememberedSession')).toBe('device-2');
  });

  it('a restore whose device token is refused still signs this tab in but forgets the device', async () => {
    localStorage.setItem('civeng.rememberedSession', 'device-1');
    post
      .mockResolvedValueOnce({ data: { user, accessToken: 'a9', refreshToken: 'tab-9' } })
      .mockRejectedValueOnce(new Error('401'));
    const store = makeStore();
    await store.dispatch(restoreRememberedSession());
    expect(store.getState().auth.isAuthenticated).toBe(true);
    expect(localStorage.getItem('civeng.rememberedSession')).toBeNull();
  });

  it('setUser merges a profile change into the signed-in user and this tab\'s stored copy', async () => {
    post.mockResolvedValue({ data: { user, accessToken: 'a1', refreshToken: 'r1' } });
    const store = makeStore();
    await store.dispatch(login({ email: user.email, password: 'secret' }));

    store.dispatch(setUser({ ...user, profilePicture: 'http://cdn/me.png' } as never));
    expect(store.getState().auth.user).toMatchObject({ id: 1, profilePicture: 'http://cdn/me.png' });
    expect(JSON.parse(sessionStorage.getItem('user')!).profilePicture).toBe('http://cdn/me.png');

    // A response for some other account (a stale request after switching users) is ignored.
    store.dispatch(setUser({ ...user, id: 2, name: 'Someone else' } as never));
    expect(store.getState().auth.user).toMatchObject({ id: 1, name: 'Asha' });
  });

  it('a sign-in that owes a second factor holds the ticket and stays signed out', async () => {
    post.mockResolvedValue({ data: { success: true, mfaRequired: true, mfaSetupRequired: false, mfaToken: 't1' } });
    const store = makeStore();
    await store.dispatch(login({ email: user.email, password: 'secret' }));
    expect(store.getState().auth).toMatchObject({ isAuthenticated: false, mfa: { token: 't1', setup: false } });
    expect(sessionStorage.getItem('accessToken')).toBeNull();

    post.mockResolvedValue({ data: { user, accessToken: 'a2', refreshToken: 'r2' } });
    await store.dispatch(verifyMfa('123456'));
    expect(post).toHaveBeenLastCalledWith('/auth/mfa/verify', { mfaToken: 't1', code: '123456' });
    expect(store.getState().auth).toMatchObject({ isAuthenticated: true, mfa: null, accessToken: 'a2' });
  });

  it('enrolment signs in and keeps the recovery codes only in memory', async () => {
    const store = makeStore();
    store.dispatch(beginMfa({ token: 't9', setup: true }));
    post.mockResolvedValue({ data: { user, accessToken: 'a3', refreshToken: 'r3', recoveryCodes: ['abcde-fghjk'] } });
    await store.dispatch(enableMfa('654321'));
    expect(post).toHaveBeenLastCalledWith('/auth/mfa/enable', { mfaToken: 't9', code: '654321' });
    expect(store.getState().auth.recoveryCodes).toEqual(['abcde-fghjk']);
    expect(JSON.stringify({ ...sessionStorage })).not.toContain('abcde-fghjk');
  });

  it('a wrong code keeps the ticket and reports the error; cancelling drops it', async () => {
    const store = makeStore();
    store.dispatch(beginMfa({ token: 't1', setup: false }));
    post.mockRejectedValue({ isAxiosError: true, response: { status: 400, data: { message: 'That code is not right.' } } });
    await store.dispatch(verifyMfa('000000'));
    expect(store.getState().auth).toMatchObject({ isAuthenticated: false, mfa: { token: 't1' } });
    expect(store.getState().auth.error).toMatch(/not right/);
    store.dispatch(cancelMfa());
    expect(store.getState().auth.mfa).toBeNull();
  });
});
