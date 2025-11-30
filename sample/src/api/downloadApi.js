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
 * 监听任务进度（优化版SSE）
 */
export const listenMultiFileProgress = (taskId, onMessage, onError) => {
    let retryCount = 0;
    let source = null;

    // 指数退避重连
    const connect = () => {
        // 关闭旧连接
        if (source) {
            source.close();
        }

        source = new EventSource(`${baseURL}/task/progress/${taskId}`);

        source.onmessage = (e) => {
            try {
                const data = JSON.parse(e.data);
                onMessage(data);
                retryCount = 0; // 成功接收消息重置重试次数

                // 任务完成/取消/不存在时关闭连接
                if (data.code === 404 || data.isFinished || data.isCancelled) {
                    source.close();
                }
            } catch (parseError) {
                console.error('SSE消息解析失败：', parseError, e.data);
                onError(new Error('进度消息格式错误'));
            }
        };

        source.onerror = (e) => {
            source.close();

            // 指数退避重连：1s, 2s, 4s, 8s... 最大30s
            const delay = Math.min(1000 * Math.pow(2, retryCount), 30000);
            retryCount++;

            if (retryCount <= 5) { // 最多重试5次
                setTimeout(connect, delay);
                onError(new Error(`SSE断开，${delay/1000}秒后重试（${retryCount}/5）`));
            } else {
                onError(new Error('SSE重连次数达到上限，请手动刷新'));
            }
        };
    };

    connect();

    return {
        source,
        close: () => {
            if (source) {
                source.close();
            }
        }
    };
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
    listenMultiFileProgress,
    getTaskStatus,
    getAllTasks,
    clearRequestCache
};