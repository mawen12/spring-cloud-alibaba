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

package com.alibaba.cloud.nacos.discovery.actuate.health;

import com.alibaba.cloud.nacos.NacosServiceManager;
import com.alibaba.nacos.api.naming.NamingService;

import org.springframework.boot.actuate.health.AbstractHealthIndicator;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;

/**
 * 基于Nacos服务发现的健康指标，基于Spring Boot Actuator的{@link HealthIndicator}的特定实现
 *
 * @author <a href="mailto:mercyblitz@gmail.com">Mercy</a>
 * @see HealthIndicator
 * @since 2.2.0
 */
public class NacosDiscoveryHealthIndicator extends AbstractHealthIndicator {

    /**
     * status up.
     */
    private static final String STATUS_UP = "UP";

    /**
     * status down.
     */
    private static final String STATUS_DOWN = "DOWN";

    /**
     * Nacos服务管理器，提供实例注册、服务订阅、服务维护等功能
     */
    private NacosServiceManager nacosServiceManager;

    /**
     * 使用{@link NacosServiceManager} 替代
     */
    @Deprecated
    private NamingService namingService;

    public NacosDiscoveryHealthIndicator(NacosServiceManager nacosServiceManager) {
        this.nacosServiceManager = nacosServiceManager;
    }

    @Deprecated
    public NacosDiscoveryHealthIndicator(NamingService namingService) {
        this.namingService = namingService;
    }

    @Override
    protected void doHealthCheck(Health.Builder builder) throws Exception {
        // Just return "UP" or "DOWN"
        /**
         * 读取服务端状态，客户端与服务端正常通信，则是UP；否则是DOWN
         */
        String status = nacosServiceManager.getNamingService().getServerStatus();
        // Set the status to Builder
        /**
         * TODO by mawen 无需调用，因为以下方法也做了同样的事
         */
        builder.status(status);
        switch (status) {
            case STATUS_UP -> builder.up();
            case STATUS_DOWN -> builder.down();
            default -> builder.unknown();
        }
    }

}
