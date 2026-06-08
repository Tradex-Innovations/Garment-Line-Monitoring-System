import type { ReactNode } from "react";
import { Link } from "react-router";

export function AuthPageShell({
  eyebrow,
  title,
  description,
  children,
  footer,
}: {
  eyebrow: string;
  title: string;
  description: string;
  children: ReactNode;
  footer?: ReactNode;
}) {
  return (
    <div className="ops-auth-page">
      <section className="ops-auth-panel ops-auth-panel-brand">
        <div className="ops-auth-brand-copy">
          <div className="ops-auth-eyebrow">{eyebrow}</div>
          <h1 className="ops-auth-title">{title}</h1>
          <p className="ops-auth-description">{description}</p>
        </div>

        <div className="ops-auth-feature-list">
          <div className="ops-auth-feature">
            <div className="ops-auth-feature-title">Live biometric reconciliation</div>
            <p>Face imports, fingerprint attendance, and exception workflows stay connected to one Supabase workspace.</p>
          </div>
          <div className="ops-auth-feature">
            <div className="ops-auth-feature-title">Role-based operations access</div>
            <p>Admins, supervisors, HR, and viewers get the right screens, actions, and data visibility from the same login.</p>
          </div>
          <div className="ops-auth-feature">
            <div className="ops-auth-feature-title">Import-ready audit trail</div>
            <p>Every batch, override, note, and downstream attendance decision is preserved for validation and reporting.</p>
          </div>
        </div>

        <div className="ops-auth-home-link">
          <Link to="/" className="ops-button ops-button-secondary">
            Back to workspace
          </Link>
        </div>
      </section>

      <section className="ops-auth-panel ops-auth-panel-form">
        {children}
        {footer ? <div className="ops-auth-footer">{footer}</div> : null}
      </section>
    </div>
  );
}
