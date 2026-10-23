'use client';
import Link from 'next/link';
import { useMutation, useQuery } from '@tanstack/react-query';
import { ArrowUpRight, Bookmark } from 'lucide-react';
import { api, Opportunity, date, money } from '@/lib/api';
import { Heading, Loading, ErrorState, Badge } from './common';
import { Button } from './ui/button';
export function Detail({ id }: { id: string }) {
  const result = useQuery({
    queryKey: ['opportunity', id],
    queryFn: () => api<Opportunity>(`/opportunities/${id}`),
  });
  const save = useMutation({
    mutationFn: () =>
      api('/saved-opportunities', { method: 'POST', body: JSON.stringify({ opportunityId: id }) }),
  });
  if (result.isPending) return <Loading />;
  if (result.error) return <ErrorState error={result.error} />;
  const o = result.data;
  return (
    <>
      <Link className="source-link" href="/opportunities">
        ← Back to explorer
      </Link>
      <div style={{ height: 24 }} />
      <Heading eyebrow={`${o.type} · ${o.source_name}`} title={o.title}>
        {o.issuer_name}
      </Heading>
      <div className="details-grid">
        <div>
          <section className="panel">
            <Badge>{o.effective_status}</Badge>
            {o.status !== o.effective_status && (
              <p className="notice">
                The source states {o.status.toLowerCase()}, but its published deadline has passed.
              </p>
            )}
            <dl className="detail-facts">
              {[
                ['Country', o.country_code || 'Not specified'],
                ['Deadline', date(o.deadline_at)],
                ['Published', date(o.published_at)],
                ['Estimated value', money(o.estimated_value, o.currency)],
              ].map(([label, value]) => (
                <div key={label}>
                  <dt>{label}</dt>
                  <dd>{value}</dd>
                </div>
              ))}
            </dl>
            {Array.isArray(o.details.deadlineDates) && o.details.deadlineDates.length > 0 && (
              <div className="notice">
                Published deadline dates:{' '}
                {o.details.deadlineDates.map((d) => date(String(d))).join(', ')}. Exact submission
                times must be checked in the official call.
              </div>
            )}
            <h3>About this opportunity</h3>
            <p className="detail-body">
              {o.description ||
                'A detailed description has not been extracted. Read the original announcement for the full scope.'}
            </p>
          </section>
          <section className="panel">
            <h3>Eligibility & requirements</h3>
            <p className="detail-body">
              {String(
                o.details.eligibilityRequirements ||
                  'Applicant conditions have not been confirmed. A public call does not imply that your company is eligible.',
              )}
            </p>
            {Array.isArray(o.details.requiredDocuments) && (
              <ul className="match-list">
                {o.details.requiredDocuments.map((d, i) => (
                  <li key={i}>{String(d)}</li>
                ))}
              </ul>
            )}
            <Link className="source-link" href="/dashboard/matches">
              View your company matches →
            </Link>
          </section>
          <section className="panel">
            <h3>Source documents</h3>
            {o.documents?.length ? (
              o.documents.map((d) => (
                <p key={d.id}>
                  <a
                    className="source-link"
                    href={d.source_document_url}
                    target="_blank"
                    rel="noopener noreferrer"
                  >
                    {d.filename || 'Official source document'} <ArrowUpRight size={15} />
                  </a>
                  {d.stored && (
                    <a
                      className="source-link"
                      href={`/api/v1/opportunities/${id}/documents/${d.id}/content`}
                    >
                      Download stored PDF ↓
                    </a>
                  )}
                </p>
              ))
            ) : (
              <p className="muted">
                No document references have been extracted. Check the original announcement.
              </p>
            )}
            {o.provenance?.map((p, i) => (
              <div key={i} className="notice">
                <strong>{p.field_name}</strong>
                <p>{p.evidence}</p>
                <a href={p.source_url} target="_blank" rel="noopener noreferrer">
                  Supporting publication ↗
                </a>
              </div>
            ))}
          </section>
        </div>
        <aside>
          <section className="panel">
            <h3>Your next step</h3>
            <p className="muted">
              Keep this opportunity in your workspace while you review the requirements.
            </p>
            <Button onClick={() => save.mutate()} disabled={save.isPending || save.isSuccess}>
              <Bookmark size={15} />
              {save.isSuccess ? 'Saved to your workspace' : 'Save opportunity'}
            </Button>
            {save.error && <ErrorState error={save.error} />}
            <div style={{ height: 24 }} />
            <a
              className="source-link"
              href={o.source_url}
              target="_blank"
              rel="noopener noreferrer"
            >
              Original announcement <ArrowUpRight size={15} />
            </a>
            <p className="muted" style={{ fontSize: 10, marginTop: 18 }}>
              Last source verification
              <br />
              {date(o.last_verified_at)}
            </p>
          </section>
          <div className="notice">
            Relevance is a starting point. Confirm eligibility, deadlines and application
            instructions with the issuer.
          </div>
        </aside>
      </div>
    </>
  );
}
