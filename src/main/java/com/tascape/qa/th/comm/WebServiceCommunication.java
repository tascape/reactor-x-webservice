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
import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.client.config.CookieSpecs;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
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
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.client.LaxRedirectStrategy;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.message.BasicHeaderElementIterator;
import org.apache.http.protocol.HTTP;
import org.apache.http.protocol.HttpContext;
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

    public static String USER_AGENT
        = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_7_3) AppleWebKit/535.19 (KHTML, like Gecko) "
        + "Chrome/18.0.1025.151 Safari/535.19";

    private final String host;

    private final int port;

    private final String baseUri;

    private String clientCertificate;

    private String keyPassword;

    private CloseableHttpClient client;

    private final Map<String, Long> responseTime = new HashMap<>();

    public static String getUri(String uri) throws IOException {
        CloseableHttpClient c = HttpClients.createDefault();
        HttpGet get = new HttpGet(uri);
        LOG.debug("get {}", uri);
        CloseableHttpResponse res = c.execute(get, getHttpClientContext());
        return checkResponse(res);
    }

    /**
     *
     * @param host host DNS name or IP
     * @param port https for *443, http for others
     */
    public WebServiceCommunication(String host, int port) {
        this.host = host;
        this.port = port;
        if (port % 1000 == 443) {
            this.baseUri = "https://" + host + ":" + port;
        } else {
            this.baseUri = "http://" + host + ":" + port;
        }
    }

    /**
     * Call this to provide client certificate.
     *
     * @param clientCertificate client certificate file
     * @param keyPassword       client certificate password
     */
    public void setClientCertificate(String clientCertificate, String keyPassword) {
        this.clientCertificate = clientCertificate;
        this.keyPassword = keyPassword;
    }

    @Override
    public void connect() throws Exception {
        SSLContextBuilder contextBuilder = SSLContexts.custom();
        contextBuilder.loadTrustMaterial(null, acceptingTrustStrategy);

        RegistryBuilder registryBuilder = RegistryBuilder.<ConnectionSocketFactory>create()
            .register("http", new PlainConnectionSocketFactory());

        HttpClientBuilder httpClientBuilder = HttpClients.custom()
            .setUserAgent(USER_AGENT)
            .setKeepAliveStrategy(this.keepAliveStrategy)
            .setDefaultRequestConfig(RequestConfig.custom().setCookieSpec(CookieSpecs.DEFAULT).build())
            .setRedirectStrategy(new LaxRedirectStrategy());

        if (clientCertificate != null && keyPassword != null) {
            LOG.debug("client cert {}", clientCertificate);
            try (FileInputStream instream = new FileInputStream(new File(clientCertificate))) {
                KeyStore ks = KeyStore.getInstance("pkcs12");
                ks.load(instream, keyPassword.toCharArray());
                contextBuilder.loadKeyMaterial(ks, keyPassword.toCharArray());
            }
        }

        if (port % 1000 == 443) {
            SSLContext sslContext = contextBuilder.build();
            SSLConnectionSocketFactory sslsf = new SSLConnectionSocketFactory(sslContext, new NoopHostnameVerifier());
            registryBuilder.register("https", sslsf);
            httpClientBuilder.setSSLSocketFactory(sslsf);
        }

        Registry<ConnectionSocketFactory> socketFactoryRegistry = registryBuilder.build();
        PoolingHttpClientConnectionManager cm = new PoolingHttpClientConnectionManager(socketFactoryRegistry);
        cm.setMaxTotal(200);
        cm.setDefaultMaxPerRoute(20);
        HttpHost h = new HttpHost(this.host, this.port);
        cm.setMaxPerRoute(new HttpRoute(h), 200);

        this.client = httpClientBuilder.setConnectionManager(cm).build();
    }

    @Override
    public void disconnect() throws Exception {
        this.client.close();
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
        return this.get(endpoint, null, null);
    }

    public String get(String endpoint, String params) throws IOException {
        return this.get(endpoint, params, null);
    }

    public String get(String endpoint, String params, String requestId) throws IOException {
        String url = String.format("%s/%s?%s", this.baseUri, endpoint, StringUtils.isBlank(params) ? "" : params);
        LOG.debug("GET {}", url);
        HttpClientContext context = WebServiceCommunication.getHttpClientContext();
        HttpGet get = new HttpGet(url);

        long start = System.currentTimeMillis();
        CloseableHttpResponse response = this.client.execute(get, context);
        if (!StringUtils.isBlank(requestId)) {
            this.responseTime.put(requestId, System.currentTimeMillis() - start);
        }
        return WebServiceCommunication.checkResponse(response);
    }

    public String postJson(String endpoint, JSONObject json) throws IOException {
        return this.postJson(endpoint, "", json, null);
    }

    public String postJson(String endpoint, String params, JSONObject json) throws IOException {
        return this.postJson(endpoint, params, json, null);
    }

    public String postJson(String endpoint, String params, JSONObject json, String requestId) throws IOException {
        String url = String.format("%s/%s?%s", this.baseUri, endpoint, StringUtils.isBlank(params) ? "" : params);
        LOG.debug("POST {}", url);
        String content = json.toString(2);
        LOG.debug("JSON {}", content);
        HttpClientContext context = WebServiceCommunication.getHttpClientContext();
        HttpPost post = new HttpPost(url);

        StringEntity entity = new StringEntity(json.toString());
        entity.setContentType("application/json");
        post.setEntity(entity);

        long start = System.currentTimeMillis();
        CloseableHttpResponse response = this.client.execute(post, context);
        if (!StringUtils.isBlank(requestId)) {
            this.responseTime.put(requestId, System.currentTimeMillis() - start);
        }
        return WebServiceCommunication.checkResponse(response);
    }

    public String post(String endpoint, String body) throws IOException {
        return this.post(endpoint, "", body, "");
    }

    public String post(String endpoint, String params, String body, String requestId) throws IOException {
        String url = String.format("%s/%s?%s", this.baseUri, endpoint, StringUtils.isBlank(params) ? "" : params);
        LOG.debug("POST {}", url);
        LOG.debug("body {}", body);
        HttpClientContext context = WebServiceCommunication.getHttpClientContext();
        HttpPost post = new HttpPost(url);

        StringEntity entity = new StringEntity(body);
        entity.setContentType("text/plain");
        post.setEntity(entity);

        long start = System.currentTimeMillis();
        CloseableHttpResponse response = this.client.execute(post, context);
        if (!StringUtils.isBlank(requestId)) {
            this.responseTime.put(requestId, System.currentTimeMillis() - start);
        }
        return WebServiceCommunication.checkResponse(response);
    }

    public String putJson(String endpoint, String params, JSONObject json, String requestId) throws IOException {
        String url = String.format("%s/%s?%s", this.baseUri, endpoint, StringUtils.isBlank(params) ? "" : params);
        LOG.debug("PUT {}", url);
        String content = json.toString(2);
        LOG.debug("JSON {}", content);
        HttpClientContext context = WebServiceCommunication.getHttpClientContext();
        HttpPut put = new HttpPut(url);

        StringEntity entity = new StringEntity(json.toString());
        entity.setContentType("application/json");
        put.setEntity(entity);

        long start = System.currentTimeMillis();
        CloseableHttpResponse response = this.client.execute(put, context);
        if (requestId != null && !requestId.isEmpty()) {
            this.responseTime.put(requestId, System.currentTimeMillis() - start);
        }
        return WebServiceCommunication.checkResponse(response);
    }

    public String put(String endpoint, String body) throws IOException {
        return this.put(endpoint, "", body, "");
    }

    public String put(String endpoint, String params, String body, String requestId) throws IOException {
        String url = String.format("%s/%s?%s", this.baseUri, endpoint, StringUtils.isBlank(params) ? "" : params);
        LOG.debug("PUT {}", url);
        LOG.debug("body {}", body);
        HttpClientContext context = WebServiceCommunication.getHttpClientContext();
        HttpPut put = new HttpPut(url);

        StringEntity entity = new StringEntity(body);
        entity.setContentType("text/plain");
        put.setEntity(entity);

        long start = System.currentTimeMillis();
        CloseableHttpResponse response = this.client.execute(put, context);
        if (requestId != null && !requestId.isEmpty()) {
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

    private static HttpClientContext getHttpClientContext() {
        return HttpClientContext.create();
    }

    private static String checkResponse(CloseableHttpResponse response) throws IOException {
        String res = EntityUtils.toString(response.getEntity());
        int code = response.getStatusLine().getStatusCode();
        if (code < 200 || code >= 300) {
            LOG.warn("{}", response.getStatusLine());
            throw new IOException(res);
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

    public static void main(String[] args) throws Exception {
        WebServiceCommunication ws = new WebServiceCommunication("bit.ly", 80);
        ws.connect();
        String status = ws.get("1c1mBAI");
        LOG.info(status);
    }
}
