/*
 * Copyright 2015 - 2016 Nebula Bay.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.tascape.qa.th.comm;

import com.tascape.qa.th.SystemConfiguration;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;
import javax.net.ssl.SSLContext;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.Header;
import org.apache.http.HeaderElement;
import org.apache.http.HeaderElementIterator;
import org.apache.http.HttpEntity;
import org.apache.http.HttpException;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.HttpRequestInterceptor;
import org.apache.http.HttpResponse;
import org.apache.http.auth.AuthScheme;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.AuthState;
import org.apache.http.auth.Credentials;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.AuthCache;
import org.apache.http.client.CookieStore;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.config.CookieSpecs;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpHead;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.config.Registry;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.conn.ConnectionKeepAliveStrategy;
import org.apache.http.conn.HttpClientConnectionManager;
import org.apache.http.conn.routing.HttpRoute;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.apache.http.conn.socket.PlainConnectionSocketFactory;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.auth.BasicScheme;
import org.apache.http.impl.client.BasicAuthCache;
import org.apache.http.impl.client.BasicCookieStore;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.client.LaxRedirectStrategy;
import org.apache.http.impl.conn.BasicHttpClientConnectionManager;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.message.BasicHeaderElementIterator;
import org.apache.http.protocol.HTTP;
import org.apache.http.protocol.HttpContext;
import org.apache.http.protocol.HttpCoreContext;
import org.apache.http.ssl.SSLContextBuilder;
import org.apache.http.ssl.SSLContexts;
import org.apache.http.ssl.TrustStrategy;
import org.apache.http.util.EntityUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Linsong Wang
 */
public class WebServiceCommunication extends EntityCommunication {
    private static final Logger LOG = LoggerFactory.getLogger(WebServiceCommunication.class);

    /**
     * Web service host name or IP address
     */
    public static final String SYSPROP_HOST = "qa.th.comm.ws.HOST";

    /**
     * Web service port number
     */
    public static final String SYSPROP_PORT = "qa.th.comm.ws.PORT";

    /**
     * Certificate used to authenticate web service client
     */
    public static final String SYSPROP_CLIENT_CERT = "qa.th.comm.ws.CLIENT_CERT";

    /**
     * Passcode of client certificate
     */
    public static final String SYSPROP_CLIENT_CERT_PASS = "qa.th.comm.ws.CLIENT_CERT_PASS";

    /**
     * Web service user name for basic authentication
     */
    public static final String SYSPROP_USER = "qa.th.comm.ws.USER";

    /**
     * Web service user password for basic authentication
     */
    public static final String SYSPROP_PASS = "qa.th.comm.ws.PASS";

    /**
     * Web service user agent string
     */
    public static final String USER_AGENT
        = "Mozilla/5.0 (TestHarness; Intel Mac OS X 10_7_3) AppleWebKit/535.19 (KHTML, like Gecko) "
        + "Chrome/18.0.1025.151 Safari/535.19";

    private static String cookieSpec = CookieSpecs.DEFAULT;

    private final HttpHost httpHost;

    private final String baseUri;

    private String clientCertificate;

    private String keyPassword;

    private String username;

    private String password;

    private CredentialsProvider userPassCredentialsProvider;

    private AuthCache authCache;

    private final CookieStore cookieStore = new BasicCookieStore();

    private CloseableHttpClient client;

    private final Map<String, String> headers = new HashMap<>();

    private final Map<String, Long> responseTime = new HashMap<>();

    public static void setCookieSpec(String aCookieSpec) {
        cookieSpec = aCookieSpec;
    }

    /**
     * Gets the response body as string of specified uri.
     *
     * @param uri http/https uri
     *
     * @return response body
     *
     * @throws IOException in case of any IO related issue
     */
    public static String getUri(String uri) throws IOException {
        CloseableHttpClient c = newHttpClient(new URL(uri));
        HttpGet get = new HttpGet(uri);
        LOG.debug("GET {}", uri);
        CloseableHttpResponse res = c.execute(get, HttpClientContext.create());
        return checkResponse(res);
    }

    /**
     * Gets the response headers of specified uri.
     *
     * @param uri http/https uri
     *
     * @return response headers
     *
     * @throws IOException in case of any IO related issue
     */
    public static Header[] headUri(String uri) throws IOException {
        CloseableHttpClient c = newHttpClient(new URL(uri));
        HttpHead head = new HttpHead(uri);
        LOG.debug("HEAD {}", uri);
        CloseableHttpResponse res = c.execute(head, HttpClientContext.create());
        checkResponse(res);
        return res.getAllHeaders();
    }

    /**
     * Gets the response header value of specified uri.
     *
     * @param uri  http/https uri
     * @param name header name
     *
     * @return response header value
     *
     * @throws IOException in case of any IO related issue
     */
    public static String headUri(String uri, String name) throws IOException {
        return Stream.of(headUri(uri)).filter(h -> h.getName().equals(name)).findFirst().get().getValue();
    }

