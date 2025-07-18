// MusicRecommendations 相关的类型定义
export interface RecommendationTab {
    id: string;
    label: string;
    icon: string;
    description: string;
  }
  
  export const RECOMMENDATION_TABS: RecommendationTab[] = [
    {
      id: 'personal',
      label: '为你推荐',
      icon: '✨',
      description: '基于你的听歌习惯'
    },
    {
      id: 'similar',
      label: '相似歌曲',
      icon: '🎵',
      description: '找到风格相近的音乐'
    },
    {
      id: 'next',
      label: '播放建议',
      icon: '▶️',
      description: '接下来听什么'
    }
  ];