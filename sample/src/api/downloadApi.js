import axios from 'axios';

const baseURL = import.meta.env.VITE_API_BASE_URL || '/api/download';
const request = axios.create({
    baseURL,
    timeout: 60000,
    headers: {
        'Content-Type': 'application/json;charset=UTF-8'
    }
});

// 请求拦截器
request.interceptors.request.use(
    (config) => {
        // 添加请求ID
        config.headers['X-Request-Id'] = Math.random().toString(36).substr(2, 9);
        return config;
    },
    (error) => {
        console.error('Request error:', error);
        return Promise.reject(error);
    }
);

// 响应拦截器
request.interceptors.response.use(
    (response) => {
        return response;
    },
    (error) => {
        // 统一错误处理
        const message = error.response?.data?.msg || error.message || '请求失败';
        console.error(`Response error [${error.response?.status}]:`, message);
        return Promise.reject(new Error(message));
    }
);

// 请求缓存
const requestCache = new Map();

/**
 * 单文件下载（服务器本地）
 */
export const downloadSingleLocalFile = async (params, signal) => {
    return await request.post('/single/local', null, {
        params,
        responseType: 'blob',
        signal,
        headers: {
            'Range': params.rangeStart ? `bytes=${params.rangeStart}-` : ''
        }
    });
};

/**
 * 提交多文件下载任务
 */
export const submitMultiLocalFileTask = async (filePathList, userId) => {
    const res = await request.post('/multi/local/submit', filePathList, {
        params: { userId }
    });
    return res.data;
};

/**
 * 取消多文件任务
 */
export const cancelMultiFileTask = async (taskId) => {
    const res = await request.post(`/multi/cancel/${taskId}`);
    return res.data;
};

/**
 * 查询单个任务状态（带缓存）
 */
export const getTaskStatus = async (taskId) => {
    // 缓存5分钟
    const cacheKey = `task_${taskId}`;
    const cacheData = requestCache.get(cacheKey);

    if (cacheData && Date.now() - cacheData.timestamp < 5 * 60 * 1000) {
        return cacheData.data;
    }

    const res = await request.get(`/task/status/${taskId}`);

    // 更新缓存
    requestCache.set(cacheKey, {
        data: res.data,
        timestamp: Date.now()
    });

    // 限制缓存大小
    if (requestCache.size > 100) {
        const oldestKey = Array.from(requestCache.keys()).shift();
        requestCache.delete(oldestKey);
    }

    return res.data;
};

/**
 * 查询所有任务状态
 */
export const getAllTasks = async () => {
    const res = await request.get('/task/all');
    return res.data;
};

/**
 * 清理请求缓存
 */
export const clearRequestCache = () => {
    requestCache.clear();
};

export default {
    downloadSingleLocalFile,
    submitMultiLocalFileTask,
    cancelMultiFileTask,
    getTaskStatus,
    getAllTasks,
    clearRequestCache
};