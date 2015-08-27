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

import com.jayway.restassured.RestAssured;
import com.jayway.restassured.response.Response;
import com.jayway.restassured.specification.RequestSpecification;
import java.io.IOException;
import javax.naming.OperationNotSupportedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A wrap-up of rest-assured.
 *
 * @author linsong wang
 */
public class RestCommunication extends EntityCommunication {
    private static final Logger LOG = LoggerFactory.getLogger(WebServiceCommunication.class);

    public static final String SYSPROP_HOST = "qa.th.comm.rest.HOST";

    public static final String SYSPROP_PORT = "qa.th.comm.rest.PORT";

    public static final String SYSPROP_CLIENT_CERT = "qa.th.comm.rest.CLIENT_CERT";

    public static final String SYSPROP_CLIENT_CERT_PASS = "qa.th.comm.rest.CLIENT_CERT_PASS";

    public static final String SYSPROP_USER = "qa.th.comm.rest.USER";

    public static final String SYSPROP_PASS = "qa.th.comm.rest.PASS";

    private RequestSpecification reqSpec;

    @Override
    public void connect() throws Exception {
        String host = SYSCONFIG.getProperty(RestCommunication.SYSPROP_HOST, "localhost");
        int port = SYSCONFIG.getIntProperty(RestCommunication.SYSPROP_PORT, 443);
        RestAssured.useRelaxedHTTPSValidation();
        this.reqSpec = RestAssured.given();

        if (port % 1000 == 443) {
            this.reqSpec = this.reqSpec.baseUri("https://" + host + ":" + port);
        } else {
            this.reqSpec = this.reqSpec.baseUri("http://" + host + ":" + port);
        }

        String user = SYSCONFIG.getProperty(RestCommunication.SYSPROP_USER);
        String pass = SYSCONFIG.getProperty(RestCommunication.SYSPROP_PASS);
        if (null != user && null != pass) {
            this.reqSpec = this.reqSpec.auth().preemptive().basic(user, pass);
        }

        String clientCert = SYSCONFIG.getProperty(RestCommunication.SYSPROP_CLIENT_CERT);
        String clientCertPass = SYSCONFIG.getProperty(RestCommunication.SYSPROP_CLIENT_CERT_PASS);
        if (null != clientCert && null != clientCertPass) {
            this.reqSpec = this.reqSpec.auth().certificate(clientCert, clientCertPass);
        }
    }

    /**
     * Throws OperationNotSupportedException.
     *
     * @throws Exception OperationNotSupportedException
     */
    @Override
    public void disconnect() throws Exception {
        throw new OperationNotSupportedException();
    }

    /**
     * Gets a new copy of defined RequestSpecification.
     *
     * @return a new copy of defined RequestSpecification
     */
    public RequestSpecification given() {
        return RestAssured.given(reqSpec);
    }

    public static String checkResponse(Response response) throws IOException {
        String res = "";
        if (response.body() != null) {
            res = response.body().asString();
        }
        int code = response.getStatusCode();
        if (code < 200 || code >= 300) {
            LOG.warn("{}", response.getStatusLine());
            throw new RestException(code, res);
        }
        return res;
    }
}