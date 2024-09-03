package com.hidewnd.costing.dto;

import com.hidewnd.costing.dto.validate.RequestModel;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Map;

@Data
@NoArgsConstructor
public class ProxyDto implements Serializable {

    @Schema(description = "代理地址,以/data开头")
    @NotBlank(message = "代理地址不能为空", groups = RequestModel.class)
    private String url;

    @Schema(description = "请求参数")
    private Map<String, Object> params;
}
