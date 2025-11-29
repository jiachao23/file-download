import { createApp } from 'vue';
import './style.css';
import DownloadManager from './components/DownloadManager.vue';
import Antd from 'ant-design-vue';
import 'ant-design-vue/dist/reset.css';

const app = createApp(DownloadManager);
app.use(Antd);
app.mount('#app');