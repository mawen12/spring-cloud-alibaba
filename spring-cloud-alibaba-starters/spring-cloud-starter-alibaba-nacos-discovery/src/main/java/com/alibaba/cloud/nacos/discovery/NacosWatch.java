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

package com.alibaba.cloud.nacos.discovery;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import com.alibaba.cloud.nacos.NacosDiscoveryProperties;
import com.alibaba.cloud.nacos.NacosServiceManager;
import com.alibaba.nacos.api.naming.NamingService;
import com.alibaba.nacos.api.naming.listener.Event;
import com.alibaba.nacos.api.naming.listener.EventListener;
import com.alibaba.nacos.api.naming.listener.NamingEvent;
import com.alibaba.nacos.api.naming.pojo.Instance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.beans.factory.DisposableBean;
import org.springframework.context.SmartLifecycle;

/**
 * Nacos 观察器，将监听Nacos服务器上当前实例发生元信息变更时，更新{@link NacosDiscoveryProperties#metadata}
 *
 * @author xiaojing
 * @author yuhuangbin
 * @author pengfei.lu
 * @author ruansheng
 */
public class NacosWatch implements SmartLifecycle, DisposableBean {

	private static final Logger log = LoggerFactory.getLogger(NacosWatch.class);

	/**
	 * Map<service:group, 更新{@link NacosDiscoveryProperties#metadata}>
	 * TODO by mawen 虽然此处用了Map，但是记录实际上应该只有一条，因为其构建仅依赖于属性的信息，此处应该不会存在多条数据的场景
	 */
	private final Map<String, EventListener> listenerMap = new ConcurrentHashMap<>(16);

	/**
	 * 原子类型的标志位，标识该观察器是否启动，由于该值可能存在多个线程访问和设置，因此使用原子来确保内存可见性
	 */
	private final AtomicBoolean running = new AtomicBoolean(false);

	/**
	 * 用于从Nacos服务器读取服务和实例信息
	 */
	private final NacosServiceManager nacosServiceManager;

	/**
	 * 从属性中获取注册的实例信息，用于核对Nacos服务上推送的实例，并在元数据发生变更时，更新{@link NacosDiscoveryProperties#metadata}
	 */
	private final NacosDiscoveryProperties properties;

	public NacosWatch(NacosServiceManager nacosServiceManager, NacosDiscoveryProperties properties) {
		this.nacosServiceManager = nacosServiceManager;
		this.properties = properties;
	}

	/**
	 * 该观察器是自启动的
	 *
	 * @return
	 */
	@Override
	public boolean isAutoStartup() {
		return true;
	}

	/**
	 * 停止时更新标志位{@link #running=false}，并进行回调
	 *
	 * @param callback
	 */
	@Override
	public void stop(Runnable callback) {
		/**
		 * 原子更新{@link #running=false}
		 */
		this.stop();
		/**
		 * 触发回调
		 */
		callback.run();
	}

	/**
	 * Bean注册时启动
	 */
	@Override
	public void start() {
		/**
		 * 基于cas操作更新，确保只有一个线程能够触发该操作
		 */
		if (this.running.compareAndSet(false, true)) {
			/**
			 * 创建默认的时间监听器，并写入{@link #listenerMap}
			 */
			EventListener eventListener = listenerMap.computeIfAbsent(buildKey(),
					event -> new EventListener() {
						@Override
						public void onEvent(Event event) {
							/**
							 * 仅监听{@link NamingEvent}事件
							 */
							if (event instanceof NamingEvent namingEvent) {
								/**
								 * 获取服务端发送过来的已经过滤后的实例
								 */
								List<Instance> instances = namingEvent.getInstances();
								/**
								 * TODO by mawen 是否可以单独设置一个{@link com.alibaba.nacos.client.naming.selector.DefaultNamingSelector}来过滤对应的指定ip、port和cluster的实例
								 * 效果差不多，因此都是在客户端进行的过滤，所以不会对实例传输产生印象
								 * 获取当前注册的实例信息
								 */
								Optional<Instance> instanceOptional = selectCurrentInstance(instances);
								instanceOptional.ifPresent(currentInstance -> {
									/**
									 * 对比本地注册时的元信息，如果不一致，则更新
									 */
									resetIfNeeded(currentInstance);
								});
							}
						}
					});

			NamingService namingService = nacosServiceManager.getNamingService();
			try {
				/**
				 * 订阅当前服务的实例，并注册监听器
				 */
				namingService.subscribe(properties.getService(), properties.getGroup(), Arrays.asList(properties.getClusterName()), eventListener);
			}
			catch (Exception e) {
				log.error("namingService subscribe failed, properties:{}", properties, e);
			}

		}
	}

	/**
	 * 构造Map键，格式为service:group
	 * @return
	 */
	private String buildKey() {
		return String.join(":", properties.getService(), properties.getGroup());
	}

	private void resetIfNeeded(Instance instance) {
		/**
		 * 如果实例的元信息发生了变更，则本地同步更新
		 * 变更的场景有：
		 * <ul>
		 *     <li>当前实例变更了元信息，并推送到服务端</li>
		 *     <li>用户通过Nacos控制台修改了元信息</li>
		 * </ul>
		 */
		if (!properties.getMetadata().equals(instance.getMetadata())) {
			properties.setMetadata(instance.getMetadata());
		}
	}

	private Optional<Instance> selectCurrentInstance(List<Instance> instances) {
		return instances.stream()
				/**
				 * 过滤出当前注册的实例
				 */
				.filter(instance -> properties.getIp().equals(instance.getIp()) && properties.getPort() == instance.getPort())
				.findFirst();
	}

	@Override
	public void stop() {
		/**
		 * 基于cas操作更新，确保只有一个线程能够触发该操作
		 */
		if (this.running.compareAndSet(true, false)) {
			/**
			 * 获取当前的
			 */
			EventListener eventListener = listenerMap.get(buildKey());
			try {
				NamingService namingService = nacosServiceManager.getNamingService();
				namingService.unsubscribe(properties.getService(), properties.getGroup(),
						Arrays.asList(properties.getClusterName()), eventListener);
			}
			catch (Exception e) {
				log.error("namingService unsubscribe failed, properties:{}", properties,
						e);
			}
		}
	}

	@Override
	public boolean isRunning() {
		return this.running.get();
	}

	@Override
	public int getPhase() {
		return 0;
	}

	@Override
	public void destroy() {
		this.stop();
	}
}
