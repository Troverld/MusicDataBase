import React from 'react';
import { ReturnInfo } from './types';

interface PageHeaderProps {
  returnInfo: ReturnInfo | null;
}

export const PageHeader: React.FC<PageHeaderProps> = ({ returnInfo }) => (
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
      歌曲管理
    </h1>
    <p style={{ 
      color: '#6b7280', 
      fontSize: '18px',
      maxWidth: '600px',
      margin: '0 auto',
      lineHeight: '1.6'
    }}>
      搜索、上传、编辑或删除系统中的音乐
      {returnInfo && (
        <span style={{ display: 'block', marginTop: '8px', fontStyle: 'italic', fontSize: '16px' }}>
          从 {returnInfo.type === 'artist' ? '艺术家' : '乐队'} 页面进入
        </span>
      )}
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

interface UploadButtonProps {
  canUploadSongs: boolean;
  onClick: () => void;
}

export const UploadButton: React.FC<UploadButtonProps> = ({ canUploadSongs, onClick }) => {
  if (!canUploadSongs) {
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
        您没有上传歌曲的权限，仅能搜索和查看歌曲信息
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
      上传新歌曲
    </button>
  );
};

export const LoadingState: React.FC = () => (
  <div style={{ textAlign: 'center', padding: '40px' }}>
    <p>正在加载歌曲信息...</p>
  </div>
);
