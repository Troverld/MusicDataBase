import React from 'react';
import { Link } from 'react-router-dom';
import { getUser } from '../utils/storage';
import { usePermissions } from '../hooks/usePermissions';

const Dashboard: React.FC = () => {
  const user = getUser();
  const { isUser, isAdmin, loading: permissionLoading } = usePermissions();

  // 获取权限徽章
  const getPermissionBadge = () => {
    if (permissionLoading) {
      return (
        <span className="permission-badge loading">
          <div className="permission-loading-spinner"></div>
          验证中...
        </span>
      );
    }
    if (isAdmin) {
      return <span className="permission-badge admin">管理员</span>;
    }
    if (isUser) {
      return <span className="permission-badge user">已认证用户</span>;
    }
    return <span className="permission-badge guest">访客</span>;
  };

  // 获取系统统计信息
  const getSystemStats = () => {
    const stats = [
      { label: '音乐管理', icon: '🎵', description: '歌曲库管理与搜索' },
      { label: '创作者档案', icon: '👨‍🎤', description: '艺术家与乐队信息' },
      { label: '智能分析', icon: '🧠', description: '音乐偏好画像' },
      { label: '曲风分类', icon: '🎼', description: '音乐风格管理' }
    ];

    return stats;
  };

  // 获取快捷功能（重新设计，不重复导航栏功能）
  const getQuickInsights = () => {
    const insights = [];
    
    if (isUser || isAdmin) {
      insights.push({
        title: '我的音乐画像',
        description: '查看个性化音乐偏好分析',
        link: '/profile',
        icon: '🎨',
        gradient: 'from-purple-500 to-pink-500',
        special: true
      });
      
      insights.push({
        title: '推荐发现',
        description: '基于您的喜好推荐新音乐',
        link: '/recommendations',
        icon: '🌟',
        gradient: 'from-blue-500 to-cyan-500'
      });
    }

    // 管理员专属洞察
    if (isAdmin) {
      insights.push({
        title: '系统概览',
        description: '查看系统使用情况和统计数据',
        link: '#',
        icon: '📊',
        gradient: 'from-green-500 to-emerald-500',
        onClick: () => {
          // 这里可以添加系统概览的逻辑
          alert('系统概览功能开发中...');
        }
      });
    }

    return insights;
  };

  const quickInsights = getQuickInsights();
  const systemStats = getSystemStats();

  return (
    <div className="modern-dashboard">
      {/* 主要欢迎区域 */}
      <div className="welcome-section">
        <div className="welcome-content">
          <div className="welcome-text">
            <h1>欢迎回来，<span className="user-name">{user?.account || '用户'}</span></h1>
            <p className="welcome-subtitle">
              {isAdmin 
                ? '您拥有完整的系统管理权限，可以管理所有内容并查看详细统计数据。' 
                : isUser 
                ? '您可以管理自己的音乐作品，探索个性化音乐推荐，查看音乐偏好画像。'
                : '欢迎访问音乐管理系统，登录后可体验完整功能。'
              }
            </p>
          </div>
          <div className="welcome-badge">
            {getPermissionBadge()}
          </div>
        </div>
        
        <div className="welcome-decoration">
          <div className="floating-elements">
            <div className="floating-note">♪</div>
            <div className="floating-note">♫</div>
            <div className="floating-note">♬</div>
          </div>
        </div>
      </div>

      {/* 系统功能概览 */}
      <div className="system-overview">
        <h2 className="section-title">系统功能</h2>
        <div className="stats-grid">
          {systemStats.map((stat, index) => (
            <div key={index} className="stat-card">
              <div className="stat-icon">{stat.icon}</div>
              <div className="stat-content">
                <h3>{stat.label}</h3>
                <p>{stat.description}</p>
              </div>
            </div>
          ))}
        </div>
      </div>

      {/* 智能洞察 - 仅在有可用功能时显示 */}
      {quickInsights.length > 0 && (
        <div className="insights-section">
          <h2 className="section-title">智能洞察</h2>
          <div className="insights-grid">
            {quickInsights.map((insight, index) => (
              <div key={index} className="insight-card">
                {insight.link && insight.link !== '#' ? (
                  <Link to={insight.link} className="insight-link">
                    <div className={`insight-background bg-gradient-to-br ${insight.gradient}`}>
                      <div className="insight-icon">{insight.icon}</div>
                      <div className="insight-content">
                        <h3>{insight.title}</h3>
                        <p>{insight.description}</p>
                      </div>
                      <div className="insight-arrow">→</div>
                    </div>
                  </Link>
                ) : (
                  <div 
                    className="insight-link"
                    onClick={insight.onClick}
                    style={{ cursor: 'pointer' }}
                  >
                    <div className={`insight-background bg-gradient-to-br ${insight.gradient}`}>
                      <div className="insight-icon">{insight.icon}</div>
                      <div className="insight-content">
                        <h3>{insight.title}</h3>
                        <p>{insight.description}</p>
                      </div>
                      <div className="insight-arrow">→</div>
                    </div>
                  </div>
                )}
              </div>
            ))}
          </div>
        </div>
      )}

      {/* 系统信息 */}
      <div className="system-info">
        <div className="info-grid">
          <div className="info-card">
            <h4>数据安全</h4>
            <p>所有操作都经过严格的权限验证，确保您的数据安全和隐私保护。</p>
          </div>
          <div className="info-card">
            <h4>智能推荐</h4>
            <p>基于先进的机器学习算法，为您提供个性化的音乐发现体验。</p>
          </div>
          <div className="info-card">
            <h4>专业管理</h4>
            <p>提供完整的音乐作品管理工具，支持艺术家、乐队和曲风分类管理。</p>
          </div>
        </div>
      </div>
    </div>
  );
};

export default Dashboard;