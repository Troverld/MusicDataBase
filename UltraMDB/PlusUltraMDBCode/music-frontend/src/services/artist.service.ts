import { callAPI } from './api';
import { Artist } from '../types';
import { getUser } from '../utils/storage';

export const artistService = {
  // 创建新艺术家
  async createArtist(artistData: { name: string; bio: string }): Promise<[string | null, string]> {
    const user = getUser();
    if (!user || !user.userToken || !user.userID) {
      throw new Error('Admin not authenticated');
    }

    const data = {
      adminID: user.userID,
      adminToken: user.userToken,
      name: artistData.name,
      bio: artistData.bio
    };

    return callAPI<[string | null, string]>('CreateArtistMessage', data);
  },

  // 更新艺术家信息
  async updateArtist(artistID: string, artistData: { name?: string; bio?: string }): Promise<[boolean, string]> {
    const user = getUser();
    if (!user || !user.userToken || !user.userID) {
      throw new Error('User not authenticated');
    }

    const data = {
      userID: user.userID,
      userToken: user.userToken,
      artistID,
      name: artistData.name,
      bio: artistData.bio
    };

    return callAPI<[boolean, string]>('UpdateArtistMessage', data);
  },

  // 删除艺术家
  async deleteArtist(artistID: string): Promise<[boolean, string]> {
    const user = getUser();
    if (!user || !user.userToken || !user.userID) {
      throw new Error('Admin not authenticated');
    }

    const data = {
      adminID: user.userID,
      adminToken: user.userToken,
      artistID
    };

    return callAPI<[boolean, string]>('DeleteArtistMessage', data);
  },

  // 根据ID获取艺术家信息
  async getArtistById(artistID: string): Promise<[Artist | null, string]> {
    const user = getUser();
    if (!user || !user.userToken || !user.userID) {
      throw new Error('User not authenticated');
    }

    const data = {
      userID: user.userID,
      userToken: user.userToken,
      artistID
    };

    return callAPI<[Artist | null, string]>('GetArtistByID', data);
  },

  // 根据名称搜索艺术家
  async searchArtistByName(name: string): Promise<[string[] | null, string]> {
    const user = getUser();
    if (!user || !user.userToken || !user.userID) {
      throw new Error('User not authenticated');
    }

    const data = {
      userID: user.userID,
      userToken: user.userToken,
      artistName: name
    };

    return callAPI<[string[] | null, string]>('SearchArtistByName', data);
  },

  // 获取所有创作者
  async getAllCreators(): Promise<[Artist[] | null, string]> {
    const user = getUser();
    if (!user || !user.userToken || !user.userID) {
      throw new Error('User not authenticated');
    }

    const data = {
      userID: user.userID,
      userToken: user.userToken
    };

    return callAPI<[Artist[] | null, string]>('GetAllCreators', data);
  },

  // 批量获取艺术家信息
  async getArtistsByIds(artistIDs: string[]): Promise<Artist[]> {
    if (!artistIDs || artistIDs.length === 0) {
      return [];
    }

    const artists: Artist[] = [];
    
    // 批量获取艺术家信息
    const promises = artistIDs.map(artistID => this.getArtistById(artistID));
    const results = await Promise.allSettled(promises);
    
    results.forEach((result, index) => {
      if (result.status === 'fulfilled') {
        const [artist, message] = result.value;
        if (artist) {
          artists.push(artist);
        } else {
          console.warn(`Failed to fetch artist ${artistIDs[index]}: ${message}`);
        }
      } else {
        console.error(`Error fetching artist ${artistIDs[index]}:`, result.reason);
      }
    });
    
    return artists;
  },

  // 添加艺术家管理者
  async addArtistManager(userID: string, artistID: string): Promise<[boolean, string]> {
    const user = getUser();
    if (!user || !user.userToken || !user.userID) {
      throw new Error('Admin not authenticated');
    }

    const data = {
      adminID: user.userID,
      adminToken: user.userToken,
      userID,
      artistID
    };

    return callAPI<[boolean, string]>('AddArtistManager', data);
  }
};