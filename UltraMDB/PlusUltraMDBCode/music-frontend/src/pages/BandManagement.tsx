import React, { useState, useEffect } from 'react';
import { bandService } from '../services/band.service';
import { Band } from '../types';
import { ArtistBandItem, useArtistBand } from '../hooks/useArtistBand';
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
  
  const { convertIdsToNames, convertIdsToArtistBandItems } = useArtistBand();
  
  const [formData, setFormData] = useState({
    name: '',
    bio: ''
  });

  // 存储成员名称的状态（用于显示）
  const [memberNamesDisplay, setMemberNamesDisplay] = useState<{ [bandID: string]: string[] }>({});

  const handleSearch = async () => {
    if (!searchKeyword.trim()) {
      setBands([]);
      setMemberNamesDisplay({});
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
        // 使用新的转换函数
        const memberNames = await convertIdsToNames(band.members || []);
        memberNamesMap[band.bandID] = memberNames;
      } catch (error) {
        console.error(`Failed to load member names for band ${band.bandID}:`, error);
        // 如果转换失败，使用原始值（可能是名称）
        memberNamesMap[band.bandID] = band.members || [];
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
    
    // 将现有成员ID转换为选中项目
    try {
      const memberItems = await convertIdsToArtistBandItems(band.members || []);
      setSelectedMembers(memberItems);
      
      // 检查是否有无法解析的成员
      const unresolvedMembers = memberItems.filter(item => 
        item.id.startsWith('unresolved-')
      );
      
      if (unresolvedMembers.length > 0) {
        setError(`警告：有 ${unresolvedMembers.length} 个乐队成员无法准确匹配，可能是旧数据或已删除的成员。建议重新搜索选择所有成员。`);
      }
    } catch (error) {
      console.error('Failed to load member details for editing:', error);
      // 如果转换失败，创建占位符项目
      const placeholderMembers: ArtistBandItem[] = (band.members || []).map((memberId, index) => ({
        id: `placeholder-${memberId}`,
        name: memberNamesDisplay[band.bandID]?.[index] || memberId,
        bio: '编辑模式：请重新搜索选择此成员以确保数据准确性。',
        type: 'artist'
      }));
      setSelectedMembers(placeholderMembers);
      setError('编辑模式：无法获取成员详情，请重新搜索选择所有乐队成员。');
    }
    
    setShowModal(true);
  };

  // 验证选中的成员是否有问题
  const validateSelectedMembers = () => {
    const problemMembers = selectedMembers.filter(member => 
      member.id.startsWith('placeholder-') || 
      member.id.startsWith('unresolved-') || 
      member.id.startsWith('error-') ||
      member.id.startsWith('virtual-')
    );
    
    return problemMembers;
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');
    setSuccess('');

    if (!formData.name.trim() || !formData.bio.trim()) {
      setError('乐队名称和简介都不能为空');
      return;
    }

    // 验证是否有问题的选中成员
    const problemMembers = validateSelectedMembers();
    if (problemMembers.length > 0) {
      setError(`请重新选择以下有问题的乐队成员：${problemMembers.map(member => member.name).join(', ')}`);
      return;
    }

    // 获取成员 ID 列表
    const memberIDs = selectedMembers.map(member => member.id);

    try {
      if (editingBand) {
        // 直接调用后端 API，传递 ID 列表
        const updateData = {
          name: formData.name,
          bio: formData.bio,
          members: memberIDs // 直接传递 ID 列表给后端
        };
        
        const [success, message] = await bandService.updateBandWithIds(editingBand.bandID, updateData);
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
        // 创建新乐队，直接传递 ID 列表
        const createData = {
          name: formData.name,
          bio: formData.bio,
          memberIDs: memberIDs // 直接传递 ID 列表
        };
        
        const [bandID, message] = await bandService.createBandWithIds(createData);
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
        管理系统中的乐队信息，搜索、创建、编辑或删除乐队档案。通过智能选择器添加乐队成员，显示成员名称而不是ID，提供更好的用户体验。
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
                      ) : (band.members && band.members.length > 0) ? (
                        // 如果名称解析失败，显示原始值（可能是ID）
                        band.members.map((member, index) => (
                          <span key={index} className="chip" style={{ backgroundColor: '#f8d7da', color: '#721c24' }}>
                            {member} (未解析)
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
          <p><strong>智能显示:</strong> 乐队成员现在显示艺术家的名称而不是ID，提供更直观的用户体验。</p>
          <p><strong>搜索乐队:</strong> 在搜索框中输入乐队名称的关键词，支持模糊匹配。</p>
          <p><strong>智能成员选择:</strong> 通过搜索选择艺术家作为乐队成员，系统使用 ID 进行精确匹配。</p>
          <p><strong>创建乐队:</strong> 填写乐队名称、选择成员和详细简介。系统会自动验证成员的存在性。</p>
          <p><strong>编辑乐队:</strong> 编辑模式下会智能加载现有成员信息，如有数据问题会提示重新选择。</p>
          <p><strong>删除乐队:</strong> 删除操作不可撤销，请确保该乐队未被歌曲或专辑引用。</p>
          <p><strong>数据一致性:</strong> 系统使用艺术家 ID 管理乐队成员，同时在界面显示名称，确保数据准确性。</p>
        </div>
      </div>
    </div>
  );
};

export default BandManagement;