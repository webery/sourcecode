/**
 * Abstract base class for {@link HandlerMapping} implementations that define a
 * mapping between a request and a {@link HandlerMethod}.
 *
 * <p>For each registered handler method, a unique mapping is maintained with
 * subclasses defining the details of the mapping type {@code <T>}.
 *
 * @param <T> The mapping for a {@link HandlerMethod} containing the conditions
 * needed to match the handler method to incoming request.
 *
 * @author Arjen Poutsma
 * @author Rossen Stoyanchev
 * @author Juergen Hoeller
 * @since 3.1
 */
public abstract class AbstractHandlerMethodMapping<T> extends AbstractHandlerMapping implements InitializingBean {

	/**
	 * Bean name prefix for target beans behind scoped proxies. Used to exclude those
	 * targets from handler method detection, in favor of the corresponding proxies.
	 * <p>We're not checking the autowire-candidate status here, which is how the
	 * proxy target filtering problem is being handled at the autowiring level,
	 * since autowire-candidate may have been turned to {@code false} for other
	 * reasons, while still expecting the bean to be eligible for handler methods.
	 * <p>Originally defined in {@link org.springframework.aop.scope.ScopedProxyUtils}
	 * but duplicated here to avoid a hard dependency on the spring-aop module.
	 */
	private static final String SCOPED_TARGET_NAME_PREFIX = "scopedTarget.";


	private boolean detectHandlerMethodsInAncestorContexts = false;
	//请求映射信息和处理器的关系
	private final Map<T, HandlerMethod> handlerMethods = new LinkedHashMap<T, HandlerMethod>();
	//路径和映射信息的关系(注意是一对多的关系),快速匹配使用
	private final MultiValueMap<String, T> urlMap = new LinkedMultiValueMap<String, T>();

	
	/**
	 * Extract and return the URL paths contained in a mapping.
	 */
	protected abstract Set<String> getMappingPathPatterns(T mapping);

	/**
	 * Invoked after all handler methods have been detected.
	 * @param handlerMethods a read-only map with handler methods and mappings.
	 */
	protected void handlerMethodsInitialized(Map<T, HandlerMethod> handlerMethods) {
	}


	/**
	 * Look up a handler method for the given request.
	 */
	@Override
	protected HandlerMethod getHandlerInternal(HttpServletRequest request) throws Exception {
		//1.获取当前请求需要匹配的那部分路径,不包括除协议,域名和端口等.例如 http://localhost:8080/user/weebr
		//lookupPath为/user/weebr
		String lookupPath = getUrlPathHelper().getLookupPathForRequest(request);
		if (logger.isDebugEnabled()) {
			logger.debug("Looking up handler method for path " + lookupPath);
		}
		//2.获取处理器
		HandlerMethod handlerMethod = lookupHandlerMethod(lookupPath, request);
		if (logger.isDebugEnabled()) {
			if (handlerMethod != null) {
				logger.debug("Returning handler method [" + handlerMethod + "]");
			}
			else {
				logger.debug("Did not find handler method for [" + lookupPath + "]");
			}
		}
		return (handlerMethod != null ? handlerMethod.createWithResolvedBean() : null);
	}

