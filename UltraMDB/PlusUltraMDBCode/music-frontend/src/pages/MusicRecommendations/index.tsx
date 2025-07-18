import React from 'react';
import SongList from '../../components/SongList';
import { RECOMMENDATION_TABS } from './types';
import { useMusicRecommendations } from './useMusicRecommendations';
import {
  PageHeader,
  Tabs,
  SearchSection,
  MessageBar,
  LoadingState,
  LoadMoreButton,
  AccessDenied
} from './MusicRecommendationsUI';
import './MusicRecommendations.css';

const MusicRecommendations: React.FC = () => {
  const {
    // 状态
    activeTab,
    songs,
    loading,
    error,
    success,
    hasMoreData,
    searchKeyword,
    selectedSongForSimilar,
    selectedSongForNext,
    searchSongs,
    searchLoading,
    creatorNames,
    isUser,
    
    // 方法
    setSearchKeyword,
    handleTabChange,
    handleSongSelect,
    handleSearch,
    handleEdit,
    handleDelete,
    loadMore,
    resetSelectedSong
  } = useMusicRecommendations();

  // 如果用户未登录，显示访问限制页面
  if (!isUser) {
    return <AccessDenied />;
  }

  return (
    <div style={{ 
      background: '#f8f9fa', 
      minHeight: 'calc(100vh - 70px)', 
      padding: '40px 20px' 
    }}>
      <div style={{ maxWidth: '1200px', margin: '0 auto' }}>
        {/* 页面标题和描述 */}
        <PageHeader 
          title="音乐推荐"
          description="管理系统中的音乐推荐，基于您的品味发现新音乐"
        />
      </div>

      {/* 极简标签页 */}
      <Tabs 
        tabs={RECOMMENDATION_TABS}
        activeTab={activeTab}
        onTabChange={handleTabChange}
      />

      {/* 搜索区域 - 更简洁的设计 */}
      {(activeTab === 'similar' || activeTab === 'next') && (
        <SearchSection
          activeTab={activeTab}
          selectedSong={activeTab === 'similar' ? selectedSongForSimilar : selectedSongForNext}
          searchKeyword={searchKeyword}
          searchLoading={searchLoading}
          searchSongs={searchSongs}
          creatorNames={creatorNames}
          onSearchKeywordChange={setSearchKeyword}
          onSearch={handleSearch}
          onSongSelect={handleSongSelect}
          onReset={resetSelectedSong}
        />
      )}

      {/* 消息提示 */}
      {error && <MessageBar message={error} type="error" />}
      {success && <MessageBar message={success} type="success" />}

      {/* 内容区域 */}
      <div className="recommendations-content">
        {loading && songs.length === 0 ? (
          <LoadingState />
        ) : (
          <>
            <SongList
              songs={songs}
              onEdit={handleEdit}
              onDelete={handleDelete}
            />
            
            {/* 加载更多按钮 - 仅个性化推荐显示 */}
            {activeTab === 'personal' && songs.length > 0 && hasMoreData && (
              <LoadMoreButton loading={loading} onClick={loadMore} />
            )}
          </>
        )}
      </div>
    </div>
  );
};

export default MusicRecommendations;