import React, { useState, useEffect } from 'react';
import { useLocation } from 'react-router-dom';
import { bandService } from '../../services/band.service';
import { Band } from '../../types';
import { useArtistBand } from '../../hooks/useArtistBand';
import { usePermissions } from '../../hooks/usePermissions';
import SearchSection from './SearchSection';
import BandList from './BandList';
import BandForm from './BandForm';

// 新增接口定义
interface BandMemberDetails {
  id: string;
  name: string;
}

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
  // 新增：存储成员详情（包含ID和名称）
  const [memberDetailsDisplay, setMemberDetailsDisplay] = useState<{ [bandID: string]: BandMemberDetails[] }>({});
  
  const { isAdmin } = usePermissions();
  const { convertIdsToNames, convertIdsToArtistBandItems } = useArtistBand();

  // 修改：加载乐队成员名称和详情
  const loadMemberNames = async (bandList: Band[]) => {
    const memberNamesMap: { [bandID: string]: string[] } = {};
    const memberDetailsMap: { [bandID: string]: BandMemberDetails[] } = {};
    
    for (const band of bandList) {
      try {
        // 保持原有逻辑：获取成员名称
        const memberNames = await convertIdsToNames(band.members || []);
        memberNamesMap[band.bandID] = memberNames;
        
        // 新增：获取成员详情
        const memberItems = await convertIdsToArtistBandItems(band.members || []);
        memberDetailsMap[band.bandID] = memberItems.map(item => ({
          id: item.id,
          name: item.name
        }));
      } catch (error) {
        console.error(`Failed to load member names for band ${band.bandID}:`, error);
        memberNamesMap[band.bandID] = band.members || [];
        memberDetailsMap[band.bandID] = [];
      }
    }
    
    setMemberNamesDisplay(memberNamesMap);
    setMemberDetailsDisplay(memberDetailsMap);
  };

  // 获取所有乐队
  const fetchAllBands = async () => {
    setLoading(true);
    setError('');
    
    try {
      // 使用空字符串搜索来获取所有乐队
      const [bandIDs, message] = await bandService.searchBandByName('');
      if (bandIDs && bandIDs.length > 0) {
        const bandDetails = await bandService.getBandsByIds(bandIDs);
        setBands(bandDetails);
        await loadMemberNames(bandDetails);
      } else {
        setBands([]);
        setMemberNamesDisplay({});
        setMemberDetailsDisplay({});
      }
    } catch (err: any) {
      setError(err.message || '获取乐队列表失败');
      setBands([]);
      setMemberNamesDisplay({});
      setMemberDetailsDisplay({});
    } finally {
      setLoading(false);
    }
  };

  // 页面加载时获取所有乐队
  useEffect(() => {
    fetchAllBands();
  }, []);

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

  const handleSearch = async () => {
    if (!searchKeyword.trim()) {
      // 如果搜索框为空，显示所有乐队
      await fetchAllBands();
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
        setMemberDetailsDisplay({});
        setError(message || '未找到匹配的乐队');
      }
    } catch (err: any) {
      setError(err.message || '搜索失败');
      setBands([]);
      setMemberNamesDisplay({});
      setMemberDetailsDisplay({});
    } finally {
      setLoading(false);
    }
  };

  const handleDelete = async (bandID: string) => {
    if (!window.confirm('确定要删除这个乐队吗？此操作不可撤销，可能会影响相关的歌曲和专辑。')) return;
    
    try {
      const [success, message] = await bandService.deleteBand(bandID);
      if (success) {
        // 如果当前有搜索关键词，重新搜索；否则重新获取所有乐队
        if (searchKeyword.trim()) {
          await handleSearch();
        } else {
          await fetchAllBands();
        }
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

  const handleFormSuccess = async (message: string) => {
    setSuccess(message);
    setShowModal(false);
    setEditingBand(null);
    
    // 刷新乐队列表
    if (searchKeyword.trim()) {
      await handleSearch();
    } else {
      await fetchAllBands();
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
    <div style={{ 
      background: '#f8f9fa', 
      minHeight: 'calc(100vh - 70px)', 
      padding: '40px 20px' 
    }}>
      <div style={{ maxWidth: '1200px', margin: '0 auto' }}>
        {/* 页面标题和描述 */}
        <div style={{ 
          textAlign: 'center', 
          marginBottom: '40px',
          animation: 'fadeIn 0.6s ease-out'
        }}>
          <h1 style={{ 
            fontSize: '36px', 
            fontWeight: '700',
            background: 'linear-gradient(135deg, #667eea 0%, #764ba2 100%)',
            WebkitBackgroundClip: 'text',
            WebkitTextFillColor: 'transparent',
            backgroundClip: 'text',
            marginBottom: '12px'
          }}>
            乐队管理
          </h1>
          <p style={{ 
            color: '#6b7280', 
            fontSize: '18px',
            maxWidth: '600px',
            margin: '0 auto',
            lineHeight: '1.6'
          }}>
            管理系统中的乐队信息，搜索、创建、编辑或删除乐队档案
          </p>
        </div>
        
        {/* 提示信息 */}
        {error && (
          <div style={{
            background: '#fee2e2',
            border: '1px solid #fecaca',
            borderRadius: '12px',
            padding: '16px 20px',
            marginBottom: '24px',
            color: '#dc2626',
            display: 'flex',
            alignItems: 'center',
            gap: '12px',
            animation: 'slideDown 0.3s ease-out'
          }}>
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
              <circle cx="12" cy="12" r="10"/>
              <line x1="12" y1="8" x2="12" y2="12"/>
              <line x1="12" y1="16" x2="12.01" y2="16"/>
            </svg>
            {error}
          </div>
        )}
        
        {success && (
          <div style={{
            background: '#d1fae5',
            border: '1px solid #a7f3d0',
            borderRadius: '12px',
            padding: '16px 20px',
            marginBottom: '24px',
            color: '#059669',
            display: 'flex',
            alignItems: 'center',
            gap: '12px',
            animation: 'slideDown 0.3s ease-out'
          }}>
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
              <path d="M22 11.08V12a10 10 0 1 1-5.93-9.14"/>
              <polyline points="22 4 12 14.01 9 11.01"/>
            </svg>
            {success}
          </div>
        )}
        
        {/* 搜索栏 */}
        <div style={{ marginBottom: '32px' }}>
          <SearchSection
            searchKeyword={searchKeyword}
            onSearchKeywordChange={setSearchKeyword}
            onSearch={handleSearch}
            loading={loading}
          />
        </div>
        
        {/* 创建按钮或权限提示 */}
        <div style={{ marginBottom: '32px', textAlign: 'center' }}>
          {isAdmin ? (
            <button 
              onClick={() => { resetForm(); setShowModal(true); }}
              style={{
                background: 'linear-gradient(135deg, #667eea 0%, #764ba2 100%)',
                color: 'white',
                border: 'none',
                padding: '14px 32px',
                borderRadius: '12px',
                fontSize: '16px',
                fontWeight: '600',
                cursor: 'pointer',
                display: 'inline-flex',
                alignItems: 'center',
                gap: '8px',
                boxShadow: '0 4px 14px rgba(102, 126, 234, 0.3)',
                transition: 'all 0.3s ease'
              }}
              onMouseEnter={(e) => {
                e.currentTarget.style.transform = 'translateY(-2px)';
                e.currentTarget.style.boxShadow = '0 6px 20px rgba(102, 126, 234, 0.4)';
              }}
              onMouseLeave={(e) => {
                e.currentTarget.style.transform = 'translateY(0)';
                e.currentTarget.style.boxShadow = '0 4px 14px rgba(102, 126, 234, 0.3)';
              }}
            >
              <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                <line x1="12" y1="5" x2="12" y2="19"/>
                <line x1="5" y1="12" x2="19" y2="12"/>
              </svg>
              创建新乐队
            </button>
          ) : (
            <div style={{
              background: '#fef3c7',
              border: '1px solid #fde68a',
              borderRadius: '12px',
              padding: '16px 24px',
              color: '#92400e',
              display: 'inline-flex',
              alignItems: 'center',
              gap: '12px'
            }}>
              <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                <circle cx="12" cy="12" r="10"/>
                <line x1="12" y1="8" x2="12" y2="12"/>
                <line x1="12" y1="16" x2="12.01" y2="16"/>
              </svg>
              您没有创建乐队的权限，仅能查看和编辑您有权限的乐队
            </div>
          )}
        </div>
        
        {/* 乐队列表 - 修改：传递 memberDetailsDisplay */}
        <BandList
          bands={bands}
          loading={loading}
          memberNamesDisplay={memberNamesDisplay}
          memberDetailsDisplay={memberDetailsDisplay}
          onEdit={handleEdit}
          onDelete={handleDelete}
          searchKeyword={searchKeyword}
        />
      </div>
      
      {/* 模态框 */}
      {showModal && (
        <BandForm
          editingBand={editingBand}
          memberNamesDisplay={memberNamesDisplay}
          onSuccess={handleFormSuccess}
          onError={handleFormError}
          onClose={() => setShowModal(false)}
        />
      )}
    </div>
  );
};

export default BandManagement;