/**
 * @author Clinton Begin
 */
//InterceptorChain�ﱣ�������е�������������mybatis��ʼ����ʱ�򴴽�������Configuration��
public class InterceptorChain {

  private final List<Interceptor> interceptors = new ArrayList<Interceptor>();

  //ÿһ����������Ŀ���඼����һ�δ���(Ҳ���ǻ���ִ���Ĵ���Ĵ���.....�е��ֿ�)
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
