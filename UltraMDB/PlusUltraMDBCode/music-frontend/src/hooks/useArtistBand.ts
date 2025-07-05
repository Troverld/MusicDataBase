import { useState, useCallback } from 'react';
import { artistService } from '../services/artist.service';
import { bandService } from '../services/band.service';
import { Artist, Band, CreatorID_Type } from '../types';

export interface ArtistBandItem {
  id: string;
  name: string;
  bio: string;
  type: 'artist' | 'band';
  members?: string[]; // 只有乐队才有
}

export const useArtistBand = () => {
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  // 搜索艺术家和乐队
  const searchArtistBand = useCallback(async (
    keyword: string, 
    searchType: 'artist' | 'band' | 'both' = 'both'
  ): Promise<ArtistBandItem[]> => {
    if (!keyword.trim()) {
      return [];
    }

    setLoading(true);
    setError('');

    try {
      const results: ArtistBandItem[] = [];

      // 搜索艺术家
      if (searchType === 'artist' || searchType === 'both') {
        try {
          const [artistIDs, artistMessage] = await artistService.searchArtistByName(keyword);
          if (artistIDs && artistIDs.length > 0) {
            const artists = await artistService.getArtistsByIds(artistIDs);
            results.push(...artists.map(artist => ({
              id: artist.artistID,
              name: artist.name,
              bio: artist.bio,
              type: 'artist' as const
            })));
          }
        } catch (err) {
          console.warn('Artist search failed:', err);
        }
      }

      // 搜索乐队
      if (searchType === 'band' || searchType === 'both') {
        try {
          const [bandIDs, bandMessage] = await bandService.searchBandByName(keyword);
          if (bandIDs && bandIDs.length > 0) {
            const bands = await bandService.getBandsByIds(bandIDs);
            results.push(...bands.map(band => ({
              id: band.bandID,
              name: band.name,
              bio: band.bio,
              type: 'band' as const,
              members: band.members
            })));
          }
        } catch (err) {
          console.warn('Band search failed:', err);
        }
      }

      // 按名称排序
      results.sort((a, b) => a.name.localeCompare(b.name));

      return results;
    } catch (err: any) {
      setError(err.message || '搜索失败');
      return [];
    } finally {
      setLoading(false);
    }
  }, []);

  // 根据ID获取艺术家详情
  const getArtistById = useCallback(async (artistID: string): Promise<Artist | null> => {
    try {
      const [artist, message] = await artistService.getArtistById(artistID);
      return artist;
    } catch (err) {
      console.error('Failed to get artist:', err);
      return null;
    }
  }, []);

  // 根据ID获取乐队详情
  const getBandById = useCallback(async (bandID: string): Promise<Band | null> => {
    try {
      const [band, message] = await bandService.getBandById(bandID);
      return band;
    } catch (err) {
      console.error('Failed to get band:', err);
      return null;
    }
  }, []);

  // 批量获取艺术家和乐队详情
  const getArtistBandsByIds = useCallback(async (
    items: Array<{id: string, type: 'artist' | 'band'}>
  ): Promise<ArtistBandItem[]> => {
    const results: ArtistBandItem[] = [];
    
    for (const item of items) {
      try {
        if (item.type === 'artist') {
          const artist = await getArtistById(item.id);
          if (artist) {
            results.push({
              id: artist.artistID,
              name: artist.name,
              bio: artist.bio,
              type: 'artist'
            });
          }
        } else if (item.type === 'band') {
          const band = await getBandById(item.id);
          if (band) {
            results.push({
              id: band.bandID,
              name: band.name,
              bio: band.bio,
              type: 'band',
              members: band.members
            });
          }
        }
      } catch (err) {
        console.error(`Failed to get ${item.type} ${item.id}:`, err);
      }
    }
    
    return results;
  }, [getArtistById, getBandById]);

  // 智能转换ID到艺术家/乐队项目（自动识别类型）
  const convertIdsToArtistBandItems = useCallback(async (ids: string[]): Promise<ArtistBandItem[]> => {
    if (!ids || ids.length === 0) return [];
    
    const results: ArtistBandItem[] = [];
    
    for (const id of ids) {
      if (!id || !id.trim()) continue;
      
      let found = false;
      
      // 首先尝试作为艺术家ID
      try {
        const artist = await getArtistById(id);
        if (artist) {
          results.push({
            id: artist.artistID,
            name: artist.name,
            bio: artist.bio,
            type: 'artist'
          });
          found = true;
        }
      } catch (err) {
        // 继续尝试乐队
      }
      
      // 如果不是艺术家，尝试作为乐队ID
      if (!found) {
        try {
          const band = await getBandById(id);
          if (band) {
            results.push({
              id: band.bandID,
              name: band.name,
              bio: band.bio,
              type: 'band',
              members: band.members
            });
            found = true;
          }
        } catch (err) {
          // 继续
        }
      }
      
      // 如果都找不到，可能是名称而不是ID，尝试搜索
      if (!found) {
        try {
          const searchResults = await searchArtistBand(id.trim(), 'both');
          const exactMatch = searchResults.find(item => 
            item.name.toLowerCase() === id.trim().toLowerCase()
          );
          
          if (exactMatch) {
            results.push(exactMatch);
            found = true;
          }
        } catch (err) {
          // 搜索失败
        }
      }
      
      // 如果还是找不到，记录未找到的项目
      if (!found) {
        console.warn(`Unable to resolve ID: ${id}`);
        // 可以选择是否添加占位符项目
        results.push({
          id: `unresolved-${id}`,
          name: id, // 使用原始值作为名称
          bio: `无法解析的项目：${id}`,
          type: 'artist'
        });
      }
    }
    
    return results;
  }, [getArtistById, getBandById, searchArtistBand]);

  // 专门处理 CreatorID_Type[] 到 ArtistBandItem[] 的转换
  const convertCreatorsToSelectedItems = useCallback(async (creators: CreatorID_Type[]): Promise<ArtistBandItem[]> => {
    if (!creators || creators.length === 0) return [];
    
    const results: ArtistBandItem[] = [];
    
    for (const creator of creators) {
      try {
        // 直接使用 creator.creatorType，它已经是类型安全的
        const creatorItem = await getArtistBandsByIds([{
          id: creator.id, 
          type: creator.creatorType // 直接使用，无需转换
        }]);
        
        if (creatorItem.length > 0) {
          results.push(creatorItem[0]);
        } else {
          // 如果找不到，创建警告项目
          const displayName = creator.creatorType === 'artist' ? '艺术家' : '乐队';
          results.push({
            id: `not-found-${creator.id}`,
            name: creator.id,
            bio: `警告：无法找到ID为"${creator.id}"的${displayName}，可能是已删除的项目。请重新搜索选择。`,
            type: creator.creatorType
          });
        }
      } catch (error) {
        console.warn(`Failed to convert creator to item:`, creator, error);
        // 创建一个错误项目
        const displayName = creator.creatorType === 'artist' ? '艺术家' : '乐队';
        results.push({
          id: `error-${creator.id}`,
          name: creator.id,
          bio: `错误：处理"${creator.id}"的${displayName}时发生错误，请重新搜索选择`,
          type: creator.creatorType
        });
      }
    }
    
    return results;
  }, [getArtistBandsByIds]);

  // 将字符串ID数组转换为选中项目（用于处理传统的字符串数组字段）
  // 注意：这些字段只能是艺术家，因为只有创作者可以是乐队
  const convertIdsToSelectedItems = useCallback(async (ids: string[]): Promise<ArtistBandItem[]> => {
    if (!ids || ids.length === 0) return [];
    
    const results: ArtistBandItem[] = [];
    
    for (const id of ids) {
      if (!id.trim()) continue;
      
      try {
        // 只尝试作为艺术家ID获取（因为只有创作者可以是乐队）
        const artistItems = await getArtistBandsByIds([{id, type: 'artist'}]);
        if (artistItems.length > 0) {
          results.push(artistItems[0]);
        } else {
          // 如果找不到艺术家，创建警告项目
          results.push({
            id: `not-found-${id}`,
            name: id,
            bio: `警告：无法找到ID为"${id}"的艺术家，可能是已删除的项目。请重新搜索选择。`,
            type: 'artist'
          });
        }
      } catch (error) {
        console.warn(`Failed to convert ID to item: ${id}`, error);
        // 创建一个错误项目
        results.push({
          id: `error-${id}`,
          name: id,
          bio: `错误：处理"${id}"时发生错误，请重新搜索选择`,
          type: 'artist'
        });
      }
    }
    
    return results;
  }, [getArtistBandsByIds]);

  // 将艺术家/乐队项目转换为ID列表
  const convertArtistBandItemsToIds = useCallback((items: ArtistBandItem[]): string[] => {
    return items.map(item => item.id).filter(id => id && !id.startsWith('unresolved-'));
  }, []);

  // 将艺术家/乐队项目转换为名称列表
  const convertArtistBandItemsToNames = useCallback((items: ArtistBandItem[]): string[] => {
    return items.map(item => item.name).filter(name => name);
  }, []);

  // 批量转换ID为名称
  const convertIdsToNames = useCallback(async (ids: string[]): Promise<string[]> => {
    if (!ids || ids.length === 0) return [];
    
    const items = await convertIdsToArtistBandItems(ids);
    return convertArtistBandItemsToNames(items);
  }, [convertIdsToArtistBandItems, convertArtistBandItemsToNames]);

  return {
    loading,
    error,
    searchArtistBand,
    getArtistById,
    getBandById,
    getArtistBandsByIds,
    convertIdsToArtistBandItems,
    convertCreatorsToSelectedItems, // 专门处理 CreatorID_Type[]
    convertIdsToSelectedItems,      // 处理传统字符串ID数组
    convertArtistBandItemsToIds,
    convertArtistBandItemsToNames,
    convertIdsToNames
  };
};