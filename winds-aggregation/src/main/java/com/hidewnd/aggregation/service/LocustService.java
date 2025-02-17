package com.hidewnd.aggregation.service;

import com.hidewnd.aggregation.dto.LocustUserDto;

import java.util.List;

public interface LocustService {

    LocustUserDto submit(LocustUserDto userDto);

    List<LocustUserDto> query(LocustUserDto userDto);
}
