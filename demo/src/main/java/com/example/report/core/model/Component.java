package com.example.report.core.model;

import java.util.List;

import lombok.Data;

@Data
public class Component {
	private String id;
	private String type; // TEXT, IMAGE, TABLE, CHART
	private String placeholder; // ${title}, ${chart.sales}
	private Object styleConfig;
	private ChartConfig chartConfig; // 仅图表类型需要
	private List<Component> children; // 嵌套支持
}
