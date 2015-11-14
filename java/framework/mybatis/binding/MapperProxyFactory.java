/**
 * @author Lasse Voss
 */
/**
 * ӳ����������
   רҵ����ӳ�����������
 */
public class MapperProxyFactory<T> {

  private final Class<T> mapperInterface;//ӳ�����ӿ�
  //�����Ѿ����ɵ�ӳ��������. ÿһ��ӳ��������һ��map,Ҳ�������������һ��ӳ���������õķ����Ļ���.
  //����MapperProxy֮�乲��,�Ż�����,����ÿ�ζ�����һ��MapperMethod����.
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
    //��JDK�Դ��Ķ�̬��������ӳ����
    return (T) Proxy.newProxyInstance(mapperInterface.getClassLoader(), new Class[] { mapperInterface }, mapperProxy);
  }

  public T newInstance(SqlSession sqlSession) {
    final MapperProxy<T> mapperProxy = new MapperProxy<T>(sqlSession, mapperInterface, methodCache);
    return newInstance(mapperProxy);
  }

}
