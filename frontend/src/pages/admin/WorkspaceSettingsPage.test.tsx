import { beforeEach, describe, expect, it, vi } from 'vitest';
import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';

vi.mock('../../services/workspaceSettingsApi', async (orig) => ({
  ...(await orig<typeof import('../../services/workspaceSettingsApi')>()),
  fetchWorkspaceModules: vi.fn(),
  saveWorkspaceModules: vi.fn(),
}));
vi.mock('./TenantIntegrationsCard', () => ({
  IntegrationsPanel: ({ name }: { name: string }) => <div data-testid="integrations-panel">{name}</div>,
}));

import * as api from '../../services/workspaceSettingsApi';
import WorkspaceSettingsPage from './WorkspaceSettingsPage';

const mocked = vi.mocked(api);
const mod = (key: string, over: Partial<api.WorkspaceModule> = {}): api.WorkspaceModule =>
  ({ key, chosen: true, running: true, entitled: true, locked: false, ...over });
const state: api.WorkspaceModules = {
  tenantKey: 'acme', planName: 'Professional',
  modules: [
    mod('auth', { locked: true }), mod('users', { locked: true }),
    mod('bookings'), mod('projects', { chosen: false, running: false }),
    mod('procurement', { chosen: true, running: false, entitled: false }),
    mod('leases', { chosen: false, running: false, entitled: false }),
  ],
};

const renderPage = () => render(
  <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
    <MemoryRouter><WorkspaceSettingsPage /></MemoryRouter>
  </QueryClientProvider>
);

describe('WorkspaceSettingsPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mocked.fetchWorkspaceModules.mockResolvedValue(state);
    mocked.saveWorkspaceModules.mockResolvedValue(state);
  });

  it('shows the plan, what runs, and why a module cannot be changed', async () => {
    renderPage();
    expect(await screen.findByText('Professional plan')).toBeInTheDocument();
    expect(within(screen.getByTestId('module-bookings')).getByText('Running')).toBeInTheDocument();
    expect(screen.getByTestId('module-procurement')).toHaveTextContent('Not in your plan — kept, but not running');
    expect(within(screen.getByTestId('module-leases')).getByRole('checkbox')).toBeDisabled();
    expect(screen.queryByTestId('module-auth')).not.toBeInTheDocument();
    expect(screen.getByText(/Always on: Auth/)).toBeInTheDocument();
    expect(screen.getByTestId('integrations-panel')).toHaveTextContent('your workspace');
  });

  it('switching a module on sends the optional set, never the locked ones', async () => {
    renderPage();
    await userEvent.click(within(await screen.findByTestId('module-projects')).getByRole('checkbox'));
    expect(mocked.saveWorkspaceModules).toHaveBeenCalledWith(['bookings', 'projects', 'procurement']);
  });

  it('a refused change puts the switch back', async () => {
    mocked.saveWorkspaceModules.mockRejectedValue({ isAxiosError: true, response: { status: 400, data: {
      message: 'Not in this tenant\'s plan: projects.' } } });
    renderPage();
    const box = within(await screen.findByTestId('module-projects')).getByRole('checkbox');
    await userEvent.click(box);
    expect(await screen.findByText("Not in this tenant's plan: projects.")).toBeInTheDocument();
    expect(box).not.toBeChecked();
  });

  it('switching a module off drops it', async () => {
    renderPage();
    await userEvent.click(within(await screen.findByTestId('module-bookings')).getByRole('checkbox'));
    expect(mocked.saveWorkspaceModules).toHaveBeenCalledWith(['procurement']);
  });

  it('says so when this workspace has no settings of its own (the operator workspace)', async () => {
    mocked.fetchWorkspaceModules.mockRejectedValue({ isAxiosError: true, response: { status: 400, data: {
      message: 'The operator workspace is configured by the platform itself' } } });
    renderPage();
    expect(await screen.findByTestId('workspace-settings-unavailable')).toHaveTextContent('configured by the platform itself');
  });
});

describe('toggledModules', () => {
  it('keeps every other choice as it is', () => {
    expect(api.toggledModules(state.modules, 'leases', true)).toEqual(['bookings', 'procurement', 'leases']);
  });
});
