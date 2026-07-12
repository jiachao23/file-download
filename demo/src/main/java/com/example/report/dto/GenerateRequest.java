package com.example.report.dto;

import lombok.Data;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.util.Map;

/**
 * 报表生成请求 DTO
 * 用于接收前端发起的报表生成或预览请求
 */
@Data
public class GenerateRequest {

	/**
	 * 模版 ID
	 * 必填：必须指定使用哪个模版
	 */
	@NotBlank(message = "模版ID不能为空")
	private String templateId;

	/**
	 * 动态参数集合
	 * 用于填充模版中的占位符 (例如: {"title": "2026报告", "date": "2026-03-06"})
	 * 也可以包含触发数据源查询所需的过滤条件
	 */
	@NotNull(message = "请求参数不能为空")
	private Map<String, Object> params;

	/**
	 * 可选：导出格式覆盖
	 * 如果留空，则使用模版默认定义的格式 (WORD/EXCEL/PPT)
	 * 如果传入 (如 "PDF")，则尝试以该格式生成（需后端支持转换）
	 */
	private String targetFormat;

	/**
	 * 可选：异步标记
	 * true: 立即返回 taskId，前端轮询结果 (适合大数据量)
	 * false: 同步等待文件流返回 (适合小数据量)
	 * 默认 false
	 */
	private Boolean async = false;
}