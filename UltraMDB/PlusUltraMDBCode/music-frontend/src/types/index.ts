export interface User {
    userID: string;
    account: string;
    userToken?: string;
  }
  
  export interface LoginCredentials {
    userName: string;
    hashedPassword: string;
  }
  
  export interface RegisterCredentials {
    userName: string;
    password: string;
  }
  
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