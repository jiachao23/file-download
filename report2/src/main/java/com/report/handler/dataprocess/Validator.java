// com/report/handler/dataprocess/Validator.java
package com.report.handler.dataprocess;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import java.util.Map;

@Component
@Order(1)
public class Validator implements DataProcessor {
	@Override
	public void process(Map<String, Object> data) {
		// 数据校验逻辑
		if (!data.containsKey("title")) {
			throw new IllegalArgumentException("缺少必填字段：title");
		}
	}

	@Override
	public int getOrder() {
		return 1;
	}
}