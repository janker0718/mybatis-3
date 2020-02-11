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
package org.apache.ibatis.binding;

import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.apache.ibatis.annotations.Flush;
import org.apache.ibatis.annotations.MapKey;
import org.apache.ibatis.cursor.Cursor;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.SqlCommandType;
import org.apache.ibatis.mapping.StatementType;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.reflection.ParamNameResolver;
import org.apache.ibatis.reflection.TypeParameterResolver;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.apache.ibatis.session.SqlSession;

/**
 * @author Clinton Begin
 * @author Eduardo Macarron
 * @author Lasse Voss
 * @author Kazuki Shimizu
 */
public class MapperMethod {

  private final SqlCommand command; //记录了SQL语句的名称和类型
  private final MethodSignature method; //Mapper接口中对应方法的相关信息

  public MapperMethod(Class<?> mapperInterface, Method method, Configuration config) {
    this.command = new SqlCommand(config, mapperInterface, method);
    this.method = new MethodSignature(config, mapperInterface, method);
  }

  public Object execute(SqlSession sqlSession, Object[] args) {
    Object result;
    switch (command.getType()) { //根据SQL语句的类型调用SqlSession对应的方法
      case INSERT: {
        //使用ParamNameResolver处理args[]数组(用户传入的实参列表)。将用户传入的实参与
        // 指定参数名称关联起来
        Object param = method.convertArgsToSqlCommandParam(args);
        //下面是为参数创建"param+索引"格式的默认参数名称，例如: paraml, param2 等，并添加
        //到param集合中
        result = rowCountResult(sqlSession.insert(command.getName(), param));
        break;
      }
      case UPDATE: {
        // UPDATE和DELETE类型的sQL语句的处理与INSERT类型的SQL语句类似，唯-的区别是调用了
        // Sqlsession 的update()方法和delete()方法
        Object param = method.convertArgsToSqlCommandParam(args);
        result = rowCountResult(sqlSession.update(command.getName(), param));
        break;
      }
      case DELETE: {
        Object param = method.convertArgsToSqlCommandParam(args);
        result = rowCountResult(sqlSession.delete(command.getName(), param));
        break;
      }
      case SELECT:
        //处理返回值为void且ResultSet通过ResultHandler处理的方法
        if (method.returnsVoid() && method.hasResultHandler()) {
          executeWithResultHandler(sqlSession, args);
          result = null;
        } else if (method.returnsMany()) {  //处理返回值类型为集合或数组的方法
          result = executeForMany(sqlSession, args);
        } else if (method.returnsMap()) {   //处理返回值类型为Map的方法
          result = executeForMap(sqlSession, args);
        } else if (method.returnsCursor()) {   //处理返回值类型为Cursor的方法
          result = executeForCursor(sqlSession, args);
        } else {  //处理返回值为单一对象的方法
          Object param = method.convertArgsToSqlCommandParam(args);
          result = sqlSession.selectOne(command.getName(), param);
          if (method.returnsOptional()
              && (result == null || !method.getReturnType().equals(result.getClass()))) {
            result = Optional.ofNullable(result);
          }
        }
        break;
      case FLUSH:
        result = sqlSession.flushStatements();
        break;
      default:
        throw new BindingException("Unknown execution method for: " + command.getName());
    }
    if (result == null && method.getReturnType().isPrimitive() && !method.returnsVoid()) {
      throw new BindingException("Mapper method '" + command.getName()
          + " attempted to return null from a method with a primitive return type (" + method.getReturnType() + ").");
    }
    return result;
  }

  private Object rowCountResult(int rowCount) {
    final Object result;
    if (method.returnsVoid()) {   //Mapper接口中相应方法的返回值为void
      result = null;
    } else if (Integer.class.equals(method.getReturnType()) || Integer.TYPE.equals(method.getReturnType())) { //Mapper接口相应方法的返回值为int或者Integer
      result = rowCount;
    } else if (Long.class.equals(method.getReturnType()) || Long.TYPE.equals(method.getReturnType())) { //Mapper接口相应方法的返回值Long或者long
      result = (long)rowCount;
    } else if (Boolean.class.equals(method.getReturnType()) || Boolean.TYPE.equals(method.getReturnType())) { //Mapper接口相应方法的返回值boolean或者Boolean
      result = rowCount > 0;
    } else {
      throw new BindingException("Mapper method '" + command.getName() + "' has an unsupported return type: " + method.getReturnType());
    }
    return result;
  }

