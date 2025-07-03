import { useState, useCallback } from 'react';
import { artistService } from '../services/artist.service';
import { bandService } from '../services/band.service';
import { Artist, Band } from '../types';

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

  return {
    loading,
    error,
    searchArtistBand,
    getArtistById,
    getBandById,
    getArtistBandsByIds
  };
};