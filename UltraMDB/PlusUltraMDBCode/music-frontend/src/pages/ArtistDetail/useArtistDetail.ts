import { useState, useEffect } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { artistService } from '../../services/artist.service';
import { musicService } from '../../services/music.service';
import { statisticsService } from '../../services/statistics.service';
import { Artist, Song, CreatorID_Type } from '../../types';
import { useArtistPermission, usePermissions } from '../../hooks/usePermissions';
import { useArtistBand, ArtistBandItem } from '../../hooks/useArtistBand';

export const useArtistDetail = () => {
  const { artistID } = useParams<{ artistID: string }>();
  const navigate = useNavigate();
  const [artist, setArtist] = useState<Artist | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [artistSongs, setArtistSongs] = useState<Song[]>([]);
  const [songsLoading, setSongsLoading] = useState(false);
  const [showSongs, setShowSongs] = useState(false);
  const [success, setSuccess] = useState('');
  
  // 相似创作者相关状态
  const [similarCreators, setSimilarCreators] = useState<ArtistBandItem[]>([]);
  const [similarCreatorsLoading, setSimilarCreatorsLoading] = useState(false);

  // 检查编辑权限
  const { canEdit, loading: permissionLoading } = useArtistPermission(artistID || '');
  const { isAdmin } = usePermissions();
  const { getArtistBandsByIds } = useArtistBand();

  // 统计数据
  const [songCount, setSongCount] = useState(0);
  const [bandCount, setBandCount] = useState(0);

  useEffect(() => {
    const fetchArtist = async () => {
      if (!artistID) {
        setError('艺术家ID无效');
        setLoading(false);
        return;
      }

      try {
        setLoading(true);
        const [artistData, message] = await artistService.getArtistById(artistID);
        
        if (artistData) {
          setArtist(artistData);
          setError('');
          
          // 获取歌曲数量
          const [songIds] = await musicService.filterSongsByEntity({id: artistID, type: 'artist'});
          if (songIds) {
            setSongCount(songIds.length);
          }
          
          // 获取所属乐队数量
          const [bandIds] = await artistService.searchAllBelongingBands(artistID);
          if (bandIds) {
            setBandCount(bandIds.length);
          }
        } else {
          setError(message || '未找到艺术家信息');
        }
      } catch (err: any) {
        setError(err.message || '获取艺术家信息失败');
      } finally {
        setLoading(false);
      }
    };

    fetchArtist();
  }, [artistID]);

  useEffect(() => {
    // 当 artistID 改变时，清空歌曲相关状态
    setArtistSongs([]);
    setShowSongs(false);
    setSimilarCreators([]);
  }, [artistID]);

  // 获取艺术家的歌曲
  const fetchArtistSongs = async () => {
    if (!artistID) return;

    if (showSongs) {
      setShowSongs(false);
      return;
    }
    
    if (artistSongs.length > 0) {
      setShowSongs(true);
      return;
    }

    setSongsLoading(true);
    try {
      const [songIds, message] = await musicService.filterSongsByEntity({id: artistID, type: 'artist'});

      if (songIds && songIds.length > 0) {
        const songsData = await musicService.getSongsByIds(songIds);
        setArtistSongs(songsData);
        setShowSongs(true);
      } else {
        setArtistSongs([]);
        setShowSongs(true);
        if (message && message !== 'Success') {
          setError(message);
        }
      }
    } catch (err: any) {
      console.error('Failed to fetch artist songs:', err);
      setError('获取歌曲列表失败');
      setArtistSongs([]);
    } finally {
      setSongsLoading(false);
    }
  };

  // 获取相似创作者
  const fetchSimilarCreators = async () => {
    if (!artistID) return;

    setSimilarCreatorsLoading(true);
    try {
      const [creatorIds, message] = await statisticsService.getSimilarCreators(artistID, 'artist', 6);

      if (creatorIds && creatorIds.length > 0) {
        // 转换 CreatorID_Type[] 为 getArtistBandsByIds 需要的格式
        const creatorRequests = creatorIds.map((creator: CreatorID_Type) => ({
          id: creator.id,
          type: creator.creatorType as 'artist' | 'band'
        }));
        
        const creators = await getArtistBandsByIds(creatorRequests);
        setSimilarCreators(creators.slice(0, 6)); // 限制显示6个
      } else {
        setSimilarCreators([]);
      }
    } catch (err: any) {
      console.error('Failed to fetch similar creators:', err);
      setSimilarCreators([]);
    } finally {
      setSimilarCreatorsLoading(false);
    }
  };

  useEffect(() => {
    if (artist) {
      fetchSimilarCreators();
    }
  }, [artist]);

  const handleEdit = () => {
    if (artist) {
      navigate('/artists', { 
        state: { 
          showModal: true, 
          editArtist: artist 
        } 
      });
    }
  };

  const handleEditSong = (song: Song) => {
    navigate('/songs', { 
      state: { 
        editSong: song,
        returnTo: 'artist',
        returnId: artistID 
      } 
    });
  };

  const handleDeleteSong = async (songID: string) => {
    const songToDelete = artistSongs.find(song => song.songID === songID);
    if (!songToDelete) {
      setError('未找到要删除的歌曲');
      return;
    }

    const confirmMessage = `确定要删除歌曲《${songToDelete.name}》吗？此操作不可撤销。`;
    if (!window.confirm(confirmMessage)) {
      return;
    }

    try {
      setError('');
      setSuccess('');
      
      const [success, message] = await musicService.deleteSong(songID);
      
      if (success) {
        setSuccess(`歌曲《${songToDelete.name}》删除成功`);
        // 从列表中移除已删除的歌曲
        setArtistSongs(prevSongs => prevSongs.filter(song => song.songID !== songID));
        setSongCount(prev => prev - 1);
      } else {
        setError(message || '删除歌曲失败');
      }
    } catch (err: any) {
      setError(err.message || '删除歌曲失败');
    }
  };

  const clearMessages = () => {
    setError('');
    setSuccess('');
  };

  return {
    // 状态
    artistID,
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
    isAdmin,
    songCount,
    bandCount,
    
    // 方法
    navigate,
    fetchArtistSongs,
    handleEdit,
    handleEditSong,
    handleDeleteSong,
    clearMessages
  };
};
