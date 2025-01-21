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

package com.alibaba.cloud.nacos.parser;

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import com.alibaba.cloud.nacos.utils.StringUtils;

import org.springframework.boot.env.PropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.Resource;

/**
 * 特定于Nacos的加载器，如果需要支持解析的其他方法，需要执行以下步骤：
 * <ul>
 *     <li>继承{@link AbstractPropertySourceLoader}</li>
 *     <li>在{@code META-INF/spring.factories}定义{@code org.springframework.boot.env.PropertySourceLoader=..}</li>
 * </ul>
 *
 * <p>需要使用{@link NacosByteArrayResource}
 *
 * @author zkz
 */
public abstract class AbstractPropertySourceLoader implements PropertySourceLoader {

	/**
	 * symbol: dot.
	 */
	static final String DOT = ".";

	/**
	 * Prevent interference with other loaders.Nacos-specific loader, unless the reload
	 * changes it.
	 * @param name the root name of the property source. If multiple documents are loaded
	 * an additional suffix should be added to the name for each source loaded.
	 * @param resource the resource to load
	 * @return if the resource can be loaded
	 */
	protected boolean canLoad(String name, Resource resource) {
		// 仅处理Nacos特定的资源
		return resource instanceof NacosByteArrayResource;
	}

	/**
	 * Load the resource into one or more property sources. Implementations may either
	 * return a list containing a single source, or in the case of a multi-document format
	 * such as yaml a source for each document in the resource.
	 * @param name the root name of the property source. If multiple documents are loaded
	 * an additional suffix should be added to the name for each source loaded.
	 * @param resource the resource to load
	 * @return a list property sources
	 * @throws IOException if the source cannot be loaded
	 */
	@Override
	public List<PropertySource<?>> load(String name, Resource resource) throws IOException {
		if (!canLoad(name, resource)) {
			return Collections.emptyList();
		}
		return this.doLoad(name, resource);
	}

	/**
	 * Load the resource into one or more property sources. Implementations may either
	 * return a list containing a single source, or in the case of a multi-document format
	 * such as yaml a source for each document in the resource.
	 * @param name the root name of the property source. If multiple documents are loaded
	 * an additional suffix should be added to the name for each source loaded.
	 * @param resource the resource to load
	 * @return a list property sources
	 * @throws IOException if the source cannot be loaded
	 */
	protected abstract List<PropertySource<?>> doLoad(String name, Resource resource) throws IOException;

	protected void flattenedMap(Map<String, Object> result, Map<String, Object> dataMap, String parentKey) {
		// 空map无需展开
		if (dataMap == null || dataMap.isEmpty()) {
			return;
		}
		// 获取所有的实体映射
		Set<Entry<String, Object>> entries = dataMap.entrySet();
		// 依次迭代
		for (Iterator<Entry<String, Object>> iterator = entries.iterator(); iterator.hasNext();) {
			Map.Entry<String, Object> entry = iterator.next();
			String key = entry.getKey();
			Object value = entry.getValue();
			/**
			 * 构造key的完整路径，处理如下：
			 * <ul>
			 *     <li>如果没有父级key，则直接使用key</li>
			 *     <li>如果key是以[作为开头，则格式为{parentKey}{key}</li>
			 *     <li>如果key不是以[作为开头，则格式为{parentKey}.{key}</li>
			 * </ul>
			 */
			String fullKey = StringUtils.isEmpty(parentKey) ? key : key.startsWith("[") ? parentKey.concat(key) : parentKey.concat(DOT).concat(key);

			// 对于嵌套Map的场景，继续下转，此时parentKey就是当前的fullKey
			if (value instanceof Map map) {
				flattenedMap(result, map, fullKey);
				continue;
			}
			else if (value instanceof Collection collection) {
				/**
				 * 对于集合，处理格式为<[0, object], [1, object], ...>
				 */
				int count = 0;
				for (Object object : collection) {
					flattenedMap(result, Collections.singletonMap("[" + (count++) + "]", object), fullKey);
				}
				continue;
			}

			result.put(fullKey, value);
		}
	}

}
