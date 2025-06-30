import { callAPI } from './api';
import { LoginCredentials, RegisterCredentials, LoginResponse } from '../types';
import { setToken, setUser } from '../utils/storage';

export const authService = {
  async login(credentials: LoginCredentials): Promise<LoginResponse> {
    // 调用更新后的 API
    const result = await callAPI<LoginResponse>('UserLoginMessage', credentials);
    
    if (result[0]) {
      // 解构返回的 [userID, userToken]
      const [userID, userToken] = result[0];
      
      // 存储 token 和完整的用户信息
      setToken(userToken);
      setUser({ 
        userID: userID,  // 现在有真实的 userID
        account: credentials.userName, 
        userToken: userToken 
      });
    }
    
    return result;
  },

  async register(credentials: RegisterCredentials): Promise<RegisterResponse> {
    const result = await callAPI<RegisterResponse>('UserRegisterMessage', credentials);
    return result;
  },

  async logout(userID: string, userToken: string): Promise<[boolean, string]> {
    const result = await callAPI<[boolean, string]>('UserLogoutMessage', { userID, userToken });
    return result;
  }
};