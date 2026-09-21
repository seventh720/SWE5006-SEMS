export interface PublishedEvent {
  id: string;
  title: string;
  description: string;
  location: string;
  startsAt: string;
  endsAt: string;
  capacity: number;
  status: "PUBLISHED";
}

export interface EventPage {
  items: PublishedEvent[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export function readEventQuery(params: URLSearchParams) {
  const rawPage = params.get("page") ?? "0";
  const page = Number(rawPage);
  return {
    page: /^\d+$/.test(rawPage) && Number.isSafeInteger(page) ? page : 0,
    keyword: (params.get("keyword") ?? "").trim(),
  };
}

export function eventListPath(page: number, keyword: string) {
  const params = new URLSearchParams({ page: String(page), size: "10" });
  if (keyword.trim()) params.set("keyword", keyword.trim());
  return `/api/v1/events?${params}`;
}

export function formatEventTime(value: string) {
  return new Intl.DateTimeFormat("en-SG", {
    dateStyle: "medium", timeStyle: "short", timeZone: "Asia/Singapore",
  }).format(new Date(value));
}
