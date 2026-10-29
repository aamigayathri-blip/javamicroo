package campus;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Serves index.html exactly as stored on disk, adding one script tag (js/app.js) just before
 * </body> at response time. The HTML file itself is never modified.
 */
@WebServlet(urlPatterns = {"", "/index.html"})
public class PageServlet extends HttpServlet {
    private static final String BRIDGE_TAG = "<script src=\"js/app.js\"></script>\n";

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        try (InputStream in = getServletContext().getResourceAsStream("/index.html")) {
            if (in == null) {
                resp.sendError(HttpServletResponse.SC_NOT_FOUND);
                return;
            }
            String html = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            int end = html.lastIndexOf("</body>");
            html = (end >= 0) ? html.substring(0, end) + BRIDGE_TAG + html.substring(end) : html + BRIDGE_TAG;
            resp.setContentType("text/html");
            resp.setCharacterEncoding("UTF-8");
            resp.setHeader("Cache-Control", "no-store");
            resp.getWriter().write(html);
        }
    }
}
