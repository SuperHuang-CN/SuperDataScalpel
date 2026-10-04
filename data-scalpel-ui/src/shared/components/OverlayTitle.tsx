import type { ReactNode } from 'react';

interface OverlayTitleProps {
  title: ReactNode;
  icon?: ReactNode;
  description?: ReactNode;
  tone?: 'default' | 'danger';
  variant?: 'business' | 'workspace' | 'popover';
}

/** Shared heading for Drawers, Modals, confirmations, and titled popovers. */
export const OverlayTitle = ({
  title,
  icon,
  description,
  tone = 'default',
  variant = 'business',
}: OverlayTitleProps) => (
  <span className={`overlay-title${tone === 'danger' ? ' overlay-title--danger' : ''}${variant === 'workspace' ? ' overlay-title--workspace' : ''}${variant === 'popover' ? ' overlay-title--popover' : ''}`}>
    {icon && <span className="overlay-title__icon" aria-hidden="true">{icon}</span>}
    <span className="overlay-title__copy">
      <span className="overlay-title__name" title={typeof title === 'string' ? title : undefined}>{title}</span>
      {description && <span className="overlay-title__description" title={typeof description === 'string' ? description : undefined}>{description}</span>}
    </span>
  </span>
);
