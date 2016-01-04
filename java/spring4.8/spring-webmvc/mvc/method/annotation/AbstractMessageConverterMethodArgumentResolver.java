/**
 * A base class for resolving method argument values by reading from the body of
 * a request with {@link HttpMessageConverter}s.
 *
 * @author Arjen Poutsma
 * @author Rossen Stoyanchev
 * @since 3.1
 */
public abstract class AbstractMessageConverterMethodArgumentResolver implements HandlerMethodArgumentResolver {

	protected final Log logger = LogFactory.getLog(getClass());

	protected final List<HttpMessageConverter<?>> messageConverters;//报文转换器

	protected final List<MediaType> allSupportedMediaTypes;//所支持的所有media类型


	public AbstractMessageConverterMethodArgumentResolver(List<HttpMessageConverter<?>> messageConverters) {
		Assert.notEmpty(messageConverters, "'messageConverters' must not be empty");
		this.messageConverters = messageConverters;
		this.allSupportedMediaTypes = getAllSupportedMediaTypes(messageConverters);
	}


	/**
	 * Return the media types supported by all provided message converters sorted
	 * by specificity via {@link MediaType#sortBySpecificity(List)}.
	 */
	private static List<MediaType> getAllSupportedMediaTypes(List<HttpMessageConverter<?>> messageConverters) {
		Set<MediaType> allSupportedMediaTypes = new LinkedHashSet<MediaType>();
		for (HttpMessageConverter<?> messageConverter : messageConverters) {
			allSupportedMediaTypes.addAll(messageConverter.getSupportedMediaTypes());
		}
		List<MediaType> result = new ArrayList<MediaType>(allSupportedMediaTypes);
		MediaType.sortBySpecificity(result);
		return Collections.unmodifiableList(result);
	}

	/**
	 * Create the method argument value of the expected parameter type by
	 * reading from the given request.
	 * @param <T> the expected type of the argument value to be created
	 * @param webRequest the current request
	 * @param methodParam the method argument
	 * @param paramType the type of the argument value to be created
	 * @return the created method argument value
	 * @throws IOException if the reading from the request fails
	 * @throws HttpMediaTypeNotSupportedException if no suitable message converter is found
	 */
	protected <T> Object readWithMessageConverters(NativeWebRequest webRequest,
			MethodParameter methodParam, Type paramType) throws IOException, HttpMediaTypeNotSupportedException {

		HttpInputMessage inputMessage = createInputMessage(webRequest);
		return readWithMessageConverters(inputMessage, methodParam, paramType);
	}

	/**
	 * Create the method argument value of the expected parameter type by reading
	 * from the given HttpInputMessage.
	 * @param <T> the expected type of the argument value to be created
	 * @param inputMessage the HTTP input message representing the current request
	 * @param methodParam the method argument
	 * @param targetType the type of object to create, not necessarily the same as
	 * the method parameter type (e.g. for {@code HttpEntity<String>} method
	 * parameter the target type is String)
	 * @return the created method argument value
	 * @throws IOException if the reading from the request fails
	 * @throws HttpMediaTypeNotSupportedException if no suitable message converter is found
	 */
	@SuppressWarnings("unchecked")
	protected <T> Object readWithMessageConverters(HttpInputMessage inputMessage,
			MethodParameter methodParam, Type targetType) throws IOException, HttpMediaTypeNotSupportedException {
		//首先要获取请求报文的类型，后面会根据这个判断使用哪种MessageConverter
		MediaType contentType;
		try {
			contentType = inputMessage.getHeaders().getContentType();//从请求头取得报文body类型
		}
		catch (InvalidMediaTypeException ex) {
			throw new HttpMediaTypeNotSupportedException(ex.getMessage());
		}
		if (contentType == null) {
			contentType = MediaType.APPLICATION_OCTET_STREAM;
		}

		Class<?> contextClass = methodParam.getContainingClass();
		//匹配报文转换器解析请求报文body数据
		for (HttpMessageConverter<?> converter : this.messageConverters) {
			if (converter instanceof GenericHttpMessageConverter) {
				GenericHttpMessageConverter<?> genericConverter = (GenericHttpMessageConverter<?>) converter;
				if (genericConverter.canRead(targetType, contextClass, contentType)) {
					if (logger.isDebugEnabled()) {
						logger.debug("Reading [" + targetType + "] as \"" +
								contentType + "\" using [" + converter + "]");
					}
					return genericConverter.read(targetType, contextClass, inputMessage);
				}
			}
			Class<T> targetClass = (Class<T>)
					ResolvableType.forMethodParameter(methodParam, targetType).resolve(Object.class);
			if (converter.canRead(targetClass, contentType)) {
				if (logger.isDebugEnabled()) {
					logger.debug("Reading [" + targetClass.getName() + "] as \"" +
							contentType + "\" using [" + converter + "]");
				}
				return ((HttpMessageConverter<T>) converter).read(targetClass, inputMessage);
			}
		}

		throw new HttpMediaTypeNotSupportedException(contentType, this.allSupportedMediaTypes);
	}

	/**
	 * Create a new {@link HttpInputMessage} from the given {@link NativeWebRequest}.
	 * @param webRequest the web request to create an input message from
	 * @return the input message
	 */
	protected ServletServerHttpRequest createInputMessage(NativeWebRequest webRequest) {
		HttpServletRequest servletRequest = webRequest.getNativeRequest(HttpServletRequest.class);
		return new ServletServerHttpRequest(servletRequest);
	}

}
