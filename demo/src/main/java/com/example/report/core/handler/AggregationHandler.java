package com.example.report.core.handler;

import java.util.HashMap;
import java.util.Map;

import org.springframework.stereotype.Component;

// 2. 数据清洗与聚合处理器 (模拟)
@Component
public class AggregationHandler extends AbstractHandler {
	@Override
	protected void doHandle(RenderContext context) {
		Map<String, Object> cleanData = new HashMap<>(context.getRawParams());

		// 模拟数据聚合逻辑
		cleanData.put("totalSales", 125000.50);
		cleanData.put("growthRate", "15%");
		cleanData.put("title", cleanData.getOrDefault("title", "默认报告标题"));

		context.setProcessedData(cleanData);
	}
}
