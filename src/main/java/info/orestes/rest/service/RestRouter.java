package info.orestes.rest.service;

import info.orestes.rest.RestServlet;
import info.orestes.rest.service.PathElement.Type;
import info.orestes.rest.util.Inject;
import info.orestes.rest.util.Module;
import org.eclipse.jetty.http.HttpStatus;
import org.eclipse.jetty.http.HttpURI;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.handler.HandlerWrapper;
import org.eclipse.jetty.util.MultiMap;
import org.eclipse.jetty.util.UrlEncoded;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.*;

public class RestRouter extends HandlerWrapper {
	
	private final Module module;
	private final List<RestMethod> methods = new ArrayList<>();
	@SuppressWarnings("unchecked")
	private ArrayList<Route>[] routeLists = (ArrayList<Route>[]) new ArrayList<?>[10];
	
	@Inject
	public RestRouter(Module module) {
		this.module = module;
	}
	
	@Override
	public void handle(String path, Request request, HttpServletRequest req, HttpServletResponse res)
			throws IOException, ServletException {
		RestRequest r = null;
		if (req instanceof RestRequest) {
			r = (RestRequest) req;
		} else {
			HttpURI uri = request.getUri();

			Map<String, String> matrix = uri.getParam() == null ? null : createMap(uri.getParam().split(";"));
			Map<String, String> query = uri.getQuery() == null ? null : createMap(uri.getQuery().split("&"));
			
			List<String> pathParts = new ArrayList<>();
			int next;
			int offset = 1;
			while ((next = path.indexOf('/', offset)) != -1) {
				pathParts.add(UrlEncoded.decodeString(path, offset, next - offset, null));
				offset = next + 1;
			}
			pathParts.add(UrlEncoded.decodeString(path, offset, path.length() - offset, null));
			
			for (Route route : getRoutes(pathParts.size())) {
				String method = request.getMethod();
				
				Map<String, String> matches = route.match(method, pathParts, matrix, query);
				if (matches != null) {
					if (!matches.isEmpty()) {
						MultiMap<String> params = request.getQueryParameters();
						if (params == null) {
							request.setQueryParameters(params = new MultiMap<>());
						}
						params.putAllValues(matches);
					}
					
					if (route.getMethod().getResponseType() != null) {
						res.setStatus(HttpStatus.OK_200);
					} else {
						res.setStatus(HttpStatus.NO_CONTENT_204);
					}
					
					r = new RestRequest(request, req, route, matches);
					break;
				}
			}
		}
		
		if (r != null) {
            if (!(res instanceof RestResponse)) {
                res = new RestResponse(res, r.getArguments());
            }

			super.handle(path, request, r, res);
			request.setHandled(true);
		}
	}
	
	protected Map<String, String> createMap(String[] params) {
		Map<String, String> map = new HashMap<>();
		
		for (String str : params) {
			int index = str.indexOf('=');
			if (index == -1) {
				map.put(UrlEncoded.decodeString(str, 0, str.length(), null), null);
			} else {
				map.put(UrlEncoded.decodeString(str, 0, index, null),
					UrlEncoded.decodeString(str, index + 1, str.length() - index - 1, null));
			}
		}
		
		return map;
	}
	
	public List<RestMethod> getMethods() {
		return Collections.unmodifiableList(methods);
	}
	
	// modified
	public void add(RestMethod method) {
		if (isStarted()) {
			throw new IllegalStateException("The Roter can not be modified while it is running");
		}
		
		List<Route> list = getRoutes(method);
		
		boolean added = false;
		Route route = new Route(method);
		for (int i = 0; i < list.size(); i++) {
			if (route.compareTo(list.get(i)) > 0) {
				list.add(i, route);
				added = true;
				break;
			}
		}
		
		if (!added) {
			list.add(route);
		}
		
		methods.add(method);
	}
	
	public void addAll(Collection<RestMethod> methods) {
		for (RestMethod method : methods) {
			add(method);
		}
	}
	
	public void remove(RestMethod method) {
		if (isStarted()) {
			throw new IllegalStateException("The Roter can not be modified while it is running");
		}
		
		int index = methods.indexOf(method);
		if (index != -1) {
			methods.remove(index);
			List<Route> list = getRoutes(method);
			
			for (Iterator<Route> iter = list.iterator(); iter.hasNext();) {
				Route route = iter.next();
				if (route.getMethod() == method) {
					iter.remove();
					break;
				}
			}
		}
	}
	
