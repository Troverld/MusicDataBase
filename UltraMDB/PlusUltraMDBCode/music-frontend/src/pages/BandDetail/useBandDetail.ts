import { useState, useEffect } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { bandService } from '../../services/band.service';
import { musicService } from '../../services/music.service';
import { statisticsService } from '../../services/statistics.service';
import { Band, Song, CreatorID_Type } from '../../types';
import { useBandPermission, usePermissions } from '../../hooks/usePermissions';
import { useArtistBand, ArtistBandItem } from '../../hooks/useArtistBand';

export const useBandDetail = () => {
  const { bandID } = useParams<{ bandID: string }>();
  const navigate = useNavigate();
  const [band, setBand] = useState<Band | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [memberDetails, setMemberDetails] = useState<ArtistBandItem[]>([]);
  const [bandSongs, setBandSongs] = useState<Song[]>([]);
  const [songsLoading, setSongsLoading] = useState(false);
  const [showSongs, setShowSongs] = useState(false);
  const [success, setSuccess] = useState('');
  
  // 相似创作者相关状态
  const [similarCreators, setSimilarCreators] = useState<ArtistBandItem[]>([]);
  const [similarCreatorsLoading, setSimilarCreatorsLoading] = useState(false);

  // 检查编辑权限
  const { canEdit, loading: permissionLoading } = useBandPermission(bandID || '');
  const { isAdmin } = usePermissions();
  const { convertIdsToArtistBandItems, getArtistBandsByIds } = useArtistBand();

  // 统计数据
  const [songCount, setSongCount] = useState(0);

  useEffect(() => {
    const fetchBand = async () => {
      if (!bandID) {
        setError('乐队ID无效');
        setLoading(false);
        return;
      }

      try {
        setLoading(true);
        const [bandData, message] = await bandService.getBandById(bandID);
        
        if (bandData) {
          setBand(bandData);
          setError('');
          
          // 获取成员详细信息
          try {
            const memberItems = await convertIdsToArtistBandItems(bandData.members || []);
            setMemberDetails(memberItems);
          } catch (error) {
            console.error('Failed to load member details:', error);
            const basicMembers: ArtistBandItem[] = (bandData.members || []).map(memberId => ({
              id: memberId,
              name: memberId,
              bio: '无法获取详细信息',
              type: 'artist'
            }));
            setMemberDetails(basicMembers);
          }
          
          // 获取歌曲数量
          const [songIds] = await musicService.filterSongsByEntity({id: bandID, type: 'band'});
          if (songIds) {
            setSongCount(songIds.length);
          }
        } else {
          setError(message || '未找到乐队信息');
        }
      } catch (err: any) {
        setError(err.message || '获取乐队信息失败');
      } finally {
        setLoading(false);
      }
    };

    fetchBand();
  }, [bandID, convertIdsToArtistBandItems]);

  useEffect(() => {
    // 当 bandID 改变时，清空歌曲相关状态
    setBandSongs([]);
    setShowSongs(false);
    setSimilarCreators([]);
  }, [bandID]);

  // 获取乐队的歌曲
  const fetchBandSongs = async () => {
    if (!bandID) return;

    if (showSongs) {
      setShowSongs(false);
      return;
    }
    
    if (bandSongs.length > 0) {
      setShowSongs(true);
      return;
    }

    setSongsLoading(true);
    try {
      const [songIds, message] = await musicService.filterSongsByEntity({id: bandID, type: 'band'});

      if (songIds && songIds.length > 0) {
        const songsData = await musicService.getSongsByIds(songIds);
        setBandSongs(songsData);
        setShowSongs(true);
      } else {
        setBandSongs([]);
        setShowSongs(true);
        if (message && message !== 'Success') {
          setError(message);
        }
      }
    } catch (err: any) {
      console.error('Failed to fetch band songs:', err);
      setError('获取歌曲列表失败');
      setBandSongs([]);
    } finally {
      setSongsLoading(false);
    }
  };

  // 获取相似创作者
  const fetchSimilarCreators = async () => {
    if (!bandID) return;

    setSimilarCreatorsLoading(true);
    try {
      const [creatorIds, message] = await statisticsService.getSimilarCreators(bandID, 'band', 6);

      if (creatorIds && creatorIds.length > 0) {
        // CreatorID_Type[] 已经是正确的格式
        const creatorRequests = creatorIds.map((creator) => ({
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
    if (band) {
      fetchSimilarCreators();
    }
  }, [band]);

  const handleEdit = () => {
    if (band) {
      navigate('/bands', { 
        state: { 
          showModal: true, 
          editBand: band 
        } 
      });
    }
  };

  const handleEditSong = (song: Song) => {
    navigate('/songs', { 
      state: { 
        editSong: song,
        returnTo: 'band',
        returnId: bandID 
      } 
    });
  };

  const handleDeleteSong = async (songID: string) => {
    const songToDelete = bandSongs.find(song => song.songID === songID);
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
        setBandSongs(prevSongs => prevSongs.filter(song => song.songID !== songID));
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

  const getBandInitial = (name: string) => {
    return name ? name.charAt(0).toUpperCase() : 'B';
  };

  const getMemberInitial = (name: string) => {
    return name ? name.charAt(0).toUpperCase() : '?';
  };

  return {
    // 状态
    bandID,
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
    isAdmin,
    songCount,
    
    // 方法
    navigate,
    fetchBandSongs,
    handleEdit,
    handleEditSong,
    handleDeleteSong,
    clearMessages,
    getBandInitial,
    getMemberInitial
  };
};