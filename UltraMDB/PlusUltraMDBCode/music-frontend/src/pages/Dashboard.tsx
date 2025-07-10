import React, { useState, useEffect } from 'react';
import { Link } from 'react-router-dom';
import { getUser } from '../../utils/storage';
import { usePermissions } from '../../hooks/usePermissions';
import { statisticsService } from '../../services/statistics.service';

const Dashboard: React.FC = () => {
  const user = getUser();
  const { isUser, isAdmin, loading: permissionLoading } = usePermissions();
  const [userStats, setUserStats] = useState({ songCount: 0, ratingCount: 0 });
  const [recentActivity, setRecentActivity] = useState<any[]>([]);

  useEffect(() => {
    // 获取用户统计数据
    const fetchUserStats = async () => {
      if (user?.userID) {
        try {
          // 这里模拟获取用户统计数据
          // 实际应该从后端获取
          const portrait = await statisticsService.getUserPortrait(user.userID);
          if (portrait) {
            setUserStats({
              songCount: Math.floor(Math.random() * 50) + 10,
              ratingCount: portrait.vector.length * 5
            });
          }
        } catch (error) {
          console.error('Failed to fetch user stats:', error);
        }
      }
    };

    fetchUserStats();
  }, [user]);

  // 模拟最近活动数据
  useEffect(() => {
    const activities = [
      { icon: '🎵', text: '开始探索音乐世界', time: '刚刚' },
      { icon: '⭐', text: '评价您喜欢的歌曲', time: '推荐' },
      { icon: '🎨', text: '查看您的音乐画像', time: '个性化' }
    ];
    setRecentActivity(activities);
  }, []);

  // 获取功能卡片数据
  const getActionCards = () => {
    const cards = [
      {
        id: 'songs',
        icon: '🎵',
        title: '歌曲库',
        description: '探索海量音乐，发现您喜欢的歌曲',
        link: '/songs',
        available: true
      },
      {
        id: 'artists',
        icon: '🎤',
        title: '艺术家',
        description: '了解您喜爱的艺术家，探索他们的作品',
        link: '/artists',
        available: true
      },
      {
        id: 'bands',
        icon: '🎸',
        title: '乐队',
        description: '发现精彩乐队，感受团队的音乐魅力',
        link: '/bands',
        available: true
      }
    ];

    // 根据权限添加功能
    if (isUser || isAdmin) {
      cards.push({
        id: 'genres',
        icon: '🎼',
        title: '曲风分类',
        description: isAdmin ? '管理音乐曲风分类' : '浏览各种音乐风格',
        link: '/genres',
        available: true
      });

      cards.push({
        id: 'profile',
        icon: '✨',
        title: '音乐画像',
        description: '您的个性化音乐偏好分析',
        link: '/profile',
        available: true,
        special: true
      });

      cards.push({
        id: 'recommendations',
        icon: '🎯',
        title: '个性推荐',
        description: '基于您的喜好推荐音乐',
        link: '/recommendations',
        available: true,
        special: true
      });
    }

    return cards;
  };

  const actionCards = getActionCards();

  if (permissionLoading) {
    return (
      <div className="dashboard">
        <div className="dashboard-container">
          <div style={{ textAlign: 'center', padding: '100px 20px' }}>
            <div className="loading-spinner"></div>
            <p style={{ marginTop: '20px', color: 'rgba(255, 255, 255, 0.6)' }}>
              正在加载您的音乐世界...
            </p>
          </div>
        </div>
      </div>
    );
  }

  return (
    <div className="dashboard">
      <div className="dashboard-container">
        {/* 欢迎区域 */}
        <div className="welcome-section">
          <h1 className="welcome-title">欢迎回来</h1>
          <p className="welcome-subtitle">
            让音乐点亮您的每一天
          </p>
        </div>

        {/* 用户信息卡片 */}
        <div className="user-info-card">
          <div className="user-details">
            <div className="user-avatar">
              {user?.account?.charAt(0).toUpperCase() || 'U'}
            </div>
            <div className="user-text">
              <h2 className="user-name">{user?.account || 'Unknown'}</h2>
              <span className={`user-role ${isAdmin ? 'role-admin' : 'role-user'}`}>
                {isAdmin ? '🛡️ 管理员' : '🎵 音乐爱好者'}
              </span>
            </div>
          </div>
          
          {(isUser || isAdmin) && (
            <div className="user-stats">
              <div className="stat-item">
                <div className="stat-value">{userStats.songCount}</div>
                <div className="stat-label">收藏歌曲</div>
              </div>
              <div className="stat-item">
                <div className="stat-value">{userStats.ratingCount}</div>
                <div className="stat-label">评价次数</div>
              </div>
            </div>
          )}
        </div>

        {/* 快速操作 */}
        <div className="quick-actions">
          <h2 className="section-title">开始探索</h2>
          <div className="actions-grid">
            {actionCards.map((card) => (
              <Link
                key={card.id}
                to={card.link}
                className={`action-card ${card.special ? 'special' : ''} ${!card.available ? 'disabled' : ''}`}
                onClick={(e) => !card.available && e.preventDefault()}
              >
                <span className="action-icon">{card.icon}</span>
                <h3 className="action-title">{card.title}</h3>
                <p className="action-description">{card.description}</p>
                {card.special && <span className="action-badge">热门功能</span>}
              </Link>
            ))}
          </div>
        </div>

        {/* 活动动态 */}
        <div className="activity-section">
          <div className="activity-header">
            <h2 className="activity-title">快速开始</h2>
          </div>
          <div className="activity-list">
            {recentActivity.map((activity, index) => (
              <div key={index} className="activity-item">
                <div className="activity-icon">{activity.icon}</div>
                <div className="activity-content">
                  <p className="activity-text">{activity.text}</p>
                  <span className="activity-time">{activity.time}</span>
                </div>
              </div>
            ))}
          </div>
        </div>
      </div>
    </div>
  );
};

export default Dashboard;