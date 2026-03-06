package com.example.demo.klarf;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Klarf缺陷坐标旋转工具类（优化版）
 * 核心优化：支持多旋转中心、增强校验、可配置、不可变设计
 */
public class KlarfCoordinateRotator {
	// 可配置常量（便于统一修改）
	private static final int COORD_PRECISION = 4; // 坐标精度（μm，保留4位小数）
	private static final String DIE_ID_SEPARATOR = "-"; // DieID分隔符

	/**
	 * 旋转单个缺陷坐标（核心方法）
	 * @param defect 待旋转的缺陷（非空）
	 * @param baseParams Klarf基准参数（非空）
	 * @return 旋转后的缺陷（新对象，不修改原对象）
	 * @throws IllegalArgumentException 参数非法时抛出
	 */
	public static KlarfDefect rotateDefect(KlarfDefect defect, KlarfBaseParams baseParams) {
		// 1. 严格参数校验
		validateParams(defect, baseParams);

		// 2. 提取核心参数（统一转换为μm）
		double sampleCenterX = baseParams.getSampleCenterX();
		double sampleCenterY = baseParams.getSampleCenterY();
		double diePitchX = baseParams.getDiePitchX();
		double diePitchY = baseParams.getDiePitchY();
		double dieOriginX = baseParams.getDieOriginX();
		double dieOriginY = baseParams.getDieOriginY();
		double rotateAngleDeg = baseParams.getRotateAngleDeg();
		// 获取实际旋转中心（SampleCenter/晶圆物理中心）
		double rotCenterX = baseParams.getActualRotationCenterX();
		double rotCenterY = baseParams.getActualRotationCenterY();

		// 3. 计算原始绝对坐标（含DieOrigin修正）
		double[] originalAbsCoord = calculateOriginalAbsoluteCoord(defect, diePitchX, diePitchY, dieOriginX, dieOriginY, sampleCenterX, sampleCenterY);

		// 4. 执行旋转变换
		double[] rotatedAbsCoord = rotateAbsoluteCoord(originalAbsCoord[0], originalAbsCoord[1], rotCenterX, rotCenterY, rotateAngleDeg);

		// 5. 反向计算旋转后的Klarf格式坐标（XIndex/XRel/DieID）
		fillRotatedKlarfCoord(defect, rotatedAbsCoord[0], rotatedAbsCoord[1], diePitchX, diePitchY, dieOriginX, dieOriginY, sampleCenterX, sampleCenterY);

		return defect;
	}

	/**
	 * 批量旋转缺陷坐标（不可变设计：返回新列表）
	 * @param defects 待旋转的缺陷列表（非空）
	 * @param baseParams Klarf基准参数（非空）
	 * @return 旋转后的新缺陷列表
	 */
	public static List<KlarfDefect> rotateDefects(List<KlarfDefect> defects, KlarfBaseParams baseParams) {
		validateParams(defects, baseParams);
		// 流处理：生成新列表，避免修改原列表
		return defects.stream()
				.map(defect -> rotateDefect(new KlarfDefect(
						defect.getOriginalXIndex(),
						defect.getOriginalYIndex(),
						defect.getOriginalXRel(),
						defect.getOriginalYRel()
				), baseParams))
				.collect(Collectors.toList());
	}

	// ------------------------------ 私有核心方法：抽离逻辑，提升可读性 ------------------------------
	/**
	 * 参数校验（增强版）
	 */
	private static void validateParams(KlarfDefect defect, KlarfBaseParams baseParams) {
		if (defect == null) {
			throw new IllegalArgumentException("缺陷对象不能为空！");
		}
		validateParams(baseParams);
	}

	private static void validateParams(List<KlarfDefect> defects, KlarfBaseParams baseParams) {
		if (defects == null || defects.isEmpty()) {
			throw new IllegalArgumentException("缺陷列表不能为空且不能为空列表！");
		}
		validateParams(baseParams);
	}

	private static void validateParams(KlarfBaseParams baseParams) {
		if (baseParams == null) {
			throw new IllegalArgumentException("Klarf基准参数不能为空！");
		}
		if (baseParams.getDiePitchX() <= 0) {
			throw new IllegalArgumentException("芯片X方向间距（DiePitchX）必须大于0！当前值：" + baseParams.getDiePitchX());
		}
		if (baseParams.getDiePitchY() <= 0) {
			throw new IllegalArgumentException("芯片Y方向间距（DiePitchY）必须大于0！当前值：" + baseParams.getDiePitchY());
		}
		// 旋转中心为晶圆物理中心时，校验物理中心坐标
		if (baseParams.getRotationCenterType() == RotationCenterType.WAFER_PHYSICAL_CENTER) {
			if (baseParams.getWaferPhysicalCenterX() == 0 && baseParams.getWaferPhysicalCenterY() == 0) {
				throw new IllegalArgumentException("旋转中心为晶圆物理中心时，必须配置有效的晶圆物理中心坐标！");
			}
		}
	}

	/**
	 * 计算原始绝对坐标（含DieOrigin修正）
	 */
	private static double[] calculateOriginalAbsoluteCoord(KlarfDefect defect, double diePitchX, double diePitchY,
			double dieOriginX, double dieOriginY,
			double sampleCenterX, double sampleCenterY) {
		// 芯片内局部坐标 = DieOrigin + XRel/YRel
		double dieLocalX = dieOriginX + defect.getOriginalXRel();
		double dieLocalY = dieOriginY + defect.getOriginalYRel();
		// 全局绝对坐标 = XIndex*DiePitch + 局部坐标 - SampleCenter
		double originalAbsX = defect.getOriginalXIndex() * diePitchX + dieLocalX - sampleCenterX;
		double originalAbsY = defect.getOriginalYIndex() * diePitchY + dieLocalY - sampleCenterY;
		return new double[]{originalAbsX, originalAbsY};
	}

