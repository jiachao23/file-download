<template>
  <div class="download-manager">
    <!-- 主Tab面板（页面中间） -->
    <div class="main-panel">
      <a-tabs v-model:activeKey="activeTab" type="card" size="middle">
        <a-tab-pane tab="单文件下载" key="single">
          <a-form layout="vertical" :label-col="{ span: 4 }" :wrapper-col="{ span: 20 }">
            <a-form-item label="服务器文件路径" required>
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
          <a-form layout="vertical" :label-col="{ span: 4 }" :wrapper-col="{ span: 20 }">
            <a-form-item label="用户ID" required>
              <a-input
                  v-model:value="multiForm.userId"
                  placeholder="用于隔离文件目录"
                  allow-clear
              />
            </a-form-item>
            <a-form-item label="服务器文件路径（每行一个）" required>
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

    <!-- 触发条（纯Vue模板语法） -->
    <div
        class="sidebar-trigger-wrapper"
        @mouseenter="handleTriggerMouseEnter"
        @mouseleave="handleTriggerMouseLeave"
    >
      <a-tooltip placement="left" title="下载任务面板">
        <a-button
            type="primary"
            shape="circle"
            size="large"
            class="main-trigger-btn"
            @click="toggleDrawer"
        >
          <!-- 新正确写法（通用 Icon 组件） -->
          <Icon icon="list" />
        </a-button>
      </a-tooltip>

      <a-tooltip placement="left" :title="isDrawerPinned ? '取消固定' : '固定面板'">
        <a-button
            shape="circle"
            size="middle"
            class="pin-trigger-btn"
            @click.stop="toggleDrawerPin"
        >
          <PushpinOutlined
              :style="{
              transform: isDrawerPinned ? 'rotate(0deg)' : 'rotate(-45deg)',
              transition: 'transform 0.2s ease'
            }"
          />
        </a-button>
      </a-tooltip>
    </div>

    <!-- 抽屉侧边栏（纯Vue模板语法） -->
    <a-drawer
        title="下载任务列表"
        placement="right"
        :width="480"
        :visible="drawerVisible"
        :closable="!isDrawerPinned"
        :mask="false"
        :destroy-on-close="true"
        @close="handleDrawerClose"
        @mouseenter="handleDrawerMouseEnter"
        @mouseleave="handleDrawerMouseLeave"
        class="task-drawer"
    >
      <!-- 抽屉头部操作按钮 -->
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

      <!-- 任务列表（纯Vue模板语法） -->
      <div class="task-list-container" v-else ref="taskListContainer">
        <div
            class="virtual-list"
            :style="{ height: `${taskList.length * 180}px`, position: 'relative' }"
        >
          <div
              class="virtual-list-content"
              :style="{ transform: `translateY(${scrollTop}px)`, position: 'absolute', top: 0, left: 0, width: '100%' }"
          >
            <a-card
                v-for="task in visibleTasks"
                :key="task.taskId"
                class="task-card"
                :bordered="true"
                size="default"
                :style="{ marginBottom: '12px', padding: '8px 0' }"
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
                    {{ formattedSizes[task.downloadedBytes] || '0 B' }} / {{ formattedSizes[task.totalBytes] || '0 B' }}
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

<script setup>
import { ref, reactive, computed, watch, onMounted, onUnmounted } from 'vue';
import { message } from 'ant-design-vue';
// 导入AntD图标（作为Vue组件，非JSX）
import {
  DownloadOutlined, UploadOutlined, PushpinOutlined,
  DeleteOutlined, CloseOutlined
} from '@ant-design/icons-vue';
// 导入业务API
import {
  downloadSingleLocalFile,
  submitMultiLocalFileTask,
  cancelMultiFileTask,
  getAllTasks,
  getTaskStatus,
  clearRequestCache
} from '../api/downloadApi';
import { createSSE } from "../api/sse.js";

// ========== 性能监控 ==========
const monitorPerformance = () => {
  window.addEventListener('load', () => {
    setTimeout(() => {
      const perfData = performance.getEntriesByType('navigation')[0];
      console.log('首屏加载时间：', perfData.loadEventEnd - perfData.navigationStart, 'ms');
    }, 0);
  });

  if (window.performance && window.performance.memory) {
    const checkMemory = () => {
      const memory = window.performance.memory;
      const used = (memory.usedJSHeapSize / 1024 / 1024).toFixed(2);
      const total = (memory.totalJSHeapSize / 1024 / 1024).toFixed(2);
      if (parseFloat(used) > 200) {
        console.warn('内存使用过高：', used, 'MB /', total, 'MB');
      }
    };
    const memoryTimer = setInterval(checkMemory, 60000);
    onUnmounted(() => clearInterval(memoryTimer));
  }
};
monitorPerformance();

