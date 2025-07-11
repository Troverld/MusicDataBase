import React, { useState, useEffect } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import { musicService } from '../../services/music.service';
import { Song } from '../../types';
import SongList from '../../components/SongList';
import SearchSection from './SearchSection';
import SongForm from './SongForm';
import Pagination from '../../components/Pagination';
import { usePermissions } from '../../hooks/usePermissions';

const SongManagement: React.FC = () => {
  const location = useLocation();
  const navigate = useNavigate();
  const [songs, setSongs] = useState<Song[]>([]);
  const [searchKeyword, setSearchKeyword] = useState('');
  const [showModal, setShowModal] = useState(false);
  const [editingSong, setEditingSong] = useState<Song | null>(null);
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');
  const [loading, setLoading] = useState(false);
  const [returnInfo, setReturnInfo] = useState<{type: string, id: string} | null>(null);
  
  // 分页相关状态
  const [currentPage, setCurrentPage] = useState(1);
  const [totalPages, setTotalPages] = useState(0);
  const [isSearchMode, setIsSearchMode] = useState(false);
  const pageSize = 5; // 固定页面大小为5
  
  // 权限检查
  const { isUser, isAdmin } = usePermissions();

  // 获取所有歌曲 (非分页)
  const fetchAllSongs = async () => {
    setLoading(true);
    setError('');
    setIsSearchMode(false);
    setCurrentPage(1);
    setTotalPages(0);
    
    try {
      // 使用 `%` 字符串搜索来获取所有歌曲
      const [songIds, message] = await musicService.searchSongs('%');
      
      if (songIds && songIds.length > 0) {
        const songsData = await musicService.getSongsByIds(songIds);
        setSongs(songsData);
      } else {
        setSongs([]);
      }
    } catch (err: any) {
      setError(err.message || '获取歌曲列表失败');
      setSongs([]);
    } finally {
      setLoading(false);
    }
  };

  // 分页搜索歌曲
  const searchSongsPaged = async (keywords: string, pageNumber: number) => {
    setLoading(true);
    setError('');
    
    try {
      const result = await musicService.searchSongsPaged(keywords, pageNumber, pageSize);
      const [pagedResult, message] = result;
      
      if (pagedResult && pagedResult.songIds && pagedResult.songIds.length > 0) {
        const songsData = await musicService.getSongsByIds(pagedResult.songIds);
        setSongs(songsData);
        
        // 如果是第一次搜索(pageNumber = -1)，只获取总页数
        if (pageNumber === -1) {
          setTotalPages(pagedResult.totalPages);
          setCurrentPage(1);
          // 再次调用获取第一页数据
          searchSongsPaged(keywords, 1);
          return;
        }
        
        setTotalPages(pagedResult.totalPages);
        setCurrentPage(pageNumber);
        
        if (songsData.length === 0) {
          setError('未找到匹配的歌曲详情');
        }
      } else {
        setSongs([]);
        setTotalPages(0);
        setCurrentPage(1);
        
        if (pageNumber !== -1) {
          setError(message || '未找到匹配的歌曲');
        }
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

  const handleSearch = async () => {
    if (!searchKeyword.trim()) {
      // 如果搜索框为空，显示所有歌曲
      await fetchAllSongs();
      return;
    }
    
    setIsSearchMode(true);
    // 先调用 pageNumber = -1 获取总页数
    await searchSongsPaged(searchKeyword, -1);
  };

  // 处理分页切换
  const handlePageChange = async (page: number) => {
    if (!isSearchMode || !searchKeyword.trim()) {
      return;
    }
    
    await searchSongsPaged(searchKeyword, page);
  };

  const handleDelete = async (songID: string) => {
    if (!window.confirm('确定要删除这首歌曲吗？此操作不可撤销。')) return;
    
    try {
      const [success, message] = await musicService.deleteSong(songID);
      if (success) {
        // 根据当前模式重新加载数据
        if (isSearchMode && searchKeyword.trim()) {
          await searchSongsPaged(searchKeyword, currentPage);
        } else {
          await fetchAllSongs();
        }
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
      // 根据当前模式重新加载数据
      if (isSearchMode && searchKeyword.trim()) {
        await searchSongsPaged(searchKeyword, currentPage);
      } else {
        await fetchAllSongs();
      }
    }
  };

  const handleFormError = (message: string) => {
    setError(message);
  };

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

  // 检查用户是否可以上传歌曲
  const canUploadSongs = isUser || isAdmin;

  return (
    <div className="page-container">
      <div className="page-header">
        <div className="header-left">
          <h1>歌曲管理</h1>
          {returnInfo && (
            <div className="return-info">
              <span>从 {returnInfo.type === 'artist' ? '艺术家' : '乐队'} 页面进入</span>
            </div>
          )}
        </div>
        <div className="header-actions">
          {canUploadSongs && (
            <button 
              className="btn-primary"
              onClick={() => setShowModal(true)}
            >
              + 添加歌曲
            </button>
          )}
        </div>
      </div>

      <div className="page-content">
        {/* 搜索区域 */}
        <SearchSection
          searchKeyword={searchKeyword}
          onSearchKeywordChange={setSearchKeyword}
          onSearch={handleSearch}
          loading={loading}
        />

        {/* 错误和成功提示 */}
        {error && (
          <div className="alert alert-error">
            {error}
          </div>
        )}
        
        {success && (
          <div className="alert alert-success">
            {success}
          </div>
        )}

        {/* 无权限提示 */}
        {!canUploadSongs && (
          <div className="alert alert-info">
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
              <circle cx="12" cy="12" r="10"/>
              <path d="m9 12 2 2 4-4"/>
            </svg>
            您可以查看歌曲信息，但没有上传歌曲的权限
          </div>
        )}

        {/* 歌曲列表 */}
        {loading ? (
          <div style={{ textAlign: 'center', padding: '40px' }}>
            <p>正在加载歌曲信息...</p>
          </div>
        ) : (
          <>
            <SongList songs={songs} onEdit={handleEdit} onDelete={handleDelete} />
            
            {/* 分页组件 - 只在搜索模式下显示 */}
            {isSearchMode && totalPages > 0 && (
              <Pagination
                currentPage={currentPage}
                totalPages={totalPages}
                onPageChange={handlePageChange}
                loading={loading}
              />
            )}
          </>
        )}
      </div>
      
      {/* 模态框 */}
      {showModal && canUploadSongs && (
        <SongForm
          editingSong={editingSong}
          onSuccess={handleFormSuccess}
          onError={handleFormError}
          onClose={handleCloseModal}
        />
      )}
    </div>
  );
};

export default SongManagement;