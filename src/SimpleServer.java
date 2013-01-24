import info.orestes.rest.Method;
import info.orestes.rest.MethodGroup;
import info.orestes.rest.RestServletHandler;
import info.orestes.rest.Router;
import info.orestes.rest.ServiceDocumentParser;
import info.orestes.rest.ServiceDocumentParserTest;

import java.util.List;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.ContextHandler;
import org.eclipse.jetty.server.handler.ContextHandlerCollection;

public class SimpleServer {
	
	/**
	 * @param args
	 * @throws Exception
	 */
	public static void main(String[] args) throws Exception {
		ServiceDocumentParser parser = new ServiceDocumentParser(
				new ServiceDocumentParserTest.ServiceDocumentTestTypes());
		
		List<MethodGroup> lists = parser.parse("/service.rest");
		
		// SslContextFactory sslFactory = new SslContextFactory();
		// sslFactory.setKeyStorePath("src/main/resources/spdy.keystore");
		// sslFactory.setKeyStorePassword("secret");
		// sslFactory.setProtocol("TLSv1");
		//
		// // simple connector to add to serve content using spdy
		// Connector connector = new HTTPSPDYServerConnector(server,
		// sslFactory);
		// server.addConnector(connector);
		
		Router router = new Router();
		for (List<Method> list : lists) {
			for (Method method : list) {
				router.add(method);
			}
		}
		
		router.setHandler(new RestServletHandler());
		
		ContextHandlerCollection context = new ContextHandlerCollection();
		
		ContextHandler handler = new ContextHandler(context, "/");
		handler.setHandler(router);
		
		// ContextHandler webHandler = new ContextHandler(context, "/");
		// handler.setHandler(new ResourceHandler().);
		
		Server server = new Server(80);
		server.setHandler(context);
		server.start();
		server.join();
	}
	
}
