package campus;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.util.Map;

/** JSON API under /api/*. Authentication is a server-side session; roles are enforced here. */
@WebServlet(urlPatterns = "/api/*", loadOnStartup = 1)
public class EventServlet extends HttpServlet {
    private static final Gson GSON = new Gson();
    private static final String SESSION_USER = "user";

    private final EventService service = new EventService();

    @Override
    public void init() throws ServletException {
        try {
            DatabaseConnection.initialize();
            service.seedDefaultData();
        } catch (Exception e) {
            throw new ServletException("Database initialisation failed (" + DatabaseConnection.path() + ")", e);
        }
    }

    @Override
    protected void service(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setCharacterEncoding("UTF-8");
        resp.setContentType("application/json");
        resp.setHeader("Cache-Control", "no-store");
        try {
            route(req, resp);
        } catch (ServiceException e) {
            send(resp, e.getStatus(), Map.of("error", e.getMessage()));
        } catch (JsonParseException | IllegalStateException e) {
            send(resp, 400, Map.of("error", "Invalid request."));
        } catch (Exception e) {
            e.printStackTrace();
            send(resp, 500, Map.of("error", "Unexpected server error."));
        }
    }

    private void route(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String method = req.getMethod();
        String[] seg = segments(req);
        boolean read = "GET".equals(method);

        // CSRF guard: browsers cannot add a custom header cross-site without a CORS preflight.
        if (!read && !"campus".equals(req.getHeader("X-Requested-With"))) {
            throw new ServiceException(403, "Request rejected.");
        }
        if (seg.length == 0) throw new ServiceException(404, "Not found.");

        if (seg.length == 1 && "login".equals(seg[0]) && "POST".equals(method)) {
            login(req, resp);
            return;
        }
        if (seg.length == 1 && "logout".equals(seg[0]) && "POST".equals(method)) {
            HttpSession s = req.getSession(false);
            if (s != null) s.invalidate();
            send(resp, 200, Map.of("ok", true));
            return;
        }

        User user = currentUser(req);

        if ("students".equals(seg[0]) && seg.length == 1) {
            requireAdmin(user);
            if (read) {
                send(resp, 200, service.listStudents());
            } else if ("POST".equals(method)) {
                JsonObject b = body(req);
                service.createStudent(str(b, "name"), str(b, "studentClass"), intVal(b, "passingYear"),
                        str(b, "rollNo"), str(b, "username"), raw(b, "password"));
                send(resp, 201, Map.of("ok", true));
            } else {
                throw new ServiceException(405, "Method not allowed.");
            }
            return;
        }

        if ("events".equals(seg[0])) {
            if (seg.length == 1 && read) {
                send(resp, 200, service.listEvents(user));
                return;
            }
            if (seg.length == 1 && "POST".equals(method)) {
                requireAdmin(user);
                long id = service.createEvent(eventFrom(body(req)));
                send(resp, 201, Map.of("id", id));
                return;
            }
            if (seg.length == 2) {
                long id = parseId(seg[1]);
                if ("PUT".equals(method)) {
                    requireAdmin(user);
                    service.updateEvent(id, eventFrom(body(req)));
                    send(resp, 200, Map.of("ok", true));
                    return;
                }
                if ("DELETE".equals(method)) {
                    requireAdmin(user);
                    service.deleteEvent(id);
                    send(resp, 200, Map.of("ok", true));
                    return;
                }
            }
            if (seg.length == 3 && "register".equals(seg[2])) {
                long id = parseId(seg[1]);
                requireStudent(user);
                if ("POST".equals(method)) {
                    send(resp, 201, service.register(id, user));
                    return;
                }
                if ("DELETE".equals(method)) {
                    service.cancelRegistration(id, user);
                    send(resp, 200, Map.of("ok", true));
                    return;
                }
            }
        }
        throw new ServiceException(404, "Not found.");
    }

    private void login(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        JsonObject b = body(req);
        User user = service.authenticate(str(b, "role"), str(b, "username"), raw(b, "password"));
        if (user == null) throw new ServiceException(401, "Login failed. Check your login ID and password, then try again.");
        HttpSession old = req.getSession(false);
        if (old != null) old.invalidate(); // new session id after login (prevents session fixation)
        HttpSession session = req.getSession(true);
        session.setAttribute(SESSION_USER, user);
        session.setMaxInactiveInterval(60 * 60);
        send(resp, 200, user);
    }

    private User currentUser(HttpServletRequest req) {
        HttpSession s = req.getSession(false);
        Object u = (s == null) ? null : s.getAttribute(SESSION_USER);
        if (!(u instanceof User)) throw new ServiceException(401, "Your session has expired. Please sign in again.");
        return (User) u;
    }

    private void requireAdmin(User u) {
        if (!u.isAdmin()) throw new ServiceException(403, "You do not have permission to perform this action.");
    }

    private void requireStudent(User u) {
        if (!u.isStudent()) throw new ServiceException(403, "You do not have permission to perform this action.");
    }

    private static String[] segments(HttpServletRequest req) {
        String p = req.getPathInfo();
        if (p == null) return new String[0];
        return java.util.Arrays.stream(p.split("/")).filter(s -> !s.isEmpty()).toArray(String[]::new);
    }

    private static long parseId(String s) {
        try {
            return Long.parseLong(s);
        } catch (NumberFormatException e) {
            throw new ServiceException(404, "Not found.");
        }
    }

    private static JsonObject body(HttpServletRequest req) throws IOException {
        JsonElement el = JsonParser.parseReader(req.getReader());
        if (el == null || !el.isJsonObject()) throw new ServiceException(400, "Invalid request.");
        return el.getAsJsonObject();
    }

    private static Event eventFrom(JsonObject b) {
        return new Event(null, str(b, "name"), str(b, "desc"), str(b, "category"),
                intVal(b, "capacity"), str(b, "date"), str(b, "time"), str(b, "venue"));
    }

    private static String raw(JsonObject o, String key) {
        try {
            JsonElement e = o.get(key);
            return (e == null || e.isJsonNull()) ? "" : e.getAsString();
        } catch (RuntimeException ex) {
            return "";
        }
    }

    private static String str(JsonObject o, String key) {
        return raw(o, key).trim();
    }

    private static int intVal(JsonObject o, String key) {
        try {
            JsonElement e = o.get(key);
            return (e == null || e.isJsonNull()) ? 0 : e.getAsInt();
        } catch (RuntimeException ex) {
            return 0;
        }
    }

    private static void send(HttpServletResponse resp, int status, Object payload) throws IOException {
        resp.setStatus(status);
        resp.getWriter().write(GSON.toJson(payload));
    }
}
