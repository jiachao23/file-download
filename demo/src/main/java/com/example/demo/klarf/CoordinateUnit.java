package com.example.demo.klarf;

/**
 * 坐标单位枚举
 */
public enum CoordinateUnit {
	/** 微米（默认） */
	MICROMETER(1.0),
	/** 毫米（转换为微米需×1000） */
	MILLIMETER(1000.0);

	private final double conversionFactor;

	CoordinateUnit(double conversionFactor) {
		this.conversionFactor = conversionFactor;
	}

	/**
	 * 单位转换为微米
	 * @param value 原始值
	 * @return 转换后的值（μm）
	 */
	public double toMicrometer(double value) {
		return value * conversionFactor;
	}
}
