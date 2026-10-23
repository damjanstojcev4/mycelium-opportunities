import type { Metadata } from 'next';
import './globals.css';
import { Providers } from '@/components/providers';
import { Shell } from '@/components/shell';
export const metadata: Metadata = {
  title: 'Mycelium Opportunities — Tender & Grant Radar',
  description:
    'Discover tenders and grants, understand your company match, and keep track of important deadlines.',
};
export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="en">
      <body>
        <Providers>
          <Shell>{children}</Shell>
        </Providers>
      </body>
    </html>
  );
}
