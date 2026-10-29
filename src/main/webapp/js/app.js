/*
 * SQLite bridge for the Campus Event Management page.
 * index.html and its inline script are untouched. This file loads after them and swaps the
 * in-memory / localStorage data handling for calls to the Java servlet API (SQLite database),
 * keeping the same screens, messages and behaviour.
 */
(function () {
    "use strict";

    async function api(method, url, body) {
        const res = await fetch("api" + url, {
            method,
            credentials: "same-origin",
            headers: Object.assign({ "X-Requested-With": "campus" }, body ? { "Content-Type": "application/json" } : {}),
            body: body ? JSON.stringify(body) : undefined
        });
        let data = null;
        try { data = await res.json(); } catch (_) { /* no body */ }
        if (!res.ok) {
            const err = new Error((data && data.error) || "Something went wrong. Please try again.");
            err.status = res.status;
            if (res.status === 401 && !url.startsWith("/login")) { window.logout(); }
            throw err;
        }
        return data;
    }

    const fail = (err) => showAlert(escapeCalendarText(err.message), "error");
    const redraw = () => { renderApp(); renderCalendar(); };

    async function reloadEvents() { events = await api("GET", "/events"); }
    async function resync() { try { await reloadEvents(); redraw(); } catch (_) { /* ignore */ } }

    // No data or plain-text passwords on the client before sign-in.
    try { localStorage.removeItem("campusStudentAccounts"); } catch (_) { /* ignore */ }
    events = [];
    studentAccounts = [];
    ADMIN_ACCOUNT.password = "";

    // ---- Login / logout -------------------------------------------------------------
    const originalHandleLogin = window.handleLogin;
    const originalLogout = window.logout;

    window.handleLogin = async function (e) {
        e.preventDefault();
        const role = document.getElementById("login-role").value;
        const username = document.getElementById("login-username").value.trim();
        const password = document.getElementById("login-password").value;
        try {
            const user = await api("POST", "/login", { role, username, password });
            await reloadEvents();
            const accounts = role === "admin" ? await api("GET", "/students") : [];

            // The server has verified the credentials; give the page's own login routine a
            // matching record so it can switch screens exactly as before.
            if (role === "admin") {
                ADMIN_ACCOUNT.username = username;
                ADMIN_ACCOUNT.password = password;
                studentAccounts = accounts;
            } else {
                studentAccounts = [{ name: user.name, username: user.username, password }];
            }
            originalHandleLogin(e);
            ADMIN_ACCOUNT.password = "";
            studentAccounts = accounts;
        } catch (err) {
            const box = document.getElementById("login-error");
            box.textContent = err.message;
            box.classList.remove("hidden");
        }
    };

    window.logout = function () {
        api("POST", "/logout").catch(() => {});
        events = [];
        studentAccounts = [];
        originalLogout();
    };

    // ---- Admin: student accounts ----------------------------------------------------
    window.createStudentAccount = async function (e) {
        e.preventDefault();
        const name = document.getElementById("new-student-name").value.trim();
        const studentClass = document.getElementById("new-student-class").value.trim();
        const passingYear = parseInt(document.getElementById("new-student-year").value);
        const rollNo = document.getElementById("new-student-roll").value.trim();
        const username = document.getElementById("new-student-id").value.trim();
        const password = document.getElementById("new-student-password").value;
        try {
            await api("POST", "/students", { name, studentClass, passingYear, rollNo, username, password });
            studentAccounts = await api("GET", "/students");
            document.getElementById("student-account-form").reset();
            renderStudentAccounts();
            showAlert(`Student login created for ${escapeCalendarText(name)}. Share the login ID and temporary password with that student.`, "success");
        } catch (err) {
            fail(err);
        }
    };

    // ---- Admin: events --------------------------------------------------------------
    window.handleSaveEvent = async function (e) {
        e.preventDefault();
        if (!validateSelectedTimeSlot()) return;
        const editId = document.getElementById("edit-event-id").value;
        const payload = {
            name: document.getElementById("f-name").value,
            desc: document.getElementById("f-desc").value,
            category: document.getElementById("f-category").value,
            capacity: parseInt(document.getElementById("f-capacity").value),
            date: document.getElementById("f-date").value,
            time: document.getElementById("f-time").value,
            venue: document.getElementById("f-venue").value
        };
        try {
            if (editId) {
                await api("PUT", "/events/" + encodeURIComponent(editId), payload);
            } else {
                await api("POST", "/events", payload);
            }
            await reloadEvents();
            showAlert(editId ? "Event updated successfully." : "New event successfully created and published.", "success");
            closeEventModal();
            redraw();
        } catch (err) {
            fail(err);
            resync();
        }
    };

    window.deleteEvent = async function (id) {
        if (!confirm("Are you sure you want to delete this event? All participant registrations will be removed.")) return;
        try {
            await api("DELETE", "/events/" + id);
            await reloadEvents();
            showAlert("Event deleted successfully.", "info");
            redraw();
        } catch (err) {
            fail(err);
        }
    };

    // ---- Student: registrations -----------------------------------------------------
    window.registerEvent = async function (eventId) {
        const ev = events.find(x => x.id === eventId);
        try {
            await api("POST", "/events/" + eventId + "/register");
            await reloadEvents();
            showAlert(`Successfully registered for "${escapeCalendarText(ev ? ev.name : "this event")}"!`, "success");
            redraw();
        } catch (err) {
            fail(err);
            resync();
        }
    };

    window.unregisterEvent = async function (eventId) {
        const ev = events.find(x => x.id === eventId);
        try {
            await api("DELETE", "/events/" + eventId + "/register");
            await reloadEvents();
            showAlert(`Cancelled registration for "${escapeCalendarText(ev ? ev.name : "this event")}".`, "info");
            redraw();
            if (!document.getElementById("student-tab-registrations").classList.contains("hidden")) renderMyRegistrations();
        } catch (err) {
            fail(err);
            resync();
        }
    };
})();
