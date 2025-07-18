import React from 'react';
import { useDashboard } from './useDashboard';
import {
  DashboardLoading,
  DashboardHeader,
  TasteSection,
  RecommendationsSection,
  PopularSection,
  EmptyState
} from './DashboardSections';

const Dashboard: React.FC = () => {
  const {
    user,
    userProfile,
    recommendedSongs,
    popularSongs,
    loading,
    isUser,
    isAdmin,
    navigate,
    getTopGenres,
    formatCreators,
    formatGenres
  } = useDashboard();

  if (loading) {
    return <DashboardLoading />;
  }

  return (
    <div className="dashboard-modern">
      {/* 极简头部 */}
      <DashboardHeader user={user} isAdmin={isAdmin} />

      {/* 主内容区 */}
      <main className="dashboard-main">
        {/* 音乐品味卡片 */}
        {isUser && userProfile && userProfile.vector.length > 0 && (
          <TasteSection topGenres={getTopGenres()} />
        )}

        {/* 个性化推荐 */}
        {recommendedSongs.length > 0 && (
          <RecommendationsSection
            songs={recommendedSongs}
            formatCreators={formatCreators}
            formatGenres={formatGenres}
          />
        )}

        {/* 热门歌曲 */}
        {popularSongs.length > 0 && (
          <PopularSection 
            songs={popularSongs} 
            formatCreators={formatCreators} 
          />
        )}

        {/* 未登录提示 */}
        {!isUser && !isAdmin && (
          <EmptyState onLogin={() => navigate('/login')} />
        )}
      </main>
    </div>
  );
};

export default Dashboard;
