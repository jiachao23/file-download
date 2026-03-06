// com/report/service/impl/ReportServiceImpl.java
package com.report.service.impl;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.report.entity.ReportTask;
import com.report.entity.Template;
import com.report.handler.dataprocess.DataProcessChain;
import com.report.handler.export.ExporterFactory;
import com.report.mapper.ReportTaskMapper;
import com.report.service.ReportService;
import com.report.service.TemplateService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.Map;
import java.util.UUID;

@Service
public class ReportServiceImpl extends ServiceImpl<ReportTaskMapper, ReportTask> implements ReportService {
	@Autowired
	private TemplateService templateService;
	@Autowired
	private DataProcessChain dataProcessChain;
	@Autowired
	private ExporterFactory exporterFactory;

	@Override
	public String generateReport(Long templateId, Map<String, Object> rawData) {
		// 1. 查询模板
		Template template = templateService.getById(templateId);
		if (template == null) {
			throw new IllegalArgumentException("模板不存在");
		}

		// 2. 数据处理（责任链）
		Map<String, Object> processedData = dataProcessChain.execute(rawData);

		// 3. 生成报表（策略模式）
		String templatePath = downloadTemplateFromMinio(template.getFileUrl());
		String resultPath = exporterFactory.getExporter(template.getType())
				.export(processedData, templatePath);

		// 4. 保存任务记录
		ReportTask task = new ReportTask();
		task.setTaskCode(UUID.randomUUID().toString());
		task.setTemplateId(templateId);
		task.setDataSource(JSON.toJSONString(rawData));
		task.setStatus("SUCCESS");
		task.setResultFileUrl(resultPath);
		save(task);

		return resultPath;
	}

	private String downloadTemplateFromMinio(String fileUrl) {
		// 简化实现：实际需从 MinIO 下载模板到本地临时路径
		return "templates/" + fileUrl;
	}
}