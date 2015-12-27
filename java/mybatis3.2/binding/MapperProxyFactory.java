/**
 * @author Lasse Voss
 */
/**
 * 映射器代理工厂
   专业生产映射器代理对象
 */
public class MapperProxyFactory<T> {

  private final Class<T> mapperInterface;//映射器接口
  //保存已经生成的映射器方法. 每一个映射器共用一个map,也就是它保存的是一个映射器所调用的方法的缓存.
  //方便MapperProxy之间共用,优化性能,不用每次都生成一个MapperMethod对象.
  private Map<Method, MapperMethod> methodCache = new ConcurrentHashMap<Method, MapperMethod>();

  public MapperProxyFactory(Class<T> mapperInterface) {
    this.mapperInterface = mapperInterface;
  }

  public Class<T> getMapperInterface() {
    return mapperInterface;
  }

  public Map<Method, MapperMethod> getMethodCache() {
    return methodCache;
  }

  @SuppressWarnings("unchecked")
  protected T newInstance(MapperProxy<T> mapperProxy) {
    //用JDK自带的动态代理生成映射器
    return (T) Proxy.newProxyInstance(mapperInterface.getClassLoader(), new Class[] { mapperInterface }, mapperProxy);
  }

  public T newInstance(SqlSession sqlSession) {
    final MapperProxy<T> mapperProxy = new MapperProxy<T>(sqlSession, mapperInterface, methodCache);
    return newInstance(mapperProxy);
  }

}
