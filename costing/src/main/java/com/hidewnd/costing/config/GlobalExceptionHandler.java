package com.hidewnd.costing.config;

import com.hidewnd.common.base.CommonException;
import com.hidewnd.common.base.response.R;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;

@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(value = CommonException.class)
    @ResponseBody
    public R<String> bizExceptionHandler(HttpServletRequest req, CommonException e) {
        return R.error(e.getCode(), e.getMessage());
    }

    @ExceptionHandler(value = NullPointerException.class)
    @ResponseBody
    public R<String> exceptionHandler(HttpServletRequest req, NullPointerException e) {
        return R.error(5001, e.getMessage());
    }


    @ExceptionHandler(value = Exception.class)
    @ResponseBody
    public R<String> exceptionHandler(HttpServletRequest req, Exception e) {
        return R.error(5002, e.getMessage());
    }

}
