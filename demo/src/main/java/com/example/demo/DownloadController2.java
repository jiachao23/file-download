//package com.example.demo;
//
//import java.util.List;
//
//import javax.servlet.http.HttpServletResponse;
//
//import lombok.extern.slf4j.Slf4j;
//
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.http.ResponseEntity;
//import org.springframework.web.bind.annotation.GetMapping;
//import org.springframework.web.bind.annotation.PathVariable;
//import org.springframework.web.bind.annotation.PostMapping;
//import org.springframework.web.bind.annotation.RequestBody;
//import org.springframework.web.bind.annotation.RequestMapping;
//import org.springframework.web.bind.annotation.RequestParam;
//import org.springframework.web.bind.annotation.RestController;
//
///**
// * 下载控制器（完整优化版）
// */
//@Slf4j
//@RestController
//@RequestMapping("/api/download")
//public class DownloadController2 {
//
//	@Autowired
//	private SingleFileDownloader singleFileDownloader;
//
//	@Autowired
//	private MultiFileDownloader multiFileDownloader;
//
//	@Autowired
//	private DownloadTaskManager taskManager;
//
//	/**
//	 * 单文件下载（服务器本地）
//	 */
//	@PostMapping("/single/local")
//	public void downloadSingleLocalFile(
//			@RequestParam String filePath,
//			@RequestParam(defaultValue = "0") long rangeStart,
//			HttpServletResponse response) {
//		try {
//			singleFileDownloader.downloadLocalFile(filePath, rangeStart, response);
//		} catch (Exception e) {
//			response.setStatus(HttpServletResponse.SC_NOT_FOUND);
//			try {
//				response.getWriter().write("下载失败：" + e.getMessage());
//			} catch (Exception ex) {
//				log.error("写入错误响应失败", ex);
//			}
//			log.error("单文件下载失败：{}", filePath, e);
//		}
//	}
//
//	/**
//	 * 提交多文件下载任务
//	 */
//	@PostMapping("/multi/local/submit")
//	public ResponseEntity<String> submitMultiLocalFileTask(
//			@RequestBody List<String> filePathList,
//			@RequestParam String userId) {
//		try {
//			String taskId = multiFileDownloader.submitMultiFileTask(filePathList, userId);
//			return ResponseEntity.ok(taskId);
//		} catch (Exception e) {
//			log.error("提交多文件下载任务失败", e);
//			return ResponseEntity.badRequest().body("提交失败：" + e.getMessage());
//		}
//	}
//
//	/**
//	 * 取消下载任务
//	 */
//	@PostMapping("/multi/cancel/{taskId}")
//	public ResponseEntity<String> cancelMultiFileTask(@PathVariable String taskId) {
//		try {
//			String result = multiFileDownloader.cancelTask(taskId);
//			return ResponseEntity.ok(result);
//		} catch (Exception e) {
//			log.error("取消下载任务失败：{}", taskId, e);
//			return ResponseEntity.badRequest().body("取消失败：" + e.getMessage());
//		}
//	}
//
//	/**
//	 * SSE监听任务进度
//	 */
//	@GetMapping("/task/progress/{taskId}")
//	public void streamTaskProgress(@PathVariable String taskId, HttpServletResponse response) {
//		multiFileDownloader.streamTaskProgress(taskId, response);
//	}
//
//	/**
//	 * 查询单个任务状态
//	 */
//	@GetMapping("/task/status/{taskId}")
//	public ResponseEntity<UserDownloadTask> getTaskStatus(@PathVariable String taskId) {
//		try {
//			UserDownloadTask task = taskManager.getTaskById(taskId);
//			if (task == null) {
//				return ResponseEntity.notFound().build();
//			}
//			return ResponseEntity.ok(task);
//		} catch (Exception e) {
//			log.error("查询任务状态失败：{}", taskId, e);
//			return ResponseEntity.internalServerError().build();
//		}
//	}
//
//	/**
//	 * 查询所有任务
//	 */
//	@GetMapping("/task/all")
//	public ResponseEntity<List<UserDownloadTask>> getAllTasks() {
//		try {
//			return ResponseEntity.ok(taskManager.getAllTasks());
//		} catch (Exception e) {
//			log.error("查询所有任务失败", e);
//			return ResponseEntity.internalServerError().build();
//		}
//	}
//}