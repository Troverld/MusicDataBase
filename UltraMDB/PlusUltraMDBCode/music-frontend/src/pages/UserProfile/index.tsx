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
  }, [user?.userID]); // 修复：简化依赖项，避免重复调用

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
      console.log('Profile data received:', profile); // 调试日志
      
      const processedData = profile.vector
        .map(dim => {
          // 修复：兼容后端的 GenreID 字段名（大写 G）
          const genreID = (dim as any).GenreID || dim.genreID || (dim as any).genreid;
          
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
        .filter(item => item !== null) // 过滤掉无效数据
        .sort((a, b) => b!.value - a!.value) as Array<{genreID: string, value: number, name: string}>;
      
      console.log('Processed data:', processedData); // 调试日志
      setSortedData(processedData);
    } else {
      setSortedData([]);
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

  if (!profile || !profile.vector || sortedData.length === 0) {
    return (
      <div className="user-profile">
        <h1>用户音乐画像</h1>
        <div className="profile-empty">
          <p>🎵 暂无音乐画像数据</p>
          <p className="empty-tip">
            多听一些歌曲并进行评分，系统将为您生成个性化的音乐画像！
          </p>
          {/* 调试信息 */}
          {profile && (
            <div style={{ marginTop: '20px', padding: '10px', background: '#f0f0f0', fontSize: '12px' }}>
              <strong>调试信息：</strong>
              <pre>{JSON.stringify(profile, null, 2)}</pre>
            </div>
          )}
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
        {/* 条形图表 */}
        <div className="chart-section">
          <h2>音乐偏好分布</h2>
          <div className="chart-container">
            <div className="bar-chart">
              {sortedData.map((item, index) => (
                <div key={item.genreID} className="bar-item">
                  <div 
                    className="bar" 
                    style={{
                      height: `${Math.max(item.value * 300, 20)}px`, // 确保最小高度
                      backgroundColor: getBarColor(item.value, index),
                      minHeight: '20px'
                    }}
                  >
                    <span className="bar-value">{formatPreference(item.value)}</span>
                  </div>
                  <div className="bar-label" title={item.name}>{item.name}</div>
                </div>
              ))}
            </div>
          </div>
        </div>
        
        {/* 详细数据表格 */}
        <div className="data-section">
          <h2>详细偏好数据</h2>
          <div className="data-table">
            <div className="table-header">
              <div className="table-cell">排名</div>
              <div className="table-cell">曲风</div>
              <div className="table-cell">偏好度</div>
              <div className="table-cell">可视化</div>
            </div>
            {sortedData.map((item, index) => (
              <div key={item.genreID} className="table-row">
                <div className="table-cell rank">#{index + 1}</div>
                <div className="table-cell genre-name">{item.name}</div>
                <div className="table-cell percentage">{formatPreference(item.value)}</div>
                <div className="table-cell">
                  <div className="mini-bar-container">
                    <div 
                      className="mini-bar"
                      style={{
                        width: `${Math.max(item.value * 100, 5)}%`, // 确保最小宽度
                        backgroundColor: getBarColor(item.value, index)
                      }}
                    />
                  </div>
                </div>
              </div>
            ))}
          </div>
        </div>
        
        {/* 用户画像洞察 */}
        <div className="insights-section">
          <h2>音乐偏好洞察</h2>
          <div className="insights-grid">
            <div className="insight-card">
              <h3>🎵 主要偏好</h3>
              <p className="primary-genre">{sortedData[0]?.name || '暂无'}</p>
              <p className="preference-level">偏好度：{sortedData[0] ? 
                formatPreference(sortedData[0].value) : '0%'}</p>
            </div>
            
            <div className="insight-card">
              <h3>🎨 偏好多样性</h3>
              <div className="diversity-score">
                {sortedData.filter(item => item.value > 0.1).length >= 3 ? (
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