    /**
     * Needs system properties.
     * <ul>
     * <li>qa.th.comm.ws.HOST, default to localhost if not set</li>
     * <li>qa.th.comm.ws.PORT, default to 443 if not set</li>
     * <li>qa.th.comm.ws.USER, no default</li>
     * <li>qa.th.comm.ws.PASS, no default</li>
     * <li>qa.th.comm.ws.CLIENT_CERT, no default</li>
     * <li>qa.th.comm.ws.CLIENT_CERT_PASS, no default</li>
     * </ul>
     *
     * @return an instance of communication
     *
     * @throws Exception if having problem connecting to the service
     */
    public static WebServiceCommunication newInstance() throws Exception {
        SystemConfiguration sysConfig = SystemConfiguration.getInstance();
        String host = sysConfig.getProperty(WebServiceCommunication.SYSPROP_HOST, "localhost");
        int port = sysConfig.getIntProperty(WebServiceCommunication.SYSPROP_PORT, 443);
        WebServiceCommunication wsc = new WebServiceCommunication(host, port);

        String user = sysConfig.getProperty(WebServiceCommunication.SYSPROP_USER);
        String pass = sysConfig.getProperty(WebServiceCommunication.SYSPROP_PASS);
        if (null != user && null != pass) {
            wsc.setBasicUsernamePassword(user, pass);
        }

        String clientCert = sysConfig.getProperty(WebServiceCommunication.SYSPROP_CLIENT_CERT);
        String clientCertPass = sysConfig.getProperty(WebServiceCommunication.SYSPROP_CLIENT_CERT_PASS);
        if (null != clientCert && null != clientCertPass) {
            wsc.setClientCertificate(clientCert, clientCertPass);
        }

        wsc.connect();
        return wsc;
    }

    /**
     * Needs system properties.
     * <ul>
     * <li>qa.th.comm.ws.HOST.$name, fall back to qa.th.comm.ws.HOST, and then default to localhost if not set</li>
     * <li>qa.th.comm.ws.PORT.$name, fall back to qa.th.comm.ws.PORT, then default to 443 if not set</li>
     * <li>qa.th.comm.ws.USER.$name, fall back to qa.th.comm.ws.USER, no default</li>
     * <li>qa.th.comm.ws.PASS.$name, fall back to qa.th.comm.ws.PASS, no default</li>
     * <li>qa.th.comm.ws.CLIENT_CERT.$name, fall back to qa.th.comm.ws.CLIENT_CERT, no default</li>
     * <li>qa.th.comm.ws.CLIENT_CERT_PASS.$name, fall back to qa.th.comm.ws.CLIENT_CERT_PASS, no default</li>
     * </ul>
     *
     * @param name name of each require system property
     *
     * @return an instance of communication
     *
     * @throws Exception if having problem connecting to the service
     */
    public static WebServiceCommunication newInstance(String name) throws Exception {
        SystemConfiguration sysConfig = SystemConfiguration.getInstance();
        String host = sysConfig.getProperty(WebServiceCommunication.SYSPROP_HOST + "." + name);
        if (host == null) {
            host = sysConfig.getProperty(WebServiceCommunication.SYSPROP_HOST, "localhost");
        }
        int port = sysConfig.getIntProperty(WebServiceCommunication.SYSPROP_PORT + "." + name);
        if (port == Integer.MIN_VALUE) {
            port = sysConfig.getIntProperty(WebServiceCommunication.SYSPROP_PORT, 443);
        }
        WebServiceCommunication wsc = new WebServiceCommunication(host, port);

        String user = sysConfig.getProperty(WebServiceCommunication.SYSPROP_USER + "." + name);
        if (user == null) {
            user = sysConfig.getProperty(WebServiceCommunication.SYSPROP_USER);
        }
        String pass = sysConfig.getProperty(WebServiceCommunication.SYSPROP_PASS + "." + name);
        if (pass == null) {
            pass = sysConfig.getProperty(WebServiceCommunication.SYSPROP_PASS);
        }
        if (null != user && null != pass) {
            wsc.setBasicUsernamePassword(user, pass);
        }

        String clientCert = sysConfig.getProperty(WebServiceCommunication.SYSPROP_CLIENT_CERT + "." + name);
        if (clientCert == null) {
            clientCert = sysConfig.getProperty(WebServiceCommunication.SYSPROP_CLIENT_CERT);
        }
        String clientCertPass = sysConfig.getProperty(WebServiceCommunication.SYSPROP_CLIENT_CERT_PASS + "." + name);
        if (clientCertPass == null) {
            clientCertPass = sysConfig.getProperty(WebServiceCommunication.SYSPROP_CLIENT_CERT_PASS);
        }
        if (null != clientCert && null != clientCertPass) {
            wsc.setClientCertificate(clientCert, clientCertPass);
        }

        wsc.connect();
        return wsc;
    }

