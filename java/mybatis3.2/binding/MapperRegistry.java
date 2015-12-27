/**
 * @author Clinton Begin
 * @author Eduardo Macarron
 * @author Lasse Voss
 */
/**
 * ӳ����ע����
 *
 */
public class MapperRegistry {

  private Configuration config;
  //�����Ѿ�ע�����Ϣ:key:ӳ�����ӿ�����;value:ӳ��������ӿڹ���
  //����ӳ����������,ʵ���Ͼ��൱�ڻ���ӳ��������,��Ϊʹ�ù�������ӳ��������
  private final Map<Class<?>, MapperProxyFactory<?>> knownMappers = new HashMap<Class<?>, MapperProxyFactory<?>>();

  public MapperRegistry(Configuration config) {
    this.config = config;
  }

  @SuppressWarnings("unchecked")
  //���ش�����
  public <T> T getMapper(Class<T> type, SqlSession sqlSession) {
    final MapperProxyFactory<T> mapperProxyFactory = (MapperProxyFactory<T>) knownMappers.get(type);
    if (mapperProxyFactory == null) {
      throw new BindingException("Type " + type + " is not known to the MapperRegistry.");
    }
    try {
      return mapperProxyFactory.newInstance(sqlSession);
    } catch (Exception e) {
      throw new BindingException("Error getting mapper instance. Cause: " + e, e);
    }
  }
  
  public <T> boolean hasMapper(Class<T> type) {
    return knownMappers.containsKey(type);
  }

  //���һ��ӳ��
  public <T> void addMapper(Class<T> type) {
    //mapper�����ǽӿ�
    if (type.isInterface()) {
      if (hasMapper(type)) {
        //����ظ�����ˣ�����
        throw new BindingException("Type " + type + " is already known to the MapperRegistry.");
      }
      boolean loadCompleted = false;
      try {
        knownMappers.put(type, new MapperProxyFactory<T>(type));
        // It's important that the type is added before the parser is run
        // otherwise the binding may automatically be attempted by the
        // mapper parser. If the type is already known, it won't try.
        MapperAnnotationBuilder parser = new MapperAnnotationBuilder(config, type);
        parser.parse();
        loadCompleted = true;
      } finally {
        //������ع����г����쳣��Ҫ�ٽ����mapper��mybatis��ɾ��
        if (!loadCompleted) {
          knownMappers.remove(type);
        }
      }
    }
  }

  /**
   * @since 3.2.2
   */
  public Collection<Class<?>> getMappers() {
    return Collections.unmodifiableCollection(knownMappers.keySet());
  }

  /**
   * @since 3.2.2
   */
  public void addMappers(String packageName, Class<?> superType) {
    //���Ұ���������superType����
    ResolverUtil<Class<?>> resolverUtil = new ResolverUtil<Class<?>>();
    resolverUtil.find(new ResolverUtil.IsA(superType), packageName);
    Set<Class<? extends Class<?>>> mapperSet = resolverUtil.getClasses();
    for (Class<?> mapperClass : mapperSet) {
      addMapper(mapperClass);
    }
  }

  /**
   * @since 3.2.2
   */
  //���Ұ���������
  public void addMappers(String packageName) {
    addMappers(packageName, Object.class);
  }
  
}
