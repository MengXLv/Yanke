package com.yangke.forum.module.content.controller;

import com.yangke.forum.common.RequestContext;
import com.yangke.forum.common.Result;
import com.yangke.forum.module.content.service.ReportService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.Map;

@RestController
@RequestMapping("/api/report")
public class ReportController {

    @Resource
    private ReportService reportService;

    @PostMapping
    public Result<Void> submit(@RequestBody Map<String, Object> body,
                                HttpServletRequest request) {
        Long userId = RequestContext.requireLogin(request);
        Long targetId = Long.valueOf(body.get("targetId").toString());
        Integer targetType = Integer.valueOf(body.get("targetType").toString());
        String reason = (String) body.get("reason");
        reportService.submit(userId, targetId, targetType, reason);
        return Result.ok();
    }
}
