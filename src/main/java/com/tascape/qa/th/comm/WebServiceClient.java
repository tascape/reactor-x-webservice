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
import java.net.URLEncoder;
import java.security.KeyStore;
import java.security.cert.X509Certificate;
import java.util.HashMap;
import java.util.Map;
import javax.net.ssl.SSLContext;
import org.apache.http.HeaderElement;
import org.apache.http.HeaderElementIterator;
import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.client.CookieStore;
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
public class WebServiceClient extends EntityCommunication {
    private static final Logger LOG = LoggerFactory.getLogger(WebServiceClient.class);

    public static String USER_AGENT
        = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_7_3) AppleWebKit/535.19 (KHTML, like Gecko) "
        + "Chrome/18.0.1025.151 Safari/535.19";

    public static final String SYSPROP_CLIENT_CERT = "qa.th.comm.webserviceclient.CLIENT_CERT";

    private final String host;

    private final int port;

    private final String baseUri;

    private CloseableHttpClient client;

    private CookieStore cookieStore;

    private final Map<String, Long> responseTime = new HashMap<>();

    /**
     *
     * @param host host DNS name or IP
     * @param port https for *443, http for others
     */
    public WebServiceClient(String host, int port) {
        this.host = host;
        this.port = port;
        if (port % 1000 == 443) {
            this.baseUri = "https://" + host + ":" + port;
        } else {
            this.baseUri = "http://" + host + ":" + port;
        }
    }

    @Override
    public void connect() throws Exception {
        SSLContextBuilder contextBuilder = SSLContexts.custom();
        contextBuilder.loadTrustMaterial(null, acceptingTrustStrategy);

        String cc = SYSCONFIG.getProperty(SYSPROP_CLIENT_CERT);
        LOG.debug("client cert {}", cc);

        RegistryBuilder registryBuilder = RegistryBuilder.<ConnectionSocketFactory>create()
            .register("http", new PlainConnectionSocketFactory());

        HttpClientBuilder httpClientBuilder = HttpClients.custom()
            .setUserAgent(USER_AGENT)
            .setKeepAliveStrategy(this.keepAliveStrategy)
            .setDefaultRequestConfig(RequestConfig.custom().setCookieSpec(CookieSpecs.DEFAULT).build())
            .setRedirectStrategy(new LaxRedirectStrategy());

        if (cc != null) {
            try (FileInputStream instream = new FileInputStream(new File(cc))) {
                KeyStore ks = KeyStore.getInstance("pkcs12");
                ks.load(instream, "123".toCharArray());
                contextBuilder.loadKeyMaterial(ks, "123".toCharArray());
            }
        }
        SSLContext sslContext = contextBuilder.build();
        SSLConnectionSocketFactory sslsf = new SSLConnectionSocketFactory(sslContext, new NoopHostnameVerifier());
        registryBuilder.register("https", sslsf);
        httpClientBuilder.setSSLSocketFactory(sslsf);
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

    public String get(String endpoint) throws IOException {
        return this.get(endpoint, null, null);
    }

    public String get(String endpoint, String params) throws IOException {
        return this.get(endpoint, params, null);
    }

    public JSONObject getJsonObject(String endpoint) throws IOException {
        return new JSONObject(this.get(endpoint, null, null));
    }

    public JSONObject getJsonObject(String endpoint, String params) throws IOException {
        return new JSONObject(this.get(endpoint, params, null));
    }

    public JSONArray getJsonArray(String endpoint) throws IOException {
        return new JSONArray(this.get(endpoint, null, null));
    }

    public JSONArray getJsonArray(String endpoint, String params) throws IOException {
        return new JSONArray(this.get(endpoint, params, null));
    }

    public String get(String endpoint, String params, String requestId) throws IOException {
        String url = String.format("%s/%s?%s", this.baseUri, endpoint, params == null ? "" : params);
        LOG.debug("GET {}", url);
        HttpContext context = HttpClientContext.create();
        HttpGet get = new HttpGet(url);
        long start = System.currentTimeMillis();
        CloseableHttpResponse response = this.client.execute(get, context);
        if (requestId != null && !requestId.isEmpty()) {
            this.responseTime.put(requestId, System.currentTimeMillis() - start);
        }
        return this.checkResponse(response);
    }

    public String post(String endpoint, String params, String requestId) throws IOException {
        String url = String.format("%s/%s?%s", this.baseUri, endpoint, params == null ? "" : params);
        LOG.debug("POST {}", url);
        HttpContext context = HttpClientContext.create();
        HttpPost post = new HttpPost(url);
        long start = System.currentTimeMillis();
        CloseableHttpResponse response = this.client.execute(post, context);
        if (requestId != null && !requestId.isEmpty()) {
            this.responseTime.put(requestId, System.currentTimeMillis() - start);
        }
        return this.checkResponse(response);
    }

    public String put(String endpoint, String params) throws IOException {
        String url = String.format("%s/%s?%s", this.baseUri, endpoint, params == null ? "" : params);
        LOG.debug("PUT {}", url);
        HttpContext context = HttpClientContext.create();
        HttpPut put = new HttpPut(url);
        CloseableHttpResponse response = this.client.execute(put, context);
        return this.checkResponse(response);
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

    private String checkResponse(CloseableHttpResponse response) throws IOException {
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
        WebServiceClient ws = new WebServiceClient("bit.ly", 80);
        ws.connect();
        String status = ws.get("1c1mBAI");
        LOG.info(status);
    }
}
