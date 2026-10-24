import React from 'react';
import { act, fireEvent, render, screen } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { api } from '@/lib/api';
import { SourceStatus, type Source, type Run } from './source-status';
import { Admin } from './admin';

vi.mock('@/lib/api', async (original) => ({
  ...(await original<typeof import('@/lib/api')>()),
  api: vi.fn(),
}));
const source: Source = {
  id: 'source-1',
  name: 'Official source',
  code: 'EU_FUNDING',
  enabled: true,
  access_status: 'VERIFIED',
  interval_minutes: 60,
  last_success_at: '2026-10-23T10:00:00Z',
  last_error: null,
};
const run: Run = {
  id: 'run-1',
  source_id: source.id,
  source_name: source.name,
  status: 'SUCCESS',
  inserted: 3,
  updated: 2,
  rejected: 0,
  error: null,
  started_at: '2026-10-24T10:00:00Z',
};
function show(ui: React.ReactNode) {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
  return render(<QueryClientProvider client={client}>{ui}</QueryClientProvider>);
}
beforeEach(() => {
  vi.mocked(api).mockReset();
});

describe('source synchronization feedback', () => {
  it('shows pending feedback, prevents duplicate syncs, then reports a partial result', async () => {
    let finish!: (result: Run) => void;
    vi.mocked(api).mockImplementation(
      () =>
        new Promise((resolve) => {
          finish = resolve as typeof finish;
        }),
    );
    show(<SourceStatus source={source} />);
    fireEvent.click(screen.getByRole('button', { name: 'Sync now' }));
    expect(await screen.findByText('Sync running')).toBeVisible();
    expect(screen.getByRole('button', { name: 'Syncing…' })).toBeDisabled();
    await act(async () => finish({ ...run, status: 'PARTIAL' }));
    expect(await screen.findByText('Partially synced')).toBeVisible();
    expect(screen.getByText('Latest run: 3 new · 2 updated · 0 rejected')).toBeVisible();
    expect(screen.getByRole('button', { name: 'Sync now' })).toBeEnabled();
  });
  it('does not call an HTTP-success response with FAILED status synced', async () => {
    vi.mocked(api).mockResolvedValue({ ...run, status: 'FAILED', error: 'Source unavailable' });
    show(<SourceStatus source={source} />);
    fireEvent.click(screen.getByRole('button', { name: 'Sync now' }));
    expect(await screen.findByText('Sync needs attention')).toBeVisible();
    expect(screen.getByText('Source unavailable')).toBeVisible();
    expect(screen.queryByText('Synced')).not.toBeInTheDocument();
  });
  it('shows a scheduled sync from its active lease without a local click', () => {
    show(
      <SourceStatus
        source={{ ...source, lease_until: new Date(Date.now() + 60000).toISOString() }}
        latestRun={{ ...run, status: 'RUNNING' }}
      />,
    );
    expect(screen.getByText('Sync running')).toBeVisible();
    expect(screen.getByRole('button', { name: 'Syncing…' })).toBeDisabled();
  });
  it('keeps the successful sync time visible when automatic sync is paused', () => {
    show(<SourceStatus source={{ ...source, enabled: false }} latestRun={run} />);
    expect(screen.getByText('Synced')).toBeVisible();
    expect(screen.getByText('Automatic sync paused')).toBeVisible();
    expect(screen.getByText(/23 Oct 2026/)).toBeVisible();
  });
  it('distinguishes loading review data from an empty queue', async () => {
    vi.mocked(api).mockImplementation((path) =>
      path === '/admin/sources' ? Promise.resolve([source]) : new Promise(() => {}),
    );
    show(<Admin />);
    expect(await screen.findByText('Loading review queue…')).toBeVisible();
    expect(screen.queryByText('No records currently awaiting review.')).not.toBeInTheDocument();
    expect(screen.getByText('Loading ingestion history…')).toBeVisible();
  });
});
