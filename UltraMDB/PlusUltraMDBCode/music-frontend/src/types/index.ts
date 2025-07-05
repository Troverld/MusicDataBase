export interface User {
  userID: string;
  account: string;
  userToken?: string;
}

export interface LoginCredentials {
  userName: string;
  password: string;
}

export interface RegisterCredentials {
  userName: string;
  password: string;
}

// 添加新的类型定义以匹配后端返回
export type LoginResponse = [
  [string, string] | null,  // [userID, userToken] 或 null
  string                    // 错误信息
];

export type RegisterResponse = [string | null, string];

// 新增：CreatorID_Type 接口，匹配后端结构
export interface CreatorID_Type {
  creatorType: 'artist' | 'band';
  id: string;
}

// 更新 Song 接口以匹配后端新结构（只支持新格式）
export interface Song {
  songID: string;
  name: string;
  releaseTime: number;
  creators: CreatorID_Type[];  // 只支持新的 CreatorID_Type 格式
  performers: string[];
  genres: string[];
  lyricists?: string[];
  composers?: string[];
  arrangers?: string[];
  instrumentalists?: string[];
  uploaderID?: string;      // 添加上传者字段（对应后端的 uploaderID）
  uploadTime?: number;      // 添加上传时间字段
}

export interface ApiResponse<T> {
  success: boolean;
  data?: T;
  error?: string;
}

export interface PlanContext {
  traceID: string;
  transactionLevel: number;
}

// 添加艺术家和乐队的类型定义，与Scala API保持一致
export interface Artist {
  artistID: string;
  name: string;
  bio: string;
  managers?: string[];
}

export interface Band {
  bandID: string;
  name: string;
  members: string[];
  bio: string;
  managers?: string[];
}

// 添加专辑和歌单的类型定义
export interface Album {
  albumID: string;
  name: string;
  description: string;
  releaseTime: number;
  creators: string[];
  collaborators: string[];
  contents: string[];
}

export interface Collection {
  collectionID: string;
  name: string;
  description: string;
  contents: string[];
  maintainers: string[];
  owner: string;
}

// 添加曲风类型定义
export interface Genre {
  genreID: string;
  name: string;
  description: string;
}