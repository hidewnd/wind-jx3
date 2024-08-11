package com.hidewnd.costing.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Map;

@Data
@NoArgsConstructor
public class ProxyDto implements Serializable {

    @Schema(description = "代理地址,以/data开头")
    private String url;

    @Schema(description = "请求参数")
    private Map<String, Object> params;
}
