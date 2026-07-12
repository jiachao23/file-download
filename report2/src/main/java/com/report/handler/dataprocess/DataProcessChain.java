// com/report/handler/dataprocess/DataProcessChain.java
package com.report.handler.dataprocess;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

@Component
public class DataProcessChain {
	private final List<DataProcessor> processors;

	@Autowired
	public DataProcessChain(List<DataProcessor> processorList) {
		this.processors = processorList.stream()
				.sorted(Comparator.comparingInt(DataProcessor::getOrder))
				.toList();
	}

	public Map<String, Object> execute(Map<String, Object> rawData) {
		for (DataProcessor processor : processors) {
			processor.process(rawData);
		}
		return rawData;
	}
}