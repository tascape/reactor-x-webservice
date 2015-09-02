/*
 * Copyright 2015.
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
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.security.KeyStore;
import java.security.cert.X509Certificate;
import java.util.HashMap;
import java.util.Map;
import javax.net.ssl.SSLContext;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HeaderElement;
import org.apache.http.HeaderElementIterator;
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
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.config.CookieSpecs;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.config.Registry;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.conn.ConnectionKeepAliveStrategy;
import org.apache.http.conn.routing.HttpRoute;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.apache.http.conn.socket.PlainConnectionSocketFactory;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.auth.BasicScheme;
import org.apache.http.impl.client.BasicAuthCache;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.client.LaxRedirectStrategy;
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
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Linsong Wang
 */
public class WebServiceCommunication extends EntityCommunication {
    private static final Logger LOG = LoggerFactory.getLogger(WebServiceCommunication.class);

    public static final String SYSPROP_HOST = "qa.th.comm.ws.HOST";

    public static final String SYSPROP_PORT = "qa.th.comm.ws.PORT";

    public static final String SYSPROP_CLIENT_CERT = "qa.th.comm.ws.CLIENT_CERT";

    public static final String SYSPROP_CLIENT_CERT_PASS = "qa.th.comm.ws.CLIENT_CERT_PASS";

    public static final String SYSPROP_USER = "qa.th.comm.ws.USER";

    public static final String SYSPROP_PASS = "qa.th.comm.ws.PASS";

