import Link from 'next/link';
import {
  ArrowUpRight,
  ArrowRight,
  FileSearch,
  Building2,
  ScanLine,
  ShieldCheck,
  Sprout,
  CalendarDays,
} from 'lucide-react';
import { Button } from '@/components/ui/button';
export default function Home() {
  return (
    <>
      <section className="hero">
        <div className="hero-copy">
          <div className="eyebrow">
            <span className="live-dot" />
            BUILT FOR BUSINESSES IN NORTH MACEDONIA
          </div>
          <h1>
            Your next opportunity.
            <br />
            <em>Closer than you think.</em>
          </h1>
          <p>
            Find public tenders and funding calls that fit your business. Understand the
            requirements. Make your next move with a clearer picture.
          </p>
          <div className="hero-actions">
            <Button asChild>
              <Link href="/opportunities">
                Explore opportunities <ArrowUpRight size={18} />
              </Link>
            </Button>
            <Button variant="outline" asChild>
              <Link href="/onboarding">
                Create company profile <ArrowRight size={17} />
              </Link>
            </Button>
          </div>
          <div className="hero-foot">
            <ShieldCheck size={17} /> Official sources. Clear provenance. No eligibility promises.
          </div>
        </div>
        <div className="hero-visual">
          <div className="orbit orbit-one" />
          <div className="orbit orbit-two" />
          <div className="visual-center">
            <Sprout size={48} />
            <strong>Room to grow.</strong>
            <span>A clearer path from discovery to action.</span>
          </div>
          <div className="floating-tag tag-top">
            <FileSearch size={19} /> Tender Radar <span>MK + EU</span>
          </div>
          <div className="floating-tag tag-bottom">
            <Sprout size={19} /> Grant Radar <span>Funding & support</span>
          </div>
        </div>
      </section>
      <section className="landing-section">
        <div className="section-intro">
          <span className="eyebrow">TWO RADARS. ONE WORKSPACE.</span>
          <h2>
            Less searching.
            <br />
            More possibility.
          </h2>
          <p>Bring procurement and funding opportunities into one focused workflow.</p>
        </div>
        <div className="module-grid">
          <article>
            <FileSearch />
            <h3>Contracts worth exploring.</h3>
            <p>
              Find public procurement notices, review the buyer and scope, and follow the original
              announcement.
            </p>
            <Link href="/opportunities?type=TENDER">
              Explore Tender Radar <ArrowRight size={16} />
            </Link>
          </article>
          <article>
            <Sprout />
            <h3>Support for your next chapter.</h3>
            <p>
              Discover funding calls and understand what is known—and still needs checking—about
              eligibility.
            </p>
            <Link href="/opportunities?type=GRANT">
              Explore Grant Radar <ArrowRight size={16} />
            </Link>
          </article>
        </div>
      </section>
      <section className="how-section">
        <div className="eyebrow">A PRACTICAL WAY FORWARD</div>
        <h2>From possibility to a plan.</h2>
        <div className="steps">
          {[
            {
              icon: Building2,
              title: 'Tell us what you do',
              text: 'Add your capabilities, markets and interests to your company profile.',
            },
            {
              icon: ScanLine,
              title: 'Understand the fit',
              text: 'Review relevance scores, matching reasons and eligibility uncertainties.',
            },
            {
              icon: CalendarDays,
              title: 'Keep things moving',
              text: 'Save opportunities, track your application and opt into deadline reminders.',
            },
          ].map(({ icon: Icon, title, text }, i) => (
            <article key={title}>
              <span className="step-number">0{i + 1}</span>
              <Icon size={22} />
              <h3>{title}</h3>
              <p>{text}</p>
            </article>
          ))}
        </div>
      </section>
      <footer className="landing-footer">
        Mycelium Opportunities · Independent business opportunity intelligence
        <Link href="/register">Build your workspace →</Link>
      </footer>
    </>
  );
}
