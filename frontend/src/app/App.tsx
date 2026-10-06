import { OrganizerOrdersPage } from "../features/bookings/OrganizerOrdersPage";
import { Navigate, Route, Routes } from "react-router-dom";
import { AdminUsersPage } from "../features/admin/AdminUsersPage";
import { AuthPage } from "../features/auth/AuthPage";
import { MyOrdersPage, AttendeeRoute } from "../features/bookings/MyOrdersPage";
import { OrderDetailPage } from "../features/bookings/OrderDetailPage";
import { DashboardPage } from "../features/dashboard/DashboardPage";
import { ProtectedRoute } from "./ProtectedRoute";
import { EventDetailPage, EventListPage } from "../features/events/EventPages";
import { EditEventPage, MyEventsPage, NewEventPage, OrganizerRoute } from "../features/events/OrganizerPages";
import { OrganizerTicketTypesPage } from "../features/ticketing/OrganizerTicketTypesPage";

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
          <Route path="/organizer/events/:id/bookings" element={<OrganizerOrdersPage />} />
          <Route path="/organizer/events/:id/ticket-types" element={<OrganizerTicketTypesPage />} />
        </Route>
        <Route element={<AttendeeRoute />}>
          <Route path="/bookings" element={<MyOrdersPage />} />
          <Route path="/bookings/:id" element={<OrderDetailPage />} />
        </Route>
      </Route>
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
}
