package com.example.demo.defect;

import java.util.List;

public class DownloadService {

	public void download(DownloadVo downloadVo) {
		String entityName = downloadVo.getEntityName();
		List<String> downloadTypes = downloadVo.getDownloadTypes();
	}
}
