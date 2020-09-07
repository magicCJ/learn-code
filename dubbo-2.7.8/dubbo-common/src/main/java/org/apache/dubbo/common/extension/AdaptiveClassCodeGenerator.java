/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.dubbo.common.extension;

import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.logger.Logger;
import org.apache.dubbo.common.logger.LoggerFactory;
import org.apache.dubbo.common.utils.StringUtils;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Code generator for Adaptive class
 */
public class AdaptiveClassCodeGenerator {

    private static final Logger logger = LoggerFactory.getLogger(AdaptiveClassCodeGenerator.class);

    private static final String CLASSNAME_INVOCATION = "org.apache.dubbo.rpc.Invocation";

    private static final String CODE_PACKAGE = "package %s;\n";

    private static final String CODE_IMPORTS = "import %s;\n";

    private static final String CODE_CLASS_DECLARATION = "public class %s$Adaptive implements %s {\n";

    private static final String CODE_METHOD_DECLARATION = "public %s %s(%s) %s {\n%s}\n";

    private static final String CODE_METHOD_ARGUMENT = "%s arg%d";

    private static final String CODE_METHOD_THROWS = "throws %s";

    private static final String CODE_UNSUPPORTED = "throw new UnsupportedOperationException(\"The method %s of interface %s is not adaptive method!\");\n";

    private static final String CODE_URL_NULL_CHECK = "if (arg%d == null) throw new IllegalArgumentException(\"url == null\");\n%s url = arg%d;\n";

    private static final String CODE_EXT_NAME_ASSIGNMENT = "String extName = %s;\n";

    private static final String CODE_EXT_NAME_NULL_CHECK = "if(extName == null) "
                    + "throw new IllegalStateException(\"Failed to get extension (%s) name from url (\" + url.toString() + \") use keys(%s)\");\n";

    private static final String CODE_INVOCATION_ARGUMENT_NULL_CHECK = "if (arg%d == null) throw new IllegalArgumentException(\"invocation == null\"); "
                    + "String methodName = arg%d.getMethodName();\n";


    private static final String CODE_EXTENSION_ASSIGNMENT = "%s extension = (%<s)%s.getExtensionLoader(%s.class).getExtension(extName);\n";

    private static final String CODE_EXTENSION_METHOD_INVOKE_ARGUMENT = "arg%d";

    private final Class<?> type;

    private String defaultExtName;

    public AdaptiveClassCodeGenerator(Class<?> type, String defaultExtName) {
        this.type = type;
        this.defaultExtName = defaultExtName;
    }

    /**
     * test if given type has at least one method annotated with <code>Adaptive</code>
     */
    private boolean hasAdaptiveMethod() {
        return Arrays.stream(type.getMethods()).anyMatch(m -> m.isAnnotationPresent(Adaptive.class));
    }

    /**
     * generate and return class code
     */
    public String generate() {
        // no need to generate adaptive class since there's no adaptive method found.
        // 对于要生成自适应拓展的接口，要求该接口至少有一个方法被 Adaptive 注解修饰。若不满足此条件，就会抛出运行时异常
        if (!hasAdaptiveMethod()) {
            throw new IllegalStateException("No adaptive method exist on extension " + type.getName() + ", refuse to create the adaptive class!");
        }

        StringBuilder code = new StringBuilder();
        // 生成 package 代码：package + type 所在包
        code.append(generatePackageInfo());
        // 生成 import 代码：import + ExtensionLoader 全限定名
        code.append(generateImports());
        // 生成类代码：public class + type简单名称 + $Adaptive + implements + type全限定名 + {
        code.append(generateClassDeclaration());

        // ${生成方法}
        Method[] methods = type.getMethods();
        for (Method method : methods) {
            // 不会为没有标注 Adaptive 注解的方法生成代理逻辑，对于该种类型的方法，仅会生成一句抛出异常的代码
            code.append(generateMethod(method));
        }
        code.append("}");

        if (logger.isDebugEnabled()) {
            logger.debug(code.toString());
        }
        return code.toString();
    }

    /**
     * generate package info
     */
    private String generatePackageInfo() {
        return String.format(CODE_PACKAGE, type.getPackage().getName());
    }

    /**
     * generate imports
     */
    private String generateImports() {
        return String.format(CODE_IMPORTS, ExtensionLoader.class.getName());
    }

    /**
     * generate class declaration
     */
    private String generateClassDeclaration() {
        return String.format(CODE_CLASS_DECLARATION, type.getSimpleName(), type.getCanonicalName());
    }

