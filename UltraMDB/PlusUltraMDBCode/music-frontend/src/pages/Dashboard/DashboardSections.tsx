import React from 'react';
import { Link } from 'react-router-dom';
import { Song, User, Dim } from '../../types';
import PlayButton from '../../components/PlayButton';
import { SongWithPopularity } from './useDashboard';

// 加载状态组件
export const DashboardLoading: React.FC = () => (
  <div className="dashboard-modern">
    <div className="dashboard-loading">
      <div className="loading-pulse"></div>
      <p>正在准备您的音乐世界...</p>
    </div>
  </div>
);

// 头部组件
interface DashboardHeaderProps {
  user: User | null;
  isAdmin: boolean;
}

export const DashboardHeader: React.FC<DashboardHeaderProps> = ({ user, isAdmin }) => (
  <header className="dashboard-header">
    <div className="header-content">
      <div className="greeting">
        <h1>Hi, {user?.account} 👋</h1>
        <p className="subtitle">
          {new Date().getHours() < 12 ? '早上好' : 
           new Date().getHours() < 18 ? '下午好' : '晚上好'}
          ，今天想听什么音乐？
        </p>
      </div>
      
      {/* 快捷操作 */}
      <div className="quick-actions">
        <Link to="/songs" className="quick-action-btn">
          <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
            <path d="M9 18V5l12-2v13"/>
            <circle cx="6" cy="18" r="3"/>
            <circle cx="18" cy="16" r="3"/>
          </svg>
          <span>浏览歌曲</span>
        </Link>
        <Link to="/profile" className="quick-action-btn">
          <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
            <path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"/>
            <circle cx="12" cy="7" r="4"/>
          </svg>
          <span>我的画像</span>
        </Link>
        {isAdmin && (
          <Link to="/genres" className="quick-action-btn admin">
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
              <circle cx="12" cy="12" r="3"/>
              <path d="M12 1v6m0 6v6m6-12h6M6 12H0m16.24-4.24l-4.24 4.24m-8 0L3.76 7.76m12.48 8.48l4.24 4.24m-16.72 0L7.76 16.24"/>
            </svg>
            <span>管理曲风</span>
          </Link>
        )}
      </div>
    </div>
  </header>
);

// 音乐品味卡片组件
interface TasteSectionProps {
  topGenres: (Dim & { name: string })[];
}

export const TasteSection: React.FC<TasteSectionProps> = ({ topGenres }) => (
  <section className="taste-section">
    <div className="section-header">
      <h2>你的音乐品味</h2>
      <Link to="/profile" className="view-more">查看完整画像 →</Link>
    </div>
    <div className="taste-cards">
      {topGenres.map((genre, index) => (
        <div key={genre.GenreID} className="taste-card">
          <div className="taste-rank">#{index + 1}</div>
          <div className="taste-name">{genre.name}</div>
          <div className="taste-score">
            <div className="score-bar">
              <div 
                className="score-fill" 
                style={{ 
                  width: `${genre.value * 100}%`,
                  backgroundColor: ['#6366f1', '#8b5cf6', '#ec4899'][index]
                }}
              />
            </div>
            <span className="score-text">{(genre.value * 100).toFixed(0)}%</span>
          </div>
        </div>
      ))}
    </div>
  </section>
);

// 推荐歌曲组件
interface RecommendationsSectionProps {
  songs: Song[];
  formatCreators: (song: Song) => string;
  formatGenres: (genreIds: string[]) => string;
}

export const RecommendationsSection: React.FC<RecommendationsSectionProps> = ({ 
  songs, 
  formatCreators, 
  formatGenres 
}) => (
  <section className="recommendations-section">
    <div className="section-header">
      <h2>为你推荐</h2>
      <Link to="/recommendations" className="view-more">更多推荐 →</Link>
    </div>
    <div className="songs-grid">
      {songs.map((song) => (
        <article key={song.songID} className="song-card-minimal">
          <div className="song-card-content">
            <div className="song-main-info">
              <h3 className="song-title">{song.name}</h3>
              <p className="song-artist">{formatCreators(song)}</p>
              <div className="song-tags">
                {formatGenres(song.genres).split(' · ').map((genre, idx) => (
                  <span key={idx} className="genre-tag">{genre}</span>
                ))}
              </div>
            </div>
            <div className="song-play-action">
              <PlayButton
                songID={song.songID}
                songName={song.name}
                size="medium"
                onPlayStart={() => console.log(`播放: ${song.name}`)}
                onPlayError={(error) => console.error(`播放失败: ${error}`)}
              />
            </div>
          </div>
        </article>
      ))}
    </div>
  </section>
);

// 热门歌曲组件
interface PopularSectionProps {
  songs: SongWithPopularity[];
  formatCreators: (song: Song) => string;
}

export const PopularSection: React.FC<PopularSectionProps> = ({ songs, formatCreators }) => (
  <section className="popular-section">
    <div className="section-header">
      <h2>当前热门</h2>
      <span className="section-subtitle">大家都在听</span>
    </div>
    <div className="popular-list">
      {songs.map((song, index) => (
        <div key={song.songID} className="popular-item">
          <div className="popular-rank">{index + 1}</div>
          <div className="popular-main">
            <div className="popular-info">
              <h4>{song.name}</h4>
              <p>{formatCreators(song)}</p>
            </div>
            <div className="popular-stats">
              <span className="popularity-badge">
                🔥 {song.popularity.toFixed(0)}
              </span>
            </div>
          </div>
          <PlayButton
            songID={song.songID}
            songName={song.name}
            size="small"
            onPlayStart={() => console.log(`播放: ${song.name}`)}
            onPlayError={(error) => console.error(`播放失败: ${error}`)}
          />
        </div>
      ))}
    </div>
  </section>
);

// 未登录提示组件
interface EmptyStateProps {
  onLogin: () => void;
}

export const EmptyState: React.FC<EmptyStateProps> = ({ onLogin }) => (
  <div className="empty-state-modern">
    <svg width="80" height="80" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5">
      <path d="M9 11V6a3 3 0 1 1 6 0v5m-6 8h6a2 2 0 0 0 2-2v-5a2 2 0 0 0-2-2H9a2 2 0 0 0-2 2v5a2 2 0 0 0 2 2z"/>
    </svg>
    <h3>解锁完整体验</h3>
    <p>登录后获得个性化推荐和音乐品味分析</p>
    <button onClick={onLogin} className="login-btn-modern">
      立即登录
    </button>
  </div>
);
