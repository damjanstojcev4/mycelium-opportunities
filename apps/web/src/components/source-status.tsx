'use client';

import { useEffect, useState } from 'react';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { CheckCircle2, AlertCircle, Clock3 } from 'lucide-react';
import { api } from '@/lib/api';
import { Badge, ErrorState, Spinner } from './common';
import { Button } from './ui/button';

export type Source = {
  id: string;
  name: string;
  code: string;
  enabled: boolean;
  access_status: string;
  interval_minutes: number;
  last_success_at: string | null;
  last_error: string | null;
  lease_until?: string | null;
};
export type Run = {
  id: string;
  source_id: string;
  source_name: string;
  status: string;
  inserted: number;
  updated: number;
  rejected: number;
  error: string | null;
  started_at: string;
};

export function syncTime(value: string | null) {
  return value
    ? new Intl.DateTimeFormat('en-GB', {
        dateStyle: 'medium',
        timeStyle: 'medium',
        timeZone: 'Europe/Skopje',
      }).format(new Date(value))
    : 'Never';
}

export function SourceStatus({ source: s, latestRun }: { source: Source; latestRun?: Run }) {
  const [now, setNow] = useState(Date.now);
  useEffect(() => {
    const timer = setInterval(() => setNow(Date.now()), 1000);
    return () => clearInterval(timer);
  }, []);
  const client = useQueryClient();
  const refresh = async () => {
    await Promise.all(
      ['admin-sources', 'admin-runs', 'admin-review'].map((key) =>
        client.invalidateQueries({ queryKey: [key] }),
      ),
    );
  };
  const sync = useMutation({
    mutationFn: () => api<Run>(`/admin/sources/${s.id}/sync`, { method: 'POST' }),
    onSettled: refresh,
  });
  const configure = useMutation({
    mutationFn: () =>
      api(`/admin/sources/${s.id}`, {
        method: 'PATCH',
        body: JSON.stringify({
          enabled: !s.enabled,
          accessStatus: s.enabled ? s.access_status : 'VERIFIED',
          intervalMinutes: s.interval_minutes,
        }),
      }),
    onSuccess: refresh,
  });
  // The lease also covers scheduled runs and survives navigation or a page reload.
  const running = sync.isPending || !!(s.lease_until && new Date(s.lease_until).getTime() > now);
  const run =
    sync.data && (!latestRun || new Date(sync.data.started_at) > new Date(latestRun.started_at))
      ? sync.data
      : latestRun;
  const failed = run?.status === 'FAILED' || !!s.last_error;
  const partial = run?.status === 'PARTIAL';
  const interrupted = run?.status === 'RUNNING' && !running;
  const label = running
    ? 'Sync running'
    : failed
      ? 'Sync needs attention'
      : partial
        ? 'Partially synced'
        : interrupted
          ? 'Run needs checking'
          : s.last_success_at
            ? 'Synced'
            : 'Not synced yet';
  const tone = running
    ? 'active'
    : failed || partial || interrupted
      ? 'warning'
      : s.last_success_at
        ? 'success'
        : 'idle';
  return (
    <div className={`admin-row source-row ${running ? 'source-row-running' : ''}`}>
      <div className="source-info">
        <strong>{s.name}</strong> <Badge>{s.access_status}</Badge>
        <div className={`sync-status sync-status-${tone}`} role="status">
          {running ? (
            <Spinner />
          ) : tone === 'success' ? (
            <CheckCircle2 size={16} aria-hidden="true" />
          ) : tone === 'warning' ? (
            <AlertCircle size={16} aria-hidden="true" />
          ) : (
            <Clock3 size={16} aria-hidden="true" />
          )}
          <strong>{label}</strong>
        </div>
        <p>
          Last complete sync: <strong>{syncTime(s.last_success_at)}</strong> (Skopje time)
        </p>
        <p>
          {s.code === 'ESJN'
            ? 'Official-file import required'
            : s.enabled && s.access_status === 'VERIFIED'
              ? `Automatic sync enabled · Every ${s.interval_minutes} minutes`
              : 'Automatic sync paused'}
        </p>
        {running && <p>Collecting and validating source records. This may take a few minutes.</p>}
        {!running && run && run.status !== 'RUNNING' && (
          <p>
            Latest run: {run.inserted} new · {run.updated} updated · {run.rejected} rejected
          </p>
        )}
        {!running && partial && (
          <p>More records remain. The next automatic run will continue from the saved position.</p>
        )}
        {!running && interrupted && (
          <p>The run has no active lease. Check ingestion history before retrying.</p>
        )}
        {!running && (s.last_error || run?.error) && (
          <p className="field-error">{s.last_error || run?.error}</p>
        )}
        {sync.error && <ErrorState error={sync.error} />}
        {configure.error && <ErrorState error={configure.error} />}
        {running && (
          <div className="sync-activity" aria-hidden="true">
            <span />
          </div>
        )}
      </div>
      {s.code !== 'ESJN' && (
        <div className="admin-actions">
          <Button
            variant="outline"
            disabled={running}
            loading={configure.isPending}
            loadingText="Saving…"
            onClick={() => configure.mutate()}
          >
            {s.enabled ? 'Disable' : 'Verify access & enable'}
          </Button>
          <Button
            disabled={!s.enabled || configure.isPending}
            loading={running}
            loadingText="Syncing…"
            onClick={() => sync.mutate()}
          >
            Sync now
          </Button>
        </div>
      )}
    </div>
  );
}
