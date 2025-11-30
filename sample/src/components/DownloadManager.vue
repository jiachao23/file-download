<template>
  <div class="download-manager">
    <!-- 主Tab面板（页面中间） -->
    <div class="main-panel">
      <div class="tabs">
        <div class="tab" :class="{ active: activeTab === 'single' }" @click="activeTab = 'single'">
          单文件下载
        </div>
        <div class="tab" :class="{ active: activeTab === 'multi' }" @click="activeTab = 'multi'">
          多文件下载
        </div>
      </div>

      <!-- 单文件下载表单 -->
      <div class="form-panel" v-if="activeTab === 'single'">
        <div class="form-item">
          <label>服务器文件路径：</label>
          <input v-model="singleForm.filePath" placeholder="如：/data/files/test.pdf" />
        </div>
        <div class="btn-group">
          <button class="btn primary" @click="handleSingleDownload">
            下载
          </button>
        </div>
      </div>

      <!-- 多文件下载表单 -->
      <div class="form-panel" v-if="activeTab === 'multi'">
        <div class="form-item">
          <label>用户ID：</label>
          <input v-model="multiForm.userId" placeholder="用于隔离文件目录" />
        </div>
        <div class="form-item">
          <label>服务器文件路径（每行一个）：</label>
          <textarea v-model="multiForm.filePathStr" rows="5" placeholder="/data/files/test1.pdf&#10;/data/files/test2.zip"></textarea>
        </div>
        <button class="btn primary" @click="handleMultiSubmit" :disabled="multiSubmitting">
          {{ multiSubmitting ? '提交中...' : '提交下载任务' }}
        </button>
      </div>
    </div>

    <!-- 🔥 触发条移到外层：与侧边栏同级 -->
    <div
        class="sidebar-trigger"
        @click="toggleSidebarExpand"
        @mouseenter="handleTriggerMouseEnter"
        @mouseleave="handleTriggerMouseLeave"
    >
      <span class="trigger-icon">📋</span>
      <button
          class="pin-btn"
          @click.stop="toggleSidebarPin"
          title="固定/取消固定任务面板"
      >
        {{ isSidebarPinned ? '📌' : '📍' }}
      </button>
    </div>

    <!-- 右侧悬浮任务面板 -->
    <div
        class="task-sidebar"
        :class="{
        'expanded': isSidebarExpanded || (isMouseOverTrigger && !isSidebarPinned),
        'pinned': isSidebarPinned
      }"
        @mouseenter="handleSidebarMouseEnter"
        @mouseleave="handleSidebarMouseLeave"
    >
      <!-- 任务列表内容区 -->
      <div class="sidebar-content">
        <div class="sidebar-header">
          <h3>下载任务列表</h3>
          <div class="task-actions">
            <button class="btn small" @click="clearFinishedTasks">
              清空已完成
            </button>
            <button class="btn small cancel" @click="cancelAllUnfinishedTasks">
              取消所有
            </button>
            <button class="btn small cancel" @click="collapseSidebar">
              收回
            </button>
          </div>
        </div>

        <!-- 空状态提示 -->
        <div class="empty-tip" v-if="taskList.length === 0">
          暂无下载任务，请先提交下载任务
        </div>

        <!-- 虚拟列表任务容器 -->
        <div class="task-list-container" ref="taskListContainer">
          <div
              class="virtual-list"
              :style="{ height: `${taskList.length * 120}px`, position: 'relative' }"
              v-if="taskList.length > 0"
          >
            <div
                class="virtual-list-content"
                :style="{ transform: `translateY(${scrollTop}px)`, position: 'absolute', top: 0, left: 0, width: '100%' }"
            >
              <div class="task-item" v-for="task in visibleTasks" :key="task.taskId">
                <div class="task-header">
                  <span class="task-type">{{ task.type === 'single' ? '单文件' : '多文件' }}</span>
                  <button
                      class="btn cancel small"
                      @click.stop="cancelTask(task.taskId)"
                      :disabled="task.finished || task.cancelled"
                  >
                    取消
                  </button>
                </div>

                <!-- 进度条 -->
                <div class="progress-bar">
                  <div class="progress-fill" :style="{
                    width: task.progress + '%',
                    backgroundColor: task.cancelled ? '#ff4d4f' : (task.finished ? '#52c41a' : '#1677ff')
                  }"></div>
                </div>

                <!-- 任务信息 -->
                <div class="task-info">
                  <p class="progress-text">{{ task.progress }}%</p>
                  <p v-if="task.type === 'multi'" class="file-count">
                    {{ task.completedCount }}/{{ task.totalCount }} 文件
                  </p>
                  <p class="file-size">
                    {{ formattedSizes[task.downloadedBytes] || '0 B' }}/{{ formattedSizes[task.totalBytes] || '0 B' }}
                  </p>
                  <p class="task-status">
                    <span :class="`status-${getTaskStatusText(task)}`">
                      {{ getTaskStatusText(task) }}
                    </span>
                  </p>
                  <p v-if="task.filePath" class="file-path">
                    {{ task.filePath }}
                  </p>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, computed, watch, onMounted, onUnmounted } from 'vue';
