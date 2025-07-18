import React from 'react';
import { Artist } from '../../types';
import './ArtistHero.css';

interface ArtistHeroProps {
  artist: Artist | null;
  songCount: number;
  bandCount: number;
  similarCreatorsCount: number;
  canEdit: boolean;
  permissionLoading: boolean;
  songsLoading: boolean;
  showSongs: boolean;
  onEdit: () => void;
  onToggleSongs: () => void;
}

const ArtistHero: React.FC<ArtistHeroProps> = ({
  artist,
  songCount,
  bandCount,
  similarCreatorsCount,
  canEdit,
  permissionLoading,
  songsLoading,
  showSongs,
  onEdit,
  onToggleSongs
}) => {
  const getArtistInitial = (name: string) => {
    return name ? name.charAt(0).toUpperCase() : 'A';
  };

  return (
    <div className="artist-hero">
      <div className="artist-hero-content">
        <div className="artist-header-info">
          <div className="artist-avatar-large">
            {getArtistInitial(artist?.name || '')}
          </div>
          
          <div className="artist-core-info">
            <h1 className="artist-name-large">{artist?.name}</h1>
            <div className="artist-id-badge">
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                <path d="M12 2L2 7v10c0 5.5 3.8 10.7 9 12 5.2-1.3 9-6.5 9-12V7l-10-5z"/>
              </svg>
              ID: {artist?.artistID}
            </div>
            
            <div className="artist-stats">
              <div className="artist-stat">
                <span className="artist-stat-value">{songCount}</span>
                <span className="artist-stat-label">首歌曲</span>
              </div>
              <div className="artist-stat">
                <span className="artist-stat-value">{bandCount}</span>
                <span className="artist-stat-label">个乐队</span>
              </div>
              <div className="artist-stat">
                <span className="artist-stat-value">{similarCreatorsCount}</span>
                <span className="artist-stat-label">相似艺术家</span>
              </div>
            </div>
          </div>
        </div>
        
        <div className="artist-hero-actions">
          {canEdit && !permissionLoading && (
            <button className="hero-btn hero-btn-primary" onClick={onEdit}>
              <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                <path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7"/>
                <path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z"/>
              </svg>
              编辑信息
            </button>
          )}
          
          <button 
            className="hero-btn hero-btn-secondary"
            onClick={onToggleSongs}
            disabled={songsLoading}
          >
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
              <circle cx="12" cy="12" r="10"/>
              <polygon points="10 8 16 12 10 16 10 8"/>
            </svg>
            {songsLoading ? '加载中...' : (showSongs ? '收起作品' : '查看作品')}
          </button>
        </div>
      </div>
    </div>
  );
};

export default ArtistHero;
