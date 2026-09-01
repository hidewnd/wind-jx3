package com.hidewnd.winds.scout.model;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.Instant;
import java.util.List;

/**
 * 微博监控博主配置的 MongoDB 实体。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "weibo_subscription")
@Schema(description = "微博监控博主持久化实体")
public class WeiboBlogger {

    @Id
    @Schema(description = "MongoDB 主键，与微博 UID 相同")
    private String id;
    @Schema(description = "微博 UID", example = "1761587065")
    private String uid;
    @Field("screen_name")
    @Schema(description = "博主显示名称")
    private String screenName;
    @Schema(description = "博主别称")
    private List<String> aliases;
    @Schema(description = "是否启用监控")
    private Boolean enabled;
    @Schema(description = "参考服务保留的轮询频率字段，管理接口不修改")
    private Integer frequency;
    @Schema(description = "参考服务保留的推送群组字段，管理接口不修改")
    private List<Long> groups;
    @Field("created_at")
    @Schema(description = "创建时间")
    private Instant createdAt;
    @Field("updated_at")
    @Schema(description = "更新时间")
    private Instant updatedAt;
}
