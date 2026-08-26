package com.hidewnd.winds.common.base.entity;

import lombok.Data;

import java.time.LocalDate;
import java.util.Date;

@Data
public class Holiday {

    private Date date;
    private int status; // 1=补班, 2=放假

    public Holiday(Date date, int status) {
        this.date = date;
        this.status = status;
    }
}
