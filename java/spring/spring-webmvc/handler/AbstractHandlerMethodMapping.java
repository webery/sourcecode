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
	//����ӳ����Ϣ�ʹ������Ĺ�ϵ
	private final Map<T, HandlerMethod> handlerMethods = new LinkedHashMap<T, HandlerMethod>();
	//·����ӳ����Ϣ�Ĺ�ϵ(ע����һ�Զ�Ĺ�ϵ),����ƥ��ʹ��
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
		//1.��ȡ��ǰ������Ҫƥ����ǲ���·��,��������Э��,�����Ͷ˿ڵ�.���� http://localhost:8080/user/weebr
		//lookupPathΪ/user/weebr
		String lookupPath = getUrlPathHelper().getLookupPathForRequest(request);
		if (logger.isDebugEnabled()) {
			logger.debug("Looking up handler method for path " + lookupPath);
		}
		//2.��ȡ������
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
		//1.���ȳ���ֱ�Ӹ��ݵ�ǰ�����pathƥ������ӳ����Ϣ,��������������,ƥ���ٶ����.restful���{}����������
		List<T> directPathMatches = this.urlMap.get(lookupPath);
		if (directPathMatches != null) {
			addMatchingMappings(directPathMatches, matches, request);
		}
		if (matches.isEmpty()) {
			// No choice but to go through all mappings...
			//2.ֱ��·��ƥ��ʧ��,�Ǿ�ֻ�ܰ�����ע���ӳ����Ϣ����ƥ����,�����ٶ�û��ֱ��ƥ����ô��
			addMatchingMappings(this.handlerMethods.keySet(), matches, request);
		}
		//3.���ܻ�ƥ�䵽������ʵ�����ӳ����Ϣ,������Ҫһ�������㷨�ҵ����ƥ��,�������ʹ����Comparator�Ƚ���,������,���ҳ�����ʵ�,
		//�ǲ��Ǻ����ǵ������������Сֵ����ð�����������,����
		if (!matches.isEmpty()) {
			Comparator<Match> comparator = new MatchComparator(getMappingComparator(request));//ʵ����Comparatorʹ�õ���RequestMappingInfo֮��ıȽ�
			Collections.sort(matches, comparator);//����,������ǰ.��������̫���ڴ�,��Ϊ�õĶ��Ƕ��������,4���ֽڶ���.info̫��,�ٶȻ᲻����Ӱ��?
			if (logger.isTraceEnabled()) {
				logger.trace("Found " + matches.size() + " matching mapping(s) for [" + lookupPath + "] : " + matches);
			}
			Match bestMatch = matches.get(0);//��ȡ��һ��,Ҳ��������
			if (matches.size() > 1) {
				Match secondBestMatch = matches.get(1);
				if (comparator.compare(bestMatch, secondBestMatch) == 0) {//������źʹ��űȽϽ��һ��,�׳��쳣
					Method m1 = bestMatch.handlerMethod.getMethod();
					Method m2 = secondBestMatch.handlerMethod.getMethod();
					throw new IllegalStateException(
							"Ambiguous handler methods mapped for HTTP path '" + request.getRequestURL() + "': {" +
							m1 + ", " + m2 + "}");
				}
			}
			handleMatch(bestMatch.mapping, lookupPath, request);//��ƥ���·������request������
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
		request.setAttribute(HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE, lookupPath);//��ƥ���·������request������
	}

	/**
	 * A thin wrapper around a matched HandlerMethod and its mapping, for the purpose of
	 * comparing the best match with a comparator in the context of the current request.
	 */
	//�ݴ浥��ƥ��Ľ��,
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

	//[ƥ��Ƚ����ķ�װ,����ʹ��,�ҳ����ŵ�ƥ����]
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
