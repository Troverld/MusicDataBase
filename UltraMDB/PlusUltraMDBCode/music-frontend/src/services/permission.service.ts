import { callAPI } from './api';
import { getUser } from '../utils/storage';

export const permissionService = {
  // 验证用户权限
  async validateUser(): Promise<[boolean, string]> {
    const user = getUser();
    if (!user || !user.userToken || !user.userID) {
      return [false, 'User not authenticated'];
    }

    const data = {
      userID: user.userID,
      userToken: user.userToken
    };

    try {
      return await callAPI<[boolean, string]>('validateUserMapping', data);
    } catch (error: any) {
      return [false, error.message];
    }
  },

  // 验证管理员权限
  async validateAdmin(): Promise<[boolean, string]> {
    const user = getUser();
    if (!user || !user.userToken || !user.userID) {
      return [false, 'Admin not authenticated'];
    }

    const data = {
      adminID: user.userID,
      adminToken: user.userToken
    };

    try {
      return await callAPI<[boolean, string]>('validateAdminMapping', data);
    } catch (error: any) {
      return [false, error.message];
    }
  },

  // 验证歌曲所有权
  async validateSongOwnership(songID: string): Promise<[boolean, string]> {
    const user = getUser();
    if (!user || !user.userToken || !user.userID) {
      return [false, 'User not authenticated'];
    }

    const data = {
      userID: user.userID,
      userToken: user.userToken,
      songID
    };

    try {
      return await callAPI<[boolean, string]>('ValidateSongOwnership', data);
    } catch (error: any) {
      return [false, error.message];
    }
  },

  // 验证专辑所有权
  async validateAlbumOwnership(albumID: string): Promise<[boolean, string]> {
    const user = getUser();
    if (!user || !user.userToken || !user.userID) {
      return [false, 'User not authenticated'];
    }

    const data = {
      userID: user.userID,
      userToken: user.userToken,
      albumID
    };

    try {
      return await callAPI<[boolean, string]>('validateAlbumOwnership', data);
    } catch (error: any) {
      return [false, error.message];
    }
  },

  // 验证歌单所有权
  async validateCollectionOwnership(collectionID: string): Promise<[boolean, string]> {
    const user = getUser();
    if (!user || !user.userToken || !user.userID) {
      return [false, 'User not authenticated'];
    }

    const data = {
      userID: user.userID,
      userToken: user.userToken,
      collectionID
    };

    try {
      return await callAPI<[boolean, string]>('validateCollectionOwnership', data);
    } catch (error: any) {
      return [false, error.message];
    }
  }
};