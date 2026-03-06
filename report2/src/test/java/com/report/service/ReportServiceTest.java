// src/test/java/com/report/service/ReportServiceTest.java
package com.report.service;

import com.report.entity.Template;
import com.report.handler.dataprocess.DataProcessChain;
import com.report.handler.export.ExporterFactory;
import com.report.handler.export.ReportExporter;
import com.report.mapper.ReportTaskMapper;
import com.report.service.impl.ReportServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReportServiceTest {

	@Mock
	private TemplateService templateService;

	@Mock
	private DataProcessChain dataProcessChain;

	@Mock
	private ExporterFactory exporterFactory;

	@Mock
	private ReportExporter reportExporter;

	@Mock
	private ReportTaskMapper reportTaskMapper;

	@InjectMocks
	private ReportServiceImpl reportService;

	@Test
	void testGenerateReport_Success() {
		// 1. 准备数据
		Long templateId = 1L;
		Map<String, Object> rawData = new HashMap<>();
		rawData.put("title", "测试报表");
		rawData.put("amount", 10000);

		Template template = new Template();
		template.setId(templateId);
		template.setName("测试模板");
		template.setType("excel");
		template.setFileUrl("test_template.xlsx");

		Map<String, Object> processedData = new HashMap<>(rawData);
		processedData.put("processed", true);

		String expectedOutputPath = "output/report_1234567890.xlsx";

		// 2. Mock 行为
		when(templateService.getById(templateId)).thenReturn(template);
		when(dataProcessChain.execute(rawData)).thenReturn(processedData);
		when(exporterFactory.getExporter("excel")).thenReturn(reportExporter);
		when(reportExporter.export(processedData, "templates/test_template.xlsx"))
				.thenReturn(expectedOutputPath);
		when(reportTaskMapper.insert(any())).thenReturn(1);

		// 3. 执行测试
		String resultPath = reportService.generateReport(templateId, rawData);

		// 4. 验证结果
		assertNotNull(resultPath);
		assertEquals(expectedOutputPath, resultPath);
		verify(templateService, times(1)).getById(templateId);
		verify(dataProcessChain, times(1)).execute(rawData);
		verify(exporterFactory, times(1)).getExporter("excel");
		verify(reportExporter, times(1))
				.export(processedData, "templates/test_template.xlsx");
		verify(reportTaskMapper, times(1)).insert(any());
	}

	@Test
	void testGenerateReport_TemplateNotFound() {
		// 1. 准备数据
		Long templateId = 999L;
		Map<String, Object> rawData = new HashMap<>();

		// 2. Mock 行为
		when(templateService.getById(templateId)).thenReturn(null);

		// 3. 执行测试 & 验证异常
		IllegalArgumentException exception = assertThrows(
				IllegalArgumentException.class,
				() -> reportService.generateReport(templateId, rawData)
		);
		assertEquals("模板不存在", exception.getMessage());
		verify(templateService, times(1)).getById(templateId);
		verifyNoInteractions(dataProcessChain);
		verifyNoInteractions(exporterFactory);
	}
}