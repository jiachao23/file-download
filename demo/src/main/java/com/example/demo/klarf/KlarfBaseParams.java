package com.example.demo.klarf;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Klarf文件基准参数（增强版）
 * 支持晶圆物理中心、单位配置
 */
public class KlarfBaseParams {
	/** 样品参考中心X（全局坐标系） */
	private double sampleCenterX;
	/** 样品参考中心Y（全局坐标系） */
	private double sampleCenterY;
	/** 芯片X方向间距 */
	private double diePitchX;
	/** 芯片Y方向间距 */
	private double diePitchY;
	/** 芯片内部局部原点X */
	private double dieOriginX;
	/** 芯片内部局部原点Y */
	private double dieOriginY;
	/** 旋转角度（度），逆时针为正，顺时针为负 */
	private double rotateAngleDeg;
	/** 晶圆物理中心X（可选，旋转中心为晶圆物理中心时必填） */
	private double waferPhysicalCenterX;
	/** 晶圆物理中心Y（可选，旋转中心为晶圆物理中心时必填） */
	private double waferPhysicalCenterY;
	/** 旋转中心类型（默认SampleCenter） */
	private RotationCenterType rotationCenterType = RotationCenterType.SAMPLE_CENTER;
	/** 坐标单位（默认μm） */
	private CoordinateUnit unit = CoordinateUnit.MICROMETER;

	// 全参构造器（简化常用场景，提供重载构造器）
	public KlarfBaseParams(double sampleCenterX, double sampleCenterY, double diePitchX, double diePitchY,
			double dieOriginX, double dieOriginY, double rotateAngleDeg) {
		this(sampleCenterX, sampleCenterY, diePitchX, diePitchY, dieOriginX, dieOriginY, rotateAngleDeg,
				0.0, 0.0, RotationCenterType.SAMPLE_CENTER, CoordinateUnit.MICROMETER);
	}

	// 全参构造器（含晶圆物理中心、旋转中心、单位）
	public KlarfBaseParams(double sampleCenterX, double sampleCenterY, double diePitchX, double diePitchY,
			double dieOriginX, double dieOriginY, double rotateAngleDeg,
			double waferPhysicalCenterX, double waferPhysicalCenterY,
			RotationCenterType rotationCenterType, CoordinateUnit unit) {
		this.sampleCenterX = sampleCenterX;
		this.sampleCenterY = sampleCenterY;
		this.diePitchX = diePitchX;
		this.diePitchY = diePitchY;
		this.dieOriginX = dieOriginX;
		this.dieOriginY = dieOriginY;
		this.rotateAngleDeg = rotateAngleDeg;
		this.waferPhysicalCenterX = waferPhysicalCenterX;
		this.waferPhysicalCenterY = waferPhysicalCenterY;
		this.rotationCenterType = rotationCenterType;
		this.unit = unit;
	}

	// Getter & Setter（补充单位转换后的数值，统一返回μm）
	public double getSampleCenterX() {
		return unit.toMicrometer(sampleCenterX);
	}

	public double getSampleCenterY() {
		return unit.toMicrometer(sampleCenterY);
	}

	public double getDiePitchX() {
		return unit.toMicrometer(diePitchX);
	}

	public double getDiePitchY() {
		return unit.toMicrometer(diePitchY);
	}

	public double getDieOriginX() {
		return unit.toMicrometer(dieOriginX);
	}

	public double getDieOriginY() {
		return unit.toMicrometer(dieOriginY);
	}

	/**
	 * 旋转角度归一化（0~360°），避免 450°、-90° 等非常规角度导致的计算冗余；
	 * @return
	 */
	public double getRotateAngleDeg() {
		// 归一化角度到0~360°
		double normalized = rotateAngleDeg % 360;
		return normalized < 0 ? normalized + 360 : normalized;
	}

	public double getWaferPhysicalCenterX() {
		return unit.toMicrometer(waferPhysicalCenterX);
	}

	public double getWaferPhysicalCenterY() {
		return unit.toMicrometer(waferPhysicalCenterY);
	}

	public RotationCenterType getRotationCenterType() {
		return rotationCenterType;
	}

	// 辅助方法：获取实际旋转中心坐标
	public double getActualRotationCenterX() {
		return rotationCenterType == RotationCenterType.SAMPLE_CENTER ? getSampleCenterX() : getWaferPhysicalCenterX();
	}

	public double getActualRotationCenterY() {
		return rotationCenterType == RotationCenterType.SAMPLE_CENTER ? getSampleCenterY() : getWaferPhysicalCenterY();
	}

	// 精度处理辅助方法
	public double round(double value, int scale) {
		return new BigDecimal(value).setScale(scale, RoundingMode.HALF_UP).doubleValue();
	}
}
