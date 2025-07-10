import React, { useState } from 'react';
import { useGenres } from '../../hooks/useGenres';
import { usePermissions } from '../../hooks/usePermissions';
import AddGenreForm from './AddGenreForm';
import DeleteGenreForm from './DeleteGenreForm';
import GenreList from './GenreList';

const GenreManagement: React.FC = () => {
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');
  const [loading, setLoading] = useState(false);
  
  // 权限检查
  const { isAdmin, isUser, loading: permissionLoading } = usePermissions();
  
  // 使用曲风Hook获取最新的曲风列表
  const { genres, fetchGenres } = useGenres();

  // 如果权限还在加载中
  if (permissionLoading) {
    return (
      <div className="container">
        <div style={{ textAlign: 'center', padding: '40px' }}>
          <div className="loading-spinner"></div>
          正在检查权限...
        </div>
      </div>
    );
  }

  // 如果用户没有基本的查看权限
  if (!isUser && !isAdmin) {
    return (
      <div className="container">
        <div style={{ textAlign: 'center', padding: '40px' }}>
          <h2>访问受限</h2>
          <p>您需要登录并验证用户身份才能查看曲风信息。</p>
        </div>
      </div>
    );
  }

  const handleFormSuccess = async (message: string) => {
    setSuccess(message);
    setError('');
    await fetchGenres();
  };

  const handleFormError = (message: string) => {
    setError(message);
    setSuccess('');
  };

  const handleLoading = (isLoading: boolean) => {
    setLoading(isLoading);
  };

  return (
    <div style={{ 
      background: '#f8f9fa', 
      minHeight: 'calc(100vh - 70px)', 
      padding: '40px 20px' 
    }}>
      <div style={{ maxWidth: '1200px', margin: '0 auto' }}>
        {/* 页面标题和描述 */}
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
            曲风管理
          </h1>
          <p style={{ 
            color: '#6b7280', 
            fontSize: '18px',
            maxWidth: '600px',
            margin: '0 auto',
            lineHeight: '1.6'
          }}>
            {isAdmin 
              ? '管理系统中的音乐曲风，添加新曲风或删除现有曲风'
              : '查看系统中的音乐曲风分类'
            }
          </p>
        </div>
        
        {/* 提示信息 */}
        {error && (
          <div style={{
            background: '#fee2e2',
            border: '1px solid #fecaca',
            borderRadius: '12px',
            padding: '16px 20px',
            marginBottom: '24px',
            color: '#dc2626',
            display: 'flex',
            alignItems: 'center',
            gap: '12px',
            animation: 'slideDown 0.3s ease-out'
          }}>
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
              <circle cx="12" cy="12" r="10"/>
              <line x1="12" y1="8" x2="12" y2="12"/>
              <line x1="12" y1="16" x2="12.01" y2="16"/>
            </svg>
            {error}
          </div>
        )}
        
        {success && (
          <div style={{
            background: '#d1fae5',
            border: '1px solid #a7f3d0',
            borderRadius: '12px',
            padding: '16px 20px',
            marginBottom: '24px',
            color: '#059669',
            display: 'flex',
            alignItems: 'center',
            gap: '12px',
            animation: 'slideDown 0.3s ease-out'
          }}>
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
              <path d="M22 11.08V12a10 10 0 1 1-5.93-9.14"/>
              <polyline points="22 4 12 14.01 9 11.01"/>
            </svg>
            {success}
          </div>
        )}
        
        {/* 权限提示 */}
        {!isAdmin && (
          <div style={{
            background: '#fef3c7',
            border: '1px solid #fde68a',
            borderRadius: '12px',
            padding: '16px 24px',
            marginBottom: '32px',
            color: '#92400e',
            display: 'flex',
            alignItems: 'center',
            gap: '12px',
            maxWidth: '600px',
            margin: '0 auto 32px auto'
          }}>
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
              <circle cx="12" cy="12" r="10"/>
              <line x1="12" y1="8" x2="12" y2="12"/>
              <line x1="12" y1="16" x2="12.01" y2="16"/>
            </svg>
            您当前以普通用户身份访问，只能查看曲风信息。如需创建或删除曲风，请联系管理员。
          </div>
        )}
        
        {/* 管理员功能区域 */}
        {isAdmin && (
          <div style={{ 
            display: 'grid', 
            gridTemplateColumns: '1fr 1fr', 
            gap: '40px', 
            marginBottom: '40px',
            maxWidth: '1200px',
            margin: '0 auto 40px auto'
          }}>
            <AddGenreForm
              loading={loading}
              onSuccess={handleFormSuccess}
              onError={handleFormError}
              onLoading={handleLoading}
            />

            <DeleteGenreForm
              genres={genres}
              loading={loading}
              onSuccess={handleFormSuccess}
              onError={handleFormError}
              onLoading={handleLoading}
            />
          </div>
        )}

        {/* 当前曲风列表 */}
        <GenreList
          genres={genres}
          isAdmin={isAdmin}
        />
      </div>
    </div>
  );
};

export default GenreManagement;