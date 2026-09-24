import { beforeEach, describe, expect, it, vi } from 'vitest';

vi.mock('./api', () => ({ default: { get: vi.fn() } }));

import api from './api';
import { lookupWorkspace } from './workspaceApi';

const get = vi.mocked(api.get);
const fail = (status: number, message?: string) => ({ isAxiosError: true, response: { status, data: { message } } });

describe('lookupWorkspace', () => {
  beforeEach(() => vi.resetAllMocks());

  it('returns the workspace this address serves, with its published branding over the onboarding copy', async () => {
    get.mockImplementation(async (url: string) => (url === '/tenant-resolution/current'
      ? { data: { tenantKey: 'acme', name: 'Acme Builders', branding: { brandName: 'Old', logoUrl: '/old.png' } } }
      : { data: { brandName: 'Acme', logoUrl: null } }) as never);
    expect(await lookupWorkspace()).toEqual({ kind: 'ok', workspace: {
      tenantKey: 'acme', name: 'Acme Builders', branding: { brandName: 'Acme', logoUrl: '/old.png' } } });
    expect(get).toHaveBeenCalledWith('/tenant-resolution/current');
    expect(get).toHaveBeenCalledWith('/ui-config/public/branding');
  });

  it('keeps the onboarding branding if the published one cannot be read', async () => {
    get.mockImplementation(async (url: string) => {
      if (url === '/ui-config/public/branding') throw new Error('down');
      return { data: { tenantKey: 'acme', name: 'Acme', branding: { brandName: 'Acme' } } } as never;
    });
    expect(await lookupWorkspace()).toMatchObject({ kind: 'ok', workspace: { branding: { brandName: 'Acme' } } });
  });

  it('only the gateway\'s own answers mean unknown or unavailable', async () => {
    get.mockRejectedValue(fail(404, 'No workspace is served at nope.example.com'));
    expect((await lookupWorkspace()).kind).toBe('unknown');
    get.mockRejectedValue(fail(503, 'This workspace is suspended'));
    expect((await lookupWorkspace()).kind).toBe('unavailable');
  });

  it('anything else carries on unbranded instead of blocking the app', async () => {
    for (const e of [fail(404, 'not mocked'), fail(500), fail(503, 'Service Unavailable'), new Error('Network Error')]) {
      get.mockRejectedValue(e);
      expect((await lookupWorkspace()).kind).toBe('undetermined');
    }
  });
});
