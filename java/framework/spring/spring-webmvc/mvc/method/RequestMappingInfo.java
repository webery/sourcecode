/**
 * Encapsulates the following request mapping conditions:
 * <ol>
 * 	<li>{@link PatternsRequestCondition}
 * 	<li>{@link RequestMethodsRequestCondition}
 * 	<li>{@link ParamsRequestCondition}
 * 	<li>{@link HeadersRequestCondition}
 * 	<li>{@link ConsumesRequestCondition}
 * 	<li>{@link ProducesRequestCondition}
 * 	<li>{@code RequestCondition} (optional, custom request condition)
 * </ol>
   使用@RequestMapping注解和所在方法上的对应关系。也就是请求信息和处理器的映射关系。
 *
 * @author Arjen Poutsma
 * @author Rossen Stoyanchev
 * @since 3.1
 */
public final class RequestMappingInfo implements RequestCondition<RequestMappingInfo> {

	private final PatternsRequestCondition patternsCondition;//URL匹配

	private final RequestMethodsRequestCondition methodsCondition;//http method匹配

	private final ParamsRequestCondition paramsCondition;//请求参数匹配

	private final HeadersRequestCondition headersCondition;//请求头匹配

	private final ConsumesRequestCondition consumesCondition;//请求类型匹配,

	private final ProducesRequestCondition producesCondition;//返回类型匹配

	private final RequestConditionHolder customConditionHolder;//暂时不知道什么东东


	/**
	 * Creates a new instance with the given request conditions.
	 */
	public RequestMappingInfo(PatternsRequestCondition patterns, RequestMethodsRequestCondition methods,
			ParamsRequestCondition params, HeadersRequestCondition headers, ConsumesRequestCondition consumes,
			ProducesRequestCondition produces, RequestCondition<?> custom) {

		this.patternsCondition = (patterns != null ? patterns : new PatternsRequestCondition());
		this.methodsCondition = (methods != null ? methods : new RequestMethodsRequestCondition());
		this.paramsCondition = (params != null ? params : new ParamsRequestCondition());
		this.headersCondition = (headers != null ? headers : new HeadersRequestCondition());
		this.consumesCondition = (consumes != null ? consumes : new ConsumesRequestCondition());
		this.producesCondition = (produces != null ? produces : new ProducesRequestCondition());
		this.customConditionHolder = new RequestConditionHolder(custom);
	}

	/**
	 * Re-create a RequestMappingInfo with the given custom request condition.
	 */
	public RequestMappingInfo(RequestMappingInfo info, RequestCondition<?> customRequestCondition) {
		this(info.patternsCondition, info.methodsCondition, info.paramsCondition, info.headersCondition,
				info.consumesCondition, info.producesCondition, customRequestCondition);
	}


	/**
	 * Returns the URL patterns of this {@link RequestMappingInfo};
	 * or instance with 0 patterns, never {@code null}.
	 */
	public PatternsRequestCondition getPatternsCondition() {
		return this.patternsCondition;
	}

	/**
	 * Returns the HTTP request methods of this {@link RequestMappingInfo};
	 * or instance with 0 request methods, never {@code null}.
	 */
	public RequestMethodsRequestCondition getMethodsCondition() {
		return this.methodsCondition;
	}

	/**
	 * Returns the "parameters" condition of this {@link RequestMappingInfo};
	 * or instance with 0 parameter expressions, never {@code null}.
	 */
	public ParamsRequestCondition getParamsCondition() {
		return this.paramsCondition;
	}

	/**
	 * Returns the "headers" condition of this {@link RequestMappingInfo};
	 * or instance with 0 header expressions, never {@code null}.
	 */
	public HeadersRequestCondition getHeadersCondition() {
		return this.headersCondition;
	}

	/**
	 * Returns the "consumes" condition of this {@link RequestMappingInfo};
	 * or instance with 0 consumes expressions, never {@code null}.
	 */
	public ConsumesRequestCondition getConsumesCondition() {
		return this.consumesCondition;
	}

	/**
	 * Returns the "produces" condition of this {@link RequestMappingInfo};
	 * or instance with 0 produces expressions, never {@code null}.
	 */
	public ProducesRequestCondition getProducesCondition() {
		return this.producesCondition;
	}

	/**
	 * Returns the "custom" condition of this {@link RequestMappingInfo}; or {@code null}.
	 */
	public RequestCondition<?> getCustomCondition() {
		return this.customConditionHolder.getCondition();
	}


