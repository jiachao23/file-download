package com.example.demo.other;

import java.io.IOException;
import java.io.OutputStream;
import java.io.File;

/**
 * 下载工具类：限速输出流、文件大小计算
 */
public class DownloadUtils {
	public static final int BUFFER_SIZE = 4096;

	/**
	 * 获取文件总大小
	 */
	public static long getFileTotalSize(String filePath) throws IOException {
		File file = new File(filePath);
		if (!file.exists()) {
			throw new IOException("文件不存在：" + filePath);
		}
		return file.length();
	}

	/**
	 * 限速输出流（控制每秒写入字节数）
	 */
	public static class RateLimitOutputStream extends OutputStream {
		private final OutputStream target;
		private final long bytesPerSecond;
		private long bytesWritten = 0;
		private long startTime = System.currentTimeMillis();

		public RateLimitOutputStream(OutputStream target, long bytesPerSecond) {
			this.target = target;
			this.bytesPerSecond = bytesPerSecond <= 0 ? 1024 * 1024 : bytesPerSecond; // 默认1MB/s
		}

		@Override
		public void write(int b) throws IOException {
			checkRateLimit(1);
			target.write(b);
			bytesWritten++;
		}

		@Override
		public void write(byte[] b, int off, int len) throws IOException {
			checkRateLimit(len);
			target.write(b, off, len);
			bytesWritten += len;
		}

		private void checkRateLimit(int len) throws IOException {
			if (bytesPerSecond <= 0) return; // 不限速

			long elapsedTime = System.currentTimeMillis() - startTime;
			if (elapsedTime == 0) elapsedTime = 1; // 避免除0

			long currentSpeed = (bytesWritten * 1000) / elapsedTime;
			if (currentSpeed > bytesPerSecond) {
				long expectedTime = (bytesWritten + len) * 1000 / bytesPerSecond;
				long sleepTime = expectedTime - elapsedTime;
				if (sleepTime > 0) {
					try {
						Thread.sleep(sleepTime);
					} catch (InterruptedException e) {
						Thread.currentThread().interrupt();
						throw new IOException("限速线程中断", e);
					}
				}
			}
		}

		@Override
		public void flush() throws IOException {
			target.flush();
		}

		@Override
		public void close() throws IOException {
			target.close();
		}
	}
}