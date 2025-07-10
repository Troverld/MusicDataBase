import React, { useState, useEffect } from 'react';
import { statisticsService } from '../../services/statistics.service';
import { musicService } from '../../services/music.service';
import { artistService } from '../../services/artist.service';
import { bandService } from '../../services/band.service';
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
    label: '为你推荐',
    icon: '✨',
    description: '基于你的听歌习惯'
  },
  {
    id: 'similar',
    label: '相似歌曲',
    icon: '🎵',
    description: '找到风格相近的音乐'
  },
  {
    id: 'next',
    label: '播放建议',
    icon: '▶️',
    description: '接下来听什么'
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
  const [creatorNames, setCreatorNames] = useState<{ [key: string]: string }>({});

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
          setError('还没有足够的数据生成推荐，多听几首歌吧！');
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
        
        // 获取所有创作者的名称
        const namesMap: { [key: string]: string } = {};
        for (const song of songsData) {
          if (song.creators) {
            for (const creator of song.creators) {
              const key = `${creator.creatorType}-${creator.id}`;
              if (!namesMap[key]) {
                try {
                  if (creator.creatorType === 'artist') {
                    const [artist] = await artistService.getArtistById(creator.id);
                    if (artist) {
                      namesMap[key] = artist.name;
                    }
                  } else if (creator.creatorType === 'band') {
                    const [band] = await bandService.getBandById(creator.id);
                    if (band) {
                      namesMap[key] = band.name;
                    }
                  }
                } catch (err) {
                  console.warn(`Failed to fetch name for ${creator.id}`);
                }
              }
            }
          }
        }
        setCreatorNames(namesMap);
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
          setSuccess(`基于《${currentSongName}》推荐：《${songData.name}》`);
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
    console.log('Edit song from recommendations:', song);
  };

  const handleDelete = (songID: string) => {
    console.log('Delete song from recommendations:', songID);
  };

  if (!isUser) {
    return (
      <div className="recommendations-container">
        <div className="access-denied">
          <div className="access-icon">🔒</div>
          <h2>需要登录</h2>
          <p>登录后即可获得个性化音乐推荐</p>
        </div>
      </div>
    );
  }

  return (
    <div style={{ 
      background: '#f8f9fa', 
      minHeight: 'calc(100vh - 70px)', 
      padding: '40px 20px' 
    }}>
      <div style={{ maxWidth: '1200px', margin: '0 auto' }}>
      {/* 页面标题和描述 */}
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
          音乐推荐
        </h1>
        <p style={{ 
          color: '#6b7280', 
          fontSize: '18px',
          maxWidth: '600px',
          margin: '0 auto',
          lineHeight: '1.6'
        }}>
          管理系统中的音乐推荐，基于您的品味发现新音乐
        </p>
      </div>
      </div>

      {/* 极简标签页 */}
      <div className="recommendations-tabs-new">
        {RECOMMENDATION_TABS.map((tab) => (
          <button
            key={tab.id}
            className={`tab-btn ${activeTab === tab.id ? 'active' : ''}`}
            onClick={() => handleTabChange(tab.id)}
          >
            <span className="tab-icon">{tab.icon}</span>
            <span className="tab-text">
              <span className="tab-title">{tab.label}</span>
              <span className="tab-desc">{tab.description}</span>
            </span>
          </button>
        ))}
      </div>

      {/* 搜索区域 - 更简洁的设计 */}
      {(activeTab === 'similar' || activeTab === 'next') && (
        <div className="search-section-new">
          <h3>
            {activeTab === 'similar' ? '选择参考歌曲' : '选择当前播放'}
          </h3>
          
          {/* 已选择的歌曲展示 */}
          {(activeTab === 'similar' ? selectedSongForSimilar : selectedSongForNext) && (
            <div className="selected-song-card">
              <div className="selected-info">
                <h4>{activeTab === 'similar' ? selectedSongForSimilar?.name : selectedSongForNext?.name}</h4>
                <p>已选择</p>
              </div>
              <button 
                className="change-btn"
                onClick={() => {
                  setSelectedSongForSimilar(null);
                  setSelectedSongForNext(null);
                  setSongs([]);
                  setError('');
                  setSuccess('');
                }}
              >
                更换
              </button>
            </div>
          )}

          {/* 搜索框 */}
          {!selectedSongForSimilar && !selectedSongForNext && (
            <>
              <ModernSearchBox
                searchKeyword={searchKeyword}
                onSearchKeywordChange={setSearchKeyword}
                onSearch={handleSearch}
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
                      onClick={() => handleSongSelect(song)}
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
      )}

      {/* 消息提示 */}
      {error && (
        <div className="message-bar error">
          <span>{error}</span>
        </div>
      )}
      
      {success && (
        <div className="message-bar success">
          <span>{success}</span>
        </div>
      )}

      {/* 内容区域 */}
      <div className="recommendations-content">
        {loading && songs.length === 0 ? (
          <div className="loading-state-new">
            <div className="loading-spinner"></div>
            <p>正在为你寻找好音乐...</p>
          </div>
        ) : (
          <>
            <SongList
              songs={songs}
              onEdit={handleEdit}
              onDelete={handleDelete}
            />

            {/* 加载更多 - 更简洁的样式 */}
            {activeTab === 'personal' && hasMoreData && songs.length > 0 && (
              <div className="load-more-container">
                <button
                  className="load-more-btn-new"
                  onClick={loadMore}
                  disabled={loading}
                >
                  {loading ? '加载中...' : '加载更多'}
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