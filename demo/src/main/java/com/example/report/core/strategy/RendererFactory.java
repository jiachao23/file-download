package com.example.report.core.strategy;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import java.util.List;

@Component
public class RendererFactory {
	@Autowired
	private List<ReportRenderer> renderers;

	public ReportRenderer getRenderer(String type) {
		return renderers.stream()
				.filter(r -> r.supports(type))
				.findFirst()
				.orElseThrow(() -> new IllegalArgumentException("Unsupported report type: " + type));
	}
}