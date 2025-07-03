import React, { useState, useEffect } from 'react';
import { bandService } from '../services/band.service';
import { Band } from '../types';
import { ArtistBandItem } from '../hooks/useArtistBand';
import ArtistBandSelector from '../components/ArtistBandSelector';

const BandManagement: React.FC = () => {
  const [bands, setBands] = useState<Band[]>([]);
  const [searchKeyword, setSearchKeyword] = useState('');
  const [showModal, setShowModal] = useState(false);
  const [editingBand, setEditingBand] = useState<Band | null>(null);
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');
  const [loading, setLoading] = useState(false);
  
  // 乐队成员选择状态
  const [selectedMembers, setSelectedMembers] = useState<ArtistBandItem[]>([]);
  
  const [formData, setFormData] = useState({
    name: '',
    bio: ''
  });

  // 存储成员名称的状态（用于显示）
  const [memberNamesDisplay, setMemberNamesDisplay] = useState<{ [bandID: string]: string[] }>({});

  const handleSearch = async () => {
    if (!searchKeyword.trim()) {
      setBands([]);
      return;
    }
    
    setLoading(true);
    setError('');
    
    try {
      const [bandIDs, message] = await bandService.searchBandByName(searchKeyword);
      if (bandIDs && bandIDs.length > 0) {
        // 获取乐队的详细信息
        const bandDetails = await bandService.getBandsByIds(bandIDs);
        setBands(bandDetails);
        
        // 获取所有乐队成员的名称
        await loadMemberNames(bandDetails);
        
        if (bandDetails.length === 0) {
          setError('未找到匹配的乐队详情');
        }
      } else {
        setBands([]);
        setMemberNamesDisplay({});
        setError(message || '未找到匹配的乐队');
      }
    } catch (err: any) {
      setError(err.message || '搜索失败');
      setBands([]);
      setMemberNamesDisplay({});
    } finally {
      setLoading(false);
    }
  };

  // 加载乐队成员名称
  const loadMemberNames = async (bandList: Band[]) => {
    const memberNamesMap: { [bandID: string]: string[] } = {};
    
    for (const band of bandList) {
      try {
        const memberNames = await bandService.convertArtistIdsToNames(band.members);
        memberNamesMap[band.bandID] = memberNames;
      } catch (error) {
        console.error(`Failed to load member names for band ${band.bandID}:`, error);
        memberNamesMap[band.bandID] = band.members; // 如果转换失败，显示ID
      }
    }
    
    setMemberNamesDisplay(memberNamesMap);
  };

  const handleDelete = async (bandID: string) => {
    if (!window.confirm('确定要删除这个乐队吗？此操作不可撤销，可能会影响相关的歌曲和专辑。')) return;
    
    try {
      const [success, message] = await bandService.deleteBand(bandID);
      if (success) {
        setBands(bands.filter(b => b.bandID !== bandID));
        // 清理成员名称显示
        const newMemberNamesDisplay = { ...memberNamesDisplay };
        delete newMemberNamesDisplay[bandID];
        setMemberNamesDisplay(newMemberNamesDisplay);
        setSuccess('乐队删除成功');
      } else {
        setError(message);
      }
    } catch (err: any) {
      setError(err.message);
    }
  };

  const handleEdit = async (band: Band) => {
    setEditingBand(band);
    setFormData({
      name: band.name,
      bio: band.bio
    });
    
    // 获取成员名称并转换为选中项目
    try {
      const memberNames = await bandService.convertArtistIdsToNames(band.members);
      // 创建虚拟的艺术家项目用于编辑
      const virtualMembers: ArtistBandItem[] = band.members.map((memberId, index) => ({
        id: memberId,
        name: memberNames[index] || memberId,
        bio: '从现有乐队加载的成员数据，请重新搜索选择具体艺术家',
        type: 'artist'
      }));
      setSelectedMembers(virtualMembers);
    } catch (error) {
      console.error('Failed to load member names for editing:', error);
      // 如果转换失败，使用ID创建虚拟项目
      const virtualMembers: ArtistBandItem[] = band.members.map((memberId) => ({
        id: memberId,
        name: memberId,
        bio: '从现有乐队加载的成员数据，请重新搜索选择具体艺术家',
        type: 'artist'
      }));
      setSelectedMembers(virtualMembers);
    }
    
    setShowModal(true);
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');
    setSuccess('');

    if (!formData.name.trim() || !formData.bio.trim()) {
      setError('乐队名称和简介都不能为空');
      return;
    }

    // 获取成员名称
    const memberNames = selectedMembers.map(member => member.name);

    try {
      if (editingBand) {
        const [success, message] = await bandService.updateBand(editingBand.bandID, {
          name: formData.name,
          bio: formData.bio,
          memberNames: memberNames
        });
        if (success) {
          setSuccess('乐队信息更新成功');
          setShowModal(false);
          // 刷新乐队列表
          if (searchKeyword.trim()) {
            handleSearch();
          }
        } else {
          setError(message);
        }
      } else {
        const [bandID, message] = await bandService.createBand({
          name: formData.name,
          bio: formData.bio,
          memberNames: memberNames
        });
        if (bandID) {
          setSuccess(`乐队创建成功！乐队ID: ${bandID}`);
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
    setSelectedMembers([]);
    setEditingBand(null);
  };

  const clearMessages = () => {
    setError('');
    setSuccess('');
  };

  // 当搜索关键词变化时清除消息
  useEffect(() => {
    clearMessages();
  }, [searchKeyword]);

  return (
    <div>
      <h1>乐队管理</h1>
      <p style={{ color: '#666', marginBottom: '30px', fontSize: '16px' }}>
        管理系统中的乐队信息，搜索、创建、编辑或删除乐队档案。通过智能选择器添加乐队成员，避免重名问题。
      </p>
      
      {error && <div className="error-message">{error}</div>}
      {success && <div className="success-message">{success}</div>}
      
      <div className="search-box">
        <div style={{ display: 'flex', gap: '10px', alignItems: 'center' }}>
          <input
            type="text"
            placeholder="搜索乐队..."
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
        创建新乐队
      </button>
      
      {loading ? (
        <div style={{ textAlign: 'center', padding: '40px' }}>
          <p>正在加载乐队信息...</p>
        </div>
      ) : (
        <div>
          {bands.length === 0 ? (
            <div className="empty-state">
              <p>未找到乐队</p>
              <p style={{ fontSize: '14px', color: '#999', marginTop: '10px' }}>
                {searchKeyword.trim() ? '请尝试其他搜索关键词' : '请使用搜索功能查找乐队'}
              </p>
            </div>
          ) : (
            <div className="song-list">
              {bands.map((band) => (
                <div key={band.bandID} className="song-item">
                  <h3>{band.name}</h3>
                  
                  <div style={{ marginBottom: '15px' }}>
                    <p><strong>乐队ID:</strong> 
                      <span style={{ 
                        fontFamily: 'monospace', 
                        backgroundColor: '#f8f9fa', 
                        padding: '2px 6px', 
                        borderRadius: '3px',
                        marginLeft: '8px',
                        fontSize: '12px'
                      }}>
                        {band.bandID}
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
                      {band.bio || '暂无简介'}
                    </div>
                  </div>

                  <div style={{ marginBottom: '15px' }}>
                    <strong>乐队成员:</strong>
                    <div style={{ marginTop: '5px' }}>
                      {memberNamesDisplay[band.bandID] && memberNamesDisplay[band.bandID].length > 0 ? (
                        memberNamesDisplay[band.bandID].map((memberName, index) => (
                          <span key={index} className="chip" style={{ backgroundColor: '#fff3cd', color: '#856404' }}>
                            {memberName}
                          </span>
                        ))
                      ) : (
                        <span className="chip" style={{ backgroundColor: '#f8f9fa', color: '#6c757d' }}>
                          暂无成员
                        </span>
                      )}
                    </div>
                  </div>

                  {band.managers && band.managers.length > 0 && (
                    <div style={{ marginBottom: '15px' }}>
                      <strong>管理者:</strong>
                      <div style={{ marginTop: '5px' }}>
                        {band.managers.map((manager, index) => (
                          <span key={index} className="chip" style={{ backgroundColor: '#e3f2fd', color: '#1976d2' }}>
                            {manager}
                          </span>
                        ))}
                      </div>
                    </div>
                  )}

                  <div className="song-actions">
                    <button className="btn btn-secondary" onClick={() => handleEdit(band)}>
                      编辑
                    </button>
                    <button className="btn btn-danger" onClick={() => handleDelete(band.bandID)}>
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
              <h2>{editingBand ? '编辑乐队' : '创建新乐队'}</h2>
              <button onClick={() => setShowModal(false)}>×</button>
            </div>
            
            <form onSubmit={handleSubmit}>
              <div className="form-group">
                <label>乐队名称*</label>
                <input
                  type="text"
                  value={formData.name}
                  onChange={(e) => setFormData({...formData, name: e.target.value})}
                  placeholder="请输入乐队名称"
                  required
                />
              </div>
              
              {/* 使用新的艺术家选择器 */}
              <ArtistBandSelector
                selectedItems={selectedMembers}
                onSelectionChange={setSelectedMembers}
                searchType="artist"
                label="乐队成员"
                placeholder="搜索艺术家作为乐队成员..."
              />
              
              <div className="form-group">
                <label>乐队简介*</label>
                <textarea
                  value={formData.bio}
                  onChange={(e) => setFormData({...formData, bio: e.target.value})}
                  placeholder="请输入乐队简介、成立背景、风格特色等..."
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
                  {editingBand ? '更新乐队' : '创建乐队'}
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
        <h3 style={{ marginBottom: '15px', color: '#495057' }}>💡 乐队管理提示</h3>
        <div style={{ fontSize: '14px', color: '#6c757d', lineHeight: '1.6' }}>
          <p><strong>搜索乐队:</strong> 在搜索框中输入乐队名称的关键词，支持模糊匹配。</p>
          <p><strong>智能成员选择:</strong> 通过搜索选择艺术家作为乐队成员，可以查看每个艺术家的详细信息避免重名。</p>
          <p><strong>创建乐队:</strong> 填写乐队名称、选择成员和详细简介。系统会自动验证成员的存在性。</p>
          <p><strong>编辑乐队:</strong> 点击"编辑"按钮修改乐队的名称、成员和简介信息。</p>
          <p><strong>删除乐队:</strong> 删除操作不可撤销，请确保该乐队未被歌曲或专辑引用。</p>
          <p><strong>成员管理:</strong> 可以随时添加或移除乐队成员，支持查看每个成员的详细信息。</p>
          <p><strong>权限说明:</strong> 乐队管理功能需要管理员权限，请确保您有足够的操作权限。</p>
        </div>
      </div>
    </div>
  );
};

export default BandManagement;