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

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

/**
 * 基于Nacos的配置中心启动条件类，默认条件下启动Nacos配置中心
 *
 * @author shiyiyue
 */
public class NacosConfigEnabledCondition implements Condition {

	@Override
	public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
		/**
		 * 从环境中解析Nacos通用配置文件前缀
		 */
		String prefix = NacosPropertiesPrefixer.getPrefix(context.getEnvironment());
		/**
		 * 解析Nacos配置中心是否启动，PROPERRTIES(spring.cloud.nacos.config.enabled) -> DEFAULT(true)
		 */
		return context.getEnvironment().getProperty(prefix + ".config.enabled", Boolean.class, true);
	}
}
