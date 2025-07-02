import { callAPI } from './api';
import { Band } from '../types';
import { getUser } from '../utils/storage';
import { artistService } from './artist.service';

export const bandService = {
  // 创建新乐队
  async createBand(bandData: { name: string; bio: string; memberNames: string[] }): Promise<[string | null, string]> {
    const user = getUser();
    if (!user || !user.userToken || !user.userID) {
      throw new Error('Admin not authenticated');
    }

    // 将艺术家名称转换为ID
    const memberIDs = await this.convertArtistNamesToIds(bandData.memberNames);

    const data = {
      adminID: user.userID,
      adminToken: user.userToken,
      name: bandData.name,
      members: memberIDs,
      bio: bandData.bio
    };

    return callAPI<[string | null, string]>('CreateBandMessage', data);
  },

  // 更新乐队信息
  async updateBand(bandID: string, bandData: { name?: string; bio?: string; memberNames?: string[] }): Promise<[boolean, string]> {
    const user = getUser();
    if (!user || !user.userToken || !user.userID) {
      throw new Error('User not authenticated');
    }

    // 如果提供了成员名称，转换为ID
    let memberIDs: string[] | undefined;
    if (bandData.memberNames) {
      memberIDs = await this.convertArtistNamesToIds(bandData.memberNames);
    }

    const data = {
      userID: user.userID,
      userToken: user.userToken,
      bandID,
      name: bandData.name,
      bio: bandData.bio,
      members: memberIDs
    };

    return callAPI<[boolean, string]>('UpdateBandMessage', data);
  },

  // 删除乐队
  async deleteBand(bandID: string): Promise<[boolean, string]> {
    const user = getUser();
    if (!user || !user.userToken || !user.userID) {
      throw new Error('Admin not authenticated');
    }

    const data = {
      adminID: user.userID,
      adminToken: user.userToken,
      bandID
    };

    return callAPI<[boolean, string]>('DeleteBandMessage', data);
  },

  // 根据ID获取乐队信息
  async getBandById(bandID: string): Promise<[Band | null, string]> {
    const user = getUser();
    if (!user || !user.userToken || !user.userID) {
      throw new Error('User not authenticated');
    }

    const data = {
      userID: user.userID,
      userToken: user.userToken,
      bandID
    };

    return callAPI<[Band | null, string]>('GetBandByID', data);
  },

  // 根据名称搜索乐队
  async searchBandByName(bandName: string): Promise<[string[] | null, string]> {
    const user = getUser();
    if (!user || !user.userToken || !user.userID) {
      throw new Error('User not authenticated');
    }

    const data = {
      userID: user.userID,
      userToken: user.userToken,
      BandName: bandName  // 注意：API中使用的是 BandName
    };

    return callAPI<[string[] | null, string]>('SearchBandByName', data);
  },

  // 批量获取乐队详情
  async getBandsByIds(bandIDs: string[]): Promise<Band[]> {
    const bands: Band[] = [];
    
    // 并行获取所有乐队详情
    const promises = bandIDs.map(bandID => this.getBandById(bandID));
    const results = await Promise.allSettled(promises);
    
    results.forEach((result, index) => {
      if (result.status === 'fulfilled') {
        const [band, message] = result.value;
        if (band) {
          bands.push(band);
        } else {
          console.warn(`Failed to fetch band ${bandIDs[index]}: ${message}`);
        }
      } else {
        console.error(`Error fetching band ${bandIDs[index]}:`, result.reason);
      }
    });
    
    return bands;
  },

  // 添加乐队管理者
  async addBandManager(userID: string, bandID: string): Promise<[boolean, string]> {
    const user = getUser();
    if (!user || !user.userToken || !user.userID) {
      throw new Error('Admin not authenticated');
    }

    const data = {
      adminID: user.userID,
      adminToken: user.userToken,
      userID,
      bandID
    };

    return callAPI<[boolean, string]>('AddBandManager', data);
  },

  // 验证乐队所有权
  async validateBandOwnership(bandID: string): Promise<[boolean, string]> {
    const user = getUser();
    if (!user || !user.userToken || !user.userID) {
      throw new Error('User not authenticated');
    }

    const data = {
      userID: user.userID,
      userToken: user.userToken,
      bandID
    };

    return callAPI<[boolean, string]>('validBandOwnership', data);
  },

  // 将艺术家名称转换为ID的辅助方法
  async convertArtistNamesToIds(artistNames: string[]): Promise<string[]> {
    if (!artistNames || artistNames.length === 0) {
      return [];
    }

    const artistIDs: string[] = [];
    
    for (const name of artistNames) {
      if (!name.trim()) continue;
      
      try {
        // 搜索艺术家
        const [foundIDs, message] = await artistService.searchArtistByName(name.trim());
        
        if (foundIDs && foundIDs.length > 0) {
          // 获取艺术家详情以确认名称完全匹配
          const artists = await artistService.getArtistsByIds(foundIDs);
          const exactMatch = artists.find(artist => 
            artist.name.toLowerCase() === name.trim().toLowerCase()
          );
          
          if (exactMatch) {
            artistIDs.push(exactMatch.artistID);
          } else {
            console.warn(`No exact match found for artist name: ${name}`);
            // 如果没有精确匹配，可以考虑使用第一个结果或抛出错误
            throw new Error(`Artist not found: ${name}`);
          }
        } else {
          throw new Error(`Artist not found: ${name}`);
        }
      } catch (error) {
        console.error(`Error converting artist name to ID: ${name}`, error);
        throw new Error(`Failed to find artist: ${name}`);
      }
    }
    
    return artistIDs;
  },

  // 将艺术家ID转换为名称的辅助方法
  async convertArtistIdsToNames(artistIDs: string[]): Promise<string[]> {
    if (!artistIDs || artistIDs.length === 0) {
      return [];
    }

    try {
      const artists = await artistService.getArtistsByIds(artistIDs);
      return artists.map(artist => artist.name);
    } catch (error) {
      console.error('Error converting artist IDs to names:', error);
      return artistIDs; // 如果转换失败，返回原始ID
    }
  }
};