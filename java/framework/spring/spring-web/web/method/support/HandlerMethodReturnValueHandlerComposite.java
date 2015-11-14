/**
 * Handles method return values by delegating to a list of registered {@link HandlerMethodReturnValueHandler}s.
 * Previously resolved return types are cached for faster lookups.
 *
 * @author Rossen Stoyanchev
 * @since 3.1
 */
public class HandlerMethodReturnValueHandlerComposite implements HandlerMethodReturnValueHandler {

	protected final Log logger = LogFactory.getLog(getClass());

	private final List<HandlerMethodReturnValueHandler> returnValueHandlers =
		new ArrayList<HandlerMethodReturnValueHandler>();//返回值解析器


	/**
	 * Return a read-only list with the registered handlers, or an empty list.
	 */
	public List<HandlerMethodReturnValueHandler> getHandlers() {
		return Collections.unmodifiableList(this.returnValueHandlers);
	}

	/**
	 * Whether the given {@linkplain MethodParameter method return type} is supported by any registered
	 * {@link HandlerMethodReturnValueHandler}.
	 */
	@Override
	public boolean supportsReturnType(MethodParameter returnType) {
		return getReturnValueHandler(returnType) != null;
	}

	/**
	 * Iterate over registered {@link HandlerMethodReturnValueHandler}s and invoke the one that supports it.
	 * @throws IllegalStateException if no suitable {@link HandlerMethodReturnValueHandler} is found.
	 */
	@Override
	public void handleReturnValue(Object returnValue, MethodParameter returnType,
			ModelAndViewContainer mavContainer, NativeWebRequest webRequest) throws Exception {

		HandlerMethodReturnValueHandler handler = getReturnValueHandler(returnType);//获取合适的返回值解析器
		Assert.notNull(handler, "Unknown return value type [" + returnType.getParameterType().getName() + "]");
		handler.handleReturnValue(returnValue, returnType, mavContainer, webRequest);//使用参数解析器解析返回值
	}

	/**
	 * Find a registered {@link HandlerMethodReturnValueHandler} that supports the given return type.
	 */
	private HandlerMethodReturnValueHandler getReturnValueHandler(MethodParameter returnType) {
		//还是熟悉的遍历
		for (HandlerMethodReturnValueHandler returnValueHandler : returnValueHandlers) {
			if (logger.isTraceEnabled()) {
				logger.trace("Testing if return value handler [" + returnValueHandler + "] supports [" +
						returnType.getGenericParameterType() + "]");
			}
			if (returnValueHandler.supportsReturnType(returnType)) {
				return returnValueHandler;
			}
		}
		return null;
	}

	/**
	 * Add the given {@link HandlerMethodReturnValueHandler}.
	 */
	public HandlerMethodReturnValueHandlerComposite addHandler(HandlerMethodReturnValueHandler handler) {
		returnValueHandlers.add(handler);
		return this;
	}

	/**
	 * Add the given {@link HandlerMethodReturnValueHandler}s.
	 */
	public HandlerMethodReturnValueHandlerComposite addHandlers(List<? extends HandlerMethodReturnValueHandler> handlers) {
		if (handlers != null) {
			for (HandlerMethodReturnValueHandler handler : handlers) {
				this.returnValueHandlers.add(handler);
			}
		}
		return this;
	}

}
