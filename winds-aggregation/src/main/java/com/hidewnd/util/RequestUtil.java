package com.hidewnd.util;

import cn.hutool.http.Header;
import cn.hutool.http.HttpRequest;

public class RequestUtil {


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
