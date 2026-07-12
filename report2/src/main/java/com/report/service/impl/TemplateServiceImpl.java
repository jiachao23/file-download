// com/report/service/impl/TemplateServiceImpl.java
package com.report.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.report.entity.Template;
import com.report.mapper.TemplateMapper;
import com.report.service.TemplateService;
import org.springframework.stereotype.Service;

@Service
public class TemplateServiceImpl extends ServiceImpl<TemplateMapper, Template> implements TemplateService {
}