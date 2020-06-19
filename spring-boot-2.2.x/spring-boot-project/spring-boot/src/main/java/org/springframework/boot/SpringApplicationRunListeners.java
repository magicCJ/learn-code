/*
 * Copyright 2012-2019 the original author or authors.
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

package org.springframework.boot;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.apache.commons.logging.Log;

import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.util.ReflectionUtils;

/**
 * A collection of {@link SpringApplicationRunListener}.
 *
 * @author Phillip Webb
 */
class SpringApplicationRunListeners {

	private final Log log;

	//启动类监听器，从spring.factories中读取装配监听器默认有11个
	private final List<SpringApplicationRunListener> listeners;

	SpringApplicationRunListeners(Log log, Collection<? extends SpringApplicationRunListener> listeners) {
		this.log = log;
		this.listeners = new ArrayList<>(listeners);
	}

	//启动上下文事件监听
	void starting() {
		for (SpringApplicationRunListener listener : this.listeners) {
			listener.starting();
		}
	}

	//environment准备完毕事件监听
	void environmentPrepared(ConfigurableEnvironment environment) {
		for (SpringApplicationRunListener listener : this.listeners) {
			listener.environmentPrepared(environment);
		}
	}

	//spring上下文准备完毕事件监听
	void contextPrepared(ConfigurableApplicationContext context) {
		for (SpringApplicationRunListener listener : this.listeners) {
			listener.contextPrepared(context);
		}
	}
	//上下文配置类加载事件监听
	void contextLoaded(ConfigurableApplicationContext context) {
		for (SpringApplicationRunListener listener : this.listeners) {
			listener.contextLoaded(context);
		}
	}
	//上下文刷新调用事件
	void started(ConfigurableApplicationContext context) {
		for (SpringApplicationRunListener listener : this.listeners) {
			listener.started(context);
		}
	}
	//上下文刷新完成，在run方法执行完之前调用该事件
	void running(ConfigurableApplicationContext context) {
		for (SpringApplicationRunListener listener : this.listeners) {
			listener.running(context);
		}
	}
	//在运行过程中失败调起的事件
	void failed(ConfigurableApplicationContext context, Throwable exception) {
		for (SpringApplicationRunListener listener : this.listeners) {
			callFailedListener(listener, context, exception);
		}
	}


	private void callFailedListener(SpringApplicationRunListener listener, ConfigurableApplicationContext context,
			Throwable exception) {
		try {
			listener.failed(context, exception);
		}
		catch (Throwable ex) {
			if (exception == null) {
				ReflectionUtils.rethrowRuntimeException(ex);
			}
			if (this.log.isDebugEnabled()) {
				this.log.error("Error handling failed", ex);
			}
			else {
				String message = ex.getMessage();
				message = (message != null) ? message : "no error message";
				this.log.warn("Error handling failed (" + message + ")");
			}
		}
	}

}
