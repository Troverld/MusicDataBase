import { callAPI } from './api';
import { Genre } from '../types';
import { getUser } from '../utils/storage';

export const genreService = {
  // 获取所有曲风列表
  async getAllGenres(): Promise<[Genre[] | null, string]> {
    const user = getUser();
    if (!user || !user.userToken || !user.userID) {
      throw new Error('User not authenticated');
    }

    const data = {
      userID: user.userID,
      userToken: user.userToken
    };

    const result = await callAPI<[Genre[] | null, string]>('GetGenreList', data);
    return result;
  },

  async createGenre(genreData: { name: string; description: string }): Promise<[string | null, string]> {
    const user = getUser();
    if (!user || !user.userToken || !user.userID) {
      throw new Error('Admin not authenticated');
    }

    const data = {
      adminID: user.userID,
      adminToken: user.userToken,
      name: genreData.name,
      description: genreData.description
    };

    return callAPI<[string | null, string]>('CreateNewGenre', data);
  },

  async deleteGenre(genreID: string): Promise<[boolean, string]> {
    const user = getUser();
    if (!user || !user.userToken || !user.userID) {
      throw new Error('Admin not authenticated');
    }

    const data = {
      adminID: user.userID,
      adminToken: user.userToken,
      genreID
    };

    return callAPI<[boolean, string]>('DeleteGenre', data);
  }
};