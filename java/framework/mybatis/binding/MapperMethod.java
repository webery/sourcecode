/**
 * @author Clinton Begin
 * @author Eduardo Macarron
 * @author Lasse Voss
 */
/**
 * ӳ��������
 *
 */
public class MapperMethod {

  private final SqlCommand command;
  private final MethodSignature method;

  public MapperMethod(Class<?> mapperInterface, Method method, Configuration config) {
    this.command = new SqlCommand(config, mapperInterface, method);
    this.method = new MethodSignature(config, method);
  }

  //ִ��
  public Object execute(SqlSession sqlSession, Object[] args) {
    Object result;
    //4�������insert|update|delete|select���ֱ����SqlSession��4���෽��
    if (SqlCommandType.INSERT == command.getType()) {
      Object param = method.convertArgsToSqlCommandParam(args);
      result = rowCountResult(sqlSession.insert(command.getName(), param));
    } else if (SqlCommandType.UPDATE == command.getType()) {
      Object param = method.convertArgsToSqlCommandParam(args);
      result = rowCountResult(sqlSession.update(command.getName(), param));
    } else if (SqlCommandType.DELETE == command.getType()) {
      Object param = method.convertArgsToSqlCommandParam(args);
      result = rowCountResult(sqlSession.delete(command.getName(), param));
    } else if (SqlCommandType.SELECT == command.getType()) {
      if (method.returnsVoid() && method.hasResultHandler()) {
        //����н��������
        executeWithResultHandler(sqlSession, args);
        result = null;
      } else if (method.returnsMany()) {
        //�������ж�����¼
        result = executeForMany(sqlSession, args);
      } else if (method.returnsMap()) {
        //��������map
        result = executeForMap(sqlSession, args);
      } else {
        //�������һ����¼
        Object param = method.convertArgsToSqlCommandParam(args);
        result = sqlSession.selectOne(command.getName(), param);
      }
    } else {
      throw new BindingException("Unknown execution method for: " + command.getName());
    }
    if (result == null && method.getReturnType().isPrimitive() && !method.returnsVoid()) {
      throw new BindingException("Mapper method '" + command.getName() 
          + " attempted to return null from a method with a primitive return type (" + method.getReturnType() + ").");
    }
    return result;
  }

  //��������Է���ֵ�����ͽ�����һЩ��飬ʹ�ø���ȫ
  private Object rowCountResult(int rowCount) {
    final Object result;
    if (method.returnsVoid()) {
      result = null;
    } else if (Integer.class.equals(method.getReturnType()) || Integer.TYPE.equals(method.getReturnType())) {
      //�������ֵ�Ǵ�int��Сint
      result = Integer.valueOf(rowCount);
    } else if (Long.class.equals(method.getReturnType()) || Long.TYPE.equals(method.getReturnType())) {
      //�������ֵ�Ǵ�long��Сlong
      result = Long.valueOf(rowCount);
    } else if (Boolean.class.equals(method.getReturnType()) || Boolean.TYPE.equals(method.getReturnType())) {
      //�������ֵ�Ǵ�boolean��Сboolean
      result = Boolean.valueOf(rowCount > 0);
    } else {
      throw new BindingException("Mapper method '" + command.getName() + "' has an unsupported return type: " + method.getReturnType());
    }
    return result;
  }

  //���������
  private void executeWithResultHandler(SqlSession sqlSession, Object[] args) {
    MappedStatement ms = sqlSession.getConfiguration().getMappedStatement(command.getName());
    if (void.class.equals(ms.getResultMaps().get(0).getType())) {
      throw new BindingException("method " + command.getName() 
          + " needs either a @ResultMap annotation, a @ResultType annotation," 
          + " or a resultType attribute in XML so a ResultHandler can be used as a parameter.");
    }
    Object param = method.convertArgsToSqlCommandParam(args);
    if (method.hasRowBounds()) {
      RowBounds rowBounds = method.extractRowBounds(args);
      sqlSession.select(command.getName(), param, rowBounds, method.extractResultHandler(args));
    } else {
      sqlSession.select(command.getName(), param, method.extractResultHandler(args));
    }
  }

