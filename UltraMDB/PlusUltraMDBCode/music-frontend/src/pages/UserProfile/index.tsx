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
    
    initialize();
  }, [fetchGenres, user]);

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
    if (profile && profile.vector) {
      const processedData = profile.vector
        .map(dim => ({
          genreID: dim.GenreID,
          value: dim.value,
          name: getGenreNameById(dim.GenreID)
        }))
        .sort((a, b) => b.value - a.value);
      
      setSortedData(processedData);
    }
  }, [profile, getGenreNameById]);

  // 格式化偏好度显示
  const formatPreference = (value: number): string => {
    return (value * 100).toFixed(1) + '%';
  };

  // 获取颜色基于偏好度
  const getBarColor = (value: number, index: number): string => {
    const hue = 220 - (index * 15); // 从蓝色到紫色渐变
    const saturation = 70 + (value * 30); // 基于偏好度调整饱和度
    const lightness = 45 + (value * 20); // 基于偏好度调整亮度
    return `hsl(${Math.max(200, hue)}, ${Math.min(100, saturation)}%, ${Math.min(65, lightness)}%)`;
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

  if (!profile || sortedData.length === 0) {
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
              🎵 {sortedData.length} 个曲风偏好
            </span>
          </div>
        </div>
      </div>

      <div className="profile-content">
        <div className="chart-section">
          <h2>曲风偏好分布</h2>
          <div className="chart-container">
            <div className="bar-chart">
              {sortedData.map((item, index) => (
                <div key={item.genreID} className="bar-item">
                  <div className="bar-wrapper">
                    <div 
                      className="bar"
                      style={{
                        height: `${Math.max(item.value * 100, 2)}%`,
                        backgroundColor: getBarColor(item.value, index)
                      }}
                      title={`${item.name}: ${formatPreference(item.value)}`}
                    >
                      <div className="bar-value">
                        {formatPreference(item.value)}
                      </div>
                    </div>
                  </div>
                  <div className="bar-label">
                    {item.name}
                  </div>
                </div>
              ))}
            </div>
          </div>
        </div>

        <div className="data-section">
          <h2>详细数据</h2>
          <div className="data-table">
            <div className="table-header">
              <span>排名</span>
              <span>曲风</span>
              <span>偏好度</span>
              <span>占比条形图</span>
            </div>
            {sortedData.map((item, index) => (
              <div key={item.genreID} className="table-row">
                <span className="rank">#{index + 1}</span>
                <span className="genre-name">{item.name}</span>
                <span className="preference-value">{formatPreference(item.value)}</span>
                <span className="mini-bar">
                  <div 
                    className="mini-bar-fill"
                    style={{
                      width: `${item.value * 100}%`,
                      backgroundColor: getBarColor(item.value, index)
                    }}
                  ></div>
                </span>
              </div>
            ))}
          </div>
        </div>

        <div className="insights-section">
          <h2>📊 音乐偏好洞察</h2>
          <div className="insights-grid">
            <div className="insight-card">
              <h3>🏆 最偏爱的曲风</h3>
              <div className="top-genre">
                <span className="genre-icon">🎵</span>
                <span className="genre-text">
                  {sortedData[0]?.name} ({formatPreference(sortedData[0]?.value)})
                </span>
              </div>
            </div>
            
            <div className="insight-card">
              <h3>🎨 偏好多样性</h3>
              <div className="diversity-score">
                {sortedData.length >= 5 ? (
                  <span className="high-diversity">🌈 多样化偏好</span>
                ) : (
                  <span className="focused-preference">🎯 专注偏好</span>
                )}
              </div>
            </div>
            
            <div className="insight-card">
              <h3>📈 偏好强度</h3>
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