import React from 'react';
import './UserProfileStates.css';

interface PageHeaderProps {
  userAccount?: string;
}

export const PageHeader: React.FC<PageHeaderProps> = ({ userAccount }) => (
  <div style={{ 
    textAlign: 'center', 
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
      音乐画像
    </h1>
    <p style={{ 
      color: '#6b7280', 
      fontSize: '18px',
      maxWidth: '600px',
      margin: '0 auto',
      lineHeight: '1.6'
    }}>
      {userAccount} 的个人音乐品味分析
    </p>
  </div>
);

export const LoadingState: React.FC = () => (
  <div className="profile-container">
    <div className="profile-loading-new">
      <div className="loading-spinner"></div>
      <p>正在生成你的音乐画像...</p>
    </div>
  </div>
);

interface ErrorStateProps {
  error: string;
}

export const ErrorState: React.FC<ErrorStateProps> = ({ error }) => (
  <div className="profile-container">
    <div className="profile-error-new">
      <div className="error-icon">⚠️</div>
      <p>{error}</p>
    </div>
  </div>
);

export const EmptyState: React.FC = () => (
  <div className="profile-container">
    <div className="profile-empty-new">
      <div className="empty-icon">🎵</div>
      <h2>还没有音乐画像</h2>
      <p>多听听歌，让我们更了解你的音乐品味</p>
    </div>
  </div>
);