// ========== 基础状态 ==========
const activeTab = ref('single');
const drawerVisible = ref(false);
const isDrawerPinned = ref(false);
const isMouseOverDrawer = ref(false);
const isMouseOverTrigger = ref(false);

// 表单状态
const singleForm = reactive({ filePath: '' });
const multiForm = reactive({ userId: '', filePathStr: '' });
const multiSubmitting = ref(false);

// ========== 任务管理 ==========
const taskList = ref(JSON.parse(localStorage.getItem('downloadTasks') || '[]'));
const sseInstances = new WeakMap();
const formattedSizes = ref({});

// 防抖函数
const debounce = (fn, delay = 100) => {
  let timer = null;
  return (...args) => {
    clearTimeout(timer);
    timer = setTimeout(() => fn(...args), delay);
  };
};
const updateTaskListDebounced = debounce((tasks) => {
  taskList.value = [...tasks];
}, 50);

// 监听任务列表变化
watch(taskList, (newTasks) => {
  localStorage.setItem('downloadTasks', JSON.stringify(newTasks));
  if (newTasks.length > 0 && !drawerVisible.value && !isDrawerPinned.value) {
    drawerVisible.value = true;
  }
}, { deep: true });

// ========== 虚拟列表配置 ==========
const taskListContainer = ref(null);
const scrollTop = ref(0);
const itemHeight = 180;
const visibleCount = ref(6);

const visibleTasks = computed(() => {
  const start = Math.floor(scrollTop.value / itemHeight);
  const end = start + visibleCount.value;
  return taskList.value.slice(start, end);
});

// ========== 工具函数 ==========
const getProgressStatus = (task) => {
  if (task.cancelled) return 'exception';
  if (task.finished) return 'success';
  return 'active';
};

const getTaskStatusText = (task) => {
  if (task.cancelled) return '已取消';
  if (task.finished) return '已完成';
  return '下载中';
};

const getStatusColor = (task) => {
  if (task.cancelled) return 'error';
  if (task.finished) return 'success';
  return 'processing';
};

const formatBytes = (bytes) => {
  if (formattedSizes.value[bytes]) return formattedSizes.value[bytes];
  if (bytes === 0) return '0 B';
  const k = 1024;
  const sizes = ['B', 'KB', 'MB', 'GB', 'TB'];
  const i = Math.floor(Math.log(bytes) / Math.log(k));
  const result = parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i];
  formattedSizes.value[bytes] = result;
  return result;
};

// ========== 抽屉&触发条交互逻辑 ==========
const handleTriggerMouseEnter = () => {
  isMouseOverTrigger.value = true;
  if (!isDrawerPinned.value) {
    drawerVisible.value = true;
  }
};

const handleTriggerMouseLeave = () => {
  isMouseOverTrigger.value = false;
  if (!isDrawerPinned.value && !isMouseOverDrawer.value && taskList.value.length === 0) {
    drawerVisible.value = false;
  }
};

const handleDrawerMouseEnter = () => {
  isMouseOverDrawer.value = true;
};

const handleDrawerMouseLeave = () => {
  isMouseOverDrawer.value = false;
  if (!isDrawerPinned.value && !isMouseOverTrigger.value && taskList.value.length === 0) {
    drawerVisible.value = false;
  }
};

const toggleDrawer = () => {
  drawerVisible.value = !drawerVisible.value;
};

