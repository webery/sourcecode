/**
 * @author Clinton Begin
 * @author Eduardo Macarron
 */
/**
 * 映射器代理，代理模式
 *
 */
public class MapperProxy<T> implements InvocationHandler, Serializable {

  private static final long serialVersionUID = -6424540398559729838L;
  private final SqlSession sqlSession;
  private final Class<T> mapperInterface;//映射器接口
  private final Map<Method, MapperMethod> methodCache;//映射器接口缓存

  public MapperProxy(SqlSession sqlSession, Class<T> mapperInterface, Map<Method, MapperMethod> methodCache) {
    this.sqlSession = sqlSession;
    this.mapperInterface = mapperInterface;
    this.methodCache = methodCache;
  }

  @Override
  public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
    //代理以后，所有Mapper的方法调用时，都会调用这个invoke方法
    //并不是任何一个方法都需要执行调用代理对象进行执行，如果这个方法是Object中通用的方法（toString、hashCode等）无需执行
    if (Object.class.equals(method.getDeclaringClass())) {
      try {
        return method.invoke(this, args);
      } catch (Throwable t) {
        throw ExceptionUtil.unwrapThrowable(t);
      }
    }
    //这里优化了，去缓存中找MapperMethod
    final MapperMethod mapperMethod = cachedMapperMethod(method);
    //执行
    return mapperMethod.execute(sqlSession, args);
  }

  //去缓存中找MapperMethod
  private MapperMethod cachedMapperMethod(Method method) {
    MapperMethod mapperMethod = methodCache.get(method);
    if (mapperMethod == null) {
      //缓存中不存在要使用的映射器方法,则新new一个,并放入缓存中.
      mapperMethod = new MapperMethod(mapperInterface, method, sqlSession.getConfiguration());
      methodCache.put(method, mapperMethod);
    }
    return mapperMethod;
  }

}
