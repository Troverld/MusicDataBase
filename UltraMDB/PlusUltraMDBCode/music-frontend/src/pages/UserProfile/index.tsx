import React, { useState, useEffect } from 'react';
import { statisticsService } from '../../services/statistics.service';
import { useGenres } from '../../hooks/useGenres';
import { Profile, Dim } from '../../types';
import { getUser } from '../../utils/storage';
import './UserProfile.css';

const UserProfile: React.FC = () => {
  const [profile, setProfile] = useState<Profile | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [sortedData, setSortedData] = useState<Array<{genreID: string, value: number, name: string}>>([]);
  
  const { getGenreNameById, fetchGenres, loading: genresLoading } = useGenres();
  const user = getUser();

  // 初始化
  useEffect(() => {
    const initialize = async () => {
      if (!user) {
        setError('用户未登录');
        setLoading(false);
        return;
      }

      try {
        setLoading(true);
        setError('');
        
        // 先获取曲风列表
        await fetchGenres();
        
        // 然后获取用户画像
        const [portraitData, message] = await statisticsService.getUserPortrait();
        
        if (portraitData) {
          setProfile(portraitData);
        } else {
          setError(message || '获取用户画像失败');
        }
      } catch (err: any) {
        setError(err.message || '获取用户画像失败');
      } finally {
        setLoading(false);
      }
    };
    
    if (user && user.userID && user.userToken) {
      initialize();
    } else {
      setError('用户未登录');
      setLoading(false);
    }
  }, []);

  // 处理和排序数据
  useEffect(() => {
    if (profile && profile.vector && !genresLoading) {
      const processedData = profile.vector
        .map(dim => ({
          genreID: dim.GenreID,
          value: dim.value || 0,
          name: getGenreNameById(dim.GenreID) || `Genre ${dim.GenreID}`
        }))
        .sort((a, b) => b.value - a.value)
        .filter(item => item.value > 0);
      
      setSortedData(processedData);
    }
  }, [profile, genresLoading, getGenreNameById]);

  // 计算总分值
  const total = sortedData.reduce((sum, item) => sum + item.value, 0);

  // 获取颜色
  const getColor = (index: number): string => {
    const colors = [
      '#6366f1', // 紫色
      '#8b5cf6', // 深紫
      '#ec4899', // 粉色
      '#f59e0b', // 橙色
      '#10b981', // 绿色
      '#3b82f6', // 蓝色
      '#ef4444', // 红色
      '#14b8a6'  // 青色
    ];
    return colors[index % colors.length];
  };

  // 获取前N个主要曲风
  const getTopGenres = (n: number = 5) => {
    return sortedData.slice(0, n);
  };

  // 分析用户特征
  const getUserCharacteristics = () => {
    if (sortedData.length === 0) return null;

    const topGenres = getTopGenres(3);
    const topPercentage = topGenres.reduce((sum, g) => sum + (g.value / total * 100), 0);
    const diversity = sortedData.length;
    
    return {
      mainStyle: topGenres[0]?.name || '未知',
      concentration: topPercentage > 70 ? '专一' : topPercentage > 50 ? '偏好明显' : '多元化',
      diversity: diversity > 10 ? '高' : diversity > 5 ? '中' : '低',
      topGenres
    };
  };

  const characteristics = getUserCharacteristics();

  if (loading || genresLoading) {
    return (
      <div className="profile-container">
        <div className="profile-loading-new">
          <div className="loading-spinner"></div>
          <p>正在生成你的音乐画像...</p>
        </div>
      </div>
    );
  }

  if (error) {
    return (
      <div className="profile-container">
        <div className="profile-error-new">
          <div className="error-icon">⚠️</div>
          <p>{error}</p>
        </div>
      </div>
    );
  }

  if (!profile || sortedData.length === 0) {
    return (
      <div className="profile-container">
        <div className="profile-empty-new">
          <div className="empty-icon">🎵</div>
          <h2>还没有音乐画像</h2>
          <p>多听听歌，让我们更了解你的音乐品味</p>
        </div>
      </div>
    );
  }

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
        音乐画像</h1>
        <p style={{ 
          color: '#6b7280', 
          fontSize: '18px',
          maxWidth: '600px',
          margin: '0 auto',
          lineHeight: '1.6'
        }}>
        {user?.account} 的个人音乐品味分析
        </p>
        </div>
      </div>
      
      <div className="profile-content">
        {/* 特征卡片 */}
        {characteristics && (
          <div className="characteristics-section">
            <div className="characteristic-cards">
              <div className="char-card">
                <div className="char-icon">🎯</div>
                <div className="char-info">
                  <h3>主要风格</h3>
                  <p>{characteristics.mainStyle}</p>
                </div>
              </div>
              
              <div className="char-card">
                <div className="char-icon">📊</div>
                <div className="char-info">
                  <h3>偏好集中度</h3>
                  <p>{characteristics.concentration}</p>
                </div>
              </div>
              
              <div className="char-card">
                <div className="char-icon">🌈</div>
                <div className="char-info">
                  <h3>音乐多样性</h3>
                  <p>{characteristics.diversity}</p>
                </div>
              </div>
            </div>
          </div>
        )}

        {/* 主要内容区域 */}
        <div className="profile-main-new">
          {/* 左侧：可视化图表 */}
          <div className="visualization-section">
            <h2>偏好分布</h2>
            
            {/* 环形图 */}
            <div className="donut-chart-container">
              <svg viewBox="0 0 240 240" className="donut-chart">
                {/* 背景圆环 */}
                <circle 
                  cx="120" 
                  cy="120" 
                  r="90" 
                  fill="none" 
                  stroke="#f3f4f6" 
                  strokeWidth="40"
                />
                
                {/* 数据环形 */}
                {getTopGenres().map((item, index) => {
                  const percentage = (item.value / total) * 100;
                  const startAngle = getTopGenres()
                    .slice(0, index)
                    .reduce((sum, d) => sum + (d.value / total) * 360, -90);
                  const dashArray = `${(percentage / 100) * 565.5} 565.5`;
                  
                  return (
                    <circle
                      key={item.genreID}
                      cx="120"
                      cy="120"
                      r="90"
                      fill="none"
                      stroke={getColor(index)}
                      strokeWidth="40"
                      strokeDasharray={dashArray}
                      strokeDashoffset={0}
                      transform={`rotate(${startAngle} 120 120)`}
                      className="donut-segment"
                    />
                  );
                })}
                
                {/* 中心文字 */}
                <text x="120" y="110" textAnchor="middle" className="center-text">
                  <tspan x="120" dy="0" className="center-number">
                    {sortedData.length}
                  </tspan>
                  <tspan x="120" dy="25" className="center-label">
                    种曲风
                  </tspan>
                </text>
              </svg>
            </div>

            {/* 图例 */}
            <div className="legend-new">
              {getTopGenres().map((item, index) => (
                <div key={item.genreID} className="legend-item-new">
                  <span 
                    className="legend-dot" 
                    style={{ backgroundColor: getColor(index) }}
                  />
                  <span className="legend-name">{item.name}</span>
                  <span className="legend-percent">
                    {((item.value / total) * 100).toFixed(1)}%
                  </span>
                </div>
              ))}
            </div>
          </div>

          {/* 右侧：详细列表 */}
          <div className="details-section">
            <h2>完整偏好列表</h2>
            
            <div className="genre-list-new">
              {sortedData.map((item, index) => (
                <div key={item.genreID} className="genre-item-new">
                  <div className="genre-rank">#{index + 1}</div>
                  <div className="genre-content">
                    <div className="genre-header">
                      <span className="genre-name">{item.name}</span>
                      <span className="genre-value">
                        {((item.value / total) * 100).toFixed(1)}%
                      </span>
                    </div>
                    <div className="genre-bar">
                      <div 
                        className="genre-bar-fill"
                        style={{ 
                          width: `${(item.value / sortedData[0].value) * 100}%`,
                          backgroundColor: getColor(index)
                        }}
                      />
                    </div>
                  </div>
                </div>
              ))}
            </div>

            {/* 建议卡片 */}
            <div className="suggestion-card">
              <h3>💡 探索建议</h3>
              <p>
                基于你对 <strong>{characteristics?.mainStyle}</strong> 的偏好，
                你可能也会喜欢相关的音乐风格。继续探索不同类型的音乐，
                让你的音乐世界更加丰富多彩！
              </p>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
};

export default UserProfile;