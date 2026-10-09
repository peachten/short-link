package com.peachten.shortlink.controller;

import com.peachten.shortlink.common.result.Result;
import com.peachten.shortlink.common.result.Results;
import com.peachten.shortlink.dto.ShortLinkCreateReqDTO;
import com.peachten.shortlink.service.ShortLinkService;
import com.peachten.shortlink.vo.ShortLinkCreateRespVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "短链模块")
@RestController
@RequestMapping("/api/short-link/admin/v1/link")
@RequiredArgsConstructor
public class ShortLinkController {

    private final ShortLinkService shortLinkService;

    @Operation(summary = "创建短链（幂等，重复提交返回同一短链）")
    @PostMapping
    public Result<ShortLinkCreateRespVO> create(@Valid @RequestBody ShortLinkCreateReqDTO req) {
        return Results.success(shortLinkService.create(req));
    }
}
