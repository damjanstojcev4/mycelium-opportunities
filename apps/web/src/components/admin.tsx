'use client';
import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { api, date } from '@/lib/api';
import { Heading, Loading, ErrorState, Badge } from './common';
import { Button } from './ui/button';
import { SourceStatus, syncTime, type Source, type Run } from './source-status';
type Review = {
  id: string;
  title: string;
  source_url: string;
  description: string | null;
  deadline_at: string | null;
  details: Record<string, unknown>;
};
type Preview = { row: number; record?: Record<string, unknown>; error?: string };
export function Admin() {
  const client = useQueryClient();
  const sources = useQuery({
    queryKey: ['admin-sources'],
    queryFn: () => api<Source[]>('/admin/sources'),
    refetchInterval: 5000,
  });
  const queue = useQuery({
    queryKey: ['admin-review'],
    queryFn: () => api<Review[]>('/admin/review-queue'),
    enabled: !!sources.data,
  });
  const runs = useQuery({
    queryKey: ['admin-runs'],
    queryFn: () => api<Run[]>('/admin/ingestion-runs'),
    refetchInterval: 5000,
    enabled: !!sources.data,
  });
  const mutation = useMutation({
    mutationFn: ({
      path,
      body,
      method = 'POST',
    }: {
      path: string;
      body?: unknown;
      method?: string;
    }) => api(path, { method, body: body ? JSON.stringify(body) : undefined }),
    onSuccess: () =>
      Promise.all(
        ['admin-sources', 'admin-review', 'admin-runs'].map((key) =>
          client.invalidateQueries({ queryKey: [key] }),
        ),
      ),
  });
  const [sourceId, setSourceId] = useState('');
  const [type, setType] = useState('TENDER');
  const [file, setFile] = useState<File | null>(null);
  const [mapping, setMapping] = useState(
    '{"externalId":"external_id","title":"title","sourceUrl":"source_url","issuer":"issuer","country":"country","deadlineAt":"deadline"}',
  );
  const preview = useMutation({
    mutationFn: async () => {
      if (!file) throw new Error('Choose a CSV or XLSX export.');
      JSON.parse(mapping);
      const form = new FormData();
      form.set('file', file);
      form.set('mapping', mapping);
      form.set('type', type);
      return api<Preview[]>('/admin/opportunities/import/preview', { method: 'POST', body: form });
    },
  });
  const imported = useMutation({
    mutationFn: () =>
      api<{ inserted: number; updated: number; duplicates: number; errors: string[] }>(
        '/admin/opportunities/import',
        {
          method: 'POST',
          body: JSON.stringify({ sourceId, records: preview.data?.map((r) => r.record) }),
        },
      ),
    onSuccess: () => {
      client.invalidateQueries({ queryKey: ['admin-review'] });
      preview.reset();
    },
  });
  if (sources.isPending)
    return (
      <>
        <Heading eyebrow="SOURCE OPERATIONS" title="Trust starts at the source.">
          Monitor collection, review changes and import permitted official datasets.
        </Heading>
        <section className="panel">
          <Loading label="Loading source status…" />
          <div className="skeleton-rows" aria-hidden="true">
            <span />
            <span />
            <span />
          </div>
        </section>
      </>
    );
  if (sources.error && !sources.data) return <ErrorState error={sources.error} />;
  return (
    <>
      <Heading eyebrow="SOURCE OPERATIONS" title="Trust starts at the source.">
        Monitor collection, review changes and import permitted official datasets.
      </Heading>
      {mutation.error && <ErrorState error={mutation.error} />}
      <section className="panel">
        <h3>Registered sources</h3>
        <p className="muted">Status refreshes every 5 seconds while this panel is open.</p>
        {sources.error && (
          <ErrorState
            error={
              new Error(
                'Source status could not refresh. Displayed information may be out of date.',
              )
            }
          />
        )}
        <div className="notice">
          Mark access verified only after confirming the source’s access and reuse conditions. A
          successful fetch alone does not establish permission.
        </div>
        {sources.data?.map((s) => (
          <SourceStatus
            key={s.id}
            source={s}
            latestRun={runs.data?.find((run) => run.source_id === s.id)}
          />
        ))}
      </section>
      <section className="panel">
        <h3>Review queue</h3>
        <p className="muted">
          Check the publication, classification, conditions and deadlines before approving. Changed
          records require a new review.
        </p>
        {queue.error && <ErrorState error={queue.error} />}{' '}
        {queue.isPending ? (
          <Loading label="Loading review queue…" />
        ) : queue.data?.length ? (
          queue.data.map((r) => (
            <div className="admin-row" key={r.id}>
              <div>
                <strong>{r.title}</strong>
                <p>Deadline: {date(r.deadline_at)}</p>
                <a
                  className="source-link"
                  href={r.source_url}
                  target="_blank"
                  rel="noopener noreferrer"
                >
                  Read original publication ↗
                </a>
                <details>
                  <summary>Extracted fields</summary>
                  <p>{r.description}</p>
                  <pre>{JSON.stringify(r.details, null, 2)}</pre>
                </details>
                <DocumentUpload opportunityId={r.id} />
              </div>
              <div className="admin-actions">
                <Button
                  disabled={mutation.isPending}
                  onClick={() =>
                    mutation.mutate({
                      path: `/admin/opportunities/${r.id}/review`,
                      method: 'PATCH',
                      body: { status: 'APPROVED' },
                    })
                  }
                  loading={
                    mutation.isPending &&
                    mutation.variables?.path === `/admin/opportunities/${r.id}/review` &&
                    (mutation.variables?.body as { status?: string })?.status === 'APPROVED'
                  }
                  loadingText="Approving…"
                >
                  Approve
                </Button>
                <Button
                  variant="outline"
                  disabled={mutation.isPending}
                  onClick={() =>
                    mutation.mutate({
                      path: `/admin/opportunities/${r.id}/review`,
                      method: 'PATCH',
                      body: { status: 'REJECTED' },
                    })
                  }
                  loading={
                    mutation.isPending &&
                    mutation.variables?.path === `/admin/opportunities/${r.id}/review` &&
                    (mutation.variables?.body as { status?: string })?.status === 'REJECTED'
                  }
                  loadingText="Rejecting…"
                >
                  Reject
                </Button>
              </div>
            </div>
          ))
        ) : (
          !queue.error && <p className="muted">No records currently awaiting review.</p>
        )}
      </section>
      <section className="panel">
        <h3>Official export import</h3>
        <p className="muted">
          CSV (UTF-8) or XLSX · Up to 1,000 rows / 5 MB · ISO 8601 deadlines with timezone required.
          Imported records remain pending review.
        </p>
        <fieldset className="import-fields" disabled={preview.isPending || imported.isPending}>
          <div className="form-grid">
            <label>
              Source
              <select
                value={sourceId}
                onChange={(e) => {
                  setSourceId(e.target.value);
                  preview.reset();
                }}
              >
                <option value="">Choose a source</option>
                {sources.data?.map((s) => (
                  <option value={s.id} key={s.id}>
                    {s.name}
                  </option>
                ))}
              </select>
            </label>
            <label>
              Opportunity type
              <select
                value={type}
                onChange={(e) => {
                  setType(e.target.value);
                  preview.reset();
                }}
              >
                <option>TENDER</option>
                <option>GRANT</option>
              </select>
            </label>
            <label className="full">
              Official export
              <input
                type="file"
                accept=".csv,.xlsx"
                onChange={(e) => {
                  setFile(e.target.files?.[0] || null);
                  preview.reset();
                }}
              />
            </label>
            <label className="full">
              Column mapping (normalized field → export column)
              <textarea
                value={mapping}
                onChange={(e) => {
                  setMapping(e.target.value);
                  preview.reset();
                }}
              />
            </label>
          </div>
        </fieldset>
        <Button
          variant="outline"
          loading={preview.isPending}
          loadingText="Validating export…"
          disabled={!file || !sourceId || imported.isPending}
          onClick={() => preview.mutate()}
        >
          Preview & validate
        </Button>
        {preview.error && <ErrorState error={preview.error} />}{' '}
        {preview.data && (
          <>
            <div className="table-wrap">
              <table>
                <thead>
                  <tr>
                    <th>Row</th>
                    <th>Title</th>
                    <th>Validation</th>
                  </tr>
                </thead>
                <tbody>
                  {preview.data.map((r) => (
                    <tr key={r.row}>
                      <td>{r.row}</td>
                      <td>{String(r.record?.title || '—')}</td>
                      <td>{r.error || 'Ready for review'}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
            <Button
              disabled={
                preview.isPending ||
                imported.isPending ||
                !preview.data.length ||
                preview.data.some((r) => r.error)
              }
              loading={imported.isPending}
              loadingText="Importing records…"
              onClick={() => imported.mutate()}
            >
              Confirm import into review queue
            </Button>
          </>
        )}
        {imported.error && <ErrorState error={imported.error} />}{' '}
        {imported.data && (
          <div className="notice" role="status">
            Imported {imported.data.inserted}; updated {imported.data.updated}; unchanged{' '}
            {imported.data.duplicates}. {imported.data.errors.join('; ')}
          </div>
        )}
      </section>
      <ManualEntry sources={sources.data || []} />
      <section className="panel">
        <h3>Ingestion history</h3>
        {runs.error && <ErrorState error={runs.error} />}
        {runs.isPending ? (
          <Loading label="Loading ingestion history…" />
        ) : !runs.error && !runs.data?.length ? (
          <p className="muted">No sync runs yet. Start a source sync to see its results here.</p>
        ) : null}
        <div className="table-wrap">
          <table>
            <thead>
              <tr>
                <th>Source</th>
                <th>Run</th>
                <th>Result</th>
                <th>New / Updated / Rejected</th>
                <th>Diagnostic</th>
              </tr>
            </thead>
            <tbody>
              {runs.data?.map((r) => (
                <tr key={r.id}>
                  <td>{r.source_name}</td>
                  <td>{syncTime(r.started_at)}</td>
                  <td>
                    <Badge>{r.status === 'RUNNING' ? 'RUNNING · awaiting result' : r.status}</Badge>
                  </td>
                  <td>
                    {r.inserted} / {r.updated} / {r.rejected}
                  </td>
                  <td>{r.error || '—'}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </section>
    </>
  );
}
function DocumentUpload({ opportunityId }: { opportunityId: string }) {
  const upload = useMutation({
    mutationFn: (form: FormData) =>
      api<{ status: string; text: string }>(`/admin/opportunities/${opportunityId}/documents`, {
        method: 'POST',
        body: form,
      }),
  });
  return (
    <details>
      <summary>Attach a permitted official PDF</summary>
      <form
        onSubmit={(e) => {
          e.preventDefault();
          upload.mutate(new FormData(e.currentTarget));
        }}
      >
        <label>
          Original document URL
          <input type="url" name="sourceUrl" required />
        </label>
        <label>
          PDF file (up to 5 MB)
          <input type="file" accept=".pdf" name="file" required />
        </label>
        <Button variant="outline" loading={upload.isPending} loadingText="Uploading & extracting…">
          Upload & extract text
        </Button>
      </form>
      {upload.error && <ErrorState error={upload.error} />}{' '}
      {upload.data && (
        <>
          <Badge>{upload.data.status}</Badge>
          <pre>
            {upload.data.text || 'No digital text found. Manual review or OCR is required.'}
          </pre>
        </>
      )}
    </details>
  );
}

function ManualEntry({ sources }: { sources: Source[] }) {
  const client = useQueryClient();
  const mutation = useMutation({
    mutationFn: async (form: FormData) => {
      const sourceUrl = String(form.get('sourceUrl'));
      const deadline = String(form.get('deadline') || '').trim();
      const evidence = String(form.get('deadlineEvidence') || '').trim();
      if (deadline && !evidence)
        throw new Error('Provide the supporting deadline passage from the publication.');
      const record = {
        externalId: sourceUrl,
        type: form.get('type'),
        title: form.get('title'),
        description: form.get('description'),
        issuer: form.get('issuer'),
        country: null,
        publishedAt: null,
        opensAt: null,
        deadlineAt: deadline || null,
        value: null,
        currency: null,
        status: 'UNKNOWN',
        sourceUrl,
        language: 'mk',
        details: {
          entryMethod: 'Manual; reviewed before publication',
          eligibilityRequirements: String(form.get('eligibility') || ''),
        },
        documents: [],
        evidence: deadline ? { deadlineAt: evidence } : {},
      };
      const report = await api<{ errors: string[] }>('/admin/opportunities/import', {
        method: 'POST',
        body: JSON.stringify({ sourceId: form.get('sourceId'), records: [record] }),
      });
      if (report.errors.length) throw new Error(report.errors.join('; '));
      return report;
    },
    onSuccess: () => client.invalidateQueries({ queryKey: ['admin-review'] }),
  });
  return (
    <section className="panel">
      <h3>Manual official-source entry</h3>
      <form
        onSubmit={(e) => {
          e.preventDefault();
          mutation.mutate(new FormData(e.currentTarget));
        }}
      >
        <div className="form-grid">
          <label>
            Source
            <select name="sourceId" required>
              {sources.map((s) => (
                <option key={s.id} value={s.id}>
                  {s.name}
                </option>
              ))}
            </select>
          </label>
          <label>
            Type
            <select name="type">
              <option>TENDER</option>
              <option>GRANT</option>
            </select>
          </label>
          <label className="full">
            Title
            <input name="title" required />
          </label>
          <label>
            Source organization
            <input name="issuer" required />
          </label>
          <label>
            Official publication URL
            <input type="url" name="sourceUrl" required />
          </label>
          <label className="full">
            Description
            <textarea name="description" />
          </label>
          <label>
            Confirmed deadline (optional, ISO 8601 with offset)
            <input name="deadline" placeholder="2026-12-01T15:00:00+01:00" />
          </label>
          <label>
            Supporting deadline passage
            <input name="deadlineEvidence" />
          </label>
          <label className="full">
            Published applicant requirements
            <textarea name="eligibility" />
          </label>
        </div>
        <Button loading={mutation.isPending} loadingText="Submitting for review…" type="submit">
          Add for review
        </Button>
        {mutation.error && <ErrorState error={mutation.error} />}{' '}
        {mutation.isSuccess && (
          <span role="status"> Submitted. Check the import report and review queue.</span>
        )}
      </form>
    </section>
  );
}