  //������¼
  private <E> Object executeForMany(SqlSession sqlSession, Object[] args) {
    List<E> result;
    Object param = method.convertArgsToSqlCommandParam(args);
    //����RowBounds
    if (method.hasRowBounds()) {
      RowBounds rowBounds = method.extractRowBounds(args);
      result = sqlSession.<E>selectList(command.getName(), param, rowBounds);
    } else {
      result = sqlSession.<E>selectList(command.getName(), param);
    }
    // issue #510 Collections & arrays support
    if (!method.getReturnType().isAssignableFrom(result.getClass())) {
      if (method.getReturnType().isArray()) {
        return convertToArray(result);
      } else {
        return convertToDeclaredCollection(sqlSession.getConfiguration(), result);
      }
    }
    return result;
  }

  private <E> Object convertToDeclaredCollection(Configuration config, List<E> list) {
    Object collection = config.getObjectFactory().create(method.getReturnType());
    MetaObject metaObject = config.newMetaObject(collection);
    metaObject.addAll(list);
    return collection;
  }

  @SuppressWarnings("unchecked")
  private <E> E[] convertToArray(List<E> list) {
    E[] array = (E[]) Array.newInstance(method.getReturnType().getComponentType(), list.size());
    array = list.toArray(array);
    return array;
  }

  private <K, V> Map<K, V> executeForMap(SqlSession sqlSession, Object[] args) {
    Map<K, V> result;
    Object param = method.convertArgsToSqlCommandParam(args);
    if (method.hasRowBounds()) {
      RowBounds rowBounds = method.extractRowBounds(args);
      result = sqlSession.<K, V>selectMap(command.getName(), param, method.getMapKey(), rowBounds);
    } else {
      result = sqlSession.<K, V>selectMap(command.getName(), param, method.getMapKey());
    }
    return result;
  }

  //����map����̬�ڲ���,���ϸ��get���������û����Ӧ��key������
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

  //SQL�����̬�ڲ���
  public static class SqlCommand {

    private final String name;
    private final SqlCommandType type;

    public SqlCommand(Configuration configuration, Class<?> mapperInterface, Method method) {
      String statementName = mapperInterface.getName() + "." + method.getName();
      MappedStatement ms = null;
      if (configuration.hasStatement(statementName)) {
        ms = configuration.getMappedStatement(statementName);
      } else if (!mapperInterface.equals(method.getDeclaringClass().getName())) { // issue #35
        //����������mapper�ӿڵķ�������ȥ�鸸��
        String parentStatementName = method.getDeclaringClass().getName() + "." + method.getName();
        if (configuration.hasStatement(parentStatementName)) {
          ms = configuration.getMappedStatement(parentStatementName);
        }
      }
      if (ms == null) {
        throw new BindingException("Invalid bound statement (not found): " + statementName);
      }
      name = ms.getId();
      type = ms.getSqlCommandType();
      if (type == SqlCommandType.UNKNOWN) {
        throw new BindingException("Unknown execution method for: " + name);
      }
    }

    public String getName() {
      return name;
    }

    public SqlCommandType getType() {
      return type;
    }
  }

  //����ǩ������̬�ڲ���
  public static class MethodSignature {

    private final boolean returnsMany;
    private final boolean returnsMap;
    private final boolean returnsVoid;
    private final Class<?> returnType;
    private final String mapKey;
    private final Integer resultHandlerIndex;
    private final Integer rowBoundsIndex;
    private final SortedMap<Integer, String> params;
    private final boolean hasNamedParameters;

    public MethodSignature(Configuration configuration, Method method) {
      this.returnType = method.getReturnType();
      this.returnsVoid = void.class.equals(this.returnType);
      this.returnsMany = (configuration.getObjectFactory().isCollection(this.returnType) || this.returnType.isArray());
      this.mapKey = getMapKey(method);
      this.returnsMap = (this.mapKey != null);
      this.hasNamedParameters = hasNamedParams(method);
      //�����ظ�ѭ��2�����getUniqueParamIndex���ǲ��ǽ���Ч����
      //����RowBounds�ǵڼ�������
      this.rowBoundsIndex = getUniqueParamIndex(method, RowBounds.class);
      //����ResultHandler�ǵڼ�������
      this.resultHandlerIndex = getUniqueParamIndex(method, ResultHandler.class);
      this.params = Collections.unmodifiableSortedMap(getParams(method, this.hasNamedParameters));
    }

