'use client';
import Link from 'next/link';
import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { api, Company, date } from '@/lib/api';
import { Heading, Loading, ErrorState, Empty, Badge } from './common';
import { Button } from './ui/button';
type Match = {
  id: string;
  opportunity_id: string;
  title: string;
  type: string;
  deadline_at: string | null;
  relevance_score: number;
  eligibility_status: string;
  positive_reasons: string[];
  missing_requirements: string[];
  disqualifying_requirements: string[];
};
type Saved = {
  id: string;
  opportunity_id: string;
  title: string;
  type: string;
  deadline_at: string | null;
  status: string;
  source_url: string;
};
type Recommendation = { id: string; title: string; deadline_at: string | null; score: number };
export function Dashboard() {
  const query = useQuery({
    queryKey: ['dashboard'],
    queryFn: () =>
      api<{
        matches: number;
        saved: number;
        deadlines: number;
        newMatches: number;
        TENDER: Recommendation[];
        GRANT: Recommendation[];
      }>('/dashboard'),
  });
  return (
    <>
      <Heading eyebrow="YOUR OPPORTUNITY WORKSPACE" title="A little clarity. A new possibility.">
        Explore your matches and keep your next steps in sight.
      </Heading>
      {query.isPending ? (
        <Loading />
      ) : query.error ? (
        <ErrorState error={query.error} />
      ) : (
        <>
          <div className="stats">
            {[
              ['Relevant company matches', query.data.matches],
              ['Discovered in the last 7 days', query.data.newMatches],
              ['Saved opportunities', query.data.saved],
              ['Deadlines in the next 7 days', query.data.deadlines],
            ].map(([label, value]) => (
              <div className="stat" key={label}>
                <span>{label}</span>
                <strong>{value}</strong>
              </div>
            ))}
          </div>
          {(['TENDER', 'GRANT'] as const).map((type) => (
            <section key={type}>
              <div className="section-heading">
                <h2>{type === 'TENDER' ? 'Recommended tenders' : 'Recommended grants'}</h2>
                <Link href="/dashboard/matches">All matches →</Link>
              </div>
              {query.data[type].length ? (
                query.data[type].map((item) => (
                  <article className="panel" key={item.id}>
                    <Badge>Relevance {item.score}/100</Badge>
                    <h3 style={{ marginTop: 12 }}>
                      <Link href={`/opportunities/${item.id}`}>{item.title} ↗</Link>
                    </h3>
                    <span className="muted">
                      Deadline: {date(item.deadline_at)} · Verify eligibility in the original
                      publication.
                    </span>
                  </article>
                ))
              ) : (
                <p className="muted">No published matches in this category yet.</p>
              )}
            </section>
          ))}
          <div className="section-heading">
            <h2>Your next steps</h2>
            <Link href="/opportunities">Explore all opportunities →</Link>
          </div>
          <div className="module-grid">
            <article>
              <h3>Start with a strong profile.</h3>
              <p>
                Your capabilities and preferences shape your matches. Keep them up to date as your
                business grows.
              </p>
              <Link href="/dashboard/company">Update company profile →</Link>
            </article>
            <article>
              <h3>Make a shortlist.</h3>
              <p>
                Review relevant opportunities, check requirements and save the ones worth pursuing.
              </p>
              <Link href="/dashboard/matches">Review company matches →</Link>
            </article>
          </div>
        </>
      )}
    </>
  );
}
export function Matches() {
  const [page, setPage] = useState(0);
  const companies = useQuery({
    queryKey: ['companies'],
    queryFn: () => api<Company[]>('/companies'),
  });
  const company = companies.data?.[0];
  const matches = useQuery({
    queryKey: ['matches', company?.id, page],
    enabled: !!company,
    queryFn: () => api<Match[]>(`/companies/${company!.id}/matches?page=${page}`),
  });
  return (
    <>
      <Heading eyebrow="RELEVANCE, EXPLAINED" title="Company matches">
        A ranked starting point for your review. Relevance and eligibility are assessed separately.
      </Heading>
      {companies.isPending ? (
        <Loading />
      ) : companies.error ? (
        <ErrorState error={companies.error} />
      ) : !company ? (
        <Empty title="Tell us about your business">
          <Link href="/onboarding">Create a company profile to calculate your matches →</Link>
        </Empty>
      ) : matches.isPending ? (
        <Loading />
      ) : matches.error ? (
        <ErrorState error={matches.error} />
      ) : (
        <>
          {matches.data.length ? (
            matches.data.map((m) => (
              <article className="panel" key={m.id}>
                <span className="match-score" title="Relevance score out of 100">
                  {m.relevance_score}
                </span>
                <div className="eyebrow">{m.type}</div>
                <h3>
                  <Link href={`/opportunities/${m.opportunity_id}`}>{m.title} ↗</Link>
                </h3>
                <Badge>{m.eligibility_status.replaceAll('_', ' ')}</Badge>
                <ul className="match-list">
                  {m.positive_reasons.map((r) => (
                    <li key={r}>{r}</li>
                  ))}
                </ul>
                {m.disqualifying_requirements.map((r) => (
                  <div className="notice error" key={r}>
                    {r}
                  </div>
                ))}
                <ul className="match-list">
                  {m.missing_requirements.map((r) => (
                    <li key={r}>{r}</li>
                  ))}
                </ul>
                <span className="muted">Deadline: {date(m.deadline_at)}</span>
              </article>
            ))
          ) : (
            <Empty title="Your next match is still ahead">
              No relevant matches are available yet. Add capabilities to your profile or explore all
              published opportunities.
            </Empty>
          )}
          <div className="pagination">
            <Button variant="outline" disabled={page === 0} onClick={() => setPage((p) => p - 1)}>
              Previous
            </Button>
            <span>Page {page + 1}</span>
            <Button
              variant="outline"
              disabled={matches.data.length < 20}
              onClick={() => setPage((p) => p + 1)}
            >
              Next
            </Button>
          </div>
        </>
      )}
    </>
  );
}
export function SavedOpportunities() {
  const [page, setPage] = useState(0);
  const [type, setType] = useState('');
  const client = useQueryClient();
  const query = useQuery({
    queryKey: ['saved', page, type],
    queryFn: () => api<Saved[]>(`/saved-opportunities?page=${page}&type=${type}`),
  });
  const mutation = useMutation({
    mutationFn: ({ id, status }: { id: string; status?: string }) =>
      api(`/saved-opportunities/${id}${status ? '/status' : ''}`, {
        method: status ? 'PATCH' : 'DELETE',
        body: status ? JSON.stringify({ status }) : undefined,
      }),
    onSuccess: () => {
      client.invalidateQueries({ queryKey: ['saved'] });
      client.invalidateQueries({ queryKey: ['dashboard'] });
    },
  });
  return (
    <>
      <Heading eyebrow="FROM INTEREST TO ACTION" title="Your shortlist">
        Keep track of the opportunities you want to pursue, ordered by deadline.
      </Heading>
      <label>
        Opportunity type
        <select
          value={type}
          onChange={(e) => {
            setType(e.target.value);
            setPage(0);
          }}
        >
          <option value="">All types</option>
          <option value="TENDER">Tenders</option>
          <option value="GRANT">Grants</option>
        </select>
      </label>
      {mutation.error && <ErrorState error={mutation.error} />}{' '}
      {query.isPending ? (
        <Loading />
      ) : query.error ? (
        <ErrorState error={query.error} />
      ) : (
        <>
          {query.data.length ? (
            query.data.map((s) => (
              <article className="panel" key={s.id}>
                <div className="eyebrow">
                  {s.type} · DEADLINE {date(s.deadline_at)}
                </div>
                <h3>
                  <Link href={`/opportunities/${s.opportunity_id}`}>{s.title} ↗</Link>
                </h3>
                <div className="workflow-row">
                  <select
                    aria-label={`Application status for ${s.title}`}
                    value={s.status}
                    disabled={mutation.isPending}
                    onChange={(e) => mutation.mutate({ id: s.id, status: e.target.value })}
                  >
                    {[
                      'SAVED',
                      'REVIEWING',
                      'PLANNING_TO_APPLY',
                      'APPLIED',
                      'WON',
                      'LOST',
                      'DISMISSED',
                    ].map((v) => (
                      <option key={v} value={v}>
                        {v.replaceAll('_', ' ')}
                      </option>
                    ))}
                  </select>
                  <a
                    className="source-link"
                    href={s.source_url}
                    target="_blank"
                    rel="noopener noreferrer"
                  >
                    Original source ↗
                  </a>
                  <Button
                    variant="ghost"
                    disabled={mutation.isPending}
                    onClick={() => mutation.mutate({ id: s.id })}
                  >
                    Remove
                  </Button>
                </div>
              </article>
            ))
          ) : (
            <Empty title="Your shortlist starts here">
              <Link href="/opportunities">
                Explore opportunities and save the ones worth a closer look →
              </Link>
            </Empty>
          )}
          <div className="pagination">
            <Button variant="outline" disabled={!page} onClick={() => setPage((p) => p - 1)}>
              Previous
            </Button>
            <span>Page {page + 1}</span>
            <Button
              variant="outline"
              disabled={query.data.length < 20}
              onClick={() => setPage((p) => p + 1)}
            >
              Next
            </Button>
          </div>
        </>
      )}
    </>
  );
}
