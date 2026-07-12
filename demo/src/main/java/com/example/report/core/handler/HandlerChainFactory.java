package com.example.report.core.handler;

import com.example.report.core.handler.AggregationHandler;
import com.example.report.core.handler.ValidationHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class HandlerChainFactory {
	@Autowired
	private ValidationHandler validationHandler;
	@Autowired
	private AggregationHandler aggregationHandler;

	public DataHandler buildChain() {
		validationHandler.setNext(aggregationHandler);
		// 可以继续添加更多 handler: aggregationHandler.setNext(new ImageHandler());
		return validationHandler;
	}
}
