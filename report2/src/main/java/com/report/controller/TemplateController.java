// com/report/controller/TemplateController.java
package com.report.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.report.dto.TemplateCreateReq;
import com.report.entity.Template;
import com.report.service.TemplateService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/template")
public class TemplateController {
	@Autowired
	private TemplateService templateService;

	@PostMapping("/upload")
	public String uploadTemplate(@RequestParam("file") MultipartFile file,
			@RequestParam("name") String name,
			@RequestParam("type") String type) {
		// 简化实现：实际需上传到 MinIO
		String fileUrl = file.getOriginalFilename();

		Template template = new Template();
		template.setTemplateCode(UUID.randomUUID().toString());
		template.setName(name);
		template.setType(type);
		template.setFileUrl(fileUrl);
		templateService.save(template);
		return "上传成功";
	}

	@GetMapping("/list")
	public List<Template> listTemplates() {
		return templateService.list(new LambdaQueryWrapper<Template>()
				.orderByDesc(Template::getCreatedTime));
	}
}