import axios from 'axios';
import { getToken, getUser } from '../utils/storage';
import { PlanContext } from '../types';

const API_BASE_URL = process.env.REACT_APP_API_URL || 'http://localhost:10011';

const api = axios.create({
  baseURL: API_BASE_URL,
  headers: {
    'Content-Type': 'application/json',
  },
});

// Add plan context to all requests
api.interceptors.request.use((config) => {
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

function generateTraceID(): string {
  return `${Date.now()}-${Math.random().toString(36).substr(2, 9)}`;
}

export const callAPI = async <T>(endpoint: string, data: any): Promise<T> => {
  try {
    const response = await api.post(`/api/${endpoint}`, data);
    return response.data;
  } catch (error: any) {
    throw new Error(error.response?.data || error.message);
  }
};

export default api;