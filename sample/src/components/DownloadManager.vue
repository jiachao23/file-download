<template>
  <div class="download-manager">
    <!-- 主Tab面板 -->
    <div class="main-panel">
      <a-tabs v-model:activeKey="activeTab" type="card" size="middle">
        <a-tab-pane tab="单文件下载" key="single">
          <a-form
              layout="vertical"
              :label-col="{ span: 4 }"
              :wrapper-col="{ span: 20 }"
              :model="singleForm"
          >
            <a-form-item
                label="服务器文件路径"
                name="filePath"
                :rules="[{ required: true, message: '请输入服务器文件路径' }]"
            >
              <a-input
                  v-model:value="singleForm.filePath"
                  placeholder="如：/data/files/test.pdf"
                  allow-clear
              />
            </a-form-item>
            <a-form-item>
              <a-button type="primary" @click="handleSingleDownload">
                <template #icon>
                  <DownloadOutlined />
                </template>
                下载
              </a-button>
            </a-form-item>
          </a-form>
        </a-tab-pane>
        <a-tab-pane tab="多文件下载" key="multi">
          <a-form
              layout="vertical"
              :label-col="{ span: 4 }"
              :wrapper-col="{ span: 20 }"
              :model="multiForm"
          >
            <a-form-item
                label="用户ID"
                name="userId"
                :rules="[{ required: true, message: '请输入用户ID' }]"
            >
              <a-input
                  v-model:value="multiForm.userId"
                  placeholder="用于隔离文件目录"
                  allow-clear
              />
            </a-form-item>
            <a-form-item
                label="服务器文件路径（每行一个）"
                name="filePathStr"
                :rules="[{ required: true, message: '请输入文件路径' }]"
            >
              <a-textarea
                  v-model:value="multiForm.filePathStr"
                  rows="5"
                  placeholder="/data/files/test1.pdf&#10;/data/files/test2.zip"
                  allow-clear
              />
            </a-form-item>
            <a-form-item>
              <a-button
                  type="primary"
                  @click="handleMultiSubmit"
                  :loading="multiSubmitting"
              >
                <template #icon>
                  <UploadOutlined />
                </template>
                {{ multiSubmitting ? '提交中...' : '提交下载任务' }}
              </a-button>
            </a-form-item>
          </a-form>
        </a-tab-pane>
      </a-tabs>
    </div>

    <!-- 触发条：仅核心显示规则保留内联 !important -->
    <div
        class="sidebar-trigger-wrapper"
        @mouseenter="handleTriggerMouseEnter"
        @mouseleave="handleTriggerMouseLeave"
    >
      <a-tooltip placement="left" title="下载任务面板" :mouseEnterDelay="0.1">
        <a-button
            type="primary"
            shape="circle"
            size="large"
            class="main-trigger-btn"
            @click="toggleDrawer"
            style="width: 40px; height: 40px;"
        >
          <template #icon>
            <UnorderedListOutlined style="font-size: 20px;" />
          </template>
        </a-button>
      </a-tooltip>

      <a-tooltip placement="left" :title="isDrawerPinned ? '取消固定' : '固定面板'" :mouseEnterDelay="0.1">
        <a-button
            shape="circle"
            size="middle"
            class="pin-trigger-btn"
            @click.stop="toggleDrawerPin"
            style="width: 40px; height: 40px;"
        >
          <PushpinOutlined
              :style="{
              transform: isDrawerPinned ? 'rotate(0deg)' : 'rotate(-45deg)',
              transition: 'transform 0.2s ease',
              'font-size': '16px',
              'color': '#1890ff'
            }"
          />
        </a-button>
      </a-tooltip>
    </div>

    <!-- 抽屉侧边栏 -->
    <a-drawer
        title="下载任务列表"
        placement="right"
        :width="480"
        :visible="drawerVisible"
        :closable="!isDrawerPinned"
        :mask="false"
        :destroyOnClose="true"
        @close="handleDrawerClose"
        @mouseenter="handleDrawerMouseEnter"
        @mouseleave="handleDrawerMouseLeave"
        class="task-drawer"
    >
      <template #extra>
        <a-space size="small">
          <a-button size="small" @click="clearFinishedTasks">
            <template #icon>
              <DeleteOutlined />
            </template>
            清空已完成
          </a-button>
          <a-button size="small" type="danger" @click="cancelAllUnfinishedTasks">
            <template #icon>
              <CloseOutlined />
            </template>
            取消所有
          </a-button>
        </a-space>
      </template>

      <!-- 空状态 -->
      <a-empty
          v-if="taskList.length === 0"
          description="暂无下载任务，请先提交下载任务"
      >
        <a-button type="primary" @click="activeTab = 'single'">
          去下载文件
        </a-button>
      </a-empty>

      <!-- 任务列表 -->
      <div class="task-list-container" v-else ref="taskListContainer">
        <div
            class="virtual-list"
            :style="{ height: `${taskList.length * 180}px`, position: 'relative' }"
        >
          <div
              class="virtual-list-content"
              :style="{ transform: `translateY(${scrollTop.value}px)`, position: 'absolute', top: 0, left: 0, width: '100%' }"
          >
            <a-card
                v-for="task in visibleTasks"
                :key="task.taskId"
                class="task-card"
                :bordered="true"
                size="default"
                style="margin-bottom: 12px; padding: 8px 0;"
            >
              <template #title>
                <a-space style="width: 100%; justify-content: space-between;">
                  <a-tag :color="task.type === 'single' ? 'blue' : 'purple'">
                    {{ task.type === 'single' ? '单文件' : '多文件' }}
                  </a-tag>
                  <a-button
                      size="small"
                      type="text"
                      danger
                      @click.stop="cancelTask(task.taskId)"
                      :disabled="task.finished || task.cancelled"
                  >
                    <template #icon>
                      <CloseOutlined />
                    </template>
                    取消
                  </a-button>
                </a-space>
              </template>

              <!-- 进度条 -->
              <a-progress
                  :percent="task.progress"
                  :status="getProgressStatus(task)"
                  size="default"
                  :stroke-color="{
                  'active': '#1677ff',
                  'success': '#52c41a',
                  'exception': '#ff4d4f'
                }"
                  class="task-progress"
                  style="margin: 10px 0;"
              />

              <!-- 任务信息 -->
              <a-space direction="vertical" size="middle" class="task-info" style="width: 100%;">
                <div style="display: flex; justify-content: space-between; align-items: center;">
                  <a-text style="font-size: 14px; color: #333;">
                    {{ formatBytes(task.downloadedBytes) }} / {{ formatBytes(task.totalBytes) }}
                  </a-text>
                  <a-tag :color="getStatusColor(task)" size="small">
                    {{ getTaskStatusText(task) }}
                  </a-tag>
                </div>

                <a-text v-if="task.type === 'multi'" style="font-size: 13px; color: #666;">
                  文件进度：{{ task.completedCount }}/{{ task.totalCount }}
                </a-text>

                <a-text
                    v-if="task.filePath"
                    ellipsis
                    :title="task.filePath"
                    style="font-size: 13px; color: #666; line-height: 1.5;"
                >
                  路径：{{ task.filePath }}
                </a-text>
              </a-space>
            </a-card>
          </div>
        </div>
      </div>
    </a-drawer>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, watch, onMounted, onUnmounted } from 'vue';
