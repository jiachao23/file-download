package com.example.report.core.strategy;

import com.example.report.core.model.ReportTemplate;
import com.example.report.core.handler.RenderContext;

public interface ReportRenderer {
	boolean supports(String type);
	byte[] render(ReportTemplate template, RenderContext context);
}