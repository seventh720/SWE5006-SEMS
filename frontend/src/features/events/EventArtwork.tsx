export const illustrations = [
  ["GENERAL", "Gatherings"], ["TECH", "Technology"], ["MUSIC", "Music"],
  ["SPORT", "Sports"], ["ART", "Arts & culture"], ["SOCIAL", "Community"],
] as const;

export function illustrationPath(illustration?: string) {
  const selection = illustrations.find(([key]) => key === illustration) ?? illustrations[0];
  return `/illustrations/${selection[0].toLowerCase()}.svg`;
}

export function EventArtwork({ illustration, hero = false }: { illustration?: string; hero?: boolean }) {
  const selection = illustrations.find(([key]) => key === illustration) ?? illustrations[0];
  return <div className={`event-artwork${hero ? " event-artwork-hero" : ""}`}>
    <img src={`/illustrations/${selection[0].toLowerCase()}.svg`} alt="" loading={hero ? "eager" : "lazy"} />
    <span className="event-category">{selection[1]}</span>
  </div>;
}
