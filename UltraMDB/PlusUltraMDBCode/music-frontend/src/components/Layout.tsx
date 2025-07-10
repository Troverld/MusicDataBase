import React from 'react';
import { Link, Outlet, useNavigate } from 'react-router-dom';
import { authService } from '../services/auth.service';
import { getUser, clearAuth } from '../utils/storage';
import { usePermissions } from '../hooks/usePermissions';

const Layout: React.FC = () => {
  const navigate = useNavigate();
  const user = getUser();
  const { isUser, isAdmin, loading: permissionLoading } = usePermissions();

  const handleLogout = async () => {
    if (user && user.userToken) {
      try {
        await authService.logout(user.userID, user.userToken);
      } catch (error) {
        console.error('Logout error:', error);
      }
    }
    clearAuth();
    navigate('/login');
  };

  // 获取用户角色显示文本
  const getUserRoleText = () => {
    if (permissionLoading) return '检查权限中...';
    if (isAdmin) return '管理员';
    if (isUser) return '用户';
    return '未知';
  };

  // 获取用户角色颜色
  const getUserRoleColor = () => {
    if (permissionLoading) return '#6c757d';
    if (isAdmin) return '#dc3545';
    if (isUser) return '#28a745';
    return '#6c757d';
  };

  return (
    <>
      <nav className="navbar">
        <div className="navbar-content">
          <div style={{ display: 'flex', alignItems: 'center', gap: '20px' }}>
            <Link to="/" style={{ fontSize: '22px', fontWeight: 'bold' }}>
              音乐管理系统
            </Link>
          </div>
          
          <div style={{ display: 'flex', alignItems: 'center', gap: '5px' }}>
            <Link to="/">仪表盘</Link>
            <Link to="/songs">歌曲库</Link>
            <Link to="/artists">艺术家</Link>
            <Link to="/bands">乐队</Link>
            
            {/* 所有已认证用户都可以查看曲风 */}
            {(isUser || isAdmin) && <Link to="/genres">曲风</Link>}
            
            {/* 推荐功能链接 - 只有已认证用户可以访问 */}
            {(isUser || isAdmin) && (
              <Link 
                to="/recommendations" 
                style={{ 
                  background: 'linear-gradient(135deg, #00C9FF 0%, #92FE9D 100%)',
                  padding: '6px 14px',
                  borderRadius: '16px',
                  fontSize: '13px',
                  fontWeight: '600',
                  textDecoration: 'none',
                  color: '#2c3e50',
                  border: '1px solid rgba(0,0,0,0.08)',
                  transition: 'all 0.3s ease',
                  display: 'inline-flex',
                  alignItems: 'center',
                  gap: '4px'
                }}
                onMouseEnter={(e) => {
                  e.currentTarget.style.transform = 'translateY(-2px)';
                  e.currentTarget.style.boxShadow = '0 4px 12px rgba(0,201,255,0.3)';
                }}
                onMouseLeave={(e) => {
                  e.currentTarget.style.transform = 'translateY(0)';
                  e.currentTarget.style.boxShadow = 'none';
                }}
              >
                <span>🎵</span>
                <span>推荐</span>
              </Link>
            )}
            
            {/* 用户画像链接 - 只有已认证用户可以访问 */}
            {(isUser || isAdmin) && (
              <Link 
                to="/profile" 
                style={{ 
                  background: 'linear-gradient(135deg, #667eea 0%, #764ba2 100%)',
                  padding: '6px 14px',
                  borderRadius: '16px',
                  fontSize: '13px',
                  fontWeight: '600',
                  textDecoration: 'none',
                  color: 'white',
                  border: '1px solid rgba(255,255,255,0.2)',
                  transition: 'all 0.3s ease',
                  display: 'inline-flex',
                  alignItems: 'center',
                  gap: '4px'
                }}
                onMouseEnter={(e) => {
                  e.currentTarget.style.transform = 'translateY(-2px)';
                  e.currentTarget.style.boxShadow = '0 4px 12px rgba(102,126,234,0.4)';
                }}
                onMouseLeave={(e) => {
                  e.currentTarget.style.transform = 'translateY(0)';
                  e.currentTarget.style.boxShadow = 'none';
                }}
              >
                <span>👤</span>
                <span>我的画像</span>
              </Link>
            )}
            
            {/* 用户名和角色标签显示 - 移到 Logout 按钮左边 */}
            <div style={{ 
              display: 'flex', 
              alignItems: 'center', 
              gap: '10px',
              marginLeft: '10px',
              paddingLeft: '10px',
              borderLeft: '1px solid rgba(0, 0, 0, 0.1)'
            }}>
              <span style={{ 
                fontSize: '14px',
                color: '#374151',
                fontWeight: '500'
              }}>
                {user?.account}
              </span>
              
              {/* 用户角色标签 - 现在在用户名右边 */}
              <span style={{ 
                backgroundColor: getUserRoleColor(),
                color: 'white',
                padding: '2px 8px',
                borderRadius: '12px',
                fontSize: '12px',
                fontWeight: 'bold'
              }}>
                {getUserRoleText()}
              </span>
              
              <button 
                onClick={handleLogout}
                style={{
                  background: '#ef4444',
                  color: 'white',
                  border: 'none',
                  padding: '6px 16px',
                  borderRadius: '6px',
                  fontSize: '14px',
                  fontWeight: '500',
                  cursor: 'pointer',
                  transition: 'all 0.2s ease'
                }}
                onMouseEnter={(e) => {
                  e.currentTarget.style.background = '#dc2626';
                  e.currentTarget.style.transform = 'translateY(-1px)';
                }}
                onMouseLeave={(e) => {
                  e.currentTarget.style.background = '#ef4444';
                  e.currentTarget.style.transform = 'translateY(0)';
                }}
              >
                登出
              </button>
            </div>
          </div>
        </div>
      </nav>
      
      <div className="container">
        <Outlet />
      </div>
    </>
  );
};

export default Layout;