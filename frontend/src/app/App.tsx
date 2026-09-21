import { Navigate, Route, Routes } from "react-router-dom";
import { AdminUsersPage } from "../features/admin/AdminUsersPage";
import { AuthPage } from "../features/auth/AuthPage";
import { DashboardPage } from "../features/dashboard/DashboardPage";
import { ProtectedRoute } from "./ProtectedRoute";
import { EventDetailPage, EventListPage } from "../features/events/EventPages";
import { EditEventPage, MyEventsPage, NewEventPage, OrganizerRoute } from "../features/events/OrganizerPages";

export function App() {
  return (
    <Routes>
      <Route path="/login" element={<AuthPage />} />
      <Route path="/events" element={<EventListPage />} />
      <Route path="/events/:id" element={<EventDetailPage />} />
      <Route element={<ProtectedRoute />}>
        <Route path="/" element={<DashboardPage />} />
        <Route path="/admin/users" element={<AdminUsersPage />} />
        <Route element={<OrganizerRoute />}>
          <Route path="/organizer/events" element={<MyEventsPage />} />
          <Route path="/organizer/events/new" element={<NewEventPage />} />
          <Route path="/organizer/events/:id" element={<EditEventPage />} />
        </Route>
      </Route>
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
}
