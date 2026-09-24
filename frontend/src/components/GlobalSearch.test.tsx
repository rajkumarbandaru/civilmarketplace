import { describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom';

// No network in unit tests: the hook's documented fallback is the shipped catalogue.
vi.mock('../services/catalogueApi', () => ({ fetchCatalogue: vi.fn().mockRejectedValue(new Error('offline')) }));

import GlobalSearch from './GlobalSearch';

const Where = () => {
  const location = useLocation();
  return <div data-testid="location">{location.pathname + location.search}</div>;
};

const renderSearch = () =>
  render(
    <MemoryRouter initialEntries={['/']}>
      <GlobalSearch />
      <Routes>
        <Route path="*" element={<Where />} />
      </Routes>
    </MemoryRouter>
  );

const input = () => screen.getByRole('textbox', { name: /search services/i });

describe('GlobalSearch', () => {
  it('suggests matching services as you type', async () => {
    renderSearch();
    await userEvent.type(input(), 'plumbing');
    expect(await screen.findByText('Plumbing Services')).toBeInTheDocument();
    expect(screen.getByText(/See all results for/)).toBeInTheDocument();
  });

  it('says so when nothing matches', async () => {
    renderSearch();
    await userEvent.type(input(), 'zzqqxx');
    expect(await screen.findByText(/Nothing matches/)).toBeInTheDocument();
  });

  it('Enter opens the full results page', async () => {
    renderSearch();
    await userEvent.type(input(), 'land survey{Enter}');
    expect(screen.getByTestId('location')).toHaveTextContent('/services?q=land%20survey');
  });

  it('clicking a suggestion opens its booking page', async () => {
    renderSearch();
    await userEvent.type(input(), 'plumbing');
    await userEvent.click(await screen.findByText('Plumbing Services'));
    expect(screen.getByTestId('location')).toHaveTextContent('/book/plumbing-services');
  });

  it('Escape closes the dropdown', async () => {
    renderSearch();
    await userEvent.type(input(), 'plumbing');
    await screen.findByText('Plumbing Services');
    await userEvent.keyboard('{Escape}');
    expect(screen.queryByText('Plumbing Services')).not.toBeInTheDocument();
  });
});
