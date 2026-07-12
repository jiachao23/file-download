package com.example.report.core.model;

import java.util.List;

import lombok.Data;

@Data
public class TemplateContent {
	private Meta meta;
	private List<Component> components;
}
