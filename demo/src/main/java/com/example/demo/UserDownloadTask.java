package com.example.demo;

import lombok.Data;
import java.io.Serializable;

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
	private String[] filePathList;
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
	private boolean isFinished;
	// 是否取消
	private boolean isCancelled;
	// 任务创建时间
	private long createTime = System.currentTimeMillis();
}