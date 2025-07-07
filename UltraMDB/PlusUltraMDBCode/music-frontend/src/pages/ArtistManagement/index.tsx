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
        loading={loading}
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

      {/* 使用提示 */}
      <div style={{ 
        background: '#f8f9fa', 
        padding: '25px', 
        borderRadius: '8px', 
        marginTop: '40px',
        border: '1px solid #e9ecef'
      }}>
        <h3 style={{ marginBottom: '15px', color: '#495057' }}>💡 艺术家管理提示</h3>
        <div style={{ fontSize: '14px', color: '#6c757d', lineHeight: '1.6' }}>
          <p><strong>查看详情:</strong> 点击艺术家名称可以查看完整的艺术家信息页面。</p>
          <p><strong>搜索艺术家:</strong> 在搜索框中输入艺术家名称的关键词，支持模糊匹配。</p>
          <p><strong>权限管理:</strong> 只有管理员可以创建和删除艺术家，艺术家管理者可以编辑对应艺术家的信息。</p>
          <p><strong>创建艺术家:</strong> 填写艺术家名称和详细简介，系统会自动生成唯一的艺术家ID。</p>
          <p><strong>编辑艺术家:</strong> 只有有权限的用户才能看到"编辑"按钮并修改艺术家信息。</p>
          <p><strong>删除艺术家:</strong> 删除操作不可撤销，仅管理员可执行，请确保该艺术家未被歌曲或专辑引用。</p>
        </div>
      </div>
    </div>
  );
};

export default ArtistManagement;