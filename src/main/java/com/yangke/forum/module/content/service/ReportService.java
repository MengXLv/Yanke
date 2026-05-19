package com.yangke.forum.module.content.service;

import com.yangke.forum.common.PageResult;
import com.yangke.forum.module.content.entity.Report;
import java.util.Map;

public interface ReportService {
    void submit(Long reporterId, Long targetId, Integer targetType, String reason);
    PageResult<Report> listPending(int page, int size);
    void handle(Long reportId, Long handlerId, Integer status, String note);
    Map<String, Object> stats();
}
