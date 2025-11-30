package com.example.demo.other;

import com.example.demo.DownloadTaskManager;
import com.example.demo.UserDownloadTask;
import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import javax.servlet.http.HttpServletResponse;
import java.io.*;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Slf4j
@Component
public class ZipStreamingUtil {

	@Autowired
	private DownloadTaskManager taskManager;

	// 流式打包多文件并输出（支持断点续传）
	public void streamZipFiles(List<File> fileList, HttpServletResponse response, String taskId, long rangeStart) throws IOException {
		long totalBytes = calculateTotalZipSize(fileList);
		taskManager.getTaskById(taskId).setTotalBytes(totalBytes);
		taskManager.updateTaskStatus(taskId, UserDownloadTask.TaskStatus.DOWNLOADING);

		byte[] buffer = new byte[8192]; // 8KB缓冲区
		long currentOffset = 0;

		try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
			 ZipOutputStream zos = new ZipOutputStream(baos);
			 OutputStream out = response.getOutputStream()) {

			for (File file : fileList) {
				if (!file.exists()) continue;
				zos.putNextEntry(new ZipEntry(file.getName()));

				try (FileInputStream fis = new FileInputStream(file)) {
					int len;
					while ((len = fis.read(buffer)) != -1) {
						// 处理断点续传：跳过已下载字节
						if (currentOffset + len <= rangeStart) {
							currentOffset += len;
							continue;
						} else if (currentOffset < rangeStart) {
							int skip = (int) (rangeStart - currentOffset);
							zos.write(buffer, skip, len - skip);
							out.write(buffer, skip, len - skip);
							taskManager.addDownloadedBytes(taskId, len - skip);
							currentOffset += len;
						} else {
							zos.write(buffer, 0, len);
							out.write(buffer, 0, len);
							taskManager.addDownloadedBytes(taskId, len);
							currentOffset += len;
						}
						out.flush(); // 实时输出
					}
				}
				zos.closeEntry();
			}

			// 输出剩余Zip流
			zos.finish();
			byte[] remaining = baos.toByteArray();
			if (currentOffset < rangeStart + remaining.length) {
				int skip = (int) (rangeStart - currentOffset);
				out.write(remaining, skip, remaining.length - skip);
				taskManager.addDownloadedBytes(taskId, remaining.length - skip);
			}

			taskManager.updateTaskStatus(taskId, UserDownloadTask.TaskStatus.COMPLETED);
		} catch (Exception e) {
			taskManager.updateTaskStatus(taskId, UserDownloadTask.TaskStatus.FAILED);
			throw e;
		}
	}

	// 预计算Zip包总大小（近似值）
	private long calculateTotalZipSize(List<File> fileList) {
		long total = 0;
		for (File file : fileList) {
			total += file.exists() ? file.length() : 0;
		}
		total += fileList.size() * 1024; // 加Zip头开销
		return total;
	}
}