	public void removeAll(Collection<RestMethod> methods) {
		for (RestMethod method : methods) {
			remove(method);
		}
	}
	
	public void clear() {
		if (isStarted()) {
			throw new IllegalStateException("The Roter can not be modified while it is running");
		}
		
		Arrays.fill(routeLists, null);
		methods.clear();
	}
	
	protected List<Route> getRoutes(RestMethod method) {
		int index = -1;
		for (PathElement element : method.getSignature()) {
			if (element.getType() == Type.PATH || element.getType() == Type.VARIABLE) {
				index++;
			}
		}
		
		if (routeLists.length <= index) {
			int newLength = routeLists.length * 2;
			if (newLength <= index) {
				newLength = index + 1;
			}
			
			routeLists = Arrays.copyOf(routeLists, newLength);
		}
		
		List<Route> list = routeLists[index];
		if (list == null) {
			list = routeLists[index] = new ArrayList<>();
		}
		
		return list;
	}
	
	protected List<Route> getRoutes(int parts) {
		int index = parts - 1;
		
		if (index >= routeLists.length || routeLists[index] == null) {
			return Collections.emptyList();
		} else {
			return routeLists[index];
		}
	}
	
	public class Route implements Comparable<Route> {
		
		private final RestMethod method;
		private final RestServlet servlet;

		public Route(RestMethod method) {
			this.method = method;
            this.servlet = module.inject(method.getTarget());
		}
		
		public RestMethod getMethod() {
			return method;
		}

        public RestServlet getServlet() {
            return servlet;
        }

        public RestRouter getRouter() {
			return RestRouter.this;
		}
		
		@Override
		public int compareTo(Route o) {
			List<PathElement> self = getMethod().getSignature();
			List<PathElement> other = o.getMethod().getSignature();
			
			int len = Math.min(self.size(), other.size());
			for (int i = 0; i < len; ++i) {
				Type selfType = self.get(i).getType();
				Type otherType = other.get(i).getType();
				
				if (selfType != otherType) {
					// fixed path wins against variables
					if (selfType == Type.PATH) {
						return 1;
					} else if (otherType == Type.PATH) {
						return -1;
					} else if (selfType == Type.VARIABLE) {
						return 1;
					} else if (otherType == Type.VARIABLE) {
						return -1;
					} else {
						// no further path or variables expected
						break;
					}
				}
			}
			
			// if the path structure is identical the route with more required
			// parameters wins e.g. will be matched first
			return getMethod().getRequiredParamaters() - o.getMethod().getRequiredParamaters();
		}
		
		public Map<String, String> match(String action, List<String> pathParts, Map<String, String> matrix,
				Map<String, String> query) {
			switch (action) {
				case "OPTIONS":
					break;
				case "HEAD":
					action = "GET";
				default:
					if (!getMethod().getAction().equals(action)) {
						return null;
					}
			}
			
			Map<String, String> matches = new HashMap<>();
			
			int matrixCounter = matrix == null ? 0 : matrix.size();
			
			int parts = 0;
			for (PathElement el : getMethod().getSignature()) {
				switch (el.getType()) {
					case PATH:
						// The matching path is longer or not equal to the
						// requested path
						if (pathParts.size() <= parts || !el.getName().equals(pathParts.get(parts++))) {
							return null;
						}
						break;
					case VARIABLE:
						// The matching path is longer than the requested path
						if (pathParts.size() <= parts) {
							return null;
						}
						
						String value = pathParts.get(parts++);
						if (value.isEmpty()) {
							return null;
						}
						
						matches.put(el.getName(), value);
						break;
					case MATRIX:
						if (matrix != null && matrix.containsKey(el.getName())) {
							matches.put(el.getName(), matrix.get(el.getName()));
							matrixCounter--;
						} else if (el.isOptional()) {
							matches.put(el.getName(), el.getDefaultValue());
						} else {
							return null;
						}
						break;
					case QUERY:
						if (matrixCounter != 0) {
							return null;
						}
						
						if (query != null && query.containsKey(el.getName())) {
							matches.put(el.getName(), query.get(el.getName()));
						} else if (el.isOptional()) {
							matches.put(el.getName(), el.getDefaultValue());
						} else {
							return null;
						}
						break;
				}
			}
			
			return matches;
		}
	}
}