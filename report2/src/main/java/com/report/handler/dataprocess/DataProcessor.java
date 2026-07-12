// com/report/handler/dataprocess/DataProcessor.java
package com.report.handler.dataprocess;

import java.util.Map;

public interface DataProcessor {
	void process(Map<String, Object> data);
	int getOrder();
}