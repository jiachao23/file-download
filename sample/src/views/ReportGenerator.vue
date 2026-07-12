<template>
  <div class="app-layout">
    <!-- 顶部导航 -->
    <header class="page-header">
      <div class="header-inner">
        <div>
          <h1>
            <FileTextOutlined />
            企业级动态报表平台
          </h1>
          <span class="subtitle">配置参数 · 数据聚合 · 一键导出</span>
        </div>
        <div class="user-info">
          <span>Admin</span>
        </div>
      </div>
    </header>

    <!-- 主体内容 -->
    <main class="container">
      <a-row :gutter="[24, 24]">
        <!-- 左侧：配置表单 -->
        <a-col :xs="24" :md="8" :lg="7">
          <a-card
              title="报表参数配置"
              :bordered="false"
              class="config-card"
              size="large"
          >
            <a-form
                layout="vertical"
                :model="formState"
                ref="formRef"
                @finish="handleFinish"
            >
              <a-form-item label="报告标题" name="title">
                <a-input
                    v-model:value="formState.params.title"
                    placeholder="例如：2026年度销售分析报告"
                    size="large"
                >
                  <template #prefix><EditOutlined /></template>
                </a-input>
              </a-form-item>

              <a-form-item label="统计日期" name="date">
                <a-date-picker
                    v-model:value="formState.params.date"
                    placeholder="选择统计截止日期"
                    style="width: 100%"
                    size="large"
                    format="YYYY-MM-DD"
                    value-format="YYYY-MM-DD"
                >
                  <template #suffix-icon><CalendarOutlined /></template>
                </a-date-picker>
              </a-form-item>

              <a-form-item label="目标格式" name="targetFormat">
                <a-select
                    v-model:value="formState.targetFormat"
                    placeholder="默认跟随模版"
                    size="large"
                    allowClear
                >
                  <a-select-option value="WORD">Word (.docx)</a-select-option>
                  const errors = await formRef.value?.validate().catch(() => null);
   if (errors) return;
      <a-select-option value="EXCEL">Excel (.xlsx)</a-select-option>
                  <a-select-option value="PDF">PDF (.pdf)</a-select-option>
                </a-select>
              </a-form-item>

              <a-divider style="margin: 16px 0" />

              <a-space direction="vertical" style="width: 100%" size="large">
                <a-button
                    type="default"
                    block
                    size="large"
                    :loading="loading.preview"
                    @click="handlePreview"
                >
                  <EyeOutlined /> 预览数据校验
                </a-button>

                <a-button
                    type="primary"
                    block
                    size="large"
                    :loading="loading.generate"
                    html-type="submit"
                    class="primary-btn-glow"
                >
                  <DownloadOutlined /> 生成并下载报表
                </a-button>
              </a-space>
            </a-form>
          </a-card>

          <!-- 提示信息卡片 -->
          <a-alert
              v-if="statusMsg"
              :message="statusMsg.title"
              :description="statusMsg.desc"
              :type="statusMsg.type"
              show-icon
              closable
              style="margin-top: 16px"
              @close="statusMsg = null"
          />
        </a-col>

        <!-- 右侧：预览展示 -->
        <a-col :xs="24" :md="16" :lg="17">
          <a-card
              title="实时预览 / 结果反馈"
              :bordered="false"
              class="preview-card"
              size="large"
          >
            <template #extra>
              <a-tag color="blue">Template ID: {{ formState.templateId }}</a-tag>
            </template>

            <div class="preview-area">
              <!-- 空状态 -->
              <a-empty
                  v-if="!hasPreviewed"
                  description="请在左侧填写参数并点击「预览数据校验」"
              >
                <template #image>
                  <FileProtectOutlined style="font-size: 64px; color: #d9d9d9" />
                </template>
              </a-empty>

              <!-- 模拟文档内容 -->
              <div v-else class="mock-doc fade-in">
                <div class="mock-doc-header">
                  <h2>{{ formState.params.title || '未命名报告' }}</h2>
                  <span>日期：{{ formState.params.date || '-' }}</span>
                </div>

                <div class="doc-content">
                  <p><strong>数据校验状态：</strong>
                    <a-tag color="success"><CheckCircleOutlined /> 通过</a-tag>
                  </p>
                  <p><strong>聚合数据预览：</strong></p>

                  <a-table
                      :columns="columns"
                      :data-source="mockData"
                      :pagination="false"
                      size="small"
                      bordered
                      class="mt-2"
                  >
                    <template #bodyCell="{ column, record }">
                      <template v-if="column.key === 'trend'">
                        <span :style="record.trend === 'up' ? 'color: #52c41a' : 'color: #ff4d4f'">
                          <ArrowUpOutlined v-if="record.trend === 'up'" />
                          <ArrowDownOutlined v-else />
                          {{ record.trendValue }}
                        </span>
                      </template>
                    </template>
                  </a-table>

                  <div class="mock-doc-footer">
                    <p>注：此页面仅为数据结构示意，最终文件排版以导出的 Word/Excel 为准。</p>
                    <p>生成时间：{{ new Date().toLocaleString() }}</p>
                  </div>
                </div>
              </div>
            </div>
          </a-card>
        </a-col>
      </a-row>
    </main>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue';
import { message, notification } from 'ant-design-vue';
import type { Rule } from 'ant-design-vue/es/form';
import type { GenerateRequest, MockDataItem } from '../types/report';
import { reportApi } from '../api/report';