    /**
     * generate method not annotated with Adaptive with throwing unsupported exception
     */
    private String generateUnsupported(Method method) {
        return String.format(CODE_UNSUPPORTED, method, type.getName());
    }

    /**
     * get index of parameter with type URL
     */
    private int getUrlTypeIndex(Method method) {
        int urlTypeIndex = -1;
        Class<?>[] pts = method.getParameterTypes();
        for (int i = 0; i < pts.length; ++i) {
            if (pts[i].equals(URL.class)) {
                urlTypeIndex = i;
                break;
            }
        }
        return urlTypeIndex;
    }

    /**
     * generate method declaration
     */
    private String generateMethod(Method method) {
        String methodReturnType = method.getReturnType().getCanonicalName();
        String methodName = method.getName();
        String methodContent = generateMethodContent(method);
        String methodArgs = generateMethodArguments(method);
        String methodThrows = generateMethodThrows(method);
        return String.format(CODE_METHOD_DECLARATION, methodReturnType, methodName, methodArgs, methodThrows, methodContent);
    }

    /**
     * generate method arguments
     */
    private String generateMethodArguments(Method method) {
        Class<?>[] pts = method.getParameterTypes();
        return IntStream.range(0, pts.length)
                        .mapToObj(i -> String.format(CODE_METHOD_ARGUMENT, pts[i].getCanonicalName(), i))
                        .collect(Collectors.joining(", "));
    }

    /**
     * generate method throws
     */
    private String generateMethodThrows(Method method) {
        Class<?>[] ets = method.getExceptionTypes();
        if (ets.length > 0) {
            String list = Arrays.stream(ets).map(Class::getCanonicalName).collect(Collectors.joining(", "));
            return String.format(CODE_METHOD_THROWS, list);
        } else {
            return "";
        }
    }

    /**
     * generate method URL argument null check
     */
    private String generateUrlNullCheck(int index) {
        return String.format(CODE_URL_NULL_CHECK, index, URL.class.getName(), index);
    }

    /**
     * generate method content
     */
    private String generateMethodContent(Method method) {
        Adaptive adaptiveAnnotation = method.getAnnotation(Adaptive.class);
        StringBuilder code = new StringBuilder(512);
        // 如果方法上无 Adaptive 注解，则生成 throw new UnsupportedOperationException(...) 代码
        if (adaptiveAnnotation == null) {
            // 生成的代码格式如下：
            // throw new UnsupportedOperationException(
            //     "method " + 方法签名 + of interface + 全限定接口名 + is not adaptive method!”)
            return generateUnsupported(method);
        } else {
            // 遍历参数列表，确定 URL 参数位置
            int urlTypeIndex = getUrlTypeIndex(method);

            // found parameter in URL type
            // urlTypeIndex != -1，表示参数列表中存在 URL 参数
            if (urlTypeIndex != -1) {
                // Null Point check
                // 为 URL 类型参数生成判空代码，格式如下：
                // if (arg + urlTypeIndex == null)
                //     throw new IllegalArgumentException("url == null");
                // 并为 URL 类型参数生成赋值代码，形如 URL url = arg1
                code.append(generateUrlNullCheck(urlTypeIndex));
            } else {
                // did not find parameter in URL type
                // 参数列表中不存在 URL 类型参数
                code.append(generateUrlAssignmentIndirectly(method));
            }

            // 如果@Adaptive注解没有给默认的属性名称，则使用当前类型（首字母小写）代替
            String[] value = getMethodAdaptiveValue(adaptiveAnnotation);

            // 检测方法列表中是否存在 Invocation 类型的参数
            boolean hasInvocation = hasInvocationArgument(method);

            // 若存在，则为其生成判空代码和其他一些代码
            code.append(generateInvocationArgumentNullCheck(method));

            code.append(generateExtNameAssignment(value, hasInvocation));
            // check extName == null?
            code.append(generateExtNameNullCheck(value));

            // 生成拓展获取代码，格式如下：
            // type全限定名 extension = (type全限定名)ExtensionLoader全限定名
            //     .getExtensionLoader(type全限定名.class).getExtension(extName);
            // Tips: 格式化字符串中的 %<s 表示使用前一个转换符所描述的参数，即 type 全限定名
            code.append(generateExtensionAssignment());

            // 生成返回值
            code.append(generateReturnAndInvocation(method));
        }

        return code.toString();
    }

    /**
     * generate code for variable extName null check
     */
    private String generateExtNameNullCheck(String[] value) {
        return String.format(CODE_EXT_NAME_NULL_CHECK, type.getName(), Arrays.toString(value));
    }

