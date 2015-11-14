/**
 * @author Clinton Begin
 * @author Eduardo Macarron
 */
/**
 * ӳ������������ģʽ
 *
 */
public class MapperProxy<T> implements InvocationHandler, Serializable {

  private static final long serialVersionUID = -6424540398559729838L;
  private final SqlSession sqlSession;
  private final Class<T> mapperInterface;//ӳ�����ӿ�
  private final Map<Method, MapperMethod> methodCache;//ӳ�����ӿڻ���

  public MapperProxy(SqlSession sqlSession, Class<T> mapperInterface, Map<Method, MapperMethod> methodCache) {
    this.sqlSession = sqlSession;
    this.mapperInterface = mapperInterface;
    this.methodCache = methodCache;
  }

  @Override
  public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
    //�����Ժ�����Mapper�ķ�������ʱ������������invoke����
    //�������κ�һ����������Ҫִ�е��ô���������ִ�У�������������Object��ͨ�õķ�����toString��hashCode�ȣ�����ִ��
    if (Object.class.equals(method.getDeclaringClass())) {
      try {
        return method.invoke(this, args);
      } catch (Throwable t) {
        throw ExceptionUtil.unwrapThrowable(t);
      }
    }
    //�����Ż��ˣ�ȥ��������MapperMethod
    final MapperMethod mapperMethod = cachedMapperMethod(method);
    //ִ��
    return mapperMethod.execute(sqlSession, args);
  }

  //ȥ��������MapperMethod
  private MapperMethod cachedMapperMethod(Method method) {
    MapperMethod mapperMethod = methodCache.get(method);
    if (mapperMethod == null) {
      //�����в�����Ҫʹ�õ�ӳ��������,����newһ��,�����뻺����.
      mapperMethod = new MapperMethod(mapperInterface, method, sqlSession.getConfiguration());
      methodCache.put(method, mapperMethod);
    }
    return mapperMethod;
  }

}
