package com.example.demo.defect;

import java.util.List;

import lombok.Data;

@Data
public class DownloadVo {
	private String entityName;
	private List<String> downloadTypes;

}