import { message } from 'ant-design-vue';
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

// 侧边栏核心状态（外层触发条专用）
const isSidebarExpanded = ref(false);    // 是否展开
const isSidebarPinned = ref(false);      // 是否固定
const isMouseOverTrigger = ref(false);   // 鼠标是否悬浮在触发条上
const isMouseOverSidebar = ref(false);   // 鼠标是否悬浮在侧边栏上

// 单文件/多文件表单状态
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
  if (newTasks.length > 0 && !isSidebarExpanded.value && !isSidebarPinned.value) {
    isSidebarExpanded.value = true;
  }
}, { deep: true });

// ========== 虚拟列表 ==========
const taskListContainer = ref(null);
const scrollTop = ref(0);
const itemHeight = 120;
const visibleCount = ref(10);
const visibleTasks = computed(() => {
  const start = Math.floor(scrollTop.value / itemHeight);
  const end = start + visibleCount.value;
  return taskList.value.slice(start, end);
});

// ========== 工具函数 ==========
const getTaskStatusText = (task) => {
  if (task.cancelled) return '已取消';
  if (task.finished) return '已完成';
  return '下载中';
};

// 格式化文件大小（本地函数，兼容所有环境）
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

// ========== 外层触发条交互逻辑（核心） ==========
// 触发条鼠标进入
const handleTriggerMouseEnter = () => {
  isMouseOverTrigger.value = true;
};

// 触发条鼠标离开
const handleTriggerMouseLeave = () => {
  isMouseOverTrigger.value = false;
  // 未固定且鼠标离开触发条+离开侧边栏 → 收起
  if (!isSidebarPinned.value && !isMouseOverSidebar.value && taskList.value.length === 0) {
    isSidebarExpanded.value = false;
  }
};

// 侧边栏鼠标进入
const handleSidebarMouseEnter = () => {
  isMouseOverSidebar.value = true;
  if (!isSidebarPinned.value) {
    isSidebarExpanded.value = true;
  }
};

// 侧边栏鼠标离开
const handleSidebarMouseLeave = () => {
  isMouseOverSidebar.value = false;
  // 未固定且鼠标离开触发条 → 收起
  if (!isSidebarPinned.value && !isMouseOverTrigger.value && taskList.value.length === 0) {
    isSidebarExpanded.value = false;
  }
};

// 切换侧边栏展开/收起
const toggleSidebarExpand = () => {
  isSidebarExpanded.value = !isSidebarExpanded.value;
};

// 切换侧边栏固定状态
const toggleSidebarPin = () => {
  isSidebarPinned.value = !isSidebarPinned.value;
  if (isSidebarPinned.value) {
    isSidebarExpanded.value = true; // 固定时强制展开
    message.success('任务面板已固定');
  } else {
    message.success('任务面板已取消固定');
    // 取消固定后，无任务则收起
    if (taskList.value.length === 0) {
      isSidebarExpanded.value = false;
    }
  }
};

