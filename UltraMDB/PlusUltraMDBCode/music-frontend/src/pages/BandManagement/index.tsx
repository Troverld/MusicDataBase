import React from 'react';
import { usePermissions } from '../../hooks/usePermissions';
import SearchSection from './SearchSection';
import BandList from './BandList';
import BandForm from './BandForm';
import { useBandManagement } from './useBandManagement';
import { PageHeader, Message, CreateButton } from './BandManagementUI';

const BandManagement: React.FC = () => {
  const { isAdmin } = usePermissions();
  
  const {
    // 状态
    bands,
    searchKeyword,
    showModal,
    editingBand,
    error,
    success,
    loading,
    memberNamesDisplay,
    memberDetailsDisplay,
    
    // 方法
    setSearchKeyword,
    setShowModal,
    handleSearch,
    handleDelete,
    handleEdit,
    handleFormSuccess,
    handleFormError,
    resetForm
  } = useBandManagement();

  return (
    <div style={{ 
      background: '#f8f9fa', 
      minHeight: 'calc(100vh - 70px)', 
      padding: '40px 20px' 
    }}>
      <div style={{ maxWidth: '1200px', margin: '0 auto' }}>
        {/* 页面标题和描述 */}
        <PageHeader 
          title="乐队管理"
          description="管理系统中的乐队信息，搜索、创建、编辑或删除乐队档案"
        />
        
        {/* 提示信息 */}
        {error && <Message message={error} type="error" />}
        {success && <Message message={success} type="success" />}
        
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
          <CreateButton 
            isAdmin={isAdmin} 
            onClick={() => { resetForm(); setShowModal(true); }} 
          />
        </div>
        
        {/* 乐队列表 */}
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