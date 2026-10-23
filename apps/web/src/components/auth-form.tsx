'use client';
import Link from 'next/link';
import { useRouter } from 'next/navigation';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { api } from '@/lib/api';
import { ErrorState } from './common';
import { Button } from './ui/button';
const schema = z.object({
  email: z.email(),
  password: z.string().min(12, 'Use at least 12 characters').max(72),
  fullName: z.string().max(160).optional(),
});
export function AuthForm({ registration = false }: { registration?: boolean }) {
  const router = useRouter();
  const client = useQueryClient();
  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<z.infer<typeof schema>>({ resolver: zodResolver(schema) });
  const mutation = useMutation({
    mutationFn: async (values: z.infer<typeof schema>) => {
      if (registration)
        await api('/auth/register', { method: 'POST', body: JSON.stringify(values) });
      const token = await api<{ token: string; headerName: string }>('/auth/csrf');
      const response = await fetch('/api/v1/auth/login', {
        method: 'POST',
        credentials: 'include',
        headers: {
          'Content-Type': 'application/x-www-form-urlencoded',
          [token.headerName]: token.token,
        },
        body: new URLSearchParams({ email: values.email, password: values.password }),
      });
      if (!response.ok)
        throw new Error(
          response.status === 429
            ? 'Too many attempts. Try again in 10 minutes.'
            : 'Could not sign in. Check your email and password.',
        );
    },
    onSuccess: () => {
      client.clear();
      router.push(registration ? '/onboarding' : '/dashboard');
      router.refresh();
    },
  });
  return (
    <div className="form-card">
      <div className="eyebrow">YOUR NEXT CHAPTER</div>
      <h1>{registration ? 'Make room for opportunity.' : 'Welcome back.'}</h1>
      <p>
        {registration
          ? 'Create your account, then tell us about your business.'
          : 'Sign in to your opportunity workspace.'}
      </p>
      <form onSubmit={handleSubmit((v) => mutation.mutate(v))}>
        {registration && (
          <label>
            Full name
            <input autoComplete="name" required {...register('fullName')} />
          </label>
        )}
        <label>
          Email address
          <input type="email" autoComplete="email" {...register('email')} />
          {errors.email && <span className="field-error">{errors.email.message}</span>}
        </label>
        <label>
          Password
          <input
            type="password"
            autoComplete={registration ? 'new-password' : 'current-password'}
            {...register('password')}
          />
          {errors.password && <span className="field-error">{errors.password.message}</span>}
        </label>
        {mutation.error && <ErrorState error={mutation.error} />}
        <Button type="submit" disabled={mutation.isPending}>
          {mutation.isPending ? 'Please wait…' : registration ? 'Create account' : 'Sign in'}
        </Button>
      </form>
      <p style={{ marginTop: 24 }}>
        {registration ? 'Already have an account?' : 'New to Mycelium?'}{' '}
        <Link href={registration ? '/login' : '/register'}>
          {registration ? 'Sign in' : 'Create an account'} →
        </Link>
      </p>
    </div>
  );
}
