/**
 *    Copyright 2009-2019 the original author or authors.
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
package org.apache.ibatis.reflection.wrapper;

import java.util.List;

import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.reflection.factory.ObjectFactory;
import org.apache.ibatis.reflection.property.PropertyTokenizer;

/**
 * @author Clinton Begin
 */
public interface ObjectWrapper {
  //如果是普通bean调用getter方法 如果是集合 则获取指定key或者下标对应的value值
  Object get(PropertyTokenizer prop);
  //如果是普通bean调用setter方法 如果是集合 则设置指定key或者下标对应的value值
  void set(PropertyTokenizer prop, Object value);
  //查找属性表达式指定的属性，第二个参数标识是否忽视属性表达式的下划线
  String findProperty(String name, boolean useCamelCaseMapping);

  //查找可读属性的名称集合
  String[] getGetterNames();
  //查找可写属性的名称集合
  String[] getSetterNames();

  //解析表达式指定属性的setter方法的参数类型
  Class<?> getSetterType(String name);
  //解析表达式指定属性的getter方法的参数类型
  Class<?> getGetterType(String name);

  //判断属性表达式指定属性是否有getter方法
  boolean hasSetter(String name);
  //判断属性表达式指定属性是否有setter方法
  boolean hasGetter(String name);

  //为属性表达式指定的属性创建相应的MetaObject对象
  MetaObject instantiatePropertyValue(String name, PropertyTokenizer prop, ObjectFactory objectFactory);

  //封装的对象是否为Collect类型
  boolean isCollection();

  //调用Collection对象的add()方法
  void add(Object element);
  //调用Collection对象的addAll()方法
  <E> void addAll(List<E> element);

}