  private void executeWithResultHandler(SqlSession sqlSession, Object[] args) {
    //获取sQL语句对应的Mappedstatement对象，MappedStatement申记录了SQL语句相关信息，
    //后面详细描述
    MappedStatement ms = sqlSession.getConfiguration().getMappedStatement(command.getName());
    //当使用ResultHandler处理结果集时，必须指定ResultMap或ResultType
    if (!StatementType.CALLABLE.equals(ms.getStatementType())
        && void.class.equals(ms.getResultMaps().get(0).getType())) {
      throw new BindingException("method " + command.getName()
          + " needs either a @ResultMap annotation, a @ResultType annotation,"
          + " or a resultType attribute in XML so a ResultHandler can be used as a parameter.");
    }
    //转换实参列表
    Object param = method.convertArgsToSqlCommandParam(args);
    if (method.hasRowBounds()) { //检测参数列表中是否有RowBounds类型的参数
      //获取RowBounds对象，根锯Methodsignature.rowBoundsIndex字段指定位置，从args数组中
      //查找。获取ResultHandler对象的原理相同
      RowBounds rowBounds = method.extractRowBounds(args);
      //调用sqlsession.select()方法， 执行查询，并由指定的ResultHandler处理结果对象
      sqlSession.select(command.getName(), param, rowBounds, method.extractResultHandler(args));
    } else {
      sqlSession.select(command.getName(), param, method.extractResultHandler(args));
    }
  }

  private <E> Object executeForMany(SqlSession sqlSession, Object[] args) {
    List<E> result;
    Object param = method.convertArgsToSqlCommandParam(args); //参数列表转换
    if (method.hasRowBounds()) {    //检测是否指定了RowBounds参数
      RowBounds rowBounds = method.extractRowBounds(args);
      //调用sqlSession.selectList()方法完成查询
      result = sqlSession.selectList(command.getName(), param, rowBounds);
    } else {
      result = sqlSession.selectList(command.getName(), param);
    }
    // issue #510 Collections & arrays support
    //将集合装换为数组或者Collection集合
    if (!method.getReturnType().isAssignableFrom(result.getClass())) {
      if (method.getReturnType().isArray()) {
        return convertToArray(result);
      } else {
        return convertToDeclaredCollection(sqlSession.getConfiguration(), result);
      }
    }
    return result;
  }

  private <T> Cursor<T> executeForCursor(SqlSession sqlSession, Object[] args) {
    Cursor<T> result;
    Object param = method.convertArgsToSqlCommandParam(args);
    if (method.hasRowBounds()) {
      RowBounds rowBounds = method.extractRowBounds(args);
      result = sqlSession.selectCursor(command.getName(), param, rowBounds);
    } else {
      result = sqlSession.selectCursor(command.getName(), param);
    }
    return result;
  }

  private <E> Object convertToDeclaredCollection(Configuration config, List<E> list) {
    //使用前面介绍的objectFactory,通过反射方式创建集合对象
    Object collection = config.getObjectFactory().create(method.getReturnType());
    //创建MetaObject对象
    MetaObject metaObject = config.newMetaObject(collection);
    metaObject.addAll(list);  //实际上调用Collection.addAll()方法
    return collection;
  }

  @SuppressWarnings("unchecked")
  //convertToArray()方法实现如下
  private <E> Object convertToArray(List<E> list) {
    //获取数组元素类型
    Class<?> arrayComponentType = method.getReturnType().getComponentType();
    //创建数组对象
    Object array = Array.newInstance(arrayComponentType, list.size());
    if (arrayComponentType.isPrimitive()) { //将list中每一项都添加到数组中
      for (int i = 0; i < list.size(); i++) {
        Array.set(array, i, list.get(i));
      }
      return array;
    } else {
      return list.toArray((E[])array);
    }
  }

