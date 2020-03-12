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
package org.apache.ibatis.cache.decorators;

import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.ibatis.cache.Cache;

/**
 * Lru (least recently used) cache decorator.
 *
 * @author Clinton Begin
 */
public class LruCache implements Cache {

  private final Cache delegate;   //被装饰的底层Cache对象
  // LinkedHashMap<Object, object>类型对象，它是一一个有序的HashMap,用于记录key最近的使用情况
  private Map<Object, Object> keyMap;
  private Object eldestKey; //记录最少被使用的缓存项的key

  public LruCache(Cache delegate) {
    this.delegate = delegate;
    setSize(1024);
  }

  @Override
  public String getId() {
    return delegate.getId();
  }

  @Override
  public int getSize() {
    return delegate.getSize();
  }

  public void setSize(final int size) { //重新设置缓存大小时，会重置keyMap字段
    //注意LinkedHashMap构造函数的第三个参数，true表示该LinkedHashMap记录的顺序是
    // access-order, 也就是说LinkedHashMap.get()方法会改变其记录的顺序
    keyMap = new LinkedHashMap<Object, Object>(size, .75F, true) {
      private static final long serialVersionUID = 4267176411845948333L;
      //当调用LinkedHashMap.put()方法时， 会调用该方法;
      @Override
      protected boolean removeEldestEntry(Map.Entry<Object, Object> eldest) {
        boolean tooBig = size() > size;
        if (tooBig) { //如果已到达缓存上限，则更新eldestKey字段，后面会删除该项
          eldestKey = eldest.getKey();
        }
        return tooBig;
      }
    };
  }

  @Override
  public void putObject(Object key, Object value) {
    delegate.putObject(key, value); //添加缓存项
    cycleKeyList(key);        //剔除最久未使用的缓存项
  }

  @Override
  public Object getObject(Object key) {
    keyMap.get(key);  //修改LinkedHashMap中记录的顺序
    return delegate.getObject(key); //剔除最久未使用的缓存项
  }

  @Override
  public Object removeObject(Object key) {
    return delegate.removeObject(key);
  }

  @Override
  public void clear() {
    delegate.clear();
    keyMap.clear();
  }

  private void cycleKeyList(Object key) {
    keyMap.put(key, key);
    if (eldestKey != null) {  //eldestKey不为空，标识已经达到缓存上限
      delegate.removeObject(eldestKey); //剔除最久未使用的缓存项
      eldestKey = null;
    }
  }

}