import { message, FormRules } from 'ant-design-vue';
import {
  DownloadOutlined, UploadOutlined, PushpinOutlined,
  DeleteOutlined, CloseOutlined, UnorderedListOutlined
} from '@ant-design/icons-vue';
import downloadApi from "../api/downloadApi.ts";
import sse, {createSSE} from "../api/sse.ts";

// ========== 类型定义 ==========
type TaskType = 'single' | 'multi';
type ProgressStatus = 'active' | 'success' | 'exception';

interface DownloadTask {
  taskId: string;
  type: TaskType;
  filePath: string;
  progress: number;
  downloadedBytes: number;
  totalBytes: number;
  finished: boolean;
  cancelled: boolean;
  completedCount?: number;
  totalCount?: number;
  createTime: number;
}

interface SingleForm {
  filePath: string;
}

interface MultiForm {
  userId: string;
  filePathStr: string;
}

interface SSE {
  close: () => void;
}

// ========== 表单验证规则 ==========
const singleFormRules: FormRules = {
  filePath: [{ required: true, message: '请输入服务器文件路径', trigger: 'blur' }]
};

const multiFormRules: FormRules = {
  userId: [{ required: true, message: '请输入用户ID', trigger: 'blur' }],
  filePathStr: [{ required: true, message: '请输入文件路径', trigger: 'blur' }]
};

