package com.example.demo;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * 性能监控拦截器
 */
@Slf4j
@Component
public class PerformanceInterceptor implements HandlerInterceptor {

	@Override
	public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
		request.setAttribute("startTime", System.currentTimeMillis());
		return true;
	}

	@Override
	public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
			Object handler, Exception ex) {
		long startTime = (long) request.getAttribute("startTime");
		long cost = System.currentTimeMillis() - startTime;
		String path = request.getRequestURI();
		String method = request.getMethod();
		int status = response.getStatus();

		// 记录慢请求（超过500ms）
		if (cost > 500) {
			log.warn("Slow request | {} {} | Status: {} | Cost: {}ms",
					method, path, status, cost);
		} else {
			log.info("Request | {} {} | Status: {} | Cost: {}ms",
					method, path, status, cost);
		}

		// 记录异常请求
		if (ex != null) {
			log.error("Request error | {} {} | Exception: {}",
					method, path, ex.getMessage(), ex);
		}
	}
}