import React from 'react';
import { useBandDetail } from './useBandDetail';
import BandHero from './BandHero';
import BandContent from './BandContent';
import BandSidebar from './BandSidebar';
import './BandDetail.css';

const BandDetail: React.FC = () => {
  const {
    band,
    loading,
    error,
    memberDetails,
    bandSongs,
    songsLoading,
    showSongs,
    success,
    similarCreators,
    similarCreatorsLoading,
    canEdit,
    permissionLoading,
    songCount,
    navigate,
    fetchBandSongs,
    handleEdit,
    handleEditSong,
    handleDeleteSong,
    clearMessages,
    getBandInitial,
    getMemberInitial
  } = useBandDetail();

  if (loading || permissionLoading) {
    return (
      <div className="band-detail-page">
        <div className="loading-card">
          <div className="loading-spinner"></div>
          <span>加载中...</span>
        </div>
      </div>
    );
  }

  if (error && !band) {
    return (
      <div className="band-detail-page">
        <div className="content-card" style={{ textAlign: 'center', margin: '40px auto', maxWidth: '600px' }}>
          <div className="empty-state-icon">❌</div>
          <h3>{error}</h3>
          <button className="btn btn-primary" onClick={() => navigate('/bands')}>
            返回乐队列表
          </button>
        </div>
      </div>
    );
  }

  return (
    <div className="band-detail-page">
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
      <BandHero
        band={band}
        memberDetails={memberDetails}
        songCount={songCount}
        similarCreatorsCount={similarCreators.length}
        canEdit={canEdit}
        permissionLoading={permissionLoading}
        songsLoading={songsLoading}
        showSongs={showSongs}
        onEdit={handleEdit}
        onToggleSongs={fetchBandSongs}
        getMemberInitial={getMemberInitial}
      />

      {/* 主内容区 */}
      <div className="band-content">
        <div className="band-content-grid">
          {/* 主要内容列 */}
          <BandContent
            band={band}
            memberDetails={memberDetails}
            showSongs={showSongs}
            bandSongs={bandSongs}
            onEditSong={handleEditSong}
            onDeleteSong={handleDeleteSong}
            getMemberInitial={getMemberInitial}
          />

          {/* 侧边栏 */}
          <BandSidebar
            songCount={songCount}
            memberCount={memberDetails.length}
            similarCreators={similarCreators}
            similarCreatorsLoading={similarCreatorsLoading}
          />
        </div>
      </div>
    </div>
  );
};

export default BandDetail;
