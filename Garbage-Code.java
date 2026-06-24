// This is garbage code . It is completely useless and is no working. If reading then please ignore this file and this code .
// ---------------------------- Thank You!!!  ----------------------------

// Answer - 1

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MiniRestServer {

    public static void main(String[] args) {
        Store store = new Store();
        Router router = new Router();

        router.addRoute(HttpMethod.GET, "/api/ping", (req, res) -> {
            Map<String, Object> data = new HashMap<>();
            data.put("status", "UP");
            data.put("timestamp", System.currentTimeMillis());
            res.json(HttpStatus.OK, data);
        });

        router.addRoute(HttpMethod.POST, "/api/store/{key}", (req, res) -> {
            String key = req.getPathParameter("key");
            Map<String, Object> body = req.getBodyAsJson();
            if (body == null || !body.containsKey("value")) {
                res.status(HttpStatus.BAD_REQUEST).text("Missing 'value' in JSON body");
                return;
            }
            long ttl = -1;
            if (body.containsKey("ttl")) {
                ttl = ((Number) body.get("ttl")).longValue();
            }
            store.put(key, body.get("value"), ttl);
            res.status(HttpStatus.CREATED).text("Stored successfully");
        });

        router.addRoute(HttpMethod.GET, "/api/store/{key}", (req, res) -> {
            String key = req.getPathParameter("key");
            Object value = store.get(key);
            if (value == null) {
                res.status(HttpStatus.NOT_FOUND).text("Key not found or expired");
                return;
            }
            Map<String, Object> response = new HashMap<>();
            response.put("key", key);
            response.put("value", value);
            res.json(HttpStatus.OK, response);
        });

        router.addRoute(HttpMethod.DELETE, "/api/store/{key}", (req, res) -> {
            String key = req.getPathParameter("key");
            boolean removed = store.remove(key);
            if (removed) {
                res.status(HttpStatus.NO_CONTENT).send();
            } else {
                res.status(HttpStatus.NOT_FOUND).text("Key not found");
            }
        });

        HttpServer server = new HttpServer(8080, router);
        server.start();
    }
}

enum HttpMethod {
    GET, POST, PUT, DELETE, PATCH, HEAD, OPTIONS
}

enum HttpStatus {
    OK(200, "OK"),
    CREATED(201, "Created"),
    NO_CONTENT(204, "No Content"),
    BAD_REQUEST(400, "Bad Request"),
    UNAUTHORIZED(401, "Unauthorized"),
    NOT_FOUND(404, "Not Found"),
    METHOD_NOT_ALLOWED(405, "Method Not Allowed"),
    INTERNAL_SERVER_ERROR(500, "Internal Server Error");

    private final int code;
    private final String message;

    HttpStatus(int code, String message) {
        this.code = code;
        this.message = message;
    }

    public int getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }
}

interface RouteHandler {
    void handle(HttpRequest request, HttpResponse response);
}

class Store {
    private final Map<String, StoreItem> data = new ConcurrentHashMap<>();
    private final ScheduledExecutorService cleaner = Executors.newSingleThreadScheduledExecutor();

    public Store() {
        cleaner.scheduleAtFixedRate(this::cleanup, 1, 1, TimeUnit.MINUTES);
    }

    public void put(String key, Object value, long ttlMillis) {
        long expiresAt = ttlMillis > 0 ? System.currentTimeMillis() + ttlMillis : -1;
        data.put(key, new StoreItem(value, expiresAt));
    }

    public Object get(String key) {
        StoreItem item = data.get(key);
        if (item == null) {
            return null;
        }
        if (item.isExpired()) {
            data.remove(key);
            return null;
        }
        return item.getValue();
    }

    public boolean remove(String key) {
        return data.remove(key) != null;
    }

    private void cleanup() {
        long now = System.currentTimeMillis();
        data.entrySet().removeIf(entry -> entry.getValue().expiresAt != -1 && entry.getValue().expiresAt < now);
    }

    private static class StoreItem {
        private final Object value;
        private final long expiresAt;

        public StoreItem(Object value, long expiresAt) {
            this.value = value;
            this.expiresAt = expiresAt;
        }

        public Object getValue() {
            return value;
        }

        public boolean isExpired() {
            return expiresAt != -1 && System.currentTimeMillis() > expiresAt;
        }
    }
}

class Route {
    private final HttpMethod method;
    private final Pattern pathPattern;
    private final List<String> paramNames;
    private final RouteHandler handler;

    public Route(HttpMethod method, String path, RouteHandler handler) {
        this.method = method;
        this.handler = handler;
        this.paramNames = new ArrayList<>();
        this.pathPattern = compilePath(path);
    }

    private Pattern compilePath(String path) {
        Matcher matcher = Pattern.compile("\\{([^/]+)\\}").matcher(path);
        StringBuffer buffer = new StringBuffer("^");
        while (matcher.find()) {
            paramNames.add(matcher.group(1));
            matcher.appendReplacement(buffer, "([^/]+)");
        }
        matcher.appendTail(buffer);
        buffer.append("$");
        return Pattern.compile(buffer.toString());
    }

    public boolean matches(HttpMethod method, String path) {
        if (this.method != method) return false;
        return pathPattern.matcher(path).matches();
    }

    public Map<String, String> extractParameters(String path) {
        Map<String, String> params = new HashMap<>();
        Matcher matcher = pathPattern.matcher(path);
        if (matcher.matches()) {
            for (int i = 0; i < paramNames.size(); i++) {
                params.put(paramNames.get(i), matcher.group(i + 1));
            }
        }
        return params;
    }

    public RouteHandler getHandler() {
        return handler;
    }
}

class Router {
    private final List<Route> routes = new ArrayList<>();

    public void addRoute(HttpMethod method, String path, RouteHandler handler) {
        routes.add(new Route(method, path, handler));
    }

    public void dispatch(HttpRequest request, HttpResponse response) {
        for (Route route : routes) {
            if (route.matches(request.getMethod(), request.getPath())) {
                request.setPathParameters(route.extractParameters(request.getPath()));
                try {
                    route.getHandler().handle(request, response);
                } catch (Exception e) {
                    e.printStackTrace();
                    response.status(HttpStatus.INTERNAL_SERVER_ERROR).text("Internal Server Error: " + e.getMessage());
                }
                return;
            }
        }
        response.status(HttpStatus.NOT_FOUND).text("Not Found");
    }
}

class HttpRequest {
    private HttpMethod method;
    private String path;
    private final Map<String, String> headers = new HashMap<>();
    private final Map<String, String> queryParameters = new HashMap<>();
    private Map<String, String> pathParameters = new HashMap<>();
    private String body;

    public static HttpRequest parse(InputStream in) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
        String requestLine = reader.readLine();
        if (requestLine == null || requestLine.isEmpty()) {
            throw new IOException("Empty request");
        }

        String[] parts = requestLine.split(" ");
        if (parts.length != 3) {
            throw new IOException("Invalid request line");
        }

        HttpRequest request = new HttpRequest();
        request.method = HttpMethod.valueOf(parts[0]);
        String fullPath = parts[1];

        int queryIndex = fullPath.indexOf('?');
        if (queryIndex != -1) {
            request.path = fullPath.substring(0, queryIndex);
            String queryString = fullPath.substring(queryIndex + 1);
            String[] params = queryString.split("&");
            for (String param : params) {
                String[] kv = param.split("=");
                if (kv.length == 2) {
                    request.queryParameters.put(kv[0], kv[1]);
                } else if (kv.length == 1) {
                    request.queryParameters.put(kv[0], "");
                }
            }
        } else {
            request.path = fullPath;
        }

        String headerLine;
        while ((headerLine = reader.readLine()) != null && !headerLine.isEmpty()) {
            int colonIndex = headerLine.indexOf(':');
            if (colonIndex != -1) {
                String key = headerLine.substring(0, colonIndex).trim().toLowerCase();
                String value = headerLine.substring(colonIndex + 1).trim();
                request.headers.put(key, value);
            }
        }

