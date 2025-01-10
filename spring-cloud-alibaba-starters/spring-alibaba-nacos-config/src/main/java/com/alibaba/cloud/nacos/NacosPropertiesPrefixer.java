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

package com.alibaba.cloud.nacos;

import java.util.ServiceLoader;

import com.alibaba.cloud.nacos.utils.StringUtils;

import org.springframework.boot.context.properties.bind.BindResult;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.env.Environment;

/**
 * 提供获取Nacos通用属性配置前缀的功能。
 * <ul>
 *     <li>首先是用户指定 PROPERTIES(spring.nacos.properties.prefix)</li>
 *     <li>其次是微服务环境下 DEFAULT(spring.cloud.nacos)</li>
 *     <li>最后是Spring环境下 PROPERTIES(spring.nacos)</li>
 * </ul>
 *
 * @see com.alibaba.cloud.nacos.SpringCloudNacosPropertiesPrefixProvider
 * @author shiyiyue
 */
public final class NacosPropertiesPrefixer {

	/**
	 * prefix from spi provider.
	 */
	public static final String PREFIX = getPrefixFromSpi();

	private NacosPropertiesPrefixer() {
	}

	private static String getPrefixFromSpi() {
		/**
		 * 加载本地的{@link NacosPropertiesPrefixProvider}，并从其获取配置属性前缀，
		 * 默认的实现是{@link com.alibaba.cloud.nacos.SpringCloudNacosPropertiesPrefixProvider}
		 */
		ServiceLoader<NacosPropertiesPrefixProvider> load = ServiceLoader.load(NacosPropertiesPrefixProvider.class);
		for (NacosPropertiesPrefixProvider provider : load) {
			return provider.getPrefix();
		}
		return "";
	}

	/**
	 * 从环境中解析Naocs通用属性文件的前缀
	 *
	 * @param environment
	 * @return
	 */
	public static String getPrefix(Environment environment) {
		String prefix = "spring.nacos";
		/**
		 * 从 PROPERTIES(spring.nacos.properties.prefix) 解析nacos的前缀
		 */
		String prefixFromProperties = environment.getProperty("spring.nacos.properties.prefix");
		/**
		 * 读取顺序: PROPERTIES(spring.nacos.properties.prefix) -> DEFAULT(spring.cloud.nacos) -> DEFAULT(spring.nacos)
		 */
		if (StringUtils.isBlank(prefixFromProperties)) {
			/**
			 * 当未指定前缀时，便从spi中读取
			 */
			if (StringUtils.isNotBlank(NacosPropertiesPrefixer.PREFIX)) {
				prefix = NacosPropertiesPrefixer.PREFIX;
			}
		}
		else {
			prefix = prefixFromProperties;
		}

		/**
		 * 对prefix进行格式处理，取出末尾的.
		 */
		if (StringUtils.isNotBlank(prefix) && prefix.endsWith(".")) {
			prefix = prefix.substring(0, prefix.length() - 1);
		}
		return prefix;
	}

	public static String getPrefix(Binder binder) {
		String prefix = "spring.nacos";
		BindResult<String> bind = binder.bind("spring.nacos.properties.prefix", String.class);
		if (!bind.isBound()) {
			if (StringUtils.isNotBlank(NacosPropertiesPrefixer.PREFIX)) {
				prefix = NacosPropertiesPrefixer.PREFIX;
			}
		}
		else {
			prefix = bind.get();
		}

		if (StringUtils.isNotBlank(prefix) && prefix.endsWith(".")) {
			prefix = prefix.substring(0, prefix.length() - 1);
		}
		return prefix;
	}

}
