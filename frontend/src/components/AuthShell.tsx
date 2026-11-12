import { Boxes, ChartNoAxesColumn, ShieldCheck } from 'lucide-react'
import type { ReactNode } from 'react'

interface AuthShellProps {
  children: ReactNode
  mode: 'login' | 'register'
}

export function AuthShell({ children, mode }: AuthShellProps) {
  return (
    <main className="auth-page">
      <section className="auth-visual" aria-label="StockWise">
        <header className="auth-brand">
          <span className="brand-mark" aria-hidden="true"><Boxes size={20} /></span>
          <span>StockWise</span>
          <span className="brand-divider" />
          <span className="brand-caption">INVENTORY, IN FOCUS</span>
        </header>

        <div className="visual-copy">
          <p className="eyebrow eyebrow--light">A steadier way to see stock</p>
          <h1>Know what’s on the shelf. <em>And what isn’t.</em></h1>
          <p className="visual-description">
            One clear place to bring your inventory into view, whatever your business keeps.
          </p>
        </div>

        <div className="shelf-illustration" aria-hidden="true">
          <div className="shelf-topline">
            <span>STOCK REGISTER</span>
            <span className="live-mark"><i /> PRIVATE WORKSPACE</span>
          </div>
          <div className="shelf-grid">
            <div className="shelf-column">
              <span className="shelf-box shelf-box--olive" />
              <span className="shelf-box shelf-box--sand" />
              <span className="shelf-box shelf-box--long" />
            </div>
            <div className="shelf-column shelf-column--offset">
              <span className="shelf-box shelf-box--long shelf-box--pale" />
              <span className="shelf-box shelf-box--brick" />
              <span className="shelf-box shelf-box--olive shelf-box--small" />
            </div>
            <div className="shelf-column">
              <span className="shelf-box shelf-box--sand shelf-box--small" />
              <span className="shelf-box shelf-box--pale" />
              <span className="shelf-box shelf-box--long shelf-box--brick" />
            </div>
          </div>
          <div className="shelf-base"><span /><span /><span /></div>
          <div className="visual-footnote">
            <span><ChartNoAxesColumn size={15} /> Built for the details that matter</span>
            <span>01 / 03</span>
          </div>
        </div>

        <footer className="visual-footer">
          <span><ShieldCheck size={15} /> Your account, yours alone</span>
          <span>© StockWise</span>
        </footer>
        <span className="visual-orbit visual-orbit--one" />
        <span className="visual-orbit visual-orbit--two" />
      </section>

      <section className="auth-form-side">
        <div className="form-side-top">
          <span>{mode === 'login' ? 'WELCOME BACK' : 'GET STARTED'}</span>
          <span className="form-side-index">{mode === 'login' ? '01' : '02'} <i /> ACCOUNT</span>
        </div>
        {children}
        <p className="legal-note">
          By continuing, you agree to keep your account credentials private.
        </p>
      </section>
    </main>
  )
}
