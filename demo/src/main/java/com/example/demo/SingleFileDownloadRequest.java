package com.example.demo;

import lombok.Data;

@Data
public class SingleFileDownloadRequest {
	private String filePath; // 下载链接
	private long rangeStart = 0; // 断点续传起始位置（默认0）
	private long rateLimit = 1024 * 1024; // 限速（默认1MB/s，0表示不限速）
}
