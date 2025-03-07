/*
 * Copyright 2013-2023 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.alibaba.cloud.sentinel;

/**
 * Sentinel 常量
 *
 * @author fangjian
 */
public final class SentinelConstants {

	/**
	 * {@link SentinelProperties}的前缀
	 */
	public static final String PROPERTY_PREFIX = "spring.cloud.sentinel";

	/**
	 * 阻塞页键
	 */
	public static final String BLOCK_PAGE_URL_CONF_KEY = "csp.sentinel.web.servlet.block.page";

	/**
	 * 阻塞类型
	 */
	public static final String BLOCK_TYPE = "block";

	/**
	 * 回退类型
	 */
	public static final String FALLBACK_TYPE = "fallback";

	/**
	 * Url清理器类型
	 */
	public static final String URLCLEANER_TYPE = "urlCleaner";

	/**
	 * 冷因子
	 */
	public static final String COLD_FACTOR = "3";

	/**
	 * 字符集
	 */
	public static final String CHARSET = "UTF-8";

	/**
	 * Sentinel API 端口
	 */
	public static final String API_PORT = "8719";

	/**
	 * 不可初始化
	 */
	private SentinelConstants() {
		throw new AssertionError("Must not instantiate constant utility class");
	}

}
