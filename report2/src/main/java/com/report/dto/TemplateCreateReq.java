// com/report/dto/TemplateCreateReq.java
package com.report.dto;

import lombok.Data;

@Data
public class TemplateCreateReq {
	private String name;
	private String type;
	private String placeholders;
}