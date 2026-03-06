package com.example.demo;

import com.example.report.core.handler.AggregationHandler;
import com.example.report.core.handler.RenderContext;
import com.example.report.core.handler.ValidationHandler;
import org.junit.jupiter.api.Test;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class DataHandlerTest {

	@Test
	public void testHandlerChainSuccess() {
		ValidationHandler validation = new ValidationHandler();
		AggregationHandler aggregation = new AggregationHandler();
		validation.setNext(aggregation);

		Map<String, Object> params = new HashMap<>();
		params.put("date", "2026-03-06");
		params.put("title", "Test Report");

		RenderContext context = new RenderContext("tpl_001", params);

		// 执行链
		validation.handle(context);

		// 验证结果
		assertNotNull(context.getProcessedData());
		assertEquals(125000.50, context.getProcessedData().get("totalSales"));
		assertEquals("Test Report", context.getProcessedData().get("title"));
	}

	@Test
	public void testHandlerChainValidationFail() {
		ValidationHandler validation = new ValidationHandler();
		RenderContext context = new RenderContext("tpl_001", null);

		assertThrows(IllegalArgumentException.class, () -> {
			validation.handle(context);
		});
	}
}