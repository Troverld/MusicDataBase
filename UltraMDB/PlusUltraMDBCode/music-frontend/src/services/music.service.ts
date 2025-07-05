import { callAPI } from './api';
import { Song } from '../types';
import { getUser } from '../utils/storage';

export const musicService = {
  async uploadSong(songData: Partial<Song>): Promise<[string | null, string]> {
    const user = getUser();
    if (!user || !user.userToken || !user.userID) {
      throw new Error('User not authenticated');
    }

    // 转换 creators 为 CreatorID_Type 格式
    const convertToCreatorIDType = (creators: string[]) => {
      return creators.map(creatorId => ({
        // 假设这些是艺术家ID，如果需要区分艺术家和乐队，需要额外逻辑
        creatorType: "Artist", // 或根据实际情况判断是 "Artist" 还是 "Band"
        id: creatorId
      }));
    };

    const data = {
      userID: user.userID,
      userToken: user.userToken,
      name: songData.name,
      // 转换时间戳为 DateTime 格式（ISO string）
      releaseTime: new Date(songData.releaseTime || Date.now()).toISOString(),
      // 转换 creators 为 CreatorID_Type 格式
      creators: convertToCreatorIDType(songData.creators || []),
      performers: songData.performers || [],
      lyricists: songData.lyricists || [],
      composers: songData.composers || [],
      arrangers: songData.arrangers || [],
      instrumentalists: songData.instrumentalists || [],
      genres: songData.genres || []
    };

    return callAPI<[string | null, string]>('UploadNewSong', data);
  },

  async updateSong(songID: string, songData: Partial<Song>): Promise<[boolean, string]> {
    const user = getUser();
    if (!user || !user.userToken || !user.userID) {
      throw new Error('User not authenticated');
    }

    // 转换 creators 为 CreatorID_Type 格式
    const convertToCreatorIDType = (creators?: string[]) => {
      if (!creators) return undefined;
      return creators.map(creatorId => ({
        creatorType: "Artist", // 或根据实际情况判断
        id: creatorId
      }));
    };

    const data = {
      userID: user.userID,
      userToken: user.userToken,
      songID,
      name: songData.name,
      // 转换时间戳为 DateTime 格式
      releaseTime: songData.releaseTime ? new Date(songData.releaseTime).toISOString() : undefined,
      // 转换 creators 为 CreatorID_Type 格式
      creators: convertToCreatorIDType(songData.creators),
      performers: songData.performers,
      lyricists: songData.lyricists,
      composers: songData.composers,
      arrangers: songData.arrangers,
      instrumentalists: songData.instrumentalists,
      genres: songData.genres
    };

    return callAPI<[boolean, string]>('UpdateSongMetadata', data);
  },

  async deleteSong(songID: string): Promise<[boolean, string]> {
    const user = getUser();
    if (!user || !user.userToken || !user.userID) {
      throw new Error('User not authenticated');
    }

    // Note: DeleteSong API requires admin privileges
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

  // 新增：根据歌曲ID获取歌曲详情
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

  // 新增：批量获取歌曲详情
  async getSongsByIds(songIDs: string[]): Promise<Song[]> {
    const songs: Song[] = [];
    
    // 并行获取所有歌曲详情
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

  // 新增：按实体筛选歌曲
  async filterSongsByEntity(creator?: {id: string, type: 'artist' | 'band'}, genreID?: string): Promise<[string[] | null, string]> {
    const user = getUser();
    if (!user || !user.userToken || !user.userID) {
      throw new Error('User not authenticated');
    }

    const data = {
      userID: user.userID,
      userToken: user.userToken,
      creator: creator ? {
        creatorType: creator.type === 'artist' ? 'Artist' : 'Band',
        id: creator.id
      } : undefined,
      genres: genreID
    };

    return callAPI<[string[] | null, string]>('FilterSongsByEntity', data);
  }
};