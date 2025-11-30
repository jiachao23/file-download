package com.example.demo.other;

import com.alibaba.fastjson.JSON;
import com.example.demo.UserDownloadTask;
import com.example.demo.UserDownloadTask.BreakpointInfo;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import javax.annotation.PreDestroy;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.Deflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * 通用文件下载服务（终极版）
 * 支持：单文件直接下载 + 多文件流式打包下载
 * 整合优化：前端直传、压缩级别配置、大文件分片、断点续传、磁盘预检查
 */
@Slf4j
@Service
public class UniversalFileDownloader {
	// 任务存储（线程安全）
	private final Map<String, UserDownloadTask> taskRepository = new ConcurrentHashMap<>();
	// 断点续传信息存储（任务ID → 断点信息）
	private final Map<String, BreakpointInfo> breakpointMap = new ConcurrentHashMap<>();
	// 本地缓存（Guava Cache）
	private final LoadingCache<String, UserDownloadTask> taskCache = CacheBuilder.newBuilder()
			.maximumSize(2000) // 最大缓存2000个任务
			.expireAfterWrite(30, TimeUnit.MINUTES) // 30分钟过期（支持断点续传）
			.build(new CacheLoader<String, UserDownloadTask>() {
				@Override
				public UserDownloadTask load(String taskId) {
					return taskRepository.get(taskId);
				}
			});

	// 线程池配置（IO密集型优化）
	private final ExecutorService executorService = new ThreadPoolExecutor(
			10, // 核心线程数
			50, // 最大线程数
			60L, TimeUnit.SECONDS,
			new LinkedBlockingQueue<>(200),
			new ThreadFactory() {
				private final AtomicInteger count = new AtomicInteger(1);
				@Override
				public Thread newThread(Runnable r) {
					Thread thread = new Thread(r, "file-download-task-" + count.getAndIncrement());
					thread.setDaemon(true); // 守护线程
					return thread;
				}
			},
			new ThreadPoolExecutor.CallerRunsPolicy() // 拒绝策略：调用者执行
	);

	// 常量配置（可通过配置文件注入，此处简化）
	private static final int BUFFER_SIZE = 64 * 1024; // 64KB缓冲区
	private static final long SHARD_SIZE = 1024 * 1024 * 5; // 5MB分片大小
	private static final long MIN_FREE_SPACE = 1024 * 1024 * 100; // 最小剩余空间（100MB）
	private static final String BREAKPOINT_DIR = "/tmp/download/breakpoint/"; // 断点存储目录
	private static final String SERVER_STORE_DIR = "/tmp/download/server/"; // 服务器存储目录

	public UniversalFileDownloader() {
		// 初始化目录（断点+服务器存储）
		initDir(BREAKPOINT_DIR);
		initDir(SERVER_STORE_DIR);
	}

