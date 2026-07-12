// src/test/java/com/report/service/TemplateServiceTest.java
package com.report.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.report.entity.Template;
import com.report.mapper.TemplateMapper;
import com.report.service.impl.TemplateServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TemplateServiceTest {

	@Mock
	private TemplateMapper templateMapper;

	@InjectMocks
	private TemplateServiceImpl templateService;

	@Test
	void testListTemplates() {
		// 1. 准备数据
		Template tpl1 = new Template();
		tpl1.setId(1L);
		tpl1.setName("销售报表模板");
		tpl1.setType("excel");
		tpl1.setCreatedTime(LocalDateTime.now());

		Template tpl2 = new Template();
		tpl2.setId(2L);
		tpl2.setName("工作总结模板");
		tpl2.setType("word");
		tpl2.setCreatedTime(LocalDateTime.now().minusDays(1));

		when(templateMapper.selectList(any(LambdaQueryWrapper.class)))
				.thenReturn(Arrays.asList(tpl1, tpl2));

		// 2. 执行测试
		List<Template> result = templateService.list(
				new LambdaQueryWrapper<Template>().orderByDesc(Template::getCreatedTime)
		);

		// 3. 验证结果
		assertNotNull(result);
		assertEquals(2, result.size());
		assertEquals("销售报表模板", result.get(0).getName());
		verify(templateMapper, times(1)).selectList(any(LambdaQueryWrapper.class));
	}

	@Test
	void testSaveTemplate() {
		// 1. 准备数据
		Template template = new Template();
		template.setName("测试模板");
		template.setType("ppt");
		template.setFileUrl("test.pptx");

		when(templateMapper.insert(any(Template.class))).thenReturn(1);

		// 2. 执行测试
		boolean result = templateService.save(template);

		// 3. 验证结果
		assertTrue(result);
		assertNotNull(template.getTemplateCode());
		verify(templateMapper, times(1)).insert(any(Template.class));
	}
}