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
        creatorType: item.type === 'artist' ? 'Artist' : 'Band',
        id: item.id
      }));
    };

    // 转换 string[] 为 CreatorID_Type[] 格式（向后兼容）
    const convertStringArrayToCreatorIDType = (ids: string[]) => {
      if (!ids || ids.length === 0) return [];
      return ids.map(id => ({
        creatorType: 'Artist', // 默认为艺术家
        id: id
      }));
    };

    // 智能判断数据格式并转换
    const smartConvertCreators = (data: any) => {
      if (!data || data.length === 0) return [];
      // 如果是 ArtistBandItem[] 格式（有 type 属性）
      if (data[0] && typeof data[0] === 'object' && data[0].type) {
        return convertToCreatorIDType(data);
      }
      // 如果是 string[] 格式
      return convertStringArrayToCreatorIDType(data);
    };

    const data = {
      userID: user.userID,
      userToken: user.userToken,
      name: songData.name,
      // 转换时间戳为 DateTime 格式（ISO string）
      releaseTime: new Date(songData.releaseTime || Date.now()).toISOString(),
      // 智能转换 creators
      creators: smartConvertCreators(songData.creators),
      // performers 也可能包含乐队，同样智能转换
      performers: smartConvertCreators(songData.performers)?.map((item: any) => item.id) || [],
      // 以下角色通常只有艺术家
      lyricists: Array.isArray(songData.lyricists) ? songData.lyricists.map((item: ArtistBandItem | string) => 
        typeof item === 'object' ? item.id : item
      ) : [],
      composers: Array.isArray(songData.composers) ? songData.composers.map((item: ArtistBandItem | string) => 
        typeof item === 'object' ? item.id : item
      ) : [],
      arrangers: Array.isArray(songData.arrangers) ? songData.arrangers.map((item: ArtistBandItem | string) => 
        typeof item === 'object' ? item.id : item
      ) : [],
      instrumentalists: Array.isArray(songData.instrumentalists) ? songData.instrumentalists.map((item: ArtistBandItem | string) => 
        typeof item === 'object' ? item.id : item
      ) : [],
      genres: songData.genres || []
    };

    return callAPI<[string | null, string]>('UploadNewSong', data);
  },

  async updateSong(songID: string, songData: any): Promise<[boolean, string]> {
    const user = getUser();
    if (!user || !user.userToken || !user.userID) {
      throw new Error('User not authenticated');
    }

    // 智能判断数据格式并转换（复用上面的逻辑）
    const smartConvertCreators = (data: any) => {
      if (!data || data.length === 0) return undefined;
      // 如果是 ArtistBandItem[] 格式（有 type 属性）
      if (data[0] && typeof data[0] === 'object' && data[0].type) {
        return data.map((item: ArtistBandItem) => ({
          creatorType: item.type === 'artist' ? 'Artist' : 'Band',
          id: item.id
        }));
      }
      // 如果是 string[] 格式
      return data.map((id: string) => ({
        creatorType: 'Artist', // 默认为艺术家
        id: id
      }));
    };

    const data = {
      userID: user.userID,
      userToken: user.userToken,
      songID,
      name: songData.name,
      // 转换时间戳为 DateTime 格式
      releaseTime: songData.releaseTime ? new Date(songData.releaseTime).toISOString() : undefined,
      // 智能转换 creators
      creators: songData.creators ? smartConvertCreators(songData.creators) : undefined,
      // performers 也智能转换，但后端期望的是 string[]
      performers: songData.performers ? (
        Array.isArray(songData.performers) ? songData.performers.map((item: ArtistBandItem | string) => 
          typeof item === 'object' ? item.id : item
        ) : songData.performers
      ) : undefined,
      // 以下角色转换为 string[]
      lyricists: songData.lyricists ? (
        Array.isArray(songData.lyricists) ? songData.lyricists.map((item: ArtistBandItem | string) => 
          typeof item === 'object' ? item.id : item
        ) : songData.lyricists
      ) : undefined,
      composers: songData.composers ? (
        Array.isArray(songData.composers) ? songData.composers.map((item: ArtistBandItem | string) => 
          typeof item === 'object' ? item.id : item
        ) : songData.composers
      ) : undefined,
      arrangers: songData.arrangers ? (
        Array.isArray(songData.arrangers) ? songData.arrangers.map((item: ArtistBandItem | string) => 
          typeof item === 'object' ? item.id : item
        ) : songData.arrangers
      ) : undefined,
      instrumentalists: songData.instrumentalists ? (
        Array.isArray(songData.instrumentalists) ? songData.instrumentalists.map((item: ArtistBandItem | string) => 
          typeof item === 'object' ? item.id : item
        ) : songData.instrumentalists
      ) : undefined,
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