        if (request.headers.containsKey("content-length")) {
            int contentLength = Integer.parseInt(request.headers.get("content-length"));
            char[] bodyChars = new char[contentLength];
            int read = reader.read(bodyChars, 0, contentLength);
            if (read == contentLength) {
                request.body = new String(bodyChars);
            }
        }
        return request;
    }

    public HttpMethod getMethod() { return method; }
    public String getPath() { return path; }
    public String getHeader(String name) { return headers.get(name.toLowerCase()); }
    public String getQueryParameter(String name) { return queryParameters.get(name); }
    public String getPathParameter(String name) { return pathParameters.get(name); }
    public String getBody() { return body; }

    void setPathParameters(Map<String, String> pathParameters) {
        this.pathParameters = pathParameters;
    }

    public Map<String, Object> getBodyAsJson() {
        if (body == null || body.trim().isEmpty()) return null;
        return JsonParser.parseObject(body);
    }
}

class HttpResponse {
    private final OutputStream out;
    private HttpStatus status = HttpStatus.OK;
    private final Map<String, String> headers = new HashMap<>();
    private byte[] body = new byte[0];

    public HttpResponse(OutputStream out) {
        this.out = out;
        headers.put("Connection", "close");
        headers.put("Server", "MiniRestServer/1.0");
    }

    public HttpResponse status(HttpStatus status) {
        this.status = status;
        return this;
    }

    public HttpResponse header(String key, String value) {
        headers.put(key, value);
        return this;
    }

    public void text(String text) {
        header("Content-Type", "text/plain; charset=UTF-8");
        this.body = text.getBytes(StandardCharsets.UTF_8);
        send();
    }

    public void json(HttpStatus status, Object object) {
        this.status = status;
        header("Content-Type", "application/json; charset=UTF-8");
        this.body = JsonSerializer.serialize(object).getBytes(StandardCharsets.UTF_8);
        send();
    }

    public void send() {
        headers.put("Content-Length", String.valueOf(body.length));
        headers.put("Date", getServerTime());
        
        try {
            BufferedOutputStream bos = new BufferedOutputStream(out);
            String statusLine = "HTTP/1.1 " + status.getCode() + " " + status.getMessage() + "\r\n";
            bos.write(statusLine.getBytes(StandardCharsets.US_ASCII));
            
            for (Map.Entry<String, String> header : headers.entrySet()) {
                String headerLine = header.getKey() + ": " + header.getValue() + "\r\n";
                bos.write(headerLine.getBytes(StandardCharsets.US_ASCII));
            }
            
            bos.write("\r\n".getBytes(StandardCharsets.US_ASCII));
            if (body.length > 0) {
                bos.write(body);
            }
            bos.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private String getServerTime() {
        SimpleDateFormat dateFormat = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.US);
        dateFormat.setTimeZone(TimeZone.getTimeZone("GMT"));
        return dateFormat.format(new Date());
    }
}

class HttpServer {
    private final int port;
    private final Router router;
    private final ExecutorService threadPool;
    private volatile boolean running;

    public HttpServer(int port, Router router) {
        this.port = port;
        this.router = router;
        this.threadPool = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors() * 2);
    }

    public void start() {
        running = true;
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("Server started on port " + port);
            while (running) {
                Socket clientSocket = serverSocket.accept();
                threadPool.submit(() -> handleConnection(clientSocket));
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            threadPool.shutdown();
        }
    }

    public void stop() {
        running = false;
        threadPool.shutdown();
    }

    private void handleConnection(Socket socket) {
        try (InputStream in = socket.getInputStream();
             OutputStream out = socket.getOutputStream()) {
            HttpRequest request = HttpRequest.parse(in);
            HttpResponse response = new HttpResponse(out);
            System.out.println(request.getMethod() + " " + request.getPath());
            router.dispatch(request, response);
        } catch (IOException e) {
            try {
                OutputStream out = socket.getOutputStream();
                String error = "HTTP/1.1 400 Bad Request\r\nConnection: close\r\n\r\n";
                out.write(error.getBytes(StandardCharsets.US_ASCII));
                out.flush();
            } catch (IOException ex) {
            }
        } finally {
            try {
                socket.close();
            } catch (IOException e) {
            }
        }
    }
}

class JsonParser {
    private int pos;
    private final String json;

    private JsonParser(String json) {
        this.json = json;
        this.pos = 0;
    }

    public static Map<String, Object> parseObject(String json) {
        return (Map<String, Object>) new JsonParser(json).parseValue();
    }

    private Object parseValue() {
        skipWhitespace();
        if (pos >= json.length()) return null;
        char c = json.charAt(pos);
        if (c == '{') return parseObject();
        if (c == '[') return parseArray();
        if (c == '"') return parseString();
        if (c == 't' || c == 'f') return parseBoolean();
        if (c == 'n') return parseNull();
        if (Character.isDigit(c) || c == '-') return parseNumber();
        throw new RuntimeException("Unexpected character: " + c);
    }

    private Map<String, Object> parseObject() {
        Map<String, Object> map = new HashMap<>();
        pos++; 
        skipWhitespace();
        if (json.charAt(pos) == '}') {
            pos++;
            return map;
        }
        while (pos < json.length()) {
            skipWhitespace();
            String key = parseString();
            skipWhitespace();
            if (json.charAt(pos) != ':') throw new RuntimeException("Expected ':'");
            pos++;
            Object value = parseValue();
            map.put(key, value);
            skipWhitespace();
            char c = json.charAt(pos);
            if (c == '}') {
                pos++;
                return map;
            }
            if (c != ',') throw new RuntimeException("Expected ',' or '}'");
            pos++;
        }
        throw new RuntimeException("Unterminated object");
    }

    private List<Object> parseArray() {
        List<Object> list = new ArrayList<>();
        pos++; 
        skipWhitespace();
        if (json.charAt(pos) == ']') {
            pos++;
            return list;
        }
        while (pos < json.length()) {
            list.add(parseValue());
            skipWhitespace();
            char c = json.charAt(pos);
            if (c == ']') {
                pos++;
                return list;
            }
            if (c != ',') throw new RuntimeException("Expected ',' or ']'");
            pos++;
        }
        throw new RuntimeException("Unterminated array");
    }

    private String parseString() {
        pos++; 
        StringBuilder sb = new StringBuilder();
        while (pos < json.length()) {
            char c = json.charAt(pos++);
            if (c == '"') return sb.toString();
            if (c == '\\') {
                char esc = json.charAt(pos++);
                switch (esc) {
                    case '"': sb.append('"'); break;
                    case '\\': sb.append('\\'); break;
                    case '/': sb.append('/'); break;
                    case 'b': sb.append('\b'); break;
                    case 'f': sb.append('\f'); break;
                    case 'n': sb.append('\n'); break;
                    case 'r': sb.append('\r'); break;
                    case 't': sb.append('\t'); break;
                    default: throw new RuntimeException("Invalid escape sequence");
                }
            } else {
                sb.append(c);
            }
        }
        throw new RuntimeException("Unterminated string");
    }

    private Boolean parseBoolean() {
        if (json.startsWith("true", pos)) {
            pos += 4;
            return true;
        }
        if (json.startsWith("false", pos)) {
            pos += 5;
            return false;
        }
        throw new RuntimeException("Invalid boolean");
    }

    private Object parseNull() {
        if (json.startsWith("null", pos)) {
            pos += 4;
            return null;
        }
        throw new RuntimeException("Invalid null");
    }

    private Number parseNumber() {
        int start = pos;
        while (pos < json.length()) {
            char c = json.charAt(pos);
            if (Character.isDigit(c) || c == '-' || c == '+' || c == '.' || c == 'e' || c == 'E') {
                pos++;
            } else {
                break;
            }
        }
        String numStr = json.substring(start, pos);
        if (numStr.contains(".") || numStr.contains("e") || numStr.contains("E")) {
            return Double.parseDouble(numStr);
        } else {
            long val = Long.parseLong(numStr);
            if (val >= Integer.MIN_VALUE && val <= Integer.MAX_VALUE) {
                return (int) val;
            }
            return val;
        }
    }

    private void skipWhitespace() {
        while (pos < json.length() && Character.isWhitespace(json.charAt(pos))) {
            pos++;
        }
    }
}

