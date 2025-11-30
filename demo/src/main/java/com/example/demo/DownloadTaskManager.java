package com.example.demo;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import com.example.demo.UserDownloadTask.BreakpointInfo;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class DownloadTaskManager {
	// 内存缓存任务（生产替换为Redis）
	private final Map<String, UserDownloadTask> taskRepository = new ConcurrentHashMap<>();

	private final Map<String, BreakpointInfo> breakpointMap = new ConcurrentHashMap<>();

	// 本地缓存（Guava Cache）
	private final LoadingCache<String, UserDownloadTask> taskCache = CacheBuilder.newBuilder()
			.maximumSize(1000) // 最大缓存1000个任务
			.expireAfterWrite(5, TimeUnit.MINUTES) // 5分钟过期
			.build(new CacheLoader<String, UserDownloadTask>() {
				@Override
				public UserDownloadTask load(String taskId) {
					return taskRepository.get(taskId); // 缓存未命中时从仓库获取
				}
			});

	/**
	 * 保存任务（更新缓存）
	 */
	public void saveTask(UserDownloadTask task) {
		taskRepository.put(task.getTaskId(), task);
		taskCache.invalidate(task.getTaskId()); // 失效缓存
		try {
			taskCache.put(task.getTaskId(), task); // 更新缓存
		} catch (Exception e) {
			log.error("更新任务缓存失败：{}", task.getTaskId(), e);
		}
	}

	/**
	 * 根据ID查询任务（从缓存获取）
	 */
	public UserDownloadTask getTaskById(String taskId) {
		try {
			return taskCache.get(taskId);
		} catch (Exception e) {
			log.error("获取任务缓存失败，从仓库获取：{}", taskId, e);
			return taskRepository.get(taskId);
		}
	}

	/**
	 * 查询所有任务
	 */
	public List<UserDownloadTask> getAllTasks() {
		return new ArrayList<>(taskRepository.values());
	}

	// 更新任务状态
	public void updateTaskStatus(String taskId, UserDownloadTask.TaskStatus status) {
		UserDownloadTask task = getTaskById(taskId);
		if (task != null) task.setStatus(status);
	}

	// 累加已下载字节
	public void addDownloadedBytes(String taskId, long bytes) {
		UserDownloadTask task = getTaskById(taskId);
		if (task != null) task.setDownloadedBytes(bytes);
	}

	// 设置断点偏移量
	public void setRangeStart(String taskId, long rangeStart) {
		UserDownloadTask task = getTaskById(taskId);
		if (task != null) task.setRangeStart(rangeStart);
	}
}