    public static String USER_AGENT
        = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_7_3) AppleWebKit/535.19 (KHTML, like Gecko) "
        + "Chrome/18.0.1025.151 Safari/535.19";

    private final int port;

    private final HttpHost httpHost;

    private final String baseUri;

    private String clientCertificate;

    private String keyPassword;

    private CredentialsProvider userPassCredentialsProvider;

    private AuthCache authCache;

    private CloseableHttpClient client;

    private final Map<String, String> headers = new HashMap<>();

    private final Map<String, Long> responseTime = new HashMap<>();

    public static String getUri(String uri) throws IOException {
        CloseableHttpClient c = HttpClients.createDefault();
        HttpGet get = new HttpGet(uri);
        LOG.debug("get {}", uri);
        CloseableHttpResponse res = c.execute(get, HttpClientContext.create());
        return checkResponse(res);
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
            wsc.setUsernamePassword(user, pass);
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
            wsc.setUsernamePassword(user, pass);
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

    public WebServiceCommunication(HttpHost httpHost) {
        this(httpHost.getHostName(), httpHost.getPort());
    }

    /**
     *
     * @param host host DNS name or IP
     * @param port https for *443, http for others
     */
    public WebServiceCommunication(String host, int port) {
        this.port = port;
        if (port % 1000 == 443) {
            this.baseUri = "https://" + host + ":" + port;
            this.httpHost = new HttpHost(host, port, "https");
        } else {
            this.baseUri = "http://" + host + ":" + port;
            this.httpHost = new HttpHost(host, port, "http");
        }
    }

    /**
     * Call this to provide client certificate.
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
     * Call this to provide username and password. This will use Basic authentication, with a header such as
     * "Authorization: Basic QWxhZGRpbjpvcGVuIHNlc2FtZQ=="
     *
     * @param username user name
     * @param password password
     */
    public void setUsernamePassword(String username, String password) {
        LOG.debug("use username/password {}/********", username);
        userPassCredentialsProvider = new BasicCredentialsProvider();
        userPassCredentialsProvider.setCredentials(AuthScope.ANY,
            new UsernamePasswordCredentials(username, password));

        authCache = new BasicAuthCache();
        BasicScheme basicAuth = new BasicScheme();
        authCache.put(httpHost, basicAuth);
    }

    public void setHeader(String name, String value) {
        this.headers.put(name, value);
    }

    @Override
    public void connect() throws Exception {
        this.disconnect();

        SSLContextBuilder contextBuilder = SSLContexts.custom();
        contextBuilder.loadTrustMaterial(null, acceptingTrustStrategy);

        RegistryBuilder registryBuilder = RegistryBuilder.<ConnectionSocketFactory>create()
            .register("http", new PlainConnectionSocketFactory());

        HttpClientBuilder httpClientBuilder = HttpClients.custom()
            .setUserAgent(USER_AGENT)
            .setKeepAliveStrategy(keepAliveStrategy)
            .setDefaultRequestConfig(RequestConfig.custom().setCookieSpec(CookieSpecs.DEFAULT).build())
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

    @Override
    public void disconnect() throws Exception {
        if (this.client != null) {
            this.client.close();
        }
    }

    public JSONObject getJsonObject(String endpoint) throws IOException {
        return new JSONObject(this.get(endpoint, null, null));
    }

    public JSONObject getJsonObject(String endpoint, String params) throws IOException {
        return new JSONObject(this.get(endpoint, params, null));
    }

    public JSONObject getJsonObject(String endpoint, String params, String requestId) throws IOException {
        return new JSONObject(this.get(endpoint, params, requestId));
    }

    public JSONArray getJsonArray(String endpoint) throws IOException {
        return new JSONArray(this.get(endpoint, null, null));
    }

    public JSONArray getJsonArray(String endpoint, String params) throws IOException {
        return new JSONArray(this.get(endpoint, params, null));
    }

    public JSONArray getJsonArray(String endpoint, String params, String reqestId) throws IOException {
        return new JSONArray(this.get(endpoint, params, reqestId));
    }

    public String get(String endpoint) throws IOException {
        return this.get(endpoint, null);
    }

    public String get(String endpoint, String params) throws IOException {
        return this.get(endpoint, params, null);
    }

    public String get(String endpoint, String params, String requestId) throws IOException {
        String url = String.format("%s/%s?%s", this.baseUri, endpoint, StringUtils.isBlank(params) ? "" : params);
        LOG.debug("GET {}", url);
        HttpGet get = new HttpGet(url);

        this.addHeaders(get);
        HttpClientContext context = this.getHttpClientContext();
        long start = System.currentTimeMillis();
        CloseableHttpResponse response = this.client.execute(get, context);
        if (!StringUtils.isBlank(requestId)) {
            this.responseTime.put(requestId, System.currentTimeMillis() - start);
        }
        return WebServiceCommunication.checkResponse(response);
    }

    public String delete(String endpoint) throws IOException {
        return this.delete(endpoint, "");
    }

    public String delete(String endpoint, String params) throws IOException {
        return this.delete(endpoint, params, "");
    }

    public String delete(String endpoint, String params, String requestId) throws IOException {
        String url = String.format("%s/%s?%s", this.baseUri, endpoint, StringUtils.isBlank(params) ? "" : params);
        LOG.debug("DELETE {}", url);
        HttpDelete delete = new HttpDelete(url);

        this.addHeaders(delete);
        HttpClientContext context = this.getHttpClientContext();
        long start = System.currentTimeMillis();
        CloseableHttpResponse response = this.client.execute(delete, context);
        if (!StringUtils.isBlank(requestId)) {
            this.responseTime.put(requestId, System.currentTimeMillis() - start);
        }
        return WebServiceCommunication.checkResponse(response);
    }

    public String postJson(String endpoint, JSONObject json) throws IOException {
        return this.postJson(endpoint, "", json);
    }

    public String postJson(String endpoint, String params, JSONObject json) throws IOException {
        return this.postJson(endpoint, params, json, "");
    }

    public String postJson(String endpoint, String params, JSONObject json, String requestId) throws IOException {
        String url = String.format("%s/%s?%s", this.baseUri, endpoint, StringUtils.isBlank(params) ? "" : params);
        LOG.debug("POST {}", url);
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
        return WebServiceCommunication.checkResponse(response);
    }

    public String post(String endpoint) throws IOException {
        return this.post(endpoint, "");
    }

    public String post(String endpoint, String params) throws IOException {
        return this.post(endpoint, params, "");
    }

    public String post(String endpoint, String params, String body) throws IOException {
        return this.post(endpoint, params, body, "");
    }

    public String post(String endpoint, String params, String body, String requestId) throws IOException {
        String url = String.format("%s/%s?%s", this.baseUri, endpoint, StringUtils.isBlank(params) ? "" : params);
        LOG.debug("POST {}", url);
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
        return WebServiceCommunication.checkResponse(response);
    }

    public String putJson(String endpoint, JSONObject json) throws IOException {
        return this.putJson(endpoint, "", json);
    }

    public String putJson(String endpoint, String params, JSONObject json) throws IOException {
        return this.putJson(endpoint, params, json, "");
    }

    public String putJson(String endpoint, String params, JSONObject json, String requestId) throws IOException {
        String url = String.format("%s/%s?%s", this.baseUri, endpoint, StringUtils.isBlank(params) ? "" : params);
        LOG.debug("PUT {}", url);
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
        return WebServiceCommunication.checkResponse(response);
    }

    public String put(String endpoint, String param) throws IOException {
        return this.put(endpoint, param, "");
    }

    public String put(String endpoint, String param, String body) throws IOException {
        return this.put(endpoint, param, body, "");
    }

    public String put(String endpoint, String params, String body, String requestId) throws IOException {
        String url = String.format("%s/%s?%s", this.baseUri, endpoint, StringUtils.isBlank(params) ? "" : params);
        LOG.debug("PUT {}", url);
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
        return WebServiceCommunication.checkResponse(response);
    }

    public Long getResponseTime(String reqId) {
        return responseTime.get(reqId);
    }

    public void clearResponseTime(String reqId) {
        this.responseTime.remove(reqId);
    }

    public static String encode(String param) throws UnsupportedEncodingException {
        return URLEncoder.encode(param, "UTF-8");
    }

    public static String decode(String param) throws UnsupportedEncodingException {
        return URLDecoder.decode(param, "UTF-8");
    }

    public HttpHost getHttpHost() {
        return httpHost;
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
        return context;
    }

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

    public static void main(String[] args) throws Exception {
        WebServiceCommunication ws = new WebServiceCommunication("bit.ly", 80);
        ws.connect();
        String status = ws.get("1c1mBAI");
        LOG.info(status);
    }
}