// ========== 响应式状态 ==========
const activeTab = ref<TaskType>('single');
const drawerVisible = ref<boolean>(false);
const isDrawerPinned = ref<boolean>(false);
const isMouseOverDrawer = ref<boolean>(false);
const isMouseOverTrigger = ref<boolean>(false);
const multiSubmitting = ref<boolean>(false);

const singleForm = reactive<SingleForm>({
  filePath: ''
});

const multiForm = reactive<MultiForm>({
  userId: '',
  filePathStr: ''
});

const taskList = ref<DownloadTask[]>([]);
const taskListContainer = ref<HTMLDivElement | null>(null);
const scrollTop = ref<number>(0);
const itemHeight = 180;
const visibleCount = ref<number>(6);
const sseInstances = new Map<string, SSE>();

// ========== 计算属性 ==========
const visibleTasks = computed<DownloadTask[]>(() => {
  const start = Math.floor(scrollTop.value / itemHeight);
  const end = start + visibleCount.value;
  return taskList.value.slice(start, end);
});

// ========== 工具函数 ==========
const formatBytes = (bytes: number): string => {
  if (bytes === 0) return '0 B';
  const k = 1024;
  const sizes = ['B', 'KB', 'MB', 'GB', 'TB'];
  const i = Math.floor(Math.log(bytes) / Math.log(k));
  return `${parseFloat((bytes / Math.pow(k, i)).toFixed(2))} ${sizes[i]}`;
};

const getProgressStatus = (task: DownloadTask): ProgressStatus => {
  if (task.cancelled) return 'exception';
  if (task.finished) return 'success';
  return 'active';
};

const getTaskStatusText = (task: DownloadTask): string => {
  if (task.cancelled) return '已取消';
  if (task.finished) return '已完成';
  return '下载中';
};

const getStatusColor = (task: DownloadTask): string => {
  if (task.cancelled) return 'error';
  if (task.finished) return 'success';
  return 'processing';
};

// ========== 交互逻辑 ==========
const handleTriggerMouseEnter = (): void => {
  isMouseOverTrigger.value = true;
  if (!isDrawerPinned.value) {
    drawerVisible.value = true;
  }
};

const handleTriggerMouseLeave = (): void => {
  isMouseOverTrigger.value = false;
  if (!isDrawerPinned.value && !isMouseOverDrawer.value && taskList.value.length === 0) {
    drawerVisible.value = false;
  }
};

const handleDrawerMouseEnter = (): void => {
  isMouseOverDrawer.value = true;
};

const handleDrawerMouseLeave = (): void => {
  isMouseOverDrawer.value = false;
  if (!isDrawerPinned.value && !isMouseOverTrigger.value && taskList.value.length === 0) {
    drawerVisible.value = false;
  }
};

const toggleDrawer = (): void => {
  drawerVisible.value = !drawerVisible.value;
};

const toggleDrawerPin = (): void => {
  isDrawerPinned.value = !isDrawerPinned.value;
  if (isDrawerPinned.value) {
    drawerVisible.value = true;
    message.success('任务面板已固定');
  } else {
    message.success('任务面板已取消固定');
    if (taskList.value.length === 0) {
      drawerVisible.value = false;
    }
  }
};

