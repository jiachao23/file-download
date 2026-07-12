package com.example.demo.klarf;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Klarf缺陷实体（增强空值防护）
 */
public class KlarfDefect {
	// 原始字段
	private Integer originalXIndex;
	private Integer originalYIndex;
	private Double originalXRel;
	private Double originalYRel;
	private String originalDieID;

	// 旋转后字段
	private Integer rotatedXIndex;
	private Double rotatedXRel;
	private Integer rotatedYIndex;
	private Double rotatedYRel;
	private String rotatedDieID;

	// 构造器（增加空值校验）
	public KlarfDefect(Integer originalXIndex, Integer originalYIndex, Double originalXRel, Double originalYRel) {
		if (originalXIndex == null || originalYIndex == null) {
			throw new IllegalArgumentException("原始芯片索引（XIndex/YIndex）不能为空！");
		}
		if (originalXRel == null || originalYRel == null) {
			throw new IllegalArgumentException("原始相对坐标（XRel/YRel）不能为空！");
		}
		this.originalXIndex = originalXIndex;
		this.originalYIndex = originalYIndex;
		this.originalXRel = originalXRel;
		this.originalYRel = originalYRel;
		this.originalDieID = generateDieID(originalXIndex, originalYIndex);
	}

	// 生成DieID（抽离为方法，便于自定义格式）
	private String generateDieID(Integer xIndex, Integer yIndex) {
		return xIndex + "-" + yIndex;
	}

	// Getter & Setter（补充）
	public Integer getOriginalXIndex() { return originalXIndex; }
	public Integer getOriginalYIndex() { return originalYIndex; }
	public Double getOriginalXRel() { return originalXRel; }
	public Double getOriginalYRel() { return originalYRel; }
	public String getOriginalDieID() { return originalDieID; }
	public Integer getRotatedXIndex() { return rotatedXIndex; }
	public void setRotatedXIndex(Integer rotatedXIndex) { this.rotatedXIndex = rotatedXIndex; }
	public Double getRotatedXRel() { return rotatedXRel; }
	public void setRotatedXRel(Double rotatedXRel) { this.rotatedXRel = rotatedXRel; }
	public Integer getRotatedYIndex() { return rotatedYIndex; }
	public void setRotatedYIndex(Integer rotatedYIndex) { this.rotatedYIndex = rotatedYIndex; }
	public Double getRotatedYRel() { return rotatedYRel; }
	public void setRotatedYRel(Double rotatedYRel) { this.rotatedYRel = rotatedYRel; }
	public String getRotatedDieID() { return rotatedDieID; }
	public void setRotatedDieID(String rotatedDieID) { this.rotatedDieID = rotatedDieID; }

	// 重写toString（优化输出格式）
	@Override
	public String toString() {
		return "=== Klarf缺陷坐标旋转结果 ===\n" +
				"原始信息：\n" +
				"  XIndex=" + originalXIndex + ", YIndex=" + originalYIndex + "\n" +
				"  XRel=" + round(originalXRel) + "μm, YRel=" + round(originalYRel) + "μm\n" +
				"  DieID=" + originalDieID + "\n" +
				"旋转后信息：\n" +
				"  XIndex=" + rotatedXIndex + ", YIndex=" + rotatedYIndex + "\n" +
				"  XRel=" + round(rotatedXRel) + "μm, YRel=" + round(rotatedYRel) + "μm\n" +
				"  DieID=" + rotatedDieID + "\n";
	}

	// 私有辅助方法：精度处理
	private double round(double value) {
		return new BigDecimal(value).setScale(4, RoundingMode.HALF_UP).doubleValue();
	}
}
