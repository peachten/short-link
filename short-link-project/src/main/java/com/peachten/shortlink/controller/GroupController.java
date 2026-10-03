package com.peachten.shortlink.controller;

import com.peachten.shortlink.common.result.Result;
import com.peachten.shortlink.common.result.Results;
import com.peachten.shortlink.dto.GroupSaveReqDTO;
import com.peachten.shortlink.dto.GroupUpdateReqDTO;
import com.peachten.shortlink.service.GroupService;
import com.peachten.shortlink.vo.GroupRespVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "分组模块")
@RestController
@RequestMapping("/api/short-link/admin/v1/group")
@RequiredArgsConstructor
public class GroupController {

    private final GroupService groupService;

    @Operation(summary = "新建分组，返回 gid")
    @PostMapping("/save")
    public Result<String> save(@Valid @RequestBody GroupSaveReqDTO req) {
        return Results.success(groupService.saveGroup(req));
    }

    @Operation(summary = "当前用户的分组列表（含短链数量）")
    @GetMapping("/list")
    public Result<List<GroupRespVO>> list() {
        return Results.success(groupService.listGroup());
    }

    @Operation(summary = "重命名分组")
    @PostMapping("/update")
    public Result<Void> update(@Valid @RequestBody GroupUpdateReqDTO req) {
        groupService.updateGroup(req);
        return Results.success();
    }

    @Operation(summary = "删除分组（分组下有短链则拒绝）")
    @PostMapping("/delete")
    public Result<Void> delete(@RequestParam String gid) {
        groupService.deleteGroup(gid);
        return Results.success();
    }

    @Operation(summary = "当前用户的分组数量")
    @GetMapping("/count")
    public Result<Long> count() {
        return Results.success(groupService.countGroup());
    }
}
