import React from 'react';
import { render, screen } from '@testing-library/react';
import { describe, it, expect } from 'vitest';
import { Empty, ErrorState, OpportunityCard } from './common';
import type { Opportunity } from '@/lib/api';
describe('honest data states', () => {
  it('explains an empty result without fake records', () => {
    render(<Empty>Nothing has been published yet.</Empty>);
    expect(screen.getByText('Nothing has been published yet.')).toBeVisible();
  });
  it('shows actionable sign-in errors', () => {
    render(<ErrorState error={new Error('Please sign in to continue.')} />);
    expect(screen.getByRole('link', { name: 'Sign in →' })).toHaveAttribute('href', '/login');
  });
  it('preserves unknown deadline and value', () => {
    const o = {
      id: 'fixture',
      title: 'Test fixture',
      type: 'GRANT',
      issuer_name: null,
      country_code: null,
      deadline_at: null,
      published_at: null,
      estimated_value: null,
      currency: null,
      status: 'UNKNOWN',
      effective_status: 'UNKNOWN',
      source_name: 'Test source',
      source_url: 'https://ec.europa.eu',
      source_code: 'EU_FUNDING',
      description: null,
      last_verified_at: null,
      details: {},
    } satisfies Opportunity;
    render(<OpportunityCard item={o} />);
    expect(screen.getByText('Deadline: Not specified')).toBeVisible();
    expect(screen.getByText('UNKNOWN')).toBeVisible();
    expect(screen.getByRole('link')).toHaveAttribute('href', '/opportunities/fixture');
  });
});
