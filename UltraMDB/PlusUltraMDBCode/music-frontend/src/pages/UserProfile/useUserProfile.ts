import { useState, useEffect } from 'react';
import { statisticsService } from '../../services/statistics.service';
import { useGenres } from '../../hooks/useGenres';
import { Profile } from '../../types';
import { getUser } from '../../utils/storage';
import { 
  SortedGenreData, 
  UserCharacteristics, 
  GENRE_COLORS, 
  TOP_GENRES_COUNT,
  TOP_CHARACTERISTICS_COUNT 
} from './types';

export const useUserProfile = () => {
  const [profile, setProfile] = useState<Profile | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [sortedData, setSortedData] = useState<SortedGenreData[]>([]);
  
  const { getGenreNameById, fetchGenres, loading: genresLoading } = useGenres();
  const user = getUser();

  // 初始化
  useEffect(() => {
    const initialize = async () => {
      if (!user) {
        setError('用户未登录');
        setLoading(false);
        return;
      }

      try {
        setLoading(true);
        setError('');
        
        // 先获取曲风列表
        await fetchGenres();
        
        // 然后获取用户画像
        const [portraitData, message] = await statisticsService.getUserPortrait();
        
        if (portraitData) {
          setProfile(portraitData);
        } else {
          setError(message || '获取用户画像失败');
        }
      } catch (err: any) {
        setError(err.message || '获取用户画像失败');
      } finally {
        setLoading(false);
      }
    };
    
    if (user && user.userID && user.userToken) {
      initialize();
    } else {
      setError('用户未登录');
      setLoading(false);
    }
  }, []);

  // 处理和排序数据
  useEffect(() => {
    if (profile && profile.vector && !genresLoading) {
      const processedData = profile.vector
        .map(dim => ({
          genreID: dim.GenreID,
          value: dim.value || 0,
          name: getGenreNameById(dim.GenreID) || `Genre ${dim.GenreID}`
        }))
        .sort((a, b) => b.value - a.value)
        .filter(item => item.value > 0);
      
      setSortedData(processedData);
    }
  }, [profile, genresLoading, getGenreNameById]);

  // 计算总分值
  const total = sortedData.reduce((sum, item) => sum + item.value, 0);

  // 获取颜色
  const getColor = (index: number): string => {
    return GENRE_COLORS[index % GENRE_COLORS.length];
  };

  // 获取前N个主要曲风
  const getTopGenres = (n: number = TOP_GENRES_COUNT) => {
    return sortedData.slice(0, n);
  };

  // 分析用户特征
  const getUserCharacteristics = (): UserCharacteristics | null => {
    if (sortedData.length === 0) return null;

    const topGenres = getTopGenres(TOP_CHARACTERISTICS_COUNT);
    const topPercentage = topGenres.reduce((sum, g) => sum + (g.value / total * 100), 0);
    const diversity = sortedData.length;
    
    return {
      mainStyle: topGenres[0]?.name || '未知',
      concentration: topPercentage > 70 ? '专一' : topPercentage > 50 ? '偏好明显' : '多元化',
      diversity: diversity > 10 ? '高' : diversity > 5 ? '中' : '低',
      topGenres
    };
  };

  const characteristics = getUserCharacteristics();

  return {
    // 状态
    profile,
    loading: loading || genresLoading,
    error,
    sortedData,
    total,
    characteristics,
    user,
    
    // 方法
    getColor,
    getTopGenres
  };
};
