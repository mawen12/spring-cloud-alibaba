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

package com.alibaba.cloud.nacos.loadbalancer;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.alibaba.cloud.nacos.NacosDiscoveryProperties;
import com.alibaba.cloud.nacos.util.InetIPv6Utils;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.client.ConditionalOnBlockingDiscoveryEnabled;
import org.springframework.cloud.client.ConditionalOnDiscoveryEnabled;
import org.springframework.cloud.client.ConditionalOnReactiveDiscoveryEnabled;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.cloud.client.discovery.ReactiveDiscoveryClient;
import org.springframework.cloud.loadbalancer.core.ReactorLoadBalancer;
import org.springframework.cloud.loadbalancer.core.ServiceInstanceListSupplier;
import org.springframework.cloud.loadbalancer.support.LoadBalancerClientFactory;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;

/**
 * 基于Nacos的负载均衡客户端配置，该配置开启的条件如下：
 * <ul>
 *     <li>PROPERTIES(spring.cloud.loadbalancer.nacos.enabled)=true</li>
 *     <li>PROPERTIES(spring.cloud.nacos.discovery.enabled)=true</li>
 * </ul>
 * <p>
 * <p>
 * <p>
 * {@link ServiceInstanceListSupplier} don't use cache.<br>
 * <br>
 * 1. LoadBalancerCache causes information such as the weight of the service instance to
 * be changed without immediate effect.<br>
 * 2. Nacos itself supports caching.
 *
 * @author XuDaojie
 * @since 2021.1
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnLoadBalancerNacos
@ConditionalOnDiscoveryEnabled
public class NacosLoadBalancerClientConfiguration {

    private static final int REACTIVE_SERVICE_INSTANCE_SUPPLIER_ORDER = 183827465;

    @Bean
    @ConditionalOnMissingBean
    public ReactorLoadBalancer<ServiceInstance> nacosLoadBalancer(Environment environment,
                                                                  LoadBalancerClientFactory loadBalancerClientFactory,
                                                                  NacosDiscoveryProperties nacosDiscoveryProperties,
                                                                  InetIPv6Utils inetIPv6Utils,
                                                                  List<ServiceInstanceFilter> serviceInstanceFilters,
                                                                  List<LoadBalancerAlgorithm> loadBalancerAlgorithms) {
        /**
         * 从 PROPERTIES(loadbalancer.client.name) 读取负载均衡客户端名称
         */
        String name = environment.getProperty(LoadBalancerClientFactory.PROPERTY_NAME);
        /**
         * Map<服务名称, 负载均衡算法>
         */
        Map<String, LoadBalancerAlgorithm> loadBalancerAlgorithmMap = new HashMap<>();
        /**
         * 转换为Map
         */
        loadBalancerAlgorithms.forEach(loadBalancerAlgorithm -> {
            if (!loadBalancerAlgorithmMap.containsKey(loadBalancerAlgorithm.getServiceId())) {
                loadBalancerAlgorithmMap.put(loadBalancerAlgorithm.getServiceId(), loadBalancerAlgorithm);
            }
        });

        return new NacosLoadBalancer(
                loadBalancerClientFactory.getLazyProvider(name, ServiceInstanceListSupplier.class),
                name, nacosDiscoveryProperties, inetIPv6Utils,
                serviceInstanceFilters, loadBalancerAlgorithmMap);
    }

    /**
     * 注册异步非阻塞的配置，触发条件为：
     * <ul>
     *     <li>CLASS(org.springframework.web.reactive.function.client.WebClient)</li>
     *     <li>PROPERTIES(spring.cloud.discovery.reactive.enabled)=true</li>
     * </ul>
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnReactiveDiscoveryEnabled
    @Order(REACTIVE_SERVICE_INSTANCE_SUPPLIER_ORDER)
    public static class ReactiveSupportConfiguration {

        /**
         * 注册异步的服务实例列表获取器，基于默认的行为，该方法开启的条件如下：
         * <ul>
         *     <li>BEAN(ReactiveDiscoveryClient)</li>
         *     <li>PROPERTIES(spring.cloud.loadbalancer.configurations)=default -> DEFAULT(default)</li>
         * </ul>
         * <p>
         * 该方法上使用{@link ConditionalOnMissingBean},防止Bean的重复注册
         *
         * @param context
         * @return
         */
        @Bean
        @ConditionalOnBean(ReactiveDiscoveryClient.class)
        @ConditionalOnMissingBean
        @ConditionalOnProperty(value = "spring.cloud.loadbalancer.configurations", havingValue = "default", matchIfMissing = true)
        public ServiceInstanceListSupplier discoveryClientServiceInstanceListSupplier(
                ConfigurableApplicationContext context) {
            return ServiceInstanceListSupplier.builder().withDiscoveryClient().build(context);
        }

        /**
         * 注册异步的服务实例列表获取器，基于默认的行为，该方法开启的条件如下：
         * <ul>
         *     <li>BEAN(ReactiveDiscoveryClient)</li>
         *     <li>PROPERTIES(spring.cloud.loadbalancer.configurations)=zone-preference</li>
         * </ul>
         * <p>
         * 该方法上使用{@link ConditionalOnMissingBean},防止Bean的重复注册
         *
         * @param context
         * @return
         */
        @Bean
        @ConditionalOnBean(ReactiveDiscoveryClient.class)
        @ConditionalOnMissingBean
        @ConditionalOnProperty(value = "spring.cloud.loadbalancer.configurations", havingValue = "zone-preference")
        public ServiceInstanceListSupplier zonePreferenceDiscoveryClientServiceInstanceListSupplier(
                ConfigurableApplicationContext context) {
            return ServiceInstanceListSupplier.builder().withDiscoveryClient().withZonePreference().build(context);
        }

    }

    /**
     * 负责同步阻塞的配置，触发条件为：
     * <ul>
     *     <li>PROPERTIES(spring.cloud.discovery.blocking.enabled)=true</li>
     * </ul>
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnBlockingDiscoveryEnabled
    @Order(REACTIVE_SERVICE_INSTANCE_SUPPLIER_ORDER + 1)
    public static class BlockingSupportConfiguration {

        /**
         * 注册同步的服务实例列表获取器，基于默认的行为，该方法开启的条件如下：
         * <ul>
         *     <li>BEAN(DiscoveryClient)</li>
         *     <li>PROPERTIES(spring.cloud.loadbalancer.configurations)=default -> DEFAULT(default)</li>
         * </ul>
         * <p>
         * 该方法上使用{@link ConditionalOnMissingBean},防止Bean的重复注册
         *
         * @param context
         * @return
         */
        @Bean
        @ConditionalOnBean(DiscoveryClient.class)
        @ConditionalOnMissingBean
        @ConditionalOnProperty(value = "spring.cloud.loadbalancer.configurations", havingValue = "default", matchIfMissing = true)
        public ServiceInstanceListSupplier discoveryClientServiceInstanceListSupplier(ConfigurableApplicationContext context) {
            return ServiceInstanceListSupplier.builder().withBlockingDiscoveryClient().build(context);
        }

        /**
         * 注册同步的服务实例获取列表，基于区域偏好，该方法开启的条件如下：
         * <ul>
         *     <li>Bean(DiscoveryClient)</li>
         *     <li>PROPERTIES(spring.cloud.loadbalancer.configurations)=zone-preference</li>
         * </ul>
         * <p>
         * 该方法上使用{@link ConditionalOnMissingBean},防止Bean的重复注册
         *
         * @param context
         * @return
         */
        @Bean
        @ConditionalOnBean(DiscoveryClient.class)
        @ConditionalOnMissingBean
        @ConditionalOnProperty(value = "spring.cloud.loadbalancer.configurations", havingValue = "zone-preference")
        public ServiceInstanceListSupplier zonePreferenceDiscoveryClientServiceInstanceListSupplier(ConfigurableApplicationContext context) {
            return ServiceInstanceListSupplier.builder().withBlockingDiscoveryClient().withZonePreference().build(context);
        }
    }
}
