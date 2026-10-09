package com.peachten.shortlink.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 创建短链入参
 */
@Data
public class ShortLinkCreateReqDTO {

    @NotBlank(message = "原始链接不能为空")
    @Pattern(regexp = "^https?://.+", message = "原始链接必须以 http:// 或 https:// 开头")
    private String originUrl;

    @NotBlank(message = "分组标识不能为空")
    private String gid;

    /** 创建来源 0 接口 1 控制台，不传按 0 处理 */
    private Integer createdType;

    /** 有效期类型 0 永久 1 自定义，不传按 0 处理 */
    private Integer validDateType;

    /** 失效时间，validDateType=1 时必填且必须晚于当前时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime validDate;

    @Size(max = 255, message = "备注长度不能超过 255")
    private String describe;

    /** 自定义后缀，不传则系统随机生成 */
    @Size(max = 16, message = "自定义后缀长度不能超过 16")
    @Pattern(regexp = "^[0-9a-zA-Z]*$", message = "自定义后缀只能包含数字和字母")
    private String customShortUri;
}