	/**
	 * 提交下载任务（单文件直接下载，多文件流式打包）
	 * @param filePathList 文件路径列表（1个=单文件，≥2个=多文件打包）
	 * @param userId 用户ID
	 * @param directDownload 是否直接下载到前端（true=前端直传，false=服务器存储）
	 * @param compressionLevel 压缩级别（0-9，仅多文件生效，默认6）
	 * @param response 响应对象（directDownload=true时必填）
	 * @return 任务ID（directDownload=false时返回，用于后续下载）
	 */
	public String submitDownloadTask(List<String> filePathList, String userId, boolean directDownload,
			Integer compressionLevel, HttpServletResponse response) throws IOException {
		// 校验参数
		if (CollectionUtils.isEmpty(filePathList)) {
			throw new IllegalArgumentException("文件路径列表不能为空");
		}
		// 压缩级别默认值（0-9，多文件生效）
		compressionLevel = compressionLevel == null ? Deflater.DEFAULT_COMPRESSION : Math.max(0, Math.min(9, compressionLevel));

		// 生成唯一任务ID
		String taskId = UUID.randomUUID().toString();
		// 标记任务类型：单文件/多文件
		boolean isSingleFile = filePathList.size() == 1;
		String targetFilePath = filePathList.get(0);

		// 1. 预检查磁盘剩余空间
		checkFreeSpace(filePathList);

		// 2. 计算总大小（单文件=文件大小，多文件=所有文件总和）
		long totalBytes = calculateTotalSize(filePathList);

		// 3. 初始化任务
		UserDownloadTask task = new UserDownloadTask();
		task.setTaskId(taskId);
		task.setUserId(userId);
		task.setFilePathList(filePathList);
		task.setSingleFile(isSingleFile);
		task.setTotalCount(filePathList.size());
		task.setCompletedCount(0);
		task.setFailedCount(0);
		task.setTotalBytes(totalBytes);
		task.setDownloadedBytes(0);
		task.setProgress(0);
		task.setFinished(false);
		task.setCancelled(false);
		task.setDirectDownload(directDownload);
		task.setCompressionLevel(compressionLevel);
		task.setCurrentFileIndex(0);
		task.setCurrentFileOffset(0);

		// 4. 加载断点信息（如果存在）
		loadAndSetBreakpoint(task);

		// 5. 保存任务
		saveTask(task);
		breakpointMap.put(taskId, new BreakpointInfo(task));

		// 6. 异步执行下载任务（单文件/多文件分支）
		executorService.submit(() -> {
			try {
				if (isSingleFile) {
					// 分支1：单文件下载（直接流式传输，无需打包）
					handleSingleFileDownload(task, targetFilePath, response);
				} else {
					// 分支2：多文件下载（流式打包，直接输出/存储）
					handleMultiFilePackDownload(task, response);
				}
			} catch (Exception e) {
				// 异常处理：更新失败计数和状态
				task.setFailedCount(task.getTotalCount() - task.getCompletedCount());
				task.setFinished(true);
				saveTask(task);
				updateBreakpointInfo(task);
				log.error("下载任务执行失败：{}（单文件：{}）", taskId, isSingleFile, e);
			}
		});

		log.info("下载任务已提交：{}，单文件：{}，文件数：{}，总大小：{}字节，直接下载：{}，压缩级别：{}",
				taskId, isSingleFile, filePathList.size(), totalBytes, directDownload, compressionLevel);
		return directDownload ? null : taskId;
	}

	/**
	 * 分支1：单文件下载（直接流式传输，无需打包）
	 */
	private void handleSingleFileDownload(UserDownloadTask task, String filePath, HttpServletResponse response) throws IOException {
		String taskId = task.getTaskId();
		File sourceFile = new File(filePath);

		// 校验文件存在性
		if (!sourceFile.exists() || !sourceFile.isFile()) {
			log.warn("单文件不存在：{}", filePath);
			task.setFailedCount(1);
			task.setFinished(true);
			saveTask(task);
			updateBreakpointInfo(task);
			return;
		}

		// 直接下载到前端 → 写入响应流；服务器存储 → 写入本地文件
		if (task.isDirectDownload()) {
			// 前端直传（支持断点续传）
			streamSingleFileToFrontend(task, sourceFile, response);
		} else {
			// 服务器存储（支持断点续传）
			streamSingleFileToServer(task, sourceFile);
		}

		// 任务完成：更新状态
		if (!task.isCancelled()) {
			task.setCompletedCount(1);
			task.setFinished(true);
			task.setProgress(100);
			saveTask(task);
			deleteBreakpointInfo(taskId);
			log.info("单文件下载完成：{}，文件：{}，大小：{}字节", taskId, filePath, sourceFile.length());
		}
	}

	/**
	 * 分支2：多文件下载（流式打包，直接输出/存储）
	 */
	private void handleMultiFilePackDownload(UserDownloadTask task, HttpServletResponse response) throws IOException {
		String taskId = task.getTaskId();
		List<String> filePathList = task.getFilePathList();

		// 直接下载到前端 → 打包后直传；服务器存储 → 打包后存储
		if (task.isDirectDownload()) {
			// 前端直传（流式打包，无中间文件）
			streamMultiFileToFrontend(task, response);
		} else {
			// 服务器存储（流式打包，保存压缩包）
			streamMultiFileToServer(task);
		}

		// 任务完成：更新状态
		if (!task.isCancelled()) {
			task.setFinished(true);
			task.setProgress(100);
			saveTask(task);
			deleteBreakpointInfo(taskId);
			log.info("多文件打包下载完成：{}，成功：{}，失败：{}",
					taskId, task.getCompletedCount(), task.getFailedCount());
		}
	}

