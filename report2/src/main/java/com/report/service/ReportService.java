// com/report/service/ReportService.java
package com.report.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.report.entity.ReportTask;
import java.util.Map;

public interface ReportService extends IService<ReportTask> {
	String generateReport(Long templateId, Map<String, Object> rawData);
}