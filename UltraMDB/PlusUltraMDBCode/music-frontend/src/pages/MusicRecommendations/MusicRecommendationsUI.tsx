import React from 'react';
import { Song } from '../../types';
import ModernSearchBox from '../../components/ModernSearchBox';
import { RecommendationTab } from './types';

interface PageHeaderProps {
  title: string;
  description: string;
}

export const PageHeader: React.FC<PageHeaderProps> = ({ title, description }) => (
  <div style={{ 
    textAlign: 'center', 
    animation: 'fadeIn 0.6s ease-out'
  }}>
    <h1 style={{ 
      fontSize: '36px', 
      fontWeight: '700',
      background: 'linear-gradient(135deg, #667eea 0%, #764ba2 100%)',
      WebkitBackgroundClip: 'text',
      WebkitTextFillColor: 'transparent',
      backgroundClip: 'text',
      marginBottom: '12px'
    }}>
      {title}
    </h1>
    <p style={{ 
      color: '#6b7280', 
      fontSize: '18px',
      maxWidth: '600px',
      margin: '0 auto',
      lineHeight: '1.6'
    }}>
      {description}
    </p>
  </div>
);

interface TabsProps {
  tabs: RecommendationTab[];
  activeTab: string;
  onTabChange: (tabId: string) => void;
}

export const Tabs: React.FC<TabsProps> = ({ tabs, activeTab, onTabChange }) => (
  <div className="recommendations-tabs-new">
    {tabs.map((tab) => (
      <button
        key={tab.id}
        className={`tab-btn ${activeTab === tab.id ? 'active' : ''}`}
        onClick={() => onTabChange(tab.id)}
      >
        <span className="tab-icon">{tab.icon}</span>
        <span className="tab-text">
          <span className="tab-title">{tab.label}</span>
          <span className="tab-desc">{tab.description}</span>
        </span>
      </button>
    ))}
  </div>
);

interface SearchSectionProps {
  activeTab: string;
  selectedSong: Song | null;
  searchKeyword: string;
  searchLoading: boolean;
  searchSongs: Song[];
  creatorNames: { [key: string]: string };
  onSearchKeywordChange: (value: string) => void;
  onSearch: () => void;
  onSongSelect: (song: Song) => void;
  onReset: () => void;
}

export const SearchSection: React.FC<SearchSectionProps> = ({
  activeTab,
  selectedSong,
  searchKeyword,
  searchLoading,
  searchSongs,
  creatorNames,
  onSearchKeywordChange,
  onSearch,
  onSongSelect,
  onReset
}) => (
  <div className="search-section-new">
    <h3>
      {activeTab === 'similar' ? '选择参考歌曲' : '选择当前播放'}
    </h3>
    
    {/* 已选择的歌曲展示 */}
    {selectedSong && (
      <div className="selected-song-card">
        <div className="selected-info">
          <h4>{selectedSong.name}</h4>
          <p>已选择</p>
        </div>
        <button className="change-btn" onClick={onReset}>
          更换
        </button>
      </div>
    )}

    {/* 搜索框 */}
    {!selectedSong && (
      <>
        <ModernSearchBox
          searchKeyword={searchKeyword}
          onSearchKeywordChange={onSearchKeywordChange}
          onSearch={onSearch}
          loading={searchLoading}
          placeholder="搜索歌曲名称..."
        />

        {/* 搜索结果 */}
        {searchSongs.length > 0 && (
          <div className="search-results-new">
            {searchSongs.slice(0, 5).map((song) => (
              <div
                key={song.songID}
                className="search-result-item"
                onClick={() => onSongSelect(song)}
              >
                <div className="result-info">
                  <h4>{song.name}</h4>
                  <p>
                    {song.creators && song.creators.length > 0
                      ? song.creators.map(c => {
                          const key = `${c.creatorType}-${c.id}`;
                          return creatorNames[key] || c.id;
                        }).join(', ')
                      : '未知创作者'}
                  </p>
                </div>
                <span className="select-icon">→</span>
              </div>
            ))}
          </div>
        )}
      </>
    )}
  </div>
);

interface MessageBarProps {
  message: string;
  type: 'error' | 'success';
}

export const MessageBar: React.FC<MessageBarProps> = ({ message, type }) => (
  <div className={`message-bar ${type}`}>
    <span>{message}</span>
  </div>
);

export const LoadingState: React.FC = () => (
  <div className="loading-state-new">
    <div className="loading-spinner"></div>
    <p>正在为你寻找好音乐...</p>
  </div>
);

interface LoadMoreButtonProps {
  loading: boolean;
  onClick: () => void;
}

export const LoadMoreButton: React.FC<LoadMoreButtonProps> = ({ loading, onClick }) => (
  <div className="load-more-container">
    <button
      className="load-more-btn-new"
      onClick={onClick}
      disabled={loading}
    >
      {loading ? '加载中...' : '加载更多'}
    </button>
  </div>
);

export const AccessDenied: React.FC = () => (
  <div className="recommendations-container">
    <div className="access-denied">
      <div className="access-icon">🔒</div>
      <h2>需要登录</h2>
      <p>登录后即可获得个性化音乐推荐</p>
    </div>
  </div>
);