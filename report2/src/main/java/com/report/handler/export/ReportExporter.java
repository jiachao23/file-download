// com/report/handler/export/ReportExporter.java
package com.report.handler.export;

import java.util.Map;

public interface ReportExporter {
	String export(Map<String, Object> data, String templatePath);
	String getType();
}