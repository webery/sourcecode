/**
 * @author Clinton Begin
 */
public interface Interceptor {

  Object intercept(Invocation invocation) throws Throwable;//拦截方法,在这里处理拦截器的业务逻辑

  Object plugin(Object target);//把目标对象封装成Plugin对象

  void setProperties(Properties properties);

}