const handleDrawerClose = (): void => {
  drawerVisible.value = false;
  if (taskList.value.length === 0) {
    isDrawerPinned.value = false;
  }
};

// ========== 任务操作 ==========
const refreshAllTaskStatus = async (): Promise<void> => {
  try {
    const serverTasks = await downloadApi.getAllTasks();
    if (serverTasks.length > 0) {
      taskList.value = serverTasks.map(task => ({
        ...task,
        progress: task.progress || 0,
        downloadedBytes: task.downloadedBytes || 0,
        totalBytes: task.totalBytes || 0,
        completedCount: task.completedCount || 0,
        totalCount: task.totalCount || 0
      }));
    }
  } catch (e) {
    console.error('刷新任务状态失败：', e);
    message.warning('无法获取最新任务状态，使用本地缓存');
  }
};

const rebuildSSEConnections = (): void => {
  const unfinishedMultiTasks = taskList.value.filter(
      task => task.type === 'multi' && !task.finished && !task.cancelled
  );

  unfinishedMultiTasks.forEach(task => {
    if (!sseInstances.has(task.taskId)) {
      downloadApi.getTaskStatus(task.taskId).then(latestTask => {
        const taskIndex = taskList.value.findIndex(t => t.taskId === task.taskId);
        if (taskIndex !== -1) {
          taskList.value[taskIndex] = { ...taskList.value[taskIndex], ...latestTask };
        }

        if (!latestTask.finished && !latestTask.cancelled) {
          const sse = createSSE(
              task.taskId,
              (data) => {
                const taskIndex = taskList.value.findIndex(t => t.taskId === task.taskId);
                if (taskIndex !== -1) {
                  taskList.value[taskIndex] = { ...taskList.value[taskIndex], ...data };
                  if (data.finished && !data.cancelled) {
                    message.success('多文件下载任务完成！');
                  }
                }
              },
              (e) => {
                console.error(`SSE连接失败(${task.taskId})：`, e);
                if ((e.message as string).includes('重试') || (e.message as string).includes('断开')) {
                  message.warning(e.message);
                } else if ((e.message as string) !== '进度消息格式错误') {
                  message.error(`任务${task.taskId}：${e.message}`);
                }
              }
          );
          sseInstances.set(task.taskId, sse);
        }
      }).catch(err => {
        console.error('查询任务状态失败：', err);
      });
    }
  });
};

const handleSingleDownload = async (): Promise<void> => {
  if (!singleForm.filePath) {
    message.warning('请输入服务器文件路径！');
    return;
  }

  const taskId = `single-${Date.now()}`;
  const newTask: DownloadTask = {
    taskId,
    type: 'single',
    filePath: singleForm.filePath,
    progress: 0,
    downloadedBytes: 0,
    totalBytes: 0,
    finished: false,
    cancelled: false,
    createTime: Date.now()
  };

  taskList.value = [...taskList.value, newTask];

  try {
    const response = await downloadApi.downloadSingleLocalFile({
      filePath: singleForm.filePath,
      rangeStart: 0
    });

    const totalBytes = Number(response.headers['content-length']) || 0;
    const taskIndex = taskList.value.findIndex(t => t.taskId === taskId);

    if (taskIndex !== -1) {
      taskList.value[taskIndex] = {
        ...taskList.value[taskIndex],
        totalBytes,
        downloadedBytes: totalBytes,
        progress: 100,
        finished: true
      };
    }

    const fileNameMatch = response.headers['content-disposition']?.match(/filename="(.*)"/);
    const fileName = fileNameMatch ? decodeURIComponent(fileNameMatch[1]) : 'download.file';
    const url = URL.createObjectURL(response.data);
    const a = document.createElement('a');
    a.href = url;
    a.download = fileName;
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
    URL.revokeObjectURL(url);

    message.success('单文件下载完成！');
  } catch (e) {
    const taskIndex = taskList.value.findIndex(t => t.taskId === taskId);
    if (taskIndex !== -1) {
      taskList.value[taskIndex] = {
        ...taskList.value[taskIndex],
        finished: true,
        cancelled: true,
        progress: 0
      };
    }
    message.error(`下载失败：${(e as Error).message}`);
  }
};

