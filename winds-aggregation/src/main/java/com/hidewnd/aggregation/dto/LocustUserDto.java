package com.hidewnd.aggregation.dto;

import com.hidewnd.aggregation.dto.validate.RequestModel;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class LocustUserDto implements Serializable {

    @Schema(description = "大区")
    private String zoneName;

    @Schema(description = "服务器")
    @NotBlank(message = "服务器不能为空", groups = RequestModel.class)
    private String serverName;

    @Schema(description = "角色名")
    @NotBlank(message = "角色名不能为空", groups = RequestModel.class)
    private String roleName;

    @Schema(description = "门派")
    private String forceName;

    @Schema(description = "帮会")
    private String tongName;

    @Schema(description = "阵营")
    private String campName;

    @Schema(description = "活跃值")
    private Integer activity;

}