	/**
	 * 单文件 → 前端直传（支持断点续传）
	 */
	private void streamSingleFileToFrontend(UserDownloadTask task, File sourceFile, HttpServletResponse response) throws IOException {
		String taskId = task.getTaskId();
		long fileSize = sourceFile.length();
		String fileName = sourceFile.getName();

		// 设置响应头（支持断点续传）
		response.setContentType(getMimeType(fileName));
		response.setCharacterEncoding("UTF-8");
		response.setHeader("Cache-Control", "no-cache");
		response.setHeader("Connection", "keep-alive");
		response.setHeader("X-Accel-Buffering", "no"); // 禁用nginx缓冲
		response.setHeader("Content-Disposition", "attachment;filename=\"" + fileName + "\"");
		response.setHeader("Accept-Ranges", "bytes");

		// 处理断点续传请求
		long startOffset = 0;
		HttpServletRequest request = getRequestFromContext();
		if (request != null && request.getHeader("Range") != null) {
			String range = request.getHeader("Range");
			startOffset = Long.parseLong(range.split("=")[1].split("-")[0]);
			// 断点偏移量优先使用任务中的记录（可能是服务端中断）
			startOffset = Math.max(startOffset, task.getCurrentFileOffset());
			response.setStatus(HttpServletResponse.SC_PARTIAL_CONTENT);
			response.setHeader("Content-Range", "bytes " + startOffset + "-" + (fileSize - 1) + "/" + fileSize);
			response.setHeader("Content-Length", String.valueOf(fileSize - startOffset));
			log.info("单文件断点续传：{}，起始偏移量：{}", taskId, startOffset);
		} else {
			response.setHeader("Content-Length", String.valueOf(fileSize));
		}

		// 流式传输文件（支持大文件分片）
		try (BufferedInputStream bis = new BufferedInputStream(new FileInputStream(sourceFile));
			 ServletOutputStream sos = response.getOutputStream();
			 BufferedOutputStream bos = new BufferedOutputStream(sos)) {

			byte[] buffer = new byte[BUFFER_SIZE];
			long bytesReadTotal = 0;

			// 跳转到断点偏移量
			if (startOffset > 0) {
				bis.skip(startOffset);
				bytesReadTotal = startOffset;
				task.setDownloadedBytes(startOffset);
				task.setProgress((int) ((double) startOffset / fileSize * 100));
				saveTask(task);
			}

			int bytesRead;
			while ((bytesRead = bis.read(buffer)) != -1) {
				// 检查任务是否取消
				if (task.isCancelled()) {
					log.info("单文件下载任务已取消：{}", taskId);
					updateBreakpointInfo(task);
					return;
				}

				// 写入响应流
				bos.write(buffer, 0, bytesRead);
				bytesReadTotal += bytesRead;
				task.setDownloadedBytes(bytesReadTotal);

				// 大文件分片：每5MB更新进度和断点
				if (bytesReadTotal % SHARD_SIZE == 0) {
					updateTaskProgress(task);
					task.setCurrentFileOffset(bytesReadTotal);
					updateBreakpointInfo(task);
					log.debug("单文件分片进度：{}，已下载：{}MB", taskId, bytesReadTotal / (1024 * 1024));
				}

				// 刷新缓冲区，避免数据积压
				bos.flush();
			}

			// 任务完成：标记成功，清除断点
			task.setCompletedCount(1);
			task.setFinished(true);
			task.setProgress(100);
			saveTask(task);
			deleteBreakpointInfo(taskId);

		} catch (Exception e) {
			// 异常时保存断点
			updateBreakpointInfo(task);
			throw e;
		}
	}

