package com.example.demo;

import com.alibaba.fastjson.JSON;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.LoadingCache;
import com.google.common.cache.CacheLoader;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import javax.annotation.PreDestroy;
import javax.servlet.http.HttpServletResponse;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 多文件下载服务（性能优化版）
 */
@Slf4j
@Service
public class MultiFileDownloader {
	// 任务存储（线程安全）
	private final Map<String, UserDownloadTask> taskRepository = new ConcurrentHashMap<>();

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

	// 优化线程池配置
	private final ExecutorService executorService = new ThreadPoolExecutor(
			5, // 核心线程数
			20, // 最大线程数
			60L, TimeUnit.SECONDS, // 空闲线程存活时间
			new LinkedBlockingQueue<>(100), // 任务队列（避免无限扩容）
			new ThreadFactory() { // 自定义线程命名
				private final AtomicInteger count = new AtomicInteger(1);
				@Override
				public Thread newThread(Runnable r) {
					Thread thread = new Thread(r, "download-task-" + count.getAndIncrement());
					thread.setDaemon(true); // 守护线程
					return thread;
				}
			},
			new ThreadPoolExecutor.CallerRunsPolicy() // 拒绝策略：调用者执行
	);

	/**
	 * 提交多文件下载任务
	 */
	public String submitMultiFileTask(List<String> filePathList, String userId) {
		if (CollectionUtils.isEmpty(filePathList)) {
			throw new IllegalArgumentException("文件路径列表不能为空");
		}

		// 生成唯一任务ID
		String taskId = UUID.randomUUID().toString();

		// 初始化任务
		UserDownloadTask task = new UserDownloadTask();
		task.setTaskId(taskId);
		task.setUserId(userId);
		task.setFilePathList(filePathList.toArray(new String[0]));
		task.setTotalCount(filePathList.size());
		task.setCompletedCount(0);
		task.setFailedCount(0);
		task.setProgress(0);
		task.setFinished(false);
		task.setCancelled(false);

		// 异步计算文件总大小（避免阻塞）
		calculateTotalFileSizeAsync(filePathList).thenAccept(totalBytes -> {
			task.setTotalBytes(totalBytes);
			saveTask(task);
		});

		// 保存初始任务
		saveTask(task);

		// 异步执行下载
		executorService.submit(() -> {
			try {
				downloadFiles(task);
			} catch (Exception e) {
				task.setFailedCount(task.getTotalCount());
				task.setFinished(true);
				saveTask(task);
				log.error("多文件下载任务执行失败：{}", taskId, e);
			}
		});

		log.info("多文件下载任务已提交：{}，文件数：{}", taskId, filePathList.size());
		return taskId;
	}

	/**
	 * 执行文件下载（带进度更新）
	 */
	private void downloadFiles(UserDownloadTask task) {
		String[] filePathList = task.getFilePathList();
		String userId = task.getUserId();

		// 创建用户目录
		File userDir = new File("/tmp/download/" + userId + "/" + task.getTaskId());
		if (!userDir.exists() && !userDir.mkdirs()) {
			log.error("创建用户目录失败：{}", userDir.getAbsolutePath());
			task.setFailedCount(task.getTotalCount());
			task.setFinished(true);
			saveTask(task);
			return;
		}

		// 遍历下载文件
		for (String filePath : filePathList) {
			// 检查任务是否被取消
			if (task.isCancelled()) {
				log.info("下载任务已取消：{}", task.getTaskId());
				break;
			}

			File file = new File(filePath);
			if (!file.exists()) {
				log.warn("文件不存在：{}", filePath);
				task.setFailedCount(task.getFailedCount() + 1);
				saveTask(task);
				continue;
			}

			try (BufferedInputStream bis = new BufferedInputStream(new FileInputStream(file));
				 BufferedOutputStream bos = new BufferedOutputStream(
						 new FileOutputStream(new File(userDir, file.getName())))) {

				byte[] buffer = new byte[64 * 1024]; // 64KB缓冲区
				int bytesRead;
				long bytesWritten = 0;
				long startTime = System.currentTimeMillis();

				// 分片下载（限速+进度更新）
				while ((bytesRead = bis.read(buffer)) != -1) {
					if (task.isCancelled()) {
						break;
					}

					bos.write(buffer, 0, bytesRead);
					bytesWritten += bytesRead;
					task.setDownloadedBytes(task.getDownloadedBytes() + bytesRead);

					// 计算进度（仅在进度变化时更新）
					int newProgress = (int) ((double) task.getDownloadedBytes() /
							(task.getTotalBytes() > 0 ? task.getTotalBytes() : 1) * 100);
					if (newProgress != task.getProgress()) {
						task.setProgress(Math.min(newProgress, 100));
						saveTask(task);
					}
				}

				if (!task.isCancelled()) {
					task.setCompletedCount(task.getCompletedCount() + 1);
				}
			} catch (Exception e) {
				log.error("下载文件失败：{}", filePath, e);
				task.setFailedCount(task.getFailedCount() + 1);
			} finally {
				saveTask(task);
			}
		}

		// 标记任务完成
		if (!task.isCancelled()) {
			task.setFinished(true);
			task.setProgress(100);
			saveTask(task);
			log.info("多文件下载任务完成：{}，成功：{}，失败：{}",
					task.getTaskId(), task.getCompletedCount(), task.getFailedCount());
		}
	}

