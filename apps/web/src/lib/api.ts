export class ApiError extends Error {
  constructor(
    public status: number,
    message: string,
  ) {
    super(message);
  }
}
export async function api<T>(path: string, options: RequestInit = {}): Promise<T> {
  const headers = new Headers(options.headers);
  if (options.body && !(options.body instanceof FormData))
    headers.set('Content-Type', 'application/json');
  if (options.method && options.method !== 'GET') {
    const response = await fetch('/api/v1/auth/csrf', {
      credentials: 'include',
      cache: 'no-store',
    });
    if (!response.ok) throw new ApiError(response.status, 'Could not establish a secure session.');
    const token = await response.json();
    headers.set(token.headerName, token.token);
  }
  const response = await fetch(`/api/v1${path}`, {
    ...options,
    headers,
    credentials: 'include',
    cache: 'no-store',
  });
  if (!response.ok) {
    const problem = await response.json().catch(() => ({}));
    throw new ApiError(
      response.status,
      problem.detail ||
        (response.status === 401
          ? 'Please sign in to continue.'
          : response.status === 403
            ? 'You do not have access to this action.'
            : 'The service could not complete this request. Please try again.'),
    );
  }
  if (response.status === 204 || response.headers.get('content-length') === '0')
    return undefined as T;
  const text = await response.text();
  return text ? (JSON.parse(text) as T) : (undefined as T);
}
export type Opportunity = {
  id: string;
  title: string;
  type: 'TENDER' | 'GRANT';
  issuer_name: string | null;
  country_code: string | null;
  deadline_at: string | null;
  published_at: string | null;
  estimated_value: number | null;
  currency: string | null;
  status: string;
  effective_status: string;
  source_name: string;
  source_url: string;
  source_code: string;
  description: string | null;
  last_verified_at: string | null;
  details: Record<string, unknown>;
  documents?: {
    id: string;
    source_document_url: string;
    filename: string | null;
    stored?: boolean;
  }[];
  provenance?: { field_name: string; source_url: string; evidence: string }[];
};
export type Company = {
  id: string;
  name: string;
  description: string;
  country_code: string;
  city: string;
  industry: string;
  profile: {
    keywords?: string[];
    countries?: string[];
    types?: string[];
    cpv?: string[];
    certifications?: string[];
    budgetCurrency?: string;
    budgetMin?: number;
    budgetMax?: number;
    businessSize?: string;
    organizationType?: string;
  };
};
export const date = (value: string | null | undefined) =>
  value
    ? new Intl.DateTimeFormat('en-GB', {
        day: 'numeric',
        month: 'short',
        year: 'numeric',
        timeZone: 'Europe/Skopje',
      }).format(new Date(value))
    : 'Not specified';
export const money = (value: number | null, currency: string | null) =>
  value !== null && currency
    ? new Intl.NumberFormat('en-GB', {
        style: 'currency',
        currency,
        maximumFractionDigits: 0,
      }).format(value)
    : 'Not specified';
