package com.example.demo;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.net.URLEncoder;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.WritableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.ByteBuffer;

/**
 * 单文件下载服务（NIO优化版）
 */
@Slf4j
@Service
public class SingleFileDownloader {

	/**
	 * 下载服务器本地文件（支持限速和NIO优化）
	 */
	public void downloadLocalFile(String filePath, long rangeStart, long rateLimit, HttpServletResponse response) throws Exception {
		long startTime = System.currentTimeMillis();
		File file = new File(filePath);

		if (!file.exists()) {
			log.error("文件不存在：{}", filePath);
			throw new FileNotFoundException("文件不存在：" + filePath);
		}

		// 设置响应头
		response.setContentType("application/octet-stream");
		response.setHeader("Content-Disposition", "attachment; filename=\"" +
				URLEncoder.encode(file.getName(), StandardCharsets.UTF_8) + "\"");
		response.setHeader("Content-Length", String.valueOf(file.length()));
		response.setHeader("Accept-Ranges", "bytes");

		// 使用NIO优化IO（适配大文件）
		try (FileChannel inChannel = new FileInputStream(file).getChannel();
			 WritableByteChannel outChannel = Channels.newChannel(response.getOutputStream())) {

			// 缓冲区大小：64KB（适配磁盘块大小，避免频繁IO）
			ByteBuffer buffer = ByteBuffer.allocateDirect(64 * 1024);
			long bytesWritten = 0;

			// 定位到起始位置（断点续传）
			if (rangeStart > 0 && rangeStart < file.length()) {
				inChannel.position(rangeStart);
				bytesWritten = rangeStart;
			}

			while (inChannel.read(buffer) != -1) {
				buffer.flip();

				// 限速控制（优化算法：避免频繁sleep）
				if (rateLimit > 0) {
					long elapsedTime = System.currentTimeMillis() - startTime;
					long expectedTime = (bytesWritten * 1000) / rateLimit;
					if (elapsedTime < expectedTime) {
						Thread.sleep(Math.min(10, expectedTime - elapsedTime)); // 最多sleep10ms
					}
				}

				bytesWritten += outChannel.write(buffer);
				buffer.clear();
			}

			log.info("文件下载完成：{}，大小：{}KB，耗时：{}ms",
					filePath, file.length()/1024, System.currentTimeMillis()-startTime);
		} catch (Exception e) {
			log.error("文件下载失败：{}", filePath, e);
			throw e;
		}
	}
}