    /**
     * Constructor.
     *
     * @param httpHost HTTP host info
     */
    public WebServiceCommunication(HttpHost httpHost) {
        this(httpHost.getHostName(), httpHost.getPort());
    }

    /**
     * Constructor.
     *
     * @param host host DNS name or IP
     * @param port https for *443, http for others
     */
    public WebServiceCommunication(String host, int port) {
        if (port % 1000 == 443) {
            this.baseUri = "https://" + host + ":" + port;
            this.httpHost = new HttpHost(host, port, "https");
        } else {
            this.baseUri = "http://" + host + ":" + port;
            this.httpHost = new HttpHost(host, port, "http");
        }
    }

    /**
     * Calls this to provide client certificate.
     *
     * @param clientCertificate client certificate file
     * @param keyPassword       client certificate password
     */
    public void setClientCertificate(String clientCertificate, String keyPassword) {
        LOG.debug("use client certificate/password {}/********", clientCertificate);
        this.clientCertificate = clientCertificate;
        this.keyPassword = keyPassword;
    }

    /**
     * Calls this to provide username and password. This will use Basic authentication, with a header such as
     * "Authorization: Basic QWxhZGRpbjpvcGVuIHNlc2FtZQ=="
     *
     * @param username user name
     * @param password password
     */
    public void setBasicUsernamePassword(String username, String password) {
        this.username = username;
        this.password = password;
        LOG.debug("use basic username/password {}/********", username);

        userPassCredentialsProvider = new BasicCredentialsProvider();
        userPassCredentialsProvider.setCredentials(AuthScope.ANY,
            new UsernamePasswordCredentials(username, password));

        authCache = new BasicAuthCache();
        BasicScheme basicAuth = new BasicScheme();
        authCache.put(httpHost, basicAuth);
    }

    /**
     * Adds HTTP header for all subsequent HTTP requests.
     *
     * @param name  HTTP header name
     * @param value HTTP header value
     */
    public void setHeader(String name, String value) {
        this.headers.put(name, value);
    }

    /**
     * Removes HTTP header for all subsequent HTTP requests.
     *
     * @param name HTTP header name
     *
     * @return the previous header value associated with <tt>name</tt>, or <tt>null</tt> if there was no mapping for
     * <tt>name</tt>.
     */
    public String removeHeader(String name) {
        return this.headers.remove(name);
    }

    /**
     *
     * @throws Exception in case of any issue
     */
    @Override
    public void connect() throws Exception {
        this.disconnect();

        SSLContextBuilder contextBuilder = SSLContexts.custom();
        contextBuilder.loadTrustMaterial(null, acceptingTrustStrategy);

        RegistryBuilder<ConnectionSocketFactory> registryBuilder = RegistryBuilder.<ConnectionSocketFactory>create()
            .register("http", new PlainConnectionSocketFactory());

        HttpClientBuilder httpClientBuilder = HttpClients.custom()
            .setUserAgent(USER_AGENT)
            .setKeepAliveStrategy(keepAliveStrategy)
            .setDefaultRequestConfig(RequestConfig.custom().setCookieSpec(cookieSpec).build())
            .setDefaultCookieStore(this.cookieStore)
            .setRedirectStrategy(new LaxRedirectStrategy());

        if (userPassCredentialsProvider != null) {
            httpClientBuilder.addInterceptorFirst(preemptiveAuth);
        }

        if (clientCertificate != null && keyPassword != null) {
            LOG.debug("client cert {}", clientCertificate);
            try (FileInputStream instream = new FileInputStream(new File(clientCertificate))) {
                KeyStore ks = KeyStore.getInstance("pkcs12");
                ks.load(instream, keyPassword.toCharArray());
                contextBuilder.loadKeyMaterial(ks, keyPassword.toCharArray());
            }
        }

        if ("https".equals(httpHost.getSchemeName())) {
            SSLContext sslContext = contextBuilder.build();
            SSLConnectionSocketFactory sslsf = new SSLConnectionSocketFactory(sslContext, new NoopHostnameVerifier());
            registryBuilder.register("https", sslsf);
            httpClientBuilder.setSSLSocketFactory(sslsf);
        }

        Registry<ConnectionSocketFactory> socketFactoryRegistry = registryBuilder.build();
        PoolingHttpClientConnectionManager cm = new PoolingHttpClientConnectionManager(socketFactoryRegistry);
        cm.setMaxTotal(200);
        cm.setDefaultMaxPerRoute(20);
        cm.setMaxPerRoute(new HttpRoute(httpHost), 200);

        this.client = httpClientBuilder.setConnectionManager(cm).build();
    }

    /**
     * @throws Exception in case of any issue
     */
    @Override
    public void disconnect() throws Exception {
        if (this.client != null) {
            this.client.close();
        }
    }

