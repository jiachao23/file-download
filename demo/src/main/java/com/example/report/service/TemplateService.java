package com.example.report.service;

import com.example.report.core.model.*;
import org.springframework.stereotype.Service;
import java.util.Arrays;
import java.util.Date;

@Service
public class TemplateService {
	public ReportTemplate getTemplate(String id) {
		// 模拟数据库查询
		ReportTemplate template = new ReportTemplate();
		template.setId(id);
		template.setName("销售月报");
		template.setType("WORD");

		TemplateContent content = new TemplateContent();
		Meta meta = new Meta();
		meta.setAuthor("Admin");
		meta.setCreateTime(new Date());
		meta.setVersion(1);
		content.setMeta(meta);

		Component c1 = new Component();
		c1.setType("TEXT");
		c1.setPlaceholder("{{title}}");

		Component c2 = new Component();
		c2.setType("TEXT");
		c2.setPlaceholder("{{totalSales}}");

		content.setComponents(Arrays.asList(c1, c2));
		template.setContent(content);

		return template;
	}
}