  private <K, V> Map<K, V> executeForMap(SqlSession sqlSession, Object[] args) {
    Map<K, V> result;
    Object param = method.convertArgsToSqlCommandParam(args);   //转换实参列表
    if (method.hasRowBounds()) {
      RowBounds rowBounds = method.extractRowBounds(args);
      //调用sqlSession.selectMap()方法完成查询操作
      result = sqlSession.selectMap(command.getName(), param, method.getMapKey(), rowBounds);
    } else {
      result = sqlSession.selectMap(command.getName(), param, method.getMapKey());
    }
    return result;
  }

  public static class ParamMap<V> extends HashMap<String, V> {

    private static final long serialVersionUID = -2212268410512043556L;

    @Override
    public V get(Object key) {
      if (!super.containsKey(key)) {
        throw new BindingException("Parameter '" + key + "' not found. Available parameters are " + keySet());
      }
      return super.get(key);
    }

  }

  public static class SqlCommand {

    private final String name;
    private final SqlCommandType type;

    public SqlCommand(Configuration configuration, Class<?> mapperInterface, Method method) {
      //获取方法名
      final String methodName = method.getName();
      final Class<?> declaringClass = method.getDeclaringClass();
      //从Configuration. mappedStatements集合中查找对应的MappedStatement对象
      MappedStatement ms = resolveMappedStatement(mapperInterface, methodName, declaringClass,
          configuration);
      if (ms == null) {
        //处理@Flush注解
        if (method.getAnnotation(Flush.class) != null) {
          name = null;
          type = SqlCommandType.FLUSH;
        } else {
          throw new BindingException("Invalid bound statement (not found): "
              + mapperInterface.getName() + "." + methodName);
        }
      } else {
        //初始化name和type
        name = ms.getId();
        type = ms.getSqlCommandType();
        if (type == SqlCommandType.UNKNOWN) {
          throw new BindingException("Unknown execution method for: " + name);
        }
      }
    }

    public String getName() {
      return name;
    }

    public SqlCommandType getType() {
      return type;
    }

    private MappedStatement resolveMappedStatement(Class<?> mapperInterface, String methodName,
        Class<?> declaringClass, Configuration configuration) {
      // SQL 语句的名称是由Mapper接口的名称与对应的方法名称组成的
      String statementId = mapperInterface.getName() + "." + methodName;
      if (configuration.hasStatement(statementId)) {  //检测是否有该名称的SQL语句
        //从Configuration. mappedStatements集合中查找对应的MappedStatement对象，
        // MappedStatement 对象中封装了SQL语句相关的信息，在MyBatis初始化时创建，后面详细描述
        return configuration.getMappedStatement(statementId);
      } else if (mapperInterface.equals(declaringClass)) {
        return null;
      }
      //如果指定方法是在父接口中定义的，则在此进行继承结构的处理
      //采用递归方式 从Configuration. mappedStatements集合中查找对应的MappedStatement对象，
      for (Class<?> superInterface : mapperInterface.getInterfaces()) {
        if (declaringClass.isAssignableFrom(superInterface)) {
          MappedStatement ms = resolveMappedStatement(superInterface, methodName,
              declaringClass, configuration);
          if (ms != null) {
            return ms;
          }
        }
      }
      return null;
    }
  }

  public static class MethodSignature {

    private final boolean returnsMany;   //返回值类型是否为Collection类型或是数组类型
    private final boolean returnsMap;     //返回值类型是否是Map类型
    private final boolean returnsVoid;    //返回值类型是否为Void
    private final boolean returnsCursor;  //返回值类型是否是cursor类型
    private final boolean returnsOptional;  //返回值类型是否是Optional类型
    private final Class<?> returnType;    //返回类型
    private final String mapKey;  // SQL 语句的名称是由Mapper接口的名称与对应的方法名称组成的
    private final Integer resultHandlerIndex; //用来标记该方法参数列表中ResultHandler类型参数的位置
    private final Integer rowBoundsIndex; //用来标记行边界的位置
    private final ParamNameResolver paramNameResolver;  //该方法对应的ParamNameResolver对象