const toggleDrawerPin = () => {
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

const handleDrawerClose = () => {
  drawerVisible.value = false;
  if (taskList.value.length === 0) {
    isDrawerPinned.value = false;
  }
};

// ========== 任务操作 ==========
const refreshAllTaskStatus = async () => {
  try {
    const serverTasks = await getAllTasks();
    if (serverTasks && serverTasks.length > 0) {
      const mergedTasks = serverTasks.map(serverTask => {
        const localTask = taskList.value.find(t => t.taskId === serverTask.taskId);
        return { ...localTask, ...serverTask };
      });
      updateTaskListDebounced([...mergedTasks]);
      mergedTasks.forEach(task => {
        formatBytes(task.downloadedBytes);
        formatBytes(task.totalBytes);
      });
    }
  } catch (e) {
    console.error('刷新任务状态失败：', e);
    message.warning('无法获取最新任务状态，使用本地缓存');
  }
};

const rebuildSSEConnections = () => {
  const unfinishedMultiTasks = taskList.value.filter(
      task => task.type === 'multi' && !task.finished && !task.cancelled
  );
  unfinishedMultiTasks.forEach(task => {
    if (!sseInstances.has(task)) {
      getTaskStatus(task.taskId).then(latestTask => {
        const taskIndex = taskList.value.findIndex(t => t.taskId === task.taskId);
        if (taskIndex !== -1) {
          const newTasks = [...taskList.value];
          newTasks[taskIndex] = { ...newTasks[taskIndex], ...latestTask };
          updateTaskListDebounced(newTasks);
        }
        if (!latestTask.finished && !latestTask.cancelled) {
          const sse = createSSE(
              task.taskId,
              (data) => {
                const newTasks = [...taskList.value];
                const index = newTasks.findIndex(t => t.taskId === task.taskId);
                if (index !== -1) {
                  newTasks[index] = { ...newTasks[index], ...data };
                  formatBytes(data.downloadedBytes);
                  formatBytes(data.totalBytes);
                  updateTaskListDebounced(newTasks);
                  if (data.finished && !data.cancelled) {
                    message.success('多文件下载任务完成！');
                  }
                }
              },
              (e) => {
                console.error(`SSE连接失败(${task.taskId})：`, e);
                if (e.message.includes('重试') || e.message.includes('断开')) {
                  message.warning(e.message);
                } else if (e.message !== '进度消息格式错误') {
                  message.error(`任务${task.taskId}：${e.message}`);
                }
              }
          );
          sseInstances.set(task, sse);
        }
      }).catch(err => {
        console.error('查询任务状态失败：', err);
      });
    }
  });
};

const handleSingleDownload = async () => {
  if (!singleForm.filePath) {
    message.warning('请输入服务器文件路径！');
    return;
  }

  const tempTaskId = 'single_' + Date.now();
  const singleTask = {
    taskId: tempTaskId,
    type: 'single',
    filePath: singleForm.filePath,
    progress: 0,
    downloadedBytes: 0,
    totalBytes: 0,
    finished: false,
    cancelled: false,
    createTime: Date.now()
  };
  updateTaskListDebounced([...taskList.value, singleTask]);

  try {
    const response = await downloadSingleLocalFile({
      filePath: singleForm.filePath,
      rangeStart: 0,
    });

    const totalBytes = Number(response.headers['content-length']) || 0;
    const taskIndex = taskList.value.findIndex(t => t.taskId === tempTaskId);
    if (taskIndex !== -1) {
      const updatedTasks = [...taskList.value];
      updatedTasks[taskIndex].totalBytes = totalBytes;
      updatedTasks[taskIndex].downloadedBytes = totalBytes;
      updatedTasks[taskIndex].progress = 100;
      updatedTasks[taskIndex].finished = true;
      updateTaskListDebounced(updatedTasks);
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
    if (e.name !== 'AbortError') {
      message.error('下载失败：' + e.message);
      const taskIndex = taskList.value.findIndex(t => t.taskId === tempTaskId);
      if (taskIndex !== -1) {
        const updatedTasks = [...taskList.value];
        updatedTasks[taskIndex].finished = true;
        updatedTasks[taskIndex].cancelled = true;
        updatedTasks[taskIndex].progress = 0;
        updateTaskListDebounced(updatedTasks);
      }
    }
  }
};

const handleMultiSubmit = async () => {
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
    const taskId = await submitMultiLocalFileTask(filePathList, multiForm.userId);
    const multiTask = {
      taskId,
      type: 'multi',
      progress: 0,
      completedCount: 0,
      failedCount: 0,
      totalCount: filePathList.length,
      downloadedBytes: 0,
      totalBytes: 0,
      finished: false,
      cancelled: false,
      createTime: Date.now()
    };
    updateTaskListDebounced([...taskList.value, multiTask]);

    const initTask = await getTaskStatus(taskId);
    if (initTask) {
      const taskIndex = taskList.value.findIndex(t => t.taskId === taskId);
      if (taskIndex !== -1) {
        const updatedTasks = [...taskList.value];
        updatedTasks[taskIndex] = {
          ...updatedTasks[taskIndex],
          totalBytes: initTask.totalBytes,
          totalCount: initTask.totalCount
        };
        updateTaskListDebounced(updatedTasks);
        formatBytes(initTask.totalBytes);
      }
    }

    const sse = createSSE(
        taskId,
        (data) => {
          const newTasks = [...taskList.value];
          const taskIndex = newTasks.findIndex(t => t.taskId === taskId);
          if (taskIndex !== -1) {
            newTasks[taskIndex] = { ...newTasks[taskIndex], ...data };
            formatBytes(data.downloadedBytes);
            formatBytes(data.totalBytes);
            updateTaskListDebounced(newTasks);
            if (data.finished && !data.cancelled) {
              message.success('多文件下载任务完成！');
            }
          }
        },
        (e) => {
          console.error(`任务${taskId} SSE错误：`, e);
          if (e.message === '任务进度监听失败') {
            message.warning(`任务${taskId}进度监听暂时中断，正在重试...`);
          }
        }
    );
    sseInstances.set(multiTask, sse);

  } catch (e) {
    message.error('提交任务失败：' + e.message);
  } finally {
    multiSubmitting.value = false;
  }
};

const cancelTask = async (taskId) => {
  const taskIndex = taskList.value.findIndex(t => t.taskId === taskId);
  if (taskIndex === -1) {
    message.warning('任务不存在！');
    return;
  }

  const task = taskList.value[taskIndex];
  const newTasks = [...taskList.value];

  if (task.type === 'multi') {
    try {
      const res = await cancelMultiFileTask(taskId);
      const resData = typeof res === 'string' ? JSON.parse(res) : res;
      newTasks[taskIndex].cancelled = true;
      newTasks[taskIndex].progress = 0;
      updateTaskListDebounced(newTasks);

      if (sseInstances.has(task)) {
        sseInstances.get(task).close();
        sseInstances.delete(task);
      }
      message.info(resData.msg || '任务已取消');
    } catch (e) {
      message.error('取消任务失败：' + e.message);
    }
  } else {
    newTasks[taskIndex].cancelled = true;
    newTasks[taskIndex].finished = true;
    newTasks[taskIndex].progress = 0;
    updateTaskListDebounced(newTasks);
    message.info('单文件下载任务已取消');
  }
};

const clearFinishedTasks = () => {
  const newTasks = taskList.value.filter(
      task => !task.finished || task.cancelled
  );
  updateTaskListDebounced(newTasks);
  message.success('已清空已完成任务');
};

const cancelAllUnfinishedTasks = async () => {
  const unfinishedTasks = taskList.value.filter(
      task => !task.finished && !task.cancelled
  );

  if (unfinishedTasks.length === 0) {
    message.warning('暂无未完成任务');
    return;
  }

  const cancelPromises = unfinishedTasks.map(task => cancelTask(task.taskId));
  await Promise.allSettled(cancelPromises);
  message.success('已取消所有未完成任务');
};

// ========== 生命周期 ==========
onMounted(async () => {
  const container = taskListContainer.value;
  if (container) {
    container.addEventListener('scroll', (e) => {
      scrollTop.value = e.target.scrollTop;
    });
    visibleCount.value = Math.floor(container.clientHeight / itemHeight) + 2;
  }

  await refreshAllTaskStatus();
  rebuildSSEConnections();

  const cleanExpiredTasks = () => {
    const now = Date.now();
    const newTasks = taskList.value.filter(task => {
      const createTime = task.createTime || 0;
      return !task.finished || !task.cancelled || (now - createTime < 7 * 24 * 60 * 60 * 1000);
    });
    updateTaskListDebounced(newTasks);
  };
  cleanExpiredTasks();
  const cleanTimer = setInterval(cleanExpiredTasks, 24 * 60 * 60 * 1000);
  onUnmounted(() => clearInterval(cleanTimer));
});

onUnmounted(() => {
  sseInstances.forEach((sse) => {
    if (sse.close) sse.close();
    else if (sse.source) sse.source.close();
  });

  clearRequestCache();
  if (window.gc) window.gc();
});
</script>

<style scoped>
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

/* 触发条容器 */
.sidebar-trigger-wrapper {
  position: fixed;
  top: 50%;
  right: 20px;
  transform: translateY(-50%);
  display: flex;
  flex-direction: column;
  gap: 10px;
  z-index: 1000;
}

.main-trigger-btn {
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.15);
  transition: all 0.2s ease;
  overflow-y: auto !important;
}

