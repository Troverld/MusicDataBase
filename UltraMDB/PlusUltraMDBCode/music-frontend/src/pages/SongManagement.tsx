import React, { useState, useEffect, useRef } from 'react';
import { musicService } from '../services/music.service';
import { Song, CreatorID_Type } from '../types';
import SongList from '../components/SongList';
import { useGenres } from '../hooks/useGenres';
import { useArtistBand, ArtistBandItem } from '../hooks/useArtistBand';
import ArtistBandSelector from '../components/ArtistBandSelector';
import { usePermissions } from '../hooks/usePermissions';

const SongManagement: React.FC = () => {
  const [songs, setSongs] = useState<Song[]>([]);
  const [searchKeyword, setSearchKeyword] = useState('');
  const [showModal, setShowModal] = useState(false);
  const [editingSong, setEditingSong] = useState<Song | null>(null);
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');
  const [loading, setLoading] = useState(false);
  const [dropdownOpen, setDropdownOpen] = useState(false);
  
  // 权限检查
  const { isUser, isAdmin } = usePermissions();
  
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
  const { getArtistBandsByIds, searchArtistBand } = useArtistBand();
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

  // 将CreatorID_Type数组或字符串ID数组转换为选中项目
  const convertCreatorsToSelectedItems = async (creators: CreatorID_Type[] | string[]): Promise<ArtistBandItem[]> => {
    if (!creators || creators.length === 0) return [];
    
    const results: ArtistBandItem[] = [];
    
    for (const creator of creators) {
      try {
        if (typeof creator === 'object' && 'id' in creator && 'creatorType' in creator) {
          // 新格式：CreatorID_Type
          const creatorItem = await getArtistBandsByIds([{
            id: creator.id, 
            type: creator.creatorType as 'artist' | 'band'
          }]);
          
          if (creatorItem.length > 0) {
            results.push(creatorItem[0]);
          } else {
            // 如果找不到，创建警告项目
            results.push({
              id: `not-found-${creator.id}`,
              name: creator.id,
              bio: `警告：无法找到ID为"${creator.id}"的${creator.creatorType === 'artist' ? '艺术家' : '乐队'}，可能是已删除的项目。请重新搜索选择。`,
              type: creator.creatorType as 'artist' | 'band'
            });
          }
        } else if (typeof creator === 'string') {
          // 旧格式：字符串ID，需要猜测类型
          const id = creator.trim();
          if (!id) continue;
          
          let found = false;
          
          // 首先尝试作为艺术家ID获取
          try {
            const artistItems = await getArtistBandsByIds([{id, type: 'artist'}]);
            if (artistItems.length > 0) {
              results.push(artistItems[0]);
              found = true;
            }
          } catch (error) {
            // 继续尝试乐队
          }
          
          // 如果不是艺术家，尝试作为乐队ID获取
          if (!found) {
            try {
              const bandItems = await getArtistBandsByIds([{id, type: 'band'}]);
              if (bandItems.length > 0) {
                results.push(bandItems[0]);
                found = true;
              }
            } catch (error) {
              // 继续
            }
          }
          
          // 如果都找不到，可能是旧数据中存储的是名称，尝试搜索
          if (!found) {
            try {
              const searchResults = await searchArtistBand(id, 'both');
              const exactMatch = searchResults.find(item => 
                item.name.toLowerCase() === id.toLowerCase()
              );
              
              if (exactMatch) {
                results.push(exactMatch);
                found = true;
              }
            } catch (error) {
              // 继续
            }
          }
          
          // 如果还是找不到，创建一个警告项目
          if (!found) {
            results.push({
              id: `not-found-${id}`,
              name: id,
              bio: `警告：无法找到ID为"${id}"的艺术家或乐队，可能是旧数据或已删除的项目。请重新搜索选择。`,
              type: 'artist'
            });
          }
        }
      } catch (error) {
        console.warn(`Failed to convert creator to item:`, creator, error);
        // 创建一个错误项目
        const fallbackId = typeof creator === 'object' && 'id' in creator ? creator.id : creator.toString();
        results.push({
          id: `error-${fallbackId}`,
          name: fallbackId,
          bio: `错误：处理"${fallbackId}"时发生错误，请重新搜索选择`,
          type: 'artist'
        });
      }
    }
    
    return results;
  };

  // 将字符串ID数组转换为选中项目（用于处理传统的字符串数组字段）
  const convertIdsToSelectedItems = async (ids: string[]): Promise<ArtistBandItem[]> => {
    return convertCreatorsToSelectedItems(ids);
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
    
    // 转换现有的ID列表为选中项目 - 使用新的转换函数处理不同的数据格式
    try {
      const [creators, performers, lyricists, composers, arrangers, instrumentalists] = await Promise.all([
        convertCreatorsToSelectedItems(song.creators || []), // 使用新的转换函数处理 CreatorID_Type[]
        convertIdsToSelectedItems(song.performers || []),
        convertIdsToSelectedItems(song.lyricists || []),
        convertIdsToSelectedItems(song.composers || []),
        convertIdsToSelectedItems(song.arrangers || []),
        convertIdsToSelectedItems(song.instrumentalists || [])
      ]);
      
      setSelectedCreators(creators);
      setSelectedPerformers(performers);
      setSelectedLyricists(lyricists);
      setSelectedComposers(composers);
      setSelectedArrangers(arrangers);
      setSelectedInstrumentalists(instrumentalists);
      
      // 检查是否有无法找到的项目
      const allItems = [...creators, ...performers, ...lyricists, ...composers, ...arrangers, ...instrumentalists];
      const notFoundItems = allItems.filter(item => 
        item.id.startsWith('not-found-') || item.id.startsWith('error-')
      );
      
      if (notFoundItems.length > 0) {
        setError(`警告：有 ${notFoundItems.length} 个创作者信息无法准确匹配，可能是旧数据或已删除的项目。请检查并重新选择相关项目。`);
      }
      
    } catch (error) {
      console.error('Failed to convert song data for editing:', error);
      setError('加载歌曲编辑数据时发生错误，请重新选择所有创作者信息。');
      
      // 如果转换失败，清空所有选择
      setSelectedCreators([]);
      setSelectedPerformers([]);
      setSelectedLyricists([]);
      setSelectedComposers([]);
      setSelectedArrangers([]);
      setSelectedInstrumentalists([]);
    }
    
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

  // 验证选中的项目是否有问题
  const validateSelectedItems = () => {
    const allItems = [
      ...selectedCreators,
      ...selectedPerformers, 
      ...selectedLyricists,
      ...selectedComposers,
      ...selectedArrangers,
      ...selectedInstrumentalists
    ];
    
    const problemItems = allItems.filter(item => 
      item.id.startsWith('not-found-') || 
      item.id.startsWith('error-') ||
      item.id.startsWith('virtual-')
    );
    
    return problemItems;
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');
    setSuccess('');

    // 验证是否有问题的选中项目
    const problemItems = validateSelectedItems();
    if (problemItems.length > 0) {
      setError(`请重新选择以下有问题的创作者信息：${problemItems.map(item => item.name).join(', ')}`);
      return;
    }

    const songData = {
      name: formData.name,
      releaseTime: new Date(formData.releaseTime).getTime(),
      // 直接传递 ArtistBandItem[] 给 service，保留类型信息
      creators: selectedCreators, // ArtistBandItem[]
      performers: selectedPerformers, // ArtistBandItem[]
      lyricists: selectedLyricists, // ArtistBandItem[]
      composers: selectedComposers, // ArtistBandItem[]
      arrangers: selectedArrangers, // ArtistBandItem[]
      instrumentalists: selectedInstrumentalists, // ArtistBandItem[]
      genres: Array.from(selectedGenresSet) // 曲风仍然是 string[]
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

  // 检查用户是否可以上传歌曲
  const canUploadSongs = isUser || isAdmin;

  return (
    <div>
      <h1>歌曲管理</h1>
      <p style={{ color: '#666', marginBottom: '30px', fontSize: '16px' }}>
        管理系统中的歌曲信息，搜索现有歌曲，查看详细信息。
        {canUploadSongs ? '您可以上传新歌曲并编辑您有权限的歌曲。' : '您可以搜索和查看歌曲信息。'}
      </p>
      
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
          <p><strong>权限说明:</strong> 只有注册用户可以上传歌曲，用户只能编辑自己上传的歌曲，管理员拥有所有权限。</p>
          <p><strong>智能显示:</strong> 歌曲列表现在显示艺术家和乐队的名称，而不是ID，提供更好的用户体验。</p>
          <p><strong>智能选择:</strong> 通过搜索选择艺术家和乐队，系统会自动使用 ID 进行匹配，避免重名问题。</p>
          <p><strong>创作者与演唱者:</strong> 支持选择艺术家或乐队，系统会显示类型和简介供您参考。</p>
          <p><strong>专业角色:</strong> 作词、作曲、编曲、演奏等角色通常由个人艺术家担任，因此只能选择艺术家。</p>
          <p><strong>编辑模式:</strong> 编辑现有歌曲时，系统会智能识别ID并转换为对应的名称显示。</p>
          <p><strong>数据一致性:</strong> 系统使用 ID 而不是名称传递数据，确保与后端 API 的完美对接。</p>
        </div>
      </div>
    </div>
  );
};

export default SongManagement;