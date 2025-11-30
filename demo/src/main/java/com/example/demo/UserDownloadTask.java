package com.example.demo;

import lombok.Data;
import java.io.Serializable;
import java.util.List;

/**
 * 下载任务实体（序列化支持Redis存储）
 */
@Data
public class UserDownloadTask implements Serializable {
	private static final long serialVersionUID = 1L;

	// 任务ID
	private String taskId;
	// 用户ID
	private String userId;
	// 文件路径列表
	private List<String> filePathList;
	// 是否单文件
	private boolean isSingleFile;
	// 总文件数
	private int totalCount;
	// 已完成文件数
	private int completedCount;
	// 失败文件数
	private int failedCount;
	// 总字节数
	private long totalBytes;
	// 已下载字节数
	private long downloadedBytes;
	// 下载进度（0-100）
	private int progress;
	// 是否完成
	private boolean finished;
	// 是否取消
	private boolean cancelled;
	private long rangeStart;
	private TaskStatus status;
	private boolean directDownload; // 是否前端直传
	private int compressionLevel; // 压缩级别（多文件生效）
	private int currentFileIndex; // 当前文件索引（断点用）
	private long currentFileOffset; // 当前文件偏移量（断点用）
	private String targetFilePath; // 服务器存储路径

	// 任务创建时间
	private long createTime = System.currentTimeMillis();

	/**
	 * 断点信息实体类
	 */
	@Data
	public static class BreakpointInfo {
		private String taskId;
		private long downloadedBytes;
		private int completedCount;
		private int failedCount;
		private int currentFileIndex;
		private long currentFileOffset;

		public BreakpointInfo() {}

		public BreakpointInfo(UserDownloadTask task) {
			this.taskId = task.getTaskId();
			this.downloadedBytes = task.getDownloadedBytes();
			this.completedCount = task.getCompletedCount();
			this.failedCount = task.getFailedCount();
			this.currentFileIndex = task.getCurrentFileIndex();
			this.currentFileOffset = task.getCurrentFileOffset();
		}
	}

	public enum TaskStatus {
		WAITING, DOWNLOADING, COMPLETED, FAILED, PAUSED
	}
}