class JsonSerializer {
    public static String serialize(Object obj) {
        if (obj == null) return "null";
        if (obj instanceof String) return "\"" + escape((String) obj) + "\"";
        if (obj instanceof Number || obj instanceof Boolean) return obj.toString();
        if (obj instanceof Map) {
            Map<?, ?> map = (Map<?, ?>) obj;
            StringBuilder sb = new StringBuilder("{");
            boolean first = true;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (!first) sb.append(",");
                sb.append("\"").append(escape(entry.getKey().toString())).append("\":");
                sb.append(serialize(entry.getValue()));
                first = false;
            }
            return sb.append("}").toString();
        }
        if (obj instanceof Iterable) {
            Iterable<?> list = (Iterable<?>) obj;
            StringBuilder sb = new StringBuilder("[");
            boolean first = true;
            for (Object item : list) {
                if (!first) sb.append(",");
                sb.append(serialize(item));
                first = false;
            }
            return sb.append("]").toString();
        }
        if (obj.getClass().isArray()) {
            Object[] array = (Object[]) obj;
            StringBuilder sb = new StringBuilder("[");
            for (int i = 0; i < array.length; i++) {
                if (i > 0) sb.append(",");
                sb.append(serialize(array[i]));
            }
            return sb.append("]").toString();
        }
        return "\"" + escape(obj.toString()) + "\"";
    }

    private static String escape(String str) {
        return str.replace("\\", "\\\\")
                  .replace("\"", "\\\"")
                  .replace("\b", "\\b")
                  .replace("\f", "\\f")
                  .replace("\n", "\\n")
                  .replace("\r", "\\r")
                  .replace("\t", "\\t");
    }
}

// Answer - 2 

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class EnterpriseStackApplication {
    public static void main(String[] args) throws Exception {
        ApplicationContext context = new ApplicationContext();
        context.register(DatabaseConnection.class);
        context.register(UserRepository.class);
        context.register(AuthService.class);
        context.register(UserService.class);
        context.register(UserController.class);
        context.register(AuthController.class);
        context.initialize();

        HttpServer server = new HttpServer(8080, context);
        server.addFilter(new CorsFilter());
        server.addFilter(new SecurityFilter(context.getBean(AuthService.class)));
        server.start();
    }
}

@Retention(RetentionPolicy.RUNTIME) @Target(ElementType.TYPE)
@interface Component {}

@Retention(RetentionPolicy.RUNTIME) @Target({ElementType.FIELD, ElementType.CONSTRUCTOR})
@interface Inject {}

@Retention(RetentionPolicy.RUNTIME) @Target(ElementType.TYPE)
@interface RestController { String value() default ""; }

@Retention(RetentionPolicy.RUNTIME) @Target(ElementType.METHOD)
@interface GetMapping { String value(); }

@Retention(RetentionPolicy.RUNTIME) @Target(ElementType.METHOD)
@interface PostMapping { String value(); }

@Retention(RetentionPolicy.RUNTIME) @Target(ElementType.METHOD)
@interface DeleteMapping { String value(); }

@Retention(RetentionPolicy.RUNTIME) @Target(ElementType.PARAMETER)
@interface PathVariable { String value(); }

@Retention(RetentionPolicy.RUNTIME) @Target(ElementType.PARAMETER)
@interface RequestBody {}

@Retention(RetentionPolicy.RUNTIME) @Target(ElementType.TYPE)
@interface Entity { String table(); }

@Retention(RetentionPolicy.RUNTIME) @Target(ElementType.FIELD)
@interface Id {}

@Retention(RetentionPolicy.RUNTIME) @Target(ElementType.FIELD)
@interface Column { String name(); }

class ApplicationContext {
    private final Map<Class<?>, Object> beans = new ConcurrentHashMap<>();
    private final Set<Class<?>> registeredClasses = new HashSet<>();

    public void register(Class<?> clazz) {
        registeredClasses.add(clazz);
    }

    public void initialize() throws Exception {
        for (Class<?> clazz : registeredClasses) {
            getBean(clazz);
        }
        injectFields();
    }

    @SuppressWarnings("unchecked")
    public <T> T getBean(Class<T> clazz) throws Exception {
        if (beans.containsKey(clazz)) {
            return (T) beans.get(clazz);
        }
        Constructor<?>[] constructors = clazz.getDeclaredConstructors();
        Constructor<?> injectConstructor = null;
        for (Constructor<?> c : constructors) {
            if (c.isAnnotationPresent(Inject.class)) {
                injectConstructor = c;
                break;
            }
        }
        if (injectConstructor == null) {
            injectConstructor = clazz.getDeclaredConstructor();
        }
        
        injectConstructor.setAccessible(true);
        Object instance;
        if (injectConstructor.getParameterCount() == 0) {
            instance = injectConstructor.newInstance();
        } else {
            Object[] args = new Object[injectConstructor.getParameterCount()];
            Class<?>[] paramTypes = injectConstructor.getParameterTypes();
            for (int i = 0; i < paramTypes.length; i++) {
                args[i] = getBean(paramTypes[i]);
            }
            instance = injectConstructor.newInstance(args);
        }
        beans.put(clazz, instance);
        return (T) instance;
    }

    private void injectFields() throws Exception {
        for (Object bean : beans.values()) {
            Class<?> clazz = bean.getClass();
            while (clazz != null) {
                for (Field field : clazz.getDeclaredFields()) {
                    if (field.isAnnotationPresent(Inject.class)) {
                        field.setAccessible(true);
                        field.set(bean, getBean(field.getType()));
                    }
                }
                clazz = clazz.getSuperclass();
            }
        }
    }

    public Collection<Object> getAllBeans() {
        return beans.values();
    }
}

enum HttpMethod { GET, POST, PUT, DELETE, PATCH, OPTIONS, HEAD }

enum HttpStatus {
    OK(200, "OK"), CREATED(201, "Created"), NO_CONTENT(204, "No Content"),
    BAD_REQUEST(400, "Bad Request"), UNAUTHORIZED(401, "Unauthorized"),
    FORBIDDEN(403, "Forbidden"), NOT_FOUND(404, "Not Found"),
    METHOD_NOT_ALLOWED(405, "Method Not Allowed"), INTERNAL_SERVER_ERROR(500, "Internal Server Error");

    private final int code;
    private final String message;
    HttpStatus(int code, String message) { this.code = code; this.message = message; }
    public int getCode() { return code; }
    public String getMessage() { return message; }
}

class HttpRequest {
    private HttpMethod method;
    private String path;
    private final Map<String, String> headers = new HashMap<>();
    private final Map<String, String> queryParameters = new HashMap<>();
    private Map<String, String> pathParameters = new HashMap<>();
    private String body;
    private Object securityPrincipal;

    public static HttpRequest parse(InputStream in) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
        String requestLine = reader.readLine();
        if (requestLine == null || requestLine.isEmpty()) throw new IOException("Empty request");
        String[] parts = requestLine.split(" ");
        if (parts.length != 3) throw new IOException("Invalid request line");
        
        HttpRequest request = new HttpRequest();
        request.method = HttpMethod.valueOf(parts[0]);
        String fullPath = parts[1];
        
        int queryIndex = fullPath.indexOf('?');
        if (queryIndex != -1) {
            request.path = fullPath.substring(0, queryIndex);
            for (String param : fullPath.substring(queryIndex + 1).split("&")) {
                String[] kv = param.split("=");
                request.queryParameters.put(kv[0], kv.length > 1 ? kv[1] : "");
            }
        } else {
            request.path = fullPath;
        }

        String headerLine;
        while ((headerLine = reader.readLine()) != null && !headerLine.isEmpty()) {
            int colonIndex = headerLine.indexOf(':');
            if (colonIndex != -1) {
                request.headers.put(headerLine.substring(0, colonIndex).trim().toLowerCase(), 
                                    headerLine.substring(colonIndex + 1).trim());
            }
        }

