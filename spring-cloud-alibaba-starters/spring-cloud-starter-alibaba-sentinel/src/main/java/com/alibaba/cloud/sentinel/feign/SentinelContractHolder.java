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

package com.alibaba.cloud.sentinel.feign;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import feign.Contract;
import feign.MethodMetadata;

/**
 * 使用静态字段{@link SentinelContractHolder#METADATA_MAP}来保存{@link MethodMetadata}数据。
 *
 * @author <a href="mailto:fangjian0423@gmail.com">Jim</a>
 */
public class SentinelContractHolder implements Contract {

	private final Contract delegate;

	/**
	 * configKey来自于{@link feign.Feign#configKey(Class, Method)}
	 */
	public final static Map<String/* ClassFullName + configKey */, MethodMetadata> METADATA_MAP = new HashMap<>();

	public SentinelContractHolder(Contract delegate) {
		this.delegate = delegate;
	}

	@Override
	public List<MethodMetadata> parseAndValidateMetadata(Class<?> targetType) {
		// 解析类中所有的方法，并生成方法元信息
		List<MethodMetadata> metadatas = delegate.parseAndValidateMetadata(targetType);
		// 放入静态变量中
		metadatas.forEach(metadata -> METADATA_MAP.put(targetType.getName() + metadata.configKey(), metadata));
		return metadatas;
	}

}
