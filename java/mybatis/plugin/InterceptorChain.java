/**
 * @author Clinton Begin
 */
//InterceptorChain里保存了所有的拦截器，它在mybatis初始化的时候创建。存在Configuration中
public class InterceptorChain {

  private final List<Interceptor> interceptors = new ArrayList<Interceptor>();

  //每一个拦截器对目标类都进行一次代理(也就是会出现代理的代理的代理.....有点拗口)
  public Object pluginAll(Object target) {
    for (Interceptor interceptor : interceptors) {
      target = interceptor.plugin(target);
    }
    return target;
  }

  public void addInterceptor(Interceptor interceptor) {
    interceptors.add(interceptor);
  }
  
  public List<Interceptor> getInterceptors() {
    return Collections.unmodifiableList(interceptors);
  }

}
