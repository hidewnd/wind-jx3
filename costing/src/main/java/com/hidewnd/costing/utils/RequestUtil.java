package com.hidewnd.costing.utils;

import cn.hutool.core.util.StrUtil;
import cn.hutool.http.Header;
import cn.hutool.http.HttpRequest;

public class RequestUtil {

    public static String getRequest(String format, Object... param) {
        String url = StrUtil.format(format, param);
        String body = HttpRequest.get(url).header(Header.ACCEPT, "gzip, deflate, br")
                .timeout(5000)
                .execute().body();
        return "[]".equals(body) ? "" : body;
    }


    public static String postRequest(String url, String params) {
        String body = HttpRequest.post(url)
                .body(params)
                .header(Header.CONTENT_TYPE, "application/json")
                .header(Header.ACCEPT, "gzip, deflate, br")
                .timeout(5000)
                .execute().body();
        return "[]".equals(body) ? "" : body;
    }

}
