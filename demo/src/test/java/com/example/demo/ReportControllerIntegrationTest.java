package com.example.demo;

import com.example.report.dto.GenerateRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.HashMap;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
public class ReportControllerIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Test
	public void testGenerateReportSuccess() throws Exception {
		GenerateRequest request = new GenerateRequest();
		request.setTemplateId("tpl_001");
		Map<String, Object> params = new HashMap<>();
		params.put("date", "2026-03-06");
		params.put("title", "Integration Test Report");
		request.setParams(params);

		mockMvc.perform(post("/api/reports/generate")
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(request)))
				.andExpect(status().isOk())
				.andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("attachment")))
				.andExpect(content().contentType(MediaType.APPLICATION_OCTET_STREAM));
	}

	@Test
	public void testGenerateReportValidationFail() throws Exception {
		GenerateRequest request = new GenerateRequest();
		// 故意留空 templateId
		request.setTemplateId("");
		request.setParams(new HashMap<>());

		mockMvc.perform(post("/api/reports/generate")
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(request)))
				.andExpect(status().isBadRequest()); // 400 Bad Request
	}

	@Test
	public void testPreviewReport() throws Exception {
		GenerateRequest request = new GenerateRequest();
		request.setTemplateId("tpl_001");
		Map<String, Object> params = new HashMap<>();
		params.put("date", "2026-03-06");
		request.setParams(params);

		mockMvc.perform(post("/api/reports/preview")
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(request)))
				.andExpect(status().isOk())
				.andExpect(content().contentType(MediaType.APPLICATION_OCTET_STREAM));
	}
}