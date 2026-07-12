package com.example.report.core.model;

import lombok.Data;

@Data
public class ChartConfig {
	private String templateId; // 关联内置图表模版ID
	private String dataSourceRef; // 关联数据源ID
	private String title;
	private Object extraConfig;
}
