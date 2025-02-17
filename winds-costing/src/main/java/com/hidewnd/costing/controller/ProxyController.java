package com.hidewnd.costing.controller;

import com.alibaba.fastjson2.JSONObject;
import com.hidewnd.common.base.response.R;
import com.hidewnd.costing.dto.ProxyDto;
import com.hidewnd.costing.dto.validate.RequestModel;
import com.hidewnd.costing.utils.RequestUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@Tag(name = "代理请求")
@RestController
@RequestMapping("/proxy")
public class ProxyController {

    @Value("${jx3api.url:}")
    private String jx3api;

    @Value("${jx3api.ticket:}")
    private String ticket;

    @Value("${jx3api.token:}")
    private String token;


    @Operation(summary = "JX3API代理接口")
    @PostMapping("/jx3api")
    public R<JSONObject> jx3api(@RequestBody @Validated(RequestModel.class) ProxyDto dto){
        Map<String, Object> params = dto.getParams();
        params.put("ticket", ticket);
        params.put("token", token);
        String json = RequestUtil.postRequest(jx3api+ dto.getUrl(), JSONObject.toJSONString(params));
        return R.success(R.CODE_SUCCESS,"代理请求成功",  JSONObject.parseObject(json));
    }

}
