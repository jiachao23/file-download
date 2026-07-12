package com.example.report.core.handler;

import lombok.Data;
import java.util.Map;

@Data
public class RenderContext {
	private String templateId;
	private Map<String, Object> rawParams; // 用户输入的原始参数
	private Map<String, Object> processedData; // 经过清洗聚合后的最终数据
	private byte[] fileContent; // 生成的文件二进制流
	private String errorMessage;

	public RenderContext(String templateId, Map<String, Object> params) {
		this.templateId = templateId;
		this.rawParams = params;
		this.processedData = params; // 初始化为原始数据
	}
}