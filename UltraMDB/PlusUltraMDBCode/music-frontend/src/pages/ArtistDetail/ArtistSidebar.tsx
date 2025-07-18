import React from 'react';
import { Link } from 'react-router-dom';
import { ArtistBandItem } from '../../hooks/useArtistBand';
import './ArtistSidebar.css';

interface ArtistSidebarProps {
  songCount: number;
  bandCount: number;
  similarCreators: ArtistBandItem[];
  similarCreatorsLoading: boolean;
}

const ArtistSidebar: React.FC<ArtistSidebarProps> = ({
  songCount,
  bandCount,
  similarCreators,
  similarCreatorsLoading
}) => {
  return (
    <div className="artist-sidebar">
      {/* 快速信息 */}
      <div className="content-card">
        <div className="content-card-header">
          <h3 className="content-card-title" style={{ fontSize: '18px' }}>快速信息</h3>
        </div>
        
        <div className="quick-info-item">
          <div className="quick-info-icon">
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
              <path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"/>
              <circle cx="12" cy="7" r="4"/>
            </svg>
          </div>
          <div className="quick-info-content">
            <div className="quick-info-label">身份</div>
            <div className="quick-info-value">独立艺术家</div>
          </div>
        </div>
        
        <div className="quick-info-item">
          <div className="quick-info-icon">
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
              <path d="M9 18V5l12-2v13"/>
              <circle cx="6" cy="18" r="3"/>
              <circle cx="18" cy="16" r="3"/>
            </svg>
          </div>
          <div className="quick-info-content">
            <div className="quick-info-label">作品数量</div>
            <div className="quick-info-value">{songCount} 首歌曲</div>
          </div>
        </div>
        
        <div className="quick-info-item">
          <div className="quick-info-icon">
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
              <path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"/>
              <circle cx="9" cy="7" r="4"/>
              <path d="M23 21v-2a4 4 0 0 0-3-3.87"/>
              <path d="M16 3.13a4 4 0 0 1 0 7.75"/>
            </svg>
          </div>
          <div className="quick-info-content">
            <div className="quick-info-label">所属乐队</div>
            <div className="quick-info-value">{bandCount} 个</div>
          </div>
        </div>
      </div>

      {/* 相似艺术家 */}
      <div className="content-card">
        <div className="content-card-header">
          <h3 className="content-card-title" style={{ fontSize: '18px' }}>相似艺术家</h3>
          <Link to="/artists" className="content-card-action">
            查看全部
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
              <polyline points="9 18 15 12 9 6"/>
            </svg>
          </Link>
        </div>
        
        {similarCreatorsLoading ? (
          <div className="loading-card" style={{ padding: '24px' }}>
            <div className="loading-spinner"></div>
          </div>
        ) : similarCreators.length > 0 ? (
          <div className="similar-creators-grid">
            {similarCreators.map((creator) => (
              <Link
                key={`${creator.type}-${creator.id}`}
                to={creator.type === 'artist' ? `/artists/${creator.id}` : `/bands/${creator.id}`}
                className="similar-creator-card"
              >
                <div className="similar-creator-avatar">
                  {creator.type === 'artist' ? '🎤' : '🎸'}
                </div>
                <div className="similar-creator-info">
                  <div className="similar-creator-name">{creator.name}</div>
                  <div className="similar-creator-type">
                    {creator.type === 'artist' ? '艺术家' : '乐队'}
                  </div>
                </div>
              </Link>
            ))}
          </div>
        ) : (
          <div className="empty-state-card" style={{ padding: '24px' }}>
            <div className="empty-state-text">暂无相似艺术家</div>
          </div>
        )}
      </div>
    </div>
  );
};

export default ArtistSidebar;
