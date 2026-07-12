// com/report/dto/ReportGenerateReq.java
package com.report.dto;

import lombok.Data;
import java.util.Map;

@Data
public class ReportGenerateReq {
	private Long templateId;
	private Map<String, Object> data;
}