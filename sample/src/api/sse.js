/**
 * 创建SSE连接，监听任务进度
 * @param {string} taskId 任务ID
 * @param {Function} onMessage 消息回调
 * @param {Function} onError 错误回调
 * @returns {EventSource} SSE实例
 */
// export function createSSE(taskId, onMessage, onError) {
//     if (!window.EventSource) {
//         onError(new Error('当前浏览器不支持SSE，请升级浏览器'))
//         return null
//     }
//
//     // 创建SSE连接
//     const source = new EventSource(`/api/download/progress/${taskId}`)
//
//     // 消息接收
//     source.onmessage = (event) => {
//         try {
//             const data = JSON.parse(event.data)
//             onMessage(data)
//         } catch (e) {
//             onError(e)
//         }
//     }
//
//     // 错误处理
//     source.onerror = (error) => {
//         onError(error)
//         // 连接关闭时自动重连（可选）
//         if (source.readyState === EventSource.CLOSED) {
//             setTimeout(() => createSSE(taskId, onMessage, onError), 1000)
//         }
//     }
//
//     // 连接关闭
//     source.onclose = () => {
//         console.log(`SSE连接已关闭: ${taskId}`)
//     }
//
//     return source
// }

// /**
//  * 监听任务进度（优化版SSE）
//  */
export function createSSE(taskId, onMessage, onError) {
    let retryCount = 0;
    let source = null;

    // 指数退避重连
    const connect = () => {
        // 关闭旧连接
        if (source) {
            source.close();
        }

        source = new EventSource(`/api/download/task/progress/${taskId}`);

        source.onmessage = (e) => {
            try {
                const data = JSON.parse(e.data);
                onMessage(data);
                retryCount = 0; // 成功接收消息重置重试次数

                // 任务完成/取消/不存在时关闭连接
                if (data.code === 404 || data.finished || data.cancelled) {
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
 * 关闭SSE连接
 * @param {EventSource} source SSE实例
 */
export function closeSSE(source) {
    if (source && source.readyState !== EventSource.CLOSED) {
        source.close()
    }
}