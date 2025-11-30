package com.example.demo;

import com.alibaba.fastjson.JSON;
import com.example.demo.UserDownloadTask.TaskStage;
import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Autowired;
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
	@Autowired
	private DownloadTaskManager taskManager;

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
		task.setFilePathList(filePathList);
		task.setTotalCount(filePathList.size());
		task.setCompletedCount(0);
		task.setFailedCount(0);
		task.setStageProgress(0);
		task.setFinished(false);
		task.setCancelled(false);

		// 异步计算文件总大小（避免阻塞）
//
//		calculateTotalFileSizeAsync(filePathList).thenAccept(totalBytes -> {
//			task.setTotalBytes(totalBytes);
//			taskManager.saveTask(task);
//		});

		// 保存初始任务
		taskManager.saveTask(task);

		// 异步执行下载
		executorService.submit(() -> {
			try {
				downloadFiles(task);
			} catch (Exception e) {
				task.setFailedCount(task.getTotalCount());
				task.setFinished(true);
				taskManager.saveTask(task);
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
		List<String> filePathList = task.getFilePathList();
		String userId = task.getUserId();
		int totalCount = task.getTotalCount(); // 总文件数

		// 创建用户目录（代码不变）
		File userDir = new File("/tmp/download/" + userId + "/" + task.getTaskId());
		if (!userDir.exists() && !userDir.mkdirs()) {
			log.error("创建用户目录失败：{}", userDir.getAbsolutePath());
			task.setFailedCount(task.getTotalCount());
			task.setFinished(true);
			taskManager.saveTask(task);
			return;
		}

		// 1. 下载阶段
		task.setCurrentStage(TaskStage.DOWNLOADING);
		taskManager.saveTask(task);

		// 遍历下载文件
		for (String filePath : filePathList) {
			// 检查任务是否被取消（代码不变）
			if (task.isCancelled()) {
				log.info("下载任务已取消：{}", task.getTaskId());
				break;
			}

			File file = new File(filePath);
			if (!file.exists()) {
				log.warn("文件不存在：{}", filePath);
				task.setFailedCount(task.getFailedCount() + 1);
				taskManager.saveTask(task);
				continue;
			}

			try (BufferedInputStream bis = new BufferedInputStream(new FileInputStream(file));
				 BufferedOutputStream bos = new BufferedOutputStream(
						 new FileOutputStream(new File(userDir, file.getName())))) {

				byte[] buffer = new byte[64 * 1024]; // 64KB缓冲区
				int bytesRead;

				// 下载文件（仅保留文件复制逻辑，移除字节数统计对进度的影响）
				while ((bytesRead = bis.read(buffer)) != -1) {
					if (task.isCancelled()) {
						break;
					}
					bos.write(buffer, 0, bytesRead);
					// 移除：基于字节数的进度更新（不再需要）
					// task.setDownloadedBytes(task.getDownloadedBytes() + bytesRead);
				}

				if (!task.isCancelled()) {
					task.setCompletedCount(task.getCompletedCount() + 1); // 成功下载，完成数+1
					// 基于文件数量计算进度：(已完成数 / 总数) * 100
					int newProgress = (int) (((double) task.getCompletedCount() / totalCount) * 100);
					task.setStageProgress(Math.min(newProgress, 100)); // 避免超过100%
					taskManager.saveTask(task); // 实时更新进度
				}
				// 2. 打包阶段（下载完成后）
				if (!task.isCancelled() && task.getCompletedCount() > 0) {
					task.setCurrentStage(TaskStage.PACKAGING);
					task.setStageProgress(0); // 重置阶段进度
					taskManager.saveTask(task);

					try {
						// 获取下载的文件列表
						File[] downloadedFiles = userDir.listFiles();
						if (downloadedFiles != null && downloadedFiles.length > 0) {
							int fileCount = downloadedFiles.length;
							// 模拟打包进度（实际应根据打包处理逻辑更新）
							for (int i = 0; i <= 100; i += 5) {
								// 每5%更新一次进度
								task.setStageProgress(i);
								// 总进度 = 下载进度(100%) + 打包进度(100%) → 映射到0-100
								task.setStageProgress(100 - (int) ((100 - i) * 0.5)); // 打包阶段占总进度的50%
								taskManager.saveTask(task);
								Thread.sleep(200); // 模拟打包耗时
							}

							// 打包完成（生成最终压缩包路径）
							String zipPath = userDir.getAbsolutePath() + ".zip";
							task.setFilePath(zipPath); // 记录压缩包路径
						}
					} catch (Exception e) {
						log.error("文件打包失败", e);
						task.setFailedCount(task.getFailedCount() + 1);
					}
				}
			} catch (Exception e) {
				log.error("下载文件失败：{}", filePath, e);
				task.setFailedCount(task.getFailedCount() + 1);
			} finally {
				taskManager.saveTask(task);
			}
		}

		// 任务结束时强制进度为100%（无论成功失败，确保最终状态正确）
		if (!task.isCancelled()) {
			task.setCurrentStage(TaskStage.COMPLETED);
			task.setStageProgress(100);
			task.setStageProgress(100);
			task.setFinished(true);
			taskManager.saveTask(task);
			log.info("多文件下载任务完成：{}，成功：{}，失败：{}",
					task.getTaskId(), task.getCompletedCount(), task.getFailedCount());
		}
	}

	/**
	 * 取消下载任务
	 */
	public String cancelTask(String taskId) {
		UserDownloadTask task = taskManager.getTaskById(taskId);
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
		taskManager.saveTask(task);
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
			UserDownloadTask task = taskManager.getTaskById(taskId);

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
				task = taskManager.getTaskById(taskId);
				if (task == null) break;

				if (task.getStageProgress() != lastProgress) {
					// SSE消息必须以"data: "开头，"\n\n"结尾
					writer.write("data: " + JSON.toJSONString(task) + "\n\n");
					writer.flush();
					lastProgress = task.getStageProgress();
				}

				// 动态休眠：进度变化快则休眠短，反之则长
				long sleepTime = task.getStageProgress() < 50 ? 500 : 1000;
				Thread.sleep(sleepTime);

				// 检查客户端连接是否断开
				if (writer.checkError()) {
					log.info("客户端断开连接，停止推送任务进度：{}", taskId);
					break;
				}
			}

			System.out.println(JSON.toJSONString(task));
			writer.write("data: " + JSON.toJSONString(task) + "\n\n");
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