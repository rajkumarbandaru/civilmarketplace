import { beforeEach, describe, expect, it, vi } from 'vitest';
import { fireEvent, render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';

const state = { role: 'ADMIN' };
vi.mock('../../hooks', () => ({ useAppSelector: (f: (s: unknown) => unknown) => f({ auth: { user: { role: state.role } } }) }));
vi.mock('../../providers/WorkspaceProvider', () => ({
  useWorkspace: () => ({ tenantKey: 'acme', name: 'acme', branding: { brandName: 'Acme Builders' } }),
}));
vi.mock('../../services/adminApi', () => ({ userApi: { getRoles: vi.fn(), inviteUser: vi.fn() } }));

import { userApi } from '../../services/adminApi';
import AddUserDialog, { roleLabel } from './AddUserDialog';

const mocked = vi.mocked(userApi);
const roles = ['ADMIN', 'CUSTOMER', 'SITE_ENGINEER', 'SUPER_ADMIN']
  .map((name) => ({ name, description: '', systemRole: true, userCount: 1 }));

const renderIt = (onAdded = vi.fn()) => {
  render(
    <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
      <AddUserDialog open onClose={vi.fn()} onAdded={onAdded} />
    </QueryClientProvider>
  );
  return onAdded;
};

const pickRole = async (label: string) => {
  fireEvent.mouseDown(within(screen.getByTestId('role-select')).getByRole('combobox'));
  await userEvent.click(await screen.findByRole('option', { name: label }));
};

describe('AddUserDialog', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    state.role = 'ADMIN';
    mocked.getRoles.mockResolvedValue({ data: { success: true, data: roles } } as never);
  });

  it('adds a user with a role and invites them to this workspace', async () => {
    mocked.inviteUser.mockResolvedValue({ data: { success: true, data: {
      userId: 7, email: 'ravi@acme.in', name: 'Ravi', role: 'SITE_ENGINEER', expiresAt: 'x' } } } as never);
    const onAdded = renderIt();
    await userEvent.type(screen.getByLabelText('Name'), 'Ravi');
    await userEvent.type(screen.getByLabelText(/Email/), 'ravi@acme.in');
    await pickRole('Site engineer');
    await userEvent.click(screen.getByRole('button', { name: 'Add and invite' }));
    expect(mocked.inviteUser).toHaveBeenCalledWith({ name: 'Ravi', email: 'ravi@acme.in', role: 'SITE_ENGINEER',
      linkBase: window.location.origin, workspaceName: 'Acme Builders' });
    expect(onAdded).toHaveBeenCalledWith(expect.objectContaining({ userId: 7 }));
  });

  it('needs a valid email and a role first', async () => {
    renderIt();
    await userEvent.type(screen.getByLabelText(/Email/), 'not-an-email');
    await userEvent.click(screen.getByRole('button', { name: 'Add and invite' }));
    expect(screen.getByText('Enter a valid email address')).toBeInTheDocument();
    expect(screen.getByText('Choose a role')).toBeInTheDocument();
    expect(mocked.inviteUser).not.toHaveBeenCalled();
  });

  it('offers the Super Admin role only to a Super Admin', async () => {
    renderIt();
    fireEvent.mouseDown(within(screen.getByTestId('role-select')).getByRole('combobox'));
    expect(await screen.findByRole('option', { name: 'Site engineer' })).toBeInTheDocument();
    expect(screen.queryByRole('option', { name: 'Super admin' })).not.toBeInTheDocument();
  });

  it('shows why the server refused', async () => {
    mocked.inviteUser.mockRejectedValue({ isAxiosError: true, response: { status: 400, data: {
      message: 'Someone with that email already has an account here' } } });
    renderIt();
    await userEvent.type(screen.getByLabelText(/Email/), 'asha@acme.in');
    await pickRole('Admin');
    await userEvent.click(screen.getByRole('button', { name: 'Add and invite' }));
    expect(await screen.findByText('Someone with that email already has an account here')).toBeInTheDocument();
  });
});

describe('roleLabel', () => {
  it('reads a role name as words', () => {
    expect(roleLabel('SITE_ENGINEER')).toBe('Site engineer');
  });
});
