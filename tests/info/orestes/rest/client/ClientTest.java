package info.orestes.rest.client;

import info.orestes.rest.conversion.ConverterService;
import info.orestes.rest.conversion.MediaType;
import info.orestes.rest.conversion.WriteableContext;
import info.orestes.rest.error.NotFound;
import info.orestes.rest.error.RestException;
import info.orestes.rest.util.Module;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jetty.client.api.Request;
import org.eclipse.jetty.client.api.Result;
import org.eclipse.jetty.http.HttpHeader;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.AbstractHandler;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class ClientTest {
	
	private static Server server = new Server(8080);
	private static Module module = new Module();
	private volatile static Handler handler;
	private static RestClient client;
	
	static {
		module.bind(ConverterService.class, ConverterService.class);
	}
	
	@BeforeClass
	public static void setUpClass() throws Exception {
		server.setHandler(new AbstractHandler() {
			@Override
			public void handle(String target, org.eclipse.jetty.server.Request baseRequest, HttpServletRequest request,
					HttpServletResponse response)
					throws IOException, ServletException {
				handler.handle(target, request, response);
				baseRequest.setHandled(true);
			}
		});
		
		server.start();
		
		ConverterService converterService = module.moduleInstance(ConverterService.class);
		converterService.initConverters();
		
		client = new RestClient("http://localhost:8080/", converterService);
		client.start();
	}
	
	@Test
	public void testConnect() throws InterruptedException, TimeoutException, ExecutionException {
		handler = new Handler() {
			@Override
			public void handle(String path, HttpServletRequest request, HttpServletResponse response) {
				assertEquals("/", path);
			}
		};
		
		Request request = client.newRequest("/");
		assertEquals(200, request.send().getStatus());
	}
	
	@Test
	public void testEcho() throws InterruptedException {
		setupEchoHandler();
		final CountDownLatch countDownLatch = new CountDownLatch(1);
		
		Request request = client.newRequest("/");
		request.content(new EntityContent<>(String.class, "testing..."));
		request.send(new EntityResponseListener<String>(String.class) {
			@Override
			public void onComplete(Result result) {
				assertTrue(result.isSucceeded());
				assertEquals("testing...", getEntity());
				
				countDownLatch.countDown();
			}
		});
		
		assertTrue(countDownLatch.await(5, TimeUnit.SECONDS));
	}
	
	@Test
	public void testEmptyRequest() throws InterruptedException {
		setupStringHandler("Test string.");
		final CountDownLatch countDownLatch = new CountDownLatch(1);
		
		Request request = client.newRequest("/");
		request.send(new EntityResponseListener<String>(String.class) {
			@Override
			public void onComplete(Result result) {
				assertTrue(result.isSucceeded());
				assertEquals("Test string.", getEntity());
				countDownLatch.countDown();
			}
		});
		
		assertTrue(countDownLatch.await(5, TimeUnit.SECONDS));
	}
	
	@Test
	public void testEmptyResponse() throws InterruptedException {
		setupStringHandler("");
		final CountDownLatch countDownLatch = new CountDownLatch(1);
		
		Request request = client.newRequest("/");
		request.send(new EntityResponseListener<Void>(Void.class) {
			@Override
			public void onComplete(Result result) {
				assertTrue(result.isSucceeded());
				assertNull(getEntity());
				
				countDownLatch.countDown();
			}
		});
		
		assertTrue(countDownLatch.await(5, TimeUnit.SECONDS));
	}
	
	@Test
	public void testUnsupportedRequest() throws InterruptedException {
		setupStringHandler("Test string.");
		final CountDownLatch countDownLatch = new CountDownLatch(1);
		
		Request request = client.newRequest("/");
		request.content(new EntityContent<>(Iterator.class, Collections.emptyIterator()));
		request.send(new EntityResponseListener<String>(String.class) {
			@Override
			public void onComplete(Result result) {
				assertTrue(result.isFailed());
				assertTrue(result.getFailure() instanceof UnsupportedOperationException);
				
				countDownLatch.countDown();
			}
		});
		
		assertTrue(countDownLatch.await(5, TimeUnit.SECONDS));
	}
	
	@Test
	public void testUnsupportedResponse() throws InterruptedException {
		setupStringHandler("Test string.", "text/plain+test");
		final CountDownLatch countDownLatch = new CountDownLatch(1);
		
		Request request = client.newRequest("/");
		request.send(new EntityResponseListener<String>(String.class) {
			@Override
			public void onComplete(Result result) {
				assertTrue(result.isFailed());
				assertTrue(result.getFailure() instanceof UnsupportedOperationException);
				
				countDownLatch.countDown();
			}
		});
		
		assertTrue(countDownLatch.await(5, TimeUnit.SECONDS));
	}
	
	@Test
	public void testServersideException() throws InterruptedException {
		setupExceptionHandler(new NotFound("Test resource not found."));
		final CountDownLatch countDownLatch = new CountDownLatch(1);
		
		Request request = client.newRequest("/");
		request.send(new EntityResponseListener<String>(String.class) {
			@Override
			public void onComplete(Result result) {
				assertTrue(result.isFailed());
				assertTrue(result.getFailure() instanceof NotFound);
				
				countDownLatch.countDown();
			}
		});
		
		assertTrue(countDownLatch.await(5, TimeUnit.SECONDS));
	}
	
	@Test
	public void testSuccessFuture() throws Exception {
		setupStringHandler("Test string.");
		
		List<Future<EntityResponse<String>>> results = new ArrayList<>(3);
		
		for (int i = 0; i < 3; ++i) {
			Request request = client.newRequest("/");
			FutureResponseListener<String> future = new FutureResponseListener<>(String.class);
			
			request.send(future);
			results.add(future);
		}
		
		for (Future<EntityResponse<String>> future : results) {
			EntityResponse<String> result = future.get();
			
			assertEquals("Test string.", result.getEntity());
		}
	}
	
	@Test
	public void testErrorFuture() throws Exception {
		setupStringHandler("Test string.", "text/plain+test");
		
		List<Future<EntityResponse<String>>> results = new ArrayList<>(3);
		
		for (int i = 0; i < 3; ++i) {
			Request request = client.newRequest("/");
			FutureResponseListener<String> future = new FutureResponseListener<>(String.class);
			
			request.send(future);
			results.add(future);
		}
		
		for (Future<EntityResponse<String>> future : results) {
			try {
				future.get();
				fail("UnsupportedOperationException expected.");
			} catch (ExecutionException e) {
				assertTrue(e.getCause() instanceof UnsupportedOperationException);
			}
		}
	}
	
	@Test
	public void testServerseideExceptionFuture() throws Exception {
		setupExceptionHandler(new NotFound("Test resource not found."));
		
		List<Future<EntityResponse<String>>> results = new ArrayList<>(3);
		
		for (int i = 0; i < 3; ++i) {
			Request request = client.newRequest("/");
			FutureResponseListener<String> future = new FutureResponseListener<>(String.class);
			
			request.send(future);
			results.add(future);
		}
		
		for (Future<EntityResponse<String>> future : results) {
			try {
				future.get();
				fail("NotFound expected.");
			} catch (ExecutionException e) {
				assertTrue(e.getCause() instanceof NotFound);
			}
		}
	}
	
	@AfterClass
	public static void tearDownClass() throws Exception {
		server.stop();
		client.stop();
	}
	
	public void setupEchoHandler() {
		handler = new Handler() {
			@Override
			public void handle(String path, HttpServletRequest request, HttpServletResponse response)
					throws IOException {
				response.setContentType(request.getHeader(HttpHeader.CONTENT_TYPE.asString()));
				
				byte[] buffer = new byte[255];
				
				int len;
				while ((len = request.getInputStream().read(buffer)) > -1) {
					response.getOutputStream().write(buffer, 0, len);
				}
			}
		};
	}
	
	public void setupStringHandler(final String str) {
		setupStringHandler(str, "text/plain");
	}
	
	public void setupStringHandler(final String str, final String mediaType) {
		handler = new Handler() {
			@Override
			public void handle(String path, HttpServletRequest request, HttpServletResponse response)
					throws IOException {
				response.setContentType(mediaType);
				
				response.getWriter().print(str);
			}
		};
	}
	
	public void setupExceptionHandler(final RestException exception) {
		handler = new Handler() {
			private final ConverterService converterService = module.moduleInstance(ConverterService.class);
			
			@Override
			public void handle(String path, HttpServletRequest request, final HttpServletResponse response)
					throws IOException, ServletException {
				response.setContentType("text/plain");
				
				converterService.toRepresentation(new WriteableContext() {
					@Override
					public void setArgument(String name, Object value) {}
					
					@Override
					public <T> T getArgument(String name) {
						return null;
					}
					
					@Override
					public PrintWriter getWriter() throws IOException {
						return response.getWriter();
					}
				}, RestException.class, new MediaType("text/plain"), exception);
				
				response.setStatus(exception.getStatusCode());
			}
		};
	}
	
	public static interface Handler {
		public void handle(String path, HttpServletRequest request, HttpServletResponse response) throws IOException,
				ServletException;
	}
}