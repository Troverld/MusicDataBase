import React, { useState, useEffect } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import { musicService } from '../../services/music.service';
import { Song } from '../../types';
import SongList from '../../components/SongList';
import SearchSection from './SearchSection';
import SongForm from './SongForm';
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
  
  // 权限检查
  const { isUser, isAdmin } = usePermissions();

  // 检查是否从其他页面传递了编辑歌曲的数据
  useEffect(() => {
    if (location.state?.editSong) {
      const songToEdit = location.state.editSong as Song;
      const returnTo = location.state.returnTo;
      const returnId = location.state.returnId;
      
      setEditingSong(songToEdit);
      setShowModal(true);
      
      // 保存返回信息
      if (returnTo && returnId) {
        setReturnInfo({ type: returnTo, id: returnId });
      }
      
      // 清除 location state 以避免重复处理
      window.history.replaceState({}, document.title);
    }
  }, [location.state]);

  const handleSearch = async () => {
    if (!searchKeyword.trim()) {
      setSongs([]);
      return;
    }
    
    setLoading(true);
    setError('');
    
    try {
      const [songIDs, message] = await musicService.searchSongs(searchKeyword);
      if (songIDs && songIDs.length > 0) {
        // 获取歌曲的详细信息
        const songDetails = await musicService.getSongsByIds(songIDs);
        setSongs(songDetails);
        
        if (songDetails.length === 0) {
          setError('未找到匹配的歌曲详情');
        }
      } else {
        setSongs([]);
        setError(message || '未找到匹配的歌曲');
      }
    } catch (err: any) {
      setError(err.message || '搜索失败');
      setSongs([]);
    } finally {
      setLoading(false);
    }
  };

  const handleDelete = async (songID: string) => {
    if (!window.confirm('确定要删除这首歌曲吗？')) return;
    
    try {
      const [success, message] = await musicService.deleteSong(songID);
      if (success) {
        setSongs(songs.filter(s => s.songID !== songID));
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

  const handleFormSuccess = (message: string) => {
    setSuccess(message);
    setShowModal(false);
    setEditingSong(null);
    
    // 如果有返回信息，提供返回选项
    if (returnInfo) {
      const entityName = returnInfo.type === 'artist' ? '艺术家' : '乐队';
      const enhancedMessage = `${message} 是否返回到${entityName}详情页面？`;
      
      if (window.confirm(enhancedMessage)) {
        const returnPath = returnInfo.type === 'artist' 
          ? `/artists/${returnInfo.id}` 
          : `/bands/${returnInfo.id}`;
        navigate(returnPath);
        return;
      }
      
      // 清除返回信息
      setReturnInfo(null);
    }
    
    // 如果当前有搜索，刷新搜索结果
    if (searchKeyword.trim()) {
      handleSearch();
    }
  };

  const handleFormError = (message: string) => {
    setError(message);
  };

  const handleCloseModal = () => {
    setShowModal(false);
    setEditingSong(null);
    
    // 如果有返回信息且是编辑模式，询问是否返回
    if (returnInfo && editingSong) {
      const entityName = returnInfo.type === 'artist' ? '艺术家' : '乐队';
      
      if (window.confirm(`是否返回到${entityName}详情页面？`)) {
        const returnPath = returnInfo.type === 'artist' 
          ? `/artists/${returnInfo.id}` 
          : `/bands/${returnInfo.id}`;
        navigate(returnPath);
        return;
      }
      
      // 清除返回信息
      setReturnInfo(null);
    }
  };

  const clearMessages = () => {
    setError('');
    setSuccess('');
  };

  const resetForm = () => {
    setEditingSong(null);
    setReturnInfo(null); // 重置时也清除返回信息
  };

  // 当搜索关键词变化时清除消息
  useEffect(() => {
    clearMessages();
  }, [searchKeyword]);

  // 检查用户是否可以上传歌曲
  const canUploadSongs = isUser || isAdmin;

  return (
    <div>
      <h1>歌曲管理</h1>
      <p style={{ color: '#666', marginBottom: '30px', fontSize: '16px' }}>
        管理系统中的歌曲信息，搜索现有歌曲，查看详细信息。
        {canUploadSongs ? '您可以上传新歌曲并编辑您有权限的歌曲。' : '您可以搜索和查看歌曲信息。'}
      </p>
      
      {/* 显示来源提示 */}
      {returnInfo && editingSong && (
        <div style={{
          background: '#e3f2fd',
          border: '1px solid #bbdefb',
          color: '#1976d2',
          padding: '10px 15px',
          borderRadius: '4px',
          marginBottom: '15px',
          fontSize: '14px'
        }}>
          📍 您正在编辑来自{returnInfo.type === 'artist' ? '艺术家' : '乐队'}详情页面的歌曲《{editingSong.name}》
        </div>
      )}
      
      {error && <div className="error-message">{error}</div>}
      {success && <div className="success-message">{success}</div>}
      
      <SearchSection
        searchKeyword={searchKeyword}
        onSearchKeywordChange={setSearchKeyword}
        onSearch={handleSearch}
        loading={loading}
      />
      
      {/* 只有有权限的用户可以上传歌曲 */}
      {canUploadSongs ? (
        <button 
          className="btn btn-primary" 
          onClick={() => { resetForm(); setShowModal(true); }}
          style={{ marginBottom: '20px' }}
        >
          上传新歌曲
        </button>
      ) : (
        <div className="permission-warning" style={{ marginBottom: '20px' }}>
          ⚠️ 您没有上传歌曲的权限，仅能搜索和查看歌曲信息
        </div>
      )}
      
      {loading ? (
        <div style={{ textAlign: 'center', padding: '40px' }}>
          <p>正在加载歌曲信息...</p>
        </div>
      ) : (
        <SongList songs={songs} onEdit={handleEdit} onDelete={handleDelete} />
      )}
      
      {showModal && canUploadSongs && (
        <SongForm
          editingSong={editingSong}
          onSuccess={handleFormSuccess}
          onError={handleFormError}
          onClose={handleCloseModal}
        />
      )}

      {/* 使用提示 */}
      <div style={{ 
        background: '#f8f9fa', 
        padding: '25px', 
        borderRadius: '8px', 
        marginTop: '40px',
        border: '1px solid #e9ecef'
      }}>
        <h3 style={{ marginBottom: '15px', color: '#495057' }}>💡 歌曲管理提示</h3>
        <div style={{ fontSize: '14px', color: '#6c757d', lineHeight: '1.6' }}>
          <p><strong>权限说明:</strong> 只有注册用户可以上传歌曲，用户只能编辑自己上传的歌曲，管理员拥有所有权限。</p>
          <p><strong>智能显示:</strong> 歌曲列表显示艺术家和乐队的名称，而不是ID，提供更好的用户体验。</p>
          <p><strong>智能选择:</strong> 通过搜索选择艺术家和乐队，系统会自动使用 ID 进行匹配，避免重名问题。</p>
          <p><strong>创作者与演唱者:</strong> 支持选择艺术家或乐队，系统会显示类型和简介供您参考。</p>
          <p><strong>专业角色:</strong> 作词、作曲、编曲、演奏等角色通常由个人艺术家担任，因此只能选择艺术家。</p>
          <p><strong>编辑模式:</strong> 编辑现有歌曲时，系统会智能识别ID并转换为对应的名称显示。</p>
          <p><strong>跨页面编辑:</strong> 从艺术家或乐队详情页面点击编辑歌曲，会跳转到此页面进行编辑，编辑完成后可选择返回来源页面。</p>
          <p><strong>数据一致性:</strong> 系统使用 ID 而不是名称传递数据，确保与后端 API 的完美对接。</p>
        </div>
      </div>
    </div>
  );
};

export default SongManagement;