	/**
	 * Look up the best-matching handler method for the current request.
	 * If multiple matches are found, the best match is selected.
	 * @param lookupPath mapping lookup path within the current servlet mapping
	 * @param request the current request
	 * @return the best-matching handler method, or {@code null} if no match
	 * @see #handleMatch(Object, String, HttpServletRequest)
	 * @see #handleNoMatch(Set, String, HttpServletRequest)
	 */
	protected HandlerMethod lookupHandlerMethod(String lookupPath, HttpServletRequest request) throws Exception {
		List<Match> matches = new ArrayList<Match>();
		//1.首先尝试直接根据当前请求的path匹配请求映射信息,这是理想的情况下,匹配速度最快.restful风格{}不包含在内
		List<T> directPathMatches = this.urlMap.get(lookupPath);
		if (directPathMatches != null) {
			addMatchingMappings(directPathMatches, matches, request);
		}
		if (matches.isEmpty()) {
			// No choice but to go through all mappings...
			//2.直接路径匹配失败,那就只能把所有注册的映射信息挨个匹配了,这样速度没有直接匹配那么快
			addMatchingMappings(this.handlerMethods.keySet(), matches, request);
		}
		//3.可能会匹配到多个合适的请求映射信息,所以需要一种优先算法找到最佳匹配,这个过程使用了Comparator比较类,先排序,再找出最合适的,
		//是不是和我们的找数组最大最小值或者冒泡排序很像呢,哈哈
		if (!matches.isEmpty()) {
			Comparator<Match> comparator = new MatchComparator(getMappingComparator(request));//实际上Comparator使用的是RequestMappingInfo之间的比较
			Collections.sort(matches, comparator);//排序,最优在前.不会暂用太多内存,因为用的都是对象的引用,4个字节而已.info太多,速度会不会有影响?
			if (logger.isTraceEnabled()) {
				logger.trace("Found " + matches.size() + " matching mapping(s) for [" + lookupPath + "] : " + matches);
			}
			Match bestMatch = matches.get(0);//获取第一个,也就是最优
			if (matches.size() > 1) {
				Match secondBestMatch = matches.get(1);
				if (comparator.compare(bestMatch, secondBestMatch) == 0) {//如果最优和次优比较结果一样,抛出异常
					Method m1 = bestMatch.handlerMethod.getMethod();
					Method m2 = secondBestMatch.handlerMethod.getMethod();
					throw new IllegalStateException(
							"Ambiguous handler methods mapped for HTTP path '" + request.getRequestURL() + "': {" +
							m1 + ", " + m2 + "}");
				}
			}
			handleMatch(bestMatch.mapping, lookupPath, request);//把匹配的路径放入request属性中
			return bestMatch.handlerMethod;
		}
		else {
			return handleNoMatch(handlerMethods.keySet(), lookupPath, request);
		}
	}

	private void addMatchingMappings(Collection<T> mappings, List<Match> matches, HttpServletRequest request) {
		for (T mapping : mappings) {
			T match = getMatchingMapping(mapping, request);
			if (match != null) {
				matches.add(new Match(match, this.handlerMethods.get(mapping)));
			}
		}
	}

	/**
	 * Check if a mapping matches the current request and return a (potentially
	 * new) mapping with conditions relevant to the current request.
	 * @param mapping the mapping to get a match for
	 * @param request the current HTTP servlet request
	 * @return the match, or {@code null} if the mapping doesn't match
	 */
	protected abstract T getMatchingMapping(T mapping, HttpServletRequest request);

	/**
	 * Return a comparator for sorting matching mappings.
	 * The returned comparator should sort 'better' matches higher.
	 * @param request the current request
	 * @return the comparator, never {@code null}
	 */
	protected abstract Comparator<T> getMappingComparator(HttpServletRequest request);

	/**
	 * Invoked when a matching mapping is found.
	 * @param mapping the matching mapping
	 * @param lookupPath mapping lookup path within the current servlet mapping
	 * @param request the current request
	 */
	protected void handleMatch(T mapping, String lookupPath, HttpServletRequest request) {
		request.setAttribute(HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE, lookupPath);//把匹配的路径放入request属性中
	}

	/**
	 * A thin wrapper around a matched HandlerMethod and its mapping, for the purpose of
	 * comparing the best match with a comparator in the context of the current request.
	 */
	//暂存单个匹配的结果,
	private class Match {

		private final T mapping;//RequestMappingInfo

		private final HandlerMethod handlerMethod;

		public Match(T mapping, HandlerMethod handlerMethod) {
			this.mapping = mapping;
			this.handlerMethod = handlerMethod;
		}

		@Override
		public String toString() {
			return this.mapping.toString();
		}
	}

	//[匹配比较器的封装,排序使用,找出最优的匹配结果]
	private class MatchComparator implements Comparator<Match> {

		private final Comparator<T> comparator;

		public MatchComparator(Comparator<T> comparator) {
			this.comparator = comparator;
		}

		@Override
		public int compare(Match match1, Match match2) {
			return this.comparator.compare(match1.mapping, match2.mapping);
		}
	}

}
