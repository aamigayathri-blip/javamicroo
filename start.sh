#!/bin/sh
# Render supplies the port in $PORT; point Tomcat's HTTP connector at it.
sed -i "s/port=\"8080\"/port=\"${PORT:-8080}\"/" /usr/local/tomcat/conf/server.xml
exec catalina.sh run
