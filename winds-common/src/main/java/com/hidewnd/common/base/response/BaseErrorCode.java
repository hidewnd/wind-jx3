package com.hidewnd.common.base.response;

/**
 * @author lyne
 * @date 2022/9/11
 */
public enum BaseErrorCode implements ErrorCode {
	NULLError(40001, "参数不能为空！"),
	PAGE_QUERY_PARAMS_LACK_ERROR(40002, "分页查询参数缺失"),
	DATA_NULL_FOUND(40001, "查询不到这条数据！"),
	NOT_LOGIN(40003, "用户未登录！"),
	SERVER_BUSY(50000, "服务繁忙，请稍后重试！");

	private final int code;
	private final String msg;

	@Override
	public String getMsg() {
		return this.msg;
	}

	@Override
	public int getCode() {
		return this.code;
	}

	BaseErrorCode(int code, String msg) {
		this.code = code;
		this.msg = msg;
	}

}
