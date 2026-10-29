package campus;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/** Business logic and JDBC access for accounts, events and registrations. */
public class EventService {
    private static final Set<String> CATEGORIES = Set.of("Technical", "Cultural", "Sports", "Workshop");
    private static final Pattern DATE_RE = Pattern.compile("\\d{4}-\\d{2}-\\d{2}");
    private static final Pattern TIME_RE = Pattern.compile("([01]\\d|2[0-3]):[0-5]\\d");
    private static final String SLOT_TAKEN = "That time slot is already booked at this venue. Please choose another slot.";

    private static volatile String dummyHash;

    @FunctionalInterface
    private interface TxWork<T> {
        T run(Connection c) throws SQLException;
    }

    // ------------------------------------------------------------------ accounts

    public User authenticate(String role, String username, String password) {
        if (!"student".equals(role) && !"admin".equals(role)) return null;
        if (username == null || username.isEmpty() || password == null) return null;
        String sql = "SELECT id, username, name, password_hash, role FROM users WHERE username = ? AND role = ?";
        try (Connection c = DatabaseConnection.get(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, username);
            ps.setString(2, role);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    PasswordHasher.verify(password, dummyHash()); // keep timing similar for unknown users
                    return null;
                }
                if (!PasswordHasher.verify(password, rs.getString("password_hash"))) return null;
                return new User(rs.getLong("id"), rs.getString("username"), rs.getString("name"), rs.getString("role"));
            }
        } catch (SQLException e) {
            throw dbError(e);
        }
    }

    public List<User> listStudents() {
        List<User> out = new ArrayList<>();
        try (Connection c = DatabaseConnection.get();
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT id, username, name, role, student_class, passing_year, roll_no FROM users WHERE role = 'student' ORDER BY id")) {
            while (rs.next()) {
                int year = rs.getInt("passing_year");
                out.add(new User(rs.getLong("id"), rs.getString("username"), rs.getString("name"), rs.getString("role"),
                        rs.getString("student_class"), rs.wasNull() ? null : year, rs.getString("roll_no")));
            }
        } catch (SQLException e) {
            throw dbError(e);
        }
        return out;
    }

    public void createStudent(String name, String studentClass, int passingYear, String rollNo,
                              String username, String password) {
        if (name.isEmpty() || username.isEmpty()) throw new ServiceException(400, "Student name and login ID are required.");
        if (studentClass.isEmpty() || rollNo.isEmpty()) throw new ServiceException(400, "Class and university roll number are required.");
        if (passingYear < 2000 || passingYear > 2100) throw new ServiceException(400, "Enter a valid year of passing.");
        if (name.length() > 100 || username.length() > 50 || studentClass.length() > 100 || rollNo.length() > 30) {
            throw new ServiceException(400, "One of the fields is too long.");
        }
        if (password == null || password.length() < 4) throw new ServiceException(400, "Password must be at least 4 characters.");
        String hash = PasswordHasher.hash(password);
        inTransaction(c -> {
            if (exists(c, "SELECT 1 FROM users WHERE username = ?", username)) {
                throw new ServiceException(409, "That student login ID already exists. Choose a unique ID.");
            }
            if (exists(c, "SELECT 1 FROM users WHERE roll_no = ?", rollNo)) {
                throw new ServiceException(409, "That university roll number is already registered.");
            }
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO users (username, name, password_hash, role, student_class, passing_year, roll_no) "
                            + "VALUES (?, ?, ?, 'student', ?, ?, ?)")) {
                ps.setString(1, username);
                ps.setString(2, name);
                ps.setString(3, hash);
                ps.setString(4, studentClass);
                ps.setInt(5, passingYear);
                ps.setString(6, rollNo);
                ps.executeUpdate();
            }
            return null;
        });
    }

    // -------------------------------------------------------------------- events

    /** Admins see participant names; students see only their own name (others appear as "Student"). */
    public List<Event> listEvents(User viewer) {
        try (Connection c = DatabaseConnection.get()) {
            return loadEvents(c, viewer);
        } catch (SQLException e) {
            throw dbError(e);
        }
    }

    public long createEvent(Event e) {
        validate(e);
        return inTransaction(c -> {
            ensureSlotFree(c, e, -1);
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO events (name, description, category, capacity, event_date, event_time, venue) "
                            + "VALUES (?, ?, ?, ?, ?, ?, ?)", Statement.RETURN_GENERATED_KEYS)) {
                bind(ps, e);
                ps.executeUpdate();
                try (ResultSet keys = ps.getGeneratedKeys()) {
                    keys.next();
                    return keys.getLong(1);
                }
            }
        });
    }

    public void updateEvent(long id, Event e) {
        validate(e);
        inTransaction(c -> {
            if (!exists(c, "SELECT 1 FROM events WHERE id = ?", id)) throw new ServiceException(404, "Event not found.");
            int registered = count(c, "SELECT COUNT(*) FROM registrations WHERE event_id = ?", id);
            if (e.getCapacity() < registered) {
                throw new ServiceException(400, "Capacity cannot be lower than the current registrations (" + registered + ").");
            }
            ensureSlotFree(c, e, id);
            try (PreparedStatement ps = c.prepareStatement(
                    "UPDATE events SET name = ?, description = ?, category = ?, capacity = ?, "
                            + "event_date = ?, event_time = ?, venue = ? WHERE id = ?")) {
                bind(ps, e);
                ps.setLong(8, id);
                ps.executeUpdate();
            }
            return null;
        });
    }

    public void deleteEvent(long id) {
        inTransaction(c -> {
            try (PreparedStatement ps = c.prepareStatement("DELETE FROM events WHERE id = ?")) {
                ps.setLong(1, id);
                if (ps.executeUpdate() == 0) throw new ServiceException(404, "Event not found.");
            }
            return null;
        });
    }

    // ------------------------------------------------------------- registrations

    public Registration register(long eventId, User student) {
        return inTransaction(c -> {
            int capacity;
            String date;
            String time;
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT capacity, event_date, event_time FROM events WHERE id = ?")) {
                ps.setLong(1, eventId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) throw new ServiceException(404, "Event not found.");
                    capacity = rs.getInt(1);
                    date = rs.getString(2);
                    time = rs.getString(3);
                }
            }
            // 1. duplicate registration
            if (exists(c, "SELECT 1 FROM registrations WHERE event_id = ? AND user_id = ?", eventId, student.getId())) {
                throw new ServiceException(409, "You are already registered for this event.");
            }
            // 2. capacity
            if (count(c, "SELECT COUNT(*) FROM registrations WHERE event_id = ?", eventId) >= capacity) {
                throw new ServiceException(409, "Registration failed: Event has reached its maximum seating capacity.");
            }
            // 3. same date and time as another of this student's events
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT e.name FROM registrations r JOIN events e ON e.id = r.event_id "
                            + "WHERE r.user_id = ? AND e.event_date = ? AND e.event_time = ? LIMIT 1")) {
                ps.setLong(1, student.getId());
                ps.setString(2, date);
                ps.setString(3, time);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        throw new ServiceException(409, "Schedule Conflict! You are already registered for '"
                                + rs.getString(1) + "' at the exact same date and time (" + date + " @ " + time + ").");
                    }
                }
            }
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO registrations (event_id, user_id) VALUES (?, ?)")) {
                ps.setLong(1, eventId);
                ps.setLong(2, student.getId());
                ps.executeUpdate();
            }
            return new Registration(eventId, student.getId());
        });
    }

    public void cancelRegistration(long eventId, User student) {
        inTransaction(c -> {
            if (!exists(c, "SELECT 1 FROM events WHERE id = ?", eventId)) throw new ServiceException(404, "Event not found.");
            try (PreparedStatement ps = c.prepareStatement(
                    "DELETE FROM registrations WHERE event_id = ? AND user_id = ?")) {
                ps.setLong(1, eventId);
                ps.setLong(2, student.getId());
                ps.executeUpdate();
            }
            return null;
        });
    }

    // ------------------------------------------------------------------- seeding

    /** First run only: loads the same demo data the prototype shipped with. */
    public void seedDefaultData() {
        inTransaction(c -> {
            if (count(c, "SELECT COUNT(*) FROM users") > 0) return null;
            String adminPassword = System.getenv("ADMIN_PASSWORD");
            if (adminPassword == null || adminPassword.isBlank()) adminPassword = "admin123";
            insertUser(c, "admin", "Administrator", adminPassword, "admin");
            long alex = insertUser(c, "alex", "Alex Smith", "student123", "student");
            long emma = insertUser(c, "emma", "Emma Watson", "student123", "student");
            long john = insertUser(c, "john", "John Doe", "student123", "student");

            long e1 = insertEvent(c, "AI & Machine Learning Hackathon", "Build cutting-edge neural models over 24 hours.",
                    "Technical", 3, "2026-10-15", "10:00", "Computer Lab Alpha");
            long e2 = insertEvent(c, "Inter-College Annual Dance Fest", "Showcase your artistic moves on the grand stage.",
                    "Cultural", 5, "2026-10-18", "17:30", "Main Auditorium");
            long e3 = insertEvent(c, "Intramural Basketball Championship", "Fast-paced knockout tournament for department teams.",
                    "Sports", 2, "2026-10-15", "14:00", "Indoor Sports Complex");
            insertEvent(c, "Cybersecurity & Ethical Hacking", "Hands-on vulnerability assessment workshop.",
                    "Workshop", 4, "2026-10-22", "11:00", "Seminar Hall B");

            insertRegistration(c, e1, alex);
            insertRegistration(c, e1, emma);
            insertRegistration(c, e2, alex);
            insertRegistration(c, e3, john);
            return null;
        });
    }

    // ------------------------------------------------------------------- helpers

    private List<Event> loadEvents(Connection c, User viewer) throws SQLException {
        Map<Long, Event> byId = new LinkedHashMap<>();
        try (Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT id, name, description, category, capacity, event_date, event_time, venue "
                     + "FROM events ORDER BY id")) {
            while (rs.next()) {
                long id = rs.getLong(1);
                byId.put(id, new Event(id, rs.getString(2), rs.getString(3), rs.getString(4),
                        rs.getInt(5), rs.getString(6), rs.getString(7), rs.getString(8)));
            }
        }
        try (Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT r.event_id, u.id, u.name FROM registrations r "
                     + "JOIN users u ON u.id = r.user_id ORDER BY r.rowid")) {
            while (rs.next()) {
                Event ev = byId.get(rs.getLong(1));
                if (ev == null) continue;
                boolean visible = viewer.isAdmin() || rs.getLong(2) == viewer.getId();
                ev.addRegisteredStudent(visible ? rs.getString(3) : "Student");
            }
        }
        return new ArrayList<>(byId.values());
    }

    private void validate(Event e) {
        if (e.getName().isEmpty() || e.getDesc().isEmpty() || e.getVenue().isEmpty()) {
            throw new ServiceException(400, "Event name, description and venue are required.");
        }
        if (e.getName().length() > 200 || e.getDesc().length() > 2000 || e.getVenue().length() > 200) {
            throw new ServiceException(400, "One of the event fields is too long.");
        }
        if (!CATEGORIES.contains(e.getCategory())) throw new ServiceException(400, "Choose a valid category.");
        if (e.getCapacity() < 1) throw new ServiceException(400, "Capacity must be at least 1.");
        if (!DATE_RE.matcher(e.getDate()).matches()) throw new ServiceException(400, "Choose a valid date.");
        if (!TIME_RE.matcher(e.getTime()).matches()) throw new ServiceException(400, "Choose a valid time slot.");
    }

    private void ensureSlotFree(Connection c, Event e, long excludeId) throws SQLException {
        if (exists(c, "SELECT 1 FROM events WHERE event_date = ? AND event_time = ? "
                        + "AND lower(trim(venue)) = lower(trim(?)) AND id <> ?",
                e.getDate(), e.getTime(), e.getVenue(), excludeId)) {
            throw new ServiceException(409, SLOT_TAKEN);
        }
    }

    private void bind(PreparedStatement ps, Event e) throws SQLException {
        ps.setString(1, e.getName());
        ps.setString(2, e.getDesc());
        ps.setString(3, e.getCategory());
        ps.setInt(4, e.getCapacity());
        ps.setString(5, e.getDate());
        ps.setString(6, e.getTime());
        ps.setString(7, e.getVenue());
    }

    private long insertUser(Connection c, String username, String name, String password, String role) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO users (username, name, password_hash, role) VALUES (?, ?, ?, ?)", Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, username);
            ps.setString(2, name);
            ps.setString(3, PasswordHasher.hash(password));
            ps.setString(4, role);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                return keys.getLong(1);
            }
        }
    }

    private long insertEvent(Connection c, String name, String desc, String category, int capacity,
                             String date, String time, String venue) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO events (name, description, category, capacity, event_date, event_time, venue) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?)", Statement.RETURN_GENERATED_KEYS)) {
            bind(ps, new Event(null, name, desc, category, capacity, date, time, venue));
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                return keys.getLong(1);
            }
        }
    }

    private void insertRegistration(Connection c, long eventId, long userId) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("INSERT INTO registrations (event_id, user_id) VALUES (?, ?)")) {
            ps.setLong(1, eventId);
            ps.setLong(2, userId);
            ps.executeUpdate();
        }
    }

    private boolean exists(Connection c, String sql, Object... params) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) ps.setObject(i + 1, params[i]);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private int count(Connection c, String sql, Object... params) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) ps.setObject(i + 1, params[i]);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    private <T> T inTransaction(TxWork<T> work) {
        try (Connection c = DatabaseConnection.get()) {
            c.setAutoCommit(false);
            try {
                T result = work.run(c);
                c.commit();
                return result;
            } catch (ServiceException | SQLException ex) {
                c.rollback();
                throw ex;
            }
        } catch (SQLException ex) {
            throw dbError(ex);
        }
    }

    private static String dummyHash() {
        if (dummyHash == null) dummyHash = PasswordHasher.hash("not-a-real-password");
        return dummyHash;
    }

    private static ServiceException dbError(SQLException e) {
        e.printStackTrace();
        return new ServiceException(500, "A database error occurred. Please try again.");
    }
}
