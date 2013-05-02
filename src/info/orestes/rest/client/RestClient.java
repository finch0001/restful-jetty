package info.orestes.rest.client;

import info.orestes.rest.conversion.ConverterService;
import info.orestes.rest.conversion.MediaType;

import java.net.URI;

import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.api.Request;

public class RestClient extends HttpClient {
	
	public static final MediaType ALL = new MediaType(MediaType.ALL);
	
	private final ConverterService converterService;
	private final URI baseURI;
	
	public RestClient(String baseURI, ConverterService converterService) {
		this.baseURI = URI.create(baseURI);
		this.converterService = converterService;
	}
	
	@Override
	public Request newRequest(String path) {
		return new RestRequest(this, baseURI, path);
	}
	
	public ConverterService getConverterService() {
		return converterService;
	}
}