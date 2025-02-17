package com.hidewnd.aggregation.controller;

import com.hidewnd.aggregation.dto.LocustUserDto;
import com.hidewnd.aggregation.dto.validate.RequestModel;
import com.hidewnd.aggregation.service.LocustService;
import com.hidewnd.common.base.response.R;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/locust")
public class LocustController {

    private LocustService locustService;

    @Autowired
    public void setLocustService(LocustService locustService) {
        this.locustService = locustService;
    }

    @PostMapping("/submit")
    @Operation(summary = "蝗虫用户登记接口")
    public R<LocustUserDto> submit(@RequestBody @Validated(RequestModel.class) LocustUserDto userDto) {
        LocustUserDto dto = locustService.submit(userDto);
        return R.success("登记成功，感谢有您", dto);
    }

    @PostMapping("/query")
    @Operation(summary = "蝗虫用户登记接口")
    public R<List<LocustUserDto>> query(@RequestBody @Validated(RequestModel.class) LocustUserDto userDto) {
        List<LocustUserDto> list = locustService.query(userDto);
        return R.success("蝗虫玩家列表", list);
    }
}