    /**
     * generate extName assigment code
     */
    private String generateExtNameAssignment(String[] value, boolean hasInvocation) {
        // TODO: refactor it
        String getNameCode = null;
        // 遍历 value，这里的 value 是 Adaptive 的注解值。此处循环目的是生成从 URL 中获取拓展名的代码，
        // 生成的代码会赋值给 getNameCode 变量。注意这个循环的遍历顺序是由后向前遍历的。
        for (int i = value.length - 1; i >= 0; --i) {
            if (i == value.length - 1) {
                if (null != defaultExtName) {
                    // protocol 是 url 的一部分，可通过 getProtocol 方法获取，其他的则是从
                    // URL 参数中获取。因为获取方式不同，所以这里要判断 value[i] 是否为 protocol
                    if (!"protocol".equals(value[i])) {
                        // hasInvocation 用于标识方法参数列表中是否有 Invocation 类型参数
                        if (hasInvocation) {
                            // 生成的代码功能等价于下面的代码：
                            //   url.getMethodParameter(methodName, value[i], defaultExtName)
                            // 以 LoadBalance 接口的 select 方法为例，最终生成的代码如下：
                            //   url.getMethodParameter(methodName, "loadbalance", "random")
                            getNameCode = String.format("url.getMethodParameter(methodName, \"%s\", \"%s\")", value[i], defaultExtName);
                        } else {
                            // 生成的代码功能等价于下面的代码：
                            //   url.getParameter(value[i], defaultExtName)
                            getNameCode = String.format("url.getParameter(\"%s\", \"%s\")", value[i], defaultExtName);
                        }
                    } else {
                        // 生成的代码功能等价于下面的代码：
                        //   ( url.getProtocol() == null ? defaultExtName : url.getProtocol() )
                        getNameCode = String.format("( url.getProtocol() == null ? \"%s\" : url.getProtocol() )", defaultExtName);
                    }
                } else {
                    if (!"protocol".equals(value[i])) {
                        if (hasInvocation) {
                            getNameCode = String.format("url.getMethodParameter(methodName, \"%s\", \"%s\")", value[i], defaultExtName);
                        } else {
                            // 生成的代码功能等价于下面的代码：
                            //   url.getParameter(value[i])
                            getNameCode = String.format("url.getParameter(\"%s\")", value[i]);
                        }
                    } else {
                        // 生成从 url 中获取协议的代码，比如 "dubbo"
                        getNameCode = "url.getProtocol()";
                    }
                }
            } else {
                if (!"protocol".equals(value[i])) {
                    if (hasInvocation) {
                        getNameCode = String.format("url.getMethodParameter(methodName, \"%s\", \"%s\")", value[i], defaultExtName);
                    } else {
                        // 生成的代码功能等价于下面的代码：
                        //   url.getParameter(value[i], getNameCode)
                        // 以 Transporter 接口的 connect 方法为例，最终生成的代码如下：
                        //   url.getParameter("client", url.getParameter("transporter", "netty"))
                        getNameCode = String.format("url.getParameter(\"%s\", %s)", value[i], getNameCode);
                    }
                } else {
                    // 生成的代码功能等价于下面的代码：
                    //   url.getProtocol() == null ? getNameCode : url.getProtocol()
                    // 以 Protocol 接口的 connect 方法为例，最终生成的代码如下：
                    //   url.getProtocol() == null ? "dubbo" : url.getProtocol()
                    getNameCode = String.format("url.getProtocol() == null ? (%s) : url.getProtocol()", getNameCode);
                }
            }
        }

        return String.format(CODE_EXT_NAME_ASSIGNMENT, getNameCode);
    }

    /**
     * @return
     */
    private String generateExtensionAssignment() {
        return String.format(CODE_EXTENSION_ASSIGNMENT, type.getName(), ExtensionLoader.class.getSimpleName(), type.getName());
    }

    /**
     * generate method invocation statement and return it if necessary
     */
    private String generateReturnAndInvocation(Method method) {
        // 如果方法返回值类型非 void，则生成 return 语句。
        String returnStatement = method.getReturnType().equals(void.class) ? "" : "return ";

        // 生成目标方法调用逻辑，格式为：
        //     extension.方法名(arg0, arg2, ..., argN);
        String args = IntStream.range(0, method.getParameters().length)
                .mapToObj(i -> String.format(CODE_EXTENSION_METHOD_INVOKE_ARGUMENT, i))
                .collect(Collectors.joining(", "));

        return returnStatement + String.format("extension.%s(%s);\n", method.getName(), args);
    }