	/**
	 * 单文件 → 服务器存储（支持断点续传）
	 */
	private void streamSingleFileToServer(UserDownloadTask task, File sourceFile) throws IOException {
		String taskId = task.getTaskId();
		String userId = task.getUserId();
		String fileName = sourceFile.getName();

		// 创建存储目录
		File userDir = new File(SERVER_STORE_DIR + userId + "/" + taskId);
		initDir(userDir.getAbsolutePath());
		// 目标文件路径
		String targetFilePath = new File(userDir, fileName).getAbsolutePath();
		File targetFile = new File(targetFilePath);

		// 流式写入文件（支持断点续传：追加模式）
		try (BufferedInputStream bis = new BufferedInputStream(new FileInputStream(sourceFile));
			 FileOutputStream fos = new FileOutputStream(targetFile, task.getCurrentFileOffset() > 0);
			 BufferedOutputStream bos = new BufferedOutputStream(fos)) {

			byte[] buffer = new byte[BUFFER_SIZE];
			long fileSize = sourceFile.length();
			long bytesReadTotal = 0;

			// 跳转到断点偏移量
			if (task.getCurrentFileOffset() > 0) {
				bis.skip(task.getCurrentFileOffset());
				bytesReadTotal = task.getCurrentFileOffset();
				task.setDownloadedBytes(bytesReadTotal);
				saveTask(task);
			}

			int bytesRead;
			while ((bytesRead = bis.read(buffer)) != -1) {
				if (task.isCancelled()) {
					log.info("单文件服务器存储任务已取消：{}", taskId);
					updateBreakpointInfo(task);
					return;
				}

				bos.write(buffer, 0, bytesRead);
				bytesReadTotal += bytesRead;
				task.setDownloadedBytes(bytesReadTotal);

				// 大文件分片：每5MB更新进度和断点
				if (bytesReadTotal % SHARD_SIZE == 0) {
					updateTaskProgress(task);
					task.setCurrentFileOffset(bytesReadTotal);
					updateBreakpointInfo(task);
					log.debug("单文件服务器存储分片：{}，已下载：{}MB", taskId, bytesReadTotal / (1024 * 1024));
				}
			}

			// 任务完成：更新状态，清除断点
			task.setCompletedCount(1);
			task.setFinished(true);
			task.setProgress(100);
			task.setTargetFilePath(targetFilePath); // 记录服务器存储路径
			saveTask(task);
			deleteBreakpointInfo(taskId);
			log.info("单文件服务器存储完成：{}，路径：{}，大小：{}字节", taskId, targetFilePath, targetFile.length());

		} catch (Exception e) {
			updateBreakpointInfo(task);
			throw e;
		}
	}

	/**
	 * 多文件 → 前端直传（流式打包，无中间文件）
	 */
	private void streamMultiFileToFrontend(UserDownloadTask task, HttpServletResponse response) throws IOException {
		String taskId = task.getTaskId();
		List<String> filePathList = task.getFilePathList();
		int compressionLevel = task.getCompressionLevel();

		// 设置响应头（打包下载）
		response.setContentType("application/zip");
		response.setCharacterEncoding("UTF-8");
		response.setHeader("Cache-Control", "no-cache");
		response.setHeader("Connection", "keep-alive");
		response.setHeader("X-Accel-Buffering", "no");
		response.setHeader("Content-Disposition", "attachment;filename=\"" + taskId + ".zip\"");
		response.setHeader("Accept-Ranges", "bytes");

		// 处理断点续传
		long startOffset = 0;
		HttpServletRequest request = getRequestFromContext();
		if (request != null && request.getHeader("Range") != null) {
			String range = request.getHeader("Range");
			startOffset = Long.parseLong(range.split("=")[1].split("-")[0]);
			response.setStatus(HttpServletResponse.SC_PARTIAL_CONTENT);
			response.setHeader("Content-Range", "bytes " + startOffset + "-" + (task.getTotalBytes() - 1) + "/" + task.getTotalBytes());
		}

		// 流式打包+传输
		try (ServletOutputStream sos = response.getOutputStream();
			 BufferedOutputStream bos = new BufferedOutputStream(sos);
			 ZipOutputStream zos = new ZipOutputStream(bos)) {

			zos.setLevel(compressionLevel); // 设置压缩级别
			int currentFileIndex = task.getCurrentFileIndex();
			long currentFileOffset = task.getCurrentFileOffset();

			for (; currentFileIndex < filePathList.size(); currentFileIndex++) {
				if (task.isCancelled()) {
					log.info("多文件打包任务已取消：{}", taskId);
					updateBreakpointInfo(task);
					return;
				}

				String filePath = filePathList.get(currentFileIndex);
				File sourceFile = new File(filePath);
				if (!sourceFile.exists() || !sourceFile.isFile()) {
					log.warn("多文件打包：文件不存在：{}", filePath);
					task.setFailedCount(task.getFailedCount() + 1);
					saveTask(task);
					updateBreakpointInfo(task);
					continue;
				}

				// 添加ZIP条目
				ZipEntry zipEntry = new ZipEntry(sourceFile.getName());
				zos.putNextEntry(zipEntry);

				// 流式读取+写入ZIP
				try (BufferedInputStream bis = new BufferedInputStream(new FileInputStream(sourceFile))) {
					byte[] buffer = new byte[BUFFER_SIZE];
					long bytesReadTotal = 0;

					// 跳转到断点偏移量
					if (currentFileOffset > 0) {
						bis.skip(currentFileOffset);
						bytesReadTotal = currentFileOffset;
					}

					int bytesRead;
					while ((bytesRead = bis.read(buffer)) != -1) {
						if (task.isCancelled()) {
							zos.closeEntry();
							updateBreakpointInfo(task);
							return;
						}

						zos.write(buffer, 0, bytesRead);
						bytesReadTotal += bytesRead;
						task.setDownloadedBytes(task.getDownloadedBytes() + bytesRead);

						// 分片更新进度和断点
						if (bytesReadTotal % SHARD_SIZE == 0) {
							updateTaskProgress(task);
							task.setCurrentFileIndex(currentFileIndex);
							task.setCurrentFileOffset(bytesReadTotal);
							updateBreakpointInfo(task);
						}

						bos.flush();
					}

					zos.closeEntry();
					task.setCompletedCount(task.getCompletedCount() + 1);
					task.setCurrentFileIndex(currentFileIndex + 1);
					task.setCurrentFileOffset(0);
				} catch (Exception e) {
					log.error("多文件打包：文件处理失败：{}", filePath, e);
					zos.closeEntry();
					task.setFailedCount(task.getFailedCount() + 1);
				} finally {
					saveTask(task);
					updateBreakpointInfo(task);
				}
			}

		} catch (Exception e) {
			updateBreakpointInfo(task);
			throw e;
		}
	}

