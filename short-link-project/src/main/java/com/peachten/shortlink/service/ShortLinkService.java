package com.peachten.shortlink.service;

import com.peachten.shortlink.dto.ShortLinkCreateReqDTO;
import com.peachten.shortlink.vo.ShortLinkCreateRespVO;

public interface ShortLinkService {

    /**
     * 创建短链（幂等）
     * <p>
     * 同一分组下同一长链重复提交只会有一条记录，重复提交返回已存在的短链且 idempotent=true。
     */
    ShortLinkCreateRespVO create(ShortLinkCreateReqDTO req);
}
