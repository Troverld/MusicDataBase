import React from 'react';
import { Band } from '../../types';
import { ArtistBandItem } from '../../hooks/useArtistBand';
import './BandHero.css';

interface BandHeroProps {
  band: Band | null;
  memberDetails: ArtistBandItem[];
  songCount: number;
  similarCreatorsCount: number;
  canEdit: boolean;
  permissionLoading: boolean;
  songsLoading: boolean;
  showSongs: boolean;
  onEdit: () => void;
  onToggleSongs: () => void;
  getMemberInitial: (name: string) => string;
}

const BandHero: React.FC<BandHeroProps> = ({
  band,
  memberDetails,
  songCount,
  similarCreatorsCount,
  canEdit,
  permissionLoading,
  songsLoading,
  showSongs,
  onEdit,
  onToggleSongs,
  getMemberInitial
}) => {
  return (
    <div className="band-hero">
      <div className="band-hero-content">
        <div className="band-header-info">
          <div className="band-avatar-large">
            🎸
          </div>
          
          <div className="band-core-info">
            <h1 className="band-name-large">{band?.name}</h1>
            <div className="band-id-badge">
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                <path d="M12 2L2 7v10c0 5.5 3.8 10.7 9 12 5.2-1.3 9-6.5 9-12V7l-10-5z"/>
              </svg>
              ID: {band?.bandID}
            </div>
            
            <div className="band-stats">
              <div className="band-stat">
                <span className="band-stat-value">{memberDetails.length}</span>
                <span className="band-stat-label">位成员</span>
              </div>
              <div className="band-stat">
                <span className="band-stat-value">{songCount}</span>
                <span className="band-stat-label">首歌曲</span>
              </div>
              <div className="band-stat">
                <span className="band-stat-value">{similarCreatorsCount}</span>
                <span className="band-stat-label">相似创作者</span>
              </div>
            </div>
            
            {/* 成员预览 */}
            {memberDetails.length > 0 && (
              <div className="band-members-preview">
                {memberDetails.slice(0, 8).map((member, index) => (
                  <div key={index} className="member-avatar-small" title={member.name}>
                    {getMemberInitial(member.name)}
                  </div>
                ))}
                {memberDetails.length > 8 && (
                  <div className="member-avatar-small" title={`还有 ${memberDetails.length - 8} 位成员`}>
                    +{memberDetails.length - 8}
                  </div>
                )}
              </div>
            )}
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

export default BandHero;