    /**
     * Issues HTTP HEAD request, returns response headers.
     *
     * @param endpoint endpoint of request url
     *
     * @return response headers
     *
     * @throws IOException in case of any IO related issue
     */
    public Header[] head(String endpoint) throws IOException {
        return this.head(endpoint, "");
    }

    /**
     * Issues HTTP HEAD request, returns response headers.
     *
     * @param endpoint endpoint of request url
     * @param params   request line parameters
     *
     * @return response headers
     *
     * @throws IOException in case of any IO related issue
     */
    public Header[] head(String endpoint, String params) throws IOException {
        String url = String.format("%s%s?%s", this.baseUri, endpoint, StringUtils.isBlank(params) ? "" : params);
        LOG.debug("HEAD {}", url);
        HttpHead head = new HttpHead(url);

        this.addHeaders(head);
        HttpClientContext context = this.getHttpClientContext();
        CloseableHttpResponse response = this.client.execute(head, context);
        check(response);
        return response.getAllHeaders();
    }

    /**
     * Issues HTTP GET request, converts response body to a JSON object.
     *
     * @param endpoint endpoint of request url
     *
     * @return response body to a JSON object
     *
     * @throws IOException in case of any IO related issue
     */
    public JSONObject getJsonObject(String endpoint) throws IOException {
        String res = this.get(endpoint, null, null);
        try {
            return new JSONObject(res);
        } catch (JSONException ex) {
            LOG.warn(res);
            throw ex;
        }
    }

    /**
     * Issues HTTP GET request, converts response body to a JSON object.
     *
     * @param endpoint endpoint of request url
     * @param params   request line parameters
     *
     * @return response body to a JSON object
     *
     * @throws IOException in case of any IO related issue
     */
    public JSONObject getJsonObject(String endpoint, String params) throws IOException {
        String res = this.get(endpoint, params, null);
        try {
            return new JSONObject(res);
        } catch (JSONException ex) {
            LOG.warn(res);
            throw ex;
        }
    }

    /**
     * Issues HTTP GET request, converts response body to a JSON object.
     *
     * @param endpoint  endpoint of request url
     * @param params    request line parameters
     * @param requestId request id for record response time in millisecond
     *
     * @return response body to a JSON object
     *
     * @throws IOException in case of any IO related issue
     */
    public JSONObject getJsonObject(String endpoint, String params, String requestId) throws IOException {
        String res = this.get(endpoint, params, requestId);
        try {
            return new JSONObject(res);
        } catch (JSONException ex) {
            LOG.warn(res);
            throw ex;
        }
    }

    /**
     * Issues HTTP GET request, converts response body to a JSON array object.
     *
     * @param endpoint endpoint of request url
     *
     * @return response body to a JSON array object
     *
     * @throws IOException in case of any IO related issue
     */
    public JSONArray getJsonArray(String endpoint) throws IOException {
        String res = this.get(endpoint, null, null);
        try {
            return new JSONArray(res);
        } catch (JSONException ex) {
            LOG.warn(res);
            throw ex;
        }
    }

    /**
     * Issues HTTP GET request, converts response body to a JSON array object.
     *
     * @param endpoint endpoint of request url
     * @param params   request line parameters
     *
     * @return response body to a JSON array object
     *
     * @throws IOException in case of any IO related issue
     */
    public JSONArray getJsonArray(String endpoint, String params) throws IOException {
        String res = this.get(endpoint, params, null);
        try {
            return new JSONArray(res);
        } catch (JSONException ex) {
            LOG.warn(res);
            throw ex;
        }
    }

    /**
     * Issues HTTP GET request, converts response body to a JSON array object.
     *
     * @param endpoint  endpoint of request url
     * @param params    request line parameters
     * @param requestId request id for record response time in millisecond
     *
     * @return response body to a JSON array object
     *
     * @throws IOException in case of any IO related issue
     */
    public JSONArray getJsonArray(String endpoint, String params, String requestId) throws IOException {
        String res = this.get(endpoint, params, requestId);
        try {
            return new JSONArray(res);
        } catch (JSONException ex) {
            LOG.warn(res);
            throw ex;
        }
    }

    /**
     * Issues HTTP GET request, returns response body as string.
     *
     * @param endpoint endpoint of request url
     *
     * @return response body
     *
     * @throws IOException in case of any IO related issue
     */
    public String get(String endpoint) throws IOException {
        return this.get(endpoint, null);
    }

    /**
     * Issues HTTP GET request, returns response body as string.
     *
     * @param endpoint endpoint of request url
     * @param params   request line parameters
     *
     * @return response body
     *
     * @throws IOException in case of any IO related issue
     */
    public String get(String endpoint, String params) throws IOException {
        return this.get(endpoint, params, null);
    }

