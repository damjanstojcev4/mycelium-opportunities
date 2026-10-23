import { Explorer } from '@/components/explorer';
export default async function Page({ searchParams }: { searchParams: Promise<{ type?: string }> }) {
  const params = await searchParams;
  return (
    <Explorer initialType={['TENDER', 'GRANT'].includes(params.type || '') ? params.type : ''} />
  );
}
