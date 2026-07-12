// com/report/entity/ReportTask.java
package com.report.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("t_report_task")
public class ReportTask {
	@TableId(type = IdType.AUTO)
	private Long id;
	private String taskCode;
	private Long templateId;
	private String dataSource; // JSON 格式数据源
	private String status; // PENDING/PROCESSING/SUCCESS/FAILED
	private String resultFileUrl;
	private LocalDateTime createdTime;
	@TableLogic
	private Integer deleted;
}