const handleMultiSubmit = async (): Promise<void> => {
  if (!multiForm.userId || !multiForm.filePathStr) {
    message.warning('请输入用户ID和文件路径！');
    return;
  }

  const filePathList = multiForm.filePathStr
      .split('\n')
      .map(p => p.trim())
      .filter(p => p);

  if (filePathList.length === 0) {
    message.warning('请输入有效的文件路径！');
    return;
  }

  multiSubmitting.value = true;

  try {
    const taskId = await downloadApi.submitMultiLocalFileTask(filePathList, multiForm.userId);
    const newTask: DownloadTask = {
      taskId,
      type: 'multi',
      filePath: multiForm.filePathStr,
      progress: 0,
      downloadedBytes: 0,
      totalBytes: 0,
      finished: false,
      cancelled: false,
      completedCount: 0,
      totalCount: filePathList.length,
      createTime: Date.now()
    };

    taskList.value = [...taskList.value, newTask];
    rebuildSSEConnections();

    message.success('多文件下载任务已提交！');
  } catch (e) {
    message.error(`提交任务失败：${(e as Error).message}`);
  } finally {
    multiSubmitting.value = false;
  }
};

const cancelTask = async (taskId: string): Promise<void> => {
  const taskIndex = taskList.value.findIndex(t => t.taskId === taskId);
  if (taskIndex === -1) {
    message.warning('任务不存在！');
    return;
  }

  const task = taskList.value[taskIndex];

  if (task.type === 'multi') {
    try {
      const res = await downloadApi.cancelMultiFileTask(taskId);
      taskList.value[taskIndex] = {
        ...task,
        cancelled: true,
        finished: true,
        progress: 0
      };

      if (sseInstances.has(taskId)) {
        sseInstances.get(taskId)?.close();
        sseInstances.delete(taskId);
      }

      message.info(res.msg || '任务已取消');
    } catch (e) {
      message.error(`取消任务失败：${(e as Error).message}`);
    }
  } else {
    taskList.value[taskIndex] = {
      ...task,
      cancelled: true,
      finished: true,
      progress: 0
    };
    message.info('单文件下载任务已取消');
  }
};

const clearFinishedTasks = (): void => {
  taskList.value = taskList.value.filter(
      task => !task.finished || task.cancelled
  );
  message.success('已清空已完成任务');
};

const cancelAllUnfinishedTasks = async (): Promise<void> => {
  const unfinishedTasks = taskList.value.filter(
      task => !task.finished && !task.cancelled
  );

  if (unfinishedTasks.length === 0) {
    message.warning('暂无未完成任务');
    return;
  }

  try {
    await Promise.allSettled(
        unfinishedTasks.map(task => cancelTask(task.taskId))
    );
    message.success('已取消所有未完成任务');
  } catch (e) {
    message.error(`取消任务失败：${(e as Error).message}`);
  }
};

const cleanExpiredTasks = (): void => {
  const now = Date.now();
  taskList.value = taskList.value.filter(task => {
    const createTime = task.createTime || 0;
    return !task.finished || task.cancelled || (now - createTime < 7 * 24 * 60 * 60 * 1000);
  });
};