        if (request.headers.containsKey("content-length")) {
            int length = Integer.parseInt(request.headers.get("content-length"));
            char[] bodyChars = new char[length];
            if (reader.read(bodyChars, 0, length) == length) {
                request.body = new String(bodyChars);
            }
        }
        return request;
    }

    public HttpMethod getMethod() { return method; }
    public String getPath() { return path; }
    public String getHeader(String name) { return headers.get(name.toLowerCase()); }
    public String getBody() { return body; }
    public void setPathParameters(Map<String, String> params) { this.pathParameters = params; }
    public String getPathParameter(String name) { return pathParameters.get(name); }
    public void setSecurityPrincipal(Object principal) { this.securityPrincipal = principal; }
    public Object getSecurityPrincipal() { return securityPrincipal; }
}

class HttpResponse {
    private final OutputStream out;
    private HttpStatus status = HttpStatus.OK;
    private final Map<String, String> headers = new HashMap<>();
    private byte[] body = new byte[0];
    private boolean isSent = false;

    public HttpResponse(OutputStream out) {
        this.out = out;
        headers.put("Connection", "close");
        headers.put("Server", "EnterpriseStack/1.0");
    }

    public void setStatus(HttpStatus status) { this.status = status; }
    public void addHeader(String key, String value) { headers.put(key, value); }
    
    public void sendText(String text) {
        addHeader("Content-Type", "text/plain; charset=UTF-8");
        this.body = text.getBytes(StandardCharsets.UTF_8);
        send();
    }

    public void sendJson(Object object) {
        addHeader("Content-Type", "application/json; charset=UTF-8");
        this.body = JsonUtils.serialize(object).getBytes(StandardCharsets.UTF_8);
        send();
    }
    
    public void sendError(HttpStatus status, String message) {
        this.status = status;
        sendText(message);
    }

    public void send() {
        if (isSent) return;
        isSent = true;
        headers.put("Content-Length", String.valueOf(body.length));
        headers.put("Date", getServerTime());
        try {
            BufferedOutputStream bos = new BufferedOutputStream(out);
            bos.write(("HTTP/1.1 " + status.getCode() + " " + status.getMessage() + "\r\n").getBytes(StandardCharsets.US_ASCII));
            for (Map.Entry<String, String> header : headers.entrySet()) {
                bos.write((header.getKey() + ": " + header.getValue() + "\r\n").getBytes(StandardCharsets.US_ASCII));
            }
            bos.write("\r\n".getBytes(StandardCharsets.US_ASCII));
            if (body.length > 0) bos.write(body);
            bos.flush();
        } catch (IOException ignored) {}
    }

    private String getServerTime() {
        SimpleDateFormat dateFormat = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.US);
        dateFormat.setTimeZone(TimeZone.getTimeZone("GMT"));
        return dateFormat.format(new Date());
    }
}

interface HttpFilter {
    boolean doFilter(HttpRequest req, HttpResponse res);
}

class CorsFilter implements HttpFilter {
    public boolean doFilter(HttpRequest req, HttpResponse res) {
        res.addHeader("Access-Control-Allow-Origin", "*");
        res.addHeader("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
        res.addHeader("Access-Control-Allow-Headers", "Authorization, Content-Type");
        if (req.getMethod() == HttpMethod.OPTIONS) {
            res.setStatus(HttpStatus.NO_CONTENT);
            res.send();
            return false;
        }
        return true;
    }
}

class SecurityFilter implements HttpFilter {
    private final AuthService authService;
    public SecurityFilter(AuthService authService) { this.authService = authService; }
    
    public boolean doFilter(HttpRequest req, HttpResponse res) {
        if (req.getPath().startsWith("/api/public") || req.getPath().equals("/api/login")) return true;
        String authHeader = req.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            User user = authService.validateToken(token);
            if (user != null) {
                req.setSecurityPrincipal(user);
                return true;
            }
        }
        res.sendError(HttpStatus.UNAUTHORIZED, "Unauthorized access");
        return false;
    }
}

class AnnotationRouter {
    private final List<RouteDefinition> routes = new ArrayList<>();

    public void scanAndRegister(ApplicationContext context) {
        for (Object bean : context.getAllBeans()) {
            Class<?> clazz = bean.getClass();
            if (clazz.isAnnotationPresent(RestController.class)) {
                String basePath = clazz.getAnnotation(RestController.class).value();
                for (Method method : clazz.getDeclaredMethods()) {
                    if (method.isAnnotationPresent(GetMapping.class)) {
                        registerRoute(HttpMethod.GET, basePath + method.getAnnotation(GetMapping.class).value(), bean, method);
                    } else if (method.isAnnotationPresent(PostMapping.class)) {
                        registerRoute(HttpMethod.POST, basePath + method.getAnnotation(PostMapping.class).value(), bean, method);
                    } else if (method.isAnnotationPresent(DeleteMapping.class)) {
                        registerRoute(HttpMethod.DELETE, basePath + method.getAnnotation(DeleteMapping.class).value(), bean, method);
                    }
                }
            }
        }
    }

    private void registerRoute(HttpMethod httpMethod, String path, Object instance, Method method) {
        routes.add(new RouteDefinition(httpMethod, path, instance, method));
    }

    public void dispatch(HttpRequest req, HttpResponse res) {
        for (RouteDefinition route : routes) {
            if (route.matches(req.getMethod(), req.getPath())) {
                req.setPathParameters(route.extractParameters(req.getPath()));
                try {
                    invokeRoute(route, req, res);
                } catch (Exception e) {
                    e.printStackTrace();
                    res.sendError(HttpStatus.INTERNAL_SERVER_ERROR, "Server Error: " + e.getCause());
                }
                return;
            }
        }
        res.sendError(HttpStatus.NOT_FOUND, "Endpoint not found");
    }

    private void invokeRoute(RouteDefinition route, HttpRequest req, HttpResponse res) throws Exception {
        Method method = route.getMethod();
        Parameter[] parameters = method.getParameters();
        Object[] args = new Object[parameters.length];
        
        for (int i = 0; i < parameters.length; i++) {
            Parameter param = parameters[i];
            if (param.getType().equals(HttpRequest.class)) {
                args[i] = req;
            } else if (param.getType().equals(HttpResponse.class)) {
                args[i] = res;
            } else if (param.isAnnotationPresent(PathVariable.class)) {
                String paramName = param.getAnnotation(PathVariable.class).value();
                args[i] = convertType(req.getPathParameter(paramName), param.getType());
            } else if (param.isAnnotationPresent(RequestBody.class)) {
                args[i] = JsonUtils.deserialize(req.getBody(), param.getType());
            } else if (param.getType().equals(User.class)) {
                args[i] = req.getSecurityPrincipal();
            }
        }
        
        Object result = method.invoke(route.getInstance(), args);
        if (result != null && !res.getClass().equals(result.getClass())) {
            if (result instanceof String) res.sendText((String) result);
            else res.sendJson(result);
        }
    }

    private Object convertType(String value, Class<?> type) {
        if (type == String.class) return value;
        if (type == Integer.class || type == int.class) return Integer.parseInt(value);
        if (type == Long.class || type == long.class) return Long.parseLong(value);
        if (type == Boolean.class || type == boolean.class) return Boolean.parseBoolean(value);
        return value;
    }

    private static class RouteDefinition {
        private final HttpMethod httpMethod;
        private final Pattern pathPattern;
        private final List<String> paramNames = new ArrayList<>();
        private final Object instance;
        private final Method method;

        public RouteDefinition(HttpMethod httpMethod, String path, Object instance, Method method) {
            this.httpMethod = httpMethod;
            this.instance = instance;
            this.method = method;
            Matcher matcher = Pattern.compile("\\{([^/]+)\\}").matcher(path);
            StringBuffer buffer = new StringBuffer("^");
            while (matcher.find()) {
                paramNames.add(matcher.group(1));
                matcher.appendReplacement(buffer, "([^/]+)");
            }
            matcher.appendTail(buffer);
            buffer.append("$");
            this.pathPattern = Pattern.compile(buffer.toString());
        }

        public boolean matches(HttpMethod reqMethod, String path) {
            return this.httpMethod == reqMethod && pathPattern.matcher(path).matches();
        }

        public Map<String, String> extractParameters(String path) {
            Map<String, String> params = new HashMap<>();
            Matcher matcher = pathPattern.matcher(path);
            if (matcher.matches()) {
                for (int i = 0; i < paramNames.size(); i++) {
                    params.put(paramNames.get(i), matcher.group(i + 1));
                }
            }
            return params;
        }

