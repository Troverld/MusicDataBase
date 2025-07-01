import { callAPI } from './api';
import { Song } from '../types';
import { getUser } from '../utils/storage';

export const musicService = {
  async uploadSong(songData: Partial<Song>): Promise<[string | null, string]> {
    const user = getUser();
    if (!user || !user.userToken || !user.userID) {
      throw new Error('User not authenticated');
    }

    const data = {
      userID: user.userID,
      userToken: user.userToken,
      name: songData.name,
      releaseTime: songData.releaseTime || Date.now(),
      creators: songData.creators || [],
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

    const data = {
      userID: user.userID,
      userToken: user.userToken,
      songID,
      name: songData.name,
      releaseTime: songData.releaseTime,
      creators: songData.creators,
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
  }
};