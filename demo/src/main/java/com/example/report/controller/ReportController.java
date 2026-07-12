package com.example.report.controller;

import com.example.report.core.handler.HandlerChainFactory;
import com.example.report.core.handler.RenderContext;
import com.example.report.core.model.ReportTemplate;
import com.example.report.core.strategy.RendererFactory;
import com.example.report.dto.GenerateRequest;
import com.example.report.service.TemplateService;
import lombok.Data;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;

@RestController
@RequestMapping("/api/reports")
//@CrossOrigin(origins = "*")
@Validated
public class ReportController {

	@Autowired
	private TemplateService templateService;
	@Autowired
	private HandlerChainFactory chainFactory;
	@Autowired
	private RendererFactory rendererFactory;

	@PostMapping("/generate")
	public ResponseEntity<byte[]> generateReport(@Valid @RequestBody GenerateRequest request) {
		ReportTemplate template = templateService.getTemplate(request.getTemplateId());
		String finalType = (request.getTargetFormat() != null) ? request.getTargetFormat() : template.getType();

		RenderContext context = new RenderContext(request.getTemplateId(), request.getParams());

		if (Boolean.TRUE.equals(request.getAsync())) {
			// 简化处理：实际应返回 TaskID
			return ResponseEntity.accepted().body(null);
		}

		chainFactory.buildChain().handle(context);
		byte[] fileContent = rendererFactory.getRenderer(finalType).render(template, context);

		String fileName = "report_" + System.currentTimeMillis() + "." + getFileExtension(finalType);
		return ResponseEntity.ok()
				.header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"")
				.contentType(MediaType.APPLICATION_OCTET_STREAM)
				.body(fileContent);
	}

	@PostMapping("/preview")
	public ResponseEntity<byte[]> previewReport(@Valid @RequestBody GenerateRequest request) {
		ReportTemplate template = templateService.getTemplate(request.getTemplateId());
		RenderContext context = new RenderContext(request.getTemplateId(), request.getParams());
		chainFactory.buildChain().handle(context);
		byte[] fileContent = rendererFactory.getRenderer(template.getType()).render(template, context);

		return ResponseEntity.ok()
				.contentType(MediaType.APPLICATION_OCTET_STREAM)
				.body(fileContent);
	}

	private String getFileExtension(String type) {
		if (type == null) return "dat";
		switch (type.toUpperCase()) {
			case "WORD": return "docx";
			case "EXCEL": return "xlsx";
			case "PPT": return "pptx";
			default: return "dat";
		}
	}

	@Data
	static class AsyncResponseDto {
		private String taskId;
		private String status;
	}
}