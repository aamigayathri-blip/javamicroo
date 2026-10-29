#!/bin/sh
# Disable Tomcat's shutdown port so health checks don't trigger warnings
sed -i 's/port="8005"/port="-1"/' /usr/local/tomcat/conf/server.xml

# Render supplies the port in $PORT; point Tomcat's HTTP connector at it.
sed -i "s/port=\"8080\"/port=\"${PORT:-8080}\"/" /usr/local/tomcat/conf/server.xml
exec catalina.sh run
