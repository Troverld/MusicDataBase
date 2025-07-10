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
    
    // 只在 user 存在且有效时执行一次
    if (user && user.userID && user.userToken) {
      initialize();
    } else {
      setError('用户未登录');
      setLoading(false);
    }
  }, [user?.userID]);

  // 重新加载用户画像的函数（用于重试按钮）
  const reloadUserPortrait = async () => {
    if (!user) {
      setError('用户未登录');
      return;
    }

    try {
      setLoading(true);
      setError('');
      
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

  // 曲风数据变化后重新处理排序数据
  useEffect(() => {
    if (profile && profile.vector && profile.vector.length > 0) {
      console.log('Profile data received:', profile);
      
      const processedData = profile.vector
        .map(dim => {
          // 兼容后端的 GenreID 字段名（大写 G）
          const genreID = dim.GenreID;
          
          if (!genreID) {
            console.warn('Missing genreID in dim:', dim);
            return null;
          }
          
          return {
            genreID: genreID,
            value: dim.value,
            name: getGenreNameById(genreID) || `未知曲风(${genreID})`
          };
        })
        .filter(item => item !== null)
        .sort((a, b) => b!.value - a!.value) as Array<{genreID: string, value: number, name: string}>;
      
      console.log('Processed data:', processedData);
      setSortedData(processedData);
    } else {
      setSortedData([]);
    }
  }, [profile, getGenreNameById]);

  // 格式化偏好度显示
  const formatPreference = (value: number): string => {
    return (value * 100).toFixed(1) + '%';
  };

  // 获取渐变颜色
  const getGradientColor = (value: number, index: number): string => {
    const colors = [
      'linear-gradient(135deg, #667eea 0%, #764ba2 100%)',
      'linear-gradient(135deg, #f093fb 0%, #f5576c 100%)',
      'linear-gradient(135deg, #4facfe 0%, #00f2fe 100%)',
      'linear-gradient(135deg, #43e97b 0%, #38f9d7 100%)',
      'linear-gradient(135deg, #fa709a 0%, #fee140 100%)',
      'linear-gradient(135deg, #a8edea 0%, #fed6e3 100%)',
      'linear-gradient(135deg, #ffecd2 0%, #fcb69f 100%)',
      'linear-gradient(135deg, #ff8a80 0%, #ea4c46 100%)',
    ];
    return colors[index % colors.length];
  };

  if (loading || genresLoading) {
    return (
      <div className="user-profile">
        <h1>用户音乐画像</h1>
        <div className="profile-loading">
          <div className="loading-spinner"></div>
          <p>正在加载您的音乐画像...</p>
        </div>
      </div>
    );
  }

  if (error) {
    return (
      <div className="user-profile">
        <h1>用户音乐画像</h1>
        <div className="profile-error">
          <p>❌ {error}</p>
          <button className="btn btn-primary" onClick={reloadUserPortrait}>
            重新加载
          </button>
        </div>
      </div>
    );
  }

  if (!profile || !profile.vector || sortedData.length === 0) {
    return (
      <div className="user-profile">
        <h1>用户音乐画像</h1>
        <div className="profile-empty">
          <p>🎵 暂无音乐画像数据</p>
          <p className="empty-tip">
            多听一些歌曲并进行评分，系统将为您生成个性化的音乐画像！
          </p>
        </div>
      </div>
    );
  }

  return (
    <div className="user-profile">
      <div className="profile-header">
        <h1>🎼 用户音乐画像</h1>
        <div className="profile-info">
          <div className="user-info">
            <strong>{user?.account}</strong> 的音乐偏好分析
          </div>
          <div className="profile-stats">
            <span className="stat-item">
              📊 {profile.norm ? '已归一化' : '未归一化'}
            </span>
            <span className="stat-item">
              🎵 {sortedData.length} 种曲风
            </span>
          </div>
        </div>
      </div>
      
      <div className="profile-content">
        {/* 现代化水平条形图 */}
        <div className="chart-section">
          <h2>音乐偏好分布</h2>
          <div className="modern-chart-container">
            {sortedData.map((item, index) => (
              <div key={item.genreID} className="horizontal-bar-item">
                <div className="bar-info">
                  <span className="genre-name">{item.name}</span>
                  <span className="preference-value">{formatPreference(item.value)}</span>
                </div>
                <div className="bar-track">
                  <div 
                    className="bar-fill"
                    style={{
                      width: `${Math.max(item.value * 100, 2)}%`,
                      background: getGradientColor(item.value, index),
                      animationDelay: `${index * 0.1}s`
                    }}
                  >
                    <div className="bar-shine"></div>
                  </div>
                </div>
              </div>
            ))}
          </div>
        </div>

        {/* 圆环图风格的概览 */}
        <div className="chart-section">
          <h2>偏好强度概览</h2>
          <div className="circular-chart-container">
            {sortedData.slice(0, 6).map((item, index) => (
              <div key={item.genreID} className="circular-item">
                <div className="circular-progress">
                  <svg className="progress-ring" width="120" height="120">
                    <circle
                      className="progress-ring-circle-bg"
                      stroke="#e9ecef"
                      strokeWidth="8"
                      fill="transparent"
                      r="50"
                      cx="60"
                      cy="60"
                    />
                    <circle
                      className="progress-ring-circle"
                      stroke={`url(#gradient${index})`}
                      strokeWidth="8"
                      fill="transparent"
                      r="50"
                      cx="60"
                      cy="60"
                      strokeDasharray={`${item.value * 314} 314`}
                      strokeDashoffset="0"
                      style={{
                        animationDelay: `${index * 0.2}s`
                      }}
                    />
                    <defs>
                      <linearGradient id={`gradient${index}`} x1="0%" y1="0%" x2="100%" y2="100%">
                        <stop offset="0%" stopColor="#667eea" />
                        <stop offset="100%" stopColor="#764ba2" />
                      </linearGradient>
                    </defs>
                  </svg>
                  <div className="progress-text">
                    <span className="percentage">{formatPreference(item.value)}</span>
                    <span className="genre">{item.name}</span>
                  </div>
                </div>
              </div>
            ))}
          </div>
        </div>
        
        {/* 详细数据表格（简化版） */}
        <div className="data-section">
          <h2>详细偏好数据</h2>
          <div className="modern-table">
            {sortedData.map((item, index) => (
              <div key={item.genreID} className="table-item">
                <div className="rank-badge">#{index + 1}</div>
                <div className="genre-info">
                  <h4>{item.name}</h4>
                  <div className="progress-bar">
                    <div 
                      className="progress-fill"
                      style={{
                        width: `${item.value * 100}%`,
                        background: getGradientColor(item.value, index)
                      }}
                    />
                  </div>
                </div>
                <div className="preference-badge">{formatPreference(item.value)}</div>
              </div>
            ))}
          </div>
        </div>
        
        {/* 用户画像洞察 */}
        <div className="insights-section">
          <h2>音乐偏好洞察</h2>
          <div className="insights-grid">
            <div className="insight-card primary">
              <div className="insight-icon">🎵</div>
              <h3>主要偏好</h3>
              <p className="primary-genre">{sortedData[0]?.name || '暂无'}</p>
              <p className="preference-level">偏好度：{sortedData[0] ? 
                formatPreference(sortedData[0].value) : '0%'}</p>
            </div>
            
            <div className="insight-card diversity">
              <div className="insight-icon">🎨</div>
              <h3>偏好多样性</h3>
              <div className="diversity-score">
                {sortedData.filter(item => item.value > 0.1).length >= 3 ? (
                  <span className="high-diversity">🌈 多样化偏好</span>
                ) : (
                  <span className="focused-preference">🎯 专注偏好</span>
                )}
              </div>
            </div>
            
            <div className="insight-card strength">
              <div className="insight-icon">📈</div>
              <h3>偏好强度</h3>
              <div className="preference-strength">
                {sortedData[0]?.value > 0.3 ? (
                  <span className="strong-preference">💪 偏好明显</span>
                ) : (
                  <span className="balanced-preference">⚖️ 偏好均衡</span>
                )}
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
};

export default UserProfile;