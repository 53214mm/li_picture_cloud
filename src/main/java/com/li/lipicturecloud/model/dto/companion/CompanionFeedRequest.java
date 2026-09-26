package com.li.lipicturecloud.model.dto.companion;

import lombok.Data;

/**
 * HTTP 喂养接口的请求体。
 *
 * <p>客户端因超时重试同一次喂养时必须复用相同 idempotencyKey，服务端才会返回原结果，
 * 而不是让伙伴重复成长。</p>
 */
@Data
public class CompanionFeedRequest {
    /** 用户选择的图片 id。 */
    private Long pictureId;
    /** 客户端生成的一次请求标识；同一业务重试时保持不变。 */
    private String idempotencyKey;
}
