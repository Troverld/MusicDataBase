import { callAPI } from './api';
import { LoginCredentials, RegisterCredentials } from '../types';
import { setToken, setUser } from '../utils/storage';

export const authService = {
  async login(credentials: LoginCredentials): Promise<[string | null, string]> {
    const result = await callAPI<[string | null, string]>('UserLoginMessage', credentials);
    
    if (result[0]) {
      // Store token and user info
      setToken(result[0]);
      setUser({ userID: '', account: credentials.userName, userToken: result[0] });
    }
    
    return result;
  },

  async register(credentials: RegisterCredentials): Promise<[string | null, string]> {
    const result = await callAPI<[string | null, string]>('UserRegisterMessage', credentials);
    return result;
  },

  async logout(userID: string, userToken: string): Promise<[boolean, string]> {
    const result = await callAPI<[boolean, string]>('UserLogoutMessage', { userID, userToken });
    return result;
  }
};