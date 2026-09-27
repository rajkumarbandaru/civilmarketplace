import { beforeEach, describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';

const state: { role: string | undefined; workspace: { tenantKey: string; name: string; branding?: { brandName?: string } } | null } = {
  role: 'PLATFORM_OWNER', workspace: { tenantKey: 'platform', name: 'RK Technologies' },
};
vi.mock('../hooks', () => ({ useAppSelector: (f: (s: unknown) => unknown) => f({ auth: { user: state.role ? { role: state.role } : null } }) }));
vi.mock('../providers/WorkspaceProvider', () => ({ useWorkspace: () => state.workspace }));
vi.mock('../hooks/usePlatformHost', () => ({ usePlatformName: () => 'RK Technologies' }));

import ConsoleTierBadge from './ConsoleTierBadge';

describe('ConsoleTierBadge', () => {
  beforeEach(() => {
    state.role = 'PLATFORM_OWNER';
    state.workspace = { tenantKey: 'platform', name: 'RK Technologies' };
  });

  it('marks the RK platform console for platform staff', () => {
    render(<ConsoleTierBadge />);
    const badge = screen.getByTestId('console-tier');
    expect(badge).toHaveAttribute('data-tier', 'platform');
    expect(badge).toHaveTextContent('RK Technologies · Platform console — Platform owner');
  });

  it('marks a tenant admin with the tenant\'s own name', () => {
    state.role = 'TENANT_OWNER';
    state.workspace = { tenantKey: 'civengmarket', name: 'CivEngMarket', branding: { brandName: 'CivEngMarket' } };
    render(<ConsoleTierBadge />);
    const badge = screen.getByTestId('console-tier');
    expect(badge).toHaveAttribute('data-tier', 'tenant');
    expect(badge).toHaveTextContent('CivEngMarket · Tenant admin — Tenant owner');
  });

  it('renders nothing when signed out', () => {
    state.role = undefined;
    const { container } = render(<ConsoleTierBadge />);
    expect(container).toBeEmptyDOMElement();
  });
});
