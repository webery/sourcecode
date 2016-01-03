/**
 * @author Clinton Begin
 */
//Plugin是JDK动态代理类
//Proxy代理对象.method -> Plugin.invoke -> interceptor.intercept -> Invocation.proceed -> ParameterHandler|ResultSetHandler|StatementHandler|Executor.method 
public class Plugin implements InvocationHandler {

  private Object target;//目标对象(ParameterHandler,ResultSetHandler,StatementHandler,Executor)
  private Interceptor interceptor;//被代理的拦截器
  //目标类需要拦截的方法缓存.因为一个拦截器可以拦截多个类,一个类可以拦截多个方法.
  //所以用Map + Set的数据结构存储
  private Map<Class<?>, Set<Method>> signatureMap;//保存每个拦截器的@signature的配置信息

  private Plugin(Object target, Interceptor interceptor, Map<Class<?>, Set<Method>> signatureMap) {
    this.target = target;
    this.interceptor = interceptor;
    this.signatureMap = signatureMap;
  }
  //把目标对象和拦截器封装成Plugin代理类实例.
  public static Object wrap(Object target, Interceptor interceptor) {
    Map<Class<?>, Set<Method>> signatureMap = getSignatureMap(interceptor);//获取拦截器的拦截信息(需要拦截的类和方法)
    Class<?> type = target.getClass();
    Class<?>[] interfaces = getAllInterfaces(type, signatureMap);//Proxy代理只能代理接口
    if (interfaces.length > 0) {
      return Proxy.newProxyInstance(
          type.getClassLoader(),
          interfaces,
          new Plugin(target, interceptor, signatureMap));//Plugin作为代理类,但是实际业务是由Interceptor拦截器完成的.
    }
    return target;
  }

  @Override
   //proxy,类代理的对象,例如CachingExecutor对象
  public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
    try {//从这里代码看到,会拦截所有的Executor方法,动态的去判断拦截器要不要去拦截.所以要小心使用拦截器,会影响性能.
      Set<Method> methods = signatureMap.get(method.getDeclaringClass());
      if (methods != null && methods.contains(method)) {
		//Invocation是目标对象,目标对象需要拦截的方法,我拦截方法的参数的封装.
        return interceptor.intercept(new Invocation(target, method, args));//调用拦截器实现拦截
      }
      return method.invoke(target, args);//不需要拦截的方法直接放行
    } catch (Exception e) {
      throw ExceptionUtil.unwrapThrowable(e);
    }
  }

  private static Map<Class<?>, Set<Method>> getSignatureMap(Interceptor interceptor) {
    Intercepts interceptsAnnotation = interceptor.getClass().getAnnotation(Intercepts.class);//获取拦截器注解@Signature
    // issue #251
    if (interceptsAnnotation == null) {
      throw new PluginException("No @Intercepts annotation was found in interceptor " + interceptor.getClass().getName());      
    }
    Signature[] sigs = interceptsAnnotation.value();//一个Signature表示一个拦截类型
	//保存需要拦截类的信息,class作为key, 需要拦截类的方法作为value集合Set保存.一个拦截器可以拦截一个类中多个方法
    Map<Class<?>, Set<Method>> signatureMap = new HashMap<Class<?>, Set<Method>>();
    for (Signature sig : sigs) {
      Set<Method> methods = signatureMap.get(sig.type());
      if (methods == null) {
        methods = new HashSet<Method>();
        signatureMap.put(sig.type(), methods);
      }
      try {
        Method method = sig.type().getMethod(sig.method(), sig.args());//获取需要拦截的方法
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
