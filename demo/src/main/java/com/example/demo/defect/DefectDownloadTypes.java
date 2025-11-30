package com.example.demo.defect;

import java.util.List;
import java.util.Map;

public enum DefectDownloadTypes implements DownloadTypes {
	DEFECT_FILE(

	);

	@Override
	public String entityName() {
		return DefectFile.class.getSimpleName();
	}

	@Override
	public List<String> getDownloadTypes() {
		return List.of("list","file","image");
	}
}
