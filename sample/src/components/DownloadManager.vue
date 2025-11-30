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
          <button class="btn primary" @click="handleSingleDownload" :disabled="singleDownloading">
            {{ singleDownloading ? '下载中...' : '开始下载' }}
          </button>
          <button class="btn cancel" @click="cancelSingleDownload" v-if="singleDownloading">
            取消下载
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

    <!-- 右侧悬浮任务面板 -->
    <div
        class="task-sidebar"
        :class="{
        'expanded': isSidebarExpanded || isMouseOverSidebar,
        'pinned': isSidebarPinned
      }"
        @mouseenter="isMouseOverSidebar = true"
        @mouseleave="() => { if(!isSidebarPinned) isMouseOverSidebar = false }"
    >
      <!-- 侧边栏触发条 -->
      <div class="sidebar-trigger">
        <span class="trigger-icon">📋</span>
        <button
            class="pin-btn"
            @click.stop="isSidebarPinned = !isSidebarPinned"
            title="固定/取消固定任务面板"
        >
          {{ isSidebarPinned ? '📌' : '📌' }}
        </button>
      </div>

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
                      :disabled="task.isFinished || task.isCancelled"
                  >
                    取消
                  </button>
                </div>

                <!-- 进度条 -->
                <div class="progress-bar">
                  <div class="progress-fill" :style="{ width: task.progress + '%',
                    backgroundColor: task.isCancelled ? '#ff4d4f' : (task.isFinished ? '#52c41a' : '#1677ff') }"></div>
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
  listenMultiFileProgress,
  getAllTasks,
  getTaskStatus,
  clearRequestCache
} from '../api/downloadApi';

// ========== 性能监控 ==========
const monitorPerformance = () => {
  // 监控首屏加载
  window.addEventListener('load', () => {
    setTimeout(() => {
      const perfData = performance.getEntriesByType('navigation')[0];
      console.log('首屏加载时间：', perfData.loadEventEnd - perfData.navigationStart, 'ms');
    }, 0);
  });

  // 监控内存使用
  if (window.performance && window.performance.memory) {
    const checkMemory = () => {
      const memory = window.performance.memory;
      const used = (memory.usedJSHeapSize / 1024 / 1024).toFixed(2);
      const total = (memory.totalJSHeapSize / 1024 / 1024).toFixed(2);
      if (parseFloat(used) > 200) { // 超过200MB警告
        console.warn('内存使用过高：', used, 'MB /', total, 'MB');
      }
    };

    // 每分钟检查一次
    const memoryTimer = setInterval(checkMemory, 60000);
    onUnmounted(() => clearInterval(memoryTimer));
  }
};

// 启动性能监控
monitorPerformance();

// ========== 基础状态 ==========
// Tab切换状态
const activeTab = ref('single');

// 侧边栏状态
const isMouseOverSidebar = ref(false);
const isSidebarPinned = ref(false);
const isSidebarExpanded = ref(false);

// 单文件下载状态
const singleForm = reactive({
  filePath: '',
});
const singleDownloading = ref(false);
let singleAbortController = null;

// 多文件下载状态
const multiForm = reactive({
  userId: '',
  filePathStr: '',
});
const multiSubmitting = ref(false);

// ========== 任务管理 ==========
// 任务列表（从LocalStorage恢复）
const taskList = ref(JSON.parse(localStorage.getItem('downloadTasks') || '[]'));
// 使用WeakMap存储SSE连接（避免内存泄漏）
const sseInstances = new WeakMap();

// 格式化大小缓存
const formattedSizes = ref({});
// 文件大小格式化Worker
const formatWorker = new Worker(new URL('../format.worker.js', import.meta.url));

// 防抖函数
const debounce = (fn, delay = 100) => {
  let timer = null;
  return (...args) => {
    clearTimeout(timer);
    timer = setTimeout(() => fn(...args), delay);
  };
};

// 防抖更新任务列表
const updateTaskListDebounced = debounce((tasks) => {
  taskList.value = [...tasks];
}, 50);

// 监听任务列表变化，自动保存到LocalStorage
watch(taskList, (newTasks) => {
  localStorage.setItem('downloadTasks', JSON.stringify(newTasks));
  if (newTasks.length > 0 && !isSidebarExpanded.value) {
    isSidebarExpanded.value = true;
  }
}, { deep: true });

// ========== 虚拟列表 ==========
const taskListContainer = ref(null);
const scrollTop = ref(0);
const itemHeight = 120; // 每个任务项高度
const visibleCount = ref(10); // 可视区域显示10个