.main-trigger-btn:hover {
  transform: scale(1.05);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.2);
}

.pin-trigger-btn {
  background: #fff;
  border: 1px solid #d9d9d9;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
  transition: all 0.2s ease;
}

.pin-trigger-btn:hover {
  background: #f5f5f5;
  transform: scale(1.05);
}

/* 抽屉样式 */
.task-drawer :deep(.ant-drawer-body) {
  padding: 16px 20px !important;
  overflow: visible !important;
}

/* 任务列表容器 */
.task-list-container {
  max-height: calc(100vh - 180px) !important;
  padding: 8px 4px !important;
  margin: 0 !important;
}

/* Card样式修复 */
.task-card :deep(.ant-card-body) {
  padding: 12px 16px !important;
  overflow: visible !important;
  box-sizing: border-box !important;
}

/* 虚拟列表 */
.virtual-list {
  width: 100% !important;
  overflow: visible !important;
}

.virtual-list-content {
  width: 100% !important;
  box-sizing: border-box !important;
}

/* 进度条 */
.task-progress :deep(.ant-progress-text) {
  font-size: 12px !important;
  color: #666 !important;
}

/* 任务信息 */
.task-info :deep(.ant-typography) {
  margin: 0 !important;
  line-height: 1.6 !important;
  word-break: break-all !important;
}

/* 滚动条 */
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

/* 响应式 */
@media (max-width: 768px) {
  .task-drawer {
    width: 90% !important;
  }

  .task-list-container {
    max-height: calc(100vh - 200px) !important;
  }
}
</style>