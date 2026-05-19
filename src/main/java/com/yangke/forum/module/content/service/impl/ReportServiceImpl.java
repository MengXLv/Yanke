package com.yangke.forum.module.content.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yangke.forum.common.BusinessException;
import com.yangke.forum.common.PageResult;
import com.yangke.forum.module.content.entity.Report;
import com.yangke.forum.module.content.mapper.ReportMapper;
import com.yangke.forum.module.content.service.ReportService;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.*;

@Service
public class ReportServiceImpl implements ReportService {

    @Resource
    private ReportMapper reportMapper;

    @Override
    public void submit(Long reporterId, Long targetId, Integer targetType, String reason) {
        if (reason == null || reason.trim().isEmpty()) {
            throw new BusinessException(400, "请填写举报原因");
        }
        // 防止重复举报
        Report existing = reportMapper.selectOne(Wrappers.<Report>lambdaQuery()
                .eq(Report::getReporterId, reporterId)
                .eq(Report::getTargetId, targetId)
                .eq(Report::getTargetType, targetType)
                .eq(Report::getStatus, 0));
        if (existing != null) {
            throw new BusinessException(400, "您已举报过，请等待处理");
        }
        Report r = new Report();
        r.setReporterId(reporterId);
        r.setTargetId(targetId);
        r.setTargetType(targetType);
        r.setReason(reason.trim());
        r.setStatus(0);
        reportMapper.insert(r);
    }

    @Override
    public PageResult<Report> listPending(int page, int size) {
        LambdaQueryWrapper<Report> wrapper = Wrappers.<Report>lambdaQuery()
                .eq(Report::getStatus, 0)
                .orderByAsc(Report::getCreateTime);
        IPage<Report> result = reportMapper.selectPage(new Page<>(page, size), wrapper);
        return new PageResult<>(result.getTotal(), page, size, result.getRecords());
    }

    @Override
    public void handle(Long reportId, Long handlerId, Integer status, String note) {
        Report r = reportMapper.selectById(reportId);
        if (r == null) throw new BusinessException(404, "举报不存在");
        if (r.getStatus() != 0) throw new BusinessException(400, "该举报已处理");
        r.setStatus(status);
        r.setHandlerId(handlerId);
        r.setHandlerNote(note);
        reportMapper.updateById(r);
    }

    @Override
    public Map<String, Object> stats() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("pendingCount", reportMapper.selectCount(
                Wrappers.<Report>lambdaQuery().eq(Report::getStatus, 0)));
        m.put("handledCount", reportMapper.selectCount(
                Wrappers.<Report>lambdaQuery().eq(Report::getStatus, 1)));
        return m;
    }
}
