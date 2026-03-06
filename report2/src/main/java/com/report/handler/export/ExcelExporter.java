// com/report/handler/export/ExcelExporter.java
package com.report.handler.export;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;
import java.io.*;
import java.util.Map;

@Component
public class ExcelExporter implements ReportExporter {
	@Override
	public String export(Map<String, Object> data, String templatePath) {
		try (FileInputStream fis = new FileInputStream(templatePath);
			 Workbook workbook = new XSSFWorkbook(fis)) {

			Sheet sheet = workbook.getSheetAt(0);
			for (Row row : sheet) {
				for (Cell cell : row) {
					if (cell.getCellType() == CellType.STRING) {
						String text = cell.getStringCellValue();
						for (Map.Entry<String, Object> entry : data.entrySet()) {
							String placeholder = "${" + entry.getKey() + "}";
							if (text.contains(placeholder)) {
								text = text.replace(placeholder, entry.getValue().toString());
								cell.setCellValue(text);
							}
						}
					}
				}
			}

			String outputPath = "output/report_" + System.currentTimeMillis() + ".xlsx";
			try (FileOutputStream fos = new FileOutputStream(outputPath)) {
				workbook.write(fos);
			}
			return outputPath;
		} catch (Exception e) {
			throw new RuntimeException("Excel 导出失败", e);
		}
	}

	@Override
	public String getType() {
		return "excel";
	}
}