// com/report/handler/export/WordExporter.java
package com.report.handler.export;

import org.apache.poi.xwpf.usermodel.*;
import org.springframework.stereotype.Component;
import java.io.*;
import java.util.Map;

@Component
public class WordExporter implements ReportExporter {
	@Override
	public String export(Map<String, Object> data, String templatePath) {
		try (FileInputStream fis = new FileInputStream(templatePath);
			 XWPFDocument document = new XWPFDocument(fis)) {

			// 替换段落占位符
			for (XWPFParagraph paragraph : document.getParagraphs()) {
				replaceText(paragraph, data);
			}

			// 替换表格占位符
			for (XWPFTable table : document.getTables()) {
				for (XWPFTableRow row : table.getRows()) {
					for (XWPFTableCell cell : row.getTableCells()) {
						for (XWPFParagraph paragraph : cell.getParagraphs()) {
							replaceText(paragraph, data);
						}
					}
				}
			}

			// 保存结果
			String outputPath = "output/report_" + System.currentTimeMillis() + ".docx";
			try (FileOutputStream fos = new FileOutputStream(outputPath)) {
				document.write(fos);
			}
			return outputPath;
		} catch (Exception e) {
			throw new RuntimeException("Word 导出失败", e);
		}
	}

	private void replaceText(XWPFParagraph paragraph, Map<String, Object> data) {
		String text = paragraph.getText();
		for (Map.Entry<String, Object> entry : data.entrySet()) {
			String placeholder = "${" + entry.getKey() + "}";
			if (text.contains(placeholder)) {
				// 替换文本
				for (XWPFRun run : paragraph.getRuns()) {
					String runText = run.getText(0);
					if (runText != null && runText.contains(placeholder)) {
						runText = runText.replace(placeholder, entry.getValue().toString());
						run.setText(runText, 0);
					}
				}
			}
		}
	}

	@Override
	public String getType() {
		return "word";
	}
}