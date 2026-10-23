'use client';
import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { Search, SlidersHorizontal } from 'lucide-react';
import { api, Opportunity } from '@/lib/api';
import { Heading, Empty, Loading, ErrorState, OpportunityCard } from './common';
import { Button } from './ui/button';
export function Explorer({ initialType = '' }: { initialType?: string }) {
  const [q, setQ] = useState('');
  const [filters, setFilters] = useState<Record<string, string>>({
    type: initialType,
    sort: 'newest',
  });
  const [page, setPage] = useState(0);
  const params = new URLSearchParams({ ...filters, page: String(page) });
  const result = useQuery({
    queryKey: ['opportunities', filters, page],
    queryFn: () =>
      api<{ items: Opportunity[]; total: number; size: number }>(`/opportunities?${params}`),
  });
  function filter(key: string, value: string) {
    setPage(0);
    setFilters((f) => ({ ...f, [key]: value }));
  }
  return (
    <>
      <Heading eyebrow="DISCOVER WHAT'S NEXT" title="Opportunity explorer">
        Public tenders and funding calls. A wider view of what your business could do next.
      </Heading>
      <form
        className="searchbar"
        onSubmit={(e) => {
          e.preventDefault();
          filter('q', q);
        }}
      >
        <Search size={19} color="#849078" />
        <input
          aria-label="Search opportunities"
          placeholder="Search by keyword, service or capability…"
          value={q}
          onChange={(e) => setQ(e.target.value)}
        />
        <Button type="submit">Search</Button>
      </form>
      <div className="explorer-grid">
        <aside className="filters">
          <h3>
            <SlidersHorizontal size={14} style={{ display: 'inline', marginRight: 8 }} />
            Refine your search
          </h3>
          <label>
            Country
            <select
              value={filters.country || ''}
              onChange={(e) => filter('country', e.target.value)}
            >
              <option value="">All countries</option>
              <option value="MK">North Macedonia</option>
              <option value="BG">Bulgaria</option>
              <option value="GR">Greece</option>
              <option value="HR">Croatia</option>
              <option value="RS">Serbia</option>
            </select>
          </label>
          <label>
            Publication status
            <select value={filters.status || ''} onChange={(e) => filter('status', e.target.value)}>
              <option value="">All statuses</option>
              {['OPEN', 'UPCOMING', 'CLOSED', 'CANCELLED', 'AWARDED', 'UNKNOWN'].map((s) => (
                <option key={s}>{s}</option>
              ))}
            </select>
          </label>
          <label>
            Official source
            <select value={filters.source || ''} onChange={(e) => filter('source', e.target.value)}>
              <option value="">All sources</option>
              <option value="EU_FUNDING">EU Funding & Tenders</option>
              <option value="TED">TED</option>
              <option value="IPARD">Agricultural agency</option>
              <option value="ESJN">ESJN official imports</option>
            </select>
          </label>
          <label>
            CPV code
            <input
              value={filters.cpv || ''}
              onChange={(e) => filter('cpv', e.target.value)}
              placeholder="e.g. 72000000"
            />
          </label>
          <label>
            Funding program
            <input
              value={filters.fundingProgram || ''}
              onChange={(e) => filter('fundingProgram', e.target.value)}
              placeholder="Program name or code"
            />
          </label>
          <Button
            variant="ghost"
            onClick={() => {
              setFilters({ type: '', sort: 'newest' });
              setQ('');
              setPage(0);
            }}
          >
            Reset filters
          </Button>
        </aside>
        <section>
          <div className="results-toolbar">
            <div className="tabs" role="tablist" aria-label="Opportunity type">
              {[
                ['', 'All opportunities'],
                ['TENDER', 'Tenders'],
                ['GRANT', 'Grants'],
              ].map(([value, label]) => (
                <button
                  key={value}
                  role="tab"
                  aria-selected={filters.type === value}
                  className={filters.type === value ? 'tab active' : 'tab'}
                  onClick={() => filter('type', value)}
                >
                  {label}
                </button>
              ))}
            </div>
            <select
              aria-label="Sort opportunities"
              value={filters.sort}
              onChange={(e) => filter('sort', e.target.value)}
            >
              <option value="newest">Newest first</option>
              <option value="deadline">Deadline soonest</option>
              <option value="value">Value by currency</option>
            </select>
          </div>
          {result.isPending ? (
            <Loading />
          ) : result.error ? (
            <ErrorState error={result.error} />
          ) : (
            <>
              <div className="results-count">
                {result.data.total} published opportunities · Official-source records
              </div>
              {result.data.items.length ? (
                result.data.items.map((o) => <OpportunityCard key={o.id} item={o} />)
              ) : (
                <Empty title="A clear view starts with verified data">
                  No published opportunities match these filters. Try a broader search, or check
                  back after the next source review.
                </Empty>
              )}
              <div className="pagination">
                <Button
                  variant="outline"
                  disabled={page === 0}
                  onClick={() => setPage((p) => p - 1)}
                >
                  Previous
                </Button>
                <span>Page {page + 1}</span>
                <Button
                  variant="outline"
                  disabled={(page + 1) * result.data.size >= result.data.total}
                  onClick={() => setPage((p) => p + 1)}
                >
                  Next
                </Button>
              </div>
            </>
          )}
        </section>
      </div>
    </>
  );
}
