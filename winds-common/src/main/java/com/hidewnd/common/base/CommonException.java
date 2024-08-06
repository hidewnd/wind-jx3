package com.hidewnd.common.base;

import com.hidewnd.common.base.response.R;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class CommonException extends RuntimeException{
    private int code;
    private String message;


    public CommonException(int code, String message) {
        this.code = code;
        this.message = message;
    }

    public CommonException(String message) {
        this.code = R.CODE_ERROR;
        this.message = message;
    }
}
