import { callAPI } from './api';
import { Song } from '../types';
import { getUser } from '../utils/storage';

export const musicService = {
  async uploadSong(songData: Partial<Song>): Promise<[string | null, string]> {
    const user = getUser();
    if (!user || !user.userToken) {
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
    if (!user || !user.userToken) {
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
    if (!user || !user.userToken) {
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
    if (!user || !user.userToken) {
      throw new Error('User not authenticated');
    }

    const data = {
      userID: user.userID,
      userToken: user.userToken,
      keywords
    };

    return callAPI<[string[] | null, string]>('SearchSongsByName', data);
  }
};