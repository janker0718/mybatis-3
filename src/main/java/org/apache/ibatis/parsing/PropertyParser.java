/**
 *    Copyright 2009-2016 the original author or authors.
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */
package org.apache.ibatis.parsing;

import java.util.Properties;

/**
 * @author Clinton Begin
 * @author Kazuki Shimizu
 */
public class PropertyParser {

  private static final String KEY_PREFIX = "org.apache.ibatis.parsing.PropertyParser.";

  //在 mybatis-config.xml 中<properties>节点下配置是否开启默认值功能的对应配置项
  public static final String KEY_ENABLE_DEFAULT_VALUE = KEY_PREFIX + "enable-default-value";

  //配置占位符与默认值之间的默认分隔符的对应配置项
  public static final String KEY_DEFAULT_VALUE_SEPARATOR = KEY_PREFIX + "default-value-separator";

  //默认情况下关闭默认值的功能
  private static final String ENABLE_DEFAULT_VALUE = "false";
  //默认分隔符是冒号
  private static final String DEFAULT_VALUE_SEPARATOR = ":";

  private PropertyParser() {
    // Prevent Instantiation
  }

  public static String parse(String string, Properties variables) {
    VariableTokenHandler handler = new VariableTokenHandler(variables);
    //创建 GenericTokenParser 解析器对象 并制定其占位符为 ${}
    GenericTokenParser parser = new GenericTokenParser("${", "}", handler);
    return parser.parse(string);
  }

  private static class  VariableTokenHandler implements TokenHandler {
    private final Properties variables;
    private final boolean enableDefaultValue;
    private final String defaultValueSeparator;

    private VariableTokenHandler(Properties variables) {
      this.variables = variables;
      this.enableDefaultValue = Boolean.parseBoolean(getPropertyValue(KEY_ENABLE_DEFAULT_VALUE, ENABLE_DEFAULT_VALUE));
      this.defaultValueSeparator = getPropertyValue(KEY_DEFAULT_VALUE_SEPARATOR, DEFAULT_VALUE_SEPARATOR);
    }

    private String getPropertyValue(String key, String defaultValue) {
      return (variables == null) ? defaultValue : variables.getProperty(key, defaultValue);
    }

    @Override
    public String handleToken(String content) {
      // 检测 variables 集合是否为空
      if (variables != null) {
        String key = content;
        //检测是否支持占位符中使用默认值的功能
        if (enableDefaultValue) {
          // 查找分隔符
          final int separatorIndex = content.indexOf(defaultValueSeparator);
          String defaultValue = null;
          if (separatorIndex >= 0) {
            // 获取占位符的名称
            key = content.substring(0, separatorIndex);
            //获取默认值
            defaultValue = content.substring(separatorIndex + defaultValueSeparator.length());
          }
          if (defaultValue != null) {
            //在variable集合中查找指定占位符
            return variables.getProperty(key, defaultValue);
          }
        }
        // 不支持默认值的功能，直接查找variables集合
        if (variables.containsKey(key)) {
          return variables.getProperty(key);
        }
      }
      return "${" + content + "}"; //variables集合为空 直接返回
    }
  }

}