// Icons
import {
  FileTextOutlined,
  EditOutlined,
  CalendarOutlined,
  EyeOutlined,
  DownloadOutlined,
  FileProtectOutlined,
  CheckCircleOutlined,
  ArrowUpOutlined,
  ArrowDownOutlined,
} from '@ant-design/icons-vue';

// State
const formRef = ref<any>(null);
const loading = reactive({ preview: false, generate: false });
const hasPreviewed = ref(false);
const statusMsg = ref<{ title: string; desc?: string; type: 'success' | 'info' | 'warning' | 'error' } | null>(null);

// Form Data
const formState = reactive<GenerateRequest>({
  templateId: 'tpl_001',
  params: {
    title: '',
    date: undefined, // Dayjs object or string depending on config
  },
  targetFormat: undefined,
});

// Validation Rules
const rules: Record<string, Rule[]> = {
  title: [{ required: true, message: '请输入报告标题', trigger: 'blur' }],
  date: [{ required: true, message: '请选择统计日期', trigger: 'change' }],
};

// Mock Data for Preview
const columns = [
  { title: '指标项目', dataIndex: 'item', key: 'item' },
  { title: '数值', dataIndex: 'value', key: 'value' },
  { title: '趋势', key: 'trend', slots: { customRender: 'trend' } },
];

const mockData: MockDataItem[] = [
  { key: '1', item: '总销售额 (Total Sales)', value: '¥ 125,000.50', trend: 'up', trendValue: '15.2%' },
  { key: '2', item: '订单总量 (Orders)', value: '3,420', trend: 'up', trendValue: '8.5%' },
  { key: '3', item: '新增用户 (New Users)', value: '856', trend: 'down', trendValue: '2.1%' },
  { key: '4', item: '平均客单价 (AOV)', value: '¥ 36.5', trend: 'stable', trendValue: '0.0%' },
];

// Actions
const handleFinish = async () => {
  // 点击“生成并下载”时触发，先校验表单
  await handleDownload();
};

const handlePreview = async () => {
  try {
    await formRef.value?.validate();
  } catch (e) {
    return;
  }

  loading.preview = true;
  statusMsg.value = null;
  hasPreviewed.value = false;

  try {
    // 调用预览接口 (实际业务中可能只校验不返回流，这里为了演示调用同一接口)
    await reportApi.preview(formState);

    hasPreviewed.value = true;
    statusMsg.value = {
      title: '数据校验成功',
      desc: '后端数据聚合完成，模版渲染逻辑已验证，可以安全导出。',
      type: 'success',
    };

    notification.success({
      message: '预览就绪',
      description: '数据校验通过，请查看右侧预览或点击下载。',
      placement: 'topRight',
    });
  } catch (error: any) {
    statusMsg.value = {
      title: '校验失败',
      desc: error.response?.data?.message || '无法连接后端服务，请检查网络。',
      type: 'error',
    };
    message.error('预览请求失败');
  } finally {
    loading.preview = false;
  }
};

const handleDownload = async () => {
  try {
    await formRef.value?.validate();
  } catch (e) {
    return;
  }

  loading.generate = true;
  try {
    const response = await reportApi.generate(formState);

    // 处理 Blob 下载
    const blob = new Blob([response.data], { type: response.headers['content-type'] });
    const url = window.URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;

    // 解析文件名
    const disposition = response.headers['content-disposition'];
    let filename = `report_${formState.params.date}.docx`;
    if (disposition) {
      const match = disposition.match(/filename="?([^"]+)"?/);
      if (match && match[1]) filename = decodeURIComponent(match[1]);
    }

    link.download = filename;
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
    window.URL.revokeObjectURL(url);

    notification.success({
      message: '下载开始',
      description: `文件 ${filename} 正在下载中...`,
      placement: 'topRight',
    });
  } catch (error: any) {
    message.error('导出失败：' + (error.message || '未知错误'));
  } finally {
    loading.generate = false;
  }
};
</script>

<style scoped lang="less">
@import '../assets/styles/variables';

.app-layout {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
}

.config-card, .preview-card {
  height: 100%;
  border-radius: @border-radius-lg;
  box-shadow: 0 1px 2px 0 rgba(0, 0, 0, 0.03);

  :deep(.ant-card-head) {
    font-weight: 600;
    font-size: 16px;
    border-bottom: 1px solid #f0f0f0;
  }
}

.primary-btn-glow {
  box-shadow: 0 2px 0 rgba(0, 0, 0, 0.045);
  transition: all 0.3s;

  &:hover {
    transform: translateY(-1px);
    box-shadow: 0 4px 12px rgba(22, 119, 255, 0.4);
  }
}

.preview-area {
  min-height: 450px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #fafafa;
  border-radius: @border-radius-base;
  padding: 24px;
}

.fade-in {
  animation: fadeIn 0.4s ease-out;
}

@keyframes fadeIn {
  from { opacity: 0; transform: translateY(10px); }
  to { opacity: 1; transform: translateY(0); }
}

.mt-2 {
  margin-top: 16px;
}

// 响应式调整
@media (max-width: 768px) {
  .header-inner {
    flex-direction: column;
    align-items: flex-start;
    gap: 10px;
  }
  .user-info {
    display: none; // 移动端隐藏用户信息
  }
}
</style>