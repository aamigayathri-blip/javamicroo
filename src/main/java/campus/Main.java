package campus;

import java.io.File;
import org.apache.catalina.Context;
import org.apache.catalina.WebResourceRoot;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.webresources.DirResourceSet;
import org.apache.catalina.webresources.StandardRoot;

/** Local development launcher (embedded Tomcat): mvn compile exec:java */
public final class Main {
    private Main() {}

    public static void main(String[] args) throws Exception {
        int port = Integer.parseInt(System.getenv().getOrDefault("PORT", "8080"));

        Tomcat tomcat = new Tomcat();
        tomcat.setBaseDir("target/tomcat");
        tomcat.setPort(port);
        tomcat.getConnector();

        Context ctx = tomcat.addWebapp("", new File("src/main/webapp").getAbsolutePath());
        WebResourceRoot resources = new StandardRoot(ctx);
        resources.addPreResources(new DirResourceSet(
                resources, "/WEB-INF/classes", new File("target/classes").getAbsolutePath(), "/"));
        ctx.setResources(resources);

        tomcat.start();
        System.out.println("Campus Event Management running at http://localhost:" + port);
        tomcat.getServer().await();
    }
}