	/**
	 * 异步计算文件总大小
	 */
	private CompletableFuture<Long> calculateTotalFileSizeAsync(List<String> filePathList) {
		return CompletableFuture.supplyAsync(() -> {
			long total = 0;
			for (String path : filePathList) {
				File file = new File(path);
				if (file.exists()) {
					total += file.length();
				}
			}
			return total;
		}, executorService);
	}

	/**
	 * 取消下载任务
	 */
	public String cancelTask(String taskId) {
		UserDownloadTask task = getTaskById(taskId);
		if (task == null) {
			String msg = "任务不存在：" + taskId;
			log.warn(msg);
			return JSON.toJSONString(Map.of("code", 404, "msg", msg));
		}
		if (task.isFinished()) {
			String msg = "任务已完成，无需取消：" + taskId;
			log.info(msg);
			return JSON.toJSONString(Map.of("code", 200, "msg", msg));
		}

		task.setCancelled(true);
		saveTask(task);
		String msg = "任务已取消：" + taskId;
		log.info(msg);
		return JSON.toJSONString(Map.of("code", 200, "msg", msg));
	}

	/**
	 * SSE推送任务进度（优化版）
	 */
	public void streamTaskProgress(String taskId, HttpServletResponse response) {
		// 设置SSE响应头
		response.setContentType("text/event-stream");
		response.setCharacterEncoding("UTF-8");
		response.setHeader("Cache-Control", "no-cache");
		response.setHeader("Connection", "keep-alive");
		response.setHeader("X-Accel-Buffering", "no"); // 禁用nginx缓冲

		PrintWriter writer = null;
		try {
			writer = response.getWriter();
			UserDownloadTask task = getTaskById(taskId);

			// 任务不存在直接返回
			if (task == null) {
				writer.write("data: {\"code\":404,\"msg\":\"任务不存在\"}\n\n");
				writer.flush();
				return;
			}

			int lastProgress = -1;
			// 仅在进度变化/状态变化时推送
			while (!task.isFinished() && !task.isCancelled()) {
				// 重新获取最新任务状态
				task = getTaskById(taskId);
				if (task == null) break;

				if (task.getProgress() != lastProgress) {
					Map<String, Object> progressData = new HashMap<>();
					progressData.put("taskId", task.getTaskId());
					progressData.put("progress", task.getProgress());
					progressData.put("completedCount", task.getCompletedCount());
					progressData.put("failedCount", task.getFailedCount());
					progressData.put("totalCount", task.getTotalCount());
					progressData.put("downloadedBytes", task.getDownloadedBytes());
					progressData.put("totalBytes", task.getTotalBytes());
					progressData.put("isFinished", task.isFinished());
					progressData.put("isCancelled", task.isCancelled());
					progressData.put("code", 200);

					// SSE消息必须以"data: "开头，"\n\n"结尾
					writer.write("data: " + JSON.toJSONString(progressData) + "\n\n");
					writer.flush();
					lastProgress = task.getProgress();
				}

				// 动态休眠：进度变化快则休眠短，反之则长
				long sleepTime = task.getProgress() < 50 ? 500 : 1000;
				Thread.sleep(sleepTime);

				// 检查客户端连接是否断开
				if (writer.checkError()) {
					log.info("客户端断开连接，停止推送任务进度：{}", taskId);
					break;
				}
			}

			// 推送最终状态
			Map<String, Object> finalData = new HashMap<>();
			finalData.put("taskId", task.getTaskId());
			finalData.put("progress", task.getProgress());
			finalData.put("isFinished", task.isFinished());
			finalData.put("isCancelled", task.isCancelled());
			finalData.put("code", 200);

			writer.write("data: " + JSON.toJSONString(finalData) + "\n\n");
			writer.flush();

		} catch (Exception e) {
			log.error("SSE推送任务进度失败：{}", taskId, e);
		} finally {
			if (writer != null) {
				writer.close();
			}
		}
	}

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

	/**
	 * 优雅关闭线程池
	 */
	@PreDestroy
	public void destroy() {
		log.info("开始关闭下载线程池...");
		executorService.shutdown();
		try {
			if (!executorService.awaitTermination(30, TimeUnit.SECONDS)) {
				log.warn("线程池未正常关闭，强制关闭...");
				executorService.shutdownNow();
			}
		} catch (InterruptedException e) {
			log.error("线程池关闭被中断", e);
			executorService.shutdownNow();
		}
		log.info("下载线程池已关闭");
	}
}