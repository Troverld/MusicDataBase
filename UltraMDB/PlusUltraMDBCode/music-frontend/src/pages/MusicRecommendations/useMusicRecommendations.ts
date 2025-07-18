import { useState, useEffect, useCallback } from 'react';
import { useNavigate } from 'react-router-dom';
import { statisticsService } from '../../services/statistics.service';
import { musicService } from '../../services/music.service';
import { artistService } from '../../services/artist.service';
import { bandService } from '../../services/band.service';
import { Song } from '../../types';
import { usePermissions } from '../../hooks/usePermissions';

export const useMusicRecommendations = () => {
  const [activeTab, setActiveTab] = useState<string>('personal');
  const [songs, setSongs] = useState<Song[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');
  const [pageNumber, setPageNumber] = useState(1);
  const [hasMoreData, setHasMoreData] = useState(true);
  const navigate = useNavigate();
  
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

  // 处理编辑
  const handleEdit = (song: Song) => {
    navigate('/songs', { 
      state: { 
        showModal: true, 
        editSong: song
      } 
    });
  };

  // 处理删除
  const handleDelete = (songID: string) => {
    setLoading(true);
    setError('');
    setSuccess('');

    musicService.deleteSong(songID)
      .then(() => {
        setSongs(prev => prev.filter(s => s.songID !== songID));
        setSuccess('歌曲删除成功');
      })
      .catch(err => {
        console.error('删除歌曲失败:', err);
        setError(err.message || '删除歌曲失败');
      })
      .finally(() => {
        setLoading(false);
      });
  };

  // 重置选择的歌曲
  const resetSelectedSong = () => {
    setSelectedSongForSimilar(null);
    setSelectedSongForNext(null);
    setSongs([]);
    setError('');
    setSuccess('');
  };

  // 初始化加载
  useEffect(() => {
    if (isUser && activeTab === 'personal') {
      loadPersonalRecommendations(1);
    }
  }, [isUser]);

  return {
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
  };
};