	/**
	 * 多文件 → 服务器存储（流式打包，支持断点续传）
	 */
	private void streamMultiFileToServer(UserDownloadTask task) throws IOException {
		String taskId = task.getTaskId();
		String userId = task.getUserId();
		List<String> filePathList = task.getFilePathList();
		int compressionLevel = task.getCompressionLevel();

		// 创建存储目录
		File userDir = new File(SERVER_STORE_DIR + userId + "/" + taskId);
		initDir(userDir.getAbsolutePath());
		// 压缩包路径
		String zipFilePath = new File(userDir, taskId + ".zip").getAbsolutePath();
		File zipFile = new File(zipFilePath);

		// 流式打包+存储
		try (FileOutputStream fos = new FileOutputStream(zipFile, task.getDownloadedBytes() > 0);
			 BufferedOutputStream bos = new BufferedOutputStream(fos);
			 ZipOutputStream zos = new ZipOutputStream(bos)) {

			zos.setLevel(compressionLevel);
			int currentFileIndex = task.getCurrentFileIndex();
			long currentFileOffset = task.getCurrentFileOffset();

			for (; currentFileIndex < filePathList.size(); currentFileIndex++) {
				if (task.isCancelled()) {
					log.info("多文件服务器存储任务已取消：{}", taskId);
					updateBreakpointInfo(task);
					return;
				}

				String filePath = filePathList.get(currentFileIndex);
				File sourceFile = new File(filePath);
				if (!sourceFile.exists() || !sourceFile.isFile()) {
					log.warn("多文件打包：文件不存在：{}", filePath);
					task.setFailedCount(task.getFailedCount() + 1);
					saveTask(task);
					updateBreakpointInfo(task);
					continue;
				}

				zos.putNextEntry(new ZipEntry(sourceFile.getName()));
				try (BufferedInputStream bis = new BufferedInputStream(new FileInputStream(sourceFile))) {
					byte[] buffer = new byte[BUFFER_SIZE];
					long bytesReadTotal = 0;

					if (currentFileOffset > 0) {
						bis.skip(currentFileOffset);
						bytesReadTotal = currentFileOffset;
					}

					int bytesRead;
					while ((bytesRead = bis.read(buffer)) != -1) {
						if (task.isCancelled()) {
							zos.closeEntry();
							updateBreakpointInfo(task);
							return;
						}

						zos.write(buffer, 0, bytesRead);
						bytesReadTotal += bytesRead;
						task.setDownloadedBytes(task.getDownloadedBytes() + bytesRead);

						if (bytesReadTotal % SHARD_SIZE == 0) {
							updateTaskProgress(task);
							task.setCurrentFileIndex(currentFileIndex);
							task.setCurrentFileOffset(bytesReadTotal);
							updateBreakpointInfo(task);
						}
					}

					zos.closeEntry();
					task.setCompletedCount(task.getCompletedCount() + 1);
					task.setCurrentFileIndex(currentFileIndex + 1);
					task.setCurrentFileOffset(0);
				} catch (Exception e) {
					log.error("多文件打包：文件处理失败：{}", filePath, e);
					zos.closeEntry();
					task.setFailedCount(task.getFailedCount() + 1);
				} finally {
					saveTask(task);
					updateBreakpointInfo(task);
				}
			}

			// 任务完成：更新压缩包实际大小
			task.setFinished(true);
			task.setProgress(100);
			task.setTotalBytes(zipFile.length());
			task.setDownloadedBytes(zipFile.length());
			task.setTargetFilePath(zipFilePath);
			saveTask(task);
			deleteBreakpointInfo(taskId);
			log.info("多文件服务器存储完成：{}，压缩包路径：{}，大小：{}字节", taskId, zipFilePath, zipFile.length());

		} catch (Exception e) {
			updateBreakpointInfo(task);
			throw e;
		}
	}

