import React, { useState, useEffect } from 'react';
import { useLocation } from 'react-router-dom';
import { bandService } from '../../services/band.service';
import { Band } from '../../types';
import { useArtistBand } from '../../hooks/useArtistBand';
import { usePermissions } from '../../hooks/usePermissions';
import SearchSection from './SearchSection';
import BandList from './BandList';
import BandForm from './BandForm';

const BandManagement: React.FC = () => {
  const location = useLocation();
  const [bands, setBands] = useState<Band[]>([]);
  const [searchKeyword, setSearchKeyword] = useState('');
  const [showModal, setShowModal] = useState(false);
  const [editingBand, setEditingBand] = useState<Band | null>(null);
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');
  const [loading, setLoading] = useState(false);
  const [memberNamesDisplay, setMemberNamesDisplay] = useState<{ [bandID: string]: string[] }>({});
  
  const { isAdmin } = usePermissions();
  const { convertIdsToNames } = useArtistBand();

  // 检查是否从其他页面传递了编辑乐队的数据
  useEffect(() => {
    if (location.state?.editBand) {
      const bandToEdit = location.state.editBand as Band;
      setEditingBand(bandToEdit);
      setShowModal(true);
      // 清除 location state 以避免重复处理
      window.history.replaceState({}, document.title);
    }
  }, [location.state]);

  // 加载乐队成员名称
  const loadMemberNames = async (bandList: Band[]) => {
    const memberNamesMap: { [bandID: string]: string[] } = {};
    
    for (const band of bandList) {
      try {
        const memberNames = await convertIdsToNames(band.members || []);
        memberNamesMap[band.bandID] = memberNames;
      } catch (error) {
        console.error(`Failed to load member names for band ${band.bandID}:`, error);
        memberNamesMap[band.bandID] = band.members || [];
      }
    }
    
    setMemberNamesDisplay(memberNamesMap);
  };

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
        const bandDetails = await bandService.getBandsByIds(bandIDs);
        setBands(bandDetails);
        
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

  const handleDelete = async (bandID: string) => {
    if (!window.confirm('确定要删除这个乐队吗？此操作不可撤销，可能会影响相关的歌曲和专辑。')) return;
    
    try {
      const [success, message] = await bandService.deleteBand(bandID);
      if (success) {
        setBands(bands.filter(b => b.bandID !== bandID));
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

  const handleEdit = (band: Band) => {
    setEditingBand(band);
    setShowModal(true);
  };

  const handleFormSuccess = (message: string) => {
    setSuccess(message);
    setShowModal(false);
    setEditingBand(null);
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
    setEditingBand(null);
  };

  useEffect(() => {
    clearMessages();
  }, [searchKeyword]);

  return (
    <div>
      <h1>乐队管理</h1>
      <p style={{ color: '#666', marginBottom: '30px', fontSize: '16px' }}>
        管理系统中的乐队信息，搜索、创建、编辑或删除乐队档案。点击乐队名称可查看详细信息。通过智能选择器添加乐队成员，显示成员名称而不是ID，提供更好的用户体验。
      </p>
      
      {error && <div className="error-message">{error}</div>}
      {success && <div className="success-message">{success}</div>}
      
      <SearchSection
        searchKeyword={searchKeyword}
        onSearchKeywordChange={setSearchKeyword}
        onSearch={handleSearch}
        loading={loading}
      />
      
      {isAdmin ? (
        <button 
          className="btn btn-primary" 
          onClick={() => { resetForm(); setShowModal(true); }}
          style={{ marginBottom: '20px' }}
        >
          创建新乐队
        </button>
      ) : (
        <div className="permission-warning" style={{ marginBottom: '20px' }}>
          ⚠️ 您没有创建乐队的权限，仅能查看和编辑您有权限的乐队
        </div>
      )}
      
      <BandList
        bands={bands}
        loading={loading}
        memberNamesDisplay={memberNamesDisplay}
        onEdit={handleEdit}
        onDelete={handleDelete}
        searchKeyword={searchKeyword}
      />
      
      {showModal && (
        <BandForm
          editingBand={editingBand}
          memberNamesDisplay={memberNamesDisplay}
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
        <h3 style={{ marginBottom: '15px', color: '#495057' }}>💡 乐队管理提示</h3>
        <div style={{ fontSize: '14px', color: '#6c757d', lineHeight: '1.6' }}>
          <p><strong>查看详情:</strong> 点击乐队名称可以查看完整的乐队信息页面，包括成员详情。</p>
          <p><strong>智能显示:</strong> 乐队成员现在显示艺术家的名称而不是ID，提供更直观的用户体验。</p>
          <p><strong>权限管理:</strong> 只有管理员可以创建和删除乐队，乐队管理者可以编辑对应乐队的信息。</p>
          <p><strong>搜索乐队:</strong> 在搜索框中输入乐队名称的关键词，支持模糊匹配。</p>
          <p><strong>智能成员选择:</strong> 通过搜索选择艺术家作为乐队成员，系统使用 ID 进行精确匹配。</p>
          <p><strong>创建乐队:</strong> 填写乐队名称、选择成员和详细简介。系统会自动验证成员的存在性。</p>
          <p><strong>编辑乐队:</strong> 编辑模式下会智能加载现有成员信息，如有数据问题会提示重新选择。</p>
          <p><strong>删除乐队:</strong> 删除操作不可撤销，仅管理员可执行，请确保该乐队未被歌曲或专辑引用。</p>
          <p><strong>数据一致性:</strong> 系统使用艺术家 ID 管理乐队成员，同时在界面显示名称，确保数据准确性。</p>
        </div>
      </div>
    </div>
  );
};

export default BandManagement;