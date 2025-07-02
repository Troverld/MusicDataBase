import React, { useState, useEffect, useRef } from 'react';
import { musicService } from '../services/music.service';
import { Song } from '../types';
import SongList from '../components/SongList';
import { useGenres } from '../hooks/useGenres';

const SongManagement: React.FC = () => {
  const [songs, setSongs] = useState<Song[]>([]);
  const [searchKeyword, setSearchKeyword] = useState('');
  const [showModal, setShowModal] = useState(false);
  const [editingSong, setEditingSong] = useState<Song | null>(null);
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');
  const [loading, setLoading] = useState(false);
  const [dropdownOpen, setDropdownOpen] = useState(false);
  
  // 使用 Set 来管理选中的曲风ID
  const [selectedGenresSet, setSelectedGenresSet] = useState<Set<string>>(new Set());
  
  const { genres } = useGenres();
  const dropdownRef = useRef<HTMLDivElement>(null);
  
  const [formData, setFormData] = useState({
    name: '',
    releaseTime: new Date().toISOString().split('T')[0],
    creators: '',
    performers: '',
    lyricists: '',
    composers: '',
    arrangers: '',
    instrumentalists: ''
  });

  // 点击外部关闭下拉框
  useEffect(() => {
    const handleClickOutside = (event: MouseEvent) => {
      if (dropdownRef.current && !dropdownRef.current.contains(event.target as Node)) {
        setDropdownOpen(false);
      }
    };

    if (dropdownOpen) {
      document.addEventListener('mousedown', handleClickOutside);
    }

    return () => {
      document.removeEventListener('mousedown', handleClickOutside);
    };
  }, [dropdownOpen]);

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
    setFormData({
      name: song.name,
      releaseTime: new Date(song.releaseTime).toISOString().split('T')[0],
      creators: song.creators.join(', '),
      performers: song.performers.join(', '),
      lyricists: song.lyricists?.join(', ') || '',
      composers: song.composers?.join(', ') || '',
      arrangers: song.arrangers?.join(', ') || '',
      instrumentalists: song.instrumentalists?.join(', ') || ''
    });
    // 使用 Set 来管理选中的曲风
    setSelectedGenresSet(new Set(song.genres));
    setShowModal(true);
  };

  // 切换曲风选中状态
  const handleGenreToggle = (genreId: string) => {
    if (!genreId) return; // 防止空ID
    
    setSelectedGenresSet(prevSet => {
      const newSet = new Set(prevSet);
      const wasSelected = newSet.has(genreId);
      
      if (wasSelected) {
        newSet.delete(genreId);
        console.log(`Removed genre: ${genreId}`); // 调试信息
      } else {
        newSet.add(genreId);
        console.log(`Added genre: ${genreId}`); // 调试信息
      }
      
      console.log('Current selected genres:', Array.from(newSet)); // 调试信息
      return newSet;
    });
  };

  // 移除曲风
  const handleGenreRemove = (genreId: string) => {
    setSelectedGenresSet(prevSet => {
      const newSet = new Set(prevSet);
      newSet.delete(genreId);
      return newSet;
    });
  };

  // 清空所有选中的曲风
  const handleClearAllGenres = () => {
    setSelectedGenresSet(new Set());
  };

  // 切换下拉框显示状态
  const toggleDropdown = () => {
    setDropdownOpen(!dropdownOpen);
  };

  // 获取选中曲风的显示信息
  const getSelectedGenresList = () => {
    return Array.from(selectedGenresSet).map(id => {
      const genre = genres.find(g => g.genreID === id);
      return { id, name: genre ? genre.name : id };
    });
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');
    setSuccess('');

    const songData = {
      name: formData.name,
      releaseTime: new Date(formData.releaseTime).getTime(),
      creators: formData.creators.split(',').map(s => s.trim()).filter(s => s),
      performers: formData.performers.split(',').map(s => s.trim()).filter(s => s),
      lyricists: formData.lyricists.split(',').map(s => s.trim()).filter(s => s),
      composers: formData.composers.split(',').map(s => s.trim()).filter(s => s),
      arrangers: formData.arrangers.split(',').map(s => s.trim()).filter(s => s),
      instrumentalists: formData.instrumentalists.split(',').map(s => s.trim()).filter(s => s),
      genres: Array.from(selectedGenresSet) // 将 Set 转换为数组
    };

    try {
      if (editingSong) {
        const [success, message] = await musicService.updateSong(editingSong.songID, songData);
        if (success) {
          setSuccess('歌曲更新成功');
          setShowModal(false);
          setDropdownOpen(false);
          // 刷新歌曲列表
          if (searchKeyword.trim()) {
            handleSearch();
          }
        } else {
          setError(message);
        }
      } else {
        const [songID, message] = await musicService.uploadSong(songData);
        if (songID) {
          setSuccess('歌曲上传成功');
          setShowModal(false);
          setDropdownOpen(false);
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
      releaseTime: new Date().toISOString().split('T')[0],
      creators: '',
      performers: '',
      lyricists: '',
      composers: '',
      arrangers: '',
      instrumentalists: ''
    });
    setSelectedGenresSet(new Set());
    setEditingSong(null);
  };

  const clearMessages = () => {
    setError('');
    setSuccess('');
  };

  // 当搜索关键词变化时清除消息
  useEffect(() => {
    clearMessages();
  }, [searchKeyword]);

  // 当模态框关闭时重置下拉框状态
  useEffect(() => {
    if (!showModal) {
      setDropdownOpen(false);
    }
  }, [showModal]);

  const selectedGenresList = getSelectedGenresList();

  return (
    <div>
      <h1>歌曲管理</h1>
      
      {error && <div className="error-message">{error}</div>}
      {success && <div className="success-message">{success}</div>}
      
      <div className="search-box">
        <div style={{ display: 'flex', gap: '10px', alignItems: 'center' }}>
          <input
            type="text"
            placeholder="搜索歌曲..."
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
        上传新歌曲
      </button>
      
      {loading ? (
        <div style={{ textAlign: 'center', padding: '40px' }}>
          <p>正在加载歌曲信息...</p>
        </div>
      ) : (
        <SongList songs={songs} onEdit={handleEdit} onDelete={handleDelete} />
      )}
      
      {showModal && (
        <div className="modal" onClick={() => { setShowModal(false); }}>
          <div className="modal-content" onClick={(e) => e.stopPropagation()}>
            <div className="modal-header">
              <h2>{editingSong ? '编辑歌曲' : '上传新歌曲'}</h2>
              <button onClick={() => setShowModal(false)}>×</button>
            </div>
            
            <form onSubmit={handleSubmit}>
              <div className="form-group">
                <label>歌曲名称*</label>
                <input
                  type="text"
                  value={formData.name}
                  onChange={(e) => setFormData({...formData, name: e.target.value})}
                  required
                />
              </div>
              
              <div className="form-group">
                <label>发布日期*</label>
                <input
                  type="date"
                  value={formData.releaseTime}
                  onChange={(e) => setFormData({...formData, releaseTime: e.target.value})}
                  required
                />
              </div>
              
              <div className="form-row">
                <div className="form-group">
                  <label>创作者 (逗号分隔)</label>
                  <input
                    type="text"
                    value={formData.creators}
                    onChange={(e) => setFormData({...formData, creators: e.target.value})}
                    placeholder="艺术家1, 艺术家2"
                  />
                </div>
                
                <div className="form-group">
                  <label>演唱者 (逗号分隔)</label>
                  <input
                    type="text"
                    value={formData.performers}
                    onChange={(e) => setFormData({...formData, performers: e.target.value})}
                    placeholder="歌手1, 歌手2"
                  />
                </div>
              </div>
              
              <div className="form-row">
                <div className="form-group">
                  <label>作词者 (逗号分隔)</label>
                  <input
                    type="text"
                    value={formData.lyricists}
                    onChange={(e) => setFormData({...formData, lyricists: e.target.value})}
                  />
                </div>
                
                <div className="form-group">
                  <label>作曲者 (逗号分隔)</label>
                  <input
                    type="text"
                    value={formData.composers}
                    onChange={(e) => setFormData({...formData, composers: e.target.value})}
                  />
                </div>
              </div>
              
              <div className="form-row">
                <div className="form-group">
                  <label>编曲者 (逗号分隔)</label>
                  <input
                    type="text"
                    value={formData.arrangers}
                    onChange={(e) => setFormData({...formData, arrangers: e.target.value})}
                  />
                </div>
                
                <div className="form-group">
                  <label>演奏者 (逗号分隔)</label>
                  <input
                    type="text"
                    value={formData.instrumentalists}
                    onChange={(e) => setFormData({...formData, instrumentalists: e.target.value})}
                  />
                </div>
              </div>
              
              <div className="form-group">
                <label>
                  曲风选择
                  {selectedGenresSet.size > 0 && (
                    <span className="multi-select-counter">
                      {selectedGenresSet.size}
                    </span>
                  )}
                </label>
                <div className="multi-select-dropdown" ref={dropdownRef}>
                  <div 
                    className={`multi-select-trigger ${dropdownOpen ? 'open' : ''}`}
                    onClick={toggleDropdown}
                    tabIndex={0}
                  >
                    {selectedGenresSet.size === 0 ? (
                      <span className="multi-select-placeholder">请选择曲风...</span>
                    ) : (
                      <div className="multi-select-values">
                        {selectedGenresList.slice(0, 5).map(({ id, name }) => (
                          <span key={id} className="multi-select-tag">
                            <span className="multi-select-tag-text" title={name}>
                              {name}
                            </span>
                            <span 
                              className="multi-select-tag-remove"
                              onClick={(e) => {
                                e.stopPropagation();
                                handleGenreRemove(id);
                              }}
                              title="移除"
                            >
                              ×
                            </span>
                          </span>
                        ))}
                        {selectedGenresSet.size > 5 && (
                          <span className="multi-select-tag" style={{ backgroundColor: '#f8f9fa', color: '#666' }}>
                            +{selectedGenresSet.size - 5}
                          </span>
                        )}
                      </div>
                    )}
                  </div>
                  
                  {dropdownOpen && (
                    <div className="multi-select-dropdown-menu">
                      {selectedGenresSet.size > 0 && (
                        <div 
                          className="multi-select-option"
                          onClick={(e) => {
                            e.stopPropagation();
                            handleClearAllGenres();
                          }}
                          style={{ 
                            borderBottom: '2px solid #dee2e6',
                            backgroundColor: '#fff3cd',
                            fontWeight: 'bold'
                          }}
                        >
                          <span style={{ fontSize: '14px' }}>🗑️</span>
                          <div className="multi-select-option-content">
                            <div className="multi-select-option-name">
                              清空所有选择 ({selectedGenresSet.size} 项)
                            </div>
                          </div>
                        </div>
                      )}
                      
                      {genres.length === 0 ? (
                        <div className="multi-select-empty">
                          暂无可用曲风，请先到曲风管理页面添加曲风
                        </div>
                      ) : (
                        genres.map((genre) => {
                          const isSelected = selectedGenresSet.has(genre.genreID);
                          return (
                            <div 
                              key={genre.genreID} 
                              className={`multi-select-option ${isSelected ? 'selected' : ''}`}
                              onMouseDown={(e) => {
                                // 使用 onMouseDown 替代 onClick，避免与复选框事件冲突
                                e.preventDefault();
                                e.stopPropagation();
                                handleGenreToggle(genre.genreID);
                              }}
                            >
                              <input
                                type="checkbox"
                                checked={isSelected}
                                onChange={() => {}} // 完全禁用复选框的事件
                                onMouseDown={(e) => {
                                  // 阻止复选框的默认行为
                                  e.preventDefault();
                                  e.stopPropagation();
                                }}
                                onClick={(e) => {
                                  // 阻止复选框的点击事件
                                  e.preventDefault();
                                  e.stopPropagation();
                                }}
                                readOnly
                                style={{ pointerEvents: 'none' }} // 完全禁用复选框的交互
                              />
                              <div className="multi-select-option-content">
                                <div className="multi-select-option-name">
                                  {genre.name}
                                  {isSelected && <span style={{ marginLeft: '8px', color: '#007bff', fontWeight: 'bold' }}>✓</span>}
                                </div>
                                {genre.description && (
                                  <div className="multi-select-option-description">
                                    {genre.description}
                                  </div>
                                )}
                              </div>
                            </div>
                          );
                        })
                      )}
                    </div>
                  )}
                </div>
                
                {/* 已选择曲风的详细信息 */}
                {selectedGenresSet.size > 0 && (
                  <div style={{ 
                    marginTop: '8px', 
                    padding: '8px 12px',
                    backgroundColor: '#e8f5e8',
                    borderLeft: '4px solid #28a745',
                    borderRadius: '0 4px 4px 0',
                    fontSize: '12px',
                    color: '#155724'
                  }}>
                    <strong>已选择 {selectedGenresSet.size} 个曲风:</strong> {selectedGenresList.slice(0, 3).map(g => g.name).join(', ')}
                    {selectedGenresSet.size > 3 && ` 等共${selectedGenresSet.size}个曲风`}
                  </div>
                )}
              </div>
              
              <button type="submit" className="btn btn-primary">
                {editingSong ? '更新歌曲' : '上传歌曲'}
              </button>
            </form>
          </div>
        </div>
      )}
    </div>
  );
};

export default SongManagement;