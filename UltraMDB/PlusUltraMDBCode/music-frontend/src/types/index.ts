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
  
  export interface Song {
    songID: string;
    name: string;
    releaseTime: number;
    creators: string[];
    performers: string[];
    genres: string[];
    lyricists?: string[];
    composers?: string[];
    arrangers?: string[];
    instrumentalists?: string[];
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