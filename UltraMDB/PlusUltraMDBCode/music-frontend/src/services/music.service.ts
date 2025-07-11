import { callAPI } from './api';
import { Song } from '../types';
import { getUser } from '../utils/storage';
import { ArtistBandItem } from '../hooks/useArtistBand';

export const musicService = {
  async uploadSong(songData: any): Promise<[string | null, string]> {
    const user = getUser();
    if (!user || !user.userToken || !user.userID) {
      throw new Error('User not authenticated');
    }

    // 转换 ArtistBandItem[] 为 CreatorID_Type[] 格式
    const convertToCreatorIDType = (items: ArtistBandItem[]) => {
      if (!items || items.length === 0) return [];
      return items.map(item => ({
        creatorType: item.type === 'artist' ? 'artist' : 'band',
        id: item.id
      }));
    };

    // 提取ID的辅助函数
    const extractIds = (items: ArtistBandItem[]) => {
      if (!items || items.length === 0) return [];
      return items.map(item => item.id);
    };

    const data = {
      userID: user.userID,
      userToken: user.userToken,
      name: songData.name,
      releaseTime: songData.releaseTime ? new Date(songData.releaseTime).getTime() : undefined,
      creators: songData.creators ? convertToCreatorIDType(songData.creators) : undefined,
      performers: songData.performers ? extractIds(songData.performers) : undefined,
      lyricists: songData.lyricists ? extractIds(songData.lyricists) : undefined,
      composers: songData.composers ? extractIds(songData.composers) : undefined,
      arrangers: songData.arrangers ? extractIds(songData.arrangers) : undefined,
      instrumentalists: songData.instrumentalists ? extractIds(songData.instrumentalists) : undefined,
      genres: songData.genres
    };

    return callAPI<[string | null, string]>('UploadNewSong', data);
  },

  async updateSong(songID: string, songData: any): Promise<[boolean, string]> {
    const user = getUser();
    if (!user || !user.userToken || !user.userID) {
      throw new Error('User not authenticated');
    }

    // 转换 ArtistBandItem[] 为 CreatorID_Type[] 格式
    const convertToCreatorIDType = (items: ArtistBandItem[]) => {
      if (!items || items.length === 0) return [];
      return items.map(item => ({
        creatorType: item.type === 'artist' ? 'artist' : 'band',
        id: item.id
      }));
    };

    // 提取ID的辅助函数
    const extractIds = (items: ArtistBandItem[]) => {
      if (!items || items.length === 0) return [];
      return items.map(item => item.id);
    };

    const data = {
      userID: user.userID,
      userToken: user.userToken,
      songID,
      name: songData.name,
      releaseTime: songData.releaseTime ? new Date(songData.releaseTime).getTime() : undefined,
      creators: songData.creators ? convertToCreatorIDType(songData.creators) : undefined,
      performers: songData.performers ? extractIds(songData.performers) : undefined,
      lyricists: songData.lyricists ? extractIds(songData.lyricists) : undefined,
      composers: songData.composers ? extractIds(songData.composers) : undefined,
      arrangers: songData.arrangers ? extractIds(songData.arrangers) : undefined,
      instrumentalists: songData.instrumentalists ? extractIds(songData.instrumentalists) : undefined,
      genres: songData.genres
    };

    return callAPI<[boolean, string]>('UpdateSongMetadata', data);
  },

  async deleteSong(songID: string): Promise<[boolean, string]> {
    const user = getUser();
    if (!user || !user.userToken || !user.userID) {
      throw new Error('User not authenticated');
    }

    const data = {
      adminID: user.userID,
      adminToken: user.userToken,
      songID
    };

    return callAPI<[boolean, string]>('DeleteSong', data);
  },

  async searchSongs(keywords: string): Promise<[string[] | null, string]> {
    const user = getUser();
    if (!user || !user.userToken || !user.userID) {
      throw new Error('User not authenticated');
    }

    const data = {
      userID: user.userID,
      userToken: user.userToken,
      keywords
    };

    return callAPI<[string[] | null, string]>('SearchSongsByName', data);
  },

  // 新增：分页搜索歌曲方法
  async searchSongsPaged(keywords: string, pageNumber: number, pageSize: number = 5): Promise<[{songIds: string[] | null, totalPages: number} | null, string]> {
    const user = getUser();
    if (!user || !user.userToken || !user.userID) {
      throw new Error('User not authenticated');
    }

    const data = {
      userID: user.userID,
      userToken: user.userToken,
      keywords,
      pageNumber,
      pageSize
    };

    try {
      const result = await callAPI<[[string[], number] | null, string]>('SearchSongsByNamePaged', data);
      const [optionResult, message] = result;
      
      if (optionResult && Array.isArray(optionResult) && optionResult.length === 2) {
        const [songIds, totalPages] = optionResult;
        return [{ songIds, totalPages }, message];
      } else {
        return [null, message];
      }
    } catch (error: any) {
      return [null, error.message || '分页搜索失败'];
    }
  },

  async getSongById(songID: string): Promise<[Song | null, string]> {
    const user = getUser();
    if (!user || !user.userToken || !user.userID) {
      throw new Error('User not authenticated');
    }

    const data = {
      userID: user.userID,
      userToken: user.userToken,
      songID
    };

    return callAPI<[Song | null, string]>('GetSongByID', data);
  },

  async getSongsByIds(songIDs: string[]): Promise<Song[]> {
    const songs: Song[] = [];
    
    const promises = songIDs.map(songID => this.getSongById(songID));
    const results = await Promise.allSettled(promises);
    
    results.forEach((result, index) => {
      if (result.status === 'fulfilled') {
        const [song, message] = result.value;
        if (song) {
          songs.push(song);
        } else {
          console.warn(`Failed to fetch song ${songIDs[index]}: ${message}`);
        }
      } else {
        console.error(`Error fetching song ${songIDs[index]}:`, result.reason);
      }
    });
    
    return songs;
  },

  async filterSongsByEntity(creator?: {id: string, type: 'artist' | 'band'}, genreID?: string): Promise<[string[] | null, string]> {
    const user = getUser();
    if (!user || !user.userToken || !user.userID) {
      throw new Error('User not authenticated');
    }

    const data = {
      userID: user.userID,
      userToken: user.userToken,
      creator: creator ? {
        creatorType: creator.type === 'artist' ? 'artist' : 'band',
        id: creator.id
      } : undefined,
      genres: genreID
    };

    return callAPI<[string[] | null, string]>('FilterSongsByEntity', data);
  }
};