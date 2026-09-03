package com.lingxi.modules.report;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lingxi.common.exception.BizException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 报告服务。
 */
@Service
@RequiredArgsConstructor
public class ReportService {

    private final ReportMapper reportMapper;

    public Report create(String title, String content, Long sessionId, Long creatorId) {
        Report report = new Report();
        report.setTitle(title == null || title.isBlank() ? "未命名报告" : title);
        report.setContent(content == null ? "" : content);
        report.setSessionId(sessionId);
        report.setCreatorId(creatorId);
        reportMapper.insert(report);
        return report;
    }

    public Page<Report> page(long current, long size) {
        return reportMapper.selectPage(new Page<>(current, size),
                new LambdaQueryWrapper<Report>().orderByDesc(Report::getCreatedAt));
    }

    public Report get(Long id) {
        Report report = reportMapper.selectById(id);
        if (report == null) {
            throw BizException.notFound("报告");
        }
        return report;
    }
}