// 可视区域任务
const visibleTasks = computed(() => {
  const start = Math.floor(scrollTop.value / itemHeight);
  const end = start + visibleCount.value;
  return taskList.value.slice(start, end);
});

// ========== 工具函数 ==========
// 获取任务状态文本
const getTaskStatusText = (task) => {
  if (task.isCancelled) return '已取消';
  if (task.isFinished) return '已完成';
  return '下载中';
};

// 格式化文件大小（使用Web Worker）
const formatBytes = (bytes) => {
  return new Promise((resolve) => {
    if (formattedSizes.value[bytes]) {
      resolve(formattedSizes.value[bytes]);
      return;
    }

    const id = Math.random().toString(36).substr(2, 9);
    formatWorker.postMessage({ id, bytes });

    formatWorker.onmessage = (e) => {
      if (e.data.id === id) {
        formattedSizes.value[bytes] = e.data.result;
        resolve(e.data.result);
      }
    };
  });
};

// ========== 任务操作 ==========
// 刷新所有任务状态（从后端拉取）
const refreshAllTaskStatus = async () => {
  try {
    const serverTasks = await getAllTasks();
    if (serverTasks && serverTasks.length > 0) {
      // 合并后端最新状态
      const mergedTasks = serverTasks.map(serverTask => {
        const localTask = taskList.value.find(t => t.taskId === serverTask.taskId);
        return {
          ...localTask, // 保留前端字段（type）
          ...serverTask // 覆盖为后端最新状态
        };
      });

      // 补充本地单文件任务
      const localSingleTasks = taskList.value.filter(
          t => t.type === 'single' && !mergedTasks.some(mt => mt.taskId === t.taskId)
      );

      // 防抖更新任务列表
      updateTaskListDebounced([...mergedTasks, ...localSingleTasks]);

      // 预格式化文件大小
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

// 重建SSE连接
const rebuildSSEConnections = () => {
  const unfinishedMultiTasks = taskList.value.filter(
      task => task.type === 'multi' && !task.isFinished && !task.isCancelled
  );

  unfinishedMultiTasks.forEach(task => {
    if (!sseInstances.has(task)) {
      // 先查询任务最新状态
      getTaskStatus(task.taskId).then(latestTask => {
        // 更新本地任务状态
        const taskIndex = taskList.value.findIndex(t => t.taskId === task.taskId);
        if (taskIndex !== -1) {
          const newTasks = [...taskList.value];
          newTasks[taskIndex] = {
            ...newTasks[taskIndex],
            ...latestTask
          };
          updateTaskListDebounced(newTasks);
        }

        // 仅对未完成任务建立SSE连接
        if (!latestTask.isFinished && !latestTask.isCancelled) {
          const sse = listenMultiFileProgress(
              task.taskId,
              (data) => {
                const newTasks = [...taskList.value];
                const index = newTasks.findIndex(t => t.taskId === task.taskId);
                if (index !== -1) {
                  newTasks[index] = {
                    ...newTasks[index],
                    ...data
                  };

                  // 预格式化文件大小
                  formatBytes(data.downloadedBytes);
                  formatBytes(data.totalBytes);

                  // 防抖更新
                  updateTaskListDebounced(newTasks);

                  if (data.isFinished && !data.isCancelled) {
                    message.success('多文件下载任务完成！');
                  }
                }
              },
              (e) => {
                console.error(`SSE连接失败(${task.taskId})：`, e);
                // 仅在非任务完成时提示错误
                if (e.message.includes('重试') || e.message.includes('断开')) {
                  message.warning(e.message);
                } else if (e.message !== '进度消息格式错误') {
                  message.error(`任务${task.taskId}：${e.message}`);
                }
              }
          );

          // 存储SSE连接
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

  const taskId = Date.now() + '-' + Math.random().toString(36).substr(2, 9);
  const singleTask = {
    taskId,
    type: 'single',
    filePath: singleForm.filePath,
    progress: 0,
    downloadedBytes: 0,
    totalBytes: 0,
    isFinished: false,
    isCancelled: false,
    createTime: Date.now()
  };

  // 添加任务到列表
  const newTasks = [...taskList.value, singleTask];
  updateTaskListDebounced(newTasks);

  isSidebarExpanded.value = true;
  singleDownloading.value = true;
  singleAbortController = new AbortController();

  try {
    const response = await downloadSingleLocalFile(
        {
          filePath: singleForm.filePath,
          rangeStart: 0,
        },
        singleAbortController.signal
    );

    // 获取文件总大小
    const totalBytes = Number(response.headers['content-length']) || 0;
    singleTask.totalBytes = totalBytes;

    // 保存文件
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

    // 模拟进度更新（实际下载由浏览器处理）
    let loaded = 0;
    const progressInterval = setInterval(() => {
      if (singleTask.isCancelled || singleTask.isFinished) {
        clearInterval(progressInterval);
        return;
      }

      loaded += 4096 * 10; // 加速模拟
      if (loaded >= totalBytes) {
        loaded = totalBytes;
        singleTask.isFinished = true;
        singleTask.progress = 100;
        clearInterval(progressInterval);
        message.success('单文件下载完成！');
      }

      singleTask.progress = Math.floor((loaded / totalBytes) * 100);
      singleTask.downloadedBytes = loaded;

      // 防抖更新任务列表
      const updatedTasks = [...taskList.value];
      const index = updatedTasks.findIndex(t => t.taskId === taskId);
      if (index !== -1) {
        updatedTasks[index] = { ...singleTask };
        updateTaskListDebounced(updatedTasks);
      }

      // 预格式化文件大小
      formatBytes(loaded);
      formatBytes(totalBytes);
    }, 100);

  } catch (e) {
    if (e.name !== 'AbortError') {
      message.error('下载失败：' + e.message);
      const taskIndex = taskList.value.findIndex(t => t.taskId === taskId);
      if (taskIndex !== -1) {
        const newTasks = [...taskList.value];
        newTasks[taskIndex].isFinished = true;
        newTasks[taskIndex].progress = 0;
        updateTaskListDebounced(newTasks);
      }
    }
  } finally {
    singleDownloading.value = false;
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
    // 提交任务到后端
    const taskId = await submitMultiLocalFileTask(
        filePathList,
        multiForm.userId,
    );

    // 初始化本地任务
    const multiTask = {
      taskId,
      type: 'multi',
      progress: 0,
      completedCount: 0,
      failedCount: 0,
      totalCount: filePathList.length,
      downloadedBytes: 0,
      totalBytes: 0,
      isFinished: false,
      isCancelled: false,
      createTime: Date.now()
    };

    // 添加任务到列表
    const newTasks = [...taskList.value, multiTask];
    updateTaskListDebounced(newTasks);

    isSidebarExpanded.value = true;

    // 先查询任务初始状态（获取总大小）
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

        // 预格式化文件大小
        formatBytes(initTask.totalBytes);
      }
    }

    // 建立SSE连接
    const sse = listenMultiFileProgress(
        taskId,
        (data) => {
          const newTasks = [...taskList.value];
          const taskIndex = newTasks.findIndex(t => t.taskId === taskId);
          if (taskIndex !== -1) {
            newTasks[taskIndex] = {
              ...newTasks[taskIndex],
              ...data
            };

            // 预格式化文件大小
            formatBytes(data.downloadedBytes);
            formatBytes(data.totalBytes);

            updateTaskListDebounced(newTasks);

            if (data.isFinished && !data.isCancelled) {
              message.success('多文件下载任务完成！');
            }
          }
        },
        (e) => {
          console.error(`任务${taskId} SSE错误：`, e);
          // 仅在真正失败时提示
          if (e.message === '任务进度监听失败') {
            message.warning(`任务${taskId}进度监听暂时中断，正在重试...`);
          }
        }
    );

    // 存储SSE连接
    sseInstances.set(multiTask, sse);

  } catch (e) {
    message.error('提交任务失败：' + e.message);
  } finally {
    multiSubmitting.value = false;
  }
};

// 统一取消任务
const cancelTask = async (taskId) => {
  const taskIndex = taskList.value.findIndex(t => t.taskId === taskId);
  if (taskIndex === -1) {
    message.warning('任务不存在！');
    return;
  }

  const task = taskList.value[taskIndex];
  const newTasks = [...taskList.value];

  // 单文件任务取消
  if (task.type === 'single') {
    if (singleAbortController) {
      singleAbortController.abort();
      singleDownloading.value = false;
    }
    newTasks[taskIndex].isCancelled = true;
    newTasks[taskIndex].progress = 0;
    updateTaskListDebounced(newTasks);
    message.info('已取消单文件下载任务');
    return;
  }

  // 多文件任务取消
  try {
    const res = await cancelMultiFileTask(taskId);
    const resData = typeof res === 'string' ? JSON.parse(res) : res;

    newTasks[taskIndex].isCancelled = true;
    newTasks[taskIndex].progress = 0;
    updateTaskListDebounced(newTasks);

    // 关闭SSE连接
    if (sseInstances.has(task)) {
      sseInstances.get(task).close();
      sseInstances.delete(task);
    }

    message.info(resData.msg || '任务已取消');
  } catch (e) {
    message.error('取消任务失败：' + e.message);
  }
};

// 取消单文件下载
const cancelSingleDownload = () => {
  const runningSingleTask = taskList.value.find(
      t => t.type === 'single' && !t.isFinished && !t.isCancelled
  );
  if (runningSingleTask) {
    cancelTask(runningSingleTask.taskId);
  }
};

// 清空已完成任务
const clearFinishedTasks = () => {
  const newTasks = taskList.value.filter(
      task => !task.isFinished || task.isCancelled
  );
  updateTaskListDebounced(newTasks);
  message.success('已清空已完成任务');
};

// 取消所有未完成任务
const cancelAllUnfinishedTasks = async () => {
  const unfinishedTasks = taskList.value.filter(
      task => !task.isFinished && !task.isCancelled
  );

  if (unfinishedTasks.length === 0) {
    message.warning('暂无未完成任务');
    return;
  }

  // 批量取消任务
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
    // 计算可视区域数量
    visibleCount.value = Math.floor(container.clientHeight / itemHeight) + 2;
  }

  // 单文件任务刷新后标记为取消
  const singleTasks = taskList.value.filter(
      t => t.type === 'single' && !t.isFinished && !t.isCancelled
  );
  if (singleTasks.length > 0) {
    const newTasks = [...taskList.value];
    singleTasks.forEach(task => {
      const index = newTasks.findIndex(t => t.taskId === task.taskId);
      if (index !== -1) {
        newTasks[index].isCancelled = true;
        newTasks[index].progress = 0;
      }
    });
    updateTaskListDebounced(newTasks);
  }

  // 拉取最新任务状态
  await refreshAllTaskStatus();

  // 重建SSE连接
  rebuildSSEConnections();

  // 定时清理过期任务（7天前）
  const cleanExpiredTasks = () => {
    const now = Date.now();
    const newTasks = taskList.value.filter(task => {
      const createTime = task.createTime || 0;
      // 保留：未完成任务 或 7天内的已完成任务
      return !task.isFinished || !task.isCancelled || (now - createTime < 7 * 24 * 60 * 60 * 1000);
    });
    updateTaskListDebounced(newTasks);
  };

  // 立即执行一次
  cleanExpiredTasks();
  // 每天执行一次
  const cleanTimer = setInterval(cleanExpiredTasks, 24 * 60 * 60 * 1000);
  onUnmounted(() => clearInterval(cleanTimer));
});

onUnmounted(() => {
  // 取消单文件下载
  if (singleAbortController) {
    singleAbortController.abort();
  }

  // 关闭所有SSE连接
  sseInstances.forEach((sse) => {
    if (sse.close) {
      sse.close();
    } else if (sse.source) {
      sse.source.close();
    }
  });

  // 关闭Worker
  formatWorker.terminate();

  // 清理请求缓存
  clearRequestCache();

  // 强制垃圾回收（非标准）
  if (window.gc) {
    window.gc();
  }
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

/* 右侧悬浮任务侧边栏 */
.task-sidebar {
  position: fixed;
  top: 0;
  right: 0;
  height: 100vh;
  width: 60px;
  background-color: #fff;
  border-left: 1px solid #e5e7eb;
  transition: width 0.3s ease;
  box-shadow: -2px 0 10px rgba(0,0,0,0.05);
  z-index: 1000;
  overflow: hidden;
}

.task-sidebar.expanded,
.task-sidebar.pinned {
  width: 450px;
}

.sidebar-trigger {
  position: absolute;
  top: 50%;
  left: 10px;
  transform: translateY(-50%);
  width: 40px;
  height: 100px;
  background-color: #f0f7ff;
  border-radius: 20px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  z-index: 10;
}

.task-sidebar.expanded .sidebar-trigger,
.task-sidebar.pinned .sidebar-trigger {
  display: none;
}

.trigger-icon {
  font-size: 20px;
  margin-bottom: 8px;
}

.pin-btn {
  background: transparent;
  border: none;
  font-size: 16px;
  cursor: pointer;
  padding: 2px;
}

.sidebar-content {
  height: 100%;
  width: 450px;
  padding: 20px;
  overflow-y: auto;
  box-sizing: border-box;
  transform: translateX(0);
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

.sidebar-content::-webkit-scrollbar {
  width: 6px;
}

.sidebar-content::-webkit-scrollbar-track {
  background: #f1f1f1;
  border-radius: 3px;
}

.sidebar-content::-webkit-scrollbar-thumb {
  background: #ccc;
  border-radius: 3px;
}

.sidebar-content::-webkit-scrollbar-thumb:hover {
  background: #999;
}
</style>