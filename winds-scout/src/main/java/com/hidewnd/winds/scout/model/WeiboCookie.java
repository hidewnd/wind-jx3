package com.hidewnd.winds.scout.model;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.Instant;

/**
 * 微博账号 Cookie 存储项，以名称、域和路径共同标识一个 Cookie。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "微博账号 Cookie 存储项")
public class WeiboCookie {

    @Schema(description = "Cookie 名称")
    private String name;
    @Schema(description = "Cookie 值", accessMode = Schema.AccessMode.WRITE_ONLY)
    private String value;
    @Schema(description = "生效域名")
    private String domain;
    @Schema(description = "生效路径")
    private String path;
    @Field("expires_at")
    @Schema(description = "绝对过期时间，空值表示会话期")
    private Instant expiresAt;
    @Field("host_only")
    @Schema(description = "是否仅匹配原始主机")
    private boolean hostOnly;
    @Schema(description = "是否仅通过 HTTPS 发送")
    private boolean secure;
    @Field("http_only")
    @Schema(description = "是否禁止页面脚本读取")
    private boolean httpOnly;
    @Schema(description = "是否由旧版 Cookie 字符串导入且尚未被响应确认")
    private boolean seed;
}
