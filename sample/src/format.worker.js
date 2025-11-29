/**
 * 文件大小格式化Worker
 */
self.onmessage = (e) => {
    try {
        const { id, bytes, decimals = 2 } = e.data;

        if (bytes === 0) {
            self.postMessage({ id, result: '0 B' });
            return;
        }

        const k = 1024;
        const sizes = ['B', 'KB', 'MB', 'GB', 'TB'];
        const i = Math.floor(Math.log(bytes) / Math.log(k));
        const result = parseFloat((bytes / Math.pow(k, i)).toFixed(decimals)) + ' ' + sizes[i];

        self.postMessage({ id, result });
    } catch (error) {
        console.error('Format worker error:', error);
        self.postMessage({ id: e.data.id, result: '0 B' });
    }
};