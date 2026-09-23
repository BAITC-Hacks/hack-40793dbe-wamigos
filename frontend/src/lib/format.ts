export function timestamp(ms: number): string {
  const seconds = Math.max(0, Math.floor(ms / 1000));
  return `${Math.floor(seconds / 60)
    .toString()
    .padStart(2, "0")}:${(seconds % 60).toString().padStart(2, "0")}`;
}
export function duration(ms: number): string {
  const minutes = Math.ceil(ms / 60_000);
  return `${minutes} ${new Intl.PluralRules("ru").select(minutes) === "one" ? "минута" : new Intl.PluralRules("ru").select(minutes) === "few" ? "минуты" : "минут"}`;
}
export function addedDate(value: string): string {
  return new Intl.DateTimeFormat("ru", {
    day: "numeric",
    month: "long",
    hour: "2-digit",
    minute: "2-digit",
  }).format(new Date(value));
}
export function calendarDate(value: string): string {
  const [year, month, day] = value.split("-");
  return `${day}.${month}.${year}`;
}
export function safeFilename(title: string, format: string): string {
  return `${
    title
      .replace(/[<>:"/\\|?*\u0000-\u001f]/g, "")
      .trim()
      .replace(/[. ]+$/, "")
      .slice(0, 100) || "Протокол"
  }.${format}`;
}
