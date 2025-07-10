import React, { useState, useEffect } from 'react';
import { statisticsService } from '../../services/statistics.service';
import { musicService } from '../../services/music.service';
import { Song } from '../../types';
import SongList from '../../components/SongList';
import ModernSearchBox from '../../components/ModernSearchBox';
import { usePermissions } from '../../hooks/usePermissions';
import './MusicRecommendations.css';

interface RecommendationTab {
  id: string;
  label: string;
  icon: string;
  description: string;
}

const RECOMMENDATION_TABS: RecommendationTab[] = [
  {
    id: 'personal',
    label: '个性化推荐',
    icon: '🎯',
    description: '基于您的音乐偏好推荐'
  },
  {
    id: 'similar',
    label: '相似歌曲',
    icon: '🔄',
    description: '与指定歌曲相似的音乐'
  },
  {
    id: 'next',
    label: '下一首推荐',
    icon: '⏭️',
    description: '根据当前歌曲推荐下一首'
  }
];

const MusicRecommendations: React.FC = () => {
  const [activeTab, setActiveTab] = useState<string>('personal');
  const [songs, setSongs] = useState<Song[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');
  const [pageNumber, setPageNumber] = useState(1);
  const [hasMoreData, setHasMoreData] = useState(true);
  
  // 相似歌曲查询相关状态
  const [searchKeyword, setSearchKeyword] = useState('');
  const [selectedSongForSimilar, setSelectedSongForSimilar] = useState<Song | null>(null);
  const [selectedSongForNext, setSelectedSongForNext] = useState<Song | null>(null);
  const [searchSongs, setSearchSongs] = useState<Song[]>([]);
  const [searchLoading, setSearchLoading] = useState(false);

  const { isUser } = usePermissions();
  const pageSize = 20;

  // 加载个性化推荐
  const loadPersonalRecommendations = async (page: number = 1, append: boolean = false) => {
    setLoading(true);
    setError('');

    try {
      const [songIds, message] = await statisticsService.getUserSongRecommendations(page, pageSize);

      if (songIds && songIds.length > 0) {
        const songsData = await musicService.getSongsByIds(songIds);
        
        if (append) {
          setSongs(prev => [...prev, ...songsData]);
        } else {
          setSongs(songsData);
        }
        
        setHasMoreData(songsData.length === pageSize);
      } else {
        if (!append) {
          setSongs([]);
        }
        setHasMoreData(false);
        
        if (message && message !== 'Success') {
          setError(message);
        } else if (page === 1) {
          setError('暂无推荐歌曲，请先多听一些歌曲并进行评分以生成个性化推荐');
        }
      }
    } catch (err: any) {
      console.error('Failed to load personal recommendations:', err);
      setError(err.message || '获取个性化推荐失败');
      if (!append) {
        setSongs([]);
      }
      setHasMoreData(false);
    } finally {
      setLoading(false);
    }
  };

  // 搜索歌曲以供选择
  const searchSongsForSelection = async () => {
    if (!searchKeyword.trim()) {
      setSearchSongs([]);
      return;
    }

    setSearchLoading(true);
    try {
      const [songIds, message] = await musicService.searchSongs(searchKeyword);
      
      if (songIds && songIds.length > 0) {
        const songsData = await musicService.getSongsByIds(songIds);
        setSearchSongs(songsData);
      } else {
        setSearchSongs([]);
      }
    } catch (err: any) {
      console.error('Failed to search songs:', err);
      setSearchSongs([]);
    } finally {
      setSearchLoading(false);
    }
  };

  // 获取相似歌曲
  const loadSimilarSongs = async (songID: string, songName: string) => {
    setLoading(true);
    setError('');
    setSongs([]);

    try {
      const [songIds, message] = await statisticsService.getSimilarSongs(songID, 20);

      if (songIds && songIds.length > 0) {
        const songsData = await musicService.getSongsByIds(songIds);
        setSongs(songsData);
        setSuccess(`找到 ${songsData.length} 首与《${songName}》相似的歌曲`);
      } else {
        setSongs([]);
        setError(message || '未找到相似歌曲');
      }
    } catch (err: any) {
      console.error('Failed to load similar songs:', err);
      setError(err.message || '获取相似歌曲失败');
      setSongs([]);
    } finally {
      setLoading(false);
    }
  };

  // 获取下一首推荐
  const loadNextSongRecommendation = async (currentSongID: string, currentSongName: string) => {
    setLoading(true);
    setError('');
    setSongs([]);

    try {
      const [nextSongId, message] = await statisticsService.getNextSongRecommendation(currentSongID);

      if (nextSongId) {
        const [songData, songMessage] = await musicService.getSongById(nextSongId);
        if (songData) {
          setSongs([songData]);
          setSuccess(`基于《${currentSongName}》为您推荐的下一首歌曲：《${songData.name}》`);
        } else {
          setError(songMessage || '推荐的歌曲信息获取失败');
        }
      } else {
        setSongs([]);
        setError(message || '暂无下一首推荐');
      }
    } catch (err: any) {
      console.error('Failed to load next song recommendation:', err);
      setError(err.message || '获取下一首推荐失败');
      setSongs([]);
    } finally {
      setLoading(false);
    }
  };

  // 加载更多数据
  const loadMore = () => {
    if (activeTab === 'personal' && hasMoreData && !loading) {
      const nextPage = pageNumber + 1;
      setPageNumber(nextPage);
      loadPersonalRecommendations(nextPage, true);
    }
  };

  // 处理Tab切换
  const handleTabChange = (tabId: string) => {
    setActiveTab(tabId);
    setPageNumber(1);
    setHasMoreData(true);
    setSongs([]);
    setError('');
    setSuccess('');
    setSelectedSongForSimilar(null);
    setSelectedSongForNext(null);
    setSearchKeyword('');
    setSearchSongs([]);

    if (tabId === 'personal') {
      loadPersonalRecommendations(1);
    }
  };

  // 处理歌曲选择
  const handleSongSelect = (song: Song) => {
    if (activeTab === 'similar') {
      setSelectedSongForSimilar(song);
      setSearchSongs([]);
      setSearchKeyword('');
      loadSimilarSongs(song.songID, song.name);
    } else if (activeTab === 'next') {
      setSelectedSongForNext(song);
      setSearchSongs([]);
      setSearchKeyword('');
      loadNextSongRecommendation(song.songID, song.name);
    }
  };

  // 处理搜索
  const handleSearch = () => {
    searchSongsForSelection();
  };

  // 初始化加载
  useEffect(() => {
    if (isUser && activeTab === 'personal') {
      loadPersonalRecommendations(1);
    }
  }, [isUser]);

  const handleEdit = (song: Song) => {
    // 推荐页面通常不允许编辑，可以跳转到歌曲管理页面
    console.log('Edit song from recommendations:', song);
  };

  const handleDelete = (songID: string) => {
    // 推荐页面通常不允许删除
    console.log('Delete song from recommendations:', songID);
  };

  if (!isUser) {
    return (
      <div className="recommendations-container">
        <div className="permission-warning">
          <h2>访问受限</h2>
          <p>您需要登录并拥有用户权限才能查看音乐推荐。</p>
        </div>
      </div>
    );
  }

  return (
    <div className="recommendations-container">
      <div className="recommendations-header">
        <h1>🎵 音乐推荐</h1>
        <p className="recommendations-subtitle">
          发现您可能喜欢的音乐，探索更多精彩内容
        </p>
      </div>

      {/* 标签页导航 */}
      <div className="recommendations-tabs">
        {RECOMMENDATION_TABS.map((tab) => (
          <button
            key={tab.id}
            className={`tab-button ${activeTab === tab.id ? 'active' : ''}`}
            onClick={() => handleTabChange(tab.id)}
          >
            <span className="tab-icon">{tab.icon}</span>
            <div className="tab-content">
              <div className="tab-label">{tab.label}</div>
              <div className="tab-description">{tab.description}</div>
            </div>
          </button>
        ))}
      </div>

      {/* 搜索和选择区域 */}
      {(activeTab === 'similar' || activeTab === 'next') && (
        <div className="song-selection-section">
          <div className="selection-header">
            <h3>
              {activeTab === 'similar' ? '🔍 选择参考歌曲' : '🎵 选择当前播放歌曲'}
            </h3>
            <p>
              {activeTab === 'similar' 
                ? '搜索并选择一首歌曲，系统将为您推荐相似的音乐' 
                : '搜索并选择当前正在播放的歌曲，系统将推荐下一首歌曲'
              }
            </p>
          </div>

          <ModernSearchBox
            searchKeyword={searchKeyword}
            onSearchKeywordChange={setSearchKeyword}
            onSearch={handleSearch}
            loading={searchLoading}
            placeholder={activeTab === 'similar' ? '搜索参考歌曲...' : '搜索当前播放歌曲...'}
          />

          {/* 显示选中的歌曲 */}
          {(selectedSongForSimilar || selectedSongForNext) && (
            <div className="selected-song-display">
              <div className="selected-song-info">
                <span className="selected-label">
                  {activeTab === 'similar' ? '参考歌曲：' : '当前歌曲：'}
                </span>
                <span className="selected-song-name">
                  {selectedSongForSimilar?.name || selectedSongForNext?.name}
                </span>
                <button
                  className="clear-selection-btn"
                  onClick={() => {
                    setSelectedSongForSimilar(null);
                    setSelectedSongForNext(null);
                    setSongs([]);
                    setError('');
                    setSuccess('');
                  }}
                >
                  ✕
                </button>
              </div>
            </div>
          )}

          {/* 搜索结果 */}
          {searchSongs.length > 0 && (
            <div className="search-results">
              <h4>搜索结果：</h4>
              <div className="search-song-list">
                {searchSongs.map((song) => (
                  <div 
                    key={song.songID} 
                    className="search-song-item"
                    onClick={() => handleSongSelect(song)}
                  >
                    <div className="search-song-name">{song.name}</div>
                    <div className="search-song-meta">
                      发布时间：{new Date(song.releaseTime).toLocaleDateString('zh-CN')}
                    </div>
                  </div>
                ))}
              </div>
            </div>
          )}
        </div>
      )}

      {/* 消息显示 */}
      {error && (
        <div className="message-card error">
          <span className="message-icon">❌</span>
          <span>{error}</span>
        </div>
      )}

      {success && (
        <div className="message-card success">
          <span className="message-icon">✅</span>
          <span>{success}</span>
          <button 
            className="message-close"
            onClick={() => setSuccess('')}
          >
            ✕
          </button>
        </div>
      )}

      {/* 推荐结果 */}
      <div className="recommendations-content">
        {loading && songs.length === 0 ? (
          <div className="loading-state">
            <div className="loading-spinner"></div>
            <p>正在加载推荐内容...</p>
          </div>
        ) : (
          <>
            <SongList
              songs={songs}
              onEdit={handleEdit}
              onDelete={handleDelete}
            />

            {/* 加载更多按钮 */}
            {activeTab === 'personal' && hasMoreData && songs.length > 0 && (
              <div className="load-more-section">
                <button
                  className="load-more-btn"
                  onClick={loadMore}
                  disabled={loading}
                >
                  {loading ? (
                    <>
                      <div className="loading-spinner-small"></div>
                      <span>加载中...</span>
                    </>
                  ) : (
                    <>
                      <span>加载更多推荐</span>
                      <svg width="16" height="16" viewBox="0 0 24 24" fill="currentColor">
                        <path d="M12 15.5L6 9.5L7.5 8L12 12.5L16.5 8L18 9.5L12 15.5Z"/>
                      </svg>
                    </>
                  )}
                </button>
              </div>
            )}
          </>
        )}
      </div>
    </div>
  );
};

export default MusicRecommendations;