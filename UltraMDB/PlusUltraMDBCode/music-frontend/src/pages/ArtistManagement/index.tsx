import React, { useState, useEffect } from 'react';
import { useLocation } from 'react-router-dom';
import { artistService } from '../../services/artist.service';
import { Artist } from '../../types';
import { usePermissions } from '../../hooks/usePermissions';
import SearchSection from './SearchSection';
import ArtistList from './ArtistList';
import ArtistForm from './ArtistForm';

const ArtistManagement: React.FC = () => {
  const location = useLocation();
  const [artists, setArtists] = useState<Artist[]>([]);
  const [searchKeyword, setSearchKeyword] = useState('');
  const [showModal, setShowModal] = useState(false);
  const [editingArtist, setEditingArtist] = useState<Artist | null>(null);
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');
  const [loading, setLoading] = useState(false);
  
  const { isAdmin } = usePermissions();

  // 检查是否从其他页面传递了编辑艺术家的数据
  useEffect(() => {
    if (location.state?.editArtist) {
      const artistToEdit = location.state.editArtist as Artist;
      setEditingArtist(artistToEdit);
      setShowModal(true);
      // 清除 location state 以避免重复处理
      window.history.replaceState({}, document.title);
    }
  }, [location.state]);

  const handleSearch = async () => {
    if (!searchKeyword.trim()) {
      setArtists([]);
      return;
    }
    
    setLoading(true);
    setError('');
    
    try {
      const [artistIDs, message] = await artistService.searchArtistByName(searchKeyword);
      if (artistIDs && artistIDs.length > 0) {
        const artistDetails = await artistService.getArtistsByIds(artistIDs);
        setArtists(artistDetails);
        
        if (artistDetails.length === 0) {
          setError('未找到匹配的艺术家详情');
        }
      } else {
        setArtists([]);
        setError(message || '未找到匹配的艺术家');
      }
    } catch (err: any) {
      setError(err.message || '搜索失败');
      setArtists([]);
    } finally {
      setLoading(false);
    }
  };

  const handleDelete = async (artistID: string) => {
    if (!window.confirm('确定要删除这个艺术家吗？此操作不可撤销，可能会影响相关的歌曲和专辑。')) return;
    
    try {
      const [success, message] = await artistService.deleteArtist(artistID);
      if (success) {
        setArtists(artists.filter(a => a.artistID !== artistID));
        setSuccess('艺术家删除成功');
      } else {
        setError(message);
      }
    } catch (err: any) {
      setError(err.message);
    }
  };

  const handleEdit = (artist: Artist) => {
    setEditingArtist(artist);
    setShowModal(true);
  };

  const handleFormSuccess = (message: string) => {
    setSuccess(message);
    setShowModal(false);
    setEditingArtist(null);
    if (searchKeyword.trim()) {
      handleSearch();
    }
  };

  const handleFormError = (message: string) => {
    setError(message);
  };

  const clearMessages = () => {
    setError('');
    setSuccess('');
  };

  const resetForm = () => {
    setEditingArtist(null);
  };

  useEffect(() => {
    clearMessages();
  }, [searchKeyword]);

  return (
    <div>
      <h1>艺术家管理</h1>
      <p style={{ color: '#666', marginBottom: '30px', fontSize: '16px' }}>
        管理系统中的艺术家信息，搜索、创建、编辑或删除艺术家档案。点击艺术家名称可查看详细信息。
      </p>
      
      {error && <div className="error-message">{error}</div>}
      {success && <div className="success-message">{success}</div>}
      
      <SearchSection
        searchKeyword={searchKeyword}
        onSearchKeywordChange={setSearchKeyword}
        onSearch={handleSearch}
        loading={loading}
      />
      
      {isAdmin && (
        <button 
          className="btn btn-primary" 
          onClick={() => { resetForm(); setShowModal(true); }}
          style={{ marginBottom: '20px' }}
        >
          创建新艺术家
        </button>
      )}
      
      {!isAdmin && (
        <div className="permission-warning" style={{ marginBottom: '20px' }}>
          ⚠️ 您没有创建艺术家的权限，仅能查看和编辑您有权限的艺术家
        </div>
      )}
      
      <ArtistList
        artists={artists}
        onEdit={handleEdit}
        onDelete={handleDelete}
        searchKeyword={searchKeyword}
      />
      
      {showModal && (
        <ArtistForm
          editingArtist={editingArtist}
          onSuccess={handleFormSuccess}
          onError={handleFormError}
          onClose={() => setShowModal(false)}
        />
      )}
    </div>
  );
};

export default ArtistManagement;