    /**
     * Issues HTTP GET request, returns response body as string.
     *
     * @param endpoint  endpoint of request url
     * @param params    request line parameters
     * @param requestId request id for record response time in millisecond
     *
     * @return response body
     *
     * @throws IOException in case of any IO related issue
     */
    public String get(String endpoint, String params, String requestId) throws IOException {
        String url = String.format("%s%s?%s", this.baseUri, endpoint, StringUtils.isBlank(params) ? "" : params);
        LOG.debug("{} GET {}", this.hashCode(), url);
        HttpGet get = new HttpGet(url);

        this.addHeaders(get);
        HttpClientContext context = this.getHttpClientContext();
        long start = System.currentTimeMillis();
        CloseableHttpResponse response = this.client.execute(get, context);
        if (!StringUtils.isBlank(requestId)) {
            this.responseTime.put(requestId, System.currentTimeMillis() - start);
        }
        return check(response);
    }

    /**
     * Issues HTTP DELETE request, returns response body as string.
     *
     * @param endpoint endpoint of request url
     *
     * @return response body
     *
     * @throws IOException in case of any IO related issue
     */
    public String delete(String endpoint) throws IOException {
        return this.delete(endpoint, "");
    }

    /**
     * Issues HTTP DELETE request, returns response body as string.
     *
     * @param endpoint endpoint of request url
     * @param params   request line parameters
     *
     * @return response body
     *
     * @throws IOException in case of any IO related issue
     */
    public String delete(String endpoint, String params) throws IOException {
        return this.delete(endpoint, params, "");
    }

    /**
     * Issues HTTP DELETE request, returns response body as string.
     *
     * @param endpoint  endpoint of request url
     * @param params    request line parameters
     * @param requestId request id for record response time in millisecond
     *
     * @return response body
     *
     * @throws IOException in case of any IO related issue
     */
    public String delete(String endpoint, String params, String requestId) throws IOException {
        String url = String.format("%s%s?%s", this.baseUri, endpoint, StringUtils.isBlank(params) ? "" : params);
        LOG.debug("{} DELETE {}", this.hashCode(), url);
        HttpDelete delete = new HttpDelete(url);

        this.addHeaders(delete);
        HttpClientContext context = this.getHttpClientContext();
        long start = System.currentTimeMillis();
        CloseableHttpResponse response = this.client.execute(delete, context);
        if (!StringUtils.isBlank(requestId)) {
            this.responseTime.put(requestId, System.currentTimeMillis() - start);
        }
        return check(response);
    }

    /**
     * Issues HTTP POST request, returns response body as string.
     *
     * @param endpoint endpoint of request url
     * @param json     request body
     *
     * @return response body
     *
     * @throws IOException in case of any IO related issue
     */
    public String postJson(String endpoint, JSONObject json) throws IOException {
        return this.postJson(endpoint, "", json);
    }

    /**
     * Issues HTTP POST request, returns response body as string.
     *
     * @param endpoint endpoint of request url
     * @param params   request line parameters
     * @param json     request body
     *
     * @return response body
     *
     * @throws IOException in case of any IO related issue
     */
    public String postJson(String endpoint, String params, JSONObject json) throws IOException {
        return this.postJson(endpoint, params, json, "");
    }

    /**
     * Issues HTTP POST request, returns response body as string.
     *
     * @param endpoint  endpoint of request url
     * @param params    request line parameters
     * @param json      request body
     * @param requestId request id for record response time in millisecond
     *
     * @return response body
     *
     * @throws IOException in case of any IO related issue
     */
    public String postJson(String endpoint, String params, JSONObject json, String requestId) throws IOException {
        String url = String.format("%s%s?%s", this.baseUri, endpoint, StringUtils.isBlank(params) ? "" : params);
        LOG.debug("{} POST {}", this.hashCode(), url);
        HttpPost post = new HttpPost(url);

        StringEntity entity = new StringEntity(json.toString());
        entity.setContentType("application/json");
        post.setEntity(entity);

        this.addHeaders(post);
        HttpClientContext context = this.getHttpClientContext();
        long start = System.currentTimeMillis();
        CloseableHttpResponse response = this.client.execute(post, context);
        if (!StringUtils.isBlank(requestId)) {
            this.responseTime.put(requestId, System.currentTimeMillis() - start);
        }

        String res = check(response);
        return res;
    }

    /**
     * Issues HTTP POST request, returns response body as string.
     *
     * @param endpoint endpoint of request url
     *
     * @return response body
     *
     * @throws IOException in case of any IO related issue
     */
    public String post(String endpoint) throws IOException {
        return this.post(endpoint, "");
    }

    /**
     * Issues HTTP POST request, returns response body as string.
     *
     * @param endpoint endpoint of request url
     * @param params   request line parameters
     *
     * @return response body
     *
     * @throws IOException in case of any IO related issue
     */
    public String post(String endpoint, String params) throws IOException {
        return this.post(endpoint, params, "");
    }

