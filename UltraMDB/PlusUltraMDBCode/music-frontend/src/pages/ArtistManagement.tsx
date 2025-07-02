import React, { useState, useEffect } from 'react';
import { artistService } from '../services/artist.service';
import { Artist } from '../types';

const ArtistManagement: React.FC = () => {
  const [artists, setArtists] = useState<Artist[]>([]);
  const [searchKeyword, setSearchKeyword] = useState('');
  const [showModal, setShowModal] = useState(false);
  const [editingArtist, setEditingArtist] = useState<Artist | null>(null);
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');
  const [loading, setLoading] = useState(false);
  
  const [formData, setFormData] = useState({
    name: '',
    bio: ''
  });

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
        // 获取艺术家的详细信息
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
    setFormData({
      name: artist.name,
      bio: artist.bio
    });
    setShowModal(true);
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');
    setSuccess('');

    if (!formData.name.trim() || !formData.bio.trim()) {
      setError('艺术家名称和简介都不能为空');
      return;
    }

    try {
      if (editingArtist) {
        const [success, message] = await artistService.updateArtist(editingArtist.artistID, formData);
        if (success) {
          setSuccess('艺术家信息更新成功');
          setShowModal(false);
          // 刷新艺术家列表
          if (searchKeyword.trim()) {
            handleSearch();
          }
        } else {
          setError(message);
        }
      } else {
        const [artistID, message] = await artistService.createArtist(formData);
        if (artistID) {
          setSuccess(`艺术家创建成功！艺术家ID: ${artistID}`);
          setShowModal(false);
          // 重置表单
          resetForm();
          // 如果当前有搜索，刷新搜索结果
          if (searchKeyword.trim()) {
            handleSearch();
          }
        } else {
          setError(message);
        }
      }
    } catch (err: any) {
      setError(err.message);
    }
  };

  const resetForm = () => {
    setFormData({
      name: '',
      bio: ''
    });
    setEditingArtist(null);
  };

  const clearMessages = () => {
    setError('');
    setSuccess('');
  };

  // 当搜索关键词变化时清除消息
  useEffect(() => {
    clearMessages();
  }, [searchKeyword]);

  const formatList = (items: string[] | undefined) => {
    if (!items || items.length === 0) return '无';
    return items.join(', ');
  };

  return (
    <div>
      <h1>艺术家管理</h1>
      <p style={{ color: '#666', marginBottom: '30px', fontSize: '16px' }}>
        管理系统中的艺术家信息，搜索、创建、编辑或删除艺术家档案。
      </p>
      
      {error && <div className="error-message">{error}</div>}
      {success && <div className="success-message">{success}</div>}
      
      <div className="search-box">
        <div style={{ display: 'flex', gap: '10px', alignItems: 'center' }}>
          <input
            type="text"
            placeholder="搜索艺术家..."
            value={searchKeyword}
            onChange={(e) => setSearchKeyword(e.target.value)}
            onKeyPress={(e) => e.key === 'Enter' && handleSearch()}
            style={{ flex: 1 }}
          />
          <button 
            className="btn btn-primary" 
            onClick={handleSearch}
            disabled={loading}
          >
            {loading ? '搜索中...' : '搜索'}
          </button>
        </div>
      </div>
      
      <button 
        className="btn btn-primary" 
        onClick={() => { resetForm(); setShowModal(true); }}
        style={{ marginBottom: '20px' }}
      >
        创建新艺术家
      </button>
      
      {loading ? (
        <div style={{ textAlign: 'center', padding: '40px' }}>
          <p>正在加载艺术家信息...</p>
        </div>
      ) : (
        <div>
          {artists.length === 0 ? (
            <div className="empty-state">
              <p>未找到艺术家</p>
              <p style={{ fontSize: '14px', color: '#999', marginTop: '10px' }}>
                {searchKeyword.trim() ? '请尝试其他搜索关键词' : '请使用搜索功能查找艺术家'}
              </p>
            </div>
          ) : (
            <div className="song-list">
              {artists.map((artist) => (
                <div key={artist.artistID} className="song-item">
                  <h3>{artist.name}</h3>
                  
                  <div style={{ marginBottom: '15px' }}>
                    <p><strong>艺术家ID:</strong> 
                      <span style={{ 
                        fontFamily: 'monospace', 
                        backgroundColor: '#f8f9fa', 
                        padding: '2px 6px', 
                        borderRadius: '3px',
                        marginLeft: '8px',
                        fontSize: '12px'
                      }}>
                        {artist.artistID}
                      </span>
                    </p>
                  </div>

                  <div style={{ marginBottom: '15px' }}>
                    <strong>简介:</strong>
                    <div style={{ 
                      marginTop: '8px', 
                      padding: '12px', 
                      backgroundColor: '#f8f9fa', 
                      borderRadius: '4px',
                      lineHeight: '1.5',
                      fontSize: '14px'
                    }}>
                      {artist.bio || '暂无简介'}
                    </div>
                  </div>

                  {artist.managers && artist.managers.length > 0 && (
                    <div style={{ marginBottom: '15px' }}>
                      <strong>管理者:</strong>
                      <div style={{ marginTop: '5px' }}>
                        {artist.managers.map((manager, index) => (
                          <span key={index} className="chip" style={{ backgroundColor: '#e3f2fd', color: '#1976d2' }}>
                            {manager}
                          </span>
                        ))}
                      </div>
                    </div>
                  )}

                  <div className="song-actions">
                    <button className="btn btn-secondary" onClick={() => handleEdit(artist)}>
                      编辑
                    </button>
                    <button className="btn btn-danger" onClick={() => handleDelete(artist.artistID)}>
                      删除
                    </button>
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>
      )}
      
      {showModal && (
        <div className="modal" onClick={() => { setShowModal(false); }}>
          <div className="modal-content" onClick={(e) => e.stopPropagation()}>
            <div className="modal-header">
              <h2>{editingArtist ? '编辑艺术家' : '创建新艺术家'}</h2>
              <button onClick={() => setShowModal(false)}>×</button>
            </div>
            
            <form onSubmit={handleSubmit}>
              <div className="form-group">
                <label>艺术家名称*</label>
                <input
                  type="text"
                  value={formData.name}
                  onChange={(e) => setFormData({...formData, name: e.target.value})}
                  placeholder="请输入艺术家名称"
                  required
                />
              </div>
              
              <div className="form-group">
                <label>艺术家简介*</label>
                <textarea
                  value={formData.bio}
                  onChange={(e) => setFormData({...formData, bio: e.target.value})}
                  placeholder="请输入艺术家简介、背景信息等..."
                  required
                  rows={6}
                  style={{ resize: 'vertical', minHeight: '120px' }}
                />
              </div>
              
              <div style={{ 
                display: 'flex', 
                gap: '10px', 
                justifyContent: 'flex-end',
                marginTop: '20px'
              }}>
                <button 
                  type="button" 
                  className="btn btn-secondary"
                  onClick={() => setShowModal(false)}
                >
                  取消
                </button>
                <button 
                  type="submit" 
                  className="btn btn-primary"
                  disabled={!formData.name.trim() || !formData.bio.trim()}
                >
                  {editingArtist ? '更新艺术家' : '创建艺术家'}
                </button>
              </div>
            </form>
          </div>
        </div>
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
          <p><strong>搜索艺术家:</strong> 在搜索框中输入艺术家名称的关键词，支持模糊匹配。</p>
          <p><strong>创建艺术家:</strong> 填写艺术家名称和详细简介，系统会自动生成唯一的艺术家ID。</p>
          <p><strong>编辑艺术家:</strong> 点击"编辑"按钮修改艺术家的名称和简介信息。</p>
          <p><strong>删除艺术家:</strong> 删除操作不可撤销，请确保该艺术家未被歌曲或专辑引用。</p>
          <p><strong>权限说明:</strong> 艺术家管理功能需要管理员权限，请确保您有足够的操作权限。</p>
        </div>
      </div>
    </div>
  );
};

export default ArtistManagement;