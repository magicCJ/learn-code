/*
 * Copyright 2002-2018 the original author or authors.
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

package org.springframework.beans.factory;

/**
 * 由{@link BeanFactory}设置完所有属性后需要进行响应的bean所实现的接口：例如执行自定义初始化或仅检查所有必需属性是否已设置。
 *
 * <p>An alternative to implementing {@code InitializingBean} is specifying a custom
 * init method, for example in an XML bean definition. For a list of all bean
 * lifecycle methods, see the {@link BeanFactory BeanFactory javadocs}.
 *
 * @author Rod Johnson
 * @author Juergen Hoeller
 * @see DisposableBean
 * @see org.springframework.beans.factory.config.BeanDefinition#getPropertyValues()
 * @see org.springframework.beans.factory.support.AbstractBeanDefinition#getInitMethodName()
 */
public interface InitializingBean {

	/**
	 * 由包含的{@code BeanFactory}设置所有bean属性并满足{@link BeanFactoryAware}，{@code ApplicationContextAware}等后调用。
	 * <p>此方法允许bean实例执行其总体配置的验证。设置所有bean属性后进行最后的初始化。  @throws在配置错误（例如无法设置*基本属性）或由于其他任何原因导致初始化失败的情况下发生的异常
	 */
	void afterPropertiesSet() throws Exception;

}
