package com.peachten.shortlink.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 修改分组入参
 */
@Data
public class GroupUpdateReqDTO {

    @NotBlank(message = "分组标识不能为空")
    private String gid;

    @NotBlank(message = "分组名称不能为空")
    @Size(max = 64, message = "分组名称长度不能超过 64")
    private String name;
}