	/**
	 * Combines "this" request mapping info (i.e. the current instance) with another request mapping info instance.
	 * <p>Example: combine type- and method-level request mappings.
	 * @return a new request mapping info instance; never {@code null}
	 */
	@Override
	public RequestMappingInfo combine(RequestMappingInfo other) {
		PatternsRequestCondition patterns = this.patternsCondition.combine(other.patternsCondition);
		RequestMethodsRequestCondition methods = this.methodsCondition.combine(other.methodsCondition);
		ParamsRequestCondition params = this.paramsCondition.combine(other.paramsCondition);
		HeadersRequestCondition headers = this.headersCondition.combine(other.headersCondition);
		ConsumesRequestCondition consumes = this.consumesCondition.combine(other.consumesCondition);
		ProducesRequestCondition produces = this.producesCondition.combine(other.producesCondition);
		RequestConditionHolder custom = this.customConditionHolder.combine(other.customConditionHolder);

		return new RequestMappingInfo(patterns, methods, params, headers, consumes, produces, custom.getCondition());
	}

	/**
	 * Checks if all conditions in this request mapping info match the provided request and returns
	 * a potentially new request mapping info with conditions tailored to the current request.
	 * <p>For example the returned instance may contain the subset of URL patterns that match to
	 * the current request, sorted with best matching patterns on top.
	 * @return a new instance in case all conditions match; or {@code null} otherwise
	 */
	@Override
	public RequestMappingInfo getMatchingCondition(HttpServletRequest request) {
		RequestMethodsRequestCondition methods = this.methodsCondition.getMatchingCondition(request);
		ParamsRequestCondition params = this.paramsCondition.getMatchingCondition(request);
		HeadersRequestCondition headers = this.headersCondition.getMatchingCondition(request);
		ConsumesRequestCondition consumes = this.consumesCondition.getMatchingCondition(request);
		ProducesRequestCondition produces = this.producesCondition.getMatchingCondition(request);

		if (methods == null || params == null || headers == null || consumes == null || produces == null) {
			return null;
		}

		PatternsRequestCondition patterns = this.patternsCondition.getMatchingCondition(request);
		if (patterns == null) {
			return null;
		}

		RequestConditionHolder custom = this.customConditionHolder.getMatchingCondition(request);
		if (custom == null) {
			return null;
		}

		return new RequestMappingInfo(patterns, methods, params, headers, consumes, produces, custom.getCondition());
	}


	/**
	 * Compares "this" info (i.e. the current instance) with another info in the context of a request.
	 * <p>Note: It is assumed both instances have been obtained via
	 * {@link #getMatchingCondition(HttpServletRequest)} to ensure they have conditions with
	 * content relevant to current request.
	 */
	@Override//两个info直接的比较,实际上就是使用info的属性对象按照优先顺序去短路比较.搞明白这个规则,可以优化映射匹配的速度,排除重复
	public int compareTo(RequestMappingInfo other, HttpServletRequest request) {
		int result = this.patternsCondition.compareTo(other.getPatternsCondition(), request);//1.path
		if (result != 0) {
			return result;
		}
		result = this.paramsCondition.compareTo(other.getParamsCondition(), request);//2.params
		if (result != 0) {
			return result;
		}
		result = this.headersCondition.compareTo(other.getHeadersCondition(), request);//3.header
		if (result != 0) {
			return result;
		}
		result = this.consumesCondition.compareTo(other.getConsumesCondition(), request);//4.consumes
		if (result != 0) {
			return result;
		}
		result = this.producesCondition.compareTo(other.getProducesCondition(), request);//5.produces
		if (result != 0) {
			return result;
		}
		result = this.methodsCondition.compareTo(other.getMethodsCondition(), request);//6.methods
		if (result != 0) {//比较http类型的字符串长度, 例如GET 比 POST短, 所以GET比POST优先
			return result;
		}
		result = this.customConditionHolder.compareTo(other.customConditionHolder, request);//7.custom
		if (result != 0) {
			return result;
		}
		return 0;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (obj != null && obj instanceof RequestMappingInfo) {
			RequestMappingInfo other = (RequestMappingInfo) obj;
			return (this.patternsCondition.equals(other.patternsCondition) &&
					this.methodsCondition.equals(other.methodsCondition) &&
					this.paramsCondition.equals(other.paramsCondition) &&
					this.headersCondition.equals(other.headersCondition) &&
					this.consumesCondition.equals(other.consumesCondition) &&
					this.producesCondition.equals(other.producesCondition) &&
					this.customConditionHolder.equals(other.customConditionHolder));
		}
		return false;
	}

	@Override
	public int hashCode() {
		return (this.patternsCondition.hashCode() * 31 +  // primary differentiation
				this.methodsCondition.hashCode() + this.paramsCondition.hashCode() +
				this.headersCondition.hashCode() + this.consumesCondition.hashCode() +
				this.producesCondition.hashCode() + this.customConditionHolder.hashCode());
	}

	@Override
	public String toString() {
		StringBuilder builder = new StringBuilder("{");
		builder.append(this.patternsCondition);
		builder.append(",methods=").append(this.methodsCondition);
		builder.append(",params=").append(this.paramsCondition);
		builder.append(",headers=").append(this.headersCondition);
		builder.append(",consumes=").append(this.consumesCondition);
		builder.append(",produces=").append(this.producesCondition);
		builder.append(",custom=").append(this.customConditionHolder);
		builder.append('}');
		return builder.toString();
	}

}