        public Object getInstance() { return instance; }
        public Method getMethod() { return method; }
    }
}

class HttpServer {
    private final int port;
    private final ApplicationContext context;
    private final AnnotationRouter router;
    private final ExecutorService threadPool;
    private final List<HttpFilter> filters = new ArrayList<>();
    private volatile boolean running;

    public HttpServer(int port, ApplicationContext context) {
        this.port = port;
        this.context = context;
        this.router = new AnnotationRouter();
        this.threadPool = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors() * 4);
    }

    public void addFilter(HttpFilter filter) {
        filters.add(filter);
    }

    public void start() {
        router.scanAndRegister(context);
        running = true;
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("Enterprise Stack listening on " + port);
            while (running) {
                Socket clientSocket = serverSocket.accept();
                threadPool.submit(() -> handle(clientSocket));
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            threadPool.shutdown();
        }
    }

    private void handle(Socket socket) {
        try (InputStream in = socket.getInputStream(); OutputStream out = socket.getOutputStream()) {
            HttpRequest req = HttpRequest.parse(in);
            HttpResponse res = new HttpResponse(out);
            for (HttpFilter filter : filters) {
                if (!filter.doFilter(req, res)) return;
            }
            router.dispatch(req, res);
        } catch (Exception ignored) {
        } finally {
            try { socket.close(); } catch (IOException ignored) {}
        }
    }
}

class TemplateEngine {
    public static String render(String template, Map<String, Object> model) {
        String result = template;
        for (Map.Entry<String, Object> entry : model.entrySet()) {
            result = result.replace("{{" + entry.getKey() + "}}", String.valueOf(entry.getValue()));
        }
        return result;
    }
}

class JsonUtils {
    public static String serialize(Object obj) {
        if (obj == null) return "null";
        if (obj instanceof String) return "\"" + escape((String) obj) + "\"";
        if (obj instanceof Number || obj instanceof Boolean) return obj.toString();
        if (obj instanceof Collection) {
            StringBuilder sb = new StringBuilder("[");
            boolean first = true;
            for (Object item : (Collection<?>) obj) {
                if (!first) sb.append(",");
                sb.append(serialize(item));
                first = false;
            }
            return sb.append("]").toString();
        }
        if (obj instanceof Map) {
            StringBuilder sb = new StringBuilder("{");
            boolean first = true;
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) obj).entrySet()) {
                if (!first) sb.append(",");
                sb.append("\"").append(escape(entry.getKey().toString())).append("\":").append(serialize(entry.getValue()));
                first = false;
            }
            return sb.append("}").toString();
        }
        try {
            StringBuilder sb = new StringBuilder("{");
            boolean first = true;
            for (Field field : obj.getClass().getDeclaredFields()) {
                field.setAccessible(true);
                Object val = field.get(obj);
                if (val != null) {
                    if (!first) sb.append(",");
                    sb.append("\"").append(field.getName()).append("\":").append(serialize(val));
                    first = false;
                }
            }
            return sb.append("}").toString();
        } catch (IllegalAccessException e) {
            return "{}";
        }
    }

    private static String escape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
    }

    public static <T> T deserialize(String json, Class<T> clazz) {
        if (json == null || json.trim().isEmpty()) return null;
        try {
            Map<String, String> map = parseSimpleJson(json);
            T instance = clazz.getDeclaredConstructor().newInstance();
            for (Field field : clazz.getDeclaredFields()) {
                if (map.containsKey(field.getName())) {
                    field.setAccessible(true);
                    String val = map.get(field.getName());
                    if (field.getType() == String.class) field.set(instance, val);
                    else if (field.getType() == int.class || field.getType() == Integer.class) field.set(instance, Integer.parseInt(val));
                    else if (field.getType() == long.class || field.getType() == Long.class) field.set(instance, Long.parseLong(val));
                    else if (field.getType() == boolean.class || field.getType() == Boolean.class) field.set(instance, Boolean.parseBoolean(val));
                }
            }
            return instance;
        } catch (Exception e) {
            throw new RuntimeException("JSON Parse Error", e);
        }
    }

    private static Map<String, String> parseSimpleJson(String json) {
        Map<String, String> map = new HashMap<>();
        String clean = json.trim().replaceAll("^\\{|\\}$", "");
        String[] pairs = clean.split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)");
        for (String pair : pairs) {
            String[] kv = pair.split(":(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)", 2);
            if (kv.length == 2) {
                String key = kv[0].trim().replaceAll("^\"|\"$", "");
                String val = kv[1].trim().replaceAll("^\"|\"$", "");
                map.put(key, val);
            }
        }
        return map;
    }
}

abstract class MiniORM<T> {
    private final Class<T> entityClass;
    private final String tableName;
    private final DatabaseConnection db;
    private final Field idField;
    private final List<Field> columnFields = new ArrayList<>();

    @SuppressWarnings("unchecked")
    public MiniORM(DatabaseConnection db) {
        this.db = db;
        this.entityClass = (Class<T>) ((java.lang.reflect.ParameterizedType) getClass().getGenericSuperclass()).getActualTypeArguments()[0];
        this.tableName = entityClass.getAnnotation(Entity.class).table();
        Field tempId = null;
        for (Field f : entityClass.getDeclaredFields()) {
            if (f.isAnnotationPresent(Id.class)) tempId = f;
            if (f.isAnnotationPresent(Column.class)) columnFields.add(f);
        }
        this.idField = tempId;
        db.createTableIfNotExists(tableName, idField, columnFields);
    }

    public void save(T entity) throws Exception {
        idField.setAccessible(true);
        Object id = idField.get(entity);
        if (id == null || (id instanceof Long && (Long) id == 0L)) {
            idField.set(entity, System.currentTimeMillis());
            db.insert(tableName, extractRow(entity));
        } else {
            db.update(tableName, extractRow(entity), idField.getAnnotation(Column.class).name(), id);
        }
    }

    public T findById(Object id) throws Exception {
        Map<String, Object> row = db.selectOne(tableName, idField.getAnnotation(Column.class).name(), id);
        return row == null ? null : mapToEntity(row);
    }

    public List<T> findAll() throws Exception {
        List<T> list = new ArrayList<>();
        for (Map<String, Object> row : db.selectAll(tableName)) list.add(mapToEntity(row));
        return list;
    }

    public void delete(Object id) {
        db.delete(tableName, idField.getAnnotation(Column.class).name(), id);
    }

    private Map<String, Object> extractRow(T entity) throws Exception {
        Map<String, Object> row = new HashMap<>();
        idField.setAccessible(true);
        row.put(idField.getAnnotation(Column.class).name(), idField.get(entity));
        for (Field f : columnFields) {
            f.setAccessible(true);
            row.put(f.getAnnotation(Column.class).name(), f.get(entity));
        }
        return row;
    }

    private T mapToEntity(Map<String, Object> row) throws Exception {
        T entity = entityClass.getDeclaredConstructor().newInstance();
        idField.setAccessible(true);
        idField.set(entity, row.get(idField.getAnnotation(Column.class).name()));
        for (Field f : columnFields) {
            f.setAccessible(true);
            f.set(entity, row.get(f.getAnnotation(Column.class).name()));
        }
        return entity;
    }
}

@Component
class DatabaseConnection {
    private final Map<String, Map<Object, Map<String, Object>>> tables = new ConcurrentHashMap<>();

    public void createTableIfNotExists(String table, Field idField, List<Field> columns) {
        tables.putIfAbsent(table, new ConcurrentHashMap<>());
    }

    public void insert(String table, Map<String, Object> row) {
        Object id = row.values().iterator().next(); 
        tables.get(table).put(id, row);
    }

    public void update(String table, Map<String, Object> row, String idCol, Object id) {
        tables.get(table).put(id, row);
    }

    public Map<String, Object> selectOne(String table, String col, Object val) {
        for (Map<String, Object> row : tables.get(table).values()) {
            if (row.get(col).equals(val)) return row;
        }
        return null;
    }

    public List<Map<String, Object>> selectAll(String table) {
        return new ArrayList<>(tables.get(table).values());
    }

