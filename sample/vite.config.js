import { defineConfig } from 'vite';
import vue from '@vitejs/plugin-vue';
import { visualizer } from 'rollup-plugin-visualizer';

// https://vitejs.dev/config/
export default defineConfig(({ mode }) => {
    const plugins = [vue()];

    // 打包体积分析
    if (mode === 'analyze') {
        plugins.push(visualizer({
            open: true,
            filename: 'dist/stats.html',
            gzipSize: true,
            brotliSize: true
        }));
    }

    return {
        plugins,
        build: {
            target: 'es2015',
            minify: 'terser', // 深度压缩
            terserOptions: {
                compress: {
                    drop_console: true, // 移除console
                    drop_debugger: true // 移除debugger
                },
                format: {
                    comments: false // 移除注释
                }
            },
            chunkSizeWarningLimit: 1000,
            rollupOptions: {
                output: {
                    // 代码分割
                    manualChunks: {
                        vendor: ['vue', 'axios'],
                        antd: ['ant-design-vue']
                    },
                    // 静态资源哈希
                    assetFileNames: 'assets/[name].[hash].[ext]',
                    chunkFileNames: 'js/[name].[hash].js',
                    entryFileNames: 'js/[name].[hash].js'
                }
            },
            // 开启压缩
            cssCodeSplit: true,
            reportCompressedSize: true
        },
        optimizeDeps: {
            include: ['vue', 'axios', 'ant-design-vue/es/locale/zh_CN'],
            esbuildOptions: {
                target: 'es2015'
            }
        },
        server: {
            port: 3000,
            proxy: {
                '/api': {
                    target: 'http://localhost:8080',
                    changeOrigin: true,
                    timeout: 60000
                }
            }
        }
    };
});