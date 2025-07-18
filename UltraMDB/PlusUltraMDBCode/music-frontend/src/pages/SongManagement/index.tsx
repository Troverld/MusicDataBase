import React from 'react';
import SongList from '../../components/SongList';
import SearchSection from './SearchSection';
import SongForm from './SongForm';
import Pagination from '../../components/Pagination';
import { useSongManagement } from './useSongManagement';
import { PageHeader, Message, UploadButton, LoadingState } from './SongManagementUI';

const SongManagement: React.FC = () => {
  const {
    // 状态
    songs,
    searchKeyword,
    showModal,
    editingSong,
    error,
    success,
    loading,
    returnInfo,
    currentPage,
    totalPages,
    isSearchMode,
    canUploadSongs,
    
    // 方法
    setSearchKeyword,
    setShowModal,
    handleSearch,
    handlePageChange,
    handleDelete,
    handleEdit,
    handleFormSuccess,
    handleFormError,
    handleCloseModal,
    resetForm
  } = useSongManagement();

  return (
    <div style={{ 
      background: '#f8f9fa', 
      minHeight: 'calc(100vh - 70px)', 
      padding: '40px 20px' 
    }}>
      <div style={{ maxWidth: '1200px', margin: '0 auto' }}>
        {/* 页面标题和描述 */}
        <PageHeader returnInfo={returnInfo} />

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
          <UploadButton 
            canUploadSongs={canUploadSongs} 
            onClick={() => { resetForm(); setShowModal(true); }} 
          />
        </div>

        {/* 歌曲列表 */}
        {loading ? (
          <LoadingState />
        ) : (
          <>
            <SongList songs={songs} onEdit={handleEdit} onDelete={handleDelete} />
            
            {/* 分页组件 - 只在搜索模式下显示 */}
            {isSearchMode && totalPages > 0 && (
              <Pagination
                currentPage={currentPage}
                totalPages={totalPages}
                onPageChange={handlePageChange}
                loading={loading}
              />
            )}
          </>
        )}
      </div>
      
      {/* 模态框 */}
      {showModal && canUploadSongs && (
        <SongForm
          editingSong={editingSong}
          onSuccess={handleFormSuccess}
          onError={handleFormError}
          onClose={handleCloseModal}
        />
      )}
    </div>
  );
};

export default SongManagement;
