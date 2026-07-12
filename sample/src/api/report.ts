import axios from 'axios';
import type { GenerateRequest } from '../types/report';

const api = axios.create({
    baseURL: import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080/api',
    timeout: 30000,
});

// 响应拦截器：统一处理错误消息（可选，这里简单打印）
api.interceptors.response.use(
    (response) => response,
    (error) => {
        console.error('API Error:', error.message);
        return Promise.reject(error);
    }
);

export const reportApi = {
    generate(request: GenerateRequest) {
        return api.post('/reports/generate', request, {
            responseType: 'blob',
            headers: { 'Content-Type': 'application/json' },
        });
    },
    preview(request: GenerateRequest) {
        return api.post('/reports/preview', request, {
            responseType: 'blob',
            headers: { 'Content-Type': 'application/json' },
        });
    },
};