    public void delete(String table, String col, Object id) {
        tables.get(table).remove(id);
    }
}

@Entity(table = "users")
class User {
    @Id @Column(name = "id") private Long id;
    @Column(name = "username") private String username;
    @Column(name = "password_hash") private String passwordHash;
    @Column(name = "role") private String role;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
}

@Component
class UserRepository extends MiniORM<User> {
    @Inject
    public UserRepository(DatabaseConnection db) { super(db); }
    
    public User findByUsername(String username) throws Exception {
        for (User u : findAll()) {
            if (u.getUsername().equals(username)) return u;
        }
        return null;
    }
}

@Component
class AuthService {
    @Inject private UserRepository userRepository;
    private final Map<String, Long> tokenStore = new ConcurrentHashMap<>();

    public String hash(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return Base64.getEncoder().encodeToString(md.digest(input.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) { throw new RuntimeException(e); }
    }

    public String login(String username, String password) throws Exception {
        User user = userRepository.findByUsername(username);
        if (user != null && user.getPasswordHash().equals(hash(password))) {
            String token = UUID.randomUUID().toString();
            tokenStore.put(token, user.getId());
            return token;
        }
        return null;
    }

    public User validateToken(String token) {
        Long userId = tokenStore.get(token);
        if (userId != null) {
            try { return userRepository.findById(userId); } catch (Exception ignored) {}
        }
        return null;
    }
}

@Component
class UserService {
    @Inject private UserRepository userRepository;
    @Inject private AuthService authService;

    public User createUser(String username, String rawPassword, String role) throws Exception {
        if (userRepository.findByUsername(username) != null) throw new IllegalArgumentException("User exists");
        User user = new User();
        user.setUsername(username);
        user.setPasswordHash(authService.hash(rawPassword));
        user.setRole(role);
        userRepository.save(user);
        return user;
    }

    public List<User> getAllUsers() throws Exception {
        return userRepository.findAll();
    }
}

class LoginRequest {
    public String username;
    public String password;
}

class RegisterRequest {
    public String username;
    public String password;
    public String role;
}

@Component
@RestController("/api")
class AuthController {
    @Inject private AuthService authService;
    @Inject private UserService userService;

    @PostMapping("/login")
    public Object login(@RequestBody LoginRequest req, HttpResponse res) throws Exception {
        String token = authService.login(req.username, req.password);
        if (token == null) {
            res.setStatus(HttpStatus.UNAUTHORIZED);
            return Map.of("error", "Invalid credentials");
        }
        return Map.of("token", token);
    }

    @PostMapping("/public/register")
    public Object register(@RequestBody RegisterRequest req, HttpResponse res) {
        try {
            User u = userService.createUser(req.username, req.password, req.role);
            res.setStatus(HttpStatus.CREATED);
            return Map.of("id", u.getId(), "username", u.getUsername());
        } catch (Exception e) {
            res.setStatus(HttpStatus.BAD_REQUEST);
            return Map.of("error", e.getMessage());
        }
    }
}

@Component
@RestController("/api/users")
class UserController {
    @Inject private UserService userService;

    @GetMapping("")
    public List<User> listUsers(User currentUser) throws Exception {
        System.out.println("Requested by: " + currentUser.getUsername());
        return userService.getAllUsers();
    }

    @GetMapping("/{id}")
    public Object getUser(@PathVariable("id") Long id, HttpResponse res) throws Exception {
        for (User u : userService.getAllUsers()) {
            if (u.getId().equals(id)) return u;
        }
        res.setStatus(HttpStatus.NOT_FOUND);
        return Map.of("error", "User not found");
    }
}

// Answer - 3

import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class NanoDB {
    public static void main(String[] args) {
        DatabaseEngine engine = new DatabaseEngine();
        TcpServer server = new TcpServer(3306, engine);
        server.start();
    }
}

enum TokenType {
    CREATE, TABLE, INSERT, INTO, VALUES, SELECT, FROM, WHERE, AND, OR,
    IDENTIFIER, STRING_LITERAL, NUMBER_LITERAL,
    EQUALS, NOT_EQUALS, GREATER_THAN, LESS_THAN, GREATER_EQUALS, LESS_EQUALS,
    LPAREN, RPAREN, COMMA, SEMICOLON, ASTERISK, EOF,
    INT, VARCHAR, BOOLEAN
}

class Token {
    final TokenType type;
    final String value;

    Token(TokenType type, String value) {
        this.type = type;
        this.value = value;
    }

    @Override
    public String toString() {
        return type + (value.isEmpty() ? "" : ":" + value);
    }
}

class Lexer {
    private final String input;
    private int position;

    Lexer(String input) {
        this.input = input;
        this.position = 0;
    }

    List<Token> tokenize() {
        List<Token> tokens = new ArrayList<>();
        while (position < input.length()) {
            char current = input.charAt(position);
            if (Character.isWhitespace(current)) {
                position++;
                continue;
            }
            if (current == ';') { tokens.add(new Token(TokenType.SEMICOLON, ";")); position++; continue; }
            if (current == ',') { tokens.add(new Token(TokenType.COMMA, ",")); position++; continue; }
            if (current == '(') { tokens.add(new Token(TokenType.LPAREN, "(")); position++; continue; }
            if (current == ')') { tokens.add(new Token(TokenType.RPAREN, ")")); position++; continue; }
            if (current == '*') { tokens.add(new Token(TokenType.ASTERISK, "*")); position++; continue; }
            if (current == '=') { tokens.add(new Token(TokenType.EQUALS, "=")); position++; continue; }
            if (current == '<') {
                if (peek() == '=') { tokens.add(new Token(TokenType.LESS_EQUALS, "<=")); position += 2; }
                else if (peek() == '>') { tokens.add(new Token(TokenType.NOT_EQUALS, "<>")); position += 2; }
                else { tokens.add(new Token(TokenType.LESS_THAN, "<")); position++; }
                continue;
            }
            if (current == '>') {
                if (peek() == '=') { tokens.add(new Token(TokenType.GREATER_EQUALS, ">=")); position += 2; }
                else { tokens.add(new Token(TokenType.GREATER_THAN, ">")); position++; }
                continue;
            }
            if (current == '!' && peek() == '=') {
                tokens.add(new Token(TokenType.NOT_EQUALS, "!=")); position += 2; continue;
            }
            if (current == '\'') {
                tokens.add(readStringLiteral());
                continue;
            }
            if (Character.isDigit(current) || current == '-') {
                tokens.add(readNumberLiteral());
                continue;
            }
            if (Character.isLetter(current) || current == '_') {
                tokens.add(readIdentifierOrKeyword());
                continue;
            }
            throw new RuntimeException("Unknown character: " + current);
        }
        tokens.add(new Token(TokenType.EOF, ""));
        return tokens;
    }

    private char peek() {
        if (position + 1 >= input.length()) return '\0';
        return input.charAt(position + 1);
    }

    private Token readStringLiteral() {
        position++;
        StringBuilder sb = new StringBuilder();
        while (position < input.length() && input.charAt(position) != '\'') {
            sb.append(input.charAt(position));
            position++;
        }
        if (position >= input.length()) throw new RuntimeException("Unterminated string literal");
        position++;
        return new Token(TokenType.STRING_LITERAL, sb.toString());
    }

    private Token readNumberLiteral() {
        StringBuilder sb = new StringBuilder();
        if (input.charAt(position) == '-') {
            sb.append('-');
            position++;
        }
        while (position < input.length() && (Character.isDigit(input.charAt(position)) || input.charAt(position) == '.')) {
            sb.append(input.charAt(position));
            position++;
        }
        return new Token(TokenType.NUMBER_LITERAL, sb.toString());
    }

