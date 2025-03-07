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

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.LinkedHashMap;
import java.util.Map;

import com.alibaba.csp.sentinel.Entry;
import com.alibaba.csp.sentinel.EntryType;
import com.alibaba.csp.sentinel.SphU;
import com.alibaba.csp.sentinel.Tracer;
import com.alibaba.csp.sentinel.context.ContextUtil;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import feign.Feign;
import feign.InvocationHandlerFactory.MethodHandler;
import feign.MethodMetadata;
import feign.Target;

import org.springframework.cloud.openfeign.FallbackFactory;

import static feign.Util.checkNotNull;

/**
 * 由Sentinel所保护的{@link InvocationHandler}处理调用
 *
 * @author <a href="mailto:fangjian0423@gmail.com">Jim</a>
 */
public class SentinelInvocationHandler implements InvocationHandler {

	private final Target<?> target;

	private final Map<Method/* Feign接口中的方法 */, MethodHandler/* 被解析增强后的方法调用 */> dispatch;

	/**
	 * Feign注解定义的回退工厂
	 */
	private FallbackFactory fallbackFactory;

	private Map<Method/* Feign接口中的方法 */, Method/* 对应的回退方法 */> fallbackMethodMap;

	SentinelInvocationHandler(Target<?> target, Map<Method, MethodHandler> dispatch, FallbackFactory fallbackFactory) {
		this.target = checkNotNull(target, "target");
		this.dispatch = checkNotNull(dispatch, "dispatch");
		this.fallbackFactory = fallbackFactory;
		this.fallbackMethodMap = toFallbackMethod(dispatch);
	}

	SentinelInvocationHandler(Target<?> target, Map<Method, MethodHandler> dispatch) {
		this.target = checkNotNull(target, "target");
		this.dispatch = checkNotNull(dispatch, "dispatch");
	}

	@Override
	public Object invoke(final Object proxy, final Method method, final Object[] args) throws Throwable {
		/**
		 * 对于equals, hashCode, toString特殊处理
		 */
		if ("equals".equals(method.getName())) {
			try {
				Object otherHandler = args.length > 0 && args[0] != null
						? Proxy.getInvocationHandler(args[0])
						: null;
				return equals(otherHandler);
			}
			catch (IllegalArgumentException e) {
				return false;
			}
		}
		else if ("hashCode".equals(method.getName())) {
			return hashCode();
		}
		else if ("toString".equals(method.getName())) {
			return toString();
		}

		Object result;
		// 获取接口方法对应的方法处理器
		MethodHandler methodHandler = this.dispatch.get(method);
		// 仅能够被HardCodedTarget处理
		if (target instanceof Target.HardCodedTarget hardCodedTarget) {
			// 获取方法元信息，ClassFullName + Feign#configKey
			MethodMetadata methodMetadata = SentinelContractHolder.METADATA_MAP.get(hardCodedTarget.type().getName() + Feign.configKey(hardCodedTarget.type(), method));
			if (methodMetadata == null) {
				// 元信息不存在，直接发起远程调用
				result = methodHandler.invoke(args);
			}
			else {
				// 资源默认为 HttpMethod:protocol://url
				// 资源就是Sentinel要保护的东西，也是其核心概念之一
				String resourceName = methodMetadata.template().method().toUpperCase() + ":" + hardCodedTarget.url() + methodMetadata.template().path();
				Entry entry = null;
				try {
					ContextUtil.enter(resourceName);
					// 将要进行流量控制的资源保护起来，
					entry = SphU.entry(resourceName, EntryType.OUT, 1, args);
					// 方法调用
					result = methodHandler.invoke(args);
				}
				catch (Throwable ex) {
					// fallback handle
					if (!BlockException.isBlockException(ex)) {
						// 对于非流控代码，进行跟踪
						Tracer.traceEntry(ex, entry);
					}
					// 处理被流控的代码，当抛出BlockException，代表触发流控了
					if (fallbackFactory != null) {
						try {
							Object fallbackResult = fallbackMethodMap.get(method)
									.invoke(fallbackFactory.create(ex), args);
							return fallbackResult;
						}
						catch (IllegalAccessException e) {
							// shouldn't happen as method is public due to being an
							// interface
							throw new AssertionError(e);
						}
						catch (InvocationTargetException e) {
							throw e.getCause();
						}
					}
					else {
						// throw exception if fallbackFactory is null
						throw ex;
					}
				}
				finally {
					if (entry != null) {
						// 退出资源控制
						entry.exit(1, args);
					}
					ContextUtil.exit();
				}
			}
		}
		else {
			// other target type using default strategy
			result = methodHandler.invoke(args);
		}

		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (obj instanceof SentinelInvocationHandler sentinelInvocationHandler) {
			return target.equals(sentinelInvocationHandler.target);
		}
		return false;
	}

	@Override
	public int hashCode() {
		return target.hashCode();
	}

	@Override
	public String toString() {
		return target.toString();
	}

	static Map<Method, Method> toFallbackMethod(Map<Method, MethodHandler> dispatch) {
		Map<Method, Method> result = new LinkedHashMap<>();
		for (Method method : dispatch.keySet()) {
			method.setAccessible(true);
			result.put(method, method);
		}
		return result;
	}

}
