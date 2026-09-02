import type { ReactNode } from 'react';

interface BusinessDetailSectionProps {
  title: ReactNode;
  description: ReactNode;
  icon: ReactNode;
  extra?: ReactNode;
  className?: string;
  children: ReactNode;
}

export const BusinessDetailSection = ({
  title,
  description,
  icon,
  extra,
  className,
  children,
}: BusinessDetailSectionProps) => (
  <section className={['business-detail-section', className].filter(Boolean).join(' ')}>
    <header className="business-detail-section-header">
      <span className="business-detail-section-icon" aria-hidden>{icon}</span>
      <div className="business-detail-section-heading">
        <div className="business-detail-section-title">{title}</div>
        <div className="business-detail-section-description">{description}</div>
      </div>
      {extra && <div className="business-detail-section-extra">{extra}</div>}
    </header>
    <div className="business-detail-section-content">{children}</div>
  </section>
);
