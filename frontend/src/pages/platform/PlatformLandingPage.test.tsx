import { beforeEach, describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';

const auth = { isAuthenticated: false, user: null as null | { role: string } };
const platform = { name: 'RK Technologies' };
vi.mock('../../hooks', () => ({ useAppSelector: (f: (s: unknown) => unknown) => f({ auth }) }));
vi.mock('../../hooks/usePlatformHost', () => ({ usePlatformName: () => platform.name }));

import PlatformLandingPage from './PlatformLandingPage';

describe('PlatformLandingPage', () => {
  beforeEach(() => { platform.name = 'RK Technologies'; });

  it('is about the platform company alone, with a staff sign-in', () => {
    render(<MemoryRouter><PlatformLandingPage /></MemoryRouter>);
    expect(screen.getByTestId('platform-name')).toHaveTextContent('RK Technologies');
    expect(screen.getByTestId('platform-diagram')).toHaveTextContent('Platform factory');
    expect(screen.getByRole('link', { name: /Staff sign in/ })).toHaveAttribute('href', '/login');
    // No tenant is named on the company's page.
    expect(screen.queryByText(/CivEngMarket/)).not.toBeInTheDocument();
    expect(screen.queryByText(/Register/)).not.toBeInTheDocument();
  });

  it('follows the platform\'s published name, so renaming the company changes the page', () => {
    platform.name = 'Acme Platforms';
    render(<MemoryRouter><PlatformLandingPage /></MemoryRouter>);
    expect(screen.getByTestId('platform-name')).toHaveTextContent('Acme Platforms');
    expect(screen.getByTestId('platform-diagram')).toHaveTextContent('Acme Platforms');
    expect(screen.queryByText(/RK Technologies/)).not.toBeInTheDocument();
  });
});
