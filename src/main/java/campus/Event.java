package campus;

import java.util.ArrayList;
import java.util.List;

/**
 * An event. Field names match the JSON the existing frontend already uses
 * (id, name, desc, category, capacity, date, time, venue, registeredStudents).
 */
public class Event {
    private Long id;
    private String name;
    private String desc;
    private String category;
    private int capacity;
    private String date;
    private String time;
    private String venue;
    private final List<String> registeredStudents = new ArrayList<>();

    public Event(Long id, String name, String desc, String category,
                 int capacity, String date, String time, String venue) {
        this.id = id;
        this.name = name;
        this.desc = desc;
        this.category = category;
        this.capacity = capacity;
        this.date = date;
        this.time = time;
        this.venue = venue;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public String getDesc() { return desc; }
    public String getCategory() { return category; }
    public int getCapacity() { return capacity; }
    public String getDate() { return date; }
    public String getTime() { return time; }
    public String getVenue() { return venue; }
    public List<String> getRegisteredStudents() { return registeredStudents; }

    public void addRegisteredStudent(String studentName) { registeredStudents.add(studentName); }
}