    private Token readIdentifierOrKeyword() {
        StringBuilder sb = new StringBuilder();
        while (position < input.length() && (Character.isLetterOrDigit(input.charAt(position)) || input.charAt(position) == '_')) {
            sb.append(input.charAt(position));
            position++;
        }
        String text = sb.toString().toUpperCase();
        switch (text) {
            case "CREATE": return new Token(TokenType.CREATE, text);
            case "TABLE": return new Token(TokenType.TABLE, text);
            case "INSERT": return new Token(TokenType.INSERT, text);
            case "INTO": return new Token(TokenType.INTO, text);
            case "VALUES": return new Token(TokenType.VALUES, text);
            case "SELECT": return new Token(TokenType.SELECT, text);
            case "FROM": return new Token(TokenType.FROM, text);
            case "WHERE": return new Token(TokenType.WHERE, text);
            case "AND": return new Token(TokenType.AND, text);
            case "OR": return new Token(TokenType.OR, text);
            case "INT": return new Token(TokenType.INT, text);
            case "VARCHAR": return new Token(TokenType.VARCHAR, text);
            case "BOOLEAN": return new Token(TokenType.BOOLEAN, text);
            default: return new Token(TokenType.IDENTIFIER, sb.toString());
        }
    }
}

abstract class AstNode {}
abstract class Statement extends AstNode {}

class CreateTableStmt extends Statement {
    final String tableName;
    final List<ColumnDef> columns;
    CreateTableStmt(String tableName, List<ColumnDef> columns) {
        this.tableName = tableName;
        this.columns = columns;
    }
}

class ColumnDef {
    final String name;
    final DataType type;
    ColumnDef(String name, DataType type) {
        this.name = name;
        this.type = type;
    }
}

enum DataType { INT, VARCHAR, BOOLEAN }

class InsertStmt extends Statement {
    final String tableName;
    final List<String> columns;
    final List<Expression> values;
    InsertStmt(String tableName, List<String> columns, List<Expression> values) {
        this.tableName = tableName;
        this.columns = columns;
        this.values = values;
    }
}

class SelectStmt extends Statement {
    final List<String> columns;
    final String tableName;
    final Expression whereClause;
    SelectStmt(List<String> columns, String tableName, Expression whereClause) {
        this.columns = columns;
        this.tableName = tableName;
        this.whereClause = whereClause;
    }
}

abstract class Expression extends AstNode {}

class BinaryExpression extends Expression {
    final Expression left;
    final TokenType operator;
    final Expression right;
    BinaryExpression(Expression left, TokenType operator, Expression right) {
        this.left = left;
        this.operator = operator;
        this.right = right;
    }
}

class LiteralExpression extends Expression {
    final Object value;
    final DataType type;
    LiteralExpression(Object value, DataType type) {
        this.value = value;
        this.type = type;
    }
}

class ColumnExpression extends Expression {
    final String columnName;
    ColumnExpression(String columnName) {
        this.columnName = columnName;
    }
}

class Parser {
    private final List<Token> tokens;
    private int position;

    Parser(List<Token> tokens) {
        this.tokens = tokens;
        this.position = 0;
    }

    Statement parse() {
        if (match(TokenType.CREATE)) return parseCreateTable();
        if (match(TokenType.INSERT)) return parseInsert();
        if (match(TokenType.SELECT)) return parseSelect();
        throw new RuntimeException("Unexpected token: " + peek().type);
    }

    private CreateTableStmt parseCreateTable() {
        consume(TokenType.TABLE, "Expected TABLE");
        String tableName = consume(TokenType.IDENTIFIER, "Expected table name").value;
        consume(TokenType.LPAREN, "Expected '('");
        List<ColumnDef> columns = new ArrayList<>();
        do {
            String colName = consume(TokenType.IDENTIFIER, "Expected column name").value;
            DataType type;
            if (match(TokenType.INT)) type = DataType.INT;
            else if (match(TokenType.VARCHAR)) type = DataType.VARCHAR;
            else if (match(TokenType.BOOLEAN)) type = DataType.BOOLEAN;
            else throw new RuntimeException("Expected data type");
            columns.add(new ColumnDef(colName, type));
        } while (match(TokenType.COMMA));
        consume(TokenType.RPAREN, "Expected ')'");
        match(TokenType.SEMICOLON);
        return new CreateTableStmt(tableName, columns);
    }

    private InsertStmt parseInsert() {
        consume(TokenType.INTO, "Expected INTO");
        String tableName = consume(TokenType.IDENTIFIER, "Expected table name").value;
        List<String> columns = new ArrayList<>();
        if (match(TokenType.LPAREN)) {
            do {
                columns.add(consume(TokenType.IDENTIFIER, "Expected column name").value);
            } while (match(TokenType.COMMA));
            consume(TokenType.RPAREN, "Expected ')'");
        }
        consume(TokenType.VALUES, "Expected VALUES");
        consume(TokenType.LPAREN, "Expected '('");
        List<Expression> values = new ArrayList<>();
        do {
            values.add(parseExpression());
        } while (match(TokenType.COMMA));
        consume(TokenType.RPAREN, "Expected ')'");
        match(TokenType.SEMICOLON);
        return new InsertStmt(tableName, columns, values);
    }

    private SelectStmt parseSelect() {
        List<String> columns = new ArrayList<>();
        if (match(TokenType.ASTERISK)) {
            columns.add("*");
        } else {
            do {
                columns.add(consume(TokenType.IDENTIFIER, "Expected column name").value);
            } while (match(TokenType.COMMA));
        }
        consume(TokenType.FROM, "Expected FROM");
        String tableName = consume(TokenType.IDENTIFIER, "Expected table name").value;
        Expression whereClause = null;
        if (match(TokenType.WHERE)) {
            whereClause = parseExpression();
        }
        match(TokenType.SEMICOLON);
        return new SelectStmt(columns, tableName, whereClause);
    }

    private Expression parseExpression() {
        return parseOr();
    }

    private Expression parseOr() {
        Expression left = parseAnd();
        while (match(TokenType.OR)) {
            TokenType op = previous().type;
            Expression right = parseAnd();
            left = new BinaryExpression(left, op, right);
        }
        return left;
    }

    private Expression parseAnd() {
        Expression left = parseEquality();
        while (match(TokenType.AND)) {
            TokenType op = previous().type;
            Expression right = parseEquality();
            left = new BinaryExpression(left, op, right);
        }
        return left;
    }

    private Expression parseEquality() {
        Expression left = parseComparison();
        while (match(TokenType.EQUALS, TokenType.NOT_EQUALS)) {
            TokenType op = previous().type;
            Expression right = parseComparison();
            left = new BinaryExpression(left, op, right);
        }
        return left;
    }

    private Expression parseComparison() {
        Expression left = parsePrimary();
        while (match(TokenType.LESS_THAN, TokenType.LESS_EQUALS, TokenType.GREATER_THAN, TokenType.GREATER_EQUALS)) {
            TokenType op = previous().type;
            Expression right = parsePrimary();
            left = new BinaryExpression(left, op, right);
        }
        return left;
    }

    private Expression parsePrimary() {
        if (match(TokenType.NUMBER_LITERAL)) {
            String val = previous().value;
            if (val.contains(".")) return new LiteralExpression(Double.parseDouble(val), DataType.VARCHAR);
            return new LiteralExpression(Integer.parseInt(val), DataType.INT);
        }
        if (match(TokenType.STRING_LITERAL)) {
            return new LiteralExpression(previous().value, DataType.VARCHAR);
        }
        if (match(TokenType.IDENTIFIER)) {
            if (previous().value.equalsIgnoreCase("TRUE")) return new LiteralExpression(true, DataType.BOOLEAN);
            if (previous().value.equalsIgnoreCase("FALSE")) return new LiteralExpression(false, DataType.BOOLEAN);
            return new ColumnExpression(previous().value);
        }
        throw new RuntimeException("Expected expression");
    }

    private boolean match(TokenType... types) {
        for (TokenType type : types) {
            if (check(type)) {
                position++;
                return true;
            }
        }
        return false;
    }

    private boolean check(TokenType type) {
        if (isAtEnd()) return false;
        return peek().type == type;
    }

    private Token consume(TokenType type, String message) {
        if (check(type)) {
            position++;
            return previous();
        }
        throw new RuntimeException(message);
    }

    private Token peek() {
        return tokens.get(position);
    }

    private Token previous() {
        return tokens.get(position - 1);
    }

    private boolean isAtEnd() {
        return peek().type == TokenType.EOF;
    }
}

class Table {
    final String name;
    final List<ColumnDef> schema;
    final List<Row> rows;

