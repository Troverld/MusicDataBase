import { callAPI } from './api';
import { Profile, SongRating } from '../types';
import { getUser } from '../utils/storage';

export const statisticsService = {
  // 用户对歌曲评分
  async rateSong(songID: string, rating: number): Promise<[boolean, string]> {
    const user = getUser();
    if (!user || !user.userToken || !user.userID) {
      throw new Error('User not authenticated');
    }

    if (rating < 1 || rating > 5) {
      throw new Error('评分必须在1-5之间');
    }

    const data = {
      userID: user.userID,
      userToken: user.userToken,
      songID,
      rating
    };

    return callAPI<[boolean, string]>('RateSong', data);
  },

  // 获取用户对歌曲的评分
  async getSongRate(targetUserID: string, songID: string): Promise<[number, string]> {
    const user = getUser();
    if (!user || !user.userToken || !user.userID) {
      throw new Error('User not authenticated');
    }

    const data = {
      userID: user.userID,
      userToken: user.userToken,
      targetUserID,
      songID
    };

    return callAPI<[number, string]>('GetSongRate', data);
  },

  // 获取当前用户对歌曲的评分（简化版本）
  async getCurrentUserSongRate(songID: string): Promise<[number, string]> {
    const user = getUser();
    if (!user || !user.userID) {
      return [0, 'User not authenticated'];
    }
    
    return this.getSongRate(user.userID, songID);
  },

  // 获取歌曲平均评分
  async getAverageRating(songID: string): Promise<[[number, number], string]> {
    const user = getUser();
    if (!user || !user.userToken || !user.userID) {
      throw new Error('User not authenticated');
    }

    const data = {
      userID: user.userID,
      userToken: user.userToken,
      songID
    };

    return callAPI<[[number, number], string]>('GetAverageRating', data);
  },

  // 获取歌曲热度
  async getSongPopularity(songID: string): Promise<[number | null, string]> {
    const user = getUser();
    if (!user || !user.userToken || !user.userID) {
      throw new Error('User not authenticated');
    }

    const data = {
      userID: user.userID,
      userToken: user.userToken,
      songID
    };

    return callAPI<[number | null, string]>('GetSongPopularity', data);
  },

  // 记录播放行为
  async logPlayback(songID: string): Promise<[boolean, string]> {
    const user = getUser();
    if (!user || !user.userToken || !user.userID) {
      throw new Error('User not authenticated');
    }

    const data = {
      userID: user.userID,
      userToken: user.userToken,
      songID
    };

    return callAPI<[boolean, string]>('LogPlayback', data);
  },

  // 综合获取歌曲评分信息
  async getSongRatingInfo(songID: string): Promise<SongRating> {
    try {
      const [
        [userRating, userRatingMessage],
        [[averageRating, ratingCount], averageMessage],
        [popularity, popularityMessage]
      ] = await Promise.allSettled([
        this.getCurrentUserSongRate(songID),
        this.getAverageRating(songID),
        this.getSongPopularity(songID)
      ]).then(results => {
        return results.map(result => {
          if (result.status === 'fulfilled') {
            return result.value;
          } else {
            console.warn('API call failed:', result.reason);
            return [null, result.reason.message || 'Unknown error'];
          }
        });
      });

      return {
        userRating: userRating || 0,
        averageRating: averageRating || 0,
        ratingCount: ratingCount || 0,
        popularity: popularity || 0
      };
    } catch (error: any) {
      console.error('Failed to get song rating info:', error);
      return {
        userRating: 0,
        averageRating: 0,
        ratingCount: 0,
        popularity: 0
      };
    }
  }
};