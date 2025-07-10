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
        .sort((a, b) => b.value - a.value);
      
      setSortedData(processedData);
    }
  }, [profile, genresLoading, getGenreNameById]);

  // 格式化百分比
  const formatPercentage = (value: number): string => {
    return `${(value * 100).toFixed(1)}%`;
  };

  // 获取颜色
  const getColor = (index: number): string => {
    const colors = [
      '#6366f1', '#8b5cf6', '#ec4899', '#f59e0b', '#10b981', 
      '#3b82f6', '#f87171', '#34d399', '#fbbf24', '#a78bfa'
    ];
    return colors[index % colors.length];
  };

  // 计算总和（用于饼图）
  const total = sortedData.reduce((sum, item) => sum + item.value, 0);

  // 获取洞察数据
  const getInsights = () => {
    if (!sortedData.length) return null;

    const significantGenres = sortedData.filter(item => item.value > 0.05);
    const topGenre = sortedData[0];
    const hasStrongPreference = topGenre && topGenre.value > 0.3;
    const isDiverse = significantGenres.length >= 3;
    
    // 计算集中度（HHI指数）
    const hhi = sortedData.reduce((sum, item) => sum + Math.pow(item.value, 2), 0);
    const concentration = hhi > 0.5 ? '高度集中' : hhi > 0.3 ? '中度集中' : '分散均衡';

    // 获取相关推荐
    const recommendations = [];
    if (topGenre) {
      if (topGenre.name.includes('流行')) recommendations.push('探索独立音乐');
      if (topGenre.name.includes('摇滚')) recommendations.push('尝试电子音乐');
      if (topGenre.name.includes('古典')) recommendations.push('发现世界音乐');
    }

    return {
      topGenre,
      significantGenres,
      hasStrongPreference,
      isDiverse,
      concentration,
      recommendations
    };
  };

  const insights = getInsights();

  if (loading || genresLoading) {
    return (
      <div className="user-profile">
        <div className="profile-loading">
          <div className="loading-spinner"></div>
          <p>正在加载用户画像...</p>
        </div>
      </div>
    );
  }

  if (error) {
    return (
      <div className="user-profile">
        <div className="profile-error">
          <p>{error}</p>
        </div>
      </div>
    );
  }

  if (!profile || sortedData.length === 0) {
    return (
      <div className="user-profile">
        <div className="profile-empty">
          <p>还没有足够的数据生成您的音乐画像</p>
          <p className="empty-tip">多听听歌，我们会为您生成个性化的音乐偏好分析</p>
        </div>
      </div>
    );
  }

  return (
    <div className="user-profile">
      {/* 简约的头部 */}
      <div className="profile-header-simple">
        <h1>音乐画像</h1>
        <p className="user-subtitle">{user?.account} 的音乐品味分析</p>
      </div>
      
      <div className="profile-main-content">
        {/* 主要图表区域 - 使用饼图 */}
        <div className="chart-container">
          <h2>音乐偏好分布</h2>
          <div className="pie-chart-section">
            <div className="pie-chart-wrapper">
              <svg viewBox="0 0 200 200" className="pie-chart">
                {/* 背景圆 */}
                <circle cx="100" cy="100" r="90" fill="#f8f9fa" />
                
                {/* 饼图扇形 */}
                {sortedData.map((item, index) => {
                  const startAngle = sortedData.slice(0, index).reduce((sum, d) => sum + (d.value / total) * 360, -90);
                  const endAngle = startAngle + (item.value / total) * 360;
                  const largeArcFlag = (item.value / total) > 0.5 ? 1 : 0;
                  
                  const x1 = 100 + 90 * Math.cos(startAngle * Math.PI / 180);
                  const y1 = 100 + 90 * Math.sin(startAngle * Math.PI / 180);
                  const x2 = 100 + 90 * Math.cos(endAngle * Math.PI / 180);
                  const y2 = 100 + 90 * Math.sin(endAngle * Math.PI / 180);
                  
                  return (
                    <g key={item.genreID}>
                      <path
                        d={`M 100 100 L ${x1} ${y1} A 90 90 0 ${largeArcFlag} 1 ${x2} ${y2} Z`}
                        fill={getColor(index)}
                        stroke="#fff"
                        strokeWidth="2"
                        className="pie-slice"
                        opacity={item.value === 0 ? 0.3 : 1}
                      />
                    </g>
                  );
                })}
                
                {/* 中心圆（甜甜圈效果） */}
                <circle cx="100" cy="100" r="40" fill="#fff" />
                <text x="100" y="105" textAnchor="middle" className="center-text">
                  {sortedData.length} 种曲风
                </text>
              </svg>
            </div>
            
            {/* 图例 */}
            <div className="chart-legend">
              {sortedData.slice(0, 10).map((item, index) => (
                <div key={item.genreID} className="legend-item">
                  <span 
                    className="legend-color" 
                    style={{ backgroundColor: getColor(index) }}
                  />
                  <span className="legend-label">{item.name}</span>
                  <span className="legend-value">{formatPercentage(item.value)}</span>
                </div>
              ))}
            </div>
          </div>
        </div>

        {/* 优化后的洞察区域 */}
        {insights && (
          <div className="insights-container">
            <h2>个性化洞察</h2>
            <div className="insights-grid">
              {/* 音乐DNA */}
              <div className="insight-card dna">
                <h3>🧬 音乐DNA</h3>
                <div className="dna-content">
                  <div className="primary-taste">
                    <span className="label">核心品味</span>
                    <span className="value">{insights.topGenre.name}</span>
                  </div>
                  <div className="taste-mix">
                    <span className="label">品味组合</span>
                    <div className="mix-tags">
                      {insights.significantGenres.slice(0, 3).map((genre, idx) => (
                        <span key={idx} className="mix-tag" style={{ backgroundColor: getColor(idx) + '20', color: getColor(idx) }}>
                          {genre.name}
                        </span>
                      ))}
                    </div>
                  </div>
                </div>
              </div>

              {/* 听歌特征 */}
              <div className="insight-card characteristics">
                <h3>🎯 听歌特征</h3>
                <div className="characteristics-content">
                  <div className="characteristic">
                    <span className="char-label">偏好集中度</span>
                    <span className="char-value">{insights.concentration}</span>
                  </div>
                  <div className="characteristic">
                    <span className="char-label">品味多样性</span>
                    <span className="char-value">{insights.isDiverse ? '探索型' : '专一型'}</span>
                  </div>
                  <div className="characteristic">
                    <span className="char-label">偏好强度</span>
                    <span className="char-value">{insights.hasStrongPreference ? '鲜明' : '均衡'}</span>
                  </div>
                </div>
              </div>

              {/* 探索建议 */}
              <div className="insight-card suggestions">
                <h3>💡 探索建议</h3>
                <div className="suggestions-content">
                  {insights.recommendations.length > 0 ? (
                    insights.recommendations.map((rec, idx) => (
                      <div key={idx} className="suggestion-item">
                        <span className="suggestion-icon">→</span>
                        <span>{rec}</span>
                      </div>
                    ))
                  ) : (
                    <div className="suggestion-item">
                      <span className="suggestion-icon">→</span>
                      <span>继续探索更多音乐风格</span>
                    </div>
                  )}
                  <div className="exploration-tip">
                    基于您的 {insights.topGenre.name} 偏好推荐
                  </div>
                </div>
              </div>
            </div>
          </div>
        )}
      </div>
    </div>
  );
};

export default UserProfile;