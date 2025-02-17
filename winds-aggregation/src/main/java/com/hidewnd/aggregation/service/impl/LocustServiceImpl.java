package com.hidewnd.aggregation.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.util.NumberUtil;
import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson2.JSONObject;
import com.hidewnd.aggregation.dao.LocustRepository;
import com.hidewnd.aggregation.dto.LocustUserDto;
import com.hidewnd.aggregation.entity.LocustUser;
import com.hidewnd.aggregation.service.LocustService;
import com.hidewnd.util.RequestUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Example;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service("locustService")
public class LocustServiceImpl implements LocustService {

    @Value("${jx3-api.token:}")
    private String token;

    @Value("${jx3-api.ticket:}")
    private String ticket;

    private LocustRepository locustRepository;

    @Autowired
    public void setLocustRepository(LocustRepository locustRepository) {
        this.locustRepository = locustRepository;
    }

    @Override
    public LocustUserDto submit(LocustUserDto userDto) {
        LocustUser locustUser = new LocustUser();
        BeanUtil.copyProperties(userDto, locustUser);
        Example<LocustUser> example = Example.of(locustUser);
        locustUser = locustRepository.findOne(example).stream().findFirst().orElse(locustUser);
        JSONObject roleInfo = getRoleInfo(locustUser.getServerName(), locustUser.getRoleName());
        if (roleInfo != null) {
            locustUser.setZoneName(roleInfo.getString("zoneName"));
            locustUser.setGlobalRoleId(roleInfo.getString("globalRoleId"));
            locustUser.setRoleId(roleInfo.getString("roleId"));
            locustUser.setForceName(roleInfo.getString("forceName"));
            locustUser.setTongName(roleInfo.getString("tongName"));
            locustUser.setCampName(roleInfo.getString("campName"));
            if (locustUser.getId() != null) {
                locustUser.setActivity(NumberUtil.nullToZero(locustUser.getActivity()) + 1);
            } else {
                locustUser.setActivity(1);
            }
        }
        locustUser = locustRepository.save(locustUser);
        BeanUtil.copyProperties(locustUser, userDto);
        return userDto;
    }

    @Override
    public List<LocustUserDto> query(LocustUserDto userDto) {
        LocustUser locustUser = new LocustUser();
        BeanUtil.copyProperties(userDto, locustUser);
        Example<LocustUser> example = Example.of(locustUser);
        List<LocustUser> list = locustRepository.findAll(example);
        List<LocustUserDto> results = new ArrayList<>();
        if (CollectionUtil.isNotEmpty(list)) {
            results = list.stream().map(item -> {
                LocustUserDto dto = new LocustUserDto();
                BeanUtil.copyProperties(item, dto);
                return dto;
            }).toList();
        }
        return results;
    }

    private JSONObject getRoleInfo(String server, String roleName) {
        String url = "https://www.jx3api.com/data/role/detailed";
        Map<String, Object> params = new HashMap<>();
        params.put("server", server);
        params.put("name", roleName);
        params.put("token", token);
        params.put("ticket", ticket);
        String body = RequestUtil.postRequest(url, JSONObject.toJSONString(params));
        JSONObject jsonObject = null;
        if (StrUtil.isNotEmpty(body)) {
            jsonObject = JSONObject.parseObject(body);
            if (jsonObject.getInteger("code") == 200) {
                jsonObject = jsonObject.getJSONObject("data");
            }
        }
        return jsonObject;
    }
}