    public MethodSignature(Configuration configuration, Class<?> mapperInterface, Method method) {
      //解析方法的返回值类型，前面已经介绍过TypeParameterResolver的实现，这里不再赘述
      Type resolvedReturnType = TypeParameterResolver.resolveReturnType(method, mapperInterface);
      if (resolvedReturnType instanceof Class<?>) {
        this.returnType = (Class<?>) resolvedReturnType;
      } else if (resolvedReturnType instanceof ParameterizedType) {
        this.returnType = (Class<?>) ((ParameterizedType) resolvedReturnType).getRawType();
      } else {
        this.returnType = method.getReturnType();
      }
      //初始化 returnsVoid, returnsMany. returnsCursor. mapKey. returnsMap 等字段
      this.returnsVoid = void.class.equals(this.returnType);
      this.returnsMany = configuration.getObjectFactory().isCollection(this.returnType) || this.returnType.isArray();
      this.returnsCursor = Cursor.class.equals(this.returnType);
      this.returnsOptional = Optional.class.equals(this.returnType);
      //若MethodSignature对应方法的返回值是Map且指定了eMapKey注解，则使用getMapKey()方法处理
      this.mapKey = getMapKey(method);
      this.returnsMap = this.mapKey != null;
      //初始化rowBoundsIndex和resultHandlerIndex字段
      this.rowBoundsIndex = getUniqueParamIndex(method, RowBounds.class);
      this.resultHandlerIndex = getUniqueParamIndex(method, ResultHandler.class);
      //创建ParamNameResolver对象
      this.paramNameResolver = new ParamNameResolver(configuration, method);
    }
    //负责将args[]数组(用户传入的实参列表)转换成sQL语句对应的参数列表，它是通过上面介绍的
    public Object convertArgsToSqlCommandParam(Object[] args) {
      return paramNameResolver.getNamedParams(args);
    }

    public boolean hasRowBounds() {
      return rowBoundsIndex != null;
    }

    public RowBounds extractRowBounds(Object[] args) {
      return hasRowBounds() ? (RowBounds) args[rowBoundsIndex] : null;
    }

    public boolean hasResultHandler() {
      return resultHandlerIndex != null;
    }

    public ResultHandler extractResultHandler(Object[] args) {
      return hasResultHandler() ? (ResultHandler) args[resultHandlerIndex] : null;
    }

    public String getMapKey() {
      return mapKey;
    }

    public Class<?> getReturnType() {
      return returnType;
    }

    public boolean returnsMany() {
      return returnsMany;
    }

    public boolean returnsMap() {
      return returnsMap;
    }

    public boolean returnsVoid() {
      return returnsVoid;
    }

    public boolean returnsCursor() {
      return returnsCursor;
    }

    /**
     * return whether return type is {@code java.util.Optional}.
     * @return return {@code true}, if return type is {@code java.util.Optional}
     * @since 3.5.0
     */
    public boolean returnsOptional() {
      return returnsOptional;
    }

    private Integer getUniqueParamIndex(Method method, Class<?> paramType) {
      Integer index = null;
      final Class<?>[] argTypes = method.getParameterTypes();
      for (int i = 0; i < argTypes.length; i++) {   //遍历MethodSignature对应方法的参数列表
        if (paramType.isAssignableFrom(argTypes[i])) {
          if (index == null) {      //记录paramType类型参数在参数列表中的位置索引
            index = i;
          } else {    // RowBounds和ResultHandler类型的参数只能有一个，不能重复出现
            throw new BindingException(method.getName() + " cannot have multiple " + paramType.getSimpleName() + " parameters");
          }
        }
      }
      return index;
    }

    private String getMapKey(Method method) {
      String mapKey = null;
      if (Map.class.isAssignableFrom(method.getReturnType())) {
        final MapKey mapKeyAnnotation = method.getAnnotation(MapKey.class);
        if (mapKeyAnnotation != null) {
          mapKey = mapKeyAnnotation.value();
        }
      }
      return mapKey;
    }
  }

}
