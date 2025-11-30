/**
 * SSE连接返回类型
 */
export interface SSEInstance {
    source: EventSource | null;
    close: () => void;
}

/**
 * SSE消息数据类型
 */
export interface SSEMessageData {
    code?: number;
    finished?: boolean;
    cancelled?: boolean;
    [key: string]: any;
}

/**
 * 创建SSE连接，监听任务进度
 * @param taskId 任务ID
 * @param onMessage 消息回调
 * @param onError 错误回调
 * @returns SSE实例
 */
export function createSSE(
    taskId: string,
    onMessage: (data: SSEMessageData) => void,
    onError: (error: Error) => void
): SSEInstance {
    let retryCount = 0;
    let source: EventSource | null = null;

    // 检查浏览器支持
    if (typeof window === 'undefined' || !window.EventSource) {
        onError(new Error('当前浏览器不支持SSE，请升级浏览器'));
        return {
            source: null,
            close: () => {}
        };
    }

    // 指数退避重连
    const connect = () => {
        // 关闭旧连接
        if (source) {
            source.close();
        }

        try {
            source = new EventSource(`/api/download/task/progress/${taskId}`);

            source.onmessage = (e) => {
                try {
                    const data: SSEMessageData = JSON.parse(e.data);
                    onMessage(data);
                    retryCount = 0; // 成功接收消息重置重试次数

                    // 任务完成/取消/不存在时关闭连接
                    if (data.code === 404 || data.finished || data.cancelled) {
                        source?.close();
                    }
                } catch (parseError) {
                    console.error('SSE消息解析失败：', parseError, e.data);
                    onError(new Error('进度消息消息格式错误'));
                }
            };

            source.onerror = () => {
                if (source) {
                    source.close();
                }

                // 指数退避重连：1s, 2s, 4s, 8s... 最大30s
                const delay = Math.min(1000 * Math.pow(2, retryCount), 30000);
                retryCount++;

                if (retryCount <= 5) { // 最多重试5次
                    setTimeout(connect, delay);
                    onError(new Error(`SSE断开，${delay/1000}秒后重试（${retryCount}/5）`));
                } else {
                    onError(new Error('SSE重连次数达到上限限，请手动刷新'));
                }
            };
        } catch (error) {
            onError(error instanceof Error ? error : new Error(String(error)));
        }
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
}

/**
 * 关闭SSE连接
 * @param sseInstance SSE实例
 */
export function closeSSE(sseInstance: SSEInstance) {
    sseInstance.close();
}