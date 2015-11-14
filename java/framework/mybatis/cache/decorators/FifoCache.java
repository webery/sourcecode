/**
 * FIFO (first in, first out) cache decorator
 *
 * @author Clinton Begin
 */
//�Ƚ��ȳ�����
public class FifoCache implements Cache {

  private final Cache delegate;//����ģʽ
  private Deque<Object> keyList;//key����(���е��ص���,�Ƚ��ȳ�)
  private int size;

  public FifoCache(Cache delegate) {
    this.delegate = delegate;
    this.keyList = new LinkedList<Object>();
    this.size = 1024;
  }

  @Override
  public String getId() {
    return delegate.getId();
  }

  @Override
  public int getSize() {
    return delegate.getSize();
  }

  public void setSize(int size) {
    this.size = size;
  }

  @Override
  public void putObject(Object key, Object value) {
    cycleKeyList(key);
    delegate.putObject(key, value);
  }

  @Override
  public Object getObject(Object key) {
    return delegate.getObject(key);
  }

  @Override
  public Object removeObject(Object key) {
    return delegate.removeObject(key);
  }

  @Override
  public void clear() {
    delegate.clear();
    keyList.clear();
  }

  @Override
  public ReadWriteLock getReadWriteLock() {
    return null;
  }

  private void cycleKeyList(Object key) {
    keyList.addLast(key);
	//���������������޶���������ʱ��,��key���л�ȡ���������е�һ��key,Ȼ��ɾ��.
    if (keyList.size() > size) {
      Object oldestKey = keyList.removeFirst();
      delegate.removeObject(oldestKey);
    }
  }

}
