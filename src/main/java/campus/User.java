package campus;

import java.io.Serializable;

/** A login account: either a student or the administrator. No password data is kept here. */
public class User implements Serializable {
    private static final long serialVersionUID = 1L;

    private final long id;
    private final String username;
    private final String name;
    private final String role;
    private final String studentClass;
    private final Integer passingYear;
    private final String rollNo;

    public User(long id, String username, String name, String role) {
        this(id, username, name, role, null, null, null);
    }

    public User(long id, String username, String name, String role,
                String studentClass, Integer passingYear, String rollNo) {
        this.id = id;
        this.username = username;
        this.name = name;
        this.role = role;
        this.studentClass = studentClass;
        this.passingYear = passingYear;
        this.rollNo = rollNo;
    }

    public String getStudentClass() { return studentClass; }
    public Integer getPassingYear() { return passingYear; }
    public String getRollNo() { return rollNo; }

    public long getId() { return id; }
    public String getUsername() { return username; }
    public String getName() { return name; }
    public String getRole() { return role; }
    public boolean isAdmin() { return "admin".equals(role); }
    public boolean isStudent() { return "student".equals(role); }
}
