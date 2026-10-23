'use client';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api, Company } from '@/lib/api';
import { Heading, Loading, ErrorState } from './common';
import { Button } from './ui/button';
const schema = z.object({
  name: z.string().min(1).max(200),
  description: z.string().max(10000),
  countryCode: z.string().regex(/^[A-Z]{2}$/),
  city: z.string().max(100),
  industry: z.string().max(200),
  keywords: z.string(),
  countries: z.string(),
  cpv: z.string(),
  certifications: z.string(),
  budgetCurrency: z.string(),
  budgetMin: z.string().regex(/^\d*(\.\d{1,2})?$/, 'Use a positive amount'),
  budgetMax: z.string().regex(/^\d*(\.\d{1,2})?$/, 'Use a positive amount'),
  businessSize: z.string(),
  organizationType: z.string(),
  tenders: z.boolean(),
  grants: z.boolean(),
});
type Values = z.infer<typeof schema>;
function Editor({ company }: { company?: Company }) {
  const client = useQueryClient();
  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<Values>({
    resolver: zodResolver(schema),
    defaultValues: {
      name: company?.name || '',
      description: company?.description || '',
      countryCode: company?.country_code || 'MK',
      city: company?.city || '',
      industry: company?.industry || '',
      keywords: company?.profile.keywords?.join(', ') || '',
      countries: company?.profile.countries?.join(', ') || 'MK',
      cpv: company?.profile.cpv?.join(', ') || '',
      certifications: company?.profile.certifications?.join(', ') || '',
      budgetCurrency: company?.profile.budgetCurrency || 'EUR',
      budgetMin: company?.profile.budgetMin?.toString() || '',
      budgetMax: company?.profile.budgetMax?.toString() || '',
      businessSize: company?.profile.businessSize || '',
      organizationType: company?.profile.organizationType || '',
      tenders: company?.profile.types?.includes('TENDER') ?? true,
      grants: company?.profile.types?.includes('GRANT') ?? true,
    },
  });
  const mutation = useMutation({
    mutationFn: (v: Values) => {
      const split = (s: string) =>
        s
          .split(',')
          .map((v) => v.trim())
          .filter(Boolean);
      return api(company ? `/companies/${company.id}` : '/companies', {
        method: company ? 'PATCH' : 'POST',
        body: JSON.stringify({
          ...v,
          preferences: {
            keywords: split(v.keywords),
            countries: split(v.countries).map((c) => c.toUpperCase()),
            cpv: split(v.cpv),
            certifications: split(v.certifications),
            budgetCurrency: v.budgetCurrency,
            budgetMin: v.budgetMin ? Number(v.budgetMin) : undefined,
            budgetMax: v.budgetMax ? Number(v.budgetMax) : undefined,
            businessSize: v.businessSize,
            organizationType: v.organizationType,
            types: [...(v.tenders ? ['TENDER'] : []), ...(v.grants ? ['GRANT'] : [])],
          },
        }),
      });
    },
    onSuccess: () => {
      client.invalidateQueries({ queryKey: ['companies'] });
      client.invalidateQueries({ queryKey: ['matches'] });
    },
  });
  return (
    <form className="panel" onSubmit={handleSubmit((v) => mutation.mutate(v))}>
      <h3>01 · Your business</h3>
      <div className="form-grid">
        <label>
          Company name
          <input {...register('name')} />
          {errors.name && <span className="field-error">Enter your company name.</span>}
        </label>
        <label>
          Country code
          <input {...register('countryCode')} maxLength={2} />
          {errors.countryCode && (
            <span className="field-error">Use two uppercase letters, e.g. MK.</span>
          )}
        </label>
        <label>
          Industry
          <input {...register('industry')} placeholder="Software development" />
        </label>
        <label>
          City
          <input {...register('city')} />
        </label>
        <label className="full">
          What does your company do?
          <textarea {...register('description')} />
        </label>
      </div>
      <h3>02 · Capabilities & interests</h3>
      <div className="form-grid">
        <label>
          Business size (optional)
          <select {...register('businessSize')}>
            <option value="">Not specified</option>
            {['MICRO', 'SMALL', 'MEDIUM', 'LARGE'].map((s) => (
              <option key={s}>{s}</option>
            ))}
          </select>
        </label>
        <label>
          Organization type (optional)
          <select {...register('organizationType')}>
            <option value="">Not specified</option>
            {['BUSINESS', 'NONPROFIT', 'PUBLIC_BODY', 'RESEARCH'].map((s) => (
              <option key={s}>{s}</option>
            ))}
          </select>
        </label>
        <label>
          Preferred minimum value
          <input {...register('budgetMin')} inputMode="decimal" />
          {errors.budgetMin && <span className="field-error">{errors.budgetMin.message}</span>}
        </label>
        <label>
          Preferred maximum value
          <input {...register('budgetMax')} inputMode="decimal" />
          {errors.budgetMax && <span className="field-error">{errors.budgetMax.message}</span>}
        </label>
        <label>
          Budget currency
          <select {...register('budgetCurrency')}>
            <option>EUR</option>
            <option>MKD</option>
          </select>
        </label>
      </div>
      <div className="form-grid">
        <label className="full">
          Service keywords and technologies, separated by commas
          <input {...register('keywords')} placeholder="Web development, API integration, Java" />
        </label>
        <label>
          CPV codes (optional)
          <input {...register('cpv')} placeholder="72000000" />
        </label>
        <label>
          Certifications (optional)
          <input {...register('certifications')} />
        </label>
        <label>
          Interested countries (ISO codes)
          <input {...register('countries')} placeholder="MK, BG, GR" />
        </label>
        <div>
          <label>
            <input type="checkbox" {...register('tenders')} />
            Tender opportunities
          </label>
          <label>
            <input type="checkbox" {...register('grants')} />
            Grants and funding
          </label>
        </div>
      </div>
      <div className="notice">
        You can start with a few capabilities. Matches indicate relevance; they do not confirm
        eligibility.
      </div>
      {mutation.error && <ErrorState error={mutation.error} />}
      <div className="form-actions">
        <Button disabled={mutation.isPending} type="submit">
          {mutation.isPending ? 'Saving and calculating matches…' : 'Save company profile'}
        </Button>
        {mutation.isSuccess && <span role="status">Profile saved. Matches recalculated.</span>}
      </div>
    </form>
  );
}
export function CompanyForm() {
  const result = useQuery({ queryKey: ['companies'], queryFn: () => api<Company[]>('/companies') });
  return (
    <>
      <Heading eyebrow="BUILD YOUR OPPORTUNITY PROFILE" title="A better fit starts with you.">
        Tell us what your business brings to the table. Add more detail whenever you are ready.
      </Heading>
      {result.isPending ? (
        <Loading />
      ) : result.error ? (
        <ErrorState error={result.error} />
      ) : (
        <Editor company={result.data[0]} />
      )}
    </>
  );
}
