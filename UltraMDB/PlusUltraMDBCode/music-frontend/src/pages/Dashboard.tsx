import React, { useState, useEffect } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { getUser } from '../utils/storage';
import { usePermissions } from '../hooks/usePermissions';
import { statisticsService } from '../services/statistics.service';
import { musicService } from '../services/music.service';
import { Song } from '../types';

const Dashboard: React.FC = () => {
  const user = getUser();
  const navigate = useNavigate();
  const { isUser, isAdmin, loading: permissionLoading } = usePermissions();
  const [userProfile, setUserProfile] = useState<any>(null);
  const [recommendedSongs, setRecommendedSongs] = useState<Song[]>([]);
  const [popularSongs, setPopularSongs] = useState<Song[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    if (user?.userID && (isUser || isAdmin)) {
      fetchDashboardData();
    } else {
      setLoading(false);
    }
  }, [user, isUser, isAdmin]);

  const fetchDashboardData = async () => {
    try {
      // 获取用户画像
      const [portrait, portraitMessage] = await statisticsService.getUserPortrait(user!.userID);
      if (portrait) {
        setUserProfile(portrait);
      }

      // 获取推荐歌曲（只获取前6首展示）
      const [recommendations, recMessage] = await statisticsService.getUserSongRecommendations(1, 6);
      if (recommendations) {
        // 获取歌曲详情
        const songDetails = await Promise.all(
          recommendations.map(async (songID) => {
            const [song, message] = await musicService.getSongByID(songID);
            return song;
          })
        );
        setRecommendedSongs(songDetails.filter(song => song !== null) as Song[]);
      }

      // 获取所有歌曲用于展示热门歌曲
      const [allSongs, allSongsMessage] = await musicService.getSongList(1, 100);
      if (allSongs) {
        // 获取每首歌的热度并排序
        const songsWithPopularity = await Promise.all(
          allSongs.slice(0, 20).map(async (song) => {
            const [popularity] = await statisticsService.getSongPopularity(song.songID);
            return { ...song, popularity: popularity || 0 };
          })
        );
        
        // 按热度排序并取前6首
        const sortedSongs = songsWithPopularity
          .sort((a, b) => b.popularity - a.popularity)
          .slice(0, 6);
        
        setPopularSongs(sortedSongs);
      }
    } catch (error) {
      console.error('Failed to fetch dashboard data:', error);
    } finally {
      setLoading(false);
    }
  };

  const getTopGenres = () => {
    if (!userProfile || !userProfile.vector) return [];
    return userProfile.vector
      .sort((a: any, b: any) => b.value - a.value)
      .slice(0, 3);
  };

  if (permissionLoading || loading) {
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
          <h1 className="welcome-title">欢迎回来，{user?.account}</h1>
          <p className="welcome-subtitle">
            {isAdmin ? '管理您的音乐王国' : '探索属于您的音乐世界'}
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
                {isAdmin ? '🛡️ 系统管理员' : '🎵 音乐爱好者'}
              </span>
            </div>
          </div>
          
          {userProfile && userProfile.vector && userProfile.vector.length > 0 && (
            <div className="user-stats">
              <div className="stat-item">
                <div className="stat-label">您的音乐偏好</div>
                <div className="genre-tags">
                  {getTopGenres().map((genre: any, index: number) => (
                    <span key={index} className="genre-tag">
                      {genre.GenreID}
                    </span>
                  ))}
                </div>
              </div>
            </div>
          )}
        </div>

        {/* 功能导航 */}
        <div className="quick-nav">
          <h2 className="section-title">功能导航</h2>
          <div className="nav-grid">
            <Link to="/songs" className="nav-card">
              <span className="nav-icon">🎵</span>
              <h3 className="nav-title">歌曲管理</h3>
              <p className="nav-description">浏览、搜索和管理音乐库</p>
            </Link>
            
            <Link to="/artists" className="nav-card">
              <span className="nav-icon">🎤</span>
              <h3 className="nav-title">艺术家</h3>
              <p className="nav-description">探索艺术家信息</p>
            </Link>
            
            <Link to="/bands" className="nav-card">
              <span className="nav-icon">🎸</span>
              <h3 className="nav-title">乐队</h3>
              <p className="nav-description">了解乐队详情</p>
            </Link>
            
            {(isUser || isAdmin) && (
              <>
                <Link to="/genres" className="nav-card">
                  <span className="nav-icon">🎼</span>
                  <h3 className="nav-title">曲风管理</h3>
                  <p className="nav-description">
                    {isAdmin ? '管理音乐分类' : '浏览曲风分类'}
                  </p>
                </Link>
                
                <Link to="/profile" className="nav-card special">
                  <span className="nav-icon">✨</span>
                  <h3 className="nav-title">音乐画像</h3>
                  <p className="nav-description">查看个性化分析</p>
                </Link>
                
                <Link to="/recommendations" className="nav-card special">
                  <span className="nav-icon">🎯</span>
                  <h3 className="nav-title">个性推荐</h3>
                  <p className="nav-description">发现新音乐</p>
                </Link>
              </>
            )}
          </div>
        </div>

        {/* 推荐歌曲 */}
        {recommendedSongs.length > 0 && (
          <div className="recommendation-section">
            <div className="section-header">
              <h2 className="section-title">为您推荐</h2>
              <Link to="/recommendations" className="see-more">查看更多 →</Link>
            </div>
            <div className="songs-grid">
              {recommendedSongs.map((song) => (
                <div key={song.songID} className="song-card">
                  <div className="song-info">
                    <h4 className="song-name">{song.name}</h4>
                    <p className="song-meta">
                      {song.genres.join(' · ')}
                    </p>
                  </div>
                  <button 
                    className="play-btn"
                    onClick={() => navigate('/songs')}
                  >
                    ▶
                  </button>
                </div>
              ))}
            </div>
          </div>
        )}

        {/* 热门歌曲 */}
        {popularSongs.length > 0 && (
          <div className="popular-section">
            <div className="section-header">
              <h2 className="section-title">热门歌曲</h2>
              <Link to="/songs" className="see-more">探索更多 →</Link>
            </div>
            <div className="songs-grid">
              {popularSongs.map((song: any) => (
                <div key={song.songID} className="song-card">
                  <div className="song-info">
                    <h4 className="song-name">{song.name}</h4>
                    <p className="song-meta">
                      热度: {song.popularity.toFixed(1)}
                    </p>
                  </div>
                  <button 
                    className="play-btn"
                    onClick={() => navigate('/songs')}
                  >
                    ▶
                  </button>
                </div>
              ))}
            </div>
          </div>
        )}

        {/* 如果没有认证，显示提示 */}
        {!isUser && !isAdmin && (
          <div className="auth-prompt">
            <h3>🔒 权限受限</h3>
            <p>登录后可以查看个性化推荐和音乐画像</p>
            <button onClick={() => navigate('/login')} className="auth-btn">
              立即登录
            </button>
          </div>
        )}
      </div>
    </div>
  );
};

export default Dashboard;