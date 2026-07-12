package com.example.report.core.handler;

import com.example.report.core.handler.AbstractHandler;
import com.example.report.core.handler.RenderContext;
import org.springframework.stereotype.Component;
import java.util.HashMap;
import java.util.Map;

// 1. 校验处理器
@Component
public class ValidationHandler extends AbstractHandler {
	@Override
	protected void doHandle(RenderContext context) {
		if (context.getRawParams() == null || !context.getRawParams().containsKey("date")) {
			throw new IllegalArgumentException("Missing required parameter: date");
		}
		// 实际项目中这里会做更复杂的校验
	}
}