	/**
	 * 从服务器下载已存储的文件（单文件=原文件，多文件=压缩包）
	 */
	public void downloadFromServer(String taskId, String userId, HttpServletResponse response) throws IOException {
		UserDownloadTask task = getTaskById(taskId);
		// 校验任务状态
		if (task == null) {
			throw new FileNotFoundException("任务不存在：" + taskId);
		}
		if (!task.isFinished() || task.isCancelled()) {
			throw new IllegalStateException("任务未完成或已取消，无法下载：" + taskId);
		}
		if (!userId.equals(task.getUserId())) {
			throw new SecurityException("无权限下载该任务文件");
		}

		// 获取服务器存储路径
		String targetFilePath = task.getTargetFilePath();
		File targetFile = new File(targetFilePath);
		if (!targetFile.exists()) {
			throw new FileNotFoundException("文件不存在：" + targetFilePath);
		}

		// 设置响应头
		String fileName = task.isSingleFile() ? targetFile.getName() : taskId + ".zip";
		response.setContentType(task.isSingleFile() ? getMimeType(fileName) : "application/zip");
		response.setCharacterEncoding("UTF-8");
		response.setHeader("Content-Disposition", "attachment;filename=\"" + fileName + "\"");
		response.setHeader("Content-Length", String.valueOf(targetFile.length()));

		// 流式下载到前端
		try (BufferedInputStream bis = new BufferedInputStream(new FileInputStream(targetFile));
			 ServletOutputStream sos = response.getOutputStream();
			 BufferedOutputStream bos = new BufferedOutputStream(sos)) {

			byte[] buffer = new byte[BUFFER_SIZE];
			int bytesRead;
			while ((bytesRead = bis.read(buffer)) != -1) {
				bos.write(buffer, 0, bytesRead);
				bos.flush();
			}
		}
		log.info("服务器文件下载完成：{}，文件路径：{}", taskId, targetFilePath);
	}

	// ====================== 通用工具方法 ======================

	/**
	 * 计算总大小（单文件/多文件）
	 */
	private long calculateTotalSize(List<String> filePathList) {
		if (filePathList.size() == 1) {
			// 单文件：直接返回文件大小
			File file = new File(filePathList.get(0));
			return file.exists() ? file.length() : 0;
		} else {
			// 多文件：累加所有文件大小
			long total = 0;
			for (String path : filePathList) {
				File file = new File(path);
				if (file.exists() && file.isFile()) {
					total += file.length();
				}
			}
			return total;
		}
	}

	/**
	 * 磁盘空间预检查
	 */
	private void checkFreeSpace(List<String> filePathList) throws IOException {
		// 检查源文件所在磁盘
		for (String path : filePathList) {
			File file = new File(path);
			if (file.exists()) {
				Path fileDir = Paths.get(file.getAbsolutePath());
				long freeSpace = Files.getFileStore(fileDir).getUsableSpace();
				if (freeSpace < MIN_FREE_SPACE) {
					throw new IOException("源文件磁盘剩余空间不足：" + freeSpace / (1024 * 1024) + "MB（需≥100MB）");
				}
			}
		}
		// 检查目标存储磁盘
		Path targetDir = Paths.get(SERVER_STORE_DIR);
		long targetFreeSpace = Files.getFileStore(targetDir).getUsableSpace();
		if (targetFreeSpace < MIN_FREE_SPACE) {
			throw new IOException("存储磁盘剩余空间不足：" + targetFreeSpace / (1024 * 1024) + "MB（需≥100MB）");
		}
	}

