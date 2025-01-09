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

import java.time.Duration;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import com.alibaba.cloud.nacos.NacosDiscoveryProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.cloud.client.discovery.event.HeartbeatEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ApplicationEventPublisherAware;
import org.springframework.context.SmartLifecycle;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;


/**
 * Nacos服务发现心跳发布器，用于发布心跳事件
 *
 * @author yuhuangbin
 * @author ruansheng
 */
public class NacosDiscoveryHeartBeatPublisher implements ApplicationEventPublisherAware, SmartLifecycle {

	private static final Logger log = LoggerFactory.getLogger(NacosDiscoveryHeartBeatPublisher.class);

	/**
	 * Nacos服务发现属性，用于读取{@link NacosDiscoveryProperties#watchDelay}，设置调度任务的执行间隔
	 */
	private final NacosDiscoveryProperties nacosDiscoveryProperties;

	/**
	 * 实际执行调度任务的工具
	 */
	private final ThreadPoolTaskScheduler taskScheduler;

	/**
	 * 用于对{@link HeartbeatEvent}的发布次数进行计数，每发布一次，便会增加一次
	 */
	private final AtomicLong nacosHeartBeatIndex = new AtomicLong(0);

	/**
	 * 原子类型的标志位，标识该心跳发布器是否启动，由于该值可能存在多个线程访问和设置，因此使用原子来确保内存可见性
	 */
	private final AtomicBoolean running = new AtomicBoolean(false);

	/**
	 * Spring内置的本地事件发布器，使用其来发布Spring Cloud中的心跳事件
	 */
	private ApplicationEventPublisher publisher;

	/**
	 * 可取消的定时任务执行过程，支持对执行过程取消
	 */
	private ScheduledFuture<?> heartBeatFuture;

	public NacosDiscoveryHeartBeatPublisher(NacosDiscoveryProperties nacosDiscoveryProperties) {
		this.nacosDiscoveryProperties = nacosDiscoveryProperties;
		this.taskScheduler = getTaskScheduler();
	}

	private static ThreadPoolTaskScheduler getTaskScheduler() {
		/**
		 * 构造线程池任务调度器
		 */
		ThreadPoolTaskScheduler taskScheduler = new ThreadPoolTaskScheduler();
		taskScheduler.setBeanName("HeartBeat-Task-Scheduler");
		taskScheduler.initialize();
		return taskScheduler;
	}

	@Override
	public void start() {
		/**
		 * 基于cas操作更新，确保只有一个线程能够触发该操作
		 */
		if (this.running.compareAndSet(false, true)) {
			log.info("Start nacos heartBeat task scheduler.");
			/**
			 * 开始执行调度任务，任务内容为使用Spring的本地事件发布器发送{@link HeartbeatEvent}，间隔为{@link NacosDiscoveryProperties#watchDelay}，并将一个可取消的执行过程保存
			 */
			this.heartBeatFuture = this.taskScheduler.scheduleWithFixedDelay(this::publishHeartBeat, Duration.ofMillis(this.nacosDiscoveryProperties.getWatchDelay()));
		}
	}

	@Override
	public void stop() {
		/**
		 * 基于cas操作更新，确保只有一个线程能够触发该操作
		 */
		if (this.running.compareAndSet(true, false)) {
			if (this.heartBeatFuture != null) {
				// shutdown current user-thread,
				// then the other daemon-threads will terminate automatic.
				/**
				 * 停止调度器
				 */
				this.taskScheduler.shutdown();
				/**
				 * 取消正在执行的任务
				 */
				this.heartBeatFuture.cancel(true);
			}
		}
	}

	@Override
	public boolean isAutoStartup() {
		return true;
	}

	@Override
	public boolean isRunning() {
		return this.running.get();
	}

	@Override
	public void setApplicationEventPublisher(ApplicationEventPublisher applicationEventPublisher) {
		this.publisher = applicationEventPublisher;
	}

	/**
	 * nacos doesn't support watch now , publish an event every 30 seconds.
	 */
	public void publishHeartBeat() {
		/**
		 * 构造心跳事件，并携带累计发送的次数，该心跳事件是Spring Cloud中定义的
		 */
		HeartbeatEvent event = new HeartbeatEvent(this, nacosHeartBeatIndex.getAndIncrement());
		/**
		 * 使用Spring本地事件发布器发布事件
		 */
		this.publisher.publishEvent(event);
	}
}