    /**
     * Issues HTTP POST request, returns response body as string.
     *
     * @param endpoint endpoint of request url
     * @param params   request line parameters
     * @param body     request body
     *
     * @return response body
     *
     * @throws IOException in case of any IO related issue
     */
    public String post(String endpoint, String params, String body) throws IOException {
        return this.post(endpoint, params, body, "");
    }

    /**
     * Issues HTTP POST request, returns response body as string.
     *
     * @param endpoint  endpoint of request url
     * @param params    request line parameters
     * @param body      request body
     * @param requestId request id for record response time in millisecond
     *
     * @return response body
     *
     * @throws IOException in case of any IO related issue
     */
    public String post(String endpoint, String params, String body, String requestId) throws IOException {
        String url = String.format("%s%s?%s", this.baseUri, endpoint, StringUtils.isBlank(params) ? "" : params);
        LOG.debug("{} POST {}", this.hashCode(), url);
        HttpPost post = new HttpPost(url);

        StringEntity entity = new StringEntity(body);
        entity.setContentType("text/plain");
        post.setEntity(entity);

        this.addHeaders(post);
        HttpClientContext context = this.getHttpClientContext();
        long start = System.currentTimeMillis();
        CloseableHttpResponse response = this.client.execute(post, context);
        if (!StringUtils.isBlank(requestId)) {
            this.responseTime.put(requestId, System.currentTimeMillis() - start);
        }
        String res = check(response);
        return res;
    }

    /**
     * Issues HTTP POST request, returns response body as string.
     *
     * @param endpoint endpoint of request url
     * @param entity   request entity
     *
     * @return response body
     *
     * @throws IOException in case of any IO related issue
     */
    public String postEntity(String endpoint, HttpEntity entity) throws IOException {
        return this.postEntity(endpoint, "", entity, "");
    }

    /**
     * Issues HTTP POST request, returns response body as string.
     * http://www.baeldung.com/httpclient-multipart-upload
     * <pre>
     * {@code
     * HttpPost post = new HttpPost("http://echo.200please.com");
     * InputStream inputStream = new FileInputStream(zipFileName);
     * File file = new File(imageFileName);
     * String message = "This is a multipart post";
     * MultipartEntityBuilder builder = MultipartEntityBuilder.create();
     * builder.setMode(HttpMultipartMode.BROWSER_COMPATIBLE);
     * builder.addBinaryBody("upfile", file, ContentType.DEFAULT_BINARY, imageFileName);
     * builder.addBinaryBody("upstream", inputStream, ContentType.create("application/zip"), zipFileName);
     * builder.addTextBody("text", message, ContentType.TEXT_PLAIN);
     *
     * HttpEntity entity = builder.build();
     * post.setEntity(entity);
     * HttpResponse response = client.execute(post);
     * }
     * </pre>
     *
     * @param endpoint  endpoint of request url
     * @param params    request line parameters
     * @param entity    request entity
     * @param requestId request id for record response time in millisecond
     *
     * @return response body
     *
     * @throws IOException in case of any IO related issue
     */
    public String postEntity(String endpoint, String params, HttpEntity entity, String requestId) throws IOException {
        String url = String.format("%s%s?%s", this.baseUri, endpoint, StringUtils.isBlank(params) ? "" : params);
        LOG.debug("{} POST {}", this.hashCode(), url);
        HttpPost post = new HttpPost(url);
        post.setEntity(entity);
        this.addHeaders(post);
        HttpClientContext context = this.getHttpClientContext();
        long start = System.currentTimeMillis();
        CloseableHttpResponse response = this.client.execute(post, context);
        if (!StringUtils.isBlank(requestId)) {
            this.responseTime.put(requestId, System.currentTimeMillis() - start);
        }
        String res = check(response);
        return res;
    }

    /**
     * Issues HTTP PUT request, returns response body as string.
     *
     * @param endpoint endpoint of request url
     * @param json     request body
     *
     * @return response body
     *
     * @throws IOException in case of any IO related issue
     */
    public String putJson(String endpoint, JSONObject json) throws IOException {
        return this.putJson(endpoint, "", json);
    }

    /**
     * Issues HTTP PUT request, returns response body as string.
     *
     * @param endpoint endpoint of request url
     * @param params   request line parameters
     * @param json     request body
     *
     * @return response body
     *
     * @throws IOException in case of any IO related issue
     */
    public String putJson(String endpoint, String params, JSONObject json) throws IOException {
        return this.putJson(endpoint, params, json, "");
    }