// 手动收回侧边栏
const collapseSidebar = () => {
  isSidebarExpanded.value = false;
  if (isSidebarPinned.value) {
    isSidebarPinned.value = false;
    message.info('已取消固定并收回任务面板');
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

// 单文件下载
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

// 提交多文件任务
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

// 取消任务
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

// 清空已完成任务
const clearFinishedTasks = () => {
  const newTasks = taskList.value.filter(
      task => !task.finished || task.cancelled
  );
  updateTaskListDebounced(newTasks);
  message.success('已清空已完成任务');
};

// 取消所有未完成任务
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
  // 初始化虚拟列表
  const container = taskListContainer.value;
  if (container) {
    container.addEventListener('scroll', (e) => {
      scrollTop.value = e.target.scrollTop;
    });
    visibleCount.value = Math.floor(container.clientHeight / itemHeight) + 2;
  }

  await refreshAllTaskStatus();
  rebuildSSEConnections();

  // 定时清理过期任务
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
  // 关闭所有SSE连接
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

.tabs {
  display: flex;
  margin-bottom: 20px;
  border-bottom: 1px solid #e5e7eb;
}

.tab {
  padding: 10px 20px;
  cursor: pointer;
  border-bottom: 2px solid transparent;
  transition: all 0.2s ease;
}

.tab.active {
  border-bottom-color: #1677ff;
  font-weight: 600;
}

.form-panel {
  padding: 10px 0;
}

.form-item {
  margin-bottom: 15px;
  display: flex;
  flex-direction: column;
}

.form-item label {
  margin-bottom: 5px;
  font-weight: 500;
}

.form-item input, .form-item textarea {
  padding: 8px 12px;
  border: 1px solid #d1d5db;
  border-radius: 4px;
  font-size: 14px;
}

.btn-group {
  display: flex;
  gap: 10px;
}

.btn {
  padding: 8px 16px;
  border: none;
  border-radius: 4px;
  cursor: pointer;
  font-size: 14px;
  transition: all 0.2s ease;
}

.btn.primary {
  background-color: #1677ff;
  color: white;
}

.btn.primary:hover {
  background-color: #4096ff;
}

.btn.cancel {
  background-color: #ff4d4f;
  color: white;
}

.btn.cancel:hover {
  background-color: #ff7875;
}

.btn.small {
  padding: 4px 8px;
  font-size: 12px;
  background-color: #f5f5f5;
  color: #666;
}

.btn.small.cancel {
  background-color: #fff2f2;
  color: #ff4d4f;
}

/* 🔥 外层触发条样式（核心修改） */
.sidebar-trigger {
  position: fixed;
  top: 50%;
  right: 0; /* 贴在最右侧 */
  transform: translateY(-50%);
  width: 50px;
  height: 120px;
  background-color: #f0f7ff;
  border-radius: 10px 0 0 10px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  z-index: 1002; /* 比侧边栏高，确保不被遮挡 */
  box-shadow: -2px 0 8px rgba(0,0,0,0.1);
  border: 1px solid #e8f3ff;
  border-right: none;
  transition: all 0.2s ease;
}

.sidebar-trigger:hover {
  background-color: #e8f3ff;
}

.trigger-icon {
  font-size: 24px;
  margin-bottom: 10px;
  user-select: none;
  color: #1677ff;
}

.pin-btn {
  background: transparent;
  border: none;
  font-size: 18px;
  cursor: pointer;
  padding: 4px;
  border-radius: 50%;
  color: #1677ff;
  transition: all 0.2s;
}

.pin-btn:hover {
  background-color: rgba(22, 119, 255, 0.2);
  transform: scale(1.1);
}

/* 右侧悬浮任务面板 */
.task-sidebar {
  position: fixed;
  top: 0;
  right: 0;
  height: 100vh;
  width: 0; /* 收起时宽度为0 */
  background-color: #fff;
  border-left: 1px solid #e5e7eb;
  transition: width 0.3s ease;
  box-shadow: -2px 0 10px rgba(0,0,0,0.05);
  z-index: 1001; /* 低于触发条 */
  overflow: hidden;
}

/* 展开/固定时的宽度 */
.task-sidebar.expanded,
.task-sidebar.pinned {
  width: 450px;
  /* 给触发条留位置：右侧50px */
  padding-left: 0;
}

.sidebar-content {
  height: 100%;
  width: 450px;
  padding: 20px;
  overflow-y: auto;
  box-sizing: border-box;
}

.sidebar-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
  padding-bottom: 10px;
  border-bottom: 1px solid #e5e7eb;
}

.sidebar-header h3 {
  margin: 0;
  color: #1677ff;
  font-size: 18px;
}

.task-actions {
  display: flex;
  gap: 8px;
}

.empty-tip {
  text-align: center;
  padding: 40px 0;
  color: #666;
  font-size: 14px;
}

.task-list-container {
  display: flex;
  flex-direction: column;
  gap: 10px;
  max-height: calc(100vh - 120px);
  overflow-y: auto;
}

.task-item {
  padding: 15px;
  border: 1px solid #e5e7eb;
  border-radius: 6px;
  background: #f9fafb;
}

.task-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 10px;
}

.task-type {
  background-color: #e8f3ff;
  color: #1677ff;
  padding: 2px 8px;
  border-radius: 4px;
  font-size: 12px;
}

.progress-bar {
  height: 8px;
  width: 100%;
  background-color: #f0f0f0;
  border-radius: 4px;
  overflow: hidden;
  margin-bottom: 10px;
}

.progress-fill {
  height: 100%;
  background-color: #1677ff;
  transition: width 0.3s ease;
}

.task-info {
  font-size: 14px;
  color: #333;
}

.progress-text {
  font-weight: 600;
  margin: 0 0 5px 0;
  color: #1677ff;
}

.file-count, .file-size {
  margin: 0 0 3px 0;
  color: #666;
  font-size: 13px;
}

.task-status {
  margin: 5px 0;
}

.status-已取消 {
  color: #ff4d4f;
}

.status-已完成 {
  color: #52c41a;
}

.status-下载中 {
  color: #1677ff;
}

.file-path {
  margin: 5px 0 0 0;
  font-size: 12px;
  color: #888;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

button:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

/* 滚动条美化 */
.sidebar-content::-webkit-scrollbar,
.task-list-container::-webkit-scrollbar {
  width: 6px;
}

.sidebar-content::-webkit-scrollbar-track,
.task-list-container::-webkit-scrollbar-track {
  background: #f1f1f1;
  border-radius: 3px;
}

.sidebar-content::-webkit-scrollbar-thumb,
.task-list-container::-webkit-scrollbar-thumb {
  background: #ccc;
  border-radius: 3px;
}

.sidebar-content::-webkit-scrollbar-thumb:hover,
.task-list-container::-webkit-scrollbar-thumb:hover {
  background: #999;
}

/* 虚拟列表容器 */
.virtual-list {
  overflow: hidden;
  width: 100%;
}

.virtual-list-content {
  transition: transform 0.1s ease;
}
</style>