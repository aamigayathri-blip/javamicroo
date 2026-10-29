# Campus Event Management System

Java Servlets + SQLite backend for the existing HTML frontend. `src/main/webapp/index.html` is your
original file, unchanged. The server adds one `<script src="js/app.js">` tag when it serves the page
(`PageServlet`); `js/app.js` sends every login, event, account and registration action to the API
instead of keeping it in browser memory/localStorage. Screens, messages and behaviour stay the same.

## Run locally (Codespaces)
    mvn compile exec:java        # http://localhost:8080  (uses ./data/campus_events.db)

## Environment variables
| Variable | Purpose | Default |
|---|---|---|
| `DATABASE_PATH` | SQLite file location (created if missing) | `data/campus_events.db` |
| `ADMIN_PASSWORD` | Admin password used when the database is first created | `admin123` |
| `PORT` | HTTP port (Render sets this) | `8080` |

First start creates the schema (`database/schema.sql`) and the demo data the prototype had:
admin / admin123, students alex, emma, john (password `student123`) and the 4 sample events.
Set `ADMIN_PASSWORD` before the first start on any public deployment.

## Deploy on Render
Web Service → Docker → set `DATABASE_PATH=/var/data/campus_events.db` → attach a persistent disk
mounted at `/var/data` (without it SQLite data is lost on restart/redeploy).

## API (JSON, session cookie)
| Method & path | Who | Purpose |
|---|---|---|
| POST `/api/login`, `/api/logout` | all | sign in / out |
| GET `/api/events` | signed in | events (students only see their own name in participant lists) |
| POST `/api/events`, PUT/DELETE `/api/events/{id}` | admin | create / edit / delete (venue+date+time clash blocked) |
| GET/POST `/api/students` | admin | list / create student logins |
| POST/DELETE `/api/events/{id}/register` | student | register / cancel (duplicate, capacity, same-time clash blocked) |

Passwords are stored as salted PBKDF2-SHA256 hashes; permissions are checked on the server.
