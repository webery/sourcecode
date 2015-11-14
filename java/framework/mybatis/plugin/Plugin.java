/**
 * @author Clinton Begin
 */
//Plugin��JDK��̬������
public class Plugin implements InvocationHandler {

  private Object target;//Ŀ�����(ParameterHandler,ResultSetHandler,StatementHandler,Executor)
  private Interceptor interceptor;//�������������
  //Ŀ������Ҫ���صķ�������.��Ϊһ���������������ض����,һ����������ض������.
  //������Map + Set�����ݽṹ�洢
  private Map<Class<?>, Set<Method>> signatureMap;//����ÿ����������@signature��������Ϣ

  private Plugin(Object target, Interceptor interceptor, Map<Class<?>, Set<Method>> signatureMap) {
    this.target = target;
    this.interceptor = interceptor;
    this.signatureMap = signatureMap;
  }
  //��Ŀ��������������װ��Plugin������ʵ��.
  public static Object wrap(Object target, Interceptor interceptor) {
    Map<Class<?>, Set<Method>> signatureMap = getSignatureMap(interceptor);//��ȡ��������������Ϣ(��Ҫ���ص���ͷ���)
    Class<?> type = target.getClass();
    Class<?>[] interfaces = getAllInterfaces(type, signatureMap);//Proxy����ֻ�ܴ���ӿ�
    if (interfaces.length > 0) {
      return Proxy.newProxyInstance(
          type.getClassLoader(),
          interfaces,
          new Plugin(target, interceptor, signatureMap));//Plugin��Ϊ������,����ʵ��ҵ������Interceptor��������ɵ�.
    }
    return target;
  }

  @Override
   //proxy,�����Ķ���,����CachingExecutor����
  public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
    try {//��������뿴��,���������е�Executor����,��̬��ȥ�ж�������Ҫ��Ҫȥ����.����ҪС��ʹ��������,��Ӱ������.
      Set<Method> methods = signatureMap.get(method.getDeclaringClass());
      if (methods != null && methods.contains(method)) {
		//Invocation��Ŀ�����,Ŀ�������Ҫ���صķ���,�����ط����Ĳ����ķ�װ.
        return interceptor.intercept(new Invocation(target, method, args));//����������ʵ������
      }
      return method.invoke(target, args);//����Ҫ���صķ���ֱ�ӷ���
    } catch (Exception e) {
      throw ExceptionUtil.unwrapThrowable(e);
    }
  }

  private static Map<Class<?>, Set<Method>> getSignatureMap(Interceptor interceptor) {
    Intercepts interceptsAnnotation = interceptor.getClass().getAnnotation(Intercepts.class);//��ȡ������ע��@Signature
    // issue #251
    if (interceptsAnnotation == null) {
      throw new PluginException("No @Intercepts annotation was found in interceptor " + interceptor.getClass().getName());      
    }
    Signature[] sigs = interceptsAnnotation.value();//һ��Signature��ʾһ����������
	//������Ҫ���������Ϣ,class��Ϊkey, ��Ҫ������ķ�����Ϊvalue����Set����.һ����������������һ�����ж������
    Map<Class<?>, Set<Method>> signatureMap = new HashMap<Class<?>, Set<Method>>();
    for (Signature sig : sigs) {
      Set<Method> methods = signatureMap.get(sig.type());
      if (methods == null) {
        methods = new HashSet<Method>();
        signatureMap.put(sig.type(), methods);
      }
      try {
        Method method = sig.type().getMethod(sig.method(), sig.args());//��ȡ��Ҫ���صķ���
        methods.add(method);
      } catch (NoSuchMethodException e) {
        throw new PluginException("Could not find method on " + sig.type() + " named " + sig.method() + ". Cause: " + e, e);
      }
    }
    return signatureMap;
  }

  private static Class<?>[] getAllInterfaces(Class<?> type, Map<Class<?>, Set<Method>> signatureMap) {
    Set<Class<?>> interfaces = new HashSet<Class<?>>();
    while (type != null) {
      for (Class<?> c : type.getInterfaces()) {
        if (signatureMap.containsKey(c)) {
          interfaces.add(c);
        }
      }
      type = type.getSuperclass();
    }
    return interfaces.toArray(new Class<?>[interfaces.size()]);
  }

}
