package com.hidewnd.common.base.response;

/**
 * @author lyne
 * @date 2022/9/11
 */
public interface ErrorCode {
	/**
	 * 获取错误信息
	 * @return msg
	 */
	String getMsg();

	/**
	 * 获取错误码
	 * @return code
	 */
	int getCode();

}