    /**
     * Issues HTTP PUT request, returns response body as string.
     *
     * @param endpoint  endpoint of request url
     * @param params    request line parameters
     * @param json      request body
     * @param requestId request id for record response time in millisecond
     *
     * @return response body
     *
     * @throws IOException in case of any IO related issue
     */
    public String putJson(String endpoint, String params, JSONObject json, String requestId) throws IOException {
        String url = String.format("%s%s?%s", this.baseUri, endpoint, StringUtils.isBlank(params) ? "" : params);
        LOG.debug("{} PUT {}", this.hashCode(), url);
        HttpPut put = new HttpPut(url);

        StringEntity entity = new StringEntity(json.toString());
        entity.setContentType("application/json");
        put.setEntity(entity);

        this.addHeaders(put);
        HttpClientContext context = this.getHttpClientContext();
        long start = System.currentTimeMillis();
        CloseableHttpResponse response = this.client.execute(put, context);
        if (!StringUtils.isBlank(requestId)) {
            this.responseTime.put(requestId, System.currentTimeMillis() - start);
        }
        return check(response);
    }

    /**
     * Issues HTTP PUT request, returns response body as string.
     *
     * @param endpoint endpoint of request url
     * @param params   request line parameters
     *
     * @return response body
     *
     * @throws IOException in case of any IO related issue
     */
    public String put(String endpoint, String params) throws IOException {
        return this.put(endpoint, params, "");
    }

    /**
     * Issues HTTP PUT request, returns response body as string.
     *
     * @param endpoint endpoint of request url
     * @param params   request line parameters
     * @param body     request body
     *
     * @return response body
     *
     * @throws IOException in case of any IO related issue
     */
    public String put(String endpoint, String params, String body) throws IOException {
        return this.put(endpoint, params, body, "");
    }

    /**
     * Issues HTTP PUT request, returns response body as string.
     *
     * @param endpoint  endpoint of request url
     * @param params    request line parameters
     * @param body      request body
     * @param requestId request id for record response time in millisecond
     *
     * @return response body
     *
     * @throws IOException in case of any IO related issue
     */
    public String put(String endpoint, String params, String body, String requestId) throws IOException {
        String url = String.format("%s%s?%s", this.baseUri, endpoint, StringUtils.isBlank(params) ? "" : params);
        LOG.debug("{} PUT {}", this.hashCode(), url);
        HttpPut put = new HttpPut(url);

        StringEntity entity = new StringEntity(body);
        entity.setContentType("text/plain");
        put.setEntity(entity);

        this.addHeaders(put);
        HttpClientContext context = this.getHttpClientContext();
        long start = System.currentTimeMillis();
        CloseableHttpResponse response = this.client.execute(put, context);
        if (!StringUtils.isBlank(requestId)) {
            this.responseTime.put(requestId, System.currentTimeMillis() - start);
        }
        return check(response);
    }

    /**
     * Gets response time previously recorded.
     *
     * @param reqId request id
     *
     * @return time in millisecond
     */
    public Long getResponseTime(String reqId) {
        return responseTime.get(reqId);
    }

    /**
     * Clears response time previously recorded.
     *
     * @param reqId request id
     */
    public void clearResponseTime(String reqId) {
        this.responseTime.remove(reqId);
    }

    /**
     * Encodes with UTF-8.
     *
     * @param param string to encode
     *
     * @return UTF-8 encoded string
     *
     * @throws UnsupportedEncodingException if UTF-8 is not supported
     */
    public static String encode(String param) throws UnsupportedEncodingException {
        return URLEncoder.encode(param, "UTF-8");
    }

    /**
     * Encodes with UTF-8.
     *
     * @param param string to decode
     *
     * @return UTF-8 decoded string
     *
     * @throws UnsupportedEncodingException if UTF-8 is not supported
     */
    public static String decode(String param) throws UnsupportedEncodingException {
        return URLDecoder.decode(param, "UTF-8");
    }

    /**
     * Gets the host name and port.
     *
     * @return HTTPHost
     */
    public HttpHost getHttpHost() {
        return httpHost;
    }

    public String getClientCertificate() {
        return clientCertificate;
    }

    public String getKeyPassword() {
        return keyPassword;
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }

    public CloseableHttpClient getClient() {
        return client;
    }

    private void addHeaders(HttpRequest request) {
        this.headers.entrySet().forEach(header -> {
            request.setHeader(header.getKey(), header.getValue());
        });
    }

    private HttpClientContext getHttpClientContext() {
        HttpClientContext context = HttpClientContext.create();
        if (this.userPassCredentialsProvider != null) {
            context.setCredentialsProvider(userPassCredentialsProvider);
            context.setAuthCache(authCache);
            BasicScheme basicAuth = new BasicScheme();
            context.setAttribute("preemptive-auth", basicAuth);
        }
        this.cookieStore.getCookies().forEach(c -> {
            LOG.trace("outgoing {} {} {}", c.getName() + "=" + c.getValue(), c.getDomain(), c.getPath());
        });
        return context;
    }

