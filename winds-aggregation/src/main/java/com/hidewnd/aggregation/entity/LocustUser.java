package com.hidewnd.aggregation.entity;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.io.Serializable;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Document("locust_user")
public class LocustUser implements Serializable {

    @Id
    private String id;

    @Schema(description = "大区")
    private String zoneName;

    @Schema(description = "服务器")
    private String serverName;

    @Schema(description = "角色名")
    private String roleName;

    private String roleId;

    @Schema(description = "全局ID")
    private String globalRoleId;

    @Schema(description = "门派")
    private String forceName;

    @Schema(description = "帮会")
    private String tongName;

    @Schema(description = "阵营")
    private String campName;

    @Schema(description = "活跃值")
    private Integer activity;

    private String description;
}
