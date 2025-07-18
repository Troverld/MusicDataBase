import React from 'react';
import { useUserProfile } from './useUserProfile';
import { PageHeader, LoadingState, ErrorState, EmptyState } from './UserProfileUI';
import CharacteristicsCards from './CharacteristicsCards';
import DonutChart from './DonutChart';
import GenreDetailsList from './GenreDetailsList';
import './UserProfile.css';

const UserProfile: React.FC = () => {
  const {
    profile,
    loading,
    error,
    sortedData,
    total,
    characteristics,
    user,
    getColor,
    getTopGenres
  } = useUserProfile();

  // 加载状态
  if (loading) {
    return <LoadingState />;
  }

  // 错误状态
  if (error) {
    return <ErrorState error={error} />;
  }

  // 空状态
  if (!profile || sortedData.length === 0) {
    return <EmptyState />;
  }

  return (
    <div style={{ 
      background: '#f8f9fa', 
      minHeight: 'calc(100vh - 70px)', 
      padding: '40px 20px' 
    }}>
      <div style={{ maxWidth: '1200px', margin: '0 auto' }}>
        {/* 页面标题和描述 */}
        <PageHeader userAccount={user?.account} />
      </div>
      
      <div className="profile-content">
        {/* 特征卡片 */}
        {characteristics && (
          <CharacteristicsCards characteristics={characteristics} />
        )}

        {/* 主要内容区域 */}
        <div className="profile-main-new">
          {/* 左侧：可视化图表 */}
          <DonutChart 
            data={getTopGenres()} 
            total={total} 
            getColor={getColor} 
          />

          {/* 右侧：详细列表 */}
          <GenreDetailsList
            sortedData={sortedData}
            total={total}
            getColor={getColor}
            characteristics={characteristics}
          />
        </div>
      </div>
    </div>
  );
};

export default UserProfile;