	/**
	 * 对绝对坐标执行旋转变换
	 * @param absX 原始绝对X坐标
	 * @param absY 原始绝对Y坐标
	 * @param rotCenterX 旋转中心X
	 * @param rotCenterY 旋转中心Y
	 * @param rotateAngleDeg 旋转角度（已归一化）
	 * @return 旋转后的绝对坐标 [x, y]
	 */
	private static double[] rotateAbsoluteCoord(double absX, double absY, double rotCenterX, double rotCenterY, double rotateAngleDeg) {
		// 角度转弧度
		double rotateAngleRad = Math.toRadians(rotateAngleDeg);
		double cosTheta = Math.cos(rotateAngleRad);
		double sinTheta = Math.sin(rotateAngleRad);

		// 平移至旋转中心
		double tx = absX - rotCenterX;
		double ty = absY - rotCenterY;

		// 执行旋转
		double rx = tx * cosTheta - ty * sinTheta;
		double ry = tx * sinTheta + ty * cosTheta;

		// 平移回原位置，得到旋转后的绝对坐标
		double rotatedAbsX = rx + rotCenterX;
		double rotatedAbsY = ry + rotCenterY;

		return new double[]{rotatedAbsX, rotatedAbsY};
	}

	/**
	 * 反向计算并填充旋转后的Klarf格式坐标（XIndex/XRel/DieID）
	 */
	private static void fillRotatedKlarfCoord(KlarfDefect defect, double rotatedAbsX, double rotatedAbsY,
			double diePitchX, double diePitchY,
			double dieOriginX, double dieOriginY,
			double sampleCenterX, double sampleCenterY) {
		// 计算旋转后的芯片索引（四舍五入）
		int rotatedXIndex = (int) Math.round((rotatedAbsX + sampleCenterX) / diePitchX);
		int rotatedYIndex = (int) Math.round((rotatedAbsY + sampleCenterY) / diePitchY);

		// 计算旋转后的相对坐标（含DieOrigin修正，保留精度）
		double rotatedDieLocalX = (rotatedAbsX + sampleCenterX) - rotatedXIndex * diePitchX;
		double rotatedXRel = roundCoordinate(rotatedDieLocalX - dieOriginX);
		double rotatedDieLocalY = (rotatedAbsY + sampleCenterY) - rotatedYIndex * diePitchY;
		double rotatedYRel = roundCoordinate(rotatedDieLocalY - dieOriginY);

		// 生成旋转后的DieID
		String rotatedDieID = rotatedXIndex + DIE_ID_SEPARATOR + rotatedYIndex;

		// 填充字段
		defect.setRotatedXIndex(rotatedXIndex);
		defect.setRotatedYIndex(rotatedYIndex);
		defect.setRotatedXRel(rotatedXRel);
		defect.setRotatedYRel(rotatedYRel);
		defect.setRotatedDieID(rotatedDieID);
	}

	/**
	 * 坐标精度处理（统一入口）
	 */
	private static double roundCoordinate(double coordinate) {
		return new BigDecimal(coordinate)
				.setScale(COORD_PRECISION, RoundingMode.HALF_UP)
				.doubleValue();
	}

	// ------------------------------ 测试示例（覆盖多场景） ------------------------------
	public static void main(String[] args) {
		// 场景1：默认旋转中心（SampleCenter），DieOrigin非0，单位μm
		System.out.println("=== 场景1：SampleCenter为旋转中心 ===");
		KlarfBaseParams params1 = new KlarfBaseParams(
				150000.0,    // SampleCenterX
				149500.0,    // SampleCenterY（偏下500μm）
				1000.0,      // DiePitchX
				1000.0,      // DiePitchY
				200.0,       // DieOriginX
				100.0,       // DieOriginY
				90.0         // 旋转90°（逆时针）
		);
		KlarfDefect defect1 = new KlarfDefect(5, 3, 300.0, 200.0);
		KlarfDefect rotatedDefect1 = KlarfCoordinateRotator.rotateDefect(defect1, params1);
		System.out.println(rotatedDefect1);

		// 场景2：晶圆物理中心为旋转中心，单位mm（自动转换为μm）
		System.out.println("=== 场景2：晶圆物理中心为旋转中心 ===");
		KlarfBaseParams params2 = new KlarfBaseParams(
				150.0,       // SampleCenterX（150mm = 150000μm）
				149.5,       // SampleCenterY（149.5mm = 149500μm）
				1.0,         // DiePitchX（1mm = 1000μm）
				1.0,         // DiePitchY
				0.2,         // DieOriginX（0.2mm = 200μm）
				0.1,         // DieOriginY（0.1mm = 100μm）
				90.0,        // 旋转角度
				150.0,       // 晶圆物理中心X（150mm）
				150.0,       // 晶圆物理中心Y（150mm）
				RotationCenterType.WAFER_PHYSICAL_CENTER,
				CoordinateUnit.MILLIMETER
		);
		KlarfDefect defect2 = new KlarfDefect(5, 3, 0.3, 0.2); // XRel=0.3mm=300μm
		KlarfDefect rotatedDefect2 = KlarfCoordinateRotator.rotateDefect(defect2, params2);
		System.out.println(rotatedDefect2);

		// 场景3：批量旋转
		System.out.println("=== 场景3：批量旋转缺陷 ===");
		List<KlarfDefect> defectList = new ArrayList<>();
		defectList.add(new KlarfDefect(5, 3, 300.0, 200.0));
		defectList.add(new KlarfDefect(6, 4, 400.0, 300.0));
		List<KlarfDefect> rotatedList = KlarfCoordinateRotator.rotateDefects(defectList, params1);
		rotatedList.forEach(System.out::println);
	}
}