    private String check(CloseableHttpResponse response) throws IOException {
        this.cookieStore.getCookies().forEach(c -> {
            LOG.trace("incoming {} {} {}", c.getName() + "=" + c.getValue(), c.getDomain(), c.getPath());
        });

        String res = "";
        if (response.getEntity() != null) {
            res = EntityUtils.toString(response.getEntity());
        }
        int code = response.getStatusLine().getStatusCode();
        if (code < 200 || code >= 300) {
            LOG.warn("{}", response.getStatusLine());
            throw new WebServiceException(code, res);
        }
        return res;
    }

    private final TrustStrategy acceptingTrustStrategy = (X509Certificate[] certificate, String authType) -> true;

    private final ConnectionKeepAliveStrategy keepAliveStrategy = (HttpResponse response, HttpContext context) -> {
        HeaderElementIterator it = new BasicHeaderElementIterator(response.headerIterator(HTTP.CONN_KEEP_ALIVE));
        while (it.hasNext()) {
            HeaderElement he = it.nextElement();
            String param = he.getName();
            String value = he.getValue();
            if (value != null && param.equalsIgnoreCase("timeout")) {
                try {
                    return Long.parseLong(value) * 1000;
                } catch (NumberFormatException ignore) {
                    LOG.trace(ignore.getMessage());
                }
            }
        }
        return 30000;
    };

    private final HttpRequestInterceptor preemptiveAuth = (final HttpRequest request, final HttpContext context) -> {
        AuthState authState = (AuthState) context.getAttribute(HttpClientContext.TARGET_AUTH_STATE);
        if (authState.getAuthScheme() == null) {
            AuthScheme authScheme = (AuthScheme) context.getAttribute("preemptive-auth");
            CredentialsProvider credsProvider = (CredentialsProvider) context.getAttribute(
                HttpClientContext.CREDS_PROVIDER);
            HttpHost targetHost = (HttpHost) context.getAttribute(HttpCoreContext.HTTP_TARGET_HOST);
            if (authScheme != null) {
                Credentials creds = credsProvider.getCredentials(
                    new AuthScope(targetHost.getHostName(), targetHost.getPort()));
                if (creds == null) {
                    throw new HttpException("No credentials for preemptive authentication");
                }
                authState.update(authScheme, creds);
            }
        }
    };

    private static String checkResponse(CloseableHttpResponse response) throws IOException {
        String res = "";
        if (response.getEntity() != null) {
            res = EntityUtils.toString(response.getEntity());
        }
        int code = response.getStatusLine().getStatusCode();
        if (code < 200 || code >= 300) {
            LOG.warn("{}", response.getStatusLine());
            throw new WebServiceException(code, res);
        }
        return res;
    }

    private static CloseableHttpClient newHttpClient(URL url) throws IOException {
        HttpClientBuilder httpClientBuilder = HttpClients.custom()
            .setUserAgent(USER_AGENT)
            .setDefaultRequestConfig(RequestConfig.custom().setCookieSpec(CookieSpecs.DEFAULT).build())
            .setRedirectStrategy(new LaxRedirectStrategy());

        RegistryBuilder<ConnectionSocketFactory> registryBuilder = RegistryBuilder.<ConnectionSocketFactory>create()
            .register("http", new PlainConnectionSocketFactory());
        if ("https".equals(url.getProtocol())) {
            SSLContextBuilder contextBuilder = SSLContexts.custom();
            TrustStrategy ats = (X509Certificate[] certificate, String authType) -> true;
            try {
                contextBuilder.loadTrustMaterial(null, ats);
                SSLContext sslContext = contextBuilder.build();
                SSLConnectionSocketFactory sslsf
                    = new SSLConnectionSocketFactory(sslContext, new NoopHostnameVerifier());
                registryBuilder.register("https", sslsf);
                httpClientBuilder.setSSLSocketFactory(sslsf);
            } catch (NoSuchAlgorithmException | KeyStoreException | KeyManagementException ex) {
                throw new IOException(ex);
            }
        }

        Registry<ConnectionSocketFactory> socketFactoryRegistry = registryBuilder.build();
        HttpClientConnectionManager cm = new BasicHttpClientConnectionManager(socketFactoryRegistry);
        return httpClientBuilder.setConnectionManager(cm).build();
    }

    public static void main(String[] args) throws Exception {
        WebServiceCommunication ws = new WebServiceCommunication("ec2-54-226-209-194.compute-1.amazonaws.com", 9000);
        ws.connect();
        Header[] headers = ws.head("thr/dashboard.xhtml");
        Stream.of(headers).forEach(h -> {
            LOG.debug("{} = {}", h.getName(), h.getValue());
        });

        headers = WebServiceCommunication
            .headUri("https://ec2-54-226-209-194.compute-1.amazonaws.com:9443/thr/dashboard.xhtml");
        Stream.of(headers).forEach(h -> {
            LOG.debug("{} = {}", h.getName(), h.getValue());
        });

        ws = new WebServiceCommunication("bit.ly", 80);
        ws.connect();
        String status = ws.get("1c1mBAI");
        LOG.info(status);
    }
}
