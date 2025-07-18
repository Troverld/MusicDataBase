import React from 'react';

interface PageHeaderProps {
  title: string;
  description: string;
}

export const PageHeader: React.FC<PageHeaderProps> = ({ title, description }) => (
  <div style={{ 
    textAlign: 'center', 
    marginBottom: '40px',
    animation: 'fadeIn 0.6s ease-out'
  }}>
    <h1 style={{ 
      fontSize: '36px', 
      fontWeight: '700',
      background: 'linear-gradient(135deg, #667eea 0%, #764ba2 100%)',
      WebkitBackgroundClip: 'text',
      WebkitTextFillColor: 'transparent',
      backgroundClip: 'text',
      marginBottom: '12px'
    }}>
      {title}
    </h1>
    <p style={{ 
      color: '#6b7280', 
      fontSize: '18px',
      maxWidth: '600px',
      margin: '0 auto',
      lineHeight: '1.6'
    }}>
      {description}
    </p>
  </div>
);

interface MessageProps {
  message: string;
  type: 'error' | 'success';
}

export const Message: React.FC<MessageProps> = ({ message, type }) => {
  const styles = type === 'error' 
    ? {
        background: '#fee2e2',
        border: '1px solid #fecaca',
        color: '#dc2626'
      }
    : {
        background: '#d1fae5',
        border: '1px solid #a7f3d0',
        color: '#059669'
      };

  const icon = type === 'error'
    ? (
        <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
          <circle cx="12" cy="12" r="10"/>
          <line x1="12" y1="8" x2="12" y2="12"/>
          <line x1="12" y1="16" x2="12.01" y2="16"/>
        </svg>
      )
    : (
        <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
          <path d="M22 11.08V12a10 10 0 1 1-5.93-9.14"/>
          <polyline points="22 4 12 14.01 9 11.01"/>
        </svg>
      );

  return (
    <div style={{
      ...styles,
      borderRadius: '12px',
      padding: '16px 20px',
      marginBottom: '24px',
      display: 'flex',
      alignItems: 'center',
      gap: '12px',
      animation: 'slideDown 0.3s ease-out'
    }}>
      {icon}
      {message}
    </div>
  );
};

interface CreateButtonProps {
  isAdmin: boolean;
  onClick: () => void;
}

export const CreateButton: React.FC<CreateButtonProps> = ({ isAdmin, onClick }) => {
  if (!isAdmin) {
    return (
      <div style={{
        background: '#fef3c7',
        border: '1px solid #fde68a',
        borderRadius: '12px',
        padding: '16px 24px',
        color: '#92400e',
        display: 'inline-flex',
        alignItems: 'center',
        gap: '12px'
      }}>
        <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
          <circle cx="12" cy="12" r="10"/>
          <line x1="12" y1="8" x2="12" y2="12"/>
          <line x1="12" y1="16" x2="12.01" y2="16"/>
        </svg>
        您没有创建乐队的权限，仅能查看和编辑您有权限的乐队
      </div>
    );
  }

  return (
    <button 
      onClick={onClick}
      style={{
        background: 'linear-gradient(135deg, #667eea 0%, #764ba2 100%)',
        color: 'white',
        border: 'none',
        padding: '14px 32px',
        borderRadius: '12px',
        fontSize: '16px',
        fontWeight: '600',
        cursor: 'pointer',
        display: 'inline-flex',
        alignItems: 'center',
        gap: '8px',
        boxShadow: '0 4px 14px rgba(102, 126, 234, 0.3)',
        transition: 'all 0.3s ease'
      }}
      onMouseEnter={(e) => {
        e.currentTarget.style.transform = 'translateY(-2px)';
        e.currentTarget.style.boxShadow = '0 6px 20px rgba(102, 126, 234, 0.4)';
      }}
      onMouseLeave={(e) => {
        e.currentTarget.style.transform = 'translateY(0)';
        e.currentTarget.style.boxShadow = '0 4px 14px rgba(102, 126, 234, 0.3)';
      }}
    >
      <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
        <line x1="12" y1="5" x2="12" y2="19"/>
        <line x1="5" y1="12" x2="19" y2="12"/>
      </svg>
      创建新乐队
    </button>
  );
};