import React, { useState, useEffect, useRef } from 'react';
import { musicService } from '../services/music.service';
import { Song } from '../types';
import SongList from '../components/SongList';
import { useGenres } from '../hooks/useGenres';
import { useArtistBand, ArtistBandItem } from '../hooks/useArtistBand';
import ArtistBandSelector from '../components/ArtistBandSelector';

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
  
  // 艺术家/乐队选择状态
  const [selectedCreators, setSelectedCreators] = useState<ArtistBandItem[]>([]);
  const [selectedPerformers, setSelectedPerformers] = useState<ArtistBandItem[]>([]);
  const [selectedLyricists, setSelectedLyricists] = useState<ArtistBandItem[]>([]);
  const [selectedComposers, setSelectedComposers] = useState<ArtistBandItem[]>([]);
  const [selectedArrangers, setSelectedArrangers] = useState<ArtistBandItem[]>([]);
  const [selectedInstrumentalists, setSelectedInstrumentalists] = useState<ArtistBandItem[]>([]);
  
  const { genres } = useGenres();
  const { getArtistBandsByIds } = useArtistBand();
  const dropdownRef = useRef<HTMLDivElement>(null);
  
  const [formData, setFormData] = useState({
    name: '',
    releaseTime: new Date().toISOString().split('T')[0]
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

  // 将艺术家/乐队名称转换为选中项目
  const convertNamesToSelectedItems = async (names: string[]): Promise<ArtistBandItem[]> => {
    if (!names || names.length === 0) return [];
    
    const results: ArtistBandItem[] = [];
    
    for (const name of names) {
      if (!name.trim()) continue;
      
      try {
        // 先搜索这个名称
        const searchResults = await getArtistBandsByIds([]);
        // 这里需要实现一个通过名称搜索的功能，暂时先返回空数组
        // 实际实现中可能需要调用搜索API然后匹配精确名称
      } catch (error) {
        console.warn(`Failed to convert name to item: ${name}`, error);
      }
    }
    
    return results;
  };

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

  const handleEdit = async (song: Song) => {
    setEditingSong(song);
    setFormData({
      name: song.name,
      releaseTime: new Date(song.releaseTime).toISOString().split('T')[0]
    });
    
    // 使用 Set 来管理选中的曲风
    setSelectedGenresSet(new Set(song.genres));
    
    // 转换现有的名称列表为选中项目（这里需要实现名称到项目的转换）
    // 由于当前API设计的限制，我们暂时使用名称创建虚拟项目
    const createVirtualItems = (names: string[], type: 'artist' | 'band' = 'artist'): ArtistBandItem[] => {
      return names.map((name, index) => ({
        id: `virtual-${type}-${index}-${name}`,
        name,
        bio: '从现有歌曲加载的数据，请重新搜索选择具体项目',
        type
      }));
    };
    
    setSelectedCreators(createVirtualItems(song.creators));
    setSelectedPerformers(createVirtualItems(song.performers));
    setSelectedLyricists(createVirtualItems(song.lyricists || []));
    setSelectedComposers(createVirtualItems(song.composers || []));
    setSelectedArrangers(createVirtualItems(song.arrangers || []));
    setSelectedInstrumentalists(createVirtualItems(song.instrumentalists || []));
    
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
      } else {
        newSet.add(genreId);
      }
      
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
      creators: selectedCreators.map(item => item.name),
      performers: selectedPerformers.map(item => item.name),
      lyricists: selectedLyricists.map(item => item.name),
      composers: selectedComposers.map(item => item.name),
      arrangers: selectedArrangers.map(item => item.name),
      instrumentalists: selectedInstrumentalists.map(item => item.name),
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
      releaseTime: new Date().toISOString().split('T')[0]
    });
    setSelectedGenresSet(new Set());
    setSelectedCreators([]);
    setSelectedPerformers([]);
    setSelectedLyricists([]);
    setSelectedComposers([]);
    setSelectedArrangers([]);
    setSelectedInstrumentalists([]);
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
              
              {/* 使用新的艺术家/乐队选择器 */}
              <ArtistBandSelector
                selectedItems={selectedCreators}
                onSelectionChange={setSelectedCreators}
                searchType="both"
                label="创作者"
                placeholder="搜索创作者（艺术家或乐队）..."
              />
              
              <ArtistBandSelector
                selectedItems={selectedPerformers}
                onSelectionChange={setSelectedPerformers}
                searchType="both"
                label="演唱者"
                placeholder="搜索演唱者（艺术家或乐队）..."
              />
              
              <div className="form-row">
                <div style={{ flex: 1, marginRight: '10px' }}>
                  <ArtistBandSelector
                    selectedItems={selectedLyricists}
                    onSelectionChange={setSelectedLyricists}
                    searchType="artist"
                    label="作词者"
                    placeholder="搜索作词者..."
                  />
                </div>
                
                <div style={{ flex: 1, marginLeft: '10px' }}>
                  <ArtistBandSelector
                    selectedItems={selectedComposers}
                    onSelectionChange={setSelectedComposers}
                    searchType="artist"
                    label="作曲者"
                    placeholder="搜索作曲者..."
                  />
                </div>
              </div>
              
              <div className="form-row">
                <div style={{ flex: 1, marginRight: '10px' }}>
                  <ArtistBandSelector
                    selectedItems={selectedArrangers}
                    onSelectionChange={setSelectedArrangers}
                    searchType="artist"
                    label="编曲者"
                    placeholder="搜索编曲者..."
                  />
                </div>
                
                <div style={{ flex: 1, marginLeft: '10px' }}>
                  <ArtistBandSelector
                    selectedItems={selectedInstrumentalists}
                    onSelectionChange={setSelectedInstrumentalists}
                    searchType="artist"
                    label="演奏者"
                    placeholder="搜索演奏者..."
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
                                e.preventDefault();
                                e.stopPropagation();
                                handleGenreToggle(genre.genreID);
                              }}
                            >
                              <input
                                type="checkbox"
                                checked={isSelected}
                                onChange={() => {}}
                                onMouseDown={(e) => {
                                  e.preventDefault();
                                  e.stopPropagation();
                                }}
                                onClick={(e) => {
                                  e.preventDefault();
                                  e.stopPropagation();
                                }}
                                readOnly
                                style={{ pointerEvents: 'none' }}
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
          <p><strong>智能选择:</strong> 现在可以通过搜索选择艺术家和乐队，避免重名问题。输入关键词即可看到详细信息。</p>
          <p><strong>创作者与演唱者:</strong> 支持选择艺术家或乐队，系统会显示类型和简介供您参考。</p>
          <p><strong>专业角色:</strong> 作词、作曲、编曲、演奏等角色通常由个人艺术家担任，因此只能选择艺术家。</p>
          <p><strong>批量管理:</strong> 可以选择多个艺术家/乐队，并随时添加或移除。</p>
          <p><strong>编辑模式:</strong> 编辑现有歌曲时，会显示现有数据，建议重新搜索选择以获得准确信息。</p>
        </div>
      </div>
    </div>
  );
};

export default SongManagement;