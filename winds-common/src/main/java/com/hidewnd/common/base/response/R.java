package com.hidewnd.common.base.response;

import lombok.Data;

import java.io.Serializable;

/**
 * 前端返回体
 * {"success": true, "code", 2000, "msg": "", "obj": null}
 *
 * @author lyne
 * @date 2022/8/18
 */
@Data
public class R<T> implements Serializable {

	private static final long serialVersionUID = 1L;

	public static final String DEFAULT_SUCCESS_MESSAGE = "请求成功";
	public static final String DEFAULT_ERROR_MESSAGE = "请求失败";

	public static final int CODE_SUCCESS = 2000;
	public static final int CODE_ERROR = 5000;


	/**
	 * 是否成功
	 */
	private boolean success = true;
	/**
	 * 状态码
	 */
	private int code;
	/**
	 * 返回信息
	 */
	private String msg;
	/**
	 * 返回实体
	 */
	private T obj;

	public static <T> R<T> error(String msg) {
		return error(CODE_ERROR, msg.trim().isEmpty() ? DEFAULT_ERROR_MESSAGE : msg);
	}

	public static <T> R<T> error(int code, String msg) {
		return error(code, msg, null);
	}

	public static <T> R<T> error(int code, String msg, T obj) {
		return create(code, msg.trim().isEmpty() ? DEFAULT_ERROR_MESSAGE : msg, obj);
	}

	public static <T> R<T> success(String msg) {
		return success(CODE_SUCCESS, msg.trim().isEmpty() ? DEFAULT_SUCCESS_MESSAGE : msg);
	}

	public static <T> R<T> successByObj(T obj) {
		return success(CODE_SUCCESS, DEFAULT_SUCCESS_MESSAGE, obj);
	}

	public static <T> R<T> success(int code, String msg) {
		return success(code, msg, null);
	}

	public static <T> R<T> success(String msg, T obj) {
		return success(CODE_SUCCESS, msg, obj);
	}

	public static <T> R<T> success(int code, T obj) {
		return success(code, "请求成功", obj);
	}

	public static <T> R<T> success(int code, String msg, T obj) {
		return create(code, msg.trim().isEmpty() ? DEFAULT_SUCCESS_MESSAGE : msg, obj);
	}

	private static <T> R<T> create(int code, String msg, T obj){
		R<T> ajaxR = new R<>();
		ajaxR.code = code;
		ajaxR.msg = msg;
		ajaxR.obj = obj;
		return ajaxR;
	}
}
