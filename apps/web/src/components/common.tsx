import Link from 'next/link';
import { ArrowUpRight, CalendarDays, Landmark, MapPin, SearchX } from 'lucide-react';
import { Opportunity, date, money } from '@/lib/api';
export function Heading({
  eyebrow,
  title,
  children,
}: {
  eyebrow: string;
  title: string;
  children?: React.ReactNode;
}) {
  return (
    <div className="page-heading">
      <div className="eyebrow">{eyebrow}</div>
      <h1>{title}</h1>
      {children && <p>{children}</p>}
    </div>
  );
}
export function ErrorState({ error }: { error: Error | null }) {
  return (
    <div className="notice error" role="alert">
      {error?.message || 'Something went wrong.'}
      {error?.message.includes('sign in') && (
        <>
          {' '}
          <Link href="/login">Sign in →</Link>
        </>
      )}
    </div>
  );
}
export function Empty({
  title = 'No opportunities yet',
  children,
}: {
  title?: string;
  children: React.ReactNode;
}) {
  return (
    <div className="empty">
      <SearchX size={32} />
      <h3>{title}</h3>
      <p>{children}</p>
    </div>
  );
}
export function Loading() {
  return (
    <div className="loading" role="status">
      Loading your workspace…
    </div>
  );
}
export function Badge({ children }: { children: React.ReactNode }) {
  return <span className="badge">{children}</span>;
}
export function OpportunityCard({ item }: { item: Opportunity }) {
  return (
    <article className="opportunity-card">
      <div className="card-meta">
        <span className={`type-label ${item.type.toLowerCase()}`}>
          {item.type === 'TENDER' ? 'Tender Radar' : 'Grant Radar'}
        </span>
        <Badge>{item.effective_status.replaceAll('_', ' ')}</Badge>
        <span className="source-label">{item.source_name}</span>
      </div>
      <Link className="opportunity-title" href={`/opportunities/${item.id}`}>
        {item.title}
        <ArrowUpRight size={19} />
      </Link>
      <p className="issuer">
        <Landmark size={15} />
        {item.issuer_name || 'Issuer not specified'}
      </p>
      {item.description && <p className="card-description">{item.description}</p>}
      <div className="card-bottom">
        <span>
          <MapPin size={14} />
          {item.country_code || 'Country not specified'}
        </span>
        <span>
          <CalendarDays size={14} />
          Deadline: {date(item.deadline_at)}
        </span>
        <strong>{money(item.estimated_value, item.currency)}</strong>
      </div>
    </article>
  );
}
