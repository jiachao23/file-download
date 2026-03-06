package com.example.report.core.strategy;

import com.deepoove.poi.XWPFTemplate;
import com.example.report.core.model.ReportTemplate;
import com.example.report.core.strategy.ReportRenderer;
import com.example.report.core.handler.RenderContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

@Component
public class WordRenderer implements ReportRenderer {

	@Override
	public boolean supports(String type) {
		return "WORD".equalsIgnoreCase(type);
	}

	@Override
	public byte[] render(ReportTemplate template, RenderContext context) {
		try {
			ClassPathResource resource = new ClassPathResource("templates/demo.docx");
			if (!resource.exists()) {
				throw new RuntimeException("Template file 'demo.docx' not found. Please create it in src/main/resources/templates/");
			}

			byte[] templateBytes = resource.getInputStream().readAllBytes();

			// POI-TL 渲染
			XWPFTemplate compile = XWPFTemplate.compile(new ByteArrayInputStream(templateBytes))
					.render(context.getProcessedData());

			ByteArrayOutputStream out = new ByteArrayOutputStream();
			compile.write(out);
			out.flush();
			out.close();
			compile.close();

			return out.toByteArray();
		} catch (Exception e) {
			throw new RuntimeException("Word rendering failed: " + e.getMessage(), e);
		}
	}
}