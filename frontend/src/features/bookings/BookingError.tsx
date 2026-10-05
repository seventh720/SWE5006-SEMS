import { Link, useLocation, useNavigate } from "react-router-dom";
import { ApiError } from "../../shared/api/client";
import { useAuth } from "../auth/AuthContext";
import { bookingError } from "./bookings";

export function BookingError({ error }: { error: unknown }) {
  const { logout } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const expired = error instanceof ApiError && error.status === 401;
  const forbidden = error instanceof ApiError && error.status === 403;
  return <div role="alert">
    <p className="error-message">{bookingError(error)}</p>
    {expired && <button type="button" className="secondary-button"
      onClick={() => { logout(); navigate("/login", { state: { from: location } }); }}>Sign in again</button>}
    {forbidden && <Link className="text-link" to="/">Return to dashboard</Link>}
  </div>;
}
