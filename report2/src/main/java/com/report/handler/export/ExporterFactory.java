// com/report/handler/export/ExporterFactory.java
package com.report.handler.export;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class ExporterFactory {
	private final Map<String, ReportExporter> exporterMap;

	@Autowired
	public ExporterFactory(List<ReportExporter> exporters) {
		exporterMap = exporters.stream()
				.collect(Collectors.toMap(ReportExporter::getType, Function.identity()));
	}

	public ReportExporter getExporter(String type) {
		return exporterMap.get(type);
	}
}