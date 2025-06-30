import axios, { AxiosInstance } from 'axios';
import { getToken, getUser } from '../utils/storage';
import { PlanContext } from '../types';

// 服务端口映射（根据后端 ServiceUtils.portMap 计算）
const SERVICE_PORTS = {
  organize: 10011,   // OrganizeService
  music: 10010,      // MusicService  
  creator: 10012,    // CreatorService
  track: 10013       // TrackService
};

// API 到服务的映射
const API_SERVICE_MAP: Record<string, keyof typeof SERVICE_PORTS> = {
  // OrganizeService APIs
  'UserLoginMessage': 'organize',
  'UserRegisterMessage': 'organize',
  'UserLogoutMessage': 'organize',
  'validateUserMapping': 'organize',
  'validateAdminMapping': 'organize',
  
  // MusicService APIs
  'UploadNewSong': 'music',
  'UpdateSongMetadata': 'music',
  'DeleteSong': 'music',
  'SearchSongsByName': 'music',
  'CreateNewGenre': 'music',
  'DeleteGenre': 'music',
  'FilterSongsByEntity': 'music',
  'ValidateSongOwnership': 'music',
  
  // CreatorService APIs
  'CreateArtistMessage': 'creator',
  'UpdateArtistMessage': 'creator',
  'DeleteArtistMessage': 'creator',
  'CreateBandMessage': 'creator',
  'UpdateBandMessage': 'creator',
  'DeleteBandMessage': 'creator',
  'AddArtistManager': 'creator',
  'AddBandManager': 'creator',
  'validArtistOwnership': 'creator',
  'validBandOwnership': 'creator',
  
  // TrackService APIs
  'CreateAlbum': 'track',
  'UpdateAlbum': 'track',
  'DeleteAlbum': 'track',
  'CreateCollection': 'track',
  'UpdateCollection': 'track',
  'DeleteCollection': 'track',
  'AddToPlaylist': 'track',
  'InviteMaintainerToCollection': 'track',
  'validateAlbumOwnership': 'track',
  'validateCollectionOwnership': 'track'
};

// 为每个服务创建 axios 实例
const serviceInstances: Record<string, AxiosInstance> = {};

Object.entries(SERVICE_PORTS).forEach(([service, port]) => {
  const instance = axios.create({
    baseURL: `http://localhost:${port}`,
    headers: {
      'Content-Type': 'application/json',
    },
  });
  
  // 添加请求拦截器
  instance.interceptors.request.use((config) => {
    const planContext: PlanContext = {
      traceID: generateTraceID(),
      transactionLevel: 0
    };
    
    if (config.data) {
      config.data = {
        ...config.data,
        planContext
      };
    }
    
    return config;
  });
  
  serviceInstances[service] = instance;
});

function generateTraceID(): string {
  return `${Date.now()}-${Math.random().toString(36).substr(2, 9)}`;
}

export const callAPI = async <T>(endpoint: string, data: any): Promise<T> => {
  const service = API_SERVICE_MAP[endpoint];
  if (!service) {
    throw new Error(`Unknown API endpoint: ${endpoint}`);
  }
  
  const instance = serviceInstances[service];
  console.log(`Calling ${endpoint} on ${service} service (port ${SERVICE_PORTS[service]})`);
  
  try {
    const response = await instance.post(`/api/${endpoint}`, data);
    return response.data;
  } catch (error: any) {
    console.error(`API call failed: ${endpoint}`, error);
    throw new Error(error.response?.data || error.message);
  }
};

export default serviceInstances.organize; // 默认导出 organize 服务