// ========== 生命周期 ==========
onMounted(async () => {
  // 强制显示触发条（仅核心规则）
  const triggerWrapper = document.querySelector('.sidebar-trigger-wrapper');
  if (triggerWrapper) {
    triggerWrapper.style.display = 'flex';
    triggerWrapper.style.zIndex = '9999';
  }

  if (taskListContainer.value) {
    taskListContainer.value.addEventListener('scroll', (e) => {
      scrollTop.value = (e.target as HTMLDivElement).scrollTop;
    });
    visibleCount.value = Math.floor(taskListContainer.value.clientHeight / itemHeight) + 2;
  }

  await refreshAllTaskStatus();
  rebuildSSEConnections();

  cleanExpiredTasks();
  const cleanTimer = setInterval(cleanExpiredTasks, 60 * 60 * 1000);

  onUnmounted(() => {
    clearInterval(cleanTimer);
    sseInstances.forEach(sse => sse.close());
    downloadApi.clearRequestCache();
  });

  // 本地存储加载
  const savedTasks = localStorage.getItem('downloadTasks');
  if (savedTasks) {
    try {
      taskList.value = JSON.parse(savedTasks) as DownloadTask[];
    } catch (e) {
      console.error('解析本地任务失败：', e);
      taskList.value = [];
    }
  }
});

watch(taskList, (newTasks) => {
  localStorage.setItem('downloadTasks', JSON.stringify(newTasks));
}, { deep: true });
</script>

<style scoped>
/* 基础容器：无 !important */
.download-manager {
  max-width: 800px;
  margin: 20px auto;
  padding: 20px;
  position: relative;
  min-height: 600px;
}

.main-panel {
  padding: 20px;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  background: #fff;
}

.sidebar-trigger-wrapper {
  display: flex !important;
  visibility: visible !important;
  flex-direction: column;
  gap: 10px;
  background: transparent;
  padding: 8px;
  border-radius: 8px;
  /* 核心定位规则保留 !important（防止被全局样式覆盖） */
  position: fixed !important;
  top: 50% !important;
  right: 20px !important;
  transform: translateY(-50%) !important;
  z-index: 9999 !important;
}

.sidebar-trigger-wrapper .main-trigger-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.15);
  transition: all 0.2s ease;
  background: #1890ff;
  border-color: #1890ff;
}

.sidebar-trigger-wrapper .main-trigger-btn:hover {
  transform: scale(1.05);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.2);
  background: #40a9ff;
  border-color: #40a9ff;
}

.sidebar-trigger-wrapper .pin-trigger-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.2s ease;
  background: #fff;
  border: 1px solid #d9d9d9;
}

.sidebar-trigger-wrapper .pin-trigger-btn:hover {
  transform: scale(1.05);
  background: #f5f5f5;
  border-color: #1890ff;
}

.task-drawer :deep(.ant-drawer-body) {
  padding: 16px 20px;
  overflow: visible;
  overflow-y: auto;
}

.task-list-container {
  max-height: calc(100vh - 180px);
  padding: 8px 4px;
  margin: 0;
}

.task-card :deep(.ant-card-body) {
  padding: 12px 16px;
  overflow: visible;
  box-sizing: border-box;
}

.virtual-list {
  width: 100%;
  overflow: visible;
}

.virtual-list-content {
  width: 100%;
  box-sizing: border-box;
}

.task-progress :deep(.ant-progress-text) {
  font-size: 12px;
  color: #666;
}

.task-info :deep(.ant-typography) {
  margin: 0;
  line-height: 1.6;
  word-break: break-all;
}

.task-list-container::-webkit-scrollbar {
  width: 8px;
}

.task-list-container::-webkit-scrollbar-track {
  background: #f5f5f5;
  border-radius: 4px;
}

.task-list-container::-webkit-scrollbar-thumb {
  background: #d9d9d9;
  border-radius: 4px;
}

.task-list-container::-webkit-scrollbar-thumb:hover {
  background: #bfbfbf;
}

@media (max-width: 768px) {
  .task-drawer {
    width: 90%;
  }

  .task-list-container {
    max-height: calc(100vh - 200px);
  }

  .sidebar-trigger-wrapper {
    right: 10px !important;
  }

  .sidebar-trigger-wrapper .main-trigger-btn {
    width: 44px;
    height: 44px;
  }

  .sidebar-trigger-wrapper .pin-trigger-btn {
    width: 36px;
    height: 36px;
  }
}
</style>