    public Object convertArgsToSqlCommandParam(Object[] args) {
      final int paramCount = params.size();
      if (args == null || paramCount == 0) {
        //���û����
        return null;
      } else if (!hasNamedParameters && paramCount == 1) {
        //���ֻ��һ������
        return args[params.keySet().iterator().next().intValue()];
      } else {
        //���򣬷���һ��ParamMap���޸Ĳ�������������������λ��
        final Map<String, Object> param = new ParamMap<Object>();
        int i = 0;
        for (Map.Entry<Integer, String> entry : params.entrySet()) {
          //1.�ȼ�һ��#{0},#{1},#{2}...����
          param.put(entry.getValue(), args[entry.getKey().intValue()]);
          // issue #71, add param names as param1, param2...but ensure backward compatibility
          final String genericParamName = "param" + String.valueOf(i + 1);
          if (!param.containsKey(genericParamName)) {
            //2.�ټ�һ��#{param1},#{param2}...����
            //����Դ��ݶ��������һ��ӳ�����������������������, 
            //Ĭ����������ǽ����������ڲ����б��е�λ��������,����:#{param1},#{param2}�ȡ�
            //�������ı����������(ֻ�ڶ���������) ,��ô������ڲ�����ʹ��@Param(��paramName��)ע�⡣ 
            param.put(genericParamName, args[entry.getKey()]);
          }
          i++;
        }
        return param;
      }
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

    private Integer getUniqueParamIndex(Method method, Class<?> paramType) {
      Integer index = null;
      final Class<?>[] argTypes = method.getParameterTypes();
      for (int i = 0; i < argTypes.length; i++) {
        if (paramType.isAssignableFrom(argTypes[i])) {
          if (index == null) {
            index = i;
          } else {
            throw new BindingException(method.getName() + " cannot have multiple " + paramType.getSimpleName() + " parameters");
          }
        }
      }
      return index;
    }

    private String getMapKey(Method method) {
      String mapKey = null;
      if (Map.class.isAssignableFrom(method.getReturnType())) {
        //�������������map���͵ģ��鿴��method�Ƿ���MapKeyע�⡣��������ע�⣬�����ע���ֵ��Ϊmap��key
        final MapKey mapKeyAnnotation = method.getAnnotation(MapKey.class);
        if (mapKeyAnnotation != null) {
          mapKey = mapKeyAnnotation.value();
        }
      }
      return mapKey;
    }

    //�õ����в���
    private SortedMap<Integer, String> getParams(Method method, boolean hasNamedParameters) {
      //��һ��TreeMap,�����ͱ�֤���ǰ��������Ⱥ�˳��
      final SortedMap<Integer, String> params = new TreeMap<Integer, String>();
      final Class<?>[] argTypes = method.getParameterTypes();
      for (int i = 0; i < argTypes.length; i++) {
        //�Ƿ���RowBounds/ResultHandler���͵Ĳ���
        if (!RowBounds.class.isAssignableFrom(argTypes[i]) && !ResultHandler.class.isAssignableFrom(argTypes[i])) {
          //��������Ĭ��Ϊ0,1,2�������Ϊʲôxml���������#{1}������д������ʾ������
          String paramName = String.valueOf(params.size());
          if (hasNamedParameters) {
            //��������ע��@Param������������
            paramName = getParamNameFromAnnotation(method, i, paramName);
          }
          params.put(i, paramName);
        }
      }
      return params;
    }

    private String getParamNameFromAnnotation(Method method, int i, String paramName) {
      final Object[] paramAnnos = method.getParameterAnnotations()[i];
      for (Object paramAnno : paramAnnos) {
        if (paramAnno instanceof Param) {
          paramName = ((Param) paramAnno).value();
        }
      }
      return paramName;
    }

    private boolean hasNamedParams(Method method) {
      boolean hasNamedParams = false;
      final Object[][] paramAnnos = method.getParameterAnnotations();
      for (Object[] paramAnno : paramAnnos) {
        for (Object aParamAnno : paramAnno) {
          if (aParamAnno instanceof Param) {
            //����@Paramע��,һ�㲻����ע��ɣ����Ժ���
            hasNamedParams = true;
            break;
          }
        }
      }
      return hasNamedParams;
    }

  }

}
