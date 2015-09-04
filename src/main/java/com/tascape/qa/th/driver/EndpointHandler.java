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
package com.tascape.qa.th.driver;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import org.apache.http.HttpException;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URLEncodedUtils;
import org.apache.http.nio.protocol.BasicAsyncRequestConsumer;
import org.apache.http.nio.protocol.BasicAsyncResponseProducer;
import org.apache.http.nio.protocol.HttpAsyncExchange;
import org.apache.http.nio.protocol.HttpAsyncRequestConsumer;
import org.apache.http.nio.protocol.HttpAsyncRequestHandler;
import org.apache.http.protocol.HttpContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author linsong wang
 */
public abstract class EndpointHandler extends EntityDriver implements HttpAsyncRequestHandler<HttpRequest> {
    private static final Logger LOG = LoggerFactory.getLogger(EndpointHandler.class);

    /**
     * Gets the endpoint the class handles.
     *
     * @return endpoint string
     */
    public abstract String getEndpoint();

    /**
     * Handles GET requests.
     *
     * @param request  HTTP request
     * @param response HTTP response
     *
     * @throws HttpException in case of HTTP related issue
     * @throws IOException   in case of IO related issue
     */
    public abstract void handleGet(HttpRequest request, HttpResponse response) throws HttpException, IOException;

    /**
     * Handles POST requests.
     *
     * @param request  HTTP request
     * @param response HTTP response
     *
     * @throws HttpException in case of HTTP related issue
     * @throws IOException   in case of IO related issue
     */
    public abstract void handlePost(HttpRequest request, HttpResponse response) throws HttpException, IOException;

    /**
     * Handles PUT requests.
     *
     * @param request  HTTP request
     * @param response HTTP response
     *
     * @throws HttpException in case of HTTP related issue
     * @throws IOException   in case of IO related issue
     */
    public abstract void handlePut(HttpRequest request, HttpResponse response) throws HttpException, IOException;

    /**
     * Handles DELETE requests.
     *
     * @param request  HTTP request
     * @param response HTTP response
     *
     * @throws HttpException in case of HTTP related issue
     * @throws IOException   in case of IO related issue
     */
    public abstract void handleDelete(HttpRequest request, HttpResponse response) throws HttpException, IOException;

    @Override
    public HttpAsyncRequestConsumer<HttpRequest> processRequest(HttpRequest hr, HttpContext hc)
        throws HttpException, IOException {
        return new BasicAsyncRequestConsumer();
    }

    @Override
    public void handle(HttpRequest request, HttpAsyncExchange hae, HttpContext hc) throws HttpException, IOException {
        LOG.debug("{}", request);
        String method = request.getRequestLine().getMethod().toUpperCase();
        HttpResponse response = hae.getResponse();
        response.setStatusCode(HttpStatus.SC_OK);
        switch (method) {
            case "GET":
                this.handleGet(request, response);
                break;
            case "POST":
                this.handlePost(request, response);
                break;
            case "PUT":
                this.handlePut(request, response);
                break;
            case "DELETE":
                this.handleDelete(request, response);
                break;
        }

        hae.submitResponse(new BasicAsyncResponseProducer(response));
    }

    /**
     * Gets parameter value of request line.
     *
     * @param request HTTP request
     * @param name    name of the request line parameter
     *
     * @return parameter value, only the first is returned if there are multiple values for the same name
     *
     * @throws URISyntaxException
     */
    public static String getParameter(HttpRequest request, String name) throws URISyntaxException {
        NameValuePair nv = URLEncodedUtils.parse(new URI(request.getRequestLine().getUri()), "UTF-8").stream()
            .filter(param -> param.getName().equals(name))
            .findFirst().get();
        if (nv == null) {
            return null;
        }
        return nv.getValue();
    }
}
