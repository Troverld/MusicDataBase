import { useState, useEffect, useCallback } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import { musicService } from '../../services/music.service';
import { Song } from '../../types';
import { usePermissions } from '../../hooks/usePermissions';
import { ReturnInfo, PAGE_SIZE } from './types';

export const useSongManagement = () => {
  const location = useLocation();
  const navigate = useNavigate();
  const [songs, setSongs] = useState<Song[]>([]);
  const [searchKeyword, setSearchKeyword] = useState('');
  const [showModal, setShowModal] = useState(false);
  const [editingSong, setEditingSong] = useState<Song | null>(null);
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');
  const [loading, setLoading] = useState(false);
  const [returnInfo, setReturnInfo] = useState<ReturnInfo | null>(null);
  
  // 分页相关状态
  const [currentPage, setCurrentPage] = useState(1);
  const [totalPages, setTotalPages] = useState(0);
  const [isSearchMode, setIsSearchMode] = useState(false);
  
  // 权限检查
  const { isUser, isAdmin } = usePermissions();
  const canUploadSongs = isUser || isAdmin;

  // 获取所有歌曲（改为分页）
  const fetchAllSongs = async () => {
    setLoading(true);
    setError('');
    setIsSearchMode(true); // 设置为搜索模式以显示分页
    setCurrentPage(1);
    setTotalPages(0);
    
    try {
      // 使用分页搜索，`%` 匹配所有歌曲
      const result = await musicService.searchSongsPaged('%', 1, PAGE_SIZE);
      const [pagedResult, message] = result;
      
      if (pagedResult && pagedResult.songIds && pagedResult.songIds.length > 0) {
        const songsData = await musicService.getSongsByIds(pagedResult.songIds);
        setSongs(songsData);
        setTotalPages(pagedResult.totalPages);
        setCurrentPage(1);
      } else {
        setSongs([]);
        setTotalPages(0);
        setCurrentPage(1);
      }
    } catch (err: any) {
      setError(err.message || '获取歌曲列表失败');
      setSongs([]);
      setTotalPages(0);
      setCurrentPage(1);
    } finally {
      setLoading(false);
    }
  };

  // 分页搜索歌曲
  const searchSongsPaged = async (keywords: string, pageNumber: number) => {
    setLoading(true);
    setError('');
    
    try {
      const result = await musicService.searchSongsPaged(keywords, pageNumber, PAGE_SIZE);
      const [pagedResult, message] = result;
      
      if (pagedResult && pagedResult.songIds && pagedResult.songIds.length > 0) {
        const songsData = await musicService.getSongsByIds(pagedResult.songIds);
        setSongs(songsData);
        setTotalPages(pagedResult.totalPages);
        setCurrentPage(pageNumber);
        
        if (songsData.length === 0) {
          setError('未找到匹配的歌曲详情');
        }
      } else {
        setSongs([]);
        setTotalPages(0);
        setCurrentPage(1);
        setError(message || '未找到匹配的歌曲');
      }
    } catch (err: any) {
      setError(err.message || '搜索失败');
      setSongs([]);
      setTotalPages(0);
      setCurrentPage(1);
    } finally {
      setLoading(false);
    }
  };

  const handleSearch = async () => {
    setIsSearchMode(true);
    
    // 如果搜索框为空，使用 '%' 获取所有歌曲的分页
    const keywords = searchKeyword.trim() || '%';
    await searchSongsPaged(keywords, 1);
  };

  // 处理分页切换
  const handlePageChange = async (page: number) => {
    if (!isSearchMode) {
      return;
    }
    
    // 如果没有搜索关键词，使用 '%' 获取所有歌曲的分页
    const keywords = searchKeyword.trim() || '%';
    await searchSongsPaged(keywords, page);
  };

  const handleDelete = async (songID: string) => {
    if (!window.confirm('确定要删除这首歌曲吗？此操作不可撤销。')) return;
    
    try {
      const [success, message] = await musicService.deleteSong(songID);
      if (success) {
        // 删除后重新加载当前页，如果没有搜索关键词则使用 '%'
        const keywords = searchKeyword.trim() || '%';
        await searchSongsPaged(keywords, currentPage);
        setSuccess('歌曲删除成功');
      } else {
        setError(message);
      }
    } catch (err: any) {
      setError(err.message);
    }
  };

  const handleEdit = (song: Song) => {
    setEditingSong(song);
    setShowModal(true);
  };

  const handleFormSuccess = async (message: string) => {
    setSuccess(message);
    setShowModal(false);
    setEditingSong(null);
    
    // 处理返回信息
    if (returnInfo) {
      if (returnInfo.type === 'artist') {
        navigate(`/artists/${returnInfo.id}`);
      } else if (returnInfo.type === 'band') {
        navigate(`/bands/${returnInfo.id}`);
      }
      setReturnInfo(null);
    } else {
      // 重新加载当前页，如果没有搜索关键词则使用 '%'
      const keywords = searchKeyword.trim() || '%';
      await searchSongsPaged(keywords, currentPage);
    }
  };

  const handleFormError = (message: string) => {
    setError(message);
  };

  const clearMessages = useCallback(() => {
    setError('');
    setSuccess('');
  }, []);

  const resetForm = useCallback(() => {
    setEditingSong(null);
  }, []);

  const handleCloseModal = () => {
    setShowModal(false);
    setEditingSong(null);
    
    // 处理返回信息
    if (returnInfo) {
      if (returnInfo.type === 'artist') {
        navigate(`/artists/${returnInfo.id}`);
      } else if (returnInfo.type === 'band') {
        navigate(`/bands/${returnInfo.id}`);
      }
      setReturnInfo(null);
    }
  };

  // 页面加载时获取所有歌曲
  useEffect(() => {
    fetchAllSongs();
  }, []); // 空依赖数组，只在组件挂载时执行一次

  // 检查是否从其他页面传递了编辑歌曲的数据
  useEffect(() => {
    if (location.state?.editSong) {
      const songToEdit = location.state.editSong as Song;
      const returnTo = location.state.returnTo;
      const returnId = location.state.returnId;
      
      setEditingSong(songToEdit);
      setShowModal(true);
      
      if (returnTo && returnId) {
        setReturnInfo({ type: returnTo, id: returnId });
      }
      
      // 清除 location state 以避免重复处理
      window.history.replaceState({}, document.title);
    }
  }, [location.state]);

  useEffect(() => {
    clearMessages();
  }, [searchKeyword, clearMessages]);

  return {
    // 状态
    songs,
    searchKeyword,
    showModal,
    editingSong,
    error,
    success,
    loading,
    returnInfo,
    currentPage,
    totalPages,
    isSearchMode,
    canUploadSongs,
    
    // 方法
    setSearchKeyword,
    setShowModal,
    handleSearch,
    handlePageChange,
    handleDelete,
    handleEdit,
    handleFormSuccess,
    handleFormError,
    handleCloseModal,
    resetForm
  };
};
