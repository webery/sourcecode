/**
 * Resolves method parameters by delegating to a list of registered {@link HandlerMethodArgumentResolver}s.
 * Previously resolved method parameters are cached for faster lookups.
 *
 * @author Rossen Stoyanchev
 * @since 3.1
 */
public class HandlerMethodArgumentResolverComposite implements HandlerMethodArgumentResolver {

	protected final Log logger = LogFactory.getLog(getClass());

	private final List<HandlerMethodArgumentResolver> argumentResolvers =
			new LinkedList<HandlerMethodArgumentResolver>();//多策略参数解析器

	private final Map<MethodParameter, HandlerMethodArgumentResolver> argumentResolverCache =
			new ConcurrentHashMap<MethodParameter, HandlerMethodArgumentResolver>(256);//解析器缓存,会把用过的参数作为可以,参数解析器作为value,提高效率


	/**
	 * Return a read-only list with the contained resolvers, or an empty list.
	 */
	public List<HandlerMethodArgumentResolver> getResolvers() {
		return Collections.unmodifiableList(this.argumentResolvers);
	}

	/**
	 * Whether the given {@linkplain MethodParameter method parameter} is supported by any registered
	 * {@link HandlerMethodArgumentResolver}.
	 */
	@Override
	public boolean supportsParameter(MethodParameter parameter) {
		return getArgumentResolver(parameter) != null;
	}

	/**
	 * Iterate over registered {@link HandlerMethodArgumentResolver}s and invoke the one that supports it.
	 * @throws IllegalStateException if no suitable {@link HandlerMethodArgumentResolver} is found.
	 */
	@Override
	public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
			NativeWebRequest webRequest, WebDataBinderFactory binderFactory) throws Exception {

		HandlerMethodArgumentResolver resolver = getArgumentResolver(parameter);//获取参数解析器
		Assert.notNull(resolver, "Unknown parameter type [" + parameter.getParameterType().getName() + "]");
		return resolver.resolveArgument(parameter, mavContainer, webRequest, binderFactory);//解析参数并返回(数据绑定和数据校验)
	}

	/**
	 * Find a registered {@link HandlerMethodArgumentResolver} that supports the given method parameter.
	 */
	//依然是熟悉的策略模式,和hm和ha一样,不同的参数有不同的参数解析器
	private HandlerMethodArgumentResolver getArgumentResolver(MethodParameter parameter) {
		HandlerMethodArgumentResolver result = this.argumentResolverCache.get(parameter);
		if (result == null) {
			for (HandlerMethodArgumentResolver methodArgumentResolver : this.argumentResolvers) {
				if (logger.isTraceEnabled()) {
					logger.trace("Testing if argument resolver [" + methodArgumentResolver + "] supports [" +
							parameter.getGenericParameterType() + "]");
				}
				//遍历参数解析器,匹配参数解析器
				if (methodArgumentResolver.supportsParameter(parameter)) {
					result = methodArgumentResolver;
					this.argumentResolverCache.put(parameter, result);//把使用过的参数解析器放入缓存
					break;
				}
			}
		}
		return result;
	}
}
