import React from 'react';
import './ModernEmptyState.css';

interface ModernEmptyStateProps {
  icon?: React.ReactNode;
  title: string;
  description: string;
  actionButton?: React.ReactNode;
}

const ModernEmptyState: React.FC<ModernEmptyStateProps> = ({
  icon,
  title,
  description,
  actionButton
}) => {
  const defaultIcon = (
    <svg width="64" height="64" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1">
      <circle cx="12" cy="12" r="3"></circle>
      <path d="M12 1v6m0 6v6"></path>
      <path d="m21 12-6 0m-6 0-6 0"></path>
    </svg>
  );

  return (
    <div className="modern-empty-state">
      <div className="empty-state-content">
        {icon || defaultIcon}
        <h3>{title}</h3>
        <p>{description}</p>
        {actionButton && (
          <div className="empty-state-action">
            {actionButton}
          </div>
        )}
      </div>
    </div>
  );
};

export default ModernEmptyState;