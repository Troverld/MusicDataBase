// UserProfile 相关的类型定义

export interface SortedGenreData {
    genreID: string;
    value: number;
    name: string;
  }
  
  export interface UserCharacteristics {
    mainStyle: string;
    concentration: string;
    diversity: string;
    topGenres: SortedGenreData[];
  }
  
  export const GENRE_COLORS = [
    '#6366f1', // 紫色
    '#8b5cf6', // 深紫
    '#ec4899', // 粉色
    '#f59e0b', // 橙色
    '#10b981', // 绿色
    '#3b82f6', // 蓝色
    '#ef4444', // 红色
    '#14b8a6'  // 青色
  ];
  
  export const TOP_GENRES_COUNT = 5;
  export const TOP_CHARACTERISTICS_COUNT = 3;
