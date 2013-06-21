package info.orestes.rest.conversion;

import info.orestes.rest.Request;
import info.orestes.rest.Response;
import info.orestes.rest.error.BadRequest;
import info.orestes.rest.error.NotAcceptable;
import info.orestes.rest.error.RestException;
import info.orestes.rest.error.UnsupportedMediaType;
import info.orestes.rest.service.EntityType;
import info.orestes.rest.service.RestHandler;
import info.orestes.rest.service.RestMethod;
import info.orestes.rest.service.RestRequest;
import info.orestes.rest.service.RestResponse;
import info.orestes.rest.util.Inject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map.Entry;

import javax.servlet.ServletException;

import org.eclipse.jetty.http.HttpHeader;

/**
 * The {@link ConversionHandler} use the {@link ConverterService} to process the
 * request and response entity which are returned by {@link Request#getEntity()}
 * and set by {@link Response#setEntity(Object)}. The Response entity
 * representation will be chosen by the content negotiation mechanism.
 * Furthermore it converts the matched method route Arguments to there accurate
 * types.
 * 
 * @author Florian
 * 
 */
public class ConversionHandler extends RestHandler {
	
	private static final List<MediaType> ANY = Arrays.asList(new MediaType(MediaType.ALL));
	private final ConverterService converterService;
	
	/**
	 * Parse the Accept header and extract the contained list of media types
	 * 
	 * @param acceptHeader
	 *            the value of the Accept header
	 * @return a list of all declared media types as they occurred
	 */
	public static List<MediaType> parseMediaTypes(String acceptHeader) {
		if (acceptHeader != null) {
			List<MediaType> mediaTypes = new ArrayList<>();
			for (String part : acceptHeader.split(",")) {
				mediaTypes.add(new MediaType(part));
			}
			
			return mediaTypes;
		} else {
			return ANY;
		}
	}
	
	@Inject
	public ConversionHandler(ConverterService converterService) {
		this.converterService = converterService;
	}
	
	@Override
	public void handle(RestRequest request, final RestResponse response) throws ServletException, IOException {
		RestMethod method = request.getRestMethod();
		
		if (!request.isAsyncStarted()) {
			for (Entry<String, Object> entry : request.getArguments().entrySet()) {
				Class<?> argType = method.getArguments().get(entry.getKey()).getValueType();
				try {
					if (entry.getValue() != null) {
						entry.setValue(converterService.toObject(request, argType, (String) entry.getValue()));
					}
				} catch (Exception e) {
					throw new BadRequest("The argument " + entry.getKey() + " can not be parsed.", e);
				}
			}
		}
		
		if (request.getEntity() == null && request.getContentType() != null) {
			handleRequestEntity(request, response);
		}
		
		super.handle(request, response);
		
		if (response.getEntity() != null && !response.isCommitted()) {
			handleResponseEntity(request, response);
		}
	}
	
	private void handleRequestEntity(RestRequest request, RestResponse response) throws RestException, IOException {
		EntityType<?> requestType = request.getRestMethod().getRequestType();
		
		if (requestType != null) {
			MediaType mediaType = new MediaType(request.getContentType());
			try {
				Object entity = converterService.toObject(request, mediaType, requestType);
				request.setEntity(entity);
			} catch (UnsupportedOperationException e) {
				throw new UnsupportedMediaType("The request media type is not supported.", e);
			}
		}
	}
	
	/**
	 * process the response entity after the request is marked as completed.
	 * 
	 * @param request
	 *            The request object
	 * @param response
	 *            The response object
	 * @throws IOException
	 */
	private void handleResponseEntity(Request request, Response response) throws IOException, RestException {
		EntityType<?> responseType = request.getRestMethod().getResponseType();
		
		if (responseType != null) {
			List<MediaType> mediaTypes = parseMediaTypes(request.getHeader(HttpHeader.ACCEPT.asString()));
			
			MediaType mediaType = converterService.getPreferedMediaType(mediaTypes, responseType.getRawType());
			if (mediaType != null) {
				response.setContentType(mediaType.toString());
				
				try {
					converterService.toRepresentation(response, responseType, mediaType, response.getEntity());
				} catch (UnsupportedOperationException e) {
					throw new IOException("The response body can not be handled.", e);
				}
			} else {
				throw new NotAcceptable("The requested response media types are not supported.");
			}
		}
	}
}
