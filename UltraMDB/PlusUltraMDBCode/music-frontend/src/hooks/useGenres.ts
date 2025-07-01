import { useState, useEffect, useCallback } from 'react';
import { genreService } from '../services/genre.service';
import { Genre } from '../types';

export const useGenres = () => {
  const [genres, setGenres] = useState<Genre[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  // 获取所有曲风
  const fetchGenres = useCallback(async () => {
    setLoading(true);
    setError('');
    
    try {
      const [genreList, message] = await genreService.getAllGenres();
      
      if (genreList) {
        setGenres(genreList);
      } else {
        setError(message || '获取曲风列表失败');
        setGenres([]);
      }
    } catch (err: any) {
      setError(err.message || '获取曲风列表失败');
      setGenres([]);
    } finally {
      setLoading(false);
    }
  }, []);

  // 根据ID获取曲风名称
  const getGenreNameById = useCallback((genreId: string): string => {
    const genre = genres.find(g => g.genreID === genreId);
    return genre ? genre.name : genreId; // 如果找不到就返回ID
  }, [genres]);

  // 根据ID数组获取曲风名称数组
  const getGenreNamesByIds = useCallback((genreIds: string[]): string[] => {
    return genreIds.map(id => getGenreNameById(id));
  }, [getGenreNameById]);

  // 根据名称获取曲风ID
  const getGenreIdByName = useCallback((genreName: string): string | null => {
    const genre = genres.find(g => g.name === genreName);
    return genre ? genre.genreID : null;
  }, [genres]);

  // 根据名称数组获取ID数组
  const getGenreIdsByNames = useCallback((genreNames: string[]): string[] => {
    return genreNames.map(name => getGenreIdByName(name)).filter(id => id !== null) as string[];
  }, [getGenreIdByName]);

  // 初始化时获取曲风列表
  useEffect(() => {
    fetchGenres();
  }, [fetchGenres]);

  return {
    genres,
    loading,
    error,
    fetchGenres,
    getGenreNameById,
    getGenreNamesByIds,
    getGenreIdByName,
    getGenreIdsByNames
  };
};