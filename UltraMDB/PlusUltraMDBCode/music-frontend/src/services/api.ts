import axios, { AxiosInstance } from 'axios';
import { getToken, getUser } from '../utils/storage';
import { PlanContext } from '../types';

// 服务端口映射，根据后端ServiceUtils.portMap配置
// const SERVICE_PORTS = {
//   organize: 10011,     // OrganizeService
//   music: 10010,        // MusicService
//   creator: 10012,      // CreatorService
//   track: 10013,        // TrackService
//   statistics: 10013    // StatisticsService
// };
const SERVICE_PORTS = {
  organize: 10011,     // OrganizeService
  music: 10011,        // MusicService
  creator: 10011,      // CreatorService
  track: 10011,        // TrackService
  statistics: 10011    // StatisticsService
};

// API到服务的映射
const API_SERVICE_MAP: Record<string, keyof typeof SERVICE_PORTS> = {
  // OrganizeService APIs
  'UserLoginMessage': 'organize',
  'UserRegisterMessage': 'organize',
  'UserLogoutMessage': 'organize',
  'validateUserMapping': 'organize',
  'validateAdminMapping': 'organize',
  'SubmitArtistAuthRequestMessage': 'organize',
  'SubmitBandAuthRequestMessage': 'organize',
  'ApproveArtistAuthRequestMessage': 'organize',
  'ApproveBandAuthRequestMessage': 'organize',
  
  // MusicService APIs
  'UploadNewSong': 'music',
  'UpdateSongMetadata': 'music',
  'DeleteSong': 'music',
  'SearchSongsByName': 'music',
  'GetSongByID': 'music',
  'GetSongList': 'music',
  'GetSongProfile': 'music',
  'CreateNewGenre': 'music',
  'DeleteGenre': 'music',
  'GetGenreList': 'music',
  'FilterSongsByEntity': 'music',
  'ValidateSongOwnership': 'music',
  
  // CreatorService APIs
  'CreateArtistMessage': 'creator',
  'UpdateArtistMessage': 'creator',
  'DeleteArtistMessage': 'creator',
  'GetArtistByID': 'creator',
  'SearchArtistByName': 'creator',
  'GetAllCreators': 'creator',
  'SearchAllBelongingBands': 'creator',
  'CreateBandMessage': 'creator',
  'UpdateBandMessage': 'creator',
  'DeleteBandMessage': 'creator',
  'GetBandByID': 'creator',
  'SearchBandByName': 'creator',
  'AddArtistManager': 'creator',
  'AddBandManager': 'creator',
  
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
  'validateCollectionOwnership': 'track',

  // StatisticsService APIs
  'RateSong': 'statistics',
  'GetSongRate': 'statistics',
  'GetAverageRating': 'statistics',
  'GetSongPopularity': 'statistics',
  'LogPlayback': 'statistics',
  'GetUserPortrait': 'statistics',
  'GetUserSongRecommendations': 'statistics',
  'GetNextSongRecommendation': 'statistics',
  'GetSimilarSongs': 'statistics',
  'GetSimilarCreators': 'statistics',
  'GetCreatorCreationTendency': 'statistics',
  'GetCreatorGenreStrength': 'statistics'
};

// 为每个服务创建axios实例
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

export default serviceInstances.organize; // 默认导出organize服务
