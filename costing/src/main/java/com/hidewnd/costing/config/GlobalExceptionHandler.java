package com.hidewnd.costing.config;

import cn.hutool.core.util.StrUtil;
import com.hidewnd.common.base.CommonException;
import com.hidewnd.common.base.response.R;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.validation.BindException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;


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
        return R.error(R.CODE_NPE_ERROR, e.getMessage());
    }


    @ExceptionHandler(value = Exception.class)
    @ResponseBody
    public R<String> exceptionHandler(HttpServletRequest req, Exception e) {
        return R.error(R.CODE_RUNTIME_ERROR, e.getMessage());
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, BindException.class})
    @ResponseStatus(HttpStatus.OK)
    @ResponseBody
    public R<String> handleMethodArgumentNotValidException(Exception exception) {
        StringBuilder errorInfo = new StringBuilder();
        BindingResult bindingResult = null;
        if (exception instanceof MethodArgumentNotValidException) {
            bindingResult = ((MethodArgumentNotValidException) exception).getBindingResult();
        }
        if (exception instanceof BindException) {
            bindingResult = ((BindException) exception).getBindingResult();
        }
        if (bindingResult != null) {
            for (int i = 0; i < bindingResult.getFieldErrors().size(); i++) {
                if (i > 0) {
                    errorInfo.append(",");
                }
                FieldError fieldError = bindingResult.getFieldErrors().get(i);
                errorInfo.append(StrUtil.format("[{}]{}", fieldError.getField(), fieldError.getDefaultMessage()));
            }
        }
        return R.error(R.CODE_PARAM_ERROR, errorInfo.toString());
    }


}
