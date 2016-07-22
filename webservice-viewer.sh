#!/bin/bash

mvn clean install

java -cp target/*:target/dependency/* com.tascape.reactor.ws.tools.WebServiceViewer
