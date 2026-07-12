
package com.report.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("t_template")
public class Template {
	@TableId(type = IdType.AUTO)
	private Long id;
	private String templateCode;
	private String name;
	private String type; // word/excel/ppt
	private String placeholders; // JSON 格式占位符定义
	private String fileUrl; // MinIO 存储路径
	private LocalDateTime createdTime;
	@TableLogic
	private Integer deleted;
}