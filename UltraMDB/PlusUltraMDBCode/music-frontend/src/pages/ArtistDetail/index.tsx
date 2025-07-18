import React from 'react';
import { useArtistDetail } from './useArtistDetail';
import ArtistHero from './ArtistHero';
import ArtistContent from './ArtistContent';
import ArtistSidebar from './ArtistSidebar';
import './ArtistDetail.css';

const ArtistDetail: React.FC = () => {
  const {
    artist,
    loading,
    error,
    artistSongs,
    songsLoading,
    showSongs,
    success,
    similarCreators,
    similarCreatorsLoading,
    canEdit,
    permissionLoading,
    songCount,
    bandCount,
    navigate,
    fetchArtistSongs,
    handleEdit,
    handleEditSong,
    handleDeleteSong,
    clearMessages
  } = useArtistDetail();

  if (loading || permissionLoading) {
    return (
      <div className="artist-detail-page">
        <div className="loading-card">
          <div className="loading-spinner"></div>
          <span>加载中...</span>
        </div>
      </div>
    );
  }

  if (error && !artist) {
    return (
      <div className="artist-detail-page">
        <div className="content-card" style={{ textAlign: 'center', margin: '40px auto', maxWidth: '600px' }}>
          <div className="empty-state-icon">❌</div>
          <h3>{error}</h3>
          <button className="btn btn-primary" onClick={() => navigate('/artists')}>
            返回艺术家列表
          </button>
        </div>
      </div>
    );
  }

  return (
    <div className="artist-detail-page">
      {/* 消息提示 */}
      {error && (
        <div className="message-toast">
          <div className="message-content message-error">
            <span>{error}</span>
            <button className="message-close" onClick={clearMessages}>×</button>
          </div>
        </div>
      )}
      
      {success && (
        <div className="message-toast">
          <div className="message-content message-success">
            <span>{success}</span>
            <button className="message-close" onClick={clearMessages}>×</button>
          </div>
        </div>
      )}

      {/* 英雄区域 */}
      <ArtistHero
        artist={artist}
        songCount={songCount}
        bandCount={bandCount}
        similarCreatorsCount={similarCreators.length}
        canEdit={canEdit}
        permissionLoading={permissionLoading}
        songsLoading={songsLoading}
        showSongs={showSongs}
        onEdit={handleEdit}
        onToggleSongs={fetchArtistSongs}
      />

      {/* 主内容区 */}
      <div className="artist-content">
        <div className="artist-content-grid">
          {/* 主要内容列 */}
          <ArtistContent
            artist={artist}
            showSongs={showSongs}
            artistSongs={artistSongs}
            onEditSong={handleEditSong}
            onDeleteSong={handleDeleteSong}
          />

          {/* 侧边栏 */}
          <ArtistSidebar
            songCount={songCount}
            bandCount={bandCount}
            similarCreators={similarCreators}
            similarCreatorsLoading={similarCreatorsLoading}
          />
        </div>
      </div>
    </div>
  );
};

export default ArtistDetail;
