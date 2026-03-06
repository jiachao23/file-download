package com.example.report.core.model;

import lombok.Data;
import java.util.List;

@Data
public class ReportTemplate {
	private String id;
	private String name;
	private String type; // WORD, EXCEL, PPT
	private TemplateContent content;
}