	/**
	 * 更新任务进度
	 */
	private void updateTaskProgress(UserDownloadTask task) {
		if (task.getTotalBytes() <= 0) {
			return;
		}
		int newProgress = (int) ((double) task.getDownloadedBytes() / task.getTotalBytes() * 100);
		if (newProgress != task.getProgress()) {
			task.setProgress(Math.min(newProgress, 100));
			saveTask(task);
		}
	}

	/**
	 * 加载断点信息并设置到任务
	 */
	private void loadAndSetBreakpoint(UserDownloadTask task) {
		String taskId = task.getTaskId();
		BreakpointInfo breakpoint = breakpointMap.get(taskId);
		if (breakpoint == null) {
			// 从文件加载
			File breakpointFile = new File(BREAKPOINT_DIR + taskId + ".breakpoint");
			if (breakpointFile.exists()) {
				try (BufferedReader br = new BufferedReader(new FileReader(breakpointFile))) {
					breakpoint = JSON.parseObject(br.readLine(), BreakpointInfo.class);
				} catch (Exception e) {
					log.error("加载断点信息失败：{}", taskId, e);
					breakpointFile.delete();
				}
			}
		}
		// 设置断点信息
		if (breakpoint != null) {
			task.setDownloadedBytes(breakpoint.getDownloadedBytes());
			task.setCompletedCount(breakpoint.getCompletedCount());
			task.setFailedCount(breakpoint.getFailedCount());
			task.setCurrentFileIndex(breakpoint.getCurrentFileIndex());
			task.setCurrentFileOffset(breakpoint.getCurrentFileOffset());
			task.setProgress((int) ((double) breakpoint.getDownloadedBytes() / task.getTotalBytes() * 100));
		}
	}

	/**
	 * 保存断点信息（内存+文件持久化）
	 */
	private void updateBreakpointInfo(UserDownloadTask task) {
		String taskId = task.getTaskId();
		BreakpointInfo breakpoint = new BreakpointInfo(task);
		// 内存缓存
		breakpointMap.put(taskId, breakpoint);
		// 文件持久化
		try (BufferedWriter bw = new BufferedWriter(new FileWriter(BREAKPOINT_DIR + taskId + ".breakpoint"))) {
			bw.write(JSON.toJSONString(breakpoint));
		} catch (Exception e) {
			log.error("保存断点信息失败：{}", taskId, e);
		}
	}

	/**
	 * 删除断点信息
	 */
	private void deleteBreakpointInfo(String taskId) {
		breakpointMap.remove(taskId);
		File breakpointFile = new File(BREAKPOINT_DIR + taskId + ".breakpoint");
		if (breakpointFile.exists() && !breakpointFile.delete()) {
			log.warn("删除断点文件失败：{}", taskId);
		}
	}

	/**
	 * 初始化目录（不存在则创建）
	 */
	private void initDir(String dirPath) {
		File dir = new File(dirPath);
		if (!dir.exists()) {
			dir.mkdirs();
		}
	}

	/**
	 * 获取文件MIME类型（单文件下载用）
	 */
	private String getMimeType(String fileName) {
		// 简化实现，可通过Files.probeContentType或第三方库（如apache tika）增强
		if (fileName.endsWith(".zip")) return "application/zip";
		if (fileName.endsWith(".txt")) return "text/plain";
		if (fileName.endsWith(".pdf")) return "application/pdf";
		if (fileName.endsWith(".jpg") || fileName.endsWith(".jpeg")) return "image/jpeg";
		if (fileName.endsWith(".png")) return "image/png";
		return "application/octet-stream"; // 默认二进制流
	}

