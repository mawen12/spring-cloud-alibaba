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

package com.alibaba.cloud.nacos.client;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.alibaba.cloud.nacos.NacosConfigProperties;

import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.util.CollectionUtils;

/**
 * 基于{@link Map}存储的属性源，同时维护了Nacos配置的上级信息{@link #dataId}和{@link #group}
 *
 * <p>属性源的名称为{dataId}-{group}
 * <p>属性以{@link Map}类型存储
 *
 * @author xiaojing
 * @author pbting
 */
public class NacosPropertySource extends MapPropertySource {

	/**
	 * 配置分组
	 */
	private final String group;

	/**
	 * 配置DataId
	 */
	private final String dataId;

	/**
	 * 获取属性的时间戳
	 */
	private final Date timestamp;

	/**
	 * 是否支持动态刷新
	 */
	private final boolean isRefreshable;

	NacosPropertySource(String group, String dataId, Map<String, Object> source, Date timestamp, boolean isRefreshable) {
		// 属性源的名称为{dataId}-{group}
		super(String.join(NacosConfigProperties.COMMAS, dataId, group), source);
		this.group = group;
		this.dataId = dataId;
		this.timestamp = timestamp;
		this.isRefreshable = isRefreshable;
	}

	public NacosPropertySource(List<PropertySource<?>> propertySources, String group, String dataId, Date timestamp, boolean isRefreshable) {
		this(group, dataId, getSourceMap(group, dataId, propertySources), timestamp, isRefreshable);
	}

	private static Map<String, Object> getSourceMap(String group, String dataId, List<PropertySource<?>> propertySources) {
		if (CollectionUtils.isEmpty(propertySources)) {
			return Collections.emptyMap();
		}
		// If only one, return the internal element, otherwise wrap it.
		if (propertySources.size() == 1) {
			PropertySource propertySource = propertySources.get(0);
			if (propertySource != null && propertySource.getSource() instanceof Map source) {
				// 直接返回属性源内部的元素类型为Map的值
				return source;
			}
		}

		Map<String, Object> sourceMap = new LinkedHashMap<>();
		List<PropertySource<?>> otherTypePropertySources = new ArrayList<>();
		for (PropertySource<?> propertySource : propertySources) {
			if (propertySource == null) {
				continue;
			}
			if (propertySource instanceof MapPropertySource mapPropertySource) {
				// 如果Nacos配置文件使用"---"来拆分属性名，属性源将有多个文档，每个文档都是一个Map, org.springframework.boot.env.YamlPropertySourceLoader#load
				// 仅处理MapPropertySource，将结果合并到哈希表中
				Map<String, Object> source = mapPropertySource.getSource();
				sourceMap.putAll(source);
			}
			else {
				// 其他类型待处理
				otherTypePropertySources.add(propertySource);
			}
		}

		// Nacos 不处理其他类型的属性源，需要用户自行处理
		if (!otherTypePropertySources.isEmpty()) {
			// 处理非MapPropertySource类型的属性源
			// 将其作为特定的属性源处理，属性名称为{dataId}-{group}，值为其他类型的属性源
			sourceMap.put(String.join(NacosConfigProperties.COMMAS, dataId, group), otherTypePropertySources);
		}

		return sourceMap;
	}

	public String getGroup() {
		return this.group;
	}

	public String getDataId() {
		return dataId;
	}

	public Date getTimestamp() {
		return timestamp;
	}

	public boolean isRefreshable() {
		return isRefreshable;
	}

}