    /**
     * test if method has argument of type <code>Invocation</code>
     */
    private boolean hasInvocationArgument(Method method) {
        Class<?>[] pts = method.getParameterTypes();
        return Arrays.stream(pts).anyMatch(p -> CLASSNAME_INVOCATION.equals(p.getName()));
    }

    /**
     * generate code to test argument of type <code>Invocation</code> is null
     */
    private String generateInvocationArgumentNullCheck(Method method) {
        Class<?>[] pts = method.getParameterTypes();
        return IntStream.range(0, pts.length).filter(i -> CLASSNAME_INVOCATION.equals(pts[i].getName()))
                        .mapToObj(i -> String.format(CODE_INVOCATION_ARGUMENT_NULL_CHECK, i, i))
                        .findFirst().orElse("");
    }

    /**
     * get value of adaptive annotation or if empty return splitted simple name
     */
    private String[] getMethodAdaptiveValue(Adaptive adaptiveAnnotation) {
        // 获取 @Adaptive 注解的属性值
        String[] value = adaptiveAnnotation.value();
        // value is not set, use the value generated from class name as the key
        if (value.length == 0) {
            // 获取类名，并将类名转换为字符数组
            String splitName = StringUtils.camelToSplitName(type.getSimpleName(), ".");
            value = new String[]{splitName};
        }
        return value;
    }

    /**
     * get parameter with type <code>URL</code> from method parameter:
     * <p>
     * test if parameter has method which returns type <code>URL</code>
     * <p>
     * if not found, throws IllegalStateException
     */
    private String generateUrlAssignmentIndirectly(Method method) {
        Class<?>[] pts = method.getParameterTypes();

        Map<String, Integer> getterReturnUrl = new HashMap<>();
        // find URL getter method
        // 遍历方法的参数类型列表
        for (int i = 0; i < pts.length; ++i) {
            // 获取某一类型参数的全部方法
            for (Method m : pts[i].getMethods()) {
                // 遍历方法列表，寻找可返回 URL 的 getter 方法
                String name = m.getName();
                // 1. 方法名以 get 开头，或方法名大于3个字符
                // 2. 方法的访问权限为 public
                // 3. 非静态方法
                // 4. 方法参数数量为0
                // 5. 方法返回值类型为 URL
                if ((name.startsWith("get") || name.length() > 3)
                        && Modifier.isPublic(m.getModifiers())
                        && !Modifier.isStatic(m.getModifiers())
                        && m.getParameterTypes().length == 0
                        && m.getReturnType() == URL.class) {
                    getterReturnUrl.put(name, i);
                }
            }
        }

        // 如果所有参数中均不包含可返回 URL 的 getter 方法，则抛出异常
        if (getterReturnUrl.size() <= 0) {
            // getter method not found, throw
            throw new IllegalStateException("Failed to create adaptive class for interface " + type.getName()
                    + ": not found url parameter or url attribute in parameters of method " + method.getName());
        }

        Integer index = getterReturnUrl.get("getUrl");
        if (index != null) {
            return generateGetUrlNullCheck(index, pts[index], "getUrl");
        } else {
            Map.Entry<String, Integer> entry = getterReturnUrl.entrySet().iterator().next();
            return generateGetUrlNullCheck(entry.getValue(), pts[entry.getValue()], entry.getKey());
        }
    }

    /**
     * 1, test if argi is null
     * 2, test if argi.getXX() returns null
     * 3, assign url with argi.getXX()
     */
    private String generateGetUrlNullCheck(int index, Class<?> type, String method) {
        // Null point check
        StringBuilder code = new StringBuilder();
        // 为 getter 方法返回的 URL 生成判空代码，格式如下：
        // if (argN.getter方法名() == null)
        //     throw new IllegalArgumentException(参数全限定名 + argument getUrl() == null);
        code.append(String.format("if (arg%d == null) throw new IllegalArgumentException(\"%s argument == null\");\n",
                index, type.getName()));
        // 生成赋值语句，格式如下：
        // URL全限定名 url = argN.getter方法名()，比如
        // com.alibaba.dubbo.common.URL url = invoker.getUrl();
        code.append(String.format("if (arg%d.%s() == null) throw new IllegalArgumentException(\"%s argument %s() == null\");\n",
                index, method, type.getName(), method));

        code.append(String.format("%s url = arg%d.%s();\n", URL.class.getName(), index, method));
        return code.toString();
    }

}