    Table(String name, List<ColumnDef> schema) {
        this.name = name;
        this.schema = schema;
        this.rows = new ArrayList<>();
    }

    void insert(Row row) {
        rows.add(row);
    }
}

class Row {
    final Map<String, Object> data;
    Row() {
        this.data = new HashMap<>();
    }
    Row(Map<String, Object> data) {
        this.data = new HashMap<>(data);
    }
}

class DatabaseEngine {
    private final Map<String, Table> tables = new ConcurrentHashMap<>();

    public String execute(String query) {
        try {
            Lexer lexer = new Lexer(query);
            List<Token> tokens = lexer.tokenize();
            if (tokens.get(0).type == TokenType.EOF) return "OK";
            Parser parser = new Parser(tokens);
            Statement stmt = parser.parse();

            if (stmt instanceof CreateTableStmt) return executeCreate((CreateTableStmt) stmt);
            if (stmt instanceof InsertStmt) return executeInsert((InsertStmt) stmt);
            if (stmt instanceof SelectStmt) return executeSelect((SelectStmt) stmt);
            
            return "UNSUPPORTED STATEMENT";
        } catch (Exception e) {
            return "ERROR: " + e.getMessage();
        }
    }

    private String executeCreate(CreateTableStmt stmt) {
        if (tables.containsKey(stmt.tableName)) {
            return "ERROR: Table '" + stmt.tableName + "' already exists.";
        }
        tables.put(stmt.tableName, new Table(stmt.tableName, stmt.columns));
        return "TABLE CREATED";
    }

    private String executeInsert(InsertStmt stmt) {
        Table table = tables.get(stmt.tableName);
        if (table == null) return "ERROR: Table '" + stmt.tableName + "' does not exist.";
        
        Row row = new Row();
        if (stmt.columns.isEmpty()) {
            if (stmt.values.size() != table.schema.size()) return "ERROR: Column count mismatch.";
            for (int i = 0; i < table.schema.size(); i++) {
                row.data.put(table.schema.get(i).name, evaluateLiteral(stmt.values.get(i)));
            }
        } else {
            if (stmt.columns.size() != stmt.values.size()) return "ERROR: Column/Value count mismatch.";
            for (int i = 0; i < stmt.columns.size(); i++) {
                String colName = stmt.columns.get(i);
                boolean validCol = table.schema.stream().anyMatch(c -> c.name.equalsIgnoreCase(colName));
                if (!validCol) return "ERROR: Column '" + colName + "' does not exist.";
                row.data.put(colName, evaluateLiteral(stmt.values.get(i)));
            }
        }
        table.insert(row);
        return "1 ROW INSERTED";
    }

    private Object evaluateLiteral(Expression expr) {
        if (expr instanceof LiteralExpression) {
            return ((LiteralExpression) expr).value;
        }
        throw new RuntimeException("Expected literal value in insert");
    }

    private String executeSelect(SelectStmt stmt) {
        Table table = tables.get(stmt.tableName);
        if (table == null) return "ERROR: Table '" + stmt.tableName + "' does not exist.";

        List<Row> results = new ArrayList<>();
        for (Row row : table.rows) {
            if (stmt.whereClause == null || evaluateWhere(stmt.whereClause, row)) {
                results.add(row);
            }
        }

        StringBuilder sb = new StringBuilder();
        List<String> targetColumns = stmt.columns;
        if (targetColumns.size() == 1 && targetColumns.get(0).equals("*")) {
            targetColumns = new ArrayList<>();
            for (ColumnDef c : table.schema) targetColumns.add(c.name);
        }

        for (int i = 0; i < targetColumns.size(); i++) {
            sb.append(targetColumns.get(i));
            if (i < targetColumns.size() - 1) sb.append("\t|\t");
        }
        sb.append("\n");
        for (int i = 0; i < sb.length() - 1; i++) sb.append("-");
        sb.append("\n");

        for (Row row : results) {
            for (int i = 0; i < targetColumns.size(); i++) {
                sb.append(row.data.get(targetColumns.get(i)));
                if (i < targetColumns.size() - 1) sb.append("\t|\t");
            }
            sb.append("\n");
        }
        sb.append(results.size()).append(" rows returned.");
        return sb.toString();
    }

    private boolean evaluateWhere(Expression expr, Row row) {
        if (expr instanceof LiteralExpression) {
            Object val = ((LiteralExpression) expr).value;
            if (val instanceof Boolean) return (Boolean) val;
            throw new RuntimeException("WHERE clause must evaluate to boolean");
        }
        if (expr instanceof BinaryExpression) {
            BinaryExpression bin = (BinaryExpression) expr;
            if (bin.operator == TokenType.AND) return evaluateWhere(bin.left, row) && evaluateWhere(bin.right, row);
            if (bin.operator == TokenType.OR) return evaluateWhere(bin.left, row) || evaluateWhere(bin.right, row);

            Object leftVal = evaluateValue(bin.left, row);
            Object rightVal = evaluateValue(bin.right, row);

            if (leftVal == null || rightVal == null) return false;

            if (leftVal instanceof Number && rightVal instanceof Number) {
                double l = ((Number) leftVal).doubleValue();
                double r = ((Number) rightVal).doubleValue();
                switch (bin.operator) {
                    case EQUALS: return l == r;
                    case NOT_EQUALS: return l != r;
                    case LESS_THAN: return l < r;
                    case LESS_EQUALS: return l <= r;
                    case GREATER_THAN: return l > r;
                    case GREATER_EQUALS: return l >= r;
                    default: throw new RuntimeException("Unknown operator in WHERE");
                }
            } else {
                String l = leftVal.toString();
                String r = rightVal.toString();
                switch (bin.operator) {
                    case EQUALS: return l.equals(r);
                    case NOT_EQUALS: return !l.equals(r);
                    default: throw new RuntimeException("Invalid operation on strings");
                }
            }
        }
        throw new RuntimeException("Unsupported WHERE clause expression");
    }

    private Object evaluateValue(Expression expr, Row row) {
        if (expr instanceof LiteralExpression) return ((LiteralExpression) expr).value;
        if (expr instanceof ColumnExpression) return row.data.get(((ColumnExpression) expr).columnName);
        throw new RuntimeException("Cannot evaluate expression value");
    }
}

class TcpServer {
    private final int port;
    private final DatabaseEngine engine;
    private final ExecutorService pool;
    private volatile boolean running;

    TcpServer(int port, DatabaseEngine engine) {
        this.port = port;
        this.engine = engine;
        this.pool = Executors.newFixedThreadPool(10);
    }

    void start() {
        running = true;
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            while (running) {
                Socket clientSocket = serverSocket.accept();
                pool.submit(new ClientHandler(clientSocket, engine));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}

class ClientHandler implements Runnable {
    private final Socket socket;
    private final DatabaseEngine engine;

    ClientHandler(Socket socket, DatabaseEngine engine) {
        this.socket = socket;
        this.engine = engine;
    }

    @Override
    public void run() {
        try (InputStream in = socket.getInputStream();
             OutputStream out = socket.getOutputStream()) {
            
            out.write("NanoDB 1.0 Ready.\n".getBytes(StandardCharsets.UTF_8));
            byte[] buffer = new byte[4096];
            int read;
            StringBuilder queryBuilder = new StringBuilder();

            while ((read = in.read(buffer)) != -1) {
                String text = new String(buffer, 0, read, StandardCharsets.UTF_8);
                queryBuilder.append(text);
                
                if (queryBuilder.toString().contains(";")) {
                    String[] queries = queryBuilder.toString().split(";");
                    for (int i = 0; i < queries.length; i++) {
                        String q = queries[i].trim();
                        if (q.isEmpty()) continue;
                        if (q.equalsIgnoreCase("EXIT") || q.equalsIgnoreCase("QUIT")) {
                            socket.close();
                            return;
                        }
                        String response = engine.execute(q + ";");
                        out.write((response + "\n> ").getBytes(StandardCharsets.UTF_8));
                    }
                    if (!queryBuilder.toString().endsWith(";")) {
                        queryBuilder = new StringBuilder(queries[queries.length - 1]);
                    } else {
                        queryBuilder = new StringBuilder();
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
