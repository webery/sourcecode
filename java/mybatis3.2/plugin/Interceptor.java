/**
 * @author Clinton Begin
 * �������ӿ�
 */
public interface Interceptor {

  Object intercept(Invocation invocation) throws Throwable;//���ط���,�����ﴦ����������ҵ���߼�

  Object plugin(Object target);//��Ŀ������װ��Plugin����

  void setProperties(Properties properties);

}
