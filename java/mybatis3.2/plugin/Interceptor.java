/**
 * @author Clinton Begin
 * 拦截器接口
 */
public interface Interceptor {

  Object intercept(Invocation invocation) throws Throwable;//拦截方法,在这里处理拦截器的业务逻辑

  Object plugin(Object target);//把目标对象封装成Plugin对象

  void setProperties(Properties properties);

}
