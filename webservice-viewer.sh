#!/bin/bash

mvn clean install

java -cp target/*:target/dependency/* com.tascape.qa.th.ws.tools.WebServiceViewer
