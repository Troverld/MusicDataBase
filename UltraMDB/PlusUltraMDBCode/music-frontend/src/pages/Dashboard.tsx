import React from 'react';
import { Link } from 'react-router-dom';
import { getUser } from '../utils/storage';
import { usePermissions } from '../hooks/usePermissions';

const Dashboard: React.FC = () => {
  const user = getUser();
  const { isUser, isAdmin, loading: permissionLoading } = usePermissions();

  // 获取权限描述
  const getPermissionDescription = () => {
    if (permissionLoading) return '正在检查权限...';
    if (isAdmin) return '您拥有完整的系统管理权限，可以管理所有内容。';
    if (isUser) return '您可以上传和管理自己的歌曲，查看系统中的艺术家和乐队信息。';
    return '权限验证中遇到问题，部分功能可能受限。';
  };

  // 获取可用功能列表
  const getAvailableFeatures = () => {
    const features = [];
    
    // 所有用户都能查看的功能
    features.push(
      { 
        title: '🎵 歌曲管理', 
        description: '搜索和查看歌曲信息', 
        link: '/songs',
        available: true 
      },
      { 
        title: '🎤 艺术家信息', 
        description: '浏览艺术家档案和详情', 
        link: '/artists',
        available: true 
      },
      { 
        title: '🎸 乐队信息', 
        description: '查看乐队信息和成员列表', 
        link: '/bands',
        available: true 
      }
    );

    // 用户权限功能
    if (isUser || isAdmin) {
      features[0].description = '上传新歌曲，编辑自己的歌曲';
      
      // 添加曲风功能 - 所有已认证用户都可以访问
      features.push({
        title: '🎼 曲风管理',
        description: isAdmin ? '创建和管理音乐曲风分类' : '查看音乐曲风分类',
        link: '/genres',
        available: true
      });

      // 添加用户画像功能 - 所有已认证用户都可以访问
      features.push({
        title: '🎨 音乐画像',
        description: '查看您的个性化音乐偏好分析',
        link: '/profile',
        available: true,
        special: true // 标记为特殊功能，使用不同样式
      });
    }

    // 管理员专属权限调整
    if (isAdmin) {
      // 更新其他功能描述
      features[1].description = '创建、编辑和删除艺术家信息';
      features[2].description = '创建、编辑和删除乐队信息';
      features[0].description = '完整的歌曲管理，包括删除权限';
    }

    // 如果用户未认证，显示受限的功能
    if (!isUser && !isAdmin && !permissionLoading) {
      features.push({
        title: '🎼 曲风管理',
        description: '需要用户权限',
        link: '/genres',
        available: false
      });
      features.push({
        title: '🎨 音乐画像',
        description: '需要用户权限',
        link: '/profile',
        available: false
      });
    }

    return features;
  };

  const features = getAvailableFeatures();

  return (
    <div>
      <h1>Welcome to Music Management System</h1>
      <div style={{ 
        background: 'linear-gradient(135deg, #667eea 0%, #764ba2 100%)',
        color: 'white',
        padding: '30px',
        borderRadius: '8px',
        marginBottom: '30px'
      }}>
        <h2 style={{ margin: '0 0 10px 0' }}>Hello, {user?.account}!</h2>
        <p style={{ margin: 0, opacity: 0.9, fontSize: '16px' }}>
          {getPermissionDescription()}
        </p>
        
        {permissionLoading && (
          <div style={{ marginTop: '10px' }}>
            <div className="loading-spinner" style={{ marginRight: '8px' }}></div>
            <span style={{ fontSize: '14px' }}>权限验证中...</span>
          </div>
        )}
      </div>
      
      <div style={{ marginTop: '40px' }}>
        <h2>Quick Actions</h2>
        <div style={{ 
          marginTop: '20px', 
          display: 'grid', 
          gridTemplateColumns: 'repeat(auto-fit, minmax(250px, 1fr))', 
          gap: '20px',
          maxWidth: '1200px'
        }}>
          {features.map((feature, index) => (
            <div
              key={index}
              style={{
                background: feature.special ? 'linear-gradient(135deg, #667eea 0%, #764ba2 100%)' : 'white',
                borderRadius: '8px',
                padding: '20px',
                boxShadow: feature.special ? '0 4px 20px rgba(102, 126, 234, 0.3)' : '0 2px 10px rgba(0, 0, 0, 0.1)',
                border: feature.special ? 'none' : '1px solid #e9ecef',
                transition: 'all 0.3s ease',
                opacity: feature.available ? 1 : 0.6,
                color: feature.special ? 'white' : 'inherit'
              }}
              onMouseEnter={(e) => {
                if (feature.available) {
                  e.currentTarget.style.transform = 'translateY(-4px)';
                  e.currentTarget.style.boxShadow = feature.special 
                    ? '0 8px 30px rgba(102, 126, 234, 0.4)' 
                    : '0 8px 25px rgba(0, 0, 0, 0.15)';
                }
              }}
              onMouseLeave={(e) => {
                e.currentTarget.style.transform = 'translateY(0)';
                e.currentTarget.style.boxShadow = feature.special 
                  ? '0 4px 20px rgba(102, 126, 234, 0.3)' 
                  : '0 2px 10px rgba(0, 0, 0, 0.1)';
              }}
            >
              <h3 style={{ 
                margin: '0 0 10px 0', 
                color: feature.special ? 'white' : (feature.available ? '#333' : '#999')
              }}>
                {feature.title}
              </h3>
              <p style={{ 
                margin: '0 0 20px 0', 
                color: feature.special ? 'rgba(255,255,255,0.9)' : (feature.available ? '#666' : '#999'),
                fontSize: '14px',
                lineHeight: '1.5'
              }}>
                {feature.description}
              </p>
              
              {feature.available ? (
                <Link 
                  to={feature.link} 
                  className="btn btn-primary"
                  style={{ 
                    textDecoration: 'none',
                    width: '100%',
                    textAlign: 'center',
                    display: 'block',
                    padding: '12px',
                    background: feature.special ? 'rgba(255,255,255,0.2)' : undefined,
                    color: feature.special ? 'white' : undefined,
                    border: feature.special ? '1px solid rgba(255,255,255,0.3)' : undefined
                  }}
                  onMouseEnter={feature.special ? (e) => {
                    e.currentTarget.style.background = 'rgba(255,255,255,0.3)';
                  } : undefined}
                  onMouseLeave={feature.special ? (e) => {
                    e.currentTarget.style.background = 'rgba(255,255,255,0.2)';
                  } : undefined}
                >
                  {feature.special ? '查看我的画像' : '进入管理'}
                </Link>
              ) : (
                <div 
                  className="btn btn-secondary"
                  style={{ 
                    width: '100%',
                    textAlign: 'center',
                    display: 'block',
                    padding: '12px',
                    cursor: 'not-allowed',
                    opacity: 0.6
                  }}
                  title="需要更高权限"
                >
                  权限不足
                </div>
              )}
            </div>
          ))}
        </div>
      </div>

      {/* 权限信息面板 */}
      <div style={{ 
        background: '#f8f9fa', 
        padding: '25px', 
        borderRadius: '8px', 
        marginTop: '40px',
        border: '1px solid #e9ecef'
      }}>
        <h3 style={{ marginBottom: '15px', color: '#495057' }}>📊 权限信息</h3>
        <div style={{ 
          display: 'grid', 
          gridTemplateColumns: 'repeat(auto-fit, minmax(200px, 1fr))', 
          gap: '15px',
          marginBottom: '20px'
        }}>
          <div style={{ 
            background: 'white', 
            padding: '15px', 
            borderRadius: '6px',
            border: '1px solid #dee2e6'
          }}>
            <div style={{ fontSize: '12px', color: '#6c757d', marginBottom: '5px' }}>用户状态</div>
            <div style={{ fontSize: '16px', fontWeight: 'bold', color: isUser ? '#28a745' : '#dc3545' }}>
              {isUser ? '✓ 已认证' : '✗ 未认证'}
            </div>
          </div>
          
          <div style={{ 
            background: 'white', 
            padding: '15px', 
            borderRadius: '6px',
            border: '1px solid #dee2e6'
          }}>
            <div style={{ fontSize: '12px', color: '#6c757d', marginBottom: '5px' }}>管理员权限</div>
            <div style={{ fontSize: '16px', fontWeight: 'bold', color: isAdmin ? '#dc3545' : '#6c757d' }}>
              {isAdmin ? '✓ 拥有' : '✗ 无权限'}
            </div>
          </div>
          
          <div style={{ 
            background: 'white', 
            padding: '15px', 
            borderRadius: '6px',
            border: '1px solid #dee2e6'
          }}>
            <div style={{ fontSize: '12px', color: '#6c757d', marginBottom: '5px' }}>账户</div>
            <div style={{ fontSize: '16px', fontWeight: 'bold', color: '#495057' }}>
              {user?.account || 'Unknown'}
            </div>
          </div>
        </div>
        
        <div style={{ fontSize: '14px', color: '#6c757d', lineHeight: '1.6' }}>
          <p><strong>系统功能:</strong> 本系统支持音乐作品管理、艺术家档案管理、乐队信息管理、曲风分类管理和个性化音乐画像分析。</p>
          <p><strong>权限说明:</strong> 不同用户角色拥有不同的操作权限，管理员拥有完整权限，普通用户可管理自己的内容并查看公共信息。</p>
          <p><strong>数据安全:</strong> 所有操作都经过权限验证，确保数据安全和用户隐私。</p>
          {(isUser || isAdmin) && (
            <p><strong>音乐画像:</strong> 基于您的播放历史和评分行为，系统会生成个性化的音乐偏好分析，帮助您更好地了解自己的音乐品味。</p>
          )}
        </div>
      </div>
    </div>
  );
};

export default Dashboard;