	/**
	 * 取消任务
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

		// 标记取消
		task.setCancelled(true);
		saveTask(task);
		// 保存断点（支持恢复）
		updateBreakpointInfo(task);

		// 清理服务器存储的不完整文件
		if (!task.isDirectDownload() && task.getTargetFilePath() != null) {
			File incompleteFile = new File(task.getTargetFilePath());
			if (incompleteFile.exists() && !incompleteFile.delete()) {
				log.warn("删除不完整文件失败：{}", task.getTargetFilePath());
			}
		}

		String msg = "任务已取消：" + taskId;
		log.info(msg);
		return JSON.toJSONString(Map.of("code", 200, "msg", msg));
	}

	/**
	 * 恢复断点续传任务
	 */
	public String resumeTask(String taskId, HttpServletResponse response) throws IOException {
		UserDownloadTask task = getTaskById(taskId);
		if (task == null) {
			String msg = "任务不存在，无法恢复：" + taskId;
			log.warn(msg);
			return JSON.toJSONString(Map.of("code", 404, "msg", msg));
		}
		if (task.isFinished() || task.isCancelled()) {
			String msg = "任务已完成或取消，无法恢复：" + taskId;
			log.info(msg);
			return JSON.toJSONString(Map.of("code", 400, "msg", msg));
		}

		// 重新提交任务（复用原有逻辑）
		return submitDownloadTask(task.getFilePathList(), task.getUserId(),
				task.isDirectDownload(), task.getCompressionLevel(), response);
	}

	/**
	 * 从上下文获取HttpServletRequest（Spring框架）
	 */
	private HttpServletRequest getRequestFromContext() {
		try {
			return org.springframework.web.context.request.RequestContextHolder.getRequestAttributes() != null ?
					((org.springframework.web.context.request.ServletRequestAttributes)
							org.springframework.web.context.request.RequestContextHolder.getRequestAttributes()).getRequest() : null;
		} catch (Exception e) {
			log.warn("获取Request失败", e);
			return null;
		}
	}

	// ====================== 原有基础方法 ======================
	public void saveTask(UserDownloadTask task) {
		taskRepository.put(task.getTaskId(), task);
		taskCache.invalidate(task.getTaskId());
		try {
			taskCache.put(task.getTaskId(), task);
		} catch (Exception e) {
			log.error("更新任务缓存失败：{}", task.getTaskId(), e);
		}
	}

	public UserDownloadTask getTaskById(String taskId) {
		try {
			return taskCache.get(taskId);
		} catch (Exception e) {
			log.error("获取任务缓存失败：{}", taskId, e);
			return taskRepository.get(taskId);
		}
	}

	public List<UserDownloadTask> getAllTasks() {
		return new ArrayList<>(taskRepository.values());
	}

	/**
	 * SSE推送任务进度（保持原有逻辑）
	 */
	public void streamTaskProgress(String taskId, HttpServletResponse response) {
		response.setContentType("text/event-stream");
		response.setCharacterEncoding("UTF-8");
		response.setHeader("Cache-Control", "no-cache");
		response.setHeader("Connection", "keep-alive");
		response.setHeader("X-Accel-Buffering", "no");

		PrintWriter writer = null;
		try {
			writer = response.getWriter();
			UserDownloadTask task = getTaskById(taskId);
			if (task == null) {
				writer.write("data: {\"code\":404,\"msg\":\"任务不存在\"}\n\n");
				writer.flush();
				return;
			}

			int lastProgress = -1;
			while (!task.isFinished() && !task.isCancelled()) {
				task = getTaskById(taskId);
				if (task == null) break;

				if (task.getProgress() != lastProgress) {
					Map<String, Object> data = new HashMap<>();
					data.put("taskId", task.getTaskId());
					data.put("progress", task.getProgress());
					data.put("completedCount", task.getCompletedCount());
					data.put("failedCount", task.getFailedCount());
					data.put("downloadedBytes", task.getDownloadedBytes());
					data.put("totalBytes", task.getTotalBytes());
					data.put("isFinished", task.isFinished());
					data.put("isCancelled", task.isCancelled());
					data.put("code", 200);
					writer.write("data: " + JSON.toJSONString(data) + "\n\n");
					writer.flush();
					lastProgress = task.getProgress();
				}

				Thread.sleep(task.getProgress() < 50 ? 500 : 1000);
				if (writer.checkError()) break;
			}

			// 推送最终状态
			Map<String, Object> finalData = new HashMap<>();
			finalData.put("taskId", task.getTaskId());
			finalData.put("progress", task.getProgress());
			finalData.put("finished", task.isFinished());
			finalData.put("cancelled", task.isCancelled());
			finalData.put("code", 200);
			writer.write("data: " + JSON.toJSONString(finalData) + "\n\n");
			writer.flush();

		} catch (Exception e) {
			log.error("SSE推送进度失败：{}", taskId, e);
		} finally {
			if (writer != null) writer.close();
		}
	}

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