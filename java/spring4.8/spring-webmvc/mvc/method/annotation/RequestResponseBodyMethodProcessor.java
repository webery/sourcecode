/**
 * Resolves method arguments annotated with {@code @RequestBody} and handles
 * return values from methods annotated with {@code @ResponseBody} by reading
 * and writing to the body of the request or response with an
 * {@link HttpMessageConverter}.
 *  包含一系列的报文转换器。可以通过请求头或者url后缀决定使用哪种转换器。需要在XML中配置。
 * <p>An {@code @RequestBody} method argument is also validated if it is
 * annotated with {@code @javax.validation.Valid}. In case of validation
 * failure, {@link MethodArgumentNotValidException} is raised and results
 * in a 400 response status code if {@link DefaultHandlerExceptionResolver}
 * is configured.
 *
 * @author Arjen Poutsma
 * @author Rossen Stoyanchev
 * @since 3.1
 */
public class RequestResponseBodyMethodProcessor extends AbstractMessageConverterMethodProcessor {

	public RequestResponseBodyMethodProcessor(List<HttpMessageConverter<?>> messageConverters) {
		super(messageConverters);
	}

	public RequestResponseBodyMethodProcessor(List<HttpMessageConverter<?>> messageConverters,
			ContentNegotiationManager contentNegotiationManager) {

		super(messageConverters, contentNegotiationManager);
	}


	@Override
	public boolean supportsParameter(MethodParameter parameter) {
		//看到@RequestBody了
		return parameter.hasParameterAnnotation(RequestBody.class);
	}

	@Override
	public boolean supportsReturnType(MethodParameter returnType) {
		//看到@ResponseBody了
		return (AnnotationUtils.findAnnotation(returnType.getContainingClass(), ResponseBody.class) != null ||
				returnType.getMethodAnnotation(ResponseBody.class) != null);
	}

	/**
	 * @throws MethodArgumentNotValidException if validation fails
	 * @throws HttpMessageNotReadableException if {@link RequestBody#required()}
	 * is {@code true} and there is no body content or if there is no suitable
	 * converter to read the content with.
	 */
	 //参数解析
	@Override
	public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
			NativeWebRequest webRequest, WebDataBinderFactory binderFactory) throws Exception {

		Object argument = readWithMessageConverters(webRequest, parameter, parameter.getGenericParameterType());
		String name = Conventions.getVariableNameForParameter(parameter);
		WebDataBinder binder = binderFactory.createBinder(webRequest, argument, name);
		if (argument != null) {
			validate(binder, parameter);
		}
		mavContainer.addAttribute(BindingResult.MODEL_KEY_PREFIX + name, binder.getBindingResult());
		return argument;
	}
	//数据校验，使用的是注解校验
	private void validate(WebDataBinder binder, MethodParameter parameter) throws Exception {
		Annotation[] annotations = parameter.getParameterAnnotations();
		for (Annotation ann : annotations) {
			if (ann.annotationType().getSimpleName().startsWith("Valid")) {
				Object hints = AnnotationUtils.getValue(ann);
				binder.validate(hints instanceof Object[] ? (Object[]) hints : new Object[] {hints});
				BindingResult bindingResult = binder.getBindingResult();
				if (bindingResult.hasErrors()) {
					if (isBindExceptionRequired(binder, parameter)) {
						throw new MethodArgumentNotValidException(parameter, bindingResult);
					}
				}
				break;
			}
		}
	}

	/**
	 * Whether to raise a {@link MethodArgumentNotValidException} on validation errors.
	 * @param binder the data binder used to perform data binding
	 * @param parameter the method argument
	 * @return {@code true} if the next method argument is not of type {@link Errors}.
	 */
	private boolean isBindExceptionRequired(WebDataBinder binder, MethodParameter parameter) {
		int i = parameter.getParameterIndex();
		Class<?>[] paramTypes = parameter.getMethod().getParameterTypes();
		boolean hasBindingResult = (paramTypes.length > (i + 1) && Errors.class.isAssignableFrom(paramTypes[i + 1]));

		return !hasBindingResult;
	}

	@Override
	protected <T> Object readWithMessageConverters(NativeWebRequest webRequest,
			MethodParameter methodParam,  Type paramType) throws IOException, HttpMediaTypeNotSupportedException {

		final HttpServletRequest servletRequest = webRequest.getNativeRequest(HttpServletRequest.class);
		//把Servlet的报文转换成SpringMVC报文，以便使用HttpMessageConverter
		HttpInputMessage inputMessage = new ServletServerHttpRequest(servletRequest);

		//获取处理器方法参数的@RequestBody注解
		RequestBody ann = methodParam.getParameterAnnotation(RequestBody.class);
		if (!ann.required()) {
			InputStream inputStream = inputMessage.getBody();
			if (inputStream == null) {
				return null;
			}
			else if (inputStream.markSupported()) {
				inputStream.mark(1);
				if (inputStream.read() == -1) {
					return null;
				}
				inputStream.reset();
			}
			else {
				final PushbackInputStream pushbackInputStream = new PushbackInputStream(inputStream);
				int b = pushbackInputStream.read();
				if (b == -1) {
					return null;
				}
				else {
					pushbackInputStream.unread(b);
				}
				inputMessage = new ServletServerHttpRequest(servletRequest) {
					@Override
					public InputStream getBody() throws IOException {
						// Form POST should not get here
						return pushbackInputStream;
					}
				};
			}
		}
		//调用父类方法处理
		return super.readWithMessageConverters(inputMessage, methodParam, paramType);
	}

	@Override
	public void handleReturnValue(Object returnValue, MethodParameter returnType,
			ModelAndViewContainer mavContainer, NativeWebRequest webRequest)
			throws IOException, HttpMediaTypeNotAcceptableException {

		mavContainer.setRequestHandled(true);
		if (returnValue != null) {
			writeWithMessageConverters(returnValue, returnType, webRequest);//2.使用MessageConverter刷入返回值
		}
	}

}
