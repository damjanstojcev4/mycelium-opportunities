'use client';
import Link from 'next/link';
import { usePathname, useRouter } from 'next/navigation';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import {
  Sprout,
  Compass,
  LayoutDashboard,
  Bookmark,
  Building2,
  ShieldCheck,
  ArrowUpRight,
  LogOut,
} from 'lucide-react';
import { api } from '@/lib/api';
import { Button } from './ui/button';
const links = [
  { href: '/opportunities', label: 'Explore opportunities', icon: Compass },
  { href: '/dashboard', label: 'Overview', icon: LayoutDashboard },
  { href: '/dashboard/matches', label: 'Company matches', icon: Sprout },
  { href: '/dashboard/saved', label: 'Saved opportunities', icon: Bookmark },
  { href: '/dashboard/company', label: 'Company profile', icon: Building2 },
];
export function Shell({ children }: { children: React.ReactNode }) {
  const path = usePathname();
  const router = useRouter();
  const client = useQueryClient();
  const me = useQuery({
    queryKey: ['me'],
    queryFn: () => api<{ full_name: string; role: string }>('/me'),
    retry: false,
  });
  const publicPage = ['/', '/login', '/register'].includes(path);
  async function logout() {
    await api('/auth/logout', { method: 'POST' });
    client.clear();
    router.push('/');
  }
  return (
    <>
      <header className="topbar">
        <Link className="brand" href="/">
          <span className="brand-icon">
            <Sprout size={23} />
          </span>
          <span>
            mycelium<small>OPPORTUNITIES</small>
          </span>
        </Link>
        <nav className="topnav">
          <Link href="/opportunities">Opportunity explorer</Link>
          <Link href="/dashboard">Workspace</Link>
        </nav>
        <div className="account">
          {me.data ? (
            <>
              <span className="muted">{me.data.full_name}</span>
              <Button variant="ghost" aria-label="Sign out" onClick={logout}>
                <LogOut size={17} />
              </Button>
            </>
          ) : (
            <>
              <Link href="/login">Sign in</Link>
              <Button asChild>
                <Link href="/register">
                  Get started <ArrowUpRight size={15} />
                </Link>
              </Button>
            </>
          )}
        </div>
      </header>
      {publicPage ? (
        <main>{children}</main>
      ) : (
        <div className="workspace">
          <aside className="sidebar">
            <div className="nav-caption">YOUR WORKSPACE</div>
            {links.map(({ href, label, icon: Icon }) => (
              <Link
                key={href}
                href={href}
                className={path === href ? 'nav-item active' : 'nav-item'}
              >
                <Icon size={18} />
                {label}
              </Link>
            ))}
            {me.data?.role === 'ADMIN' && (
              <Link className="nav-item" href="/admin">
                <ShieldCheck size={18} />
                Administration
              </Link>
            )}
            <div className="sidebar-note">
              <span className="live-dot" />
              Source-first intelligence<p>Every opportunity starts with an official publication.</p>
              <Link href="/opportunities">Find your next opportunity →</Link>
            </div>
            <div className="region">
              North Macedonia & Europe
              <br />
              <small>Times shown in Europe/Skopje</small>
            </div>
          </aside>
          <main className="workspace-main">
            {children}
            <footer>
              Independent opportunity intelligence. Always confirm eligibility and deadlines with
              the official source.
            </footer>
          </main>
        </div>
      )}
    </>
  );
}
