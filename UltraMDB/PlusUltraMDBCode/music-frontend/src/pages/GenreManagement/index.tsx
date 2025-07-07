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
    <div>
      <h1>曲风管理</h1>
      <p style={{ color: '#666', marginBottom: '30px', fontSize: '16px' }}>
        {isAdmin 
          ? '管理系统中的音乐曲风，添加新曲风或删除现有曲风。您拥有管理员权限，可以创建和删除曲风。'
          : '查看系统中的音乐曲风分类。您可以浏览所有曲风信息，但需要管理员权限才能进行修改。'
        }
      </p>
      
      {error && <div className="error-message">{error}</div>}
      {success && <div className="success-message">{success}</div>}
      
      {/* 权限提示 */}
      {!isAdmin && (
        <div className="permission-warning" style={{ marginBottom: '20px' }}>
          ⚠️ 您当前以普通用户身份访问，只能查看曲风信息。如需创建或删除曲风，请联系管理员。
        </div>
      )}
      
      {/* 管理员功能区域 */}
      {isAdmin && (
        <div style={{ 
          display: 'grid', 
          gridTemplateColumns: '1fr 1fr', 
          gap: '40px', 
          marginTop: '20px' 
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

      {/* 使用提示 */}
      <div style={{ 
        background: '#f8f9fa', 
        padding: '25px', 
        borderRadius: '8px', 
        marginTop: '40px',
        border: '1px solid #e9ecef'
      }}>
        <h3 style={{ marginBottom: '15px', color: '#495057' }}>💡 曲风管理提示</h3>
        <div style={{ fontSize: '14px', color: '#6c757d', lineHeight: '1.6' }}>
          <p><strong>权限说明:</strong> {isAdmin ? '您拥有管理员权限，可以创建和删除曲风。' : '您当前是普通用户，只能查看曲风信息。'}</p>
          {isAdmin && (
            <>
              <p><strong>添加曲风：</strong> 系统会自动生成唯一的曲风ID，您只需填写名称和描述即可。</p>
              <p><strong>删除曲风：</strong> 从下拉框中选择要删除的曲风，删除前请确认没有歌曲正在使用该曲风。</p>
            </>
          )}
          <p><strong>曲风使用：</strong> 用户在上传或编辑歌曲时，可以从当前曲风列表中多选曲风进行标记。</p>
          <p><strong>数据一致性：</strong> 所有曲风操作都会立即同步到歌曲管理界面，确保数据一致性。</p>
          {!isAdmin && (
            <p><strong>权限申请:</strong> 如需管理曲风，请联系系统管理员申请相应权限。</p>
          )}
        </div>
      </div>
    </div>
  );
};

export default GenreManagement;