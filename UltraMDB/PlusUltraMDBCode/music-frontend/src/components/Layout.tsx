import React from 'react';
import { Link, Outlet, useNavigate } from 'react-router-dom';
import { clearAuth, getUser } from '../utils/storage';
import { authService } from '../services/auth.service';
import { usePermissions } from '../hooks/usePermissions';

const Layout: React.FC = () => {
  const navigate = useNavigate();
  const user = getUser();
  const { isAdmin, isUser, loading: permissionLoading } = usePermissions();

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
            <Link to="/" style={{ fontSize: '18px', fontWeight: 'bold' }}>
              Music Management System
            </Link>
            
            {/* 用户角色显示 */}
            <div style={{ 
              display: 'flex', 
              alignItems: 'center', 
              gap: '8px',
              fontSize: '14px'
            }}>
              <span style={{ opacity: 0.8 }}>
                {user?.account}
              </span>
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
            </div>
          </div>
          
          <div style={{ display: 'flex', alignItems: 'center', gap: '5px' }}>
            <Link to="/">Dashboard</Link>
            <Link to="/songs">Songs</Link>
            <Link to="/artists">Artists</Link>
            <Link to="/bands">Bands</Link>
            
            {/* 只有管理员可以看到曲风管理 */}
            {isAdmin && <Link to="/genres">Genres</Link>}
            {!isAdmin && !permissionLoading && (
              <span 
                style={{ 
                  color: '#6c757d', 
                  fontSize: '14px',
                  cursor: 'not-allowed',
                  padding: '8px 12px'
                }}
                title="需要管理员权限"
              >
                Genres
              </span>
            )}
            
            <a 
              href="#" 
              onClick={handleLogout}
              style={{ 
                color: '#ffc107',
                fontWeight: 'bold'
              }}
            >
              Logout
            </a>
          </div>
        </div>
      </nav>
      
      {/* 权限加载提示 */}
      {permissionLoading && (
        <div style={{
          backgroundColor: '#fff3cd',
          color: '#856404',
          padding: '8px 20px',
          textAlign: 'center',
          fontSize: '14px',
          borderBottom: '1px solid #ffeaa7'
        }}>
          <div className="loading-spinner" style={{ marginRight: '8px' }}></div>
          正在验证用户权限...
        </div>
      )}
      
      {/* 权限不足提示 */}
      {!permissionLoading && !isUser && !isAdmin && (
        <div style={{
          backgroundColor: '#f8d7da',
          color: '#721c24',
          padding: '12px 20px',
          textAlign: 'center',
          fontSize: '14px',
          borderBottom: '1px solid #f5c6cb'
        }}>
          ⚠️ 权限验证失败，某些功能可能受限。如有问题请重新登录或联系管理员。
        </div>
      )}
      
      <div className="container">
        <Outlet />
      </div>
    </>
  );
};

export default Layout;