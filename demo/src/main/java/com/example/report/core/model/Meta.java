package com.example.report.core.model;

import lombok.Data;
import java.util.Date;
import java.util.Map;

/**
 * 模版元数据类
 * 用于存储模版的非渲染相关信息，如版本、作者、格式版本等
 */
@Data
public class Meta {
	/**
	 * 模版格式版本 (例如: DOCX_2016, XLSX_2019)
	 * 用于兼容不同版本的 Office 软件特性
	 */
	private String formatVersion;

	/**
	 * 模版作者
	 */
	private String author;

	/**
	 * 模版描述
	 */
	private String description;

	/**
	 * 创建时间
	 */
	private Date createTime;

	/**
	 * 最后修改时间
	 */
	private Date updateTime;

	/**
	 * 扩展属性 (用于存储特定格式的额外配置，如 PPT 的主题色、Excel 的保护密码等)
	 * Key-Value 结构保证灵活性
	 */
	private Map<String, Object> extensions;

	/**
	 * 版本号 (用于乐观锁或历史版本回溯)
	 */
	private Integer version;

	// 构造函数
	public Meta() {
		this.createTime = new Date();
		this.updateTime = new Date();
		this.version = 1;
	}
}