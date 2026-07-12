import { createApp } from 'vue';
import Antd from 'ant-design-vue';
import App from './App.vue';
import './assets/styles/global.less';
import 'ant-design-vue/dist/reset.css'; // Antd v4 重置样式

const app = createApp(App);

app.